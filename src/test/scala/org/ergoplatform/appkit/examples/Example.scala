package org.ergoplatform.appkit.examples

import java.io.{InputStreamReader, BufferedReader, PrintStream}

import org.ergoplatform.appkit.console.{Console, TestConsole}

object Example {
  def main(args: Array[String]): Unit = {
    val in = new BufferedReader(new InputStreamReader(System.in))
    val out = new PrintStream(System.out)
    process(new TestConsole(in, out))
  }

  def process(console: Console) = {
    console.print("Enter line 1> ")
    val line1 = console.readLine()
    console.print("Enter line 2> ")
    val line2 = console.readLine()
    val res = line1 + line2
    console.println(s"You entered: $res")
  }
}
