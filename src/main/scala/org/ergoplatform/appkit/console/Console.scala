package org.ergoplatform.appkit.console

import java.io.{BufferedReader, PrintStream}

import org.ergoplatform.appkit.SecretString

/** Abstract interface for Console interactions (print and read operations).
 * Console read operations consume from input stream, and print operations
 * send data to output stream.
 */
abstract class Console {
  /** Print the given string to the output stream. */
  def print(s: String): Console

  /** Print the given string to the output stream and append new line character '\n'. */
  def println(s: String): Console

  /** Read a line (sequence of characters up to '\n') from input stream.
   * The ending '\n' character is consumed, but not included in the result.
   */
  def readLine(): String
  def readLine(prompt: String): String
  def readPassword(): SecretString
  def readPassword(prompt: String): SecretString
}
object Console {
  /** The console which should be used in application's main method. */
  lazy val instance: Console = new MainConsole()
}

/** Wrapper around system console to be used in `Application.main` method. */
class MainConsole() extends Console {
  val sysConsole = System.console()

  override def print(s: String): Console = { sysConsole.printf("%s", s); this }

  override def println(s: String): Console = { sysConsole.printf(s"%s\n", s); this }

  override def readLine(): String = sysConsole.readLine()

  override def readLine(prompt: String): String = sysConsole.readLine("%s", prompt)

  override def readPassword(): SecretString = SecretString.create(sysConsole.readPassword())

  override def readPassword(prompt: String): SecretString = SecretString.create(sysConsole.readPassword("%s", prompt))
}

/** Console implementation to be used in tests */
class TestConsole(in: BufferedReader, out: PrintStream) extends Console {
  override def print(s: String): Console = { out.print(s); this }

  override def println(s: String): Console = { out.println(s); this }

  override def readLine(): String = { in.readLine() }

  override def readLine(msg: String): String = {
    print(msg).readLine()
  }

  // TODO security: these methods should be reimplemented without using String (See java.io.Console)
  override def readPassword(): SecretString = {
    val line = readLine()
    SecretString.create(line)
  }

  override def readPassword(msg: String): SecretString = {
    print(msg).readPassword()
  }
}


