package org.ergoplatform.appkit.ergotool

import java.util.Arrays

import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.console.Console

/** Base class for all commands which can be executed by ErgoTool.
  * Inherit this class to implement a new command.
  * @see [[RunWithErgoClient]] if your command need to communicate with blockchain.
  */
abstract class Cmd {
  /** @return current tool configuration parameters */
  def toolConf: ErgoToolConfig

  /** @return the name of this command (Example: `send`, `mnemonic` etc.) */
  def name: String

  /** @return the url of the Ergo blockchain node used to communicate with the network. */
  def apiUrl: String = toolConf.getNode.getNodeApi.getApiUrl

  /** ApiKey which is used for Ergo node API authentication.
    * This is a secrete key whose hash was used in Ergo node config.
    * This is only necessary to communicate to the protected parts of node API.
    */
  def apiKey: String = toolConf.getNode.getNodeApi.getApiKey

  /** Returns the network type (MAINNET or TESTNET) [[ErgoTool]] is expected to communicate.
    * This parameter should correspond to the real network type of the node pointed to by [[apiUrl]].
    */
  def networkType: NetworkType = toolConf.getNode.getNetworkType

  /** Runs this command using given [[AppContext]].
 *
    * @param ctx context information of this command execution collected from command line,
    * configuration file etc.
    * @throws CmdException when command execution fails
    */
  def run(ctx: AppContext): Unit

  def error(msg: String) = throw CmdException(msg, this)
}

/** This trait can be used to implement commands which need to communicate with Ergo blockchain.
  * The default [[Cmd.run]] method is implemented and the new method with additional `ErgoClient`
  * parameter is declared, which is called from the default implementation.
  * To implement new command mix-in this train and implement [[RunWithErgoClient.runWithClient]] method.
  */
trait RunWithErgoClient extends Cmd {
  override def run(ctx: AppContext): Unit = {
    val ergoClient = ctx.clientFactory(ctx)
    runWithClient(ergoClient, ctx)
  }

  /** Called from [[run]] method with ErgoClient instance ready for Ergo blockchain communication. */
  def runWithClient(ergoClient: ErgoClient, ctx: AppContext): Unit
}

case class CmdParameter(name: String, tpe: ErgoType[_])

/** Base class for all Cmd descriptors (usually companion objects)
 */
abstract class CmdDescriptor(
     /** Command name used in command line. */
     val name: String,
     /** Specifies parameters syntax for this command. */
     val cmdParamSyntax: String,
     /** Human readable description of the command. Used in Usage Help output. */
     val description: String) {

  /** Creates a new command instance based on the given [[AppContext]]
    * @throws UsageException when the command cannot be parsed or the usage is not correct
    */
  def parseCmd(ctx: AppContext): Cmd

  /** Called during command line parsing and instantiation of [[Cmd]] for execution.
    * This is the prefered method to throw an exception.
    */
  def error(msg: String) = {
    sys.error(s"Error executing command `$name`: $msg")
  }

  /** Can be used by concrete command descriptors to report usage errors. */
  protected def usageError(msg: String) = throw UsageException(msg, Some(this))

  def parseNetwork(network: String): NetworkType = network match {
    case "testnet" => NetworkType.TESTNET
    case "mainnet" => NetworkType.MAINNET
    case _ => usageError(s"Invalid network type $network")
  }

  /** Secure double entry of the new password giving the user many attempts.
    *
    * @param nAttemps number of attempts before failing with exception
    * @param block  code block which can request the user to enter a new password twice
    * @return password returned by `block` as `Array[Char]` instead of `String`. This allows
    *        the password to be erased as fast as possible and avoid leaking to GC.
    * @throws UsageException
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
          usageError(s"Cannot continue without providing valid password")
      }
    } while (true)
    error("should never go here due to exhaustive `if` above")
  }

  /** Outputs the usage help for this command to the given console */
  def printUsage(console: Console) = {
    val msg =
      s"""
        |Usage for $name
        |ergo-tool $name ${cmdParamSyntax}\n\t${description}
       """.stripMargin
    console.println(msg)
  }

}

/** Exception thrown by ErgoTool application when incorrect usage is detected.
  * @param message error message
  * @param cmdDescOpt  optional descriptor of the command which was incorrectly used
  */
case class UsageException(message: String, cmdDescOpt: Option[CmdDescriptor]) extends RuntimeException(message)

/** Exception thrown by ErgoTool application before or after command execution.
  * @see CmdException which should be thrown by commands during execution
  */
case class ErgoToolException(message: String, cause: Throwable = null) extends RuntimeException(message, cause)

/** Exception thrown by executing [[Cmd.run]], wrapping the cause if needed.
  */
case class CmdException(message: String, cmd: Cmd, cause: Throwable = null) extends RuntimeException(message, cause)

