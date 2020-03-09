package org.ergoplatform.appkit.ergotool

import org.ergoplatform.appkit.ErgoClient
import org.ergoplatform.appkit.BlockchainContext

case class MockedErgoClientProxy(val mockedBlockchainCtx: BlockchainContext) extends ErgoClient {

  override def execute[T](f: java.util.function.Function[BlockchainContext, T]):T = {
    f(mockedBlockchainCtx)
  }
}
