package org.ergoplatform.appkit.ergotool

import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.Mnemonic

case class MnemonicCmd(toolConf: ErgoToolConfig, name: String) extends Cmd {
  override def run(ctx: AppContext): Unit = {
    val m = Mnemonic.generateEnglishMnemonic()
    ctx.console.print(m)
  }
}

object MnemonicCmd extends CmdDescriptor(
  name = "mnemonic", cmdParamSyntax = "",
  description = "generate new mnemonic phrase using english words and default cryptographic strength") {

  override def parseCmd(ctx: AppContext): Cmd = {
    MnemonicCmd(ctx.toolConf, name)
  }
}