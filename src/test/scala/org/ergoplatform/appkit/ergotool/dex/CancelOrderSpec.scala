package org.ergoplatform.appkit.ergotool.dex

import org.ergoplatform.appkit.Parameters.MinFee
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.{Address, ErgoContract, InputBox, MockInputBox, ObjectGenerators}
import org.scalacheck.Gen
import org.scalatest.{Matchers, PropSpec}
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

  property("if orderBox.value does not cover tx fee and output box value add more input"){
    val orderAuthorAddress = testnetAddressGen.sample.get
    forAll(Gen.oneOf(sellOrderBoxGen(orderAuthorAddress), buyOrderBoxGen(orderAuthorAddress))) { orderBox =>

      val txProto = CancelOrder.createTx(orderBox, orderAuthorAddress, getOneInputBox)

      val valueToCoverTxFeeAndMinimumTransfer = MinFee * 2
      if (orderBox.getValue >= valueToCoverTxFeeAndMinimumTransfer) {
          txProto.inputBoxes should contain only orderBox
      } else {
          txProto.inputBoxes should contain (orderBox)
      }
    }
  }

  property("cancel wrong contract type (neither sell or buy order) should fail"){
    val orderBox = MockInputBox(validBoxValueGen.sample.get)
    an[RuntimeException] should be thrownBy CancelOrder.createTx(orderBox, testnetAddressGen.sample.get, getOneInputBox)
  }

  property("using withdrawal address not in the order contract should fail "){
    val orderAuthorAddress = testnetAddressGen.sample.get
    forAll(Gen.oneOf(sellOrderBoxGen(orderAuthorAddress), buyOrderBoxGen(orderAuthorAddress))) { orderBox =>
      val invalidRecipientAddress = testnetAddressGen.sample.get
      an[RuntimeException] should be thrownBy CancelOrder.createTx(orderBox, invalidRecipientAddress, getOneInputBox)
    }
  }

  property("valid outbox for sell order"){
    val orderAuthorAddress = testnetAddressGen.sample.get
    forAll(sellOrderBoxGen(orderAuthorAddress)) { orderBox =>
      val txProto = CancelOrder.createTx(orderBox, orderAuthorAddress, getOneInputBox)
      txProto.outputBoxes.length shouldBe 1
      val outbox = txProto.outputBoxes.head
      outbox.getValue shouldBe orderBox.getValue - MinFee
      outbox.tokens should contain only orderBox.getTokens.get(0)
      val expectedOutboxContract = sendToPk(orderAuthorAddress)
      outbox.contract.getErgoTree shouldEqual expectedOutboxContract.getErgoTree
    }
  }

  property("valid outbox for buy order"){
    val orderAuthorAddress = testnetAddressGen.sample.get
    forAll(buyOrderBoxGen(orderAuthorAddress)) { orderBox =>
      val txProto = CancelOrder.createTx(orderBox, orderAuthorAddress, getOneInputBox)
      txProto.outputBoxes.length shouldBe 1
      val outbox = txProto.outputBoxes.head
      outbox.getValue shouldBe orderBox.getValue - MinFee
      // although in this case outbox.tokes should be empty
      // as a workaround for https://github.com/ScorexFoundation/sigmastate-interpreter/issues/628
      // box.tokens cannot be empty
      //      outbox.tokens shouldBe empty
      val expectedOutboxContract = sendToPk(orderAuthorAddress)
      outbox.contract.getErgoTree shouldEqual expectedOutboxContract.getErgoTree
    }
  }

  property("valid tx.fee"){
    val orderAuthorAddress = testnetAddressGen.sample.get
    forAll(Gen.oneOf(sellOrderBoxGen(orderAuthorAddress), buyOrderBoxGen(orderAuthorAddress))) { orderBox =>
      val txProto = CancelOrder.createTx(orderBox, orderAuthorAddress, getOneInputBox)
      txProto.fee shouldBe MinFee
    }
  }

  property("valid tx.sendChangeTo address"){
    val orderAuthorAddress = testnetAddressGen.sample.get
    forAll(Gen.oneOf(sellOrderBoxGen(orderAuthorAddress), buyOrderBoxGen(orderAuthorAddress))) { orderBox =>
      val txProto = CancelOrder.createTx(orderBox, orderAuthorAddress, getOneInputBox)
      txProto.sendChangeTo shouldEqual orderAuthorAddress
    }
  }
}

