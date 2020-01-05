package org.ergoplatform.appkit.ergotool

import org.ergoplatform.appkit.console.{ConsoleTesting, Console}
import org.ergoplatform.appkit.examples.util.FileMockedErgoClient
import org.scalatest.{PropSpec, Matchers}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import scalan.util.FileUtil
import org.ergoplatform.appkit.JavaHelpers._
import java.util.{List => JList}
import java.lang.{String => JString}
import java.nio.file.{Files, Paths}

class ErgoToolSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks with ConsoleTesting {

  // test values which correspond to each other (see also addr.json storage file, which is obtained using this values)
  val addrStr = "3WzR39tWQ5cxxWWX6ys7wNdJKLijPeyaKgx72uqg9FJRBCdZPovL"
  val mnemonic = "slow silly start wash bundle suffer bulb ancient height spin express remind today effort helmet"
  val mnemonicPassword = ""
  val storagePassword = "def"
  val publicKey = "03f56b14197c1d0f9bf8418ed8c57a3179d12d9af98745fbd0ab3b9dd6883d24a8"
  val secretKey = "18258e98ea87256806275b71cb203dc234752488e01985d405426e5c6f4ea1d1"
  val masterKey = "18258e98ea87256806275b71cb203dc234752488e01985d405426e5c6f4ea1d1efe92e5adfcaa6f61173108305f7e3ba4ec9643a81dffa347879cf4d58d2a10006000200000000"

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

  /** Run the given command with expected console scenario (print and read operations)
   * @param name the command
   * @param args arguments of command line
   * @param expectedConsoleScenario input and output operations with the console (see parseScenario)
   */
  def runCommand(name: String, args: Seq[String], expectedConsoleScenario: String, data: MockData = MockData.empty): String = {
    val consoleOps = parseScenario(expectedConsoleScenario)
    runScenario(consoleOps) { console =>
      runErgoTool(console, name, args, data)
    }
  }

  def testCommand(name: String, args: Seq[String], expectedConsoleScenario: String, data: MockData = MockData.empty): Unit = {
    val consoleOps = parseScenario(expectedConsoleScenario)
    testScenario(consoleOps) { console =>
      runErgoTool(console, name, args, data)
    }
    ()
  }

  property("help command") {
    ErgoTool.commands.values.foreach { c =>
      val res = runCommand("help", Seq(c.name), expectedConsoleScenario = "")
      res should include (s"Command Name:\t${c.name}")
      res should include (s"Doc page:\t${c.docUrl}")

    }
  }

  property("address command") {
    testCommand("address", Seq("testnet", mnemonic),
      expectedConsoleScenario =
        s"""Mnemonic password> ::$mnemonicPassword;
          |Repeat mnemonic password> ::$mnemonicPassword;
          |$addrStr::;
          |""".stripMargin)
  }

  property("mnemonic command") {
    val res = runCommand("mnemonic", Nil, "")
    res.split(" ").length shouldBe 15
  }

  property("checkAddress command") {
    testCommand("checkAddress", Seq("testnet", mnemonic, addrStr),
      expectedConsoleScenario =
        s"""Mnemonic password> ::$mnemonicPassword;
           |Ok::;
           |""".stripMargin)
  }

  property("checkAddress command validates address format") {
    val res = runCommand("checkAddress", Seq("testnet", mnemonic, "someaddress"),
      expectedConsoleScenario =
        s"""Mnemonic password> ::$mnemonicPassword;
          |""".stripMargin)
    res should include ("Invalid address encoding, expected base58 string: someaddress")
  }

  property("checkAddress command validates network type") {
    val res = runCommand("checkAddress",
      args = Seq("testnet", mnemonic, "9f4QF8AD1nQ3nJahQVkMj8hFSVVzVom77b52JU7EW71Zexg6N8v"),
      expectedConsoleScenario =
        s"""Mnemonic password> ::$mnemonicPassword;
          |""".stripMargin)
    res should include ("Network type of the address MAINNET don't match expected TESTNET")
  }

  property("listAddressBoxes command") {
    val data = MockData(
      Seq(
        loadNodeResponse("response_Box1.json"),
        loadNodeResponse("response_Box2.json"),
        loadNodeResponse("response_Box3.json"),
        loadNodeResponse("response_Box4.json")),
    Seq(
        loadExplorerResponse("response_boxesByAddressUnspent.json")))
    val res = runCommand("listAddressBoxes", Seq("9hHDQb26AjnJUXxcqriqY1mnhpLuUeC81C4pggtK7tupr92Ea1K"),
      expectedConsoleScenario = "", data)
    res should include ("d47f958b201dc7162f641f7eb055e9fa7a9cb65cc24d4447a10f86675fc58328")
    res should include ("e050a3af38241ce444c34eb25c0ab880674fc23a0e63632633ae14f547141c37")
    res should include ("26d6e08027e005270b38e5c5f4a73ffdb6d65a3289efb51ac37f98ad395d887c")
  }

