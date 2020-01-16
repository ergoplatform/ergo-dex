package org.ergoplatform.appkit.ergotool.AssetsAtomicExchange

import org.ergoplatform.appkit.ObjectGenerators
import org.ergoplatform.{Height, Outputs}
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import sigmastate.EQ
import sigmastate.Values.{BooleanConstant, ErgoTree, TrueLeaf}
import sigmastate.utxo.SizeOf

class SellerContractSpec extends PropSpec
  with Matchers
  with ScalaCheckDrivenPropertyChecks
  with ObjectGenerators {

  property("contractInstance") {
    val anyAddress = testnetAddressGen.sample.get
    val sellerContract = SellerContract.contractInstance(0,1L, anyAddress)
    sellerContract.getErgoTree.constants should (not be empty)
  }

  property("sellerPkFromTree") {
    val anyAddress = testnetAddressGen.sample.get
    val sellerContract = SellerContract.contractInstance(0, 1L, anyAddress)
    SellerContract.sellerPkFromTree(sellerContract.getErgoTree) shouldEqual Some(anyAddress.getPublicKey)
  }

  property("sellerPkFromTree(wrong type of constants)") {
    val tree = ErgoTree.fromProposition(TrueLeaf)
    tree.constants(0).isInstanceOf[BooleanConstant]
    SellerContract.sellerPkFromTree(tree) shouldEqual None
  }

  property("sellerPkFromTree(no constants)") {
    val tree = ErgoTree.fromProposition(EQ(Height, SizeOf(Outputs)))
    tree.constants shouldBe empty
    SellerContract.sellerPkFromTree(tree) shouldEqual None
  }

  property("tokenPriceFromTree") {
    val anyAddress = testnetAddressGen.sample.get
    val tokenPrice = 9238472L
    val sellerContract = SellerContract.contractInstance(0, tokenPrice, anyAddress)
    SellerContract.tokenPriceFromTree(sellerContract.getErgoTree) shouldEqual Some(tokenPrice)
  }

  property("tokenPriceFromTree(no constants)") {
    val tree = ErgoTree.fromProposition(EQ(Height, SizeOf(Outputs)))
    tree.constants shouldBe empty
    SellerContract.tokenPriceFromTree(tree) shouldEqual None
  }

  property("tokenPriceFromTree(wrong type of all constants)") {
    val tree = ErgoTree.fromProposition(EQ(Height, SizeOf(Outputs)))
    SellerContract.tokenPriceFromTree(
      ErgoTree(tree.header, Array.fill(10)(BooleanConstant(true)), tree.root.right.get)
    ) shouldEqual None
  }

}
