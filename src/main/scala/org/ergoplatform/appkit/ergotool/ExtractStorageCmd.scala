package org.ergoplatform.appkit.ergotool

import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.{NetworkType, SecretStorage}
import org.ergoplatform.wallet.secrets.ExtendedSecretKeySerializer
import scorex.util.encode.Base16

/** Extracts secret data from encrypted storage file (e.g. created by [[CreateStorageCmd]]).
  *
  * Steps:<br/>
  * 1) request storage password ([[storagePass]])<br/>
  * 2) load encrypted data from storageFile into [[SecretStorage]] instance<br/>
  * 3) unlock the instance using storagePass and retrieve secret data from storage<br/>
  * 4) depending on the parameter `prop`:<br/>
  *   - "address" => get master key address for the given `network`<br/>
  *   - "masterKey" => serialie [[org.ergoplatform.wallet.secrets.ExtendedSecretKey]] into bytes<br/>
  *     and encode as base16<br/>
  *   - "secretKey" => get bytes of the secret key and encode as base16<br/>
  *   - "publicKey" => get bytes of the public key and encode as base16<br/>
  * 5) print obtained secret string to the console output
  *
  * Note, a storage file doesn't contain network type information, this the same storage can be used
  * to obtain address both from mainnet and from testnet.
  *
  * @param storageFile path to encrypted storage file
  * @param storagePass encryption password necessary to access storage file content
  * @param prop        name of the secret data stored in the file (e.g. [[PropAddress]])
  * @param network     network type
  */
case class ExtractStorageCmd(
    toolConf: ErgoToolConfig, name: String,
    storageFile: String, storagePass: Array[Char], prop: String, network: NetworkType) extends Cmd {
  import ExtractStorageCmd._
  override def run(ctx: AppContext): Unit = {
    val console = ctx.console
    val storage = SecretStorage.loadFrom(storageFile)
    storage.unlock(String.valueOf(storagePass))
    val secret = storage.getSecret
    prop match {
      case PropAddress =>
        console.println(storage.getAddressFor(network).toString)
      case PropMasterKey =>
        val secretStr = Base16.encode(ExtendedSecretKeySerializer.toBytes(secret))
        console.println(secretStr)
      case PropSecretKey =>
        val sk  = Base16.encode(secret.keyBytes)
        assert(sk == secret.key.w.toString(16), "inconsistent secret")
        console.println(sk)
      case PropPublicKey =>
        val pk = Base16.encode(secret.key.publicImage.pkBytes)
        console.println(pk)
      case _ =>
        sys.error(s"Invalid property requested: $prop")
    }
  }
}

object ExtractStorageCmd extends CmdDescriptor(
  name = "extractStorage", cmdParamSyntax = "",
  description = "Reads the file, unlocks it using password and extract the requested property from the given storage file.") {

  val PropAddress = "address"
  val PropMasterKey = "masterKey"
  val PropPublicKey = "publicKey"
  val PropSecretKey = "secretKey"

  val supportedKeys: Seq[String] = Array(PropAddress, PropMasterKey, PropPublicKey, PropSecretKey)
  override val cmdParamSyntax = s"<storage file> ${supportedKeys.mkString("|")} mainnet|testnet"

  private def propErrorMsg = {
    error(s"Please specify one of the supported properties: ${supportedKeys.map(k => s"`$k`").mkString(",")}")
  }

  private def parsePropName(prop: String): String =
    if (supportedKeys.contains(prop)) prop
    else propErrorMsg

  override def parseCmd(ctx: AppContext): Cmd = {
    val args = ctx.cmdArgs
    val storageFile = if (args.length > 1) args(1) else error("storage file is not specified")
    val prop = if (args.length > 2) parsePropName(args(2)) else propErrorMsg
    val network = parseNetwork(if (args.length > 3) args(3) else error("please specify network type (mainnet|testnet)"))
    val storagePass = ctx.console.readPassword("Storage password> ")
    ExtractStorageCmd(ctx.toolConf, name, storageFile, storagePass, prop, network)
  }
}





