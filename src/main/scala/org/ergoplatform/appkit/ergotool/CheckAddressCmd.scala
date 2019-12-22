package org.ergoplatform.appkit.ergotool

import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.{NetworkType, Address}
import org.ergoplatform.appkit.ergotool.ErgoTool.RunContext

case class CheckAddressCmd(toolConf: ErgoToolConfig, name: String, network: NetworkType, mnemonic: String, mnemonicPass: Array[Char], address: Address) extends Cmd {
  override def run(ctx: RunContext): Unit = {
    val addressComputed = Address.fromMnemonic(network, mnemonic, String.valueOf(mnemonicPass))
    val res = if (addressComputed == address) "Ok"
              else s"$addressComputed != $address"
    ctx.console.print(res)
  }
}

object CheckAddressCmd extends CmdDescriptor(
  name = "checkAddress", cmdParamSyntax = "testnet|mainnet <mnemonic> <address>",
  description = "Check the given mnemonic and password pair correspond to the given address") {

  override def parseCmd(ctx: RunContext): Cmd = {
    val args = ctx.cmdArgs
    val network = if (args.length > 1) args(1) else error("network type is not specified")
    val networkType = network match {
      case "testnet" => NetworkType.TESTNET
      case "mainnet" => NetworkType.MAINNET
      case _ => error(s"Invalid network type $network")
    }
    val mnemonic = if (args.length > 2) args(2) else error("mnemonic is not specified")
    val address = Address.create(if (args.length > 3) args(3) else error("address is not specified"))
    if (networkType != address.getNetworkType)
      error(s"Network type of the address ${address.getNetworkType} don't match expected $networkType")
    val mnemonicPass = ctx.console.readPassword("Mnemonic password> ")
    CheckAddressCmd(ctx.toolConf, name, networkType, mnemonic, mnemonicPass, address)
  }
}