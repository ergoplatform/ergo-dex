package org.ergoplatform.dex.commands

import java.io.File

import org.ergoplatform.appkit.Parameters.MinFee
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.JavaHelpers._
import org.ergoplatform.appkit.cli.AppContext
import org.ergoplatform.appkit.commands._
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.impl.ErgoTreeContract
import sigmastate.Values.SigmaPropConstant

/** Claims an unspent buy/sell order and send the ERGs/tokens to the address of this wallet
  *
  * Steps:<br/>
  * 1) request storage password from the user<br/>
  * 2) read storage file, unlock using password and get secret<br/>
  * 3) get master public key and compute sender's address<br/>
  * 4) find the box with order to cancel (by orderBoxId)<br/>
  * 5) select sender's coins to cover the transaction fee, and computes the amount of change<br/>
  * 6) create output box for buyer's tokens<br/>
  * 7) create output box for ERGs (and tokens if sell order)<br/>
  * 8) create a transaction canceled order as input<br/>
  * 9) sign (using secret key) the transaction<br/>
  * 10) if no `--dry-run` option is specified, send the transaction to the network<br/>
  *    otherwise skip sending<br/>
  * 11) serialize transaction to Json and print to the console<br/>
  *
  * @param storageFile storage with secret key of the sender
  * @param storagePass password to access sender secret key in the storage
  * @param orderBoxId BoxId of the sell/buy order to cancel
  */
case class CancelOrderCmd(toolConf: ErgoToolConfig,
                          name: String,
                          storageFile: File,
                          storagePass: SecretString,
                          orderBoxId: ErgoId) extends Cmd with RunWithErgoClient {

  override def runWithClient(ergoClient: ErgoClient, runCtx: AppContext): Unit = {
    val console = runCtx.console
    ergoClient.execute(ctx => {
      val senderProver = loggedStep("Creating prover", console) {
        BoxOperations.createProver(ctx, storageFile.getPath, storagePass).build()
      }
      val orderBox = loggedStep(s"Loading order's box (${orderBoxId.toString})", console) {
        ctx.getBoxesById(orderBoxId.toString).head
      }

      val recipientAddress = senderProver.getAddress

      def unspentBoxesForAmount(nanoErgs: Long): Seq[InputBox] = {
        val unspent = loggedStep(s"Loading unspent boxes from at address $recipientAddress", console) {
          ctx.getUnspentBoxesFor(recipientAddress)
        }
        BoxOperations.selectTop(unspent, nanoErgs).convertTo[IndexedSeq[InputBox]]
      }

      val orderBoxContractTemplate = ErgoTreeTemplate.fromErgoTree(orderBox.getErgoTree)

      val txBuilder = ctx.newTxBuilder
      val signedTxs = if (orderBoxContractTemplate.equals(SellerContract.contractTemplate)) {
        val tx = CancelOrder.txForSellOrder(orderBox, recipientAddress, unspentBoxesForAmount).toTx(txBuilder)
        val signed = loggedStep(s"Signing cancel transaction for sell order", console) {
          senderProver.sign(tx)
        }
        val txJson = signed.toJson(true)
        console.println(s"Tx: ${signed.toJson(true)}")
        Seq(signed)
      } else {
        val firstTx = CancelOrder.firstTxForBuyOrder(orderBox, recipientAddress, unspentBoxesForAmount).toTx(txBuilder)
        val signedFirstTx = loggedStep(s"Signing the first cancel transaction for buy order", console) {
          senderProver.sign(firstTx)
        }
        console.println(s"Tx: ${signedFirstTx.toJson(true)}")
        val secondTx = CancelOrder.txToBurnMintedToken(signedFirstTx, recipientAddress).toTx(txBuilder)
        val signedSecondTx = loggedStep(s"Signing the second cancel transaction for buy order", console) {
          senderProver.sign(secondTx)
        }
        console.println(s"Tx: ${signedSecondTx.toJson(true)}")
        Seq(signedFirstTx, signedSecondTx)
      }

      signedTxs.foreach{ tx => 
        if (!runCtx.isDryRun) {
          val txId = loggedStep(s"Sending the transaction(s)", console) {
            ctx.sendTransaction(tx)
          }
          console.println(s"Server returned tx id: $txId")
        }
      }
    })
  }
}

object CancelOrderCmd extends CmdDescriptor(
  name = "dex:CancelOrder", cmdParamSyntax = "<wallet file> <orderBoxId>",
  description = "claim an unspent buy/sell order (by <orderBoxId>) and sends the ERGs/tokens to the address of this wallet (requests storage password)") {

  override val parameters: Seq[CmdParameter] = Array(
    CmdParameter("storageFile", FilePType,
      "storage with secret key of the sender"),
    CmdParameter("storagePass", "Storage password", SecretStringPType,
      "password to access sender secret key in the storage", None,
      Some(PasswordInput), None),
    CmdParameter("orderBoxId", ErgoIdPType,
      "order box id"),

  )

  override def createCmd(ctx: AppContext): Cmd = {
    val Seq(
      storageFile: File,
      pass: SecretString,
      orderBoxId: ErgoId
    ) = ctx.cmdParameters
    CancelOrderCmd(ctx.toolConf, name, storageFile, pass, orderBoxId)
  }
}

object CancelOrder {

