# Trustless Decentralized Exchange on Ergo

## Introduction
                                                 
Centralized exchanges are popular, have high assets liquidity and are easy to use, 
but unfortunately they may be [hacked](https://coinsutra.com/biggest-bitcoin-hacks/).

[Decentralized Exchanges](https://en.wikipedia.org/wiki/Decentralized_exchange) (aka
DEXes) are gaining popularity and promising better security, but they are harder to use,
have lower liquidity and come with their own drawbacks.

Ergo have expressive smart contracts and transaction model which allows an
implementation of fully trustless DEX protocol. The signed buy and sell orders can be
put into blockchain independently by the buyers and sellers. The off-chain matching
service can observe Ergo blockchain, find matching orders and submit the atomic swap
transaction without knowing any secrets. The matching can be incentivized by DEX reward
payed as part of the swap transaction.

In this post we describe a simple yet fully functional implementation of DEX protocol
withing ErgoTool - a command line interface (CLI) utility for Ergo. 
    
## Issue A New Token

The first operation in the lifecycle of a new token is its issue.
Ergo natively support issue, storage and transfer of tokens. New tokens can be issued
according to the [Assets
Standard](https://github.com/ergoplatform/eips/blob/master/eip-0004.md). 

The following ErgoTool command allows to issue a new assert on the Ergo blockchain.
// TODO implement command and write example
```
$ ergo-tool help 
``` 
// TODO show an example of command usage with command results

## Sell Tokens
 
  
## References

- [Top 6 Biggest Bitcoin Hacks Ever](https://coinsutra.com/biggest-bitcoin-hacks/)
- [Decentralized exchange](https://en.wikipedia.org/wiki/Decentralized_exchange)
- [0x Protocol](https://0x.org/)
