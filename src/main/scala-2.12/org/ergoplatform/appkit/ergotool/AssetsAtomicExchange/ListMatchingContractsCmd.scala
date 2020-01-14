package org.ergoplatform.appkit.ergotool.AssetsAtomicExchange

import org.ergoplatform.appkit.JavaHelpers._
import org.ergoplatform.appkit.Parameters.MinFee
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.ergotool.{AppContext, Cmd, CmdDescriptor, RunWithErgoClient}

/** Shows matching buyer and seller contracts for AssetsAtomicExchange
  *
  * Steps:<br/>
  * 1) request storage password from the user<br/>
  * 2) read storage file, unlock using password and get secret<br/>
  * 3) get master public key and compute sender's address<br/>
  * 4) finds seller and buyer boxes with matching orders and lists them sorting by DEX fee
  *
  */
case class ListMatchingContractsCmd(toolConf: ErgoToolConfig,
                                    name: String) extends Cmd with RunWithErgoClient {

  private lazy val sellerContractTemplate: ErgoTreeTemplate = {
    val anyAddress = Address.create("9f4QF8AD1nQ3nJahQVkMj8hFSVVzVom77b52JU7EW71Zexg6N8v")
    val sellerContract = SellerContract.contractInstance(0,
      0L, anyAddress.getPublicKey)
    ErgoTreeTemplate.fromErgoTree(sellerContract.getErgoTree)
  }

  private lazy val buyerContractTemplate: ErgoTreeTemplate = {
    val anyAddress = Address.create("9f4QF8AD1nQ3nJahQVkMj8hFSVVzVom77b52JU7EW71Zexg6N8v")
    val token = new ErgoToken("21f84cf457802e66fb5930fb5d45fbe955933dc16a72089bf8980797f24e2fa1",
      0L)
    val buyerContract = BuyerContract.contractInstance(0, token, anyAddress.getPublicKey)
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
      val matchingContractPairs = ListMatchingContracts
        .matchingContracts(sellerHolderBoxes, buyerHolderBoxes)
      console.println("Seller                                                       Buyer                                            DEX fee")
      matchingContractPairs.foreach { p =>
        console.println(s"${p.seller.getId}, ${p.buyer.getId}, ${p.dexFee}")
      }
    })
  }
}

object ListMatchingContractsCmd extends CmdDescriptor(
  name = "AssetAtomicExchangeList", cmdParamSyntax = "",
  description = "show matching token seller's and buyer's contracts") {

  override def parseCmd(ctx: AppContext): Cmd = {
    ListMatchingContractsCmd(ctx.toolConf, name)
  }

}

object ListMatchingContracts {

  case class MatchingContract(seller: InputBox, buyer: InputBox, dexFee: Long)

  def matchingContracts(sellerBoxes: Seq[InputBox], buyerBoxes: Seq[InputBox]): Seq[MatchingContract] =
    sellerBoxes
      .flatMap { sb =>
        val sellerTokenPrice = SellerContract.tokenPriceFromTree(sb.getErgoTree)
        buyerBoxes
          .filter { bb =>
            val sellerToken = sb.getTokens.get(0)
            val buyerToken = BuyerContract.tokenFromContractTree(bb.getErgoTree)
            sellerToken.getId == buyerToken.getId && sellerToken.getValue >= buyerToken.getValue &&
              bb.getValue >= sellerTokenPrice
          }
          .map { bb =>
            val dexFee = bb.getValue - sellerTokenPrice + sb.getValue - MinFee
            MatchingContract(sb, bb, dexFee)
          }
      }
      .sortBy(_.dexFee)

}

