package org.ergoplatform.appkit.ergotool.AssetsAtomicExchange

import java.lang.{Long => JLong}
import java.util.{List => JList}

import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import ListMatchingContracts._
import org.ergoplatform.{ErgoAddressEncoder, P2PKAddress}
import org.ergoplatform.appkit.{Address, ErgoId, ErgoToken, InputBox, NetworkType}
import sigmastate.Values.{ErgoTree, TrueLeaf}
import org.ergoplatform.appkit.JavaHelpers._
import org.ergoplatform.appkit.Iso._
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.tagobjects.Network
import sigmastate.basics.DLogProtocol.ProveDlog
import sigmastate.interpreter.CryptoConstants
import sigmastate.interpreter.CryptoConstants.EcPointType
import org.ergoplatform.appkit.Parameters.MinFee

class ListMatchingContractsSpec extends PropSpec
  with Matchers
  with ScalaCheckDrivenPropertyChecks {

  // TODO: extract
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

  // TODO: extract (to appkit?)
  def ergoIdGen: Gen[ErgoId] = for {
    bytes <- Gen.listOfN(32, Arbitrary.arbByte.arbitrary)
  } yield new ErgoId(bytes.toArray)

  val groupElementGen: Gen[EcPointType] = for {
    _ <- Gen.const(1)
  } yield CryptoConstants.dlogGroup.createRandomElement()
  val proveDlogGen: Gen[ProveDlog] = for {v <- groupElementGen} yield ProveDlog(v)

  def addressGen(networkPrefix: Byte): Gen[Address] = for {
    pd <- proveDlogGen
  } yield new Address(P2PKAddress(pd)(new ErgoAddressEncoder(networkPrefix)))

  val testnetAddressGen: Gen[Address] = addressGen(NetworkType.TESTNET.networkPrefix)

  property("empty list (no input)") {
    matchingContracts(Seq.empty, Seq.empty) shouldBe empty
  }

  property("empty list (no valid contracts in inputs)") {
    matchingContracts(Seq.empty, Seq.empty) shouldBe empty
    matchingContracts(Seq(MockInputBox(0L)), Seq.empty) shouldBe empty
    matchingContracts(Seq.empty, Seq(MockInputBox(0L))) shouldBe empty
    matchingContracts(Seq(MockInputBox(0L)), Seq(MockInputBox(0L))) shouldBe empty
  }

  property("empty list (only a seller contract is given)") {
    // seller contract with a token
    val anyAddress = testnetAddressGen.sample.get
    val sellerContract = SellerContract.contractInstance(0,0L, anyAddress)
    val token = new ErgoToken(ergoIdGen.sample.get, 1L)
    val sellerBox = MockInputBox(ergoIdGen.sample.get, 1L, sellerContract.getErgoTree, Seq(token))

    matchingContracts(Seq(sellerBox), Seq(MockInputBox(0L))) shouldBe empty
  }

  property("empty list (no matching token)") {
    val sellerContract = SellerContract.contractInstance(0,0L,
      testnetAddressGen.sample.get)
    val sellerBox = MockInputBox(ergoIdGen.sample.get, 1L, sellerContract.getErgoTree,
      Seq(new ErgoToken(ergoIdGen.sample.get, 1L)))

    val buyerContract = BuyerContract.contractInstance(0,
      new ErgoToken(ergoIdGen.sample.get, 1L), testnetAddressGen.sample.get)
    val buyerBox = MockInputBox(ergoIdGen.sample.get, 1L, buyerContract.getErgoTree)

    matchingContracts(Seq(sellerBox), Seq(buyerBox)) shouldBe empty
  }

  property("empty list (no matching token count)") {
    val tokenPrice = 10L
    val sellerContract = SellerContract.contractInstance(0, tokenPrice,
      testnetAddressGen.sample.get)
    val tokenId = ergoIdGen.sample.get
    val sellerBox = MockInputBox(ergoIdGen.sample.get, 1L, sellerContract.getErgoTree,
      Seq(new ErgoToken(tokenId, 1L)))

    val buyerContract = BuyerContract.contractInstance(0, new ErgoToken(tokenId, 2L),
      testnetAddressGen.sample.get)
    val buyerBox = MockInputBox(ergoIdGen.sample.get, tokenPrice + MinFee, buyerContract.getErgoTree)

    matchingContracts(Seq(sellerBox), Seq(buyerBox)) shouldBe empty
  }

  property("empty list (DEX fee < 0)") {
    val sellerContract = SellerContract.contractInstance(0,20L,
      testnetAddressGen.sample.get)
    val token = new ErgoToken(ergoIdGen.sample.get, 1L)
    val sellerBox = MockInputBox(ergoIdGen.sample.get, 1L, sellerContract.getErgoTree,
      Seq(token))

    val buyerContract = BuyerContract.contractInstance(0, token, testnetAddressGen.sample.get)
    val buyerBox = MockInputBox(ergoIdGen.sample.get, 10L + MinFee, buyerContract.getErgoTree)

    matchingContracts(Seq(sellerBox), Seq(buyerBox)) shouldBe empty
  }

  property("one match") {
    val sellerContract = SellerContract.contractInstance(0,20L,
      testnetAddressGen.sample.get)
    val token = new ErgoToken(ergoIdGen.sample.get, 1L)
    val sellerBox = MockInputBox(ergoIdGen.sample.get, 1L, sellerContract.getErgoTree,
      Seq(token))

    val buyerContract = BuyerContract.contractInstance(0, token, testnetAddressGen.sample.get)
    val buyerBox = MockInputBox(ergoIdGen.sample.get, 20L + MinFee, buyerContract.getErgoTree)

    val matches = matchingContracts(Seq(sellerBox), Seq(buyerBox))
    matches.length shouldBe 1
    matches.forall(_.dexFee >= 0) shouldBe true
  }

  property("many matches") {
    val sellerContract = SellerContract.contractInstance(0,20L,
      testnetAddressGen.sample.get)
    val token = new ErgoToken(ergoIdGen.sample.get, 1L)
    val sellerBox = MockInputBox(ergoIdGen.sample.get, 1L, sellerContract.getErgoTree,
      Seq(token))

    val buyerContract = BuyerContract.contractInstance(0, token, testnetAddressGen.sample.get)
    val buyerBox = MockInputBox(ergoIdGen.sample.get, 20L + MinFee, buyerContract.getErgoTree)

    val matches = matchingContracts(Array.fill(5)(sellerBox), Array.fill(5)(buyerBox))
    matches.length shouldBe 25 // (5 * 5, cartesian product)
    matches.forall(_.dexFee >= 0) shouldBe true
  }
}
