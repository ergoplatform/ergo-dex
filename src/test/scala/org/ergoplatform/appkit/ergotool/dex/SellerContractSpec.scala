package org.ergoplatform.appkit.ergotool.dex

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

  property("sellerPkFromTree") {
    forAll(testnetAddressGen, unsignedLongGen) { (address, tokenPrice) =>
      val sellerContract = SellerContract.contractInstance(tokenPrice, address)
      SellerContract.sellerPkFromTree(sellerContract.getErgoTree) shouldEqual Some(address.getPublicKey)
    }
  }

  property("sellerPkFromTree(wrong type of constants)") {
    val tree = ErgoTree.fromProposition(TrueLeaf)
    tree.constants(0) shouldEqual TrueLeaf
    SellerContract.sellerPkFromTree(tree) shouldEqual None
  }

  property("sellerPkFromTree(no constants)") {
    val tree = ErgoTree.fromProposition(EQ(Height, SizeOf(Outputs)))
    tree.constants shouldBe empty
    SellerContract.sellerPkFromTree(tree) shouldEqual None
  }

  property("tokenPriceFromTree") {
    forAll(testnetAddressGen, unsignedLongGen) { (address, tokenPrice) =>
      val sellerContract = SellerContract.contractInstance(tokenPrice, address)
      SellerContract.tokenPriceFromTree(sellerContract.getErgoTree) shouldEqual Some(tokenPrice)
    }
  }

  property("tokenPriceFromTree(no constants)") {
    val tree = ErgoTree.fromProposition(EQ(Height, SizeOf(Outputs)))
    tree.constants shouldBe empty
    SellerContract.tokenPriceFromTree(tree) shouldEqual None
  }

  property("tokenPriceFromTree(wrong type of all constants)") {
    val tree = ErgoTree.fromProposition(EQ(Height, SizeOf(Outputs)))
    val booleanConstants = Array.fill(10)(BooleanConstant(true))
    val treeWithBooleanConstants = ErgoTree(tree.header, booleanConstants, tree.root.right.get)
    SellerContract.tokenPriceFromTree(treeWithBooleanConstants) shouldEqual None
  }

}
