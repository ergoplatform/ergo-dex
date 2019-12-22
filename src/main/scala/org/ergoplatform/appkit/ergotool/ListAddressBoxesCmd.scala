package org.ergoplatform.appkit.ergotool

import org.ergoplatform.appkit.JavaHelpers._
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.{ErgoClient, Address, InputBox}
import org.ergoplatform.appkit.ergotool.ErgoTool.RunContext

case class ListAddressBoxesCmd(toolConf: ErgoToolConfig, name: String, address: String, limit: Int) extends Cmd with RunWithErgoClient {
  override def runWithClient(ergoClient: ErgoClient, runCtx: RunContext): Unit = {
    val res: String = ergoClient.execute(ctx => {
      val boxes = ctx.getUnspentBoxesFor(Address.create(address)).convertTo[IndexedSeq[InputBox]]
      val lines = boxes.take(this.limit).map(b => b.toJson(true)).mkString("[", ",\n", "]")
      lines
    })
    runCtx.console.print(res)
  }
}
object ListAddressBoxesCmd extends CmdDescriptor(
  name = "listAddressBoxes", cmdParamSyntax = "address [<limit>=10]",
  description = "list top <limit=10> confirmed unspent boxes owned by the given <address>") {

  override def parseCmd(ctx: RunContext): Cmd = {
    val args = ctx.cmdArgs
    val address = if (args.length > 1) args(1) else error(s"address is not specified")
    val limit = if (args.length > 2) args(2).toInt else 10
    ListAddressBoxesCmd(ctx.toolConf, name, address, limit)
  }
}
