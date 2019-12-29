package org.ergoplatform.appkit.ergotool

import org.ergoplatform.appkit.JavaHelpers._
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.{ErgoClient, Address, InputBox}

/** Lists unspent outputs (aka boxes or coins) belonging to the given address.
  *
  * @param address string encoding of the address
  * @param limit   limit the size of the list (optional, default is 10)
  */
case class ListAddressBoxesCmd(toolConf: ErgoToolConfig, name: String, address: String, limit: Int) extends Cmd with RunWithErgoClient {
  override def runWithClient(ergoClient: ErgoClient, runCtx: AppContext): Unit = {
    val res: String = ergoClient.execute(ctx => {
      val boxes = ctx.getUnspentBoxesFor(Address.create(address))
        .convertTo[IndexedSeq[InputBox]]
        .take(this.limit)
      val lines = if (runCtx.isPrintJson) {
        boxes.map(b => b.toJson(false)).mkString("[", ",\n", "]")
      } else {
        "BoxId                                                             NanoERGs          \n" +
        boxes.map(b => s"${b.getId}  ${b.getValue}").mkString("\n")
      }
      lines
    })
    runCtx.console.print(res)
  }
}
object ListAddressBoxesCmd extends CmdDescriptor(
  name = "listAddressBoxes", cmdParamSyntax = "address [<limit>=10]",
  description = "list top <limit=10> confirmed unspent boxes owned by the given <address>") {

  override def parseCmd(ctx: AppContext): Cmd = {
    val args = ctx.cmdArgs
    val address = if (args.length > 1) args(1) else usageError(s"address is not specified")
    val limit = if (args.length > 2) args(2).toInt else 10
    ListAddressBoxesCmd(ctx.toolConf, name, address, limit)
  }
}
