package org.ergoplatform.appkit.ergotool

import org.ergoplatform.appkit.cli._
import org.ergoplatform.appkit.commands._
import org.ergoplatform.appkit.{ErgoClient, RestApiErgoClient}

import scala.util.control.NonFatal
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.console.Console
import org.ergoplatform.appkit.ergotool.dex.{CancelOrderCmd, CreateBuyOrderCmd, CreateSellOrderCmd, IssueTokenCmd, ListMatchingOrdersCmd, ListMyOrdersCmd, MatchOrdersCmd}
import org.ergoplatform.appkit.ergotool.dex.ShowOrderBookCmd

/** ErgoTool implementation, contains main entry point of the console application.
  *
  * @see instructions in README to generate native executable
  */
object ErgoTool {
  /** Commands supported by this application. */
  val commands: Map[String, CmdDescriptor] = Array(
    HelpCmd,
    AddressCmd, MnemonicCmd, CheckAddressCmd,
    ListAddressBoxesCmd,
    CreateStorageCmd, ExtractStorageCmd, SendCmd,
    CreateSellOrderCmd, CreateBuyOrderCmd, MatchOrdersCmd,
    ListMatchingOrdersCmd, IssueTokenCmd, CancelOrderCmd, ListMyOrdersCmd, ShowOrderBookCmd
    ).map(c => (c.name, c)).toMap

  /** Main entry point of console application. */
  def main(args: Array[String]): Unit = {
    val console = Console.instance
    run(args, console, clientFactory = { ctx =>
      RestApiErgoClient.create(ctx.apiUrl, ctx.networkType, ctx.apiKey)
    })
  }

  /** Main application runner<br/>
    * 1) Parse options from command line (see [[ErgoTool.parseOptions]]<br/>
    * 2) load config file<br/>
    * 3) create [[AppContext]]<br/>
    * 4) parse command parameters, create and execute command
    */
  private[ergotool] def run(args: Seq[String], console: Console, clientFactory: AppContext => ErgoClient): Unit = {
    try {
      val (cmdOptions, cmdArgs) = CmdLineParser.parseOptions(args)
      if (cmdArgs.isEmpty) usageError(s"Please specify command name and parameters.", None)
      val toolConf = loadConfig(cmdOptions)
      val ctx = AppContext(args, console, cmdOptions, cmdArgs(0), cmdArgs.drop(1), toolConf, clientFactory)
      val cmd = parseCmd(ctx)
      try {
        cmd.run(ctx)
      }
      catch {
        case e: CmdException => throw e
        case e: UsageException => throw e
        case NonFatal(t) =>
          t.printStackTrace()
          throw CmdException(s"Error executing command $cmd:\n $t", cmd, t)
      }
    }
    catch {
      case ue: UsageException =>
        console.println(ue.getMessage)
        printUsage(console, ue.cmdDescOpt)
      case NonFatal(t) =>
        console.println(t.getMessage)
    }
  }

  /** Loads `ErgoToolConfig` from a file specified either by command line option `--conf` or from
    * the default file location */
  def loadConfig(cmdOptions: Map[String, String]): ErgoToolConfig = {
    val configFile = cmdOptions.getOrElse(ConfigOption.name, "ergo_tool_config.json")
    val toolConf = ErgoToolConfig.load(configFile)
    toolConf
  }

  /** Parses the command parameters form the command line using [[AppContext]] and returns a new instance
    * of the command configured with the parsed parameters.
    */
  def parseCmd(ctx: AppContext): Cmd = {
    commands.get(ctx.cmdName) match {
      case Some(c) =>
        val args = ctx.cmdArgs
        val params = c.parseArgs(ctx, args)
        c.createCmd(ctx.withCmdParameters(params))
      case _ =>
        usageError(s"Unknown command: ${ctx.cmdName}", None)
    }
  }

  /** Prints usage help to the console for the given command (if defined).
    * If the command is not defined, then print basic usage info about all commands.
    */
  def printUsage(console: Console, cmdDescOpt: Option[CmdDescriptor]): Unit = {
    cmdDescOpt match {
      case Some(desc) =>
        desc.printUsage(console)
      case _ =>
        val actions = commands.toSeq.sortBy(_._1).map { case (name, c) =>
          s"""  $name ${c.cmdParamSyntax}\n\t${c.description}""".stripMargin
        }.mkString("\n")
        val options = CmdOption.options.sortBy(_.name).map(_.helpString).mkString("\n")
        val msg =
          s"""
            |Usage:
            |ergotool [options] action [action parameters]
            |
            |Available actions:
            |$actions
            |
            |Options:
            |$options
          """.stripMargin
        console.println(msg)
    }
  }

}

