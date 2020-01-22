package org.ergoplatform.appkit.ergotool.dex

import java.io.File
import java.util

import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.Parameters.MinFee
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.JavaHelpers._
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.ergotool.{AppContext, Cmd, CmdDescriptor, RunWithErgoClient}

import scala.util.Try

/** Creates and sends a new transaction with boxes that match given buyer and seller orders for AssetsAtomicExchange
  *
  * Steps:<br/>
  * 1) request storage password from the user<br/>
  * 2) read storage file, unlock using password and get secret<br/>
  * 3) get master public key and compute sender's address<br/>
  * 4) find the box with buyer's order (by buyerHolderBoxId)<br/>
  * 5) find the box with seller's order (by sellerHolderBoxId)<br/>
  * 6) select sender's coins to cover the transaction fee, and computes the amount of change<br/>
  * 7) create output box for buyer's tokens<br/>
  * 8) create output box for seller's Ergs<br/>
  * 9) create a transaction using buyer's and seller's order boxes (from steps 4,5) as inputs<br/>
  * 10) sign (using secret key) the transaction<br/>
  * 11) if no `--dry-run` option is specified, send the transaction to the network<br/>
  *    otherwise skip sending<br/>
  * 12) serialize transaction to Json and print to the console<br/>
  *
  * @param storageFile storage with secret key of the sender
  * @param storagePass password to access sender secret key in the storage
  * @param sellerHolderBoxId BoxId of the seller's order
  * @param buyerHolderBoxId BoxId of the buyer's order
  * @param minDexFee minimal fee claimable by DEX in this transaction
  */
case class CancelOrderCmd(toolConf: ErgoToolConfig,
                          name: String,
                          storageFile: File,
                          storagePass: SecretString,
                          orderBoxId: ErgoId) extends Cmd with RunWithErgoClient {

  // TODO: update scaladoc

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

      val contract = ErgoContracts.sendToPK(ctx, recipientAddress)
      val tx = CancelOrder.txProto(orderBox, recipientAddress, contract, unspentBoxesForAmount)
        .fold(t => error(s"$t"), tx => tx).toTx(ctx.newTxBuilder)

      val signed = loggedStep(s"Signing the transaction", console) {
        senderProver.sign(tx)
      }
      val txJson = signed.toJson(true)
      console.println(s"Tx: $txJson")

      if (!runCtx.isDryRun) {
        val txId = loggedStep(s"Sending the transaction", console) {
          ctx.sendTransaction(signed)
        }
        console.println(s"Server returned tx id: $txId")
      }
    })
  }
}

object CancelOrderCmd extends CmdDescriptor(
  name = "dex:CancelOrder", cmdParamSyntax = "<wallet file> <orderBoxId>",
  description = "claim an unspent buy/sell order (by <orderBoxId>) and sends the ERGs/tokens to the address of this wallet (requests storage password)") {

  override def parseCmd(ctx: AppContext): Cmd = {
    val args = ctx.cmdArgs
    val storageFile = new File(if (args.length > 1) args(1) else error("Wallet storage file path is not specified"))
    if (!storageFile.exists()) error(s"Specified wallet file is not found: $storageFile")
    val orderBoxId = ErgoId.create(if (args.length > 2) args(2) else error("seller order box id is not specified"))
    val pass = ctx.console.readPassword("Storage password>")
    CancelOrderCmd(ctx.toolConf, name, storageFile, pass, orderBoxId)
  }
}

object CancelOrder {

  case class TxProto(inputBoxes: Seq[InputBox],
                     outputBoxes: Seq[OutBoxProto],
                     fee: Long,
                     sendChangeTo: ErgoAddress) {

    def toTx(builder: UnsignedTransactionBuilder): UnsignedTransaction =
      builder
        .boxesToSpend(Iso.JListToIndexedSeq(Iso.identityIso[InputBox]).from(inputBoxes.toIndexedSeq))
        .outputs(outputBoxes.map(_.toOutBox(builder.outBoxBuilder)): _*)
        .fee(fee)
        .sendChangeTo(sendChangeTo)
        .build()
  }

  case class OutBoxProto(getValue: Long, tokens: Seq[ErgoToken], contract: ErgoContract) {

    def toOutBox(builder: OutBoxBuilder): OutBox =
      builder
        .value(getValue)
        .tokens(tokens: _*)
        .contract(contract)
        .build()
  }

  def txProto(orderBox: InputBox, recipientAddress: Address, contract: ErgoContract,
              unspentBoxesForAmount: (Long) => Seq[InputBox]): Try[TxProto] =
    outBoxProto(orderBox, recipientAddress, contract)
      .map { outbox =>
        val inputBoxes = selectInputBoxes(orderBox, outbox.getValue, unspentBoxesForAmount)
        TxProto(inputBoxes, Seq(outbox), MinFee, recipientAddress.getErgoAddress)
      }

  def outBoxProto(orderBox: InputBox, recipientAddress: Address, contract: ErgoContract): Try[OutBoxProto] = {
    Try {
      val orderBoxContractTemplate = ErgoTreeTemplate.fromErgoTree(orderBox.getErgoTree)
      val orderBoxId = orderBox.getId
      val txFee = MinFee
      // TODO: add ErgoTreeTemplate.equals
      if (util.Arrays.equals(orderBoxContractTemplate.getBytes, SellerContract.contractTemplate.getBytes)) {
        // sell order
        val sellerPk = SellerContract.sellerPkFromTree(orderBox.getErgoTree)
          .getOrElse(sys.error(s"cannot extract seller PK from order box $orderBoxId"))
        require(sellerPk == recipientAddress.getPublicKey,
          s"sell order box $orderBoxId can be claimed with $sellerPk PK, while your's is ${recipientAddress.getPublicKey}")
        OutBoxProto(orderBox.getValue - txFee, Seq(orderBox.getTokens.get(0)), contract)
      } else if (util.Arrays.equals(orderBoxContractTemplate.getBytes, BuyerContract.contractTemplate.getBytes)) {
        // buy order
        val buyerPk = BuyerContract.buyerPkFromTree(orderBox.getErgoTree)
          .getOrElse(sys.error(s"cannot extract buyer PK from order box $orderBoxId"))
        require(buyerPk == recipientAddress.getPublicKey,
          s"buy order box $orderBoxId can be claimed with ${buyerPk} PK, while yours is ${recipientAddress.getPublicKey}")
        OutBoxProto(orderBox.getValue - txFee, Seq(), contract)
      } else {
        sys.error(s"unsupported contract type in box ${orderBoxId.toString}")
      }
    }
  }

  def selectInputBoxes(orderBox: InputBox, outboxValue: Long,
                       unspentBoxes: (Long) => Seq[InputBox]): Seq[InputBox] =
    if (outboxValue >= MinFee) {
      Seq(orderBox)
    } else {
      unspentBoxes(MinFee - outboxValue) :+ orderBox
    }
}

