package org.ergoplatform.dex.commands

import java.io.File
import java.util.Optional

import org.ergoplatform.appkit.Parameters.MinFee
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.cli.AppContext
import org.ergoplatform.appkit.commands._
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.contracts.AssetsAtomicExchangeCompilation
import sigmastate.{SLong, Values}
import sigmastate.Values.{SigmaPropConstant, ErgoTree}
import sigmastate.basics.DLogProtocol.{ProveDlogProp, ProveDlog}
import scala.collection.JavaConverters._

/** Creates and sends a new transaction with seller's order for AssetsAtomicExchange
  *
  * Steps:<br/>
  * 1) request storage password from the user<br/>
  * 2) read storage file, unlock using password and get secret<br/>
  * 3) get master public key and compute sender's address<br/>
  * 4) load available tokens belonging to the seller's address<br/>
  * 5) select sender's coins to cover the transaction fee, and computes the amount of change<br/>
  * 6) create an instance of the seller's order passing token and wallet address<br/>
  * 7) create an output box protected with the instance of seller's order from the previous step<br/>
  * 8) create and sign (using secret key) the transaction<br/>
  * 9) if no `--dry-run` option is specified, send the transaction to the network<br/>
  *    otherwise skip sending<br/>
  * 10) serialize transaction to Json and print to the console<br/>
  *
  * @param storageFile storage with secret key of the sender
  * @param storagePass password to access sender secret key in the storage
  * @param tokenPrice Ergs amount for seller to receive for tokens
  * @param token token id and amount
  * @param dexFee Ergs amount claimable(box.value) in this order (DEX fee)
  */
case class CreateSellOrderCmd(toolConf: ErgoToolConfig,
                              name: String,
                              storageFile: File,
                              storagePass: SecretString,
                              tokenPrice: Long,
                              token: ErgoToken,
                              dexFee: Long) extends Cmd with RunWithErgoClient {

  override def runWithClient(ergoClient: ErgoClient, runCtx: AppContext): Unit = {
    require(tokenPrice > 0, s"invalid tokenPrice: $tokenPrice")
    require(token.getValue > 0, s"invalid token amount: ${token.getValue}")
    require(dexFee > 0, s"invalid DEX fee: $dexFee")
    val console = runCtx.console
    ergoClient.execute(ctx => {
      val senderProver = loggedStep("Creating prover", console) {
        BoxOperations.createProver(ctx, storageFile.getPath, storagePass).build()
      }
      val sender = senderProver.getAddress
      val sellerContract = SellerContract.contractInstance(tokenPrice, sender)
      val unspent = loggedStep(s"Loading unspent boxes from at address $sender", console) {
        ctx.getUnspentBoxesFor(sender)
      }
      val boxesToSpend = BoxOperations.selectTop(unspent, MinFee + dexFee, List(token).asJava)
//      println(s"contract ergo tree: ${ScalaBridge.isoStringToErgoTree.from(sellerContract.getErgoTree)}")
      val txB = ctx.newTxBuilder
      val newBox = txB.outBoxBuilder
        .value(dexFee)
        .contract(sellerContract)
        .tokens(token)
        .build()
      val tx = txB
        .boxesToSpend(boxesToSpend).outputs(newBox)
        .fee(MinFee)
        .sendChangeTo(senderProver.getP2PKAddress)
        .build()
      val signed = loggedStep(s"Signing the transaction", console) {
        senderProver.sign(tx)
      }
      val txJson = signed.toJson(true)
      console.println(s"Tx: $txJson")

      if (!runCtx.isDryRun) {
        val txId = loggedStep(s"Sending the transaction", console) {
          ctx.sendTransaction(signed)
        }
        console.println(s"Server returned tx id: $txId")
      }
    })
  }
}

object CreateSellOrderCmd extends CmdDescriptor(
  name = "dex:SellOrder", cmdParamSyntax = "<wallet file> <ergPrice> <tokenId> <tokenAmount> <dexFee>",
  description = "put a token seller order with given <tokenId> and <tokenAmount> for sale at given <ergPrice> price with <dexFee> as a reward for anyone who matches this order with buyer, with wallet's address to be used for withdrawal \n " +
    "with the given <wallet file> to sign transaction (requests storage password)") {

  override val parameters: Seq[CmdParameter] = Array(
    CmdParameter("storageFile", FilePType,
      "storage with secret key of the sender"),
    CmdParameter("storagePass", "Storage password", SecretStringPType,
      "password to access sender secret key in the storage", None,
      Some(PasswordInput), None),
    CmdParameter("tokenPrice", LongPType,
      "amount of NanoERG asked for tokens"),
    CmdParameter("tokenId", ErgoIdPType,
      "token id offered for sale"),
    CmdParameter("tokenAmount", LongPType,
      "token amount offered for sale"),
    CmdParameter("dexFee", LongPType,
      "reward for anyone who matches this order with buyer's order")
  )

  override def createCmd(ctx: AppContext): Cmd = {
    val Seq(
      storageFile: File,
      pass: SecretString,
      tokenPrice: Long,
      tokenId: ErgoId,
      tokenAmount: Long,
      dexFee: Long
    ) = ctx.cmdParameters

    val token = new ErgoToken(tokenId, tokenAmount)
    CreateSellOrderCmd(ctx.toolConf, name, storageFile, pass, tokenPrice, token, dexFee)
  }
}

object SellerContract {

  lazy val contractTemplate: ErgoTreeTemplate = {
    val anyAddress = Address.create("9f4QF8AD1nQ3nJahQVkMj8hFSVVzVom77b52JU7EW71Zexg6N8v")
    val sellerContract = SellerContract.contractInstance(0L, anyAddress)
    ErgoTreeTemplate.fromErgoTree(sellerContract.getErgoTree)
  }

  def contractInstance(tokenPrice: Long, sellerPk: Address): ErgoContract = {
    import org.ergoplatform.sigma.verified.VerifiedTypeConverters._
    val sellerPkProp = sigmastate.eval.SigmaDsl.SigmaProp(sellerPk.getPublicKey)
    val verifiedContract = AssetsAtomicExchangeCompilation.sellerContractInstance(tokenPrice, sellerPkProp)
    new ErgoTreeContract(verifiedContract.ergoTree)
  }

  def tokenPriceFromTree(tree: ErgoTree): Option[Long] =
    // TODO get rid on magic constant (consider refactoring using ErgoContract.getConstantByName())
    tree.constants.lift(5).collect {
      case Values.ConstantNode(value, SLong) => value.asInstanceOf[Long]
    }

  def sellerPkFromTree(tree: ErgoTree): Option[ProveDlog] =
    tree.constants.headOption.collect { case SigmaPropConstant(ProveDlogProp(v)) => v }

}
