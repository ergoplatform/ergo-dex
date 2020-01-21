package org.ergoplatform.appkit.ergotool

import java.io.File
import java.nio.file.{Path, Paths}
import java.util.Arrays

import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.console.Console
import org.ergoplatform.appkit.ergotool.HelpCmd.usageError

/** Base class for all commands which can be executed by ErgoTool.
  * Inherit this class to implement a new command.
  * @see [[RunWithErgoClient]] if your command need to communicate with blockchain.
  */
abstract class Cmd {
  /** Returns current tool configuration parameters */
  def toolConf: ErgoToolConfig

  /** Returns the name of this command (Example: `send`, `mnemonic` etc.) */
  def name: String

  /** Returns the url of the Ergo blockchain node used to communicate with the network. */
  def apiUrl: String = toolConf.getNode.getNodeApi.getApiUrl

  /** ApiKey which is used for Ergo node API authentication.
    * This is a secret key whose hash was used in Ergo node config.
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

  /** Helper method to throw a new [[CmdException]] from this command. */
  def error(msg: String) = throw CmdException(msg, this)

  /** Helper method to log the result of the step execution to a console */
  def loggedStep[T](msg: String, console: Console)(step: => T): T = {
    console.print(msg + "...")
    val res = step
    val status = if (res != null) "Ok" else "Error"
    console.println(s" $status")
    res
  }
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

sealed trait PType {
}
case object NanoErgPType extends PType {
}

case object FilePType extends PType {
}

case object FilePathPType extends PType {
}

case object DirPathPType extends PType {
}

case object AddressPType extends PType {
}

case object NetworkPType extends PType {
}

case object CommandNamePType extends PType {
}

case object SecretStringPType extends PType {
}

case object IntPType extends PType {
}

case object BooleanPType extends PType {
}

case object StringPType extends PType {
}

/** Command parameter descriptor.
  * @param name         parameter name
  * @param tpe          type of the object which should be created from command line parameter string
  * @param description  description of the command parameter
  * @param defaultValue the string value which will be used when parameter is missing in the command line
  * @param interactivInput Some(producer) when parameter is entered interactively, i.e. it is not parsed from the command line
  */
case class CmdParameter(
  name: String,
  tpe: PType,
  description: String,
  defaultValue: Option[String] = None,
  interactivInput: Option[AppContext => Any] = None)

/** Base class for all Cmd descriptors (usually companion objects)
 */
abstract class CmdDescriptor(
     /** Command name used in command line. */
     val name: String,
     /** Specifies parameters syntax for this command. */
     val cmdParamSyntax: String,
     /** Human readable description of the command. Used in Usage Help output. */
     val description: String
     ) {

  /** Returns the descriptors of parameters of the command. */
  def parameters: Seq[CmdParameter] = Nil

  val BaseDocUrl = "https://aslesarenko.github.io/ergo-tool/api"

  /** Url of the ScalaDoc for this command. */
  def docUrl: String = {
    val classPath = this.getClass.getName.replace('.', '/').stripSuffix("$")
    s"$BaseDocUrl/$classPath.html"
  }

  /** Creates a new command instance based on the given [[AppContext]]
    * @throws UsageException when the command cannot be parsed or the usage is not correct
    */
  def createCmd(ctx: AppContext): Cmd

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

  def parseArgs(ctx: AppContext, args: Seq[String]): Seq[Any] = {
    var iArg = 0
    val rawParams = parameters.map { p =>
      p.interactivInput match {
        case Some(producer) =>
          (p, producer(ctx))
        case _ =>
          if (iArg >= args.length)
            usageError(s"parameter '${p.name}' is not specified (run 'ergo-tool help ${this.name}' for usage help)")
          val arg = args(iArg)
          iArg += 1 // step to the next non-interactive parameter in command line
          (p, arg)
      }
    }
    rawParams.map {
      case (p, param) if p.interactivInput.isDefined => param  // this is final value
      case (p, rawArg: String) =>
        // command line string needs further parsing according to the parameter descriptor
        parseRawArg(p, rawArg)
    }
  }

  private def parseRawArg(p: CmdParameter, rawArg: String): Any = {
    p.tpe match {
      case CommandNamePType | StringPType => rawArg
      case NetworkPType =>
        val networkType = parseNetwork(rawArg)
        networkType
      case SecretStringPType =>
        SecretString.create(rawArg)
      case AddressPType =>
        Address.create(rawArg)
      case DirPathPType =>
        val file = new File(rawArg)
        if (!file.exists())
          usageError(s"Invalid parameter '${p.name}': directory '$file' doesn't exists.")
        if (!file.isDirectory)
          usageError(s"Invalid parameter '${p.name}': '$file' is not directory.")
        file.toPath
      case _ =>
        usageError(s"Unsupported parameter type: ${p.tpe}")
    }
  }

  /** Secure double entry of the new password giving the user many attempts.
    *
    * @param nAttemps number of attempts before failing with exception
    * @param block  code block which can request the user to enter a new password twice
    * @return password returned by `block` as `Array[Char]` instead of `String`. This allows
    *        the password to be erased as fast as possible and avoid leaking to GC.
    * @throws UsageException
    */
  def readNewPassword(nAttemps: Int, console: Console)(block: => (SecretString, SecretString)): SecretString = {
    var i = 0
    do {
      val (p1, p2) = block
      i += 1
      if (p1.equals(p2)) {
        p2.erase() // cleanup duplicate copy
        return p1
      }
      else {
        p1.erase() // cleanup sensitive data
        p2.erase()
        if (i < nAttemps) {
          console.println(s"Passwords are different, try again [${i + 1}/$nAttemps]")
          // and loop
        } else
          usageError(s"Cannot continue without providing valid password")
      }
    } while (true)
    error("should never go here due to exhaustive `if` above")
  }

  def readNewPassword(prompt: String, secondPrompt: String)(implicit ctx: AppContext): SecretString = {
    val console = ctx.console
    readNewPassword(3, console) {
      val p1 = console.readPassword(prompt)
      val p2 = console.readPassword(secondPrompt)
      (p1, p2)
    }
  }

  /** Outputs the usage help for this command to the given console */
  def printUsage(console: Console) = {
    val msg =
      s"""
        |Command Name:\t$name
        |Usage Syntax:\tergo-tool $name ${cmdParamSyntax}
        |Description:\t${description}
        |Doc page:\t$docUrl
        |""".stripMargin
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

