package org.ergoplatform.appkit.ergotool

import org.ergoplatform.appkit.console.{Console, ConsoleTesting}
import org.ergoplatform.appkit.FileMockedErgoClient
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import scalan.util.FileUtil
import org.ergoplatform.appkit.JavaHelpers._
import java.util.{List => JList}
import java.lang.{String => JString}
import java.nio.file.{Files, Paths}

import org.ergoplatform.settings.ErgoAlgos
import sigmastate.Values.ByteArrayConstant
import sigmastate.serialization.{SigmaSerializer, ValueSerializer}

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
      testCommand("createStorage", Seq(storageDir, storageFileName),
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

  property("dex:SellOrder command") {
    val data = MockData(
      Seq(
        loadNodeResponse("response_Box1.json"),
        loadNodeResponse("response_Box2.json"),
        loadNodeResponse("response_Box3.json"),
        loadNodeResponse("response_Box4.json"),
        "21f84cf457802e66fb5930fb5d45fbe955933dc16a72089bf8980797f24e2fa1"),
      Seq(
        loadExplorerResponse("response_boxesByAddressUnspent.json")))
    val res = runCommand("dex:SellOrder",
      args = Seq(
        "storage/E2.json",
        "9f4QF8AD1nQ3nJahQVkMj8hFSVVzVom77b52JU7EW71Zexg6N8v", // seller address
        "50000000", // token price in NanoERGs
        "21f84cf457802e66fb5930fb5d45fbe955933dc16a72089bf8980797f24e2fa1", // tokenId
        "60", // token amount
        "5000000" // DEX fee
      ),
      expectedConsoleScenario =
        s"""Storage password> ::abc;
           |""".stripMargin, data)
    println(res)
    res should include ("\"transactionId\": \"3149bb0bb69dc69da061dc02933d2908bf469f1f81521365e3e82ec832b05699\",")
  }

  property("dex:BuyOrder command") {
    val data = MockData(
      Seq(
        loadNodeResponse("response_Box1.json"),
        loadNodeResponse("response_Box2.json"),
        loadNodeResponse("response_Box3.json"),
        loadNodeResponse("response_Box4.json"),
        "21f84cf457802e66fb5930fb5d45fbe955933dc16a72089bf8980797f24e2fa1"),
      Seq(
        loadExplorerResponse("response_boxesByAddressUnspent.json")))
    val res = runCommand("dex:BuyOrder",
      args = Seq(
        "storage/E2.json",
        "9hHDQb26AjnJUXxcqriqY1mnhpLuUeC81C4pggtK7tupr92Ea1K", // buyer address
        "50000000", // token price in NanoERGs
        "21f84cf457802e66fb5930fb5d45fbe955933dc16a72089bf8980797f24e2fa1", // tokenId
        "60", // token amount
        "5000000", // DEX fee
      ),
      expectedConsoleScenario =
        s"""Storage password> ::abc;
           |""".stripMargin, data)
    println(res)
    res should include ("\"transactionId\": \"1bf5cdad177b24f9543573337ca789982b4b6bd9ac2be8f6e6113b14e52bd544\",")
  }

  property("dex:MatchOrders command") {
    val data = MockData(
      Seq(
        loadNodeResponse("response_Box_AAE_seller_contract.json"),
        loadNodeResponse("response_Box_AAE_buyer_contract.json"),
        "21f84cf457802e66fb5930fb5d45fbe955933dc16a72089bf8980797f24e2fa1"),
      Seq(
      ))
    val res = runCommand("dex:MatchOrders",
      args = Seq(
        "storage/E2.json",
        "8eda35c7a781e183ab309678499b7fb05c715e1f47fea43b5e0dd05b3c69ab2f", // seller contract box id
        "969482db6643a16b6d8f4c8b50d0a9d5b47a698014c927ee0fa495e2adabbb8e", // buyer contract box id
        "7000000" // minimum DEX fee
      ),
      expectedConsoleScenario =
        s"""Storage password> ::abc;
           |""".stripMargin, data)
    println(res)
    res should include ("\"transactionId\": \"e0fb62af449bf7765fadded16d07232e245c9623f686b694a27fb39066645415\",")
  }

  property("dex:ListMatchingOrders command") {
    val data = MockData(
      Seq(
        loadNodeResponse("response_Box_AAE_seller_contract.json"),
        loadNodeResponse("response_Box1.json"),
        loadNodeResponse("response_Box2.json"),
        loadNodeResponse("response_Box3.json"),
        loadNodeResponse("response_Box_AAE_buyer_contract.json"),
        loadNodeResponse("response_Box1.json"),
        loadNodeResponse("response_Box2.json"),
        loadNodeResponse("response_Box3.json"),
        ),
      Seq(
        loadExplorerResponse("response_boxesByAddressUnspent.json"),
        loadExplorerResponse("response_boxesByAddressUnspent.json")
      )
    )

    val res = runCommand("dex:ListMatchingOrders",
      args = Seq(
      ),
      expectedConsoleScenario = "",
      data)
    println(res)
    res should include ("969482db6643a16b6d8f4c8b50d0a9d5b47a698014c927ee0fa495e2adabbb8e, 9000000")
  }

  property("dex:IssueToken command") {
    def encodedRegValue(str: String): String = {
      ErgoAlgos.encode(ValueSerializer.serialize(ByteArrayConstant(str.getBytes)))
    }

    val data = MockData(
      Seq(
        loadNodeResponse("response_Box1.json"),
        loadNodeResponse("response_Box2.json"),
        loadNodeResponse("response_Box3.json"),
        loadNodeResponse("response_Box4.json"),
        "21f84cf457802e66fb5930fb5d45fbe955933dc16a72089bf8980797f24e2fa1"),
      Seq(
        loadExplorerResponse("response_boxesByAddressUnspent.json")))
    val ergAmount = "50000000" // NanoERGs
    val tokenAmount = "1000000"
    val tokenName = "TKN"
    val tokenDesc = "Generic token"
    val numberOfDecimals = "2"
    val res = runCommand("dex:IssueToken",
      args = Seq(
        "storage/E2.json",
        ergAmount,
        tokenAmount,
        tokenName,
        tokenDesc,
        numberOfDecimals,
      ),
      expectedConsoleScenario =
        s"""Storage password> ::abc;
           |""".stripMargin, data)
    println(res)
    res should include
    s"""
          "amount": $tokenAmount
        }
      ],
      "additionalRegisters": {
        "R4": "${encodedRegValue(tokenName)}",
        "R5": "${encodedRegValue(tokenDesc)}",
        "R6": "${encodedRegValue(numberOfDecimals)}"
      },
    """.stripMargin

    res should include
    s"""
       | "value": $ergAmount,
       |""".stripMargin
  }
}

