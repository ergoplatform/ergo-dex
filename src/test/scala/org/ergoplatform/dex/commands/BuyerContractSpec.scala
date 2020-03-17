package org.ergoplatform.dex.commands

import org.ergoplatform.appkit.ObjectGenerators
import org.ergoplatform.{Height, Outputs}
import org.scalatest.{PropSpec, Matchers}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import sigmastate.EQ
import sigmastate.Values.{TrueLeaf, ByteArrayConstant, ErgoTree, BooleanConstant}
import sigmastate.utxo.SizeOf

class BuyerContractSpec extends PropSpec
  with Matchers
  with ScalaCheckDrivenPropertyChecks
  with ObjectGenerators {

  property("buyerPkFromTree") {
    forAll(testnetAddressGen, tokenGen) { (address, token) =>
      val buyerContract = BuyerContract.contractInstance(token, address)
      BuyerContract.buyerPkFromTree(buyerContract.getErgoTree) shouldEqual Some(address.getPublicKey)
    }
  }

  property("buyerPkFromTree(wrong type of constants)") {
    val tree = ErgoTree.fromProposition(TrueLeaf)
    tree.constants(0) shouldEqual TrueLeaf
    BuyerContract.buyerPkFromTree(tree) shouldEqual None
  }

  property("buyerPkFromTree(no constants)") {
    val tree = ErgoTree.fromProposition(EQ(Height, SizeOf(Outputs)))
    tree.constants shouldBe empty
    BuyerContract.buyerPkFromTree(tree) shouldEqual None
  }

  property("tokenFromContractTree") {
    forAll(testnetAddressGen, tokenGen) { (address, token) =>
      val buyerContract = BuyerContract.contractInstance(token, address)
      BuyerContract.tokenFromContractTree(buyerContract.getErgoTree) shouldEqual Some(token)
    }
  }

  property("tokenFromContractTree(no constants)") {
    val tree = ErgoTree.fromProposition(EQ(Height, SizeOf(Outputs)))
    tree.constants shouldBe empty
    BuyerContract.tokenFromContractTree(tree) shouldEqual None
  }

  property("tokenFromContractTree(wrong type of all constants)") {
    val tree = ErgoTree.fromProposition(EQ(Height, SizeOf(Outputs)))
    val booleanConstants = Array.fill(10)(BooleanConstant(true))
    val treeWithBooleanConstants = ErgoTree(tree.header, booleanConstants, tree.root.right.get)
    BuyerContract.tokenFromContractTree(treeWithBooleanConstants) shouldEqual None
  }

  property("tokenFromContractTree(only token id constant has correct type)") {
    val tree = ErgoTree.fromProposition(EQ(Height, SizeOf(Outputs)))
    val byteArrayConstants = Array.fill(10)(ByteArrayConstant(Array.fill(10)(1.toByte)))
    val treeWithByteArrayConstants = ErgoTree(tree.header, byteArrayConstants, tree.root.right.get)
    BuyerContract.tokenFromContractTree(treeWithByteArrayConstants) shouldEqual None
  }
}