  property("createStorage and extractStorage commands") {
    import ExtractStorageCmd._
    val storageDir = "storage"
    val storageFileName = "secret.json"
    val filePath = Paths.get(storageDir, storageFileName)
    try {
      // create a storage file
      testCommand("createStorage", Seq(),
        expectedConsoleScenario =
            s"""Enter mnemonic phrase> ::$mnemonic;
              |Mnemonic password> ::$mnemonicPassword;
              |Repeat mnemonic password> ::$mnemonicPassword;
              |Storage password> ::$storagePassword;
              |Repeat storage password> ::$storagePassword;
              |Storage File: $filePath\n::;
              |""".stripMargin)

      // extract properties from the storage file
      Seq(
        PropAddress -> addrStr,
        PropPublicKey -> publicKey,
        PropMasterKey -> masterKey,
        PropSecretKey -> secretKey).foreach { case (propName, expectedValue) =>
        testCommand("extractStorage", Seq(filePath.toString, propName, "testnet"),
          expectedConsoleScenario =
            s"""Storage password> ::$storagePassword;
              |$expectedValue\n::;
              |""".stripMargin)
        println(s"$propName: ok")
      }

      // try extract invalid property
      val res = runCommand("extractStorage", Seq(filePath.toString, "invalidProp", "testnet"),
        expectedConsoleScenario = s"ignored")
      res should include ("Please specify one of the supported properties")
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
        loadNodeResponse("response_Box4.json"),
        "21f84cf457802e66fb5930fb5d45fbe955933dc16a72089bf8980797f24e2fa1"),
      Seq(
        loadExplorerResponse("response_boxesByAddressUnspent.json")))
    val res = runCommand("send",
      args = Seq("storage/E2.json", "9f4QF8AD1nQ3nJahQVkMj8hFSVVzVom77b52JU7EW71Zexg6N8v", "1000000"),
      expectedConsoleScenario =
        s"""Storage password> ::abc;
          |""".stripMargin, data)
    println(res)
    res should include ("Server returned tx id: 21f84cf457802e66fb5930fb5d45fbe955933dc16a72089bf8980797f24e2fa1")
  }

  property("AssetsAtomicExchange seller command") {
    val data = MockData(
      Seq(
        loadNodeResponse("response_Box1.json"),
        loadNodeResponse("response_Box2.json"),
        loadNodeResponse("response_Box3.json"),
        loadNodeResponse("response_Box4.json"),
        "21f84cf457802e66fb5930fb5d45fbe955933dc16a72089bf8980797f24e2fa1"),
      Seq(
        loadExplorerResponse("response_boxesByAddressUnspent.json")))
    val res = runCommand("AssetAtomicExchangeSeller",
      args = Seq(
        "storage/E2.json",
        "9f4QF8AD1nQ3nJahQVkMj8hFSVVzVom77b52JU7EW71Zexg6N8v", // seller address
        "999999", // deadline
        "50000000", // token price in nanoErgs
        "21f84cf457802e66fb5930fb5d45fbe955933dc16a72089bf8980797f24e2fa1", // tokenId
        "60", // token amount
        "5000000" // DEX fee
      ),
      expectedConsoleScenario =
        s"""Storage password> ::abc;
           |""".stripMargin, data)
    println(res)
    res should include ("\"transactionId\": \"1cf299fe144ac2d89b348f6e8666dd78ec2d8a030c3001f1809b771f4e566dca\",")
  }

  property("AssetsAtomicExchange buyer command") {
    val data = MockData(
      Seq(
        loadNodeResponse("response_Box1.json"),
        loadNodeResponse("response_Box2.json"),
        loadNodeResponse("response_Box3.json"),
        loadNodeResponse("response_Box4.json"),
        "21f84cf457802e66fb5930fb5d45fbe955933dc16a72089bf8980797f24e2fa1"),
      Seq(
        loadExplorerResponse("response_boxesByAddressUnspent.json")))
    val res = runCommand("AssetAtomicExchangeBuyer",
      args = Seq(
        "storage/E2.json",
        "9hHDQb26AjnJUXxcqriqY1mnhpLuUeC81C4pggtK7tupr92Ea1K", // buyer address
        "999999", // deadline
        "50000000", // token price in nanoErgs
        "21f84cf457802e66fb5930fb5d45fbe955933dc16a72089bf8980797f24e2fa1", // tokenId
        "60", // token amount
        "5000000", // DEX fee
      ),
      expectedConsoleScenario =
        s"""Storage password> ::abc;
           |""".stripMargin, data)
    println(res)
    res should include ("\"transactionId\": \"38f784490cdff2bb4f4088f1bb306d2ccc0fc5123dd9f7d116cc7ee69620b6a6\",")
  }

  property("AssetsAtomicExchange match command") {
    val data = MockData(
      Seq(
        loadNodeResponse("response_Box_AAE_seller_contract.json"),
        loadNodeResponse("response_Box_AAE_buyer_contract.json"),
        "21f84cf457802e66fb5930fb5d45fbe955933dc16a72089bf8980797f24e2fa1"),
      Seq(
      ))
    val res = runCommand("AssetAtomicExchangeMatch",
      args = Seq(
        "storage/E2.json",
        "7de38874effe031a7522460cef870c3a8fbcfb0cc70df769ba63688fd2b2b35d", // seller contract box id
        "4bb384d56abc2764523582cb1c514828c6a8436067127caac040903a683be0ee", // buyer contract box id
        "9f4QF8AD1nQ3nJahQVkMj8hFSVVzVom77b52JU7EW71Zexg6N8v", // seller address
        "9hHDQb26AjnJUXxcqriqY1mnhpLuUeC81C4pggtK7tupr92Ea1K", // buyer address
        "7000000" // minimum DEX fee
      ),
      expectedConsoleScenario =
        s"""Storage password> ::abc;
           |""".stripMargin, data)
    println(res)
    res should include ("\"transactionId\": \"f3e37a37b561a34bf91f37c9f9fbed1eb42a4d9bb364f869d42bcde22f5d8229\",")
  }
}

