package org.ergoplatform.dex.commands

import org.ergoplatform.appkit.Parameters.MinFee
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.{InputBox, Address, MockInputBox, ErgoContract, ObjectGenerators}
import org.scalacheck.Gen
import org.scalatest.{PropSpec, Matchers}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import sigmastate.Values.SigmaPropConstant

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

/*
  property("valid txs for buy order"){
    val orderAuthorAddress = testnetAddressGen.sample.get
    forAll(buyOrderBoxGen(orderAuthorAddress)) { orderBox =>
      val txProtos = CancelOrder.createTx(orderBox, orderAuthorAddress, getOneInputBox)
      txProtos.length shouldBe 2
      val firstTx = txProtos(0)
      val secondTx = txProtos(1)

      firstTx.outputBoxes.length shouldBe 1
      val expectedOutboxContract = sendToPk(orderAuthorAddress)
      firstTx.outputBoxes.head.contract.getErgoTree shouldEqual expectedOutboxContract.getErgoTree
      firstTx.sendChangeTo shouldEqual orderAuthorAddress
      firstTx.outputBoxes.head.getValue shouldBe (orderBox.getValue - MinFee)
      // although in this case minted token should be empty
      // as a workaround for https://github.com/ScorexFoundation/sigmastate-interpreter/issues/628
      firstTx.outputBoxes.head.mintToken.isDefined shouldBe true
      val mintedToken = firstTx.outputBoxes.head.mintToken.get.token
      firstTx.outputBoxes.head.tokens shouldBe empty

      secondTx.sendChangeTo shouldEqual orderAuthorAddress
      // TODO check that minted token from previous tx is present in inputs
      secondTx.outputBoxes.length shouldBe 1
      secondTx.outputBoxes.head.contract.getErgoTree shouldEqual expectedOutboxContract.getErgoTree
      secondTx.outputBoxes.head.getValue shouldBe (MinFee)
      // check that minted token from previous tx is NOT present in outputs
      secondTx.outputBoxes.forall(!_.tokens.exists(_.equals(mintedToken)))
      secondTx.outputBoxes.head.mintToken.isDefined shouldBe false
    }
  }

  property("valid tx.fee"){
    val orderAuthorAddress = testnetAddressGen.sample.get
    forAll(Gen.oneOf(sellOrderBoxGen(orderAuthorAddress), buyOrderBoxGen(orderAuthorAddress))) { orderBox =>
      val txProto = CancelOrder.createTx(orderBox, orderAuthorAddress, getOneInputBox).head
      txProto.fee shouldBe MinFee
    }
  }

  property("valid tx.sendChangeTo address"){
    val orderAuthorAddress = testnetAddressGen.sample.get
    forAll(Gen.oneOf(sellOrderBoxGen(orderAuthorAddress), buyOrderBoxGen(orderAuthorAddress))) { orderBox =>
      val txProto = CancelOrder.createTx(orderBox, orderAuthorAddress, getOneInputBox).head
      txProto.sendChangeTo shouldEqual orderAuthorAddress
    }
  }
  */
}

