package org.ergoplatform.appkit

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

  val tokenGen: Gen[ErgoToken] = for {
    id <- ergoIdGen
    value <- unsignedLongGen
  } yield new ErgoToken(id, value)

}
