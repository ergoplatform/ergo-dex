package org.ergoplatform.dex.commands

import org.ergoplatform.appkit.Parameters.MinFee
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.{InputBox, Address, MockInputBox, ErgoContract, ObjectGenerators}
import org.scalacheck.Gen
import org.scalatest.{PropSpec, Matchers}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import sigmastate.Values.SigmaPropConstant
import org.ergoplatform.appkit.ErgoToken

class CancelOrderSpec extends PropSpec
  with Matchers
  with ScalaCheckDrivenPropertyChecks
  with ObjectGenerators {

  private def getOneInputBox(amountToSpent: Long): Seq[InputBox] =
    Seq(MockInputBox(amountToSpent + unsignedLongGen.sample.get))

  def sendToPk(address: Address): ErgoContract =
    new ErgoTreeContract(SigmaPropConstant(address.getPublicKey))

  property("Sell order: if orderBox.value does not cover tx fee and output box value add more input") {
    val orderAuthorAddress = testnetAddressGen.sample.get
    forAll(sellOrderBoxGen(orderAuthorAddress)) { orderBox =>
      val txProto = CancelOrder.txForSellOrder(orderBox, orderAuthorAddress, getOneInputBox)

      val valueToCoverTxFeeAndMinimumTransfer = MinFee * 2
      if (orderBox.getValue >= valueToCoverTxFeeAndMinimumTransfer) {
          txProto.inputBoxes should contain only orderBox
      } else {
          txProto.inputBoxes should contain (orderBox)
      }
    }
  }

  property("Buy order: if orderBox.value does not cover tx fee and output box value add more input") {
    val orderAuthorAddress = testnetAddressGen.sample.get
    forAll(buyOrderBoxGen(orderAuthorAddress)) { orderBox =>
      val txProto = CancelOrder.firstTxForBuyOrder(orderBox, orderAuthorAddress, getOneInputBox)

      val valueToCoverTxFeeAndMinimumTransfer = MinFee * 2
      val valueToCoverSecondTxFeeAndValue = MinFee * 2
      val total = valueToCoverTxFeeAndMinimumTransfer + valueToCoverSecondTxFeeAndValue
      if (orderBox.getValue >= total) {
          txProto.inputBoxes should contain only orderBox
      } else {
          txProto.inputBoxes should contain (orderBox)
      }
    }
  }

  property("cancel wrong contract type (neither sell or buy order) should fail"){
    val orderBox = MockInputBox(validBoxValueGen.sample.get)
    an[RuntimeException] should be thrownBy CancelOrder.firstTxForBuyOrder(orderBox, testnetAddressGen.sample.get, getOneInputBox)
    an[RuntimeException] should be thrownBy CancelOrder.txForSellOrder(orderBox, testnetAddressGen.sample.get, getOneInputBox)
  }

  property("using withdrawal address not in the order contract should fail "){
    val orderAuthorAddress = testnetAddressGen.sample.get
    forAll(Gen.oneOf(sellOrderBoxGen(orderAuthorAddress), buyOrderBoxGen(orderAuthorAddress))) { orderBox =>
      val invalidRecipientAddress = testnetAddressGen.sample.get
      an[RuntimeException] should be thrownBy CancelOrder.txForSellOrder(orderBox, invalidRecipientAddress, getOneInputBox)
      an[RuntimeException] should be thrownBy CancelOrder.firstTxForBuyOrder(orderBox, invalidRecipientAddress, getOneInputBox)
    }
  }

  property("valid outbox for sell order"){
    val orderAuthorAddress = testnetAddressGen.sample.get
    forAll(sellOrderBoxGen(orderAuthorAddress)) { orderBox =>
      val txProto = CancelOrder.txForSellOrder(orderBox, orderAuthorAddress, getOneInputBox)
      txProto.outputBoxes.length shouldBe 1
      val outbox = txProto.outputBoxes.head
      outbox.getValue shouldBe math.max(orderBox.getValue - MinFee, MinFee)
      outbox.tokens should contain only orderBox.getTokens.get(0)
      val expectedOutboxContract = sendToPk(orderAuthorAddress)
      outbox.contract.getErgoTree shouldEqual expectedOutboxContract.getErgoTree
    }
  }

  property("valid txs for buy order"){
    val orderAuthorAddress = testnetAddressGen.sample.get
    forAll(buyOrderBoxGen(orderAuthorAddress)) { orderBox =>
      val expectedTokenToBurn = new ErgoToken(orderBox.getId, 1L)
      val firstTx = CancelOrder.firstTxForBuyOrder(orderBox, orderAuthorAddress, getOneInputBox)
      firstTx.outputBoxes should have size 1
      val expectedOutboxContract = sendToPk(orderAuthorAddress)
      firstTx.outputBoxes.head.contract.getErgoTree shouldEqual expectedOutboxContract.getErgoTree
      firstTx.sendChangeTo shouldEqual orderAuthorAddress
      val feeForSecondTx = MinFee * 2
      val expectedOutboxValue = math.max(orderBox.getValue - MinFee, MinFee + feeForSecondTx)
      firstTx.outputBoxes.head.getValue shouldBe expectedOutboxValue
      // although in this case minted token should be empty
      // as a workaround for https://github.com/ScorexFoundation/sigmastate-interpreter/issues/628
      firstTx.outputBoxes.head.mintToken.isDefined shouldBe true
      firstTx.outputBoxes.head.mintToken.get.token shouldEqual expectedTokenToBurn
      firstTx.outputBoxes.head.tokens shouldBe empty
      firstTx.fee shouldBe MinFee
      firstTx.sendChangeTo shouldEqual orderAuthorAddress

      val outBoxWithToken = firstTx.outputBoxes(0)
      val mockedInputBoxWithTokenToBurn = MockInputBox(outBoxWithToken.getValue, 
        IndexedSeq(outBoxWithToken.mintToken.get.token))
      val secondTx = CancelOrder.txToBurnMintedToken(mockedInputBoxWithTokenToBurn, orderAuthorAddress)

      secondTx.sendChangeTo shouldEqual orderAuthorAddress
      secondTx.inputBoxes should have size 1
      secondTx.inputBoxes.head.getTokens should have size 1
      secondTx.inputBoxes.head.getTokens.get(0) shouldEqual expectedTokenToBurn 
      secondTx.outputBoxes should have size 1
      secondTx.outputBoxes.head.contract.getErgoTree shouldEqual expectedOutboxContract.getErgoTree
      secondTx.outputBoxes.head.getValue shouldBe mockedInputBoxWithTokenToBurn.getValue - MinFee
      // check that minted token from previous tx is NOT present in outputs
      secondTx.outputBoxes.head.tokens should be ('empty)
      secondTx.outputBoxes.head.mintToken.isDefined shouldBe false
      secondTx.fee shouldBe MinFee
      secondTx.sendChangeTo shouldEqual orderAuthorAddress
    }
  }

  property("Sell order: valid tx.fee, outbox value, change address"){
    val orderAuthorAddress = testnetAddressGen.sample.get
    forAll(sellOrderBoxGen(orderAuthorAddress)) { orderBox =>
      val txProto = CancelOrder.txForSellOrder(orderBox, orderAuthorAddress, getOneInputBox)
      txProto.fee should be >= MinFee
      txProto.outputBoxes.foreach(_.getValue should be >= MinFee)
      txProto.sendChangeTo shouldEqual orderAuthorAddress
    }
  }

}
