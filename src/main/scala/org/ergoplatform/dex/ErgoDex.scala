package org.ergoplatform.dex

import org.ergoplatform.appkit.RestApiErgoClient
import org.ergoplatform.appkit.commands.CmdDescriptor
import org.ergoplatform.appkit.cli.{Console, CliApplication}

/** ErgoTool implementation, contains main entry point of the console application.
  *
  * @see instructions in README to generate native executable
  */
object ErgoDex extends CliApplication {
  /** Commands supported by this application. */
  override def commands: Seq[CmdDescriptor] = super.commands ++ Array(
    CreateSellOrderCmd, CreateBuyOrderCmd, MatchOrdersCmd,
    ListMatchingOrdersCmd, IssueTokenCmd, CancelOrderCmd, ListMyOrdersCmd, ShowOrderBookCmd
  )

  /** Main entry point of console application. */
  def main(args: Array[String]): Unit = {
    val console = Console.instance
    run(args, console, clientFactory = { ctx =>
      RestApiErgoClient.create(ctx.apiUrl, ctx.networkType, ctx.apiKey)
    })
  }
}
