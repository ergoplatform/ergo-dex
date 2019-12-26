package org.ergoplatform.appkit.ergotool

import org.ergoplatform.appkit.{ErgoClient, RestApiErgoClient, NetworkType}

import scala.util.control.NonFatal
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.console.Console
import org.ergoplatform.appkit.ergotool.ErgoTool.{options, usageError}

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
    run(args, console, clientFactory = { ctx =>
      RestApiErgoClient.create(ctx.apiUrl, ctx.networkType, ctx.apiKey)
    })
  }

  /** Main application runner
    * 1) Parse options from command line (see [[ErgoTool.parseOptions]]
    * 2) load config file
    * 3) create [[AppContext]]
    * 4) parse command parameters, create and execute command
    */
  private[ergotool] def run(args: Seq[String], console: Console, clientFactory: AppContext => ErgoClient): Unit = {
    try {
      val (cmdOptions, cmdArgs) = CmdLineParser.parseOptions(args)
      if (cmdArgs.isEmpty) usageError(s"Please specify command name and parameters.", None)
      val toolConf = loadConfig(cmdOptions)
      val ctx = AppContext(args, console, cmdOptions, cmdArgs, toolConf, clientFactory)
      val cmd = parseCmd(ctx)
      cmd.run(ctx)
    }
    catch {
      case ue: UsageException =>
        console.println(ue.getMessage)
        printUsage(console, ue.cmdDescOpt)
      case NonFatal(t) =>
        console.println(t.getMessage)
    }
  }

  /** Should be used by ErgoTool to report usage errors */
  private[ergotool] def usageError(msg: String, cmdDescOpt: Option[CmdDescriptor]) = throw UsageException(msg, cmdDescOpt)

  /** Loads [[ErgoToolConfig]] from a file specified either by command line option `--conf` or from
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
    val cmdName = ctx.cmdArgs(0)
    commands.get(cmdName) match {
      case Some(c) =>
        c.parseCmd(ctx)
      case _ =>
        usageError(s"Unknown command: $cmdName", None)
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

}

object CmdLineParser {
  /** Extracts options like `--conf myconf.json` from the command line.
    * The command line is parsed using the following simple algorithm:
    * 1) the whole line starting from executable file name is split by whitespace into parts
    * which are passed is as `args` of this method (this is done by java app launcher)
    * 2) the sequence of args is traversed and each starting with `--` is parsed into option
    * name-value pair and extracted from original args sequence. Any error in option parsing is
    * reported via [[ErgoTool.usageError]]
    * 3) Any option with [[CmdOption.isFlag]] == true is parsed without value (the value is not
    * expected to be on the command line)
    * 4) After the options with their values are extracted then everything that remains in `args` is
    * interpreted as command parameters
    * @param args the command line split by whitespace into parts
    */
  def parseOptions(args: Seq[String]): (Map[String, String], Seq[String]) = {
    var resOptions = Map.empty[String, String]

    // clone args into mutable buffer to extract option
    // what will remain will be returned as parameters
    val resArgs: ArrayBuffer[String] = ArrayBuffer.empty
    resArgs ++= args.toArray.clone()

    // scan until the end of the buffer
    var i = 0
    while (i < resArgs.length) {
      val arg = resArgs(i)
      if (arg.startsWith(CmdOption.Prefix)) {
        // this must be an option name
        val name = arg.drop(CmdOption.Prefix.length)
        options.find(_.name == name) match {
          case Some(o) =>
            if (o.isFlag) {
              resOptions = resOptions + (o.name -> "true")
            } else {
              if (i + 1 >= resArgs.length)
                usageError(s"Value for the non-flag command ${o.name} is not provided: unexpected end of command line.", None)
              resOptions = resOptions + (o.name -> resArgs(i + 1))
              resArgs.remove(i + 1) // remove option value
            }
          case _ =>
            usageError(s"Unknown option name: $arg", None)
        }
        // option parsed, remove it from buffer
        resArgs.remove(i)
      } else {
        // this is parameter, leave it in buffer
        i += 1
      }
    }
    (resOptions, resArgs)
  }
}
