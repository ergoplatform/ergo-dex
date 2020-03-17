package org.ergoplatform.appkit

import java.util

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
