package org.ergoplatform.dex

import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import ShowOrderBook._
import ListMatchingOrders._
import org.ergoplatform.appkit.{Address, ErgoId, ErgoToken, InputBox, MockInputBox, NetworkType, ObjectGenerators}
import org.ergoplatform.appkit.Parameters.MinFee
import org.scalacheck.Gen

class ShowOrderBookSpec extends PropSpec
  with Matchers
  with ScalaCheckDrivenPropertyChecks
  with ObjectGenerators {

  val tokenId = ergoIdGen.sample.get

  property("empty list (no input)") {
    sellOrders(Seq.empty, tokenId) shouldBe empty
    buyOrders(Seq.empty, tokenId) shouldBe empty
  }

  property("empty list (no valid contracts in inputs)") {
    sellOrders(Seq(MockInputBox(1L)), tokenId) shouldBe empty
    buyOrders(Seq(MockInputBox(1L)), tokenId) shouldBe empty
  }

  property("empty sell order list (no matching token)") {
    val sellerContract = SellerContract.contractInstance(1L,
      testnetAddressGen.sample.get)
    val sellerBox = MockInputBox(ergoIdGen.sample.get, 1L, sellerContract.getErgoTree,
      Seq(new ErgoToken(ergoIdGen.sample.get, 1L)))
    sellOrders(Seq(sellerBox), tokenId) shouldBe empty
  }

  property("empty buy order list (no matching token)") {
    val buyerContract = BuyerContract.contractInstance(new ErgoToken(ergoIdGen.sample.get, 1L),
      testnetAddressGen.sample.get)
    val buyerBox = MockInputBox(ergoIdGen.sample.get, 1L, buyerContract.getErgoTree)
    buyOrders(Seq(buyerBox), tokenId) shouldBe empty
  }

  property("BuyOrder properties") {
    forAll(positiveLongGen, validBoxValueGen) { case (tokenAmount, tokenPriceWithDexFee) =>
      val buyerContract = BuyerContract.contractInstance(new ErgoToken(tokenId, tokenAmount),
        testnetAddressGen.sample.get)
      val buyerBox = MockInputBox(ergoIdGen.sample.get, tokenPriceWithDexFee, buyerContract.getErgoTree)
      val expectedBuyOrder = BuyOrder(buyerBox, tokenAmount, tokenPriceWithDexFee)
      buyOrders(Seq(buyerBox), tokenId) shouldBe Seq(expectedBuyOrder)
    }
  }

  property("SellOrder properties") {
    forAll(positiveLongGen, positiveLongGen, validBoxValueGen) { case (tokenPrice, tokenAmount, dexFee) =>
      val sellerContract = SellerContract.contractInstance(tokenPrice, testnetAddressGen.sample.get)
      val sellerBox = MockInputBox(ergoIdGen.sample.get, dexFee, sellerContract.getErgoTree,
        Seq(new ErgoToken(tokenId, tokenAmount)))
      val expectedSellOrder = SellOrder(sellerBox, tokenAmount, tokenPrice + dexFee)
      sellOrders(Seq(sellerBox), tokenId) shouldBe Seq(expectedSellOrder)
    }
  }

  property("many buy orders, sorting") {
    val buyerContract = BuyerContract.contractInstance(new ErgoToken(tokenId, 1L),
      testnetAddressGen.sample.get)
    val buyerBox = MockInputBox(ergoIdGen.sample.get, 1L, buyerContract.getErgoTree)
    val buyerBox2 = MockInputBox(ergoIdGen.sample.get, 2L, buyerContract.getErgoTree)
    buyOrders(Seq(buyerBox, buyerBox2), tokenId).map(_.tokenPriceWithDexFee) shouldEqual Seq(2L, 1L)
  }

  property("many sell orders, sorting") {
    val sellerContract = SellerContract.contractInstance(1L, testnetAddressGen.sample.get)
    val sellerBox = MockInputBox(ergoIdGen.sample.get, 1L, sellerContract.getErgoTree,
      Seq(new ErgoToken(tokenId, 1L)))
    val sellerBox2 = MockInputBox(ergoIdGen.sample.get, 2L, sellerContract.getErgoTree,
      Seq(new ErgoToken(tokenId, 1L)))
    sellOrders(Seq(sellerBox, sellerBox2), tokenId).map(_.tokenPriceWithDexFee) shouldEqual Seq(3L, 2L)
  }
}