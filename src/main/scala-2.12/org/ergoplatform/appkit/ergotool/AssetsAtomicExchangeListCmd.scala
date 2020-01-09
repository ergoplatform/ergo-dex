package org.ergoplatform.appkit.ergotool

import java.io.File
import java.util

import org.ergoplatform.appkit.Parameters.MinFee
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.console.Console
import org.ergoplatform.appkit.impl.{ErgoTreeContract, ScalaBridge}
import sigmastate.SLong
import sigmastate.Values.{ByteArrayConstant, Constant, LongConstant, SigmaPropConstant}
import sigmastate.basics.DLogProtocol.ProveDlog
import sigmastate.eval.CSigmaProp
import sigmastate.verification.contract.AssetsAtomicExchangeCompilation
import special.sigma.SigmaProp
import sigmastate.eval.Extensions._

/** Shows matching buyer and seller contracts for AssetsAtomicExchange
  *
  * Steps:<br/>
  * 1) request storage password from the user<br/>
  * 2) read storage file, unlock using password and get secret<br/>
  * 3) get master public key and compute sender's address<br/>
  * ...
  *
  */
case class AssetsAtomicExchangeListCmd(toolConf: ErgoToolConfig,
                                        name: String) extends Cmd with RunWithErgoClient {

  override def runWithClient(ergoClient: ErgoClient, runCtx: AppContext): Unit = {
    val console = runCtx.console
    ergoClient.execute(ctx => {
      import sigmastate.verified.VerifiedTypeConverters._
      val anyAddress = Address.create("9f4QF8AD1nQ3nJahQVkMj8hFSVVzVom77b52JU7EW71Zexg6N8v")
      val anyAddressPkProp: sigmastate.verified.SigmaProp = CSigmaProp(anyAddress.getPublicKey).asInstanceOf[SigmaProp]
      // TODO: add contractInstance wrappers (use local types - ErgoToken, Address, etc.)
      val sellerContract = AssetsAtomicExchangeCompilation.sellerContractInstance(0,
        0L, anyAddressPkProp)
      val sellerHolderBoxes = loggedStep(s"Loading seller boxes", console) {
        ctx.getUnspentBoxesForErgoTreeTemplate(ErgoTreeTemplate.fromErgoTree(sellerContract.ergoTree))
      }
      val tokenIdBytes = new ErgoToken("21f84cf457802e66fb5930fb5d45fbe955933dc16a72089bf8980797f24e2fa1",
        0L).getId.getBytes.toColl
      val buyerContract = AssetsAtomicExchangeCompilation.buyerContractInstance(0, tokenIdBytes , 0L, anyAddressPkProp)
      val buyerHolderBoxes = loggedStep(s"Loading buyer boxes", console) {
        ctx.getUnspentBoxesForErgoTreeTemplate(ErgoTreeTemplate.fromErgoTree(buyerContract.ergoTree))
      }
      // TODO: extract token id and amount from buyer's contract (use in other cmds)
      // TODO: extract token price from seller's contract (use in other cmds)
      // TODO: find matching contracts
    })
  }
}

object AssetsAtomicExchangeListCmd extends CmdDescriptor(
  name = "AssetAtomicExchangeList", cmdParamSyntax = "",
  description = "show matching token seller's and buyer's contracts") {

  override def parseCmd(ctx: AppContext): Cmd = {
    AssetsAtomicExchangeListCmd(ctx.toolConf, name)
  }
}

