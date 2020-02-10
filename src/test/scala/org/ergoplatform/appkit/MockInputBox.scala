package org.ergoplatform.appkit

import java.lang.{Long => JLong}
import java.util.{List => JList}

import org.ergoplatform.appkit.JavaHelpers._
import org.ergoplatform.appkit.Iso._
import sigmastate.Values.{ErgoTree, TrueLeaf}

case class MockInputBox(getId: ErgoId,
                        getValue: JLong,
                        getTokens: JList[ErgoToken],
                        getErgoTree: ErgoTree) extends InputBox {
  override def toJson(prettyPrint: Boolean): String = ???
  override def getRegisters: (JList[ErgoValue[_]]) = ???
}

object MockInputBox extends ObjectGenerators {

  def apply(getId: ErgoId, getValue: Long,
            getErgoTree: ErgoTree, getTokens: Seq[ErgoToken] = Seq.empty): MockInputBox = {
    val tokens = JListToIndexedSeq(identityIso[ErgoToken]).from(getTokens.toIndexedSeq)
    new MockInputBox(getId, getValue, tokens, getErgoTree)
  }

  def apply(getValue: Long): MockInputBox =
    MockInputBox(ergoIdGen.sample.get, getValue, ErgoTree.fromProposition(TrueLeaf))

}

