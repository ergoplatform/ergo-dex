package org.ergoplatform.dex.commands

import java.io.File

import org.ergoplatform.appkit.JavaHelpers._
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.cli.AppContext
import org.ergoplatform.appkit.commands._
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.ErgoAddressEncoder
import org.ergoplatform.P2PKAddress
import org.ergoplatform.Pay2SHAddress
import org.ergoplatform.Pay2SAddress

/** Shows buy and sell orders created from the given address
  *
  * @param address address to filter buy and sell orders
  */
case class ListMyOrdersCmd(toolConf: ErgoToolConfig,
                           name: String,
                           address: Address) extends Cmd with RunWithErgoClient {

  override def runWithClient(ergoClient: ErgoClient, runCtx: AppContext): Unit = {
    val console = runCtx.console
    ergoClient.execute(ctx => {
      val addressEncoder = ErgoAddressEncoder(ctx.getNetworkType().networkPrefix)
      val pubkey = address.getPublicKey()

      val sellerHolderBoxes = loggedStep(s"Loading seller boxes", console) {
        ctx.getUnspentBoxesForErgoTreeTemplate(SellerContract.contractTemplate).convertTo[IndexedSeq[InputBox]]
      }
        .filter { b => SellerContract.sellerPkFromTree(b.getErgoTree).contains(pubkey) }

      val buyerHolderBoxes = loggedStep(s"Loading buyer boxes", console) {
        ctx.getUnspentBoxesForErgoTreeTemplate(BuyerContract.contractTemplate).convertTo[IndexedSeq[InputBox]]
      }
        .filter { b => BuyerContract.buyerPkFromTree(b.getErgoTree).contains(pubkey) }

      console.println(s"Orders created from address $address :")
      console.println("Sell:")
      console.println("Box id                                                           Token ID                                                         Token amount  Token price  Box value")
      sellerHolderBoxes.foreach { b =>
        require(b.getTokens.size > 0, s"no tokens in sell order box id ${b.getId}")
        val token = b.getTokens.get(0)
        val tokenPrice = SellerContract.tokenPriceFromTree(b.getErgoTree)
          .getOrElse(error(s"cannot find token price in the ergo tree of sell order box id ${b.getId}}"))
        console.println(s"${b.getId} ${token.getId} ${token.getValue}            $tokenPrice     ${b.getValue}")
      }

      console.println("Buy:")
      console.println("Box id                                                           Token ID                                                         Token amount  Box value")
      buyerHolderBoxes.foreach { b =>
        val token = BuyerContract.tokenFromContractTree(b.getErgoTree)
          .getOrElse(error(s"cannot find token in the ergo tree of buy order box id ${b.getId}"))
        console.println(s"${b.getId} ${token.getId} ${token.getValue}            ${b.getValue}")
      }
    })
  }
}

object ListMyOrdersCmd extends CmdDescriptor(
  name = "dex:ListMyOrders", cmdParamSyntax = "<address>",
  description = "show buy and sell orders created from the given address") {

  override val parameters: Seq[CmdParameter] = Array(
    CmdParameter("address", AddressPType, "address"),
  )

  override def createCmd(ctx: AppContext): Cmd = {
    val Seq(
      address: Address
    ) = ctx.cmdParameters
    ListMyOrdersCmd(ctx.toolConf, name, address)
  }
}
