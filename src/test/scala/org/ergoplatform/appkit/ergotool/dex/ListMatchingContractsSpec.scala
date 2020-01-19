package org.ergoplatform.appkit.ergotool.dex

import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import ListMatchingContracts._
import org.ergoplatform.appkit.{Address, ErgoId, ErgoToken, InputBox, MockInputBox, NetworkType, ObjectGenerators}
import org.ergoplatform.appkit.Parameters.MinFee

class ListMatchingContractsSpec extends PropSpec
  with Matchers
  with ScalaCheckDrivenPropertyChecks
  with ObjectGenerators {

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

  property("empty list(wrong contracts)") {
    val sellerContract = SellerContract.contractInstance(0,20L,
      testnetAddressGen.sample.get)
    val token = new ErgoToken(ergoIdGen.sample.get, 1L)
    val sellerBox = MockInputBox(ergoIdGen.sample.get, 1L, sellerContract.getErgoTree,
      Seq(token))

    val buyerContract = BuyerContract.contractInstance(0, token, testnetAddressGen.sample.get)
    val buyerBox = MockInputBox(ergoIdGen.sample.get, 20L + MinFee, buyerContract.getErgoTree)

    // swapped parameter places
    matchingContracts(Seq(buyerBox), Seq(sellerBox)) shouldBe empty
    // same contract for both parameters
    matchingContracts(Seq(buyerBox), Seq(buyerBox)) shouldBe empty
    matchingContracts(Seq(sellerBox), Seq(sellerBox)) shouldBe empty
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
