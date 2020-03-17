package org.ergoplatform.appkit

import org.ergoplatform.dex.commands.{BuyerContract, SellerContract}
import org.ergoplatform.{ErgoAddressEncoder, P2PKAddress}
import org.scalacheck.{Arbitrary, Gen}
import sigmastate.basics.DLogProtocol.ProveDlog
import sigmastate.interpreter.CryptoConstants
import sigmastate.interpreter.CryptoConstants.EcPointType

trait ObjectGenerators {

  def ergoIdGen: Gen[ErgoId] = for {
    bytes <- Gen.listOfN(32, Arbitrary.arbByte.arbitrary)
  } yield new ErgoId(bytes.toArray)

  val groupElementGen: Gen[EcPointType] = for {
    _ <- Gen.const(1)
  } yield CryptoConstants.dlogGroup.createRandomElement()
  val proveDlogGen: Gen[ProveDlog] = for {v <- groupElementGen} yield ProveDlog(v)

  def addressGen(networkPrefix: Byte): Gen[Address] = for {
    pd <- proveDlogGen
  } yield new Address(P2PKAddress(pd)(new ErgoAddressEncoder(networkPrefix)))

  val testnetAddressGen: Gen[Address] = addressGen(NetworkType.TESTNET.networkPrefix)

  val unsignedLongGen: Gen[Long] = Gen.chooseNum(0, Long.MaxValue)
  val positiveLongGen: Gen[Long] = Gen.chooseNum(1, Long.MaxValue)

  val validBoxValueGen: Gen[Long] = Gen.chooseNum(Parameters.MinFee, Long.MaxValue)

  val tokenGen: Gen[ErgoToken] = for {
    id <- ergoIdGen
    value <- positiveLongGen
  } yield new ErgoToken(id, value)

  val sellOrderContractGen: Gen[ErgoContract] = for {
    tokenPrice <- Gen.chooseNum(1L, Long.MaxValue)
    sellerAddress <- testnetAddressGen
  } yield SellerContract.contractInstance(tokenPrice, sellerAddress)

  def buyOrderContractGen(token: ErgoToken): Gen[ErgoContract] = for {
    buyerAddress <- testnetAddressGen
  } yield BuyerContract.contractInstance(token, buyerAddress)

  def sellOrderBoxGen(sellerAddress: Address): Gen[InputBox] = for {
    id <- ergoIdGen
    value <- validBoxValueGen
    tokenPrice <- positiveLongGen
    token <- tokenGen
  } yield {
    val ergoTree = SellerContract.contractInstance(tokenPrice, sellerAddress).getErgoTree
    MockInputBox(id, value, ergoTree, Seq(token))
  }

  def buyOrderBoxGen(buyerAddress: Address): Gen[InputBox] = for {
    id <- ergoIdGen
    value <- validBoxValueGen
    token <- tokenGen
  } yield MockInputBox(id, value, BuyerContract.contractInstance(token, buyerAddress).getErgoTree)
}
