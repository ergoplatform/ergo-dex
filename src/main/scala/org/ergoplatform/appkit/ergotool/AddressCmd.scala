package org.ergoplatform.appkit.ergotool

import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.{NetworkType, Address}

/** Given [[mnemonic]], [[mnemonicPass]] and [[network]] the command computes
  * the address of the given network type.
  *
  * The command do the following:<br>
  * 1) it uses (mnemonic, password) pair to generate master secret key (unambiguously for each such pair)<br>
  * 2) it extracts public key (pk) which corresponds to the generated secret key <br>
  * 3) it construct pay-to-public-key address for pk (see `org.ergoplatform.P2PKAddress`)<br>
  * 4) it prints the text representation (Base58 string) of P2PKAddress bytes.
  *
  * @param toolConf     configuration parameters to be used for operation
  * @param name         command name
  * @param network      [[NetworkType]] of the target network for which the address should be generated
  * @param mnemonic     secret phrase which is used to generate (private, public) key pair, of which
  *                     public key is used to generate the [[Address]]
  * @param mnemonicPass password which is used to additionally protect mnemonic
  * @see [[AddressCmd$]] descriptor of the `address` command
  */
case class AddressCmd(
    toolConf: ErgoToolConfig,
    name: String, network: NetworkType, mnemonic: String, mnemonicPass: Array[Char])
  extends Cmd {
  override def run(ctx: AppContext): Unit = {
    val address = Address.fromMnemonic(network, mnemonic, String.valueOf(mnemonicPass))
    ctx.console.print(address.toString)
  }
}

/** Descriptor and parser of the `address` command. */
object AddressCmd extends CmdDescriptor(
  name = "address", cmdParamSyntax = "testnet|mainnet <mnemonic>",
  description = "return address for a given mnemonic and password pair") {

  override def parseCmd(ctx: AppContext): Cmd = {
    val args = ctx.cmdArgs
    val network = if (args.length > 1) args(1) else usageError("network is not specified (mainnet or testnet)")
    val networkType = parseNetwork(network)
    val mnemonic = if (args.length > 2) args(2) else usageError("mnemonic is not specified")
    val mnemonicPass = readNewPassword("Mnemonic password> ", "Repeat mnemonic password> ")(ctx)
    AddressCmd(ctx.toolConf, name, networkType, mnemonic, mnemonicPass)
  }
}