package org.ergoplatform.appkit.ergotool

import org.ergoplatform.appkit.JavaHelpers._
import org.ergoplatform.appkit.cli.AppContext
import org.ergoplatform.appkit.commands.{CmdParameter, AddressPType, RunWithErgoClient, Cmd, CmdDescriptor}
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.{ErgoClient, Address, InputBox}

/** Lists unspent outputs (aka boxes or coins) belonging to the given address.
  *
  * @param address string encoding of the address
  * @param limit   limit the size of the list (optional, default is 10)
  */
case class ListAddressBoxesCmd(toolConf: ErgoToolConfig, name: String, address: Address, limit: Int) extends Cmd with RunWithErgoClient {
  override def runWithClient(ergoClient: ErgoClient, runCtx: AppContext): Unit = {
    val res: String = ergoClient.execute(ctx => {
      val boxes = ctx.getUnspentBoxesFor(address)
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
  name = "listAddressBoxes", cmdParamSyntax = "[--limit-list <limit>] <address>",
  description = "list top <limit> confirmed unspent boxes owned by the given <address>") {

  override val parameters: Seq[CmdParameter] = Array(
    CmdParameter("address", AddressPType, "string encoding of the address")
  )

  override def createCmd(ctx: AppContext): Cmd = {
    val Seq(address: Address) = ctx.cmdParameters
    val limit = ctx.cmdOptions.get("limit-list").fold(10)(_.toInt)
    ListAddressBoxesCmd(ctx.toolConf, name, address, limit)
  }
}
