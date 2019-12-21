package org.ergoplatform.appkit.examples

import org.ergoplatform.appkit.console.Console
import org.ergoplatform.appkit.examples.ergotool.{ConfigOption, ErgoTool}
import org.ergoplatform.appkit.examples.util.FileMockedErgoClient
import org.scalatest.{PropSpec, Matchers}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import scalan.util.FileUtil
import org.ergoplatform.appkit.JavaHelpers._
import java.util.{List => JList}
import java.lang.{String => JString}
import java.nio.file.{Files, Paths}

/** To run in IDEA set `Working directory` in Run/Debug configuration. */
class ErgoToolSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks with ConsoleTesting {
  val addrStr = "3WzR39tWQ5cxxWWX6ys7wNdJKLijPeyaKgx72uqg9FJRBCdZPovL"
  val mnemonic = "slow silly start wash bundle suffer bulb ancient height spin express remind today effort helmet"

  val addr2Str = "3WwWU3GJLK3aXCsoqptRboPYnvdqwv9QMBbpHybwXdVo9bSaMLEE"
  val mnemonic2 = "burst cancel left report gauge fame fit slow series dial convince satoshi outer magnet filter"

  val responsesDir = "src/test/resources/mockwebserver"
  def loadNodeResponse(name: String) = {
    FileUtil.read(FileUtil.file(s"$responsesDir/node_responses/$name"))
  }
  def loadExplorerResponse(name: String) = {
    FileUtil.read(FileUtil.file(s"$responsesDir/explorer_responses/$name"))
  }

  // NOTE, mainnet data is used for testing
  val testConfigFile = "ergo_tool_config.json"

  case class MockData(nodeResponses: Seq[String] = Nil, explorerResponses: Seq[String] = Nil)
  object MockData {
    def empty = MockData()
  }

  def runErgoTool(console: Console, name: String, args: Seq[String], data: MockData = MockData.empty) = {
    ErgoTool.run(name +: (Seq(ConfigOption.cmdText, testConfigFile) ++ args), console, {
      ctx => {
        val nrs = IndexedSeq(
          loadNodeResponse("response_NodeInfo.json"),
          loadNodeResponse("response_LastHeaders.json")) ++ data.nodeResponses
        val ers: IndexedSeq[String] = data.explorerResponses.toIndexedSeq
        new FileMockedErgoClient(nrs.convertTo[JList[JString]], ers.convertTo[JList[JString]])
      }
    })
  }

  /**
   * @param consoleScenario input and output operations with the console
   * @param name command name
   * @param args arguments of command line
   */
  def runCommand(consoleScenario: String, name: String, args: Seq[String], data: MockData = MockData.empty): String = {
    val consoleOps = parseScenario(consoleScenario)
    runScenario(consoleOps) { console =>
      runErgoTool(console, name, args, data)
    }
  }

  def testCommand(consoleScenario: String, name: String, args: Seq[String], data: MockData = MockData.empty): Unit = {
    val consoleOps = parseScenario(consoleScenario)
    testScenario(consoleOps) { console =>
      runErgoTool(console, name, args, data)
    }
    ()
  }

  property("address command") {
    testCommand(
      s"""Mnemonic password> ::;
        |$addrStr::;
        |""".stripMargin,
      "address", Seq("testnet", mnemonic))
  }

  property("mnemonic command") {
    val res = runCommand("", "mnemonic", Nil)
    res.split(" ").length shouldBe 15
  }

  property("checkAddress command") {
    testCommand(
      s"""Mnemonic password> ::;
         |Ok::;
         |""".stripMargin,
      "checkAddress", Seq("testnet", mnemonic, addrStr))
  }

  property("checkAddress command validates address format") {
    val res = runCommand(
      s"""Mnemonic password> ::;
        |""".stripMargin,
      "checkAddress", Seq("testnet", mnemonic, "someaddress"))
    res should include ("Invalid address encoding, expected base58 string: someaddress")
  }

  property("checkAddress command validates network type") {
    val res = runCommand(
      s"""Mnemonic password> ::;
        |""".stripMargin,
      "checkAddress", Seq("testnet", mnemonic, "9f4QF8AD1nQ3nJahQVkMj8hFSVVzVom77b52JU7EW71Zexg6N8v"))
    res should include ("Network type of the address MAINNET don't match expected TESTNET")
  }

  property("listAddressBoxes command") {
    val data = MockData(
      Seq(
        loadNodeResponse("response_Box1.json"),
        loadNodeResponse("response_Box2.json"),
        loadNodeResponse("response_Box3.json")),
      Seq(
        loadExplorerResponse("response_boxesByAddressUnspent.json")))
    val res = runCommand(
      s"""""".stripMargin,
      "listAddressBoxes", Seq("9hHDQb26AjnJUXxcqriqY1mnhpLuUeC81C4pggtK7tupr92Ea1K"), data)
    res should include ("d47f958b201dc7162f641f7eb055e9fa7a9cb65cc24d4447a10f86675fc58328")
    res should include ("e050a3af38241ce444c34eb25c0ab880674fc23a0e63632633ae14f547141c37")
    res should include ("26d6e08027e005270b38e5c5f4a73ffdb6d65a3289efb51ac37f98ad395d887c")
  }

  property("createStorage and extractStorage commands") {
    val storageDir = "storage"
    val storageFileName = "secret.json"
    val filePath = Paths.get(storageDir, storageFileName)
    try {
      // create a storage file
      testCommand(
        consoleScenario =
            s"""Mnemonic password> ::;
              |Repeat mnemonic password> ::;
              |Storage password> ::def;
              |Repeat storage password> ::def;
              |Storage File: $filePath\n::;
              |""".stripMargin,
        "createStorage", Seq(mnemonic))

      // extract address from the storage file
      testCommand(
        consoleScenario =
            s"""Storage password> ::def;
              |$addrStr\n::;
              |""".stripMargin,
        "extractStorage", Seq(filePath.toString, "address", "testnet"))

    } finally {
      if (Files.exists(filePath)) Files.delete(filePath)
    }
  }

  property("send command") {
    val data = MockData(
      Seq(
        loadNodeResponse("response_Box1.json"),
        loadNodeResponse("response_Box2.json"),
        loadNodeResponse("response_Box3.json"),
        "21f84cf457802e66fb5930fb5d45fbe955933dc16a72089bf8980797f24e2fa1"),
      Seq(
        loadExplorerResponse("response_boxesByAddressUnspent.json")))
    val res = runCommand(
      s"""Storage password> ::abc;
        |""".stripMargin,
      "send",
      args = Seq("storage/E2.json", "9f4QF8AD1nQ3nJahQVkMj8hFSVVzVom77b52JU7EW71Zexg6N8v", "1000000"),
      data)
    println(res)
    res should include ("\"transactionId\": \"21f84cf457802e66fb5930fb5d45fbe955933dc16a72089bf8980797f24e2fa1\",")
  }

//  ignore("send command") {
//    val res = runCommand(
//      s"""Storage password> ::abc;
//        |""".stripMargin,
//      "send", "storage/E1.json", "9hHDQb26AjnJUXxcqriqY1mnhpLuUeC81C4pggtK7tupr92Ea1K", "1000000")
//    println(res)
//  }


}

