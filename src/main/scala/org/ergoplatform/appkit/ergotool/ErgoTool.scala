package org.ergoplatform.appkit.ergotool

import org.ergoplatform.appkit.{ErgoClient, RestApiErgoClient, NetworkType}

import scala.util.control.NonFatal
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.console.Console

import scala.collection.mutable.ArrayBuffer

/** ErgoTool implementation, contains main entry point of the console application.
  *
  * @see instructions in README to generate native executable
  */
object ErgoTool {
  /** Commands supported by this application. */
  val commands: Map[String, CmdDescriptor] = Array(
    AddressCmd, MnemonicCmd, CheckAddressCmd,
    ListAddressBoxesCmd,
    CreateStorageCmd, ExtractStorageCmd, SendCmd
    ).map(c => (c.name, c)).toMap

  /** Options supported by this application */
  val options: Seq[CmdOption] = Array(ConfigOption, DryRunOption)

  /** Main entry point of console application. */
  def main(args: Array[String]): Unit = {
    val console = Console.instance
    run(args, console, { ctx =>
      RestApiErgoClient.create(ctx.apiUrl, ctx.networkType, ctx.apiKey)
    })
  }

  def run(args: Seq[String], console: Console, clientFactory: AppContext => ErgoClient): Unit = {
    try {
      val (cmdOptions, cmdArgs) = parseOptions(args)
      if (cmdArgs.isEmpty) sys.error(s"Please specify command name and parameters.")
      val toolConf = loadConfig(cmdOptions)
      val ctx = AppContext(args, console, cmdOptions, cmdArgs, toolConf, clientFactory)
      val cmd = parseCmd(ctx)
      cmd.run(ctx)
    }
    catch { case NonFatal(t) =>
      console.println(t.getMessage)
      printUsage(console)
    }
  }

  def parseOptions(args: Seq[String]): (Map[String, String], Seq[String]) = {
    var resOptions = Map.empty[String, String]
    val resArgs: ArrayBuffer[String] = ArrayBuffer.empty
    resArgs ++= args.toArray.clone()
    for (o <- options) {
      val pos = resArgs.indexOf(o.cmdText)
      if (pos != -1) {
        if (o.isFlag) {
          resOptions = resOptions + (o.name -> "true")
        } else {
          resOptions = resOptions + (o.name -> resArgs(pos + 1))
          resArgs.remove(pos + 1) // remove option value
        }
        resArgs.remove(pos)     // remove option name
      }
    }
    (resOptions, resArgs)
  }

  def loadConfig(cmdOptions: Map[String, String]): ErgoToolConfig = {
    val configFile = cmdOptions.getOrElse(ConfigOption.name, "ergo_tool_config.json")
    val toolConf = ErgoToolConfig.load(configFile)
    toolConf
  }

  def parseCmd(ctx: AppContext): Cmd = {
    val cmdName = ctx.cmdArgs(0)
    commands.get(cmdName) match {
      case Some(c) => c.parseCmd(ctx)
      case _ =>
        sys.error(s"Unknown command: $cmdName")
    }
  }

  def printUsage(console: Console): Unit = {
    val actions = commands.toSeq.sortBy(_._1).map { case (name, c) =>
      s"""  $name ${c.cmdParamSyntax}\n\t${c.description}""".stripMargin
    }.mkString("\n")
    val options = ErgoTool.options.sortBy(_.name).map(_.helpString).mkString("\n")
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
