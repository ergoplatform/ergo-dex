package org.ergoplatform.appkit.ergotool

import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.{Mnemonic, SecretStorage, SecretString}
import java.nio.file.{Files, StandardCopyOption, Paths, Path}
import java.util

/** Create a new json file with encrypted content storing a secret key.
  *
  * The format of secret file corresponds to [[org.ergoplatform.wallet.secrets.EncryptedSecret]].
  * By default it uses the following cipher parameters `{"prf":"HmacSHA256","c":128000,"dkLen":256}`
  * which information is openly stored in the storage file in order to be able to decipher it (see
  * [[org.ergoplatform.wallet.settings.EncryptionSettings]]).
  *
  * Command steps:<br/>
  * 1) request the user to enter a mnemonic phrase (or read it from input stream)<br/>
  * 2) request the user to enter a mnemonic password twice<br/>
  * 3) request the user to enter a storage encryption password twice ([[storagePass]]<br/>
  * 4) use the (mnemonic, mnemonicPass) pair instance to generate secret `seed` (see [[Mnemonic.toSeed]])<br/>
  * 5) use [[storagePass]] to encrypt `seed` with AES using "AES/GCM/NoPadding" [[javax.crypto.Cipher]]<br/>
  * 6) save encrypted `seed` (along with additional data necessary for decryption) in the given file
  *    ([[storageFileName]]) of the given directory ([[storageDir]]).<br/>
  * 7) print the path to the created file to the console output
  *
  * @param storageDir  directory (relative to the current) where to put storage file (default is
  *                    "storage")
  * @param storageFileName name of the storage file (default is "secret.json")
  * @param mnemonicPhrase instance of [[SecretString]] holding mnemonic phrase.
  * @param mnemonicPass   instance of [[SecretString]] holding mnemonic password.
  * @param storagePass password used to encrypt the file and which is necessary to access and
  *                    decipher the file.
  */
case class CreateStorageCmd
( toolConf: ErgoToolConfig, name: String,
  storageDir: String, storageFileName: String,  // TODO refactor: make single storagePath: Path parameter
  mnemonicPhrase: SecretString,
  mnemonicPass: SecretString,
  storagePass: SecretString) extends Cmd {
  override def run(ctx: AppContext): Unit = {
    val storagePath = Paths.get(storageDir, storageFileName)
    if (Files.exists(storagePath)) error(s"File $storagePath already exists")

    val mnemonic = Mnemonic.create(mnemonicPhrase, mnemonicPass)
    val storage = SecretStorage.createFromMnemonicIn(storageDir, mnemonic, storagePass)
    storagePass.erase()
    val filePath = Files.move(storage.getFile.toPath, Paths.get(storageDir, storageFileName), StandardCopyOption.ATOMIC_MOVE)
    ctx.console.println(s"Storage File: $filePath")
  }
}
object CreateStorageCmd extends CmdDescriptor(
  name = "createStorage", cmdParamSyntax = "<storageDir> <storageFileName>]",
  description = "Creates an encrypted storage file for the mnemonic entered by the user") {

  override val parameters: Seq[CmdParameter] = Array(
    CmdParameter("storageDir", DirPathPType,
      "directory (relative to the current) where to put storage file"),
    CmdParameter("storageFileName", StringPType,
      "name of the storage file"),
    CmdParameter("mnemonicPhrase", SecretStringPType,
      "secret mnemonic phrase", None,
      Some(ctx => SecretString.create(ctx.console.readLine("Enter mnemonic phrase> ")))),
    CmdParameter("mnemonicPass", SecretStringPType,
      "secret mnemonic password", None,
      Some(ctx => readNewPassword("Mnemonic password> ", "Repeat mnemonic password> ")(ctx))),
    CmdParameter("storagePass", SecretStringPType,
      "secret storage password", None,
      Some(ctx => readNewPassword("Storage password> ", "Repeat storage password> ")(ctx)))
  )


  override def createCmd(ctx: AppContext): Cmd = {
    val Seq(
      storageDir: Path,
      storageFileName: String,
      mnemonicPhrase: SecretString,
      mnemonicPass: SecretString,
      storagePass: SecretString) = ctx.cmdParameters

    CreateStorageCmd(ctx.toolConf, name, storageDir.toString, storageFileName, mnemonicPhrase, mnemonicPass, storagePass)

  }
}




