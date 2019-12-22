package org.ergoplatform.appkit.ergotool

import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit._
import java.io.File

import org.ergoplatform.appkit.Parameters.MinFee
import org.ergoplatform.appkit.console.Console
import org.ergoplatform.appkit.ergotool.ErgoTool.RunContext

case class SendCmd(toolConf: ErgoToolConfig, name: String, storageFile: File, storagePass: Array[Char], recipient: Address, amountToSend: Long) extends Cmd with RunWithErgoClient {
  def loggedStep[T](msg: String, console: Console)(step: => T): T = {
    console.print(msg + "...")
    val res = step
    val status = if (res != null) "Ok" else "Error"
    console.println(s" $status")
    res
  }

  override def runWithClient(ergoClient: ErgoClient, runCtx: RunContext): Unit = {
    val console = runCtx.console
    val res: String = ergoClient.execute(ctx => {
      val senderProver = loggedStep("Creating prover", console) {
        BoxOperations.createProver(ctx, storageFile.getPath, String.valueOf(storagePass))
      }
//      BoxOperations.send(ctx, sender, recipient, amountToSend)
      val sender = senderProver.getAddress
      val unspent = loggedStep(s"Loading unspent boxes from at address $sender", console) {
        ctx.getUnspentBoxesFor(sender)
      }
      val boxesToSpend = BoxOperations.selectTop(unspent, amountToSend + MinFee)
      val txB = ctx.newTxBuilder
      val newBox = txB.outBoxBuilder
        .value(amountToSend)
        .contract(ctx.compileContract(
          ConstantsBuilder.create
            .item("recipientPk", recipient.getPublicKey)
            .build(),
          "{ recipientPk }")).build()
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

      val txId = loggedStep(s"Sendng the transaction", console) {
        ctx.sendTransaction(signed)
      }
      txId
    })
    console.println(s"Server returned tx id: $res")
  }
}
object SendCmd extends CmdDescriptor(
  name = "send", cmdParamSyntax = "<wallet file> <recipientAddr> <amountToSend>",
  description = "send the given <amountToSend> to the given <recipientAddr> using \n " +
      "the given <wallet file> to sign transaction (requests storage password)") {

  override def parseCmd(ctx: RunContext): Cmd = {
    val args = ctx.cmdArgs
    val storageFile = new File(if (args.length > 1) args(1) else error("Wallet storage file path is not specified"))
    if (!storageFile.exists()) error(s"Specified wallet file is not found: $storageFile")
    val recipient = Address.create(if (args.length > 2) args(2) else error("recipient address is not specified"))
    val amountToSend = if (args.length > 3) args(3).toLong else error("amountToSend is not specified")
    if (amountToSend < MinFee) error(s"Please specify amount no less than $MinFee (MinFee)")
    val pass = ctx.console.readPassword("Storage password>")
    SendCmd(ctx.toolConf, name, storageFile, pass, recipient, amountToSend)
  }
}

