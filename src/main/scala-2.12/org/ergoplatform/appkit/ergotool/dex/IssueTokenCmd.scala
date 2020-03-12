package org.ergoplatform.appkit.ergotool.dex

import java.io.File

import org.ergoplatform.appkit.Parameters.MinFee
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.ergotool.dex.CreateBuyOrderCmd.name
import org.ergoplatform.appkit.ergotool.{CmdParameter, FilePType, LongPType, RunWithErgoClient, ErgoIdPType, Cmd, SecretStringPType, IntPType, StringPType, CmdDescriptor, AppContext}

/** Issues a new token
  * following the Assets standard [[https://github.com/ergoplatform/eips/blob/master/eip-0004.md]]
  *
  * Steps:<br/>
  * 1) request storage password from the user<br/>
  * 2) read storage file, unlock using password and get secret<br/>
  * 3) get master public key and compute sender's address<br/>
  * 4) load available coins belonging to the sender's address<br/>
  * 5) select coins to cover ergAmount, compute transaction fee and amount of change<br/>
  * 7) create an output box with ergAmount and tokenAmount using the box if of the first input box as token id <br/>
  * 8) create and sign (using secret key) the transaction<br/>
  * 9) if no `--dry-run` option is specified, send the transaction to the network<br/>
  *    otherwise skip sending<br/>
  * 10) serialize transaction to Json and print to the console<br/>
  *
  * @param storageFile storage with secret key of the sender
  * @param storagePass password to access sender secret key in the storage
  * @param ergAmount   NanoERG amount to put in box with issued tokens
  * @param tokenAmount amount of tokens to issue
  * @param tokenName   token verbose name (UTF-8 representation)
  * @param tokenDesc   token description (UTF-8 representation)
  * @param tokenNumberOfDecimals number or decimals
  */
case class IssueTokenCmd(toolConf: ErgoToolConfig,
                         name: String,
                         storageFile: File,
                         storagePass: SecretString,
                         ergAmount: Long,
                         tokenAmount: Long,
                         tokenName: String,
                         tokenDesc: String,
                         tokenNumberOfDecimals: Int) extends Cmd with RunWithErgoClient {

  override def runWithClient(ergoClient: ErgoClient, runCtx: AppContext): Unit = {
    val console = runCtx.console
    ergoClient.execute(ctx => {
      val senderProver = loggedStep("Creating prover", console) {
        BoxOperations.createProver(ctx, storageFile.getPath, storagePass).build()
      }
      val sender = senderProver.getAddress
      val unspent = loggedStep(s"Loading unspent boxes from at address $sender", console) {
        ctx.getUnspentBoxesFor(sender)
      }
      // id of the issued token has to be the same as the box id of the first input box
      // see https://github.com/ergoplatform/eips/blob/master/eip-0004.md
      val boxesToSpend = BoxOperations.selectTop(unspent, ergAmount + MinFee)
      val token = new ErgoToken(boxesToSpend.get(0).getId, tokenAmount)
      val txB = ctx.newTxBuilder
      val newBox = txB.outBoxBuilder
        .value(ergAmount)
        .mintToken(token, tokenName,tokenDesc, tokenNumberOfDecimals)
        .contract(ErgoContracts.sendToPK(ctx, sender))
        .build()
      val tx = txB
        .boxesToSpend(boxesToSpend).outputs(newBox)
        .fee(MinFee)
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


object IssueTokenCmd extends CmdDescriptor(
  name = "dex:IssueToken", cmdParamSyntax = "<wallet file> <ergAmount> <tokenAmount> <tokenName> <tokenDesc> <tokenNumberOfDecimals",
  description = "issue a token with given <tokenName>, <tokenAmount>, <tokenDesc>, <tokenNumberOfDecimals> and <ergAmount> " +
    "with the given <wallet file> to sign transaction (requests storage password)") {

  override val parameters: Seq[CmdParameter] = Array(
    CmdParameter("storageFile", FilePType,
      "storage with secret key of the sender"),
    CmdParameter("storagePass", SecretStringPType,
      "password to access sender secret key in the storage", None,
      Some(ctx => ctx.console.readPassword("Storage password>"))),
    CmdParameter("ergAmount", LongPType,
      "NanoERG amount to put in box with issued tokens"),
    CmdParameter("tokenAmount", LongPType,
      "Amount of tokens to issue"),
    CmdParameter("tokenName", StringPType,
      "token verbose name (UTF-8 representation)"),
    CmdParameter("tokenDesc", StringPType,
      "token description (UTF-8 representation)"),
    CmdParameter("tokenNumberOfDecimals", IntPType,
      "number or decimals")
  )

  override def createCmd(ctx: AppContext): Cmd = {
    val Seq(
      storageFile: File,
      pass: SecretString,
      ergAmount: Long,
      tokenAmount: Long,
      tokenName: String,
      tokenDesc: String,
      tokenNumberOfDecimals: Int
    ) = ctx.cmdParameters
    IssueTokenCmd(
      ctx.toolConf, name, storageFile, pass, ergAmount, tokenAmount, tokenName,
      tokenDesc, tokenNumberOfDecimals)
  }

}
