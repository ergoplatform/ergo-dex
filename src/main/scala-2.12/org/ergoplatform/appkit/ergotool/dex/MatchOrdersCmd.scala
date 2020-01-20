package org.ergoplatform.appkit.ergotool.dex

import java.io.File
import java.util

import org.ergoplatform.appkit.Parameters.MinFee
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.ergotool.{AppContext, Cmd, CmdDescriptor, RunWithErgoClient}

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
case class MatchOrdersCmd(toolConf: ErgoToolConfig,
                          name: String,
                          storageFile: File,
                          storagePass: SecretString,
                          sellerHolderBoxId: ErgoId,
                          buyerHolderBoxId: ErgoId,
                          minDexFee: Long) extends Cmd with RunWithErgoClient {

  override def runWithClient(ergoClient: ErgoClient, runCtx: AppContext): Unit = {
    val console = runCtx.console
    ergoClient.execute(ctx => {
      val senderProver = loggedStep("Creating prover", console) {
        BoxOperations.createProver(ctx, storageFile.getPath, storagePass).build
      }
      val sellerHolderBox = loggedStep(s"Loading seller's box (${sellerHolderBoxId.toString})", console) {
        ctx.getBoxesById(sellerHolderBoxId.toString).head
      }
      val sellerAddressPk = SellerContract.sellerPkFromTree(sellerHolderBox.getErgoTree)
        .getOrElse(error(s"cannot find seller's public key in seller order in box $sellerHolderBoxId"))
      val buyerHolderBox = loggedStep(s"Loading buyer's box (${buyerHolderBoxId.toString})", console) {
        ctx.getBoxesById(buyerHolderBoxId.toString).head
      }
      val buyerAddressPk = BuyerContract.buyerPkFromTree(buyerHolderBox.getErgoTree)
        .getOrElse(error(s"cannot find buyer's public key in buyer order in box $buyerHolderBoxId"))

      val token = sellerHolderBox.getTokens.get(0)
      if (!BuyerContract.tokenFromContractTree(buyerHolderBox.getErgoTree).contains(token)) {
        error(s"cannot find token $token in buyer order in box $buyerHolderBoxId")
      }
      val ergAmountSellerAsk = SellerContract.tokenPriceFromTree(sellerHolderBox.getErgoTree)
        .getOrElse(error(s"cannot find token price in seller's order box $sellerHolderBoxId"))
      if (buyerHolderBox.getValue < ergAmountSellerAsk) {
        error(s"not enough value in buyer's order box for seller order in box $sellerHolderBoxId")
      }

      val buyerOutBoxValue = MinFee
      val claimableValue = buyerHolderBox.getValue - ergAmountSellerAsk + sellerHolderBox.getValue - buyerOutBoxValue
      val txFee = MinFee
      val dexFee = claimableValue - txFee
      if (dexFee <= minDexFee) {
        error(s"found DEX fee = (claimable value - miner's fee) to be $dexFee, which is <= minDexFee ")
      }
      val inputBoxes = util.Arrays.asList(buyerHolderBox, sellerHolderBox)
      val txB = ctx.newTxBuilder
      val buyerTokensOutBox = txB.outBoxBuilder
        .value(buyerOutBoxValue)
        .contract(ctx.compileContract(
          ConstantsBuilder.create
            .item("recipientPk", buyerAddressPk)
            .build(),
          "{ recipientPk }"))
        .tokens(token)
        .registers(ErgoValue.of(buyerHolderBoxId.getBytes))
        .build()
      val sellerErgsOutBox = txB.outBoxBuilder
        .value(ergAmountSellerAsk)
        .contract(ctx.compileContract(
          ConstantsBuilder.create
            .item("recipientPk", sellerAddressPk)
            .build(),
          "{ recipientPk }"))
        .registers(ErgoValue.of(sellerHolderBoxId.getBytes))
        .build()
      val tx = txB
        .boxesToSpend(inputBoxes).outputs(buyerTokensOutBox, sellerErgsOutBox)
        .fee(txFee)
        // DEX's fee
        .sendChangeTo(senderProver.getP2PKAddress)
        .build()
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

object MatchOrdersCmd extends CmdDescriptor(
  name = "dex:MatchOrders", cmdParamSyntax = "<wallet file> <sellerHolderBoxId> <buyerHolderBoxId>      <minDexFee",
  description = "match an existing token seller's order (by <sellerHolderBoxId>) and an existing buyer's order (by <buyerHolderBoxId) and send tokens to buyer's address(extracted from buyer's order) and Ergs to seller's address(extracted from seller's order) claiming the minimum fee of <minDexFee> with the given <wallet file> to sign transaction (requests storage password)") {

  override def parseCmd(ctx: AppContext): Cmd = {
    val args = ctx.cmdArgs
    val storageFile = new File(if (args.length > 1) args(1) else error("Wallet storage file path is not specified"))
    if (!storageFile.exists()) error(s"Specified wallet file is not found: $storageFile")
    val sellerHolderBoxId = ErgoId.create(if (args.length > 2) args(2) else error("seller order box id is not specified"))
    val buyerHolderBoxId = ErgoId.create(if (args.length > 3) args(3) else error("buyer order box id is not specified"))
    val minDexFee = if(args.length > 4) args(4).toLong else error("minDexFee is not specified")
    val pass = ctx.console.readPassword("Storage password>")
    MatchOrdersCmd(ctx.toolConf, name, storageFile, pass, sellerHolderBoxId, buyerHolderBoxId, minDexFee)
  }
}

