package org.ergoplatform.appkit.ergotool

import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit._
import java.io.File
import org.ergoplatform.appkit.Parameters.MinFee
import Utils._

/** Creates and sends a new transaction to transfer Ergs from one address to another.
  *
  * Steps:<br/>
  * 1) request storage password from the user<br/>
  * 2) read storage file, unlock using password and get secret<br/>
  * 3) get master public key and compute sender's address<br/>
  * 4) load available coins belonging to the sender's address<br/>
  * 5) select coins to cover amountToSend, compute transaction fee and amount of change<br/>
  * 6) create and sign (using secret key) transaction<br/>
  * 7) if no `--dry-run` option is specified, send the transaction to the network<br/>
  *    otherwise skip sending<br/>
  * 8) serialize transaction to Json and print to the console<br/>
  *
  * @param storageFile storage with secret key of the sender
  * @param storagePass password to access sender secret key in the storage
  * @param recipient    address of the recepient of the transfer
  * @param amountToSend amount of NanoErg to transfer to recipient
  */
case class SendCmd(toolConf: ErgoToolConfig, name: String, storageFile: File, storagePass: Array[Char], recipient: Address, amountToSend: Long) extends Cmd with RunWithErgoClient {
  override def runWithClient(ergoClient: ErgoClient, runCtx: AppContext): Unit = {
    val console = runCtx.console
    ergoClient.execute(ctx => {
      val senderProver = loggedStep("Creating prover", console) {
        BoxOperations.createProver(ctx, storageFile.getPath, String.valueOf(storagePass))
      }
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
      if (runCtx.isDryRun) {
        val txJson = signed.toJson(true)
        console.println(s"Tx: $txJson")
      }
      else {
        val txId = loggedStep(s"Sending the transaction", console) {
          ctx.sendTransaction(signed)
        }
        console.println(s"Server returned tx id: $txId")
      }
    })
  }
}
object SendCmd extends CmdDescriptor(
  name = "send", cmdParamSyntax = "<storageFile> <recipientAddr> <amountToSend>",
  description = "send the given <amountToSend> to the given <recipientAddr> using \n " +
      "the given <storageFile> to sign transaction (requests storage password)") {

  override def parseCmd(ctx: AppContext): Cmd = {
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

