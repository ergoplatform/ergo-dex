package org.ergoplatform.appkit.ergotool

import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.{Mnemonic, SecretStorage}
import java.nio.file.{Files, StandardCopyOption, Paths}
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
  * @param mnemonic    instance of [[Mnemonic]] holding both mnemonic phrase and mnemonic password.
  * @param storagePass password used to encrypt the file and which is necessary to access and
  *                    decipher the file.
  * @param storageDir  directory (relative to the current) where to put storage file (default is
  *                    "storage")
  * @param storageFileName name of the storage file (default is "secret.json")
  */
case class CreateStorageCmd(
    toolConf: ErgoToolConfig, name: String,
    mnemonic: Mnemonic, storagePass: Array[Char],
    storageDir: String, storageFileName: String) extends Cmd {
  override def run(ctx: AppContext): Unit = {
    val storage = SecretStorage.createFromMnemonicIn(storageDir, mnemonic, String.valueOf(storagePass))
    util.Arrays.fill(storagePass, 0.asInstanceOf[Char])
    val filePath = Files.move(storage.getFile.toPath, Paths.get(storageDir, storageFileName), StandardCopyOption.ATOMIC_MOVE)
    ctx.console.println(s"Storage File: $filePath")
  }
}
object CreateStorageCmd extends CmdDescriptor(
  name = "createStorage", cmdParamSyntax = "[<storageDir>=\"storage\"] [<storageFileName>=\"secret.json\"]",
  description = "Creates an encrypted storage file for the mnemonic entered by user") {

  override def parseCmd(ctx: AppContext): Cmd = {
    val args = ctx.cmdArgs
    val console = ctx.console
    val storageDir = if (args.length > 1) args(1) else "storage"
    val storageFileName = if (args.length > 2) args(2) else "secret.json"

    val storagePath = Paths.get(storageDir, storageFileName)
    if (Files.exists(storagePath)) usageError(s"File $storagePath already exists")

    val phrase = console.readLine("Enter mnemonic phrase> ")
    val mnemonicPass = readNewPassword(3, console) {
      val p1 = console.readPassword("Mnemonic password> ")
      val p2 = console.readPassword("Repeat mnemonic password> ")
      (p1, p2)
    }
    val mnemonic = Mnemonic.create(phrase, String.valueOf(mnemonicPass))
    val storagePass = readNewPassword(3, console) {
      val p1 = console.readPassword("Storage password> ")
      val p2 = console.readPassword("Repeat storage password> ")
      (p1, p2)
    }
    CreateStorageCmd(ctx.toolConf, name, mnemonic, storagePass, storageDir, storageFileName)
  }
}




