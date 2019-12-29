package org.ergoplatform.appkit.ergotool

import java.io.File

import org.ergoplatform.appkit.Parameters.MinFee
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.console.Console
import org.ergoplatform.appkit.impl.ErgoTreeContract
import sigmastate.eval.CSigmaProp
import sigmastate.verification.contract.AssetsAtomicExchangeCompilation
import special.sigma.SigmaProp
import sigmastate.eval.Extensions._

case class AssetsAtomicExchangeBuyerCmd(toolConf: ErgoToolConfig,
                                        name: String,
                                        storageFile: File,
                                        storagePass: Array[Char],
                                        buyer: Address,
                                        deadline: Int,
                                        ergAmount: Long,
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
      import sigmastate.verified.VerifiedTypeConverters._
      val buyerPkProp: sigmastate.verified.SigmaProp = CSigmaProp(buyer.getPublicKey).asInstanceOf[SigmaProp]
      val verifiedContract = AssetsAtomicExchangeCompilation.buyerContractInstance(deadline, token.getId.getBytes.toColl, token.getValue, buyerPkProp)

      val senderProver = loggedStep("Creating prover", console) {
        BoxOperations.createProver(ctx, storageFile.getPath, String.valueOf(storagePass))
      }
      val sender = senderProver.getAddress
      val unspent = loggedStep(s"Loading unspent boxes from at address $sender", console) {
        ctx.getUnspentBoxesFor(sender)
      }
      val outboxValue = ergAmount
      val boxesToSpend = BoxOperations.selectTop(unspent, MinFee + outboxValue)
      val txB = ctx.newTxBuilder
      val newBox = txB.outBoxBuilder
        .value(outboxValue)
        .contract(new ErgoTreeContract(verifiedContract.ergoTree))
        .build()
      val tx = txB
        .boxesToSpend(boxesToSpend).outputs(newBox)
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


object AssetsAtomicExchangeBuyerCmd extends CmdDescriptor(
  name = "AssetAtomicExchangeBuyer", cmdParamSyntax = "<wallet file> <buyerAddr> <deadline> <ergAmount> <tokenId> <tokenAmount>",
  description = "put a token buyer contract with given <tokenId> and <tokenAmount> to buy at given <ergPrice> price until given <deadline> with <buyerAddr> to be used for withdrawal(after the deadline) \n " +
    "with the given <wallet file> to sign transaction (requests storage password)") {

  override def parseCmd(ctx: AppContext): Cmd = {
    val args = ctx.cmdArgs
    val storageFile = new File(if (args.length > 1) args(1) else error("Wallet storage file path is not specified"))
    if (!storageFile.exists()) error(s"Specified wallet file is not found: $storageFile")
    val buyer = Address.create(if (args.length > 2) args(2) else error("buyer address is not specified"))
    val deadline = if (args.length > 3) args(3).toInt else error("deadline is not specified")
    val ergAmount = if (args.length > 4) args(4).toLong else error("ergAmount is not specified")
    val tokenId = if(args.length > 5) args(5) else error("tokenId is not specified")
    val tokenAmount = if(args.length > 6) args(6).toLong else error("tokenAmount is not specified")
    val token = new ErgoToken(tokenId, tokenAmount)
    val pass = ctx.console.readPassword("Storage password>")
    AssetsAtomicExchangeBuyerCmd(ctx.toolConf, name, storageFile, pass, buyer,
      deadline, ergAmount, token)
  }
}

