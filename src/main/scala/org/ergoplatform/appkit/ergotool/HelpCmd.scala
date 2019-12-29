package org.ergoplatform.appkit.ergotool

import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.{NetworkType, Address}

/** Prints usage help for the given command name.
  *
  * @param askedCmd command name which usage help should be printed
  * @throws CmdException if `askedCmd` is not valid command name
  * @see [[HelpCmd$]] descriptor of the `help` command
  */
case class HelpCmd(toolConf: ErgoToolConfig, name: String, askedCmd: String) extends Cmd {
  override def run(ctx: AppContext): Unit = {
    ErgoTool.commands.get(askedCmd) match {
      case Some(cmd) => cmd.printUsage(ctx.console)
      case _ => error(s"Help not found. Unknown command: $askedCmd")
    }
  }
}
/** Descriptor and parser of the `help` command. */
object HelpCmd extends CmdDescriptor(
  name = "help", cmdParamSyntax = "<commandName>",
  description = "prints usage help for a command") {

  override def parseCmd(ctx: AppContext): Cmd = {
    val args = ctx.cmdArgs
    val askedCmd =
      if (args.length > 1) args(1)
      else usageError("command name is not specified (run ergo-tool without arguments to list commands)")
    HelpCmd(ctx.toolConf, name, askedCmd)
  }
}
