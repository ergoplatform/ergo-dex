package org.ergoplatform.appkit.ergotool.dex

import java.io.File
import java.util.Optional

import org.ergoplatform.appkit.Parameters.MinFee
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.ergotool.{AppContext, Cmd, CmdDescriptor, RunWithErgoClient}
import org.ergoplatform.appkit.impl.{ErgoTreeContract, ScalaBridge}
import sigmastate.{SLong, Values}
import sigmastate.Values.{ErgoTree, SigmaPropConstant}
import sigmastate.basics.DLogProtocol.{ProveDlog, ProveDlogProp}
import sigmastate.eval.WrapperOf
import sigmastate.verification.contract.AssetsAtomicExchangeCompilation

/** Creates and sends a new transaction with seller's order for AssetsAtomicExchange
  *
  * Steps:<br/>
  * 1) request storage password from the user<br/>
  * 2) read storage file, unlock using password and get secret<br/>
  * 3) get master public key and compute sender's address<br/>
  * 4) load available tokens belonging to the seller's address<br/>
  * 5) select sender's coins to cover the transaction fee, and computes the amount of change<br/>
  * 6) create an instance of the seller's order passing deadline, token and seller's address<br/>
  * 7) create an output box protected with the instance of seller's order from the previous step<br/>
  * 8) create and sign (using secret key) the transaction<br/>
  * 9) if no `--dry-run` option is specified, send the transaction to the network<br/>
  *    otherwise skip sending<br/>
  * 10) serialize transaction to Json and print to the console<br/>
  *
  * @param storageFile storage with secret key of the sender
  * @param storagePass password to access sender secret key in the storage
  * @param seller address of the seller
  * @param deadline height of the blockchain after which the seller can withdraw tokens from this order
  * @param tokenPrice Ergs amount for seller to receive for tokens
  * @param token token id and amount
  * @param dexFee Ergs amount claimable(box.value) in this order (DEX fee)
  */
case class CreateSellOrderCmd(toolConf: ErgoToolConfig,
                              name: String,
                              storageFile: File,
                              storagePass: SecretString,
                              seller: Address,
                              deadline: Int,
                              tokenPrice: Long,
                              token: ErgoToken,
                              dexFee: Long) extends Cmd with RunWithErgoClient {

  override def runWithClient(ergoClient: ErgoClient, runCtx: AppContext): Unit = {
    val console = runCtx.console
    ergoClient.execute(ctx => {
      val sellerContract = SellerContract.contractInstance(deadline, tokenPrice, seller)
      val senderProver = loggedStep("Creating prover", console) {
        BoxOperations.createProver(ctx, storageFile.getPath, storagePass)
      }
      val sender = senderProver.getAddress
      val unspent = loggedStep(s"Loading unspent boxes from at address $sender", console) {
        ctx.getUnspentBoxesFor(sender)
      }
      val boxesToSpend = BoxOperations.selectTop(unspent, MinFee + dexFee, Optional.of(token))
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
  name = "dex:SellOrder", cmdParamSyntax = "<wallet file> <sellerAddr> <deadline> <ergPrice> <tokenId> <tokenAmount> <dexFee>",
  description = "put a token seller order with given <tokenId> and <tokenAmount> for sale at given <ergPrice> price with <dexFee> as a reward for anyone who matches this order with buyer, until given <deadline> with <sellerAddr> to be used for withdrawal(after the deadline) \n " +
    "with the given <wallet file> to sign transaction (requests storage password)") {

  override def parseCmd(ctx: AppContext): Cmd = {
    val args = ctx.cmdArgs
    val storageFile = new File(if (args.length > 1) args(1) else error("Wallet storage file path is not specified"))
    if (!storageFile.exists()) error(s"Specified wallet file is not found: $storageFile")
    val seller = Address.create(if (args.length > 2) args(2) else error("seller address is not specified"))
    val deadline = if (args.length > 3) args(3).toInt else error("deadline is not specified")
    val ergAmount = if (args.length > 4) args(4).toLong else error("ergPrice is not specified")
    val tokenId = if(args.length > 5) args(5) else error("tokenId is not specified")
    val tokenAmount = if(args.length > 6) args(6).toLong else error("tokenAmount is not specified")
    val token = new ErgoToken(tokenId, tokenAmount)
    val dexFee = if(args.length > 7) args(7).toLong else error("dexFee is not specified")
    val pass = ctx.console.readPassword("Storage password>")
    CreateSellOrderCmd(ctx.toolConf, name, storageFile, pass, seller,
      deadline, ergAmount, token, dexFee)
  }
}

object SellerContract {

  def contractInstance(deadline: Int, tokenPrice: Long, sellerPk: Address): ErgoContract = {
    import sigmastate.verified.VerifiedTypeConverters._
    val sellerPkProp = sigmastate.eval.SigmaDsl.SigmaProp(sellerPk.getPublicKey)
    val verifiedContract = AssetsAtomicExchangeCompilation.sellerContractInstance(deadline,
      tokenPrice, sellerPkProp)
    new ErgoTreeContract(verifiedContract.ergoTree)
  }

  def tokenPriceFromTree(tree: ErgoTree): Option[Long] =
    // TODO get rid on magic constant (consider refactoring using ErgoContract.getConstantByName())
    tree.constants.lift(6).collect {
      case Values.ConstantNode(value, SLong) => value.asInstanceOf[Long]
    }

  def sellerPkFromTree(tree: ErgoTree): Option[ProveDlog] =
    tree.constants.lift(1).collect { case SigmaPropConstant(ProveDlogProp(v)) => v }

}
