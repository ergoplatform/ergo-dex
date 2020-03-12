package org.ergoplatform.appkit.ergotool

import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.{Address, NetworkType, SecretString}
import org.ergoplatform.appkit.console.Console._

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
case class AddressCmd
( toolConf: ErgoToolConfig,
  name: String, network: NetworkType, mnemonic: SecretString, mnemonicPass: SecretString)
  extends Cmd {
  override def run(ctx: AppContext): Unit = {
    val address = Address.fromMnemonic(network, mnemonic, mnemonicPass)
    ctx.console.print(address.toString)
  }
}

/** Descriptor and parser of the `address` command. */
object AddressCmd extends CmdDescriptor(
  name = "address", cmdParamSyntax = "testnet|mainnet <mnemonic>",
  description = "return address for a given mnemonic and password pair") {

  override val parameters: Seq[CmdParameter] = Array(
    CmdParameter("network", NetworkPType,
      "[[NetworkType]] of the target network for which the address should be generated"),
    CmdParameter("mnemonic", SecretStringPType,
      """secret phrase which is used to generate (private, public) key pair, of which
        |public key is used to generate the [[Address]]""".stripMargin),
    CmdParameter("mnemonicPass", SecretStringPType,
      "password which is used to additionally protect mnemonic", None,
      Some(ctx => readNewPassword("Mnemonic password> ", "Repeat mnemonic password> ")(ctx)))
  )

  override def createCmd(ctx: AppContext): Cmd = {
    val Seq(networkType: NetworkType, mnemonic: SecretString, mnemonicPass: SecretString) = ctx.cmdParameters
    AddressCmd(ctx.toolConf, name, networkType, mnemonic, mnemonicPass)
  }
}