  case class TxProto(inputBoxes: Seq[InputBox],
                     outputBoxes: Seq[OutBoxProto],
                     fee: Long,
                     sendChangeTo: Address) {

    def toTx(builder: UnsignedTransactionBuilder): UnsignedTransaction =
      builder
        .boxesToSpend(Iso.JListToIndexedSeq(Iso.identityIso[InputBox]).from(inputBoxes.toIndexedSeq))
        .outputs(outputBoxes.map(_.toOutBox(builder.outBoxBuilder)): _*)
        .fee(fee)
        .sendChangeTo(sendChangeTo.getErgoAddress)
        .build()
  }

  case class MintTokenInfo(token: ErgoToken, name: String, desc: String, numberOfDecimals: Int)

  case class OutBoxProto(getValue: Long,
                         tokens: Seq[ErgoToken],
                         mintToken: Option[MintTokenInfo],
                         registers: Seq[ErgoValue[_]],
                         contract: ErgoContract) {

    def toOutBox(builder: OutBoxBuilder): OutBox = {
      val builder2 = builder
        .value(getValue)
        .contract(contract)
      if (registers.nonEmpty) {
        builder2.registers(registers: _*)
      }
      if (tokens.nonEmpty) {
        builder2.tokens(tokens: _*)
      }
      if (mintToken.isDefined) {
        val t = mintToken.get
        builder2.mintToken(t.token, t.name, t.desc, t.numberOfDecimals)
      }
      builder2.build()
    }
  }

  def txForSellOrder(orderBox: InputBox, recipientAddress: Address,
               unspentBoxesForAmount: (Long) => Seq[InputBox]): TxProto = {
    val orderBoxId = orderBox.getId
    val txFee = MinFee
    val outboxContract = new ErgoTreeContract(SigmaPropConstant(recipientAddress.getPublicKey))
    val sellerPk = SellerContract.sellerPkFromTree(orderBox.getErgoTree)
      .getOrElse(sys.error(s"cannot extract seller PK from order box $orderBoxId"))
    require(sellerPk == recipientAddress.getPublicKey,
      s"sell order box $orderBoxId can be claimed with $sellerPk PK, while your's is ${recipientAddress.getPublicKey}")
    val outboxValue = math.max(orderBox.getValue - txFee, MinFee)
    val outbox = OutBoxProto(outboxValue, Seq(orderBox.getTokens.get(0)), None, Seq(), outboxContract)
    val inputBoxes = selectInputBoxes(orderBox, outboxValue + txFee, unspentBoxesForAmount)
    TxProto(inputBoxes, Seq(outbox), txFee, recipientAddress)
  }

  def firstTxForBuyOrder(orderBox: InputBox, recipientAddress: Address,
               unspentBoxesForAmount: (Long) => Seq[InputBox]): TxProto = {
    val orderBoxId = orderBox.getId
    val txFee = MinFee
    val outboxContract = new ErgoTreeContract(SigmaPropConstant(recipientAddress.getPublicKey))
    val buyerPk = BuyerContract.buyerPkFromTree(orderBox.getErgoTree)
      .getOrElse(sys.error(s"cannot extract buyer PK from order box $orderBoxId"))
    require(buyerPk == recipientAddress.getPublicKey,
      s"buy order box $orderBoxId can be claimed with ${buyerPk} PK, while yours is ${recipientAddress.getPublicKey}")
    // as a workaround for https://github.com/ScorexFoundation/sigmastate-interpreter/issues/628
    // box.tokens cannot be empty, so we mint new token
    val token = new ErgoToken(orderBox.getId, 1L)
    // according to https://github.com/ergoplatform/eips/blob/master/eip-0004.md
    val mintTokenInfo = MintTokenInfo(
      token = token,
      name = "CANCELDEXBUY", // token name (see EIP-4)
      desc = "New token each time DEX buy order is cancelled", // token description (see EIP-4)
      numberOfDecimals = 2 // number of decimals (see EIP-4)
    )
    val feeForSecondTx = MinFee + txFee
    val outboxValue = math.max(orderBox.getValue - txFee, MinFee + feeForSecondTx)
    val outbox = OutBoxProto(outboxValue, Seq(), Some(mintTokenInfo), Seq(), outboxContract)
    val inputBoxes = selectInputBoxes(orderBox, outboxValue + txFee, unspentBoxesForAmount)
    TxProto(inputBoxes, Seq(outbox), txFee, recipientAddress)
  }

  def txToBurnMintedToken(firstTx: SignedTransaction, recipientAddress: Address): TxProto = {
    val inputBoxWithToken = firstTx.getOutputsToSpend().get(0)
    val txFee = MinFee
    val outboxValue = MinFee
    val outboxContract = new ErgoTreeContract(SigmaPropConstant(recipientAddress.getPublicKey))
    val outbox = OutBoxProto(outboxValue, Seq(), None, Seq(), outboxContract)
    TxProto(Seq(inputBoxWithToken), Seq(outbox), txFee, recipientAddress)
  }

  def selectInputBoxes(orderBox: InputBox, toSpend: Long,
                       unspentBoxesForAmount: (Long) => Seq[InputBox]): Seq[InputBox] =
    if (orderBox.getValue >= toSpend) {
      Seq(orderBox)
    } else {
      Seq(orderBox) ++ unspentBoxesForAmount(toSpend - orderBox.getValue())
    }

}

