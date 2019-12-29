# Managing Ergo Coins with ErgoTool

## Introduction

[ErgoTool](https://github.com/aslesarenko/ergo-tool) is a command line interface (CLI) for
Ergo blockchain. You can use ErgoTool without running your own Ergo node. Keep in mind,
however, that running a node is the most secure way to communicate with the blockchain
network, but ErgoTool aims to provide a more foundational tools at your disposal. Surely
you can use it with your own running node. See also discussion in the [security notes
section](#security-notes) below.

In this post we will walk through simple steps to generate a mnemonic phrase, create a
local secret storage and use it to send ERGs between addresses.

## Getting Started

First of all we need to install ErgoTool on our system by following the [installation
instructions](https://github.com/aslesarenko/ergo-tool#installation). Once completed we can

## Security Notes

ErgoTool is created with security in mind and tries its best to safeguard the usage of
sensitive information like mnemonic phrases (which are never stored persistently locally), 
passwords (which are never shown on the screen) etc. In addition secret keys are never
stored on local disk unencrypted and surely never sent anywhere.

Also security practices are being followed to protect password storage during ErgoTool
operations. For example, password characters are stored in mutable buffers which are erased
as soon as possible so that they are not leaked to Java Garbage Collector and to the OS
when the running ErgoTool process terminates.
