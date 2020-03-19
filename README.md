# ErgoDexTool: A Command Line Interface for Decentralized Exchange on Ergo Blockchain

 - [Introduction](#introduction)
 - [Installation](#installation)
 - [Commands](#commands)
 - [Contributions](#contributions)
 - [References](#references)
 
## Introduction

ErgoDexTool is a [command-line
interface](https://en.wikipedia.org/wiki/Command-line_interface) for
[Ergo](https://ergoplatform.org/) blockchain. It is implemented in Scala using
[Ergo Appkit](https://github.com/ergoplatform/ergo-appkit) library. ErgoDexTool is a Java
application and it can be executed on Java 8 or later compliant JRE. In addition ErgoTool
can be compiled to a native executable using GraalVM's
[native-image](https://www.graalvm.org/docs/reference-manual/native-image/). This
capability is inherited from Appkit, which is native-image friendly by design.

Native executables are especially attractive for CLI tools, because of the very fast start
up times (comparing to a typical start up pause of a JVM based executable). Please follow
the [installation instructions](#installation) to setup ErgoDexTool on your system.

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

#### 1. Clone ErgoTool repository

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
  dex:ListMyOrders <storageFile>
	show buy and sell orders created from the address of this wallet
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

### Download ErgoTool Release Package 

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

### Supported Commands
Click on the command name to open its detailed description.

 Command     |  Description       
-------------|--------------------
 [dex:IssueToken]()     | `<wallet file> <ergAmount> <tokenAmount> <tokenName> <tokenDesc> <tokenNumberOfDecimals>` <br/> issue a token with given `tokenName`, `tokenAmount`, `tokenDesc`, `tokenNumberOfDecimals` and `ergAmount` with the given `wallet file` to sign transaction (requests storage password)
         
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
To do that run ErgoToolSpec with `native-image-agent` configured.
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
