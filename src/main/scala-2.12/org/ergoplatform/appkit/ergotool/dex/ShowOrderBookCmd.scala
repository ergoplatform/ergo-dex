package org.ergoplatform.appkit.ergotool.dex

import org.ergoplatform.appkit.JavaHelpers._
import org.ergoplatform.appkit.Parameters.MinFee
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.ergotool.{AppContext, Cmd, CmdDescriptor, RunWithErgoClient}

/** Shows order book for AssetsAtomicExchange
  */
case class ShowOrderBookCmd(toolConf: ErgoToolConfig,
                                 name: String,
                                 tokenId: ErgoId) extends Cmd with RunWithErgoClient {

  override def runWithClient(ergoClient: ErgoClient, runCtx: AppContext): Unit = {
    val console = runCtx.console
    ergoClient.execute(ctx => {
      val sellerHolderBoxes = loggedStep(s"Loading seller boxes", console) {
        ctx.getUnspentBoxesForErgoTreeTemplate(SellerContract.contractTemplate).convertTo[IndexedSeq[InputBox]]
      }
      val buyerHolderBoxes = loggedStep(s"Loading buyer boxes", console) {
        ctx.getUnspentBoxesForErgoTreeTemplate(BuyerContract.contractTemplate).convertTo[IndexedSeq[InputBox]]
      }
      val sellOrders = ShowOrderBook.sellOrders(sellerHolderBoxes, tokenId)
        .sortBy(_.tokenPriceWithDexFee)
        .reverse

      val buyOrders = ShowOrderBook.buyOrders(buyerHolderBoxes, tokenId)
        .sortBy(_.tokenPriceWithDexFee)
        .reverse

      console.println("Sell orders:")
      console.println("Amount   Total")
      sellOrders.foreach { o => console.println(f"${o.tokenAmount}%8d ${o.tokenPriceWithDexFee}") }

      console.println("Buy orders:")
      console.println("Amount   Total")
      buyOrders.foreach { o => console.println(f"${o.tokenAmount}%8d ${o.tokenPriceWithDexFee}") }
    })
  }
}

object ShowOrderBookCmd extends CmdDescriptor(
  name = "dex:ListMatchingOrders", cmdParamSyntax = "",
  description = "show matching token seller's and buyer's orders") {

  override def createCmd(ctx: AppContext): Cmd = {
    ListMatchingOrdersCmd(ctx.toolConf, name)
  }
}

object ShowOrderBook {

  case class SellOrder(seller: InputBox, tokenAmount: Long, tokenPriceWithDexFee: Long)
  case class BuyOrder(buyer: InputBox, tokenAmount: Long, tokenPriceWithDexFee: Long)

  def sellOrders(sellerBoxes: Seq[InputBox], tokenId: ErgoId): Seq[SellOrder] = 
    sellerBoxes
      .flatMap { sellerBox =>
        for {
          tokenPrice <- SellerContract.tokenPriceFromTree(sellerBox.getErgoTree)
          token <- sellerBox.getTokens
            .convertTo[IndexedSeq[ErgoToken]]
            .filter(_.getId().equals(tokenId))
            .headOption
          tokenPriceWithDexFee = tokenPrice + sellerBox.getValue()
        } yield SellOrder(sellerBox, token.getValue(), tokenPriceWithDexFee)
      }

  def buyOrders(buyerBoxes: Seq[InputBox], tokenId: ErgoId): Seq[BuyOrder] = 
    buyerBoxes
      .flatMap { buyerBox =>
        for {
          token <- BuyerContract.tokenFromContractTree(buyerBox.getErgoTree)
            .filter(_.getId().equals(tokenId))
          tokenPriceWithDexFee = buyerBox.getValue()
        } yield BuyOrder(buyerBox, token.getValue(), tokenPriceWithDexFee)
      }

}