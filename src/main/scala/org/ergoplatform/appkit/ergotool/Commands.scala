package org.ergoplatform.appkit.ergotool

import java.util.Arrays

import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.console.Console
import org.ergoplatform.appkit.ergotool.ErgoTool.RunContext

abstract class Cmd {
  def toolConf: ErgoToolConfig

  def name: String

  def seed: String = toolConf.getNode.getWallet.getMnemonic

  def password: String = toolConf.getNode.getWallet.getPassword

  def apiUrl: String = toolConf.getNode.getNodeApi.getApiUrl

  def apiKey: String = toolConf.getNode.getNodeApi.getApiKey

  def networkType: NetworkType = toolConf.getNode.getNetworkType

  def run(ctx: RunContext): Unit
}

trait RunWithErgoClient extends Cmd {
  override def run(ctx: RunContext): Unit = {
    val ergoClient = ctx.clientFactory(ctx)
    runWithClient(ergoClient, ctx)
  }

  def runWithClient(ergoClient: ErgoClient, ctx: RunContext): Unit
}

/** Base class for all Cmd factories (usually companion objects)
 */
abstract class CmdDescriptor(
     /** Command name used in command line. */
     val name: String,

     /** parameters syntax specification */
     val cmdParamSyntax: String,
     val description: String) {

  /** Creates a new command instance based on the given [[RunContext]] */
  def parseCmd(ctx: RunContext): Cmd

  def error(msg: String) = {
    sys.error(s"Error executing command `$name`: $msg")
  }

  def parseNetwork(network: String): NetworkType = network match {
    case "testnet" => NetworkType.TESTNET
    case "mainnet" => NetworkType.MAINNET
    case _ => error(s"Invalid network type $network")
  }

  /** Secure entry of the new password.
   *
   * @param nAttemps number of attempts
   * @param block  code block which can request the user to enter a new password twice
   * @return password returned by `block`
   */
  def readNewPassword(nAttemps: Int, console: Console)(block: => (Array[Char], Array[Char])): Array[Char] = {
    var i = 0
    do {
      val (p1, p2) = block
      i += 1
      if (Arrays.equals(p1, p2)) {
        Arrays.fill(p2, ' ') // cleanup duplicate copy
        return p1
      }
      else {
        Arrays.fill(p1, ' ') // cleanup sensitive data
        Arrays.fill(p2, ' ')
        if (i < nAttemps) {
          console.println(s"Passwords are different, try again [${i + 1}/$nAttemps]")
          // and loop
        } else
          error(s"Cannot continue without providing valid password")
      }
    } while (true)
    error("should never go here due to exhaustive `if` above")
  }
}

