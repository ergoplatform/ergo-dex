# Trustless Decentralized Exchange on Ergo

## Introduction
                                                 
Centralized exchanges are popular, have high assets liquidity and are easy to use, 
but unfortunately they may be [hacked](https://coinsutra.com/biggest-bitcoin-hacks/).

[Decentralized Exchanges](https://en.wikipedia.org/wiki/Decentralized_exchange) (aka
DEXes) are gaining popularity and promising better security, but they are harder to use,
have lower liquidity and come with their own drawbacks.

Programming model of Ergo smart contracts is quite powerful which is demonstrated by [many
examples](https://ergoplatform.org/docs/ErgoScript.pdf) including more [advanced potential
applications](https://ergoplatform.org/docs/AdvancedErgoScriptTutorial.pdf), those are
published around a time the network was launched. 
What was missing is the concrete recipe, a step-by-step guidance and tools to put conceptual 
design of smart contracts into working application running on Ergo blockchain. 

In this [Appkit](https://ergoplatform.org/en/blog/2019_12_03_top5/) and
[ErgoTool](https://ergoplatform.org/en/blog/2019_12_31_ergo_tool/) series of posts we aim
to fill this gap and give updates on both new network and tooling development.

Ergo have expressive smart contracts and transaction model which allows an
implementation of fully trustless DEX protocol, in which signed buy and sell orders can be
put into blockchain independently by buyers and sellers. The off-chain matching
service can observe Ergo blockchain, find matching orders and submit the swap
transaction without knowing any secrets. The matching can be incentivized by _DEX reward_
payed as part of a _swap transaction_. Anyone who first discover the match of the two
orders can create the swap transaction and get a reward in ERGs.

In this post we describe a simple yet functional implementation of DEX protocol
within ErgoTool - a command line interface (CLI) utility for Ergo.  

## Issue A New Token

The first operation in the lifecycle of a new token is its issue.
Ergo natively support issue, storage and transfer of tokens. New tokens can be issued
according to the [Assets
Standard](https://github.com/ergoplatform/eips/blob/master/eip-0004.md). 

The following ErgoTool command allows to issue a new token on the Ergo blockchain.
```
$ ergo-tool help dex:IssueToken
Command Name:	dex:IssueToken
Usage Syntax:	ergo-tool dex:IssueToken <wallet file> <ergAmount> <tokenAmount> <tokenName> <tokenDesc> <tokenNumberOfDecimals
Description:	issue a token with given <tokenName>, <tokenAmount>, <tokenDesc>, <tokenNumberOfDecimals> and <ergAmount> with the given <wallet file> to sign transaction (requests storage password)
Doc page:	https://aslesarenko.github.io/ergo-tool/api/org/ergoplatform/appkit/ergotool/dex/IssueTokenCmd.html
``` 

A token is issued by creating a new box with the `ergAmount` of ERGs and 
(`tokenId`, `tokenAmount`) pair in the `R2` register. Additional registers should also be
specified as required by
[EIP-4](https://github.com/ergoplatform/eips/blob/master/eip-0004.md) standard.
The `dex:IssueToken` command uses a wallet storage given by `storageFile` to transfer given `ergAmount` of ERGs 
to the new box with tokens. The new box will belong the same wallet given by storageFile.

```
$ ergo-tool dex:IssueToken "storage/E2.json" 50000000 1000000 "TKN" "Generic token" 2
Creating prover... Ok
Loading unspent boxes from at address 9hHDQb26AjnJUXxcqriqY1mnhpLuUeC81C4pggtK7tupr92Ea1K... Ok
Signing the transaction... Ok
Tx: {
  "id": "5d899e1551b324012c6c17636422fceb8077c444585de06f444fe680f9badd92",
  "inputs": [
    {
      "boxId": "d47f958b201dc7162f641f7eb055e9fa7a9cb65cc24d4447a10f86675fc58328",
      "spendingProof": {
        "proofBytes": "a8e796930fb11cb122bd8a506bd7b7dad38bbe287122580669aa28c36a08f7b5be95bd084878be05b695bfb1753417f45efe1e7647691304",
        "extension": {}
      }
    },
    {
      "boxId": "e050a3af38241ce444c34eb25c0ab880674fc23a0e63632633ae14f547141c37",
      "spendingProof": {
        "proofBytes": "dc22066266ec65058457b87f36e4ad825df644c97c7d36abefa6e396ff3ceee21f0599861b46725839b575bcdd5f0292344502aead9bde6e",
        "extension": {}
      }
    },
    {
      "boxId": "26d6e08027e005270b38e5c5f4a73ffdb6d65a3289efb51ac37f98ad395d887c",
      "spendingProof": {
        "proofBytes": "d09fd17f6f1a11f903b435538fd8badb9185160ce8e5e8c9ec6d09041b5dc7c8b587d304696460e11abf77d348ca153ad29e8b9c6fdd91f4",
        "extension": {}
      }
    }
  ],
  "dataInputs": [],
  "outputs": [
    {
      "boxId": "7e704ede7958b647bd7f008a80b01972f7251d0d717eaeda7ef2caf50ade3e1a",
      "value": 50000000,
      "ergoTree": "ErgoTree(0,WrappedArray(),Right(ConstantNode(SigmaProp(ProveDlog(ECPoint(6ba5cf,8ae5ac,...))),SSigmaProp)),80,[B@1a07bf6)",
      "creationHeight": 123414,
      "assets": [
        {
          "tokenId": "d47f958b201dc7162f641f7eb055e9fa7a9cb65cc24d4447a10f86675fc58328",
          "amount": 1000000
        }
      ],
      "additionalRegisters": {
        "R4": "0e03544b4e",
        "R5": "0e0d47656e6572696320746f6b656e",
        "R6": "0e0132"
      },
      "transactionId": "5d899e1551b324012c6c17636422fceb8077c444585de06f444fe680f9badd92",
      "index": 0
    },
    {
      "boxId": "d35162679ff1479df4851020278ad83e6fdc78acfde1e66fe1efffc9869ab41a",
      "value": 1000000,
      "ergoTree": "ErgoTree(16,WrappedArray(IntConstant(0), IntConstant(0), ConstantNode(Coll(16,2,4,-96,11,8,-51,2,121,-66,102,126,-7,-36,-69,-84,85,-96,98,-107,-50,-121,11,7,2,-101,-4,-37,45,-50,40,-39,89,-14,-127,91,22,-8,23,-104,-22,2,-47,-110,-93,-102,-116,-57,-89,1,115,0,115,1),Coll[SByte$]), ConstantNode(Coll(1),Coll[SInt$]), IntConstant(1)),Right(BoolToSigmaProp(AND(ConcreteCollection(WrappedArray(EQ(Height$(163),SelectField(ExtractCreationInfo(ByIndex(Outputs$(165),ConstantPlaceholder(0,SInt$),None)),1)), EQ(ExtractScriptBytes(ByIndex(Outputs$(165),ConstantPlaceholder(1,SInt$),None)),SubstConstants(ConstantPlaceholder(2,Coll[SByte$]),ConstantPlaceholder(3,Coll[SInt$]),ConcreteCollection(WrappedArray(CreateProveDlog(DecodePoint(MinerPubkey$(172)))),SSigmaProp))), EQ(SizeOf(Outputs$(165)),ConstantPlaceholder(4,SInt$))),SBoolean)))),4836,[B@713ec32d)",
      "creationHeight": 123414,
      "assets": [],
      "additionalRegisters": {},
      "transactionId": "5d899e1551b324012c6c17636422fceb8077c444585de06f444fe680f9badd92",
      "index": 1
    },
    {
      "boxId": "57dc06e5a197e15d9a73804735231d6c240ca6bdd45753073883c79cf726ea96",
      "value": 9951000000,
      "ergoTree": "ErgoTree(0,WrappedArray(),Right(ConstantNode(SigmaProp(ProveDlog(ECPoint(6ba5cf,8ae5ac,...))),SSigmaProp)),80,[B@34d713a2)",
      "creationHeight": 123414,
      "assets": [],
      "additionalRegisters": {},
      "transactionId": "5d899e1551b324012c6c17636422fceb8077c444585de06f444fe680f9badd92",
      "index": 2
    }
  ]
}
Sending the transaction... Ok
Server returned tx id: 5d899e1551b324012c6c17636422fceb8077c444585de06f444fe680f9badd92
``` 

## Sell Tokens

If the have tokens in a box we can sell them. Well, at least we can create an Ask order
and submit it in the order book. In out DEX implementation we create orders and store
them directly on the Ergo blockchain. The created order in our case is a box (seller box)
protected with the special _seller contract_ holding the necessary amount of ERGs (`ergAmount`). 

```
// Seller Contract
// pkB: SigmaProp - public key of the seller
{
  pkB || {
    val knownBoxId = OUTPUTS(1).R4[Coll[Byte]].get == SELF.id
    OUTPUTS(1).value >= ergAmount &&
      knownBoxId &&
      OUTPUTS(1).propositionBytes == pkB.propBytes
  }
}
```

The seller contract guarantees that the seller box can be spent: 
1) by seller itself, which is the way for a seller to [cancel the order](#canceling-the-orders)
2) by a _swap transaction_ created by Matcher in which _seller box_ is spent together (i.e.
atomically) with the matched _buyer box_ (see [buy tokens](#buy-tokens)).

The following command can be used to create new _ask order_ to sell tokens.
```
$ ergo-tool help dex:SellOrder
Command Name:	dex:SellOrder
Usage Syntax:	ergo-tool dex:SellOrder <storageFile> <sellerAddr> <tokenPrice> <tokenId> <tokenAmount> <dexFee>
Description:	put a token seller order with given <tokenId> and <tokenAmount> for sale at given <tokenPrice> price with <dexFee> as a reward for anyone who matches this order with buyer, with <sellerAddr> to be used for withdrawal 
 with the given <storageFile> to sign transaction (requests storage password)
Doc page:	https://aslesarenko.github.io/ergo-tool/api/org/ergoplatform/appkit/ergotool/dex/CreateSellOrderCmd.html
```

  
## Buy Tokens
  
## Cancel Order

## To recap

ErgoToolDEX is simple implementation of trustless decentralized exchange of crypto assets
directly on Ergo blockchain, it mostly motivated by three goals we keep in mind: 
1) anyone (with CLI skills) should be able to issue and trade tokens on Ergo (at
least using CLI, in the absence of better UI)
2) our implementation should be simple and easy to use as an example of application
development on top of Ergo and as an inspiration for other useful dApps.
3) the commands should be available as the library of reusable components which can be
used by developers to design and implement a much better UI for Ergo DEX.

In the next posts we are going look under the hood and see how to implement new commands of
ErgoTool, stay tuned!

## References

- [Ergo Sources](https://github.com/ergoplatform/ergo)
- [Ergo Appkit](https://github.com/aslesarenko/ergo-appkit)
- [Ergo Tool](https://github.com/aslesarenko/ergo-tool)

