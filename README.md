# ErgoDexTool: A Command Line Interface for Decentralized Exchange on Ergo Blockchain

 - [Introduction](#introduction)
 - [DEX Protocol Overview](#dex-protocol-overview)
 - [Installation](#installation)
 - [Commands](#commands)
 - [Contributions](#contributions)
 - [References](#references)
 
## Introduction

ErgoDexTool is a [command-line
interface](https://en.wikipedia.org/wiki/Command-line_interface) for
[Ergo](https://ergoplatform.org/) blockchain. It is implemented in Scala using [Ergo
Appkit Commands](https://github.com/ergoplatform/ergo-appkit) framework. ErgoDexTool is a
Java application and it can be executed on Java 8 or later compliant JRE. In addition
ErgoDexTool can be compiled to a native executable using GraalVM's
[native-image](https://www.graalvm.org/docs/reference-manual/native-image/). This
capability is inherited from Appkit, which is native-image friendly by design.

Native executables are especially attractive for CLI tools, because of the very fast start
up times (comparing to a typical start up pause of a JVM based executable). Please follow
the [installation instructions](#installation) to setup ErgoDexTool on your system.

## DEX Protocol Overview

There are three participants (buyer, seller and DEX) of the DEX dApp and five different
transaction types, which can be created by participants. The buyer wants to swap `ergAmt`
ERGs for `tAmt` of `TID` tokens (or seller wants to sell, who send the orders first
doesn't matter). Both the buyer and the seller can cancel their orders. The DEX off-chain
service can find matching orders and create a special `Swap` transaction to complete the
exchange without knowledge of any secrets, thus both the buyer and the seller are in full
control of their funds.

The following diagram fully specifies all the five transactions of the DEX scenario.
For each transaction there is a corresponding command in ErgoDexTool as well as
additional commands to list orders, create tokens etc (see [Commands](#commands)).

![DEX](docs/dex-contracts.png)

## Installation

Installation of ErgoDexTool on your computer is simple and depend on how you want to obtain
the executable binary files.
- you can build ErgoDexTool yourself from source code (which is a recommended method for
security sensitive software) and use the executables generated this way.
- or alternatively (if you trust the developers) you can download pre-built executables and
install from release package.

The instructions below should work on POSIX OSes (i.e. Linux, MacOS) and were tested on
MacOS.

### Build ErgoDexTool From Source Code

#### 1. Clone ergo-dex repository

You need to have [git](https://git-scm.com/) installed on your system.
Open Terminal and use the following commands to clone the ErgoDexTool source code. 
```
$ git clone https://github.com/ergoplatfoeckm/ergo-dex.git
Cloning into 'ergo-dex'...
...
```
Once the clone operation is complete you can compile ErgoDexTool.

#### 2. Compile Java Executable

In order to compile ErgoDexTool you need to have SBT
[installed](https://www.scala-sbt.org/download.html) on your system. 

Run the following commands to build a Java fat jar file with ErgoDexTool and all the
dependencies needed to run it using the `java` launcher.

```shell
$ cd ergo-dex
$ sbt assembly
```

you should see the output which looks like the following 
```
[info] Loading global plugins from ~/.sbt/1.0/plugins
...
[info] Packaging ./target/scala-2.12//ergodex-0.1.0.jar ...
[info] Done packaging.
[success] Total time: 31 s, completed Mar 19, 2020 1:22:27 PM
```
Now run the compiled ErgoDexTool using the following command which runs the ErgoDexTool CLI application.
Note, you should run this command while in the root folder of the cloned ErgoDexTool repository.

```
$ java -jar target/scala-2.12/ergodex-0.1.0.jar --conf ergo_tool_config.json 
Please specify command name and parameters.

Usage:
ergotool [options] action [action parameters]

Available actions:
  dex:BuyOrder <wallet file> <ergAmount> <tokenId> <tokenAmount>, <dexFee>
	put a token buyer order with given <tokenId> and <tokenAmount> to buy at given <ergPrice> price with <dexFee> as a reward for anyone who matches this order with a seller, with wallet's address to be used for withdrawal 
 with the given <wallet file> to sign transaction (requests storage password)
  dex:CancelOrder <wallet file> <orderBoxId>
	claim an unspent buy/sell order (by <orderBoxId>) and sends the ERGs/tokens to the address of this wallet (requests storage password)
  dex:IssueToken <wallet file> <ergAmount> <tokenAmount> <tokenName> <tokenDesc> <tokenNumberOfDecimals>
	issue a token with given <tokenName>, <tokenAmount>, <tokenDesc>, <tokenNumberOfDecimals> and <ergAmount> with the given <wallet file> to sign transaction (requests storage password)
  dex:ListMatchingOrders 
	show matching token seller's and buyer's orders
  dex:ListMyOrders <address>
	show buy and sell orders created from the given address
  dex:MatchOrders <wallet file> <sellerHolderBoxId> <buyerHolderBoxId>      <minDexFee
	match an existing token seller's order (by <sellerHolderBoxId>) and an existing buyer's order (by <buyerHolderBoxId) and send tokens to buyer's address(extracted from buyer's order) and Ergs to seller's address(extracted from seller's order) claiming the minimum fee of <minDexFee> with the given <wallet file> to sign transaction (requests storage password)
  dex:SellOrder <wallet file> <ergPrice> <tokenId> <tokenAmount> <dexFee>
	put a token seller order with given <tokenId> and <tokenAmount> for sale at given <ergPrice> price with <dexFee> as a reward for anyone who matches this order with buyer, with wallet's address to be used for withdrawal 
 with the given <wallet file> to sign transaction (requests storage password)
  dex:ShowOrderBook <tokenId>
	show order book, sell and buy order for a given token id
  help <commandName>
	prints usage help for a command

Options:
  --conf
	configuration file path relative to the local directory (Example: `--conf ergo_tool.json`
  --dry-run
	Forces the command to report what will be done by the operation without performing the actual operation.
  --limit-list
	Specifies a number of items in the output list.
  --print-json
	Forces the commands to print json instead of table rows.
```

The command prints usage information with the available commands and options.

### Download ErgoDex Release Package 

Release packages are published on the
[releases](https://github.com/ergoplatform/ergo-dex/releases) page. Please download the
appropriate archive and extract it locally on you computer.

## Commands

A unit of execution in ErgoDexTool is a command. The command is specified by its name
when the tool is started and it can be followed by options and parameters. 
```
$ ./ergo-dex.sh dex:IssueToken storage.json 1000000 1000 "TKN" "DEX test token" "2" 
```
The command option is given by the name with `--` prefix (i.e. `--conf`) and the value
(i.e. `ergo_tool_config.json`) separated by the whitespace. Options (name-value pairs) can
go anywhere in the command line. All other components which remain after removing all the
options are the command name and parameters (i.e. `storage.json`, `1000000` etc).

For further detail of how a command line is parsed see this
[description](https://github.com/ergoplatform/ergo-appkit/blob/75468e4c0fd4cf1c2417be970119c47ba3c5dbb7/appkit/src/main/scala/org/ergoplatform/appkit/cli/CmdLineParser.scala#L8).

#### Supported Commands
Click on the command name to open its detailed description.

 Command     |  Description       
-------------|--------------------
 [dex:IssueToken]()     | `<wallet file> <ergAmount> <tokenAmount> <tokenName> <tokenDesc> <tokenNumberOfDecimals>` <br/> issue a token with given `tokenName`, `tokenAmount`, `tokenDesc`, `tokenNumberOfDecimals` and `ergAmount` with the given `wallet file` to sign transaction (requests storage password)
         
TODO [generate](https://github.com/ergoplatform/ergo-appkit/issues/45) table for the rest
of the commands
         
### Issue A New Token

The first operation in the lifecycle of a new token is its issue. Ergo natively support
issue, storage and transfer of tokens. New tokens can be issued according to the [Assets
Standard](https://github.com/ergoplatform/eips/blob/master/eip-0004.md).

A token is issued by creating a new box with the `ergAmount` of ERGs and (`tokenId`,
`tokenAmount`) pair in the `R2` register, where `tokenId` is selected automatically using
the id of the first input box of the transaction (as required by Ergo protocol).
Additional registers should also be specified as required by
[EIP-4](https://github.com/ergoplatform/eips/blob/master/eip-0004.md) standard. The
`dex:IssueToken` command uses a wallet storage given by `<wallet file>` to transfer given
`ergAmount` of ERGs to a new box with tokens. The new box will belong the same wallet
given by `<wallet file>`.

The following ErgoDexTool command allows to issue a new token on the Ergo blockchain.
```
$ ./ergo-dex.sh dex:IssueToken "storage/E2.json" 50000000 1000000 "TKN" "Generic token" 2
Creating prover... Ok
Loading unspent boxes from at address 9hHDQb26AjnJUXxcqriqY1mnhpLuUeC81C4pggtK7tupr92Ea1K... Ok
Signing the transaction... Ok
...
``` 
[output](https://gist.github.com/greenhat/a14094cdba984222c5841e65870071bc)

### Sell Tokens

When we have tokens in a box we can sell them on Ergo DEX by creating a sell order and
submit it to the order book. In ErgoDexTool implementation we create orders and store them
directly on the Ergo blockchain. The created order is the `ask` box (see diagram)
protected with the special _seller contract_ holding the minimum amount of ERGs (`minErg`)
and `tokenAmount` of the `TID` tokens.

```scala
/** The `sellOrder` contract which protects the `ask` box (see the diagram)
  * @param ergAmt   nanoERG amount seller wants to receive for the tokens
  * @param pkSeller public key of the seller
  * @return  sigma protocol proposition (see ErgoTree Specification)
  */
def sellOrder(ctx: Context, ergAmt: Long, pkSeller: SigmaProp): SigmaProp = {
  import ctx._
  pkSeller || (
    OUTPUTS.size > 1 &&
    OUTPUTS(1).R4[Coll[Byte]].isDefined
  ) && {
    val knownBoxId = OUTPUTS(1).R4[Coll[Byte]].get == SELF.id
    OUTPUTS(1).value >= ergAmt &&
    knownBoxId &&
    OUTPUTS(1).propositionBytes == pkSeller.propBytes
  }
}
```
The contract is
[implemented](http://github.com/ergoplatform/ergo-contracts/blob/391912fbd466c1b262e8d2fa61d4bfd94981df4a/verified-contracts/src/main/scala/org/ergoplatform/contracts/AssetsAtomicExchange.scala#L41-L58)
in the repository of certified contracts.

The `sellOrder` contract guarantees that the seller box can be spent:
1) by seller itself, which is the way for a seller to [cancel the
order](#canceling-the-orders)
2) by a _swap transaction_ created by anyone else in which the `ask` box is spent together
(i.e. atomically) with the matched `bid` box (see the diagram and also [buy
tokens](#buy-tokens)).

The following command can be used to create a new `ask` box to sell tokens:
```
$ ./ergo-dex.sh dex:SellOrder storage/secret.json 10000000 "d0105f7469be3ac90f16d943b29133f16c3bf4d85bd754656194cead849baf1e" 3 5000000
Storage password>
Creating prover... Ok
Loading unspent boxes from at address 3WxrCKgrcmS7oPpWXgwuKNiB1JSNEmpyqaXPM1cBrXiJY1jhk4Ep... Ok
Signing the transaction... Ok
```
[output](https://gist.github.com/greenhat/9536a7c13106f6a99530720504a6031a)

### Buy Tokens

You may also want to buy tokens, either because you believe it's value is going to surge
or you need one to participate in a dApp which require having some tokens or whatever
reason you may have. You can create a _buy order_ and submit it to the order book. The
created order is a `bid` box (see diagram) protected with the special _buyer contract_.
The `bid` box holds the necessary amount of ERGs and whose contract checks the swap
conditions (given `tokenId` and `tAmt` you want to buy).

```scala
/** The `buyOrder` contract which protects the `bid` box (see the diagram) 
  * @param tokenId token id to buy
  * @param tAmt token amount to buy
  * @param pkBuyer public key for the buyer
  * @return sigma protocol proposition (see ErgoTree Specification) 
  */
def buyer(
  ctx: Context,
  tokenId: Coll[Byte],
  tAmt: Long,
  pkBuyer: SigmaProp
): SigmaProp = {
  import ctx._
  pkBuyer || {
    (OUTPUTS.nonEmpty && OUTPUTS(0).R4[Coll[Byte]].isDefined) && {
      val tokens = OUTPUTS(0).tokens
      val tokenDataCorrect = tokens.nonEmpty &&
        tokens(0)._1 == tokenId &&
        tokens(0)._2 >= tAmt

      val knownId = OUTPUTS(0).R4[Coll[Byte]].get == SELF.id
      tokenDataCorrect &&
      OUTPUTS(0).propositionBytes == pkBuyer.propBytes &&
      knownId
    }
  }
}
```
The contract is
[implemented](http://github.com/ergoplatform/ergo-contracts/blob/5d064a71d2300684d18069912776b0e125f5c5bd/verified-contracts/src/main/scala/org/ergoplatform/contracts/AssetsAtomicExchange.scala#L12-L40)
in the repository of certified contracts.

The buyer contract guarantees that the buyer box can be spent:
1) by the buyer itself, which is the way for the buyer to [cancel the order](#cancel-order)
2) by a _swap transaction_ created by anyone else in which the `bid` box is spent together (i.e atomically)
with the matched `ask` box (see the diagram and also [sell tokens](#sell-tokens)).

The following command can be used to create a new _buy order_ to buy tokens:
```
$ ./ergo-dex.sh dex:BuyOrder storage/secret.json 10000000 "d0105f7469be3ac90f16d943b29133f16c3bf4d85bd754656194cead849baf1e" 3 5000000
Storage password>
Creating prover... Ok
Loading unspent boxes from at address 3WxrCKgrcmS7oPpWXgwuKNiB1JSNEmpyqaXPM1cBrXiJY1jhk4Ep... Ok
Signing the transaction... Ok
```
[output](https://gist.github.com/greenhat/0c75738edadb9870a2cfb492d6069a57)

### List My Orders

To show your outstanding buy/sell orders (that use your public key in their contracts) use
`dex:ListMyOrders` command:
```
$ ./ergo-dex.sh dex:ListMyOrders "9hHDQb26AjnJUXxcqriqY1mnhpLuUeC81C4pggtK7tupr92Ea1K"
Storage password>
Creating prover... Ok
Loading seller boxes... Ok
Loading buyer boxes... Ok
Orders created with key 9hHDQb26AjnJUXxcqriqY1mnhpLuUeC81C4pggtK7tupr92Ea1K:
Sell:
Box id                                                           Token ID                                                         Token amount  Token price  Box value
357bba87df0299ed692e3945fcf6ab88465e0dc7fff6db48957c939603ae23f0 d0105f7469be3ac90f16d943b29133f16c3bf4d85bd754656194cead849baf1e 3             10000000     5000000
Buy:
Box id                                                           Token ID                                                         Token amount  Box value
```
[output](https://gist.github.com/greenhat/6b1b2f7be1279de49e33045b3fac6f81)

### Show order book

To show all outstanding sell and buy orders for a particular token use `dex:ShowOrderBook`
command. Below is an example of using `dex:ShowOrderBook`:
```
$ ./ergo-dex.sh dex:ShowOrderBook "56cf33485be550cc32cf607255be8dc8c32522d0539f6f01d44028dc1d190450"
Loading seller boxes... Ok
Loading buyer boxes... Ok
Order book for token 56cf33485be550cc32cf607255be8dc8c32522d0539f6f01d44028dc1d190450:

Sell orders:
BoxId                                                             Token Amount   Erg Amount(including DEX fee)
10008261a053ffb63919a410059a4e6bf1c87de6020198138544ebcbf9c2182c      100           1005000000

Buy orders:
BoxId                                                             Token Amount   Erg Amount(including DEX fee)
8cc5b491f31db054f80c93b08704155fa68fa3a8477b87a8c0ef7097cda3c80d      100           1005000000
```

### Match Orders
To match and swap buy and sell orders the `dex:MatchOrders` command can be used.
The command creates and sends a new transaction which spends the `bid` and `ask` boxes
with matching `ergAmt` and `tAmt` values. 
The command: 
1)  requests the storage password from the user, reads the storage file,
unlocks it using password and gets the secret to sign the transaction; 
2) finds the boxes with buyer's and seller's orders (using `buyerHolderBoxId` and `sellerHolderBoxId`);
3) computes the amount of change (including dexFee and checking it is at least `minDexFee`);
4) create output boxes for buyer's tokens and seller's Ergs;
5) create a transaction spending buyer's and seller's order boxes and sign it using
sender`s key.

```
$ ./ergo-dex.sh dex:MatchOrders storage "10008261a053ffb63919a410059a4e6bf1c87de6020198138544ebcbf9c2182c" "d5b150c73baad5debbaaeab27ee269cbd487ff0dcab8ff0cd5084dff8f0db167" 1000000
Storage password>
Creating prover... Ok
Loading seller's box (10008261a053ffb63919a410059a4e6bf1c87de6020198138544ebcbf9c2182c)... Ok
Loading buyer's box (d5b150c73baad5debbaaeab27ee269cbd487ff0dcab8ff0cd5084dff8f0db167)... Ok
Signing the transaction... Ok 
```
[output](https://github.com/ergoplatform/ergo-tool/issues/44#issuecomment-599982502)

### Cancel Order

To cancel a buy/sell order you need to "spend" the box of the order (`bid` of `ask` boxes
on the diagram) by sending its assets (coins and/or tokens) back to your own address. The
following command can be used to spend sell/buy order box and send the assets home:
```
$ ./ergo-dex.sh dex:CancelOrder storage/secret.json "357bba87df0299ed692e3945fcf6ab88465e0dc7fff6db48957c939603ae23f0"
Storage password>
Creating prover... Ok
Loading order's box (357bba87df0299ed692e3945fcf6ab88465e0dc7fff6db48957c939603ae23f0)... Ok
Signing the transaction... Ok
```
[output](https://gist.github.com/greenhat/6c70999c763a70a7253170d33127e9da)
         
## Contributions

All kinds of contributions are equally welcome. Don't hesitate to file a PR with fixes of
typos, documentation out of sync or something you believe should be fixed.
If you wish to start hacking on the code, you can file an issue with the description of
what you want to do. 
Not sure about your idea? Get in touch with a direct message.

#### ScalaDoc API Reference

ScalaDocs for the latest releases are always [available on project
site](https://ergoplatform.github.io/ergo-dex/api/org/ergoplatform/dex/ErgoDexTool$.html).
Please submit a PR if you find typos or mistakes.

#### Preparing for native image generation

You may need to re-generate reflection and resources configs for native-image utility. 
To do that run ErgoDexToolSpec with `native-image-agent` configured.
The following should be added to command line.
```
-agentlib:native-image-agent=config-merge-dir=graal/META-INF/native-image
```

After that you will have to review changes made in the files and remove unnecessary
declarations.

## References

- [Ergo](https://ergoplatform.org/)
- [Ergo Appkit](https://github.com/ergoplatform/ergo-appkit)
- [Introduction to Appkit](https://ergoplatform.org/en/blog/2019_12_03_top5/)
- [Appkit Examples](https://github.com/aslesarenko/ergo-appkit-examples)
- [ErgoDex ScalaDocs](https://ergoplatform.github.io/ergo-dex/api/org/ergoplatform/dex/ErgoDexTool$.html)
- [ErgoTree Specification](https://ergoplatform.org/docs/ErgoTree.pdf)

