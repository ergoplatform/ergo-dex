package org.ergoplatform.appkit.ergotool

import org.ergoplatform.appkit.ErgoClient
import org.ergoplatform.appkit.BlockchainContext
import org.ergoplatform.appkit.FileMockedErgoClient
import java.util

case class ErgoClientMock(val blockchainCtx: BlockchainContextMock) extends ErgoClient {

  override def execute[T](f: util.function.Function[BlockchainContext, T]):T = {
    f(blockchainCtx)
  }
}


class FileMockedErgoClientWithStubbedCtx(nodeResponses: util.List[String], 
   explorerResponses: util.List[String], 
   ctxStubber: BlockchainContext => BlockchainContext) extends 
   FileMockedErgoClient(nodeResponses, explorerResponses) {

  override def execute[T](f: util.function.Function[BlockchainContext,T]): T = { 
    val newF = {ctx: BlockchainContext => 
      val stubbedCtx = ctxStubber(ctx)
      f(stubbedCtx)
    }
    super.execute(newF)
  }
}
