package org.ergoplatform.appkit.ergotool.dex

import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import ListMatchingOrders._
import org.ergoplatform.appkit.{Address, ErgoId, ErgoToken, InputBox, MockInputBox, NetworkType, ObjectGenerators}
import org.ergoplatform.appkit.Parameters.MinFee
import org.scalacheck.Gen

class ListMatchingOrdersSpec extends PropSpec
  with Matchers
  with ScalaCheckDrivenPropertyChecks
  with ObjectGenerators {

  property("empty list (no input)") {
    matchingOrders(Seq.empty, Seq.empty) shouldBe empty
  }

  property("empty list (no valid contracts in inputs)") {
    matchingOrders(Seq.empty, Seq.empty) shouldBe empty
    matchingOrders(Seq(MockInputBox(0L)), Seq.empty) shouldBe empty
    matchingOrders(Seq.empty, Seq(MockInputBox(0L))) shouldBe empty
    matchingOrders(Seq(MockInputBox(0L)), Seq(MockInputBox(0L))) shouldBe empty
  }

  property("empty list (only a seller contract is given)") {
    // seller contract with a token
    val anyAddress = testnetAddressGen.sample.get
    val sellerContract = SellerContract.contractInstance(0,0L, anyAddress)
    val token = new ErgoToken(ergoIdGen.sample.get, 1L)
    val sellerBox = MockInputBox(ergoIdGen.sample.get, 1L, sellerContract.getErgoTree, Seq(token))

    matchingOrders(Seq(sellerBox), Seq(MockInputBox(0L))) shouldBe empty
  }

  property("empty list (no matching token)") {
    val sellerContract = SellerContract.contractInstance(0,0L,
      testnetAddressGen.sample.get)
    val sellerBox = MockInputBox(ergoIdGen.sample.get, 1L, sellerContract.getErgoTree,
      Seq(new ErgoToken(ergoIdGen.sample.get, 1L)))

    val buyerContract = BuyerContract.contractInstance(0,
      new ErgoToken(ergoIdGen.sample.get, 1L), testnetAddressGen.sample.get)
    val buyerBox = MockInputBox(ergoIdGen.sample.get, 1L, buyerContract.getErgoTree)

    matchingOrders(Seq(sellerBox), Seq(buyerBox)) shouldBe empty
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

    matchingOrders(Seq(sellerBox), Seq(buyerBox)) shouldBe empty
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
    matchingOrders(Seq(buyerBox), Seq(sellerBox)) shouldBe empty
    // same contract for both parameters
    matchingOrders(Seq(buyerBox), Seq(buyerBox)) shouldBe empty
    matchingOrders(Seq(sellerBox), Seq(sellerBox)) shouldBe empty
  }

  private def matchContractsPair(dexFee: Long, tokenPrice: Long): (InputBox, InputBox) = {
    val dexFeePartSeller = dexFee / 2
    val dexFeePartBuyer = dexFee - dexFeePartSeller // to handle odd defFee
    val matchingTxFeePartSeller = MinFee / 2
    val matchingTxFeePartBuyer = MinFee - matchingTxFeePartSeller
    val sellerBoxValue = matchingTxFeePartSeller + dexFeePartSeller
    val buyerBoxValue = matchingTxFeePartBuyer + dexFeePartBuyer + tokenPrice

    val sellerContract = SellerContract.contractInstance(0, tokenPrice,
      testnetAddressGen.sample.get)
    val token = new ErgoToken(ergoIdGen.sample.get, 1L)
    val sellerBox = MockInputBox(ergoIdGen.sample.get, sellerBoxValue, sellerContract.getErgoTree, Seq(token))

    val buyerContract = BuyerContract.contractInstance(0, token, testnetAddressGen.sample.get)
    val buyerBox = MockInputBox(ergoIdGen.sample.get, buyerBoxValue, buyerContract.getErgoTree)
    (sellerBox, buyerBox)
  }

  property("empty list (DEX fee < MinFee)") {
    val dexFeeGen = Gen.chooseNum(Long.MinValue, MinFee - 1, 0L)
    val tokenPriceGen = Gen.chooseNum(1L, Long.MaxValue)
    forAll(dexFeeGen, tokenPriceGen) { (dexFee, tokenPrice) =>
      val(sellerBox, buyerBox) = matchContractsPair(dexFee, tokenPrice)
      matchingOrders(Seq(sellerBox), Seq(buyerBox)) shouldBe empty
    }
  }

  property("one match") {
    val dexFeeGen = Gen.chooseNum(MinFee, Long.MaxValue)
    val tokenPriceGen = Gen.chooseNum(1L, Long.MaxValue)
    forAll(dexFeeGen, tokenPriceGen) { (dexFee, tokenPrice) =>
      val(sellerBox, buyerBox) = matchContractsPair(dexFee, tokenPrice)
      val matches = matchingOrders(Seq(sellerBox), Seq(buyerBox))
      matches.length shouldBe 1
      matches.forall(_.dexFee == dexFee) shouldBe true
    }
  }

  property("many matches") {
    val dexFeeGen = Gen.chooseNum(MinFee, Long.MaxValue)
    val tokenPriceGen = Gen.chooseNum(1L, Long.MaxValue)
    forAll(dexFeeGen, tokenPriceGen) { (dexFee, tokenPrice) =>
      val(sellerBox, buyerBox) = matchContractsPair(dexFee, tokenPrice)
      val matches = matchingOrders(Array.fill(5)(sellerBox), Array.fill(5)(buyerBox))
      matches.length shouldBe 25 // (5 * 5, cartesian product)
      matches.forall(_.dexFee == dexFee) shouldBe true
    }
  }
}
