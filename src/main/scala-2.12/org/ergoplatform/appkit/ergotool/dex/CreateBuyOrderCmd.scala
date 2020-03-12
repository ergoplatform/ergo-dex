package org.ergoplatform.appkit.ergotool.dex

import java.io.File

import org.ergoplatform.appkit.Parameters.MinFee
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.ergotool.{AddressPType, AppContext, Cmd, CmdDescriptor, CmdParameter, ErgoIdPType, FilePType, LongPType, RunWithErgoClient, SecretStringPType, StringPType}
import org.ergoplatform.appkit.impl.{ErgoTreeContract, ScalaBridge}
import org.ergoplatform.contracts.AssetsAtomicExchangeCompilation
import sigmastate.Values.{ByteArrayConstant, CollectionConstant, ErgoTree, SigmaPropConstant}
import sigmastate.basics.DLogProtocol.{ProveDlog, ProveDlogProp}
import sigmastate.eval.WrapperOf
import sigmastate.eval.Extensions._
import sigmastate.{SByte, SLong, Values}

/** Creates and sends a new transaction with buyer's order for AssetsAtomicExchange
  *
  * Steps:<br/>
  * 1) request storage password from the user<br/>
  * 2) read storage file, unlock using password and get secret<br/>
  * 3) get master public key and compute sender's address<br/>
  * 4) load available coins belonging to the sender's address<br/>
  * 5) select coins to cover ergAmount, compute transaction fee and amount of change<br/>
  * 6) create an instance of the buyer's order passing token and wallet address<br/>
  * 7) create an output box protected with the instance of buyer's order from the previous step<br/>
  * 8) create and sign (using secret key) the transaction<br/>
  * 9) if no `--dry-run` option is specified, send the transaction to the network<br/>
  *    otherwise skip sending<br/>
  * 10) serialize transaction to Json and print to the console<br/>
  *
  * @param storageFile storage with secret key of the sender
  * @param storagePass password to access sender secret key in the storage
  * @param ergAmount   NanoERG amount for buyer to pay for tokens
  * @param token       token id and amount
  * @param dexFee      an amount of NanoERGs to put in addition to ergAmount into the new box protected
  *                    by the buyer order. When the buyer setup up a bid price he/she also decide on
  *                    the DEX fee amount to pay. Reward for anyone who matches this order with seller's order
  */
case class CreateBuyOrderCmd(toolConf: ErgoToolConfig,
                             name: String,
                             storageFile: File,
                             storagePass: SecretString,
                             ergAmount: Long,
                             token: ErgoToken,
                             dexFee: Long) extends Cmd with RunWithErgoClient {

  override def runWithClient(ergoClient: ErgoClient, runCtx: AppContext): Unit = {
    require(ergAmount > 0, s"invalid ergAmount: $ergAmount")
    require(token.getValue > 0, s"invalid token amount: ${token.getValue}")
    require(dexFee > 0, s"invalid DEX fee: $dexFee")
    val console = runCtx.console
    ergoClient.execute(ctx => {
      val senderProver = loggedStep("Creating prover", console) {
        BoxOperations.createProver(ctx, storageFile.getPath, storagePass).build()
      }
      val sender = senderProver.getAddress
      val buyerContract = BuyerContract.contractInstance(token, sender)
//      println(s"contract ergo tree: ${ScalaBridge.isoStringToErgoTree.from( buyerContract.getErgoTree)}")
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


object CreateBuyOrderCmd extends CmdDescriptor(
  name = "dex:BuyOrder", cmdParamSyntax = "<wallet file> <ergAmount> <tokenId> <tokenAmount>, <dexFee>",
  description = "put a token buyer order with given <tokenId> and <tokenAmount> to buy at given <ergPrice> price with <dexFee> as a reward for anyone who matches this order with a seller, with wallet's address to be used for withdrawal \n " +
    "with the given <wallet file> to sign transaction (requests storage password)") {

  override val parameters: Seq[CmdParameter] = Array(
    CmdParameter("storageFile", FilePType,
      "storage with secret key of the sender"),
    CmdParameter("storagePass", SecretStringPType,
      "password to access sender secret key in the storage", None,
      Some(ctx => ctx.console.readPassword("Storage password>"))),
    CmdParameter("ergAmount", LongPType,
      "amount of NanoERG to pay for tokens"),
    CmdParameter("tokenId", ErgoIdPType,
      "token id to buy"),
    CmdParameter("tokenAmount", LongPType,
      "token amount to buy"),
    CmdParameter("dexFee", LongPType,
      "an amount of NanoERGs to put in addition to ergAmount into the new box protected by the buyer order. When the buyer setup up a bid price he/she also decide on the DEX fee amount to pay. Reward for anyone who matches this order with seller's order")
  )

  override def createCmd(ctx: AppContext): Cmd = {
    val Seq(
      storageFile: File,
      pass: SecretString,
      ergAmount: Long,
      tokenId: ErgoId,
      tokenAmount: Long,
      dexFee: Long
    ) = ctx.cmdParameters

    val token = new ErgoToken(tokenId, tokenAmount)
    CreateBuyOrderCmd(ctx.toolConf, name, storageFile, pass, ergAmount, token, dexFee)
  }
}

object BuyerContract {

  lazy val contractTemplate: ErgoTreeTemplate = {
    val anyAddress = Address.create("9f4QF8AD1nQ3nJahQVkMj8hFSVVzVom77b52JU7EW71Zexg6N8v")
    val token = new ErgoToken("21f84cf457802e66fb5930fb5d45fbe955933dc16a72089bf8980797f24e2fa1",
      0L)
    val buyerContract = BuyerContract.contractInstance(token, anyAddress)
    ErgoTreeTemplate.fromErgoTree(buyerContract.getErgoTree)
  }

  def contractInstance(token: ErgoToken, buyerPk: Address): ErgoContract = {
    import org.ergoplatform.sigma.verified.VerifiedTypeConverters._
    val buyerPkProp = sigmastate.eval.SigmaDsl.SigmaProp(buyerPk.getPublicKey)
    val tokenId = token.getId.getBytes.toColl
    val verifiedContract = AssetsAtomicExchangeCompilation.buyerContractInstance(tokenId,
      token.getValue, buyerPkProp)
    new ErgoTreeContract(verifiedContract.ergoTree)
  }

  def tokenFromContractTree(tree: ErgoTree): Option[ErgoToken] = for {
    tokenId <- tree.constants.lift(6).collect {
      case ByteArrayConstant(coll) => coll.toArray
    }
    tokenAmount <- tree.constants.lift(8).collect {
      case Values.ConstantNode(value, SLong) => value.asInstanceOf[Long]
    }
  } yield new ErgoToken(tokenId, tokenAmount)

  def buyerPkFromTree(tree: ErgoTree): Option[ProveDlog] =
    tree.constants.headOption.collect { case SigmaPropConstant(ProveDlogProp(v)) => v }
}
