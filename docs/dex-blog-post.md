# Trustless Decentralized Exchange on Ergo

## Introduction

Centralized exchanges are popular, have high assets liquidity and are easy to use,
but unfortunately they may be [hacked](https://coingape.com/top-cryptocurrency-exchange-hacks-in-2019/).

[Decentralized Exchanges](https://en.wikipedia.org/wiki/Decentralized_exchange) (aka
DEXes) are gaining popularity and promising better security, but they are harder to use,
have lower liquidity and come with their own drawbacks.

Programming model of Ergo smart contracts is quite powerful which is demonstrated by [many
examples](https://ergoplatform.org/docs/ErgoScript.pdf) including more [advanced potential
applications](https://ergoplatform.org/docs/AdvancedErgoScriptTutorial.pdf), those are
published around a time the network was launched.
What was missing is the concrete recipe, a step-by-step guidance and tools to put conceptual
design of smart contracts into working application running on Ergo blockchain.

In this and the previously published [Appkit](https://ergoplatform.org/en/blog/2019_12_03_top5/)
and [ErgoTool](https://ergoplatform.org/en/blog/2019_12_31_ergo_tool/) posts we aim to
give updates on both new application and tooling development.

Ergo have expressive smart contracts and transaction model which allows an
implementation of fully trustless DEX protocol, in which signed buy and sell orders can be
put into the blockchain independently by buyers and sellers. An off-chain matching
service can observe the Ergo blockchain, find matching orders and submit the swap
transaction without knowing any secrets. The matching can be incentivized by _DEX reward_
payed as part of a _swap transaction_. Anyone who first discover the match of the two
orders can create the swap transaction and get a reward in ERGs.

In this post we describe a simple yet functional implementation of DEX protocol as a
command line interface (CLI) utility
([ErgoDexTool](https://github.com/ergoplatform/ergo-dex)) which is implemented using
[Appkit Commands Framework](https://github.com/ergoplatform/ergo-appkit).

## DEX Protocol Overview

There are three participants (buyer, seller and DEX) of the DEX dApp and five different
transaction types, which can be created by participants. The buyer wants to swap `ergAmt`
ERGs for `tAmt` of `TID` tokens (or seller wants to sell, who send the orders first
doesn't matter). Both the buyer and the seller can cancel their orders. The DEX off-chain
service can find matching orders and create a special `Swap` transaction to complete the
exchange without knowledge of any secrets, thus both the buyer and the seller are in full
control of their funds.

The following diagram fully specifies all the five transactions of the DEX scenario.
For each transaction there is a corresponding command in [ErgoDexTool](https://github.com/ergoplatform/ergo-dex) as well as
additional commands to list orders, create tokens etc.

![DEX](dex-contracts.png)

Let's look at the specification of each transaction:

#### BuyOrder Transaction

The transaction spends `E` amount of NERGs (NanoERGs which we will write `E: NERG`) from
one or more boxes in the `pk(buyer)` wallet. The transaction creates a `bid` box with
`ergAmt: NERG` protected by the `buyOrder` contract. The `Buy Order` transaction can be
created, signed and sent by the
[dex:BuyOrder](https://github.com/ergoplatform/ergo-dex/tree/blog-post#buy-tokens) command.
The command ensure that at run time the `bid` box contains the `buyOrder`
contract as the guarding script, so that the conditions specified in the diagram are
checked.

The `change` box is created to make the input and output sums of the transaction balanced.
The transaction fee box is omited to simplify the diagram, in real transaction it is added
explicitly as one of the transaction outputs.

#### CancelBuy, CancelSell Transactions

At any time, the `buyer` can cancel the order by sending `CancelBuy` transaction. The
transaction should satisfy the guarding `buyOrder` contract which protects the `bid` box.
As you can see on the diagram, both the `Cancel` and the `Swap` transactions can spend the
`bid` box. When a box have spending alternatives (or _spending path_) then each
alternative on the diagram is identified by unique name prefixed with `!` (`!cancel` and
`!swap` for the `bid` box). Each alternative have specific spending conditions. In our
example, when the `bid` box is spend by the `Cancel` transaction the `?buyer` condition
should be satisfied, which can be read as "the signature of the buyer should be presented
in the transaction". Therefore, only buyer can cancel the buy order. This "signature"
condition is only required for `!cancel` spending alternative and not required for
`!swap`. The same is true for cancelling `sellOrder`.

#### SellOrder Transaction

The `SellOrder` transaction is similar to the `BuyOrder` it has to do with tokens in
addition to ERGs. The `Sell Order` transaction can be created, signed and sent by the
[dex:SellOrder](https://github.com/ergoplatform/ergo-dex/tree/blog-post#sell-tokens)
command. The transaction spends `E: ERG` and `T: TID` tokens from seller's wallet
(specified as `pk(seller)` contract). The two outputs are `ask` and `change`. The change
is a standard box to balance transaction. The `ask` box stores `tAmt: TID` tokens to swap
and the `minErg: ERG` - a minimum amount of ERGs required in every box.

#### Swap Transaction

This is a key transaction in the DEX scenario which can be created, signed and sent by the
[dex:MatchOrders](https://github.com/ergoplatform/ergo-dex/tree/blog-post#match-orders)
command. The transaction has many of spending conditions on the input boxes and those
conditions are included in the `buyOrder` and `sellOrder` contracts which are verified
when the transaction is added to the blockchain (see
[buyer](https://github.com/ergoplatform/ergo-dex/tree/blog-post#buy-tokens) and
[seller](https://github.com/ergoplatform/ergo-dex/tree/blog-post#sell-tokens) contracts in
ErgoScript).

We can put all the conditions for spending `bid` and `ask` boxes on the diagram.
However, those conditions are not specified in the `bid` and `ask` boxes,
they are instead defined in the output boxes of the `Swap` transaction.
This is more convenient in the diagram, because most of the conditions relate to the
properties of the output boxes. We could specify those properties in the `bid` and `ask`
boxes, but then we would had to use more complex expressions for the same logic.

The `buyerOut@0` label tell us that the output is at the index `0` in the `OUTPUTS`
collection of the transaction and that we can refer to this box by the `buyerOut` name.
Thus we can label both the box itself and the arrow to give it a name.

The conditions shown in the `buyerOut` box have the form `bid ? condition`, which means
they should be checked before the `bid` box can be spent. 
The conditions have the following meaning:
1) `tAmt: TID` require the box to have `tAmt` amount of `TID` token
2) `R4 == bid.id`  require R4 register in the box to be equal to id of the
`bid` box.
3) `@contract` require the box to have the contract of the wallet where it is located on
the diagram, i.e. `pk(buyer)`
Compare this conditions with [buyer
contract](https://github.com/ergoplatform/ergo-dex/tree/blog-post#buy-tokens) defined in
ErgoScript.

Similar properties are added to the `sellerOut` box, which is specified to be at index `1`
and the name is given using label on the box itself, rather than on the arrow.

The `Swap` transaction spends two boxes `bid` and `ask` using the `!swap` spending path,
however unlike `!cancel` the conditions on the path are not specified. This is where the
`bid ?` and `ask ?` prefixes come into play, so the conditions on the `buyerOut` and
`sellerOut` boxes are moved to the `!swap` spending path of the `bid` and `ask` boxes
correspondingly.

If you look at the conditions of the output boxed, you will see that they exactly specify
the swap of values between seller's and buyer's wallets. Buyer get's the necessary amount
of `TID` token and seller get's the corresponding amount of ERGs. The `Swap` transaction
can be created when there are two matching boxes with `buyOrder` and `sellOrder` contracts.

## To recap

Ergo platform allows simple implementation of trustless decentralized exchange of crypto
assets directly on Ergo blockchain. And
[ErgoDexTool](https://github.com/ergoplatform/ergo-dex) is simple CLI application which 
implements DEX protocol transactions.

The development of CLI tool is motivated by the three goals:
1) anyone (with CLI skills) should be able to issue and trade tokens on Ergo (at
least using CLI, in the absence of better UI)
2) the implementation is simple, documented and can be used as an example of application
development on top of Ergo (and as an inspiration for other useful dApps).
3) the commands are implemented as a library of reusable components which can be
used by developers to design and implement a much better UI for Ergo DEX (e.g. Android or
Web).

## References

- [Ergo Sources](https://github.com/ergoplatform/ergo)
- [Ergo Appkit](https://github.com/ergoplatform/ergo-appkit)
- [Ergo Dex](https://github.com/ergoplatform/ergo-dex)

### Questions to be answered
 So probably we need a steps, how to make a swap in DEX. E.g. I want to swap Token1/BTC
 1. What is the request to extract Token1/BTC orderbook?
 2. How to construct correct transaction that put a new order to orderbook (market maker deal)
 3. How to construct correct transaction that takes some orders from orderbook (market taker deal)
 4. What if both? E.g. I want to make a request that is partially market taker, and the rest should
 go to market maker
 5. What is with parallel request/front running attacks? How to ensure that my transaction will be
 executed?
 6. Do we have a separate centralized backend for DEX?
 7. Can you please refer to DEX documentation, I should probably read more about it, before asking a
 lot of questions)
