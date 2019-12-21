package org.ergoplatform.appkit.examples.ergotool

import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.Mnemonic
import org.ergoplatform.appkit.examples.ergotool.ErgoTool.RunContext

case class MnemonicCmd(toolConf: ErgoToolConfig, name: String) extends Cmd {
  override def run(ctx: RunContext): Unit = {
    val m = Mnemonic.generateEnglishMnemonic()
    ctx.console.print(m)
  }
}

object MnemonicCmd extends CmdFactory(
  name = "mnemonic", cmdParamSyntax = "",
  description = "generate new mnemonic phrase using english words and default cryptographic strength") {

  override def parseCmd(ctx: RunContext): Cmd = {
    MnemonicCmd(ctx.toolConf, name)
  }
}