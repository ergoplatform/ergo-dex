package org.ergoplatform.appkit.examples.ergotool

import org.ergoplatform.appkit.JavaHelpers._
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.{ErgoClient, InputBox}

import org.ergoplatform.appkit.examples.ergotool.ErgoTool.RunContext

case class ListWalletBoxesCmd(toolConf: ErgoToolConfig, name: String, limit: Int) extends Cmd with RunWithErgoClient {
  override def runWithClient(ergoClient: ErgoClient, runCtx: RunContext): Unit = {
    val res: String = ergoClient.execute(ctx => {
      val wallet = ctx.getWallet
      val boxes = wallet.getUnspentBoxes(0).get().convertTo[IndexedSeq[InputBox]]
      val lines = boxes.take(this.limit).map(b => b.toJson(true)).mkString("[", ",\n", "]")
      lines
    })
    runCtx.console.print(res)
  }
}

object ListWalletBoxesCmd extends CmdFactory(
  name = "listWalletBoxes", cmdParamSyntax = "<limit>",
  description = "list top <limit> confirmed wallet boxes which can be spent") {

  override def parseCmd(ctx: RunContext): Cmd = {
    val args = ctx.cmdArgs
    val limit = if (args.length > 1) args(1).toInt else 10
    ListWalletBoxesCmd(ctx.toolConf, name, limit)
  }
}