package org.ergoplatform.appkit.ergotool

import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.{NetworkType, Address}

case class AddressCmd(
    toolConf: ErgoToolConfig,
    name: String, network: NetworkType, mnemonic: String, mnemonicPass: Array[Char])
  extends Cmd {
  override def run(ctx: AppContext): Unit = {
    val address = Address.fromMnemonic(network, mnemonic, String.valueOf(mnemonicPass))
    ctx.console.print(address.toString)
  }
}

object AddressCmd extends CmdDescriptor(
  name = "address", cmdParamSyntax = "testnet|mainnet <mnemonic>",
  description = "return address for a given mnemonic and password pair") {

  override def parseCmd(ctx: AppContext): Cmd = {
    val args = ctx.cmdArgs
    val network = if (args.length > 1) args(1) else error("network is not specified (mainnet or testnet)")
    val networkType = parseNetwork(network)
    val mnemonic = if (args.length > 2) args(2) else error("mnemonic is not specified")
    val mnemonicPass = ctx.console.readPassword("Mnemonic password> ")
    AddressCmd(ctx.toolConf, name, networkType, mnemonic, mnemonicPass)
  }
}