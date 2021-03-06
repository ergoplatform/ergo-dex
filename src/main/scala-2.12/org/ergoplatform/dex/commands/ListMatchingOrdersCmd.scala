package org.ergoplatform.dex.commands

import org.ergoplatform.appkit.JavaHelpers._
import org.ergoplatform.appkit.Parameters.MinFee
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.cli.AppContext
import org.ergoplatform.appkit.commands.{RunWithErgoClient, CmdDescriptor, Cmd}
import org.ergoplatform.appkit.config.ErgoToolConfig

/** Shows matching buyer and seller orders for AssetsAtomicExchange
  *
  * Steps:<br/>
  * 1) request storage password from the user<br/>
  * 2) read storage file, unlock using password and get secret<br/>
  * 3) get master public key and compute sender's address<br/>
  * 4) finds seller and buyer boxes with matching orders and lists them sorting by DEX fee
  *
  */
case class ListMatchingOrdersCmd(toolConf: ErgoToolConfig,
                                 name: String) extends Cmd with RunWithErgoClient {

  override def runWithClient(ergoClient: ErgoClient, runCtx: AppContext): Unit = {
    val console = runCtx.console
    ergoClient.execute(ctx => {
      val sellerHolderBoxes = loggedStep(s"Loading seller boxes", console) {
        ctx.getUnspentBoxesForErgoTreeTemplate(SellerContract.contractTemplate).convertTo[IndexedSeq[InputBox]]
      }
      val buyerHolderBoxes = loggedStep(s"Loading buyer boxes", console) {
        ctx.getUnspentBoxesForErgoTreeTemplate(BuyerContract.contractTemplate).convertTo[IndexedSeq[InputBox]]
      }
      val matchingOrderPairs = ListMatchingOrders
        .matchingOrders(sellerHolderBoxes, buyerHolderBoxes)
      console.println("Seller                                                            Buyer                                                             DEX fee(nanoERG)")
      matchingOrderPairs.foreach { p =>
        console.println(s"${p.seller.getId}, ${p.buyer.getId}, ${p.dexFee}")
      }
    })
  }
}

object ListMatchingOrdersCmd extends CmdDescriptor(
  name = "dex:ListMatchingOrders", cmdParamSyntax = "",
  description = "show matching token seller's and buyer's orders") {

  override def createCmd(ctx: AppContext): Cmd = {
    ListMatchingOrdersCmd(ctx.toolConf, name)
  }

}

object ListMatchingOrders {

  case class MatchingOrder(seller: InputBox, buyer: InputBox, dexFee: Long)

  def matchingOrders(sellerBoxes: Seq[InputBox], buyerBoxes: Seq[InputBox]): Seq[MatchingOrder] =
    sellerBoxes
      .flatMap { sellerBox =>
        for {
          sellerTokenPrice <- SellerContract.tokenPriceFromTree(sellerBox.getErgoTree)
          sellerToken <- sellerBox.getTokens
            .convertTo[IndexedSeq[ErgoToken]]
            .headOption
          matchingOrders = buyerBoxes
            .filter { buyerBox =>
              BuyerContract.tokenFromContractTree(buyerBox.getErgoTree)
                .exists{ buyerToken =>
                  sellerToken.getId == buyerToken.getId &&
                    sellerToken.getValue >= buyerToken.getValue }
            }
            .map { buyerBox =>
              val dexTxFee = MinFee
              val dexFee = buyerBox.getValue - sellerTokenPrice + sellerBox.getValue - dexTxFee
              MatchingOrder(sellerBox, buyerBox, dexFee)
            }
            .filter(_.dexFee >= MinFee)
        } yield matchingOrders
      }
      .flatten
      .sortBy(_.dexFee)

}
