package org.ergoplatform.dex.commands

import java.io.File
import java.util

import org.ergoplatform.appkit.Parameters.MinFee
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.cli.AppContext
import org.ergoplatform.appkit.commands._
import org.ergoplatform.appkit.config.ErgoToolConfig

/** Creates and sends a new transaction which spend the boxes that match the given buyer and seller
  * orders (protected by `buyer` and `seller` contracts from AssetsAtomicExchange).
  *
  * Steps:<br/>
  * 1) request storage password from the user<br/>
  * 2) read storage file, unlock using password and get secret<br/>
  * 3) get master public key and compute transaction sender's address<br/>
  * 4) find the box with buyer's order (by buyerHolderBoxId)<br/>
  * 5) find the box with seller's order (by sellerHolderBoxId)<br/>
  * 6) computes the amount of change (including dexFee and checking it is at least `minDexFee`)<br/>
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
  * @param minDexFee lower limit of the reward for the DEX to be claimed from this transaction
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
        BoxOperations.createProver(ctx, storageFile.getPath, storagePass).build()
      }
      val sellerHolderBox = loggedStep(s"Loading seller's box (${sellerHolderBoxId.toString})", console) {
        ctx.getBoxesById(sellerHolderBoxId.toString).headOption
          .getOrElse(error(s"failed to load seller's box (${sellerHolderBoxId.toString})}"))
      }
      val sellerAddressPk = SellerContract.sellerPkFromTree(sellerHolderBox.getErgoTree)
        .getOrElse(error(s"cannot find seller's public key in seller order in box $sellerHolderBoxId"))
      val buyerHolderBox = loggedStep(s"Loading buyer's box (${buyerHolderBoxId.toString})", console) {
        ctx.getBoxesById(buyerHolderBoxId.toString).headOption
          .getOrElse(error(s"failed to load buyer's box (${buyerHolderBoxId.toString})}"))
      }
      val buyerAddressPk = BuyerContract.buyerPkFromTree(buyerHolderBox.getErgoTree)
        .getOrElse(error(s"cannot find buyer's public key in buyer order in box $buyerHolderBoxId"))

      if (sellerHolderBox.getTokens.isEmpty) {
        error(s"no tokens in seller's box (${sellerHolderBoxId.toString})")
      }
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
        error(s"found DEX fee = (claimable value - miner's fee) to be $dexFee, which is <= $minDexFee (minDexFee) ")
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

  override val parameters: Seq[CmdParameter] = Array(
    CmdParameter("storageFile", FilePType,
      "storage with secret key of the sender"),
    CmdParameter("storagePass", "Storage password", SecretStringPType,
      "password to access sender secret key in the storage", None,
      Some(PasswordInput), None),
    CmdParameter("sellerHolderBoxId", ErgoIdPType,
      "box id of the seller order"),
    CmdParameter("buyerHolderBoxId", ErgoIdPType,
      "box id of the buyer order"),
    CmdParameter("minDexFee", LongPType,
      "lower limit of the reward for the DEX to be claimed from this transaction")
  )

  override def createCmd(ctx: AppContext): Cmd = {
    val Seq(
      storageFile: File,
      pass: SecretString,
      sellerHolderBoxId: ErgoId,
      buyerHolderBoxId: ErgoId,
      minDexFee: Long
    ) = ctx.cmdParameters


    MatchOrdersCmd(ctx.toolConf, name, storageFile, pass, sellerHolderBoxId, buyerHolderBoxId, minDexFee)
  }
}

