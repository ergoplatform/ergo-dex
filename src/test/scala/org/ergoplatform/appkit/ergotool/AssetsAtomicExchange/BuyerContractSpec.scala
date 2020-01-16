package org.ergoplatform.appkit.ergotool.AssetsAtomicExchange

import org.ergoplatform.appkit.{ErgoToken, ObjectGenerators}
import org.ergoplatform.{Height, Outputs}
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import sigmastate.EQ
import sigmastate.Values.{BooleanConstant, ByteArrayConstant, ErgoTree, IntConstant, TrueLeaf}
import sigmastate.utxo.SizeOf

class BuyerContractSpec extends PropSpec
  with Matchers
  with ScalaCheckDrivenPropertyChecks
  with ObjectGenerators {

  property("contractInstance") {
    val anyAddress = testnetAddressGen.sample.get
    val token = new ErgoToken(ergoIdGen.sample.get, 1L)
    val buyerContract = BuyerContract.contractInstance(0, token, anyAddress)
    buyerContract.getErgoTree.constants should (not be empty)
  }

  property("buyerPkFromTree") {
    val anyAddress = testnetAddressGen.sample.get
    val token = new ErgoToken(ergoIdGen.sample.get, 1L)
    val buyerContract = BuyerContract.contractInstance(0, token, anyAddress)
    BuyerContract.buyerPkFromTree(buyerContract.getErgoTree) shouldEqual Some(anyAddress.getPublicKey)
  }

  property("buyerPkFromTree(wrong type of constants)") {
    val tree = ErgoTree.fromProposition(TrueLeaf)
    tree.constants(0).isInstanceOf[BooleanConstant]
    BuyerContract.buyerPkFromTree(tree) shouldEqual None
  }

  property("buyerPkFromTree(no constants)") {
    val tree = ErgoTree.fromProposition(EQ(Height, SizeOf(Outputs)))
    tree.constants shouldBe empty
    BuyerContract.buyerPkFromTree(tree) shouldEqual None
  }

  property("tokenFromContractTree") {
    val anyAddress = testnetAddressGen.sample.get
    val token = new ErgoToken(ergoIdGen.sample.get, 1L)
    val buyerContract = BuyerContract.contractInstance(0, token, anyAddress)
    BuyerContract.tokenFromContractTree(buyerContract.getErgoTree) shouldEqual Some(token)
  }

  property("tokenFromContractTree(no constants)") {
    val tree = ErgoTree.fromProposition(EQ(Height, SizeOf(Outputs)))
    tree.constants shouldBe empty
    BuyerContract.tokenFromContractTree(tree) shouldEqual None
  }

  property("tokenFromContractTree(wrong type of all constants)") {
    val tree = ErgoTree.fromProposition(EQ(Height, SizeOf(Outputs)))
    BuyerContract.tokenFromContractTree(
      ErgoTree(tree.header, Array.fill(10)(BooleanConstant(true)), tree.root.right.get)
    ) shouldEqual None
  }

  property("tokenFromContractTree(only token id constant has correct type)") {
    val tree = ErgoTree.fromProposition(EQ(Height, SizeOf(Outputs)))
    BuyerContract.tokenFromContractTree(
      ErgoTree(tree.header, Array.fill(10)(ByteArrayConstant(Array.fill(10)(1.toByte))), tree.root.right.get)
    ) shouldEqual None
  }
}
