package org.ergoplatform.appkit.ergotool.dex

import org.ergoplatform.appkit.JavaHelpers._
import org.ergoplatform.appkit.Parameters.MinFee
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.ergotool.{AppContext, Cmd, CmdDescriptor, RunWithErgoClient}

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

  private lazy val sellerContractTemplate: ErgoTreeTemplate = {
    val anyAddress = Address.create("9f4QF8AD1nQ3nJahQVkMj8hFSVVzVom77b52JU7EW71Zexg6N8v")
    val sellerContract = SellerContract.contractInstance(0,
      0L, anyAddress)
    ErgoTreeTemplate.fromErgoTree(sellerContract.getErgoTree)
  }

  private lazy val buyerContractTemplate: ErgoTreeTemplate = {
    val anyAddress = Address.create("9f4QF8AD1nQ3nJahQVkMj8hFSVVzVom77b52JU7EW71Zexg6N8v")
    val token = new ErgoToken("21f84cf457802e66fb5930fb5d45fbe955933dc16a72089bf8980797f24e2fa1",
      0L)
    val buyerContract = BuyerContract.contractInstance(0, token, anyAddress)
    ErgoTreeTemplate.fromErgoTree(buyerContract.getErgoTree)
  }

  override def runWithClient(ergoClient: ErgoClient, runCtx: AppContext): Unit = {
    val console = runCtx.console
    ergoClient.execute(ctx => {
      val sellerHolderBoxes = loggedStep(s"Loading seller boxes", console) {
        ctx.getUnspentBoxesForErgoTreeTemplate(sellerContractTemplate).convertTo[IndexedSeq[InputBox]]
      }
      val buyerHolderBoxes = loggedStep(s"Loading buyer boxes", console) {
        ctx.getUnspentBoxesForErgoTreeTemplate(buyerContractTemplate).convertTo[IndexedSeq[InputBox]]
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

  override def parseCmd(ctx: AppContext): Cmd = {
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
          sellerToken <- sellerBox .getTokens
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

