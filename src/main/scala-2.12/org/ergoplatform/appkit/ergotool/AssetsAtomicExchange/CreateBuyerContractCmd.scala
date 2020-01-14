package org.ergoplatform.appkit.ergotool.AssetsAtomicExchange

import java.io.File

import org.ergoplatform.appkit.Parameters.MinFee
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.ergotool.{AppContext, Cmd, CmdDescriptor, RunWithErgoClient}
import org.ergoplatform.appkit.impl.{ErgoTreeContract, ScalaBridge}
import sigmastate.Values.{CollectionConstant, Constant, ErgoTree}
import sigmastate.basics.DLogProtocol.ProveDlog
import sigmastate.eval.CSigmaProp
import sigmastate.eval.Extensions._
import sigmastate.verification.contract.AssetsAtomicExchangeCompilation
import sigmastate.{SByte, SLong}
import special.sigma.SigmaProp

/** Creates and sends a new transaction with buyer's contract for AssetsAtomicExchange
  *
  * Steps:<br/>
  * 1) request storage password from the user<br/>
  * 2) read storage file, unlock using password and get secret<br/>
  * 3) get master public key and compute sender's address<br/>
  * 4) load available coins belonging to the sender's address<br/>
  * 5) select coins to cover ergAmount, compute transaction fee and amount of change<br/>
  * 6) create an instance of the buyer's contract passing deadline, token and buyer address<br/>
  * 7) create an output box protected with the instance of buyer's contract from the previous step<br/>
  * 8) create and sign (using secret key) the transaction<br/>
  * 9) if no `--dry-run` option is specified, send the transaction to the network<br/>
  *    otherwise skip sending<br/>
  * 10) serialize transaction to Json and print to the console<br/>
  *
  * @param storageFile storage with secret key of the sender
  * @param storagePass password to access sender secret key in the storage
  * @param buyer address of the buyer
  * @param deadline height of the blockchain after which the buyer can withdraw Ergs from this contract
  * @param ergAmount Erg amount for buyer to pay for tokens
  * @param token token id and amount
  * @param dexFee Ergs amount put in addition to ergAmount into box.value in this contract (DEX fee)
  */
case class CreateBuyerContractCmd(toolConf: ErgoToolConfig,
                                  name: String,
                                  storageFile: File,
                                  storagePass: Array[Char],
                                  buyer: Address,
                                  deadline: Int,
                                  ergAmount: Long,
                                  token: ErgoToken,
                                  dexFee: Long) extends Cmd with RunWithErgoClient {

  override def runWithClient(ergoClient: ErgoClient, runCtx: AppContext): Unit = {
    val console = runCtx.console
    ergoClient.execute(ctx => {
      val buyerContract = BuyerContract.contractInstance(deadline, token, buyer.getPublicKey)
      println(s"contract ergo tree: ${ScalaBridge.isoStringToErgoTree.from( buyerContract.getErgoTree)}")
      val senderProver = loggedStep("Creating prover", console) {
        BoxOperations.createProver(ctx, storageFile.getPath, String.valueOf(storagePass))
      }
      val sender = senderProver.getAddress
      val unspent = loggedStep(s"Loading unspent boxes from at address $sender", console) {
        ctx.getUnspentBoxesFor(sender)
      }
      val outboxValue = ergAmount + dexFee
      val boxesToSpend = BoxOperations.selectTop(unspent, MinFee + outboxValue)
      val txB = ctx.newTxBuilder
      val newBox = txB.outBoxBuilder
        .value(outboxValue)
        .contract(buyerContract)
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


object CreateBuyerContractCmd extends CmdDescriptor(
  name = "AssetAtomicExchangeBuyer", cmdParamSyntax = "<wallet file> <buyerAddr> <deadline> <ergAmount> <tokenId> <tokenAmount>, <dexFee>",
  description = "put a token buyer contract with given <tokenId> and <tokenAmount> to buy at given <ergPrice> price with <dexFee> as a reward for anyone who matches this contract with a seller, until given <deadline> with <buyerAddr> to be used for withdrawal(after the deadline) \n " +
    "with the given <wallet file> to sign transaction (requests storage password)") {

  override def parseCmd(ctx: AppContext): Cmd = {
    val args = ctx.cmdArgs
    val storageFile = new File(if (args.length > 1) args(1) else error("Wallet storage file path is not specified"))
    if (!storageFile.exists()) error(s"Specified wallet file is not found: $storageFile")
    val buyer = Address.create(if (args.length > 2) args(2) else error("buyer address is not specified"))
    val deadline = if (args.length > 3) args(3).toInt else error("deadline is not specified")
    val ergAmount = if (args.length > 4) args(4).toLong else error("ergAmount is not specified")
    val tokenId = if(args.length > 5) args(5) else error("tokenId is not specified")
    val tokenAmount = if(args.length > 6) args(6).toLong else error("tokenAmount is not specified")
    val token = new ErgoToken(tokenId, tokenAmount)
    val dexFee = if(args.length > 7) args(7).toLong else error("dexFee is not specified")
    val pass = ctx.console.readPassword("Storage password>")
    CreateBuyerContractCmd(ctx.toolConf, name, storageFile, pass, buyer,
      deadline, ergAmount, token, dexFee)
  }
}

object BuyerContract {

  def contractInstance(deadline: Int, token: ErgoToken, buyerPk: ProveDlog): ErgoContract = {
    import sigmastate.verified.VerifiedTypeConverters._
    val buyerPkProp: sigmastate.verified.SigmaProp = CSigmaProp(buyerPk).asInstanceOf[SigmaProp]
    val verifiedContract = AssetsAtomicExchangeCompilation.buyerContractInstance(deadline,
      token.getId.getBytes.toColl, token.getValue, buyerPkProp)
    new ErgoTreeContract(verifiedContract.ergoTree)
  }

  def tokenFromContractTree(tree: ErgoTree): ErgoToken = {
    val tokenId = tree.constants(7).asInstanceOf[CollectionConstant[SByte.type]].value.toArray
    val tokenAmount = tree.constants(9).asInstanceOf[Constant[SLong.type]].value
    new ErgoToken(tokenId, tokenAmount)
  }

  def buyerPkFromTree(tree: ErgoTree): ProveDlog =
    tree.constants(1).value.asInstanceOf[CSigmaProp].sigmaTree.asInstanceOf[ProveDlog]
}
