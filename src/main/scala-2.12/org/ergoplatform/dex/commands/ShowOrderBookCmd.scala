package org.ergoplatform.dex.commands

import org.ergoplatform.appkit.JavaHelpers._
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.cli.AppContext
import org.ergoplatform.appkit.commands._

/** Order book for AssetsAtomicExchange
 *  Show sell and buy orders(token amount, total order price with DEX fee) for a given token
 *  sorted by total order price(descending)
 *  @param tokenId token id to filter orders
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
      val buyOrders = ShowOrderBook.buyOrders(buyerHolderBoxes, tokenId)

      console.println(s"Order book for token $tokenId:")
      console.println("Sell orders:")
      console.println("Token Amount   Erg Amount(including DEX fee)")
      sellOrders.foreach { o => console.println(f"${o.tokenAmount}%12d   ${o.tokenPriceWithDexFee}") }

      console.println("Buy orders:")
      console.println("Token Amount   Erg Amount(including DEX fee)")
      buyOrders.foreach { o => console.println(f"${o.tokenAmount}%12d   ${o.tokenPriceWithDexFee}") }
    })
  }
}

object ShowOrderBookCmd extends CmdDescriptor(
  name = "dex:ShowOrderBook", cmdParamSyntax = "<tokenId>",
  description = "show order book for a given token (sell and buy orders sorted by total price(descending)") {

  override val parameters: Seq[CmdParameter] = Array(
    CmdParameter("tokenId", ErgoIdPType,
      "token id to filter sell and buy orders")
  )

  override def createCmd(ctx: AppContext): Cmd = {
    val Seq(tokenId: ErgoId) = ctx.cmdParameters
    ShowOrderBookCmd(ctx.toolConf, name, tokenId)
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
    .sortBy(_.tokenPriceWithDexFee)
    .reverse

  def buyOrders(buyerBoxes: Seq[InputBox], tokenId: ErgoId): Seq[BuyOrder] =
    buyerBoxes
      .flatMap { buyerBox =>
        for {
          token <- BuyerContract.tokenFromContractTree(buyerBox.getErgoTree)
            .filter(_.getId().equals(tokenId))
          tokenPriceWithDexFee = buyerBox.getValue()
        } yield BuyOrder(buyerBox, token.getValue(), tokenPriceWithDexFee)
      }
    .sortBy(_.tokenPriceWithDexFee)
    .reverse

}
