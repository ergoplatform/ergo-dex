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

case class AssetsAtomicExchangeSellerCmd(toolConf: ErgoToolConfig,
                                         name: String,
                                         storageFile: File,
                                         storagePass: Array[Char],
                                         seller: Address,
                                         deadline: Int,
                                         tokenPrice: Long,
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
      val sellerPkProp: sigmastate.verified.SigmaProp = CSigmaProp(seller.getPublicKey).asInstanceOf[SigmaProp]
      val verifiedContract = AssetsAtomicExchangeCompilation.sellerContractInstance(deadline,
        tokenPrice, sellerPkProp)

      val senderProver = loggedStep("Creating prover", console) {
        BoxOperations.createProver(ctx, storageFile.getPath, String.valueOf(storagePass))
      }
      val sender = senderProver.getAddress
      val unspent = loggedStep(s"Loading unspent boxes from at address $sender", console) {
        ctx.getUnspentBoxesFor(sender)
      }
      // TODO: add selectTopWithTokens or add tokens to selectTop
      val boxesToSpend = BoxOperations.selectTop(unspent, MinFee + 1)
      val txB = ctx.newTxBuilder
      val newBox = txB.outBoxBuilder
        .value(MinFee)
        .contract(new ErgoTreeContract(verifiedContract.ergoTree))
        .tokens(token)
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
object AssetsAtomicExchangeSellerCmd extends CmdDescriptor(
  name = "AssetAtomicExchangeSeller", cmdParamSyntax = "<wallet file> <sellerAddr> <deadline> <ergPrice> <tokenId> <tokenAmount>",
  description = "put a token seller contract with given <tokenId> and <tokenAmount> for sale at given <ergPrice> price until given <deadline> with <sellerAddr> to be used for withdrawal(after the deadline) \n " +
    "with the given <wallet file> to sign transaction (requests storage password)") {

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
    AssetsAtomicExchangeSellerCmd(ctx.toolConf, name, storageFile, pass, seller,
      deadline, ergAmount, token)
  }
}

