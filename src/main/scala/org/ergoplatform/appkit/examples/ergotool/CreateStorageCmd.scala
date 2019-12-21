package org.ergoplatform.appkit.examples.ergotool

import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.{Mnemonic, SecretStorage}
import java.io.PrintStream
import java.nio.file.{CopyOption, Files, Paths, StandardCopyOption}
import java.util

import org.ergoplatform.appkit.console.Console
import org.ergoplatform.appkit.examples.ergotool.ErgoTool.RunContext

case class CreateStorageCmd(
    toolConf: ErgoToolConfig, name: String,
    mnemonic: Mnemonic, storagePass: Array[Char],
    storageDir: String, storageFileName: String) extends Cmd {
  override def run(ctx: RunContext): Unit = {
    val storage = SecretStorage.createFromMnemonicIn(storageDir, mnemonic, String.valueOf(storagePass))
    util.Arrays.fill(storagePass, 0.asInstanceOf[Char])
    val filePath = Files.move(storage.getFile.toPath, Paths.get(storageDir, storageFileName), StandardCopyOption.ATOMIC_MOVE)
    ctx.console.println(s"Storage File: $filePath")
  }
}
object CreateStorageCmd extends CmdFactory(
  name = "createStorage", cmdParamSyntax = "<mnemonic> [<storageDir>=\"storage\"] [<storageFileName>=\"secret.json\"]",
  description = "Creates an encrypted storage file for the given <mnemonic> (requests storage password)") {

  override def parseCmd(ctx: RunContext): Cmd = {
    val args = ctx.cmdArgs
    val console = ctx.console
    val phrase = if (args.length > 1) args(1) else error("mnemonic is not specified")
    val storageDir = if (args.length > 2) args(2) else "storage"
    val storageFileName = if (args.length > 3) args(3) else "secret.json"

    val storagePath = Paths.get(storageDir, storageFileName)
    if (Files.exists(storagePath)) error(s"File $storagePath already exists")

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




