package org.ergoplatform.appkit.ergotool.AssetsAtomicExchange

import java.lang.{Long => JLong}
import java.util.{List => JList}

import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import ListMatchingContracts._
import org.ergoplatform.appkit.{Address, ErgoId, ErgoToken, InputBox}
import sigmastate.Values.{ErgoTree, TrueLeaf}
import org.ergoplatform.appkit.JavaHelpers._
import org.ergoplatform.appkit.Iso._
import org.scalacheck.{Arbitrary, Gen}

class ListMatchingContractsSpec extends PropSpec
  with Matchers
  with ScalaCheckDrivenPropertyChecks {

  case class MockInputBox(getId: ErgoId,
                          getValue: JLong,
                          getTokens: JList[ErgoToken],
                          getErgoTree: ErgoTree) extends InputBox {
    override def toJson(prettyPrint: Boolean): String = ???
  }

  object MockInputBox {

    def apply(getId: ErgoId, getValue: Long,
              getErgoTree: ErgoTree, getTokens: Seq[ErgoToken] = Seq.empty): MockInputBox = {
      val tokens = JListToIndexedSeq(identityIso[ErgoToken]).from(getTokens.toIndexedSeq)
      new MockInputBox(getId, getValue, tokens, getErgoTree)
    }

    def apply(getValue: Long): MockInputBox =
      MockInputBox(ergoIdGen.sample.get, getValue, ErgoTree.fromProposition(TrueLeaf))

  }

  def ergoIdGen: Gen[ErgoId] = for {
    bytes <- Gen.listOfN(32, Arbitrary.arbByte.arbitrary)
  } yield new ErgoId(bytes.toArray)

  private def sellerBox: InputBox = ???

  property("empty list") {
    matchingContracts(Seq.empty, Seq.empty) shouldBe empty
    matchingContracts(Seq(MockInputBox(0L)), Seq.empty) shouldBe empty
    matchingContracts(Seq.empty, Seq(MockInputBox(0L))) shouldBe empty
    matchingContracts(Seq(MockInputBox(0L)), Seq(MockInputBox(0L))) shouldBe empty

    val anyAddress = Address.create("9f4QF8AD1nQ3nJahQVkMj8hFSVVzVom77b52JU7EW71Zexg6N8v")
    val sellerContract = SellerContract.contractInstance(0,0L, anyAddress.getPublicKey)
    val token = new ErgoToken("21f84cf457802e66fb5930fb5d45fbe955933dc16a72089bf8980797f24e2fa1", 0L)
    val sellerBox = MockInputBox(ergoIdGen.sample.get, 1L, sellerContract.getErgoTree, Seq(token))

    matchingContracts(Seq(sellerBox), Seq(MockInputBox(0L))) shouldBe empty
  }

}
