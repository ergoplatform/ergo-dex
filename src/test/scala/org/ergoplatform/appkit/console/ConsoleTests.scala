package org.ergoplatform.appkit.console

import org.ergoplatform.appkit.examples.Example
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.scalatest.{PropSpec, Matchers}

class ConsoleTests extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks with ConsoleTesting {
  val scenario = ConsoleScenario(Seq(
    WriteRead("Enter line 1> ", "input line 1"),
    WriteRead("Enter line 2> ", "input line 2"),
    WriteRead("You entered: input line 1input line 2\n", ""),
  ))

  property("read input string") {
    testScenario(scenario) { console =>
      Example.process(console)
    }
  }

  property("parse scenario from text") {
    val text =
      s"""# first line comment (should be ignored)
       |Enter line 1> ::input line 1;
       |# second line comment
       |Enter line 2> ::input line 2;
       |You entered: input line 1input line 2${'\n'}::;
       |""".stripMargin

     val parsed = parseScenario(text)
     parsed shouldBe scenario

    testScenario(parsed) { console =>
      Example.process(console)
    }
  }
}



