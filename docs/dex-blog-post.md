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
// TODO implement issueToken command and show usage help  
```
$ ergo-tool help issueToken
...
``` 

A token is issued by creating a new box with the `ergAmount` of ERGs and 
(`tokenId`, `tokenAmount`) pair in the `R2` register. Additional registers should also be
specified as required by
[EIP-4](https://github.com/ergoplatform/eips/blob/master/eip-0004.md) standard.
The `issueToken` command uses a wallet storage given by `storageFile` to transfer given `ergAmount` of ERGs 
to the new box with tokens. The new box will belong the same wallet given by storageFile.

// TODO show an example of command usage with command results
```
$ ergo-tool issueToken ...
...
``` 

## Sell Tokens

If we have tokens in a box we can sell them. Well, at least we can create an Ask order
and submit it in the order book. In our DEX implementation we create orders and store
them directly on the Ergo blockchain. The created order in our case is a box (seller box)
protected with the special _seller contract_ holding the necessary amount of ERGs (`ergAmount`). 

```
// Seller Contract
// pkB: SigmaProp - public key of the seller (Bob)
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
// TODO show usage help
```

Here is an example of using `dex:SellOrder` to 
  
## Buy Tokens

You may also want to buy tokens, either because you believe it's value is going to surge
or you need one to participate in a dApp which require having some tokens or what ever
reason you may have. You can 
  
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

