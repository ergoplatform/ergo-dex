package org.ergoplatform.appkit.ergotool

import java.io.File
import java.util

import org.ergoplatform.appkit.Parameters.MinFee
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.console.Console
import org.ergoplatform.appkit.impl.{ErgoTreeContract, ScalaBridge}
import sigmastate.eval.CSigmaProp
import sigmastate.verification.contract.AssetsAtomicExchangeCompilation
import special.sigma.SigmaProp

case class AssetsAtomicExchangeMatchCmd(toolConf: ErgoToolConfig,
                                        name: String,
                                        storageFile: File,
                                        storagePass: Array[Char],
                                        sellerHolderBoxId: ErgoId,
                                        buyerHolderBoxId: ErgoId,
                                        buyerAddress: Address,
                                        sellerAddress: Address,
                                        token: ErgoToken) extends Cmd with RunWithErgoClient {

  def loggedStep[T](msg: String, console: Console)(step: => T): T = {
    console.print(msg + "...")
    val res = step
    val status = if (res != null) "Ok" else "Error"
    console.println(s" $status")
    res
  }

  override def runWithClient(ergoClient: ErgoClient, runCtx: AppContext): Unit = {
    val console = runCtx.console
    ergoClient.execute(ctx => {
      val senderProver = loggedStep("Creating prover", console) {
        BoxOperations.createProver(ctx, storageFile.getPath, String.valueOf(storagePass))
      }
      val buyerHolderBox = loggedStep(s"Loading buyer's box (${buyerHolderBoxId.toString})", console) {
        ctx.getBoxesById(buyerHolderBoxId.toString).head
      }
      val sellerHolderBox = loggedStep(s"Loading seller's box (${sellerHolderBoxId.toString})", console) {
        ctx.getBoxesById(sellerHolderBoxId.toString).head
      }
      // TODO: where is the miner's fee?
      val txB = ctx.newTxBuilder
      val buyerTokensOutBox = txB.outBoxBuilder
        .value(sellerHolderBox.getValue)
        .contract(ctx.compileContract(
          ConstantsBuilder.create
            .item("recipientPk", buyerAddress.getPublicKey)
            .build(),
          "{ recipientPk }"))
        .tokens(token) // TODO: use new InputBox.getTokens? Only when complete match?
        .registers(ErgoValue.of(buyerHolderBoxId.getBytes))
        .build()
      val sellerErgsOutBox = txB.outBoxBuilder
        .value(buyerHolderBox.getValue)
        .contract(ctx.compileContract(
          ConstantsBuilder.create
            .item("recipientPk", sellerAddress.getPublicKey)
            .build(),
          "{ recipientPk }"))
        .registers(ErgoValue.of(sellerHolderBoxId.getBytes))
        .build()
      import Iso._
      val tx = txB
        .boxesToSpend(util.Arrays.asList(buyerHolderBox, sellerHolderBox)).outputs(buyerTokensOutBox, sellerErgsOutBox)
        .fee(Parameters.MinFee)
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

// TODO: add buyer and seller addresses
object AssetsAtomicExchangeMatchCmd extends CmdDescriptor(
  name = "AssetAtomicExchangeMatch", cmdParamSyntax = "<wallet file> <sellerBoxId> <buyerBoxId>  <ergPrice> <tokenId> <tokenAmount>",
  description = "match an existing token seller's contract (by <sellerBoxId>) and an existing buyer's contract (by <buyerBoxId) for specified token amount (by <tokenId> and <tokenAmount>) at given <ergPrice> price until with the given <wallet file> to sign transaction (requests storage password)") {

  override def parseCmd(ctx: AppContext): Cmd = {
    val args = ctx.cmdArgs
    val storageFile = new File(if (args.length > 1) args(1) else error("Wallet storage file path is not specified"))
    if (!storageFile.exists()) error(s"Specified wallet file is not found: $storageFile")
    val seller = Address.create(if (args.length > 2) args(2) else error("seller address is not specified"))
    val deadline = if (args.length > 3) args(3).toInt else error("deadline is not specified")
    val ergAmount = if (args.length > 4) args(4).toLong else error("ergPrice is not specified")
    val tokenId = if(args.length > 5) args(5) else error("tokenId is not specified")
    val tokenAmount = if(args.length > 6) args(6).toLong else error("tokenAmount is not specified")
    val token = new ErgoToken(tokenId, tokenAmount)
    val pass = ctx.console.readPassword("Storage password>")
    AssetsAtomicExchangeMatchCmd(ctx.toolConf, name, storageFile, pass, seller,
      deadline, ergAmount, token)
  }
}

