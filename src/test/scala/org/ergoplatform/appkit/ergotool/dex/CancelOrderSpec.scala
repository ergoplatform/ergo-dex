package org.ergoplatform.appkit.ergotool.dex

import org.ergoplatform.appkit.Parameters.MinFee
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.{Address, ErgoContract, InputBox, MockInputBox, ObjectGenerators}
import org.scalacheck.Gen
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import sigmastate.Values.SigmaPropConstant

import scala.util.Failure

class CancelOrderSpec extends PropSpec
  with Matchers
  with ScalaCheckDrivenPropertyChecks
  with ObjectGenerators {

  private def getOneInputBox(amountToSpent: Long): Seq[InputBox] =
    Seq(MockInputBox(amountToSpent + unsignedLongGen.sample.get))

  // TODO: extract to use in other commands
  def sendToPk(address: Address): ErgoContract =
    new ErgoTreeContract(SigmaPropConstant(address.getPublicKey))

  property("if orderBox.value does not cover tx fee and output box value add more input"){
    val orderAuthorAddress = testnetAddressGen.sample.get
    forAll(Gen.oneOf(sellOrderBoxGen(orderAuthorAddress), buyOrderBoxGen(orderAuthorAddress))) { orderBox =>

      val txProto = CancelOrder.createTx(orderBox, orderAuthorAddress, getOneInputBox).get

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
    CancelOrder.createTx(orderBox, testnetAddressGen.sample.get, getOneInputBox) shouldBe
      a [Failure[_]]
  }

  property("using withdrawal address not in the order contract should fail "){
    val orderAuthorAddress = testnetAddressGen.sample.get
    forAll(Gen.oneOf(sellOrderBoxGen(orderAuthorAddress), buyOrderBoxGen(orderAuthorAddress))) { orderBox =>
      val invalidRecipientAddress = testnetAddressGen.sample.get
      CancelOrder.createTx(orderBox, invalidRecipientAddress, getOneInputBox) shouldBe
        a [Failure[_]]
    }
  }

  property("valid outbox for sell order"){
    val orderAuthorAddress = testnetAddressGen.sample.get
    forAll(sellOrderBoxGen(orderAuthorAddress)) { orderBox =>
      val txProto = CancelOrder.createTx(orderBox, orderAuthorAddress, getOneInputBox).get
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
      val txProto = CancelOrder.createTx(orderBox, orderAuthorAddress, getOneInputBox).get
      txProto.outputBoxes.length shouldBe 1
      val outbox = txProto.outputBoxes.head
      outbox.getValue shouldBe orderBox.getValue - MinFee
      outbox.tokens shouldBe empty
      val expectedOutboxContract = sendToPk(orderAuthorAddress)
      outbox.contract.getErgoTree shouldEqual expectedOutboxContract.getErgoTree
    }
  }

  property("valid tx.fee"){
    val orderAuthorAddress = testnetAddressGen.sample.get
    forAll(Gen.oneOf(sellOrderBoxGen(orderAuthorAddress), buyOrderBoxGen(orderAuthorAddress))) { orderBox =>
      val txProto = CancelOrder.createTx(orderBox, orderAuthorAddress, getOneInputBox).get
      txProto.fee shouldBe MinFee
    }
  }

  property("valid tx.sendChangeTo address"){
    val orderAuthorAddress = testnetAddressGen.sample.get
    forAll(Gen.oneOf(sellOrderBoxGen(orderAuthorAddress), buyOrderBoxGen(orderAuthorAddress))) { orderBox =>
      val txProto = CancelOrder.createTx(orderBox, orderAuthorAddress, getOneInputBox).get
      txProto.sendChangeTo shouldEqual orderAuthorAddress
    }
  }
}

