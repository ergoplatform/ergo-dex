package org.ergoplatform.appkit.ergotool

import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.{Address, NetworkType, SecretString}

/** Given [[network]], [[mnemonic]], [[mnemonicPass]] and [[address]] checks that the address
  * belongs to the given network and corresponds to the given mnemonic and mnemonic password.
  *
  * Steps:<br/>
  * 1) The network, mnemonic and mnemonicPass parameters are used to compute new address (see [[AddressCmd]])<br/>
  * 2) if computed address equals to the given address then print `Ok`
  *    otherwise print `Error`
  *
  * @param network      network type
  * @param mnemonic     mnemonic phrase
  * @param mnemonicPass mnemonic password
  * @param address      address to check
  */
case class CheckAddressCmd(
    toolConf: ErgoToolConfig,
    name: String,
    network: NetworkType,
    mnemonic: SecretString,
    mnemonicPass: SecretString,
    address: Address) extends Cmd {

  override def run(ctx: AppContext): Unit = {
    val computedAddress = Address.fromMnemonic(network, mnemonic, mnemonicPass)
    val computedNetwork = computedAddress.getNetworkType
    val okNetwork = computedNetwork == network
    val res = if (okNetwork && computedAddress == address) "Ok" else s"Error"
    ctx.console.print(res)
  }

}

object CheckAddressCmd extends CmdDescriptor(
  name = "checkAddress", cmdParamSyntax = "testnet|mainnet <mnemonic> <address>",
  description = "Check the given mnemonic and password pair correspond to the given address") {

  override def parseCmd(ctx: AppContext): Cmd = {
    val args = ctx.cmdArgs
    val network = if (args.length > 1) args(1) else error("network type is not specified")
    val networkType = network match {
      case "testnet" => NetworkType.TESTNET
      case "mainnet" => NetworkType.MAINNET
      case _ => error(s"Invalid network type $network")
    }
    val mnemonic = if (args.length > 2) SecretString.create(args(2)) else error("mnemonic is not specified")
    val address = Address.create(if (args.length > 3) args(3) else error("address is not specified"))
    if (networkType != address.getNetworkType)
      error(s"Network type of the address ${address.getNetworkType} don't match expected $networkType")
    val mnemonicPass = ctx.console.readPassword("Mnemonic password> ")
    CheckAddressCmd(ctx.toolConf, name, networkType, mnemonic, mnemonicPass, address)
  }
}