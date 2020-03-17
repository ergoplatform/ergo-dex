package org.ergoplatform.dex.commands

import java.io.File

import org.ergoplatform.appkit.JavaHelpers._
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.cli.AppContext
import org.ergoplatform.appkit.commands._
import org.ergoplatform.appkit.config.ErgoToolConfig

/** Shows buy and sell orders created from the address of this wallet
  *
  * Steps:<br/>
  * 1) request storage password from the user<br/>
  * 2) read storage file, unlock using password and get secret<br/>
  * 3) get master public key and compute sender's address<br/>
  * 4) load and show sell and buy orders<br/>
  *
  * @param storageFile storage with secret key of the sender
  * @param storagePass password to access sender secret key in the storage
  */
case class ListMyOrdersCmd(toolConf: ErgoToolConfig,
                           name: String,
                           storageFile: File,
                           storagePass: SecretString) extends Cmd with RunWithErgoClient {

  override def runWithClient(ergoClient: ErgoClient, runCtx: AppContext): Unit = {
    val console = runCtx.console
    ergoClient.execute(ctx => {
      val senderProver = loggedStep("Creating prover", console) {
        BoxOperations.createProver(ctx, storageFile.getPath, storagePass).build()
      }
      val sender = senderProver.getAddress

      val sellerHolderBoxes = loggedStep(s"Loading seller boxes", console) {
        ctx.getUnspentBoxesForErgoTreeTemplate(SellerContract.contractTemplate).convertTo[IndexedSeq[InputBox]]
      }
        .filter { b => SellerContract.sellerPkFromTree(b.getErgoTree).contains(sender.getPublicKey) }

      val buyerHolderBoxes = loggedStep(s"Loading buyer boxes", console) {
        ctx.getUnspentBoxesForErgoTreeTemplate(BuyerContract.contractTemplate).convertTo[IndexedSeq[InputBox]]
      }
        .filter { b => BuyerContract.buyerPkFromTree(b.getErgoTree).contains(sender.getPublicKey) }

      console.println(s"Orders created with key $sender :")

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
  name = "dex:ListMyOrders", cmdParamSyntax = "<storageFile>",
  description = "show buy and sell orders created from the address of this wallet") {

  override val parameters: Seq[CmdParameter] = Array(
    CmdParameter("storageFile", FilePType,
      "storage with secret key of the sender"),
    CmdParameter("storagePass", "Storage password", SecretStringPType,
      "password to access sender secret key in the storage", None,
      Some(PasswordInput), None),
  )

  override def createCmd(ctx: AppContext): Cmd = {
    val Seq(
      storageFile: File,
      pass: SecretString,
    ) = ctx.cmdParameters
    ListMyOrdersCmd(ctx.toolConf, name, storageFile, pass)
  }
}
