# ErgoTool: A Command Line Interface for Ergo

## Introduction

ErgoTool is a [command-line
interface](https://en.wikipedia.org/wiki/Command-line_interface) for
[Ergo](https://ergoplatform.org/) blockchain. It is implemented in Scala using
[Ergo Appkit](https://github.com/aslesarenko/ergo-appkit) library. ErgoTool is a Java
application and it can be executed on Java 8 or later compliant JRE. In addition ErgoTool
can be compiled to a native executable using GraalVM's
[native-image](https://www.graalvm.org/docs/reference-manual/native-image/). This
capability is inherited from Appkit, which is native-image friendly by design.

Native executables are especially attractive for CLI tools, because of the very fast start
up times (comparing to a typical start up pause of a JVM based executable). Please follow
the [installation instructions](#installation) to setup ErgoTool on your system.

## Installation

Installation of ErgoTool on your computer is simple and depend on how you want to obtain
the executable binary files.
- you can build ErgoTool yourself from source code (which is a recommended method for
security sensitive software) and use the executables generated this way.
- or alternatively (if you trust the developers) you can download pre-built executables and
install from release package.

The instructions below should work on POSIX OSes (i.e. Linux, MacOS) and were tested on
MacOS.

### Build ErgoTool From Source Code

#### 1. Clone ErgoTool repository

You need to have [git](https://git-scm.com/) installed on your system.
Open Terminal and use the following commands to clone the ErgoTool source code. 
```
$ git clone https://github.com/aslesarenko/ergo-tool.git
Cloning into 'ergo-tool'...
remote: Enumerating objects: 238, done.
remote: Counting objects: 100% (238/238), done.
remote: Compressing objects: 100% (124/124), done.
remote: Total 238 (delta 80), reused 206 (delta 48), pack-reused 0
Receiving objects: 100% (238/238), 42.44 KiB | 517.00 KiB/s, done.
Resolving deltas: 100% (80/80), done.
```
Once the clone operation is complete you can compile ErgoTool.

#### 2. Compile Java Executable

In order to compile ErgoTool you need to have SBT
[installed](https://www.scala-sbt.org/download.html) on your system. 

Run the following commands to build a Java fat jar file with ErgoTool and all the
dependencies needed to run it using the `java` launcher.

```shell
$ cd ergo-tool
$ sbt assembly
```

you should see the output which looks like the following 
```
[info] Loading global plugins from ~/.sbt/1.0/plugins

... test execution results

[info] Packaging ./target/scala-2.12/ergotool-3.1.0.jar ...
[info] Done packaging.
[success] Total time: 33 s, completed Dec 23, 2019 10:23:50 PM
```
Now run the compiled ErgoTool using the following command which runs the ErgoTool application.
Note, you should run this command while in the root folder of the cloned ErgoTool repository.

```
java -cp target/scala-2.12/ergotool-3.1.0.jar org.ergoplatform.appkit.ergotool.ErgoTool          
Please specify command name and parameters.

Usage:
ergotool [options] action [action parameters]

Available actions:
  address testnet|mainnet <mnemonic>
	return address for a given mnemonic and password pair
  checkAddress testnet|mainnet <mnemonic> <address>
	Check the given mnemonic and password pair correspond to the given address
  createStorage [<storageDir>="storage"] [<storageFileName>="secret.json"]
	Creates an encrypted storage file for the mnemonic entered by user
  extractStorage <storage file> address|masterKey|publicKey|secretKey mainnet|testnet
	Reads the file, unlocks it using password and extract the requested property from the given storage file.
  listAddressBoxes address [<limit>=10]
	list top <limit=10> confirmed unspent boxes owned by the given <address>
  mnemonic 
	generate new mnemonic phrase using english words and default cryptographic strength
  send <wallet file> <recipientAddr> <amountToSend>
	send the given <amountToSend> to the given <recipientAddr> using 
 the given <wallet file> to sign transaction (requests storage password)

Options:
  --conf
	configuration file path (relative to local directory) e.g. `--conf ergo_tool.json`
  --dry-run
	When used the command report what will be done without performing the actual operation.
```

The command prints usage information with the available commands and options.

#### 3. Generate Native Executable 

SBT build for ErgoTool is configured to generate native image using GraalVM's
[native-image](https://www.graalvm.org/docs/reference-manual/native-image/) utility. To
make `native-image` available the GraalVM package should be installed on your system. You
can follow [the instruction from Appkit](https://github.com/aslesarenko/ergo-appkit#setup)
to install both GraalVM and native-image. You can also use
[instructions](https://www.graalvm.org/downloads/) from GraalVM's site.

Ones GraalVM is installed use the following command to compile native image of ErgoTool. 

```shell 
$ sbt graalvm-native-image:packageBin
...
[info] [ergo-tool:2393]    classlist:  29,125.82 ms
[info] [ergo-tool:2393]        (cap):   4,064.53 ms
[info] [ergo-tool:2393]        setup:   6,738.59 ms
[info] [ergo-tool:2393]   (typeflow):  44,211.21 ms
[info] [ergo-tool:2393]    (objects):  45,740.46 ms
[info] [ergo-tool:2393]   (features):   4,697.90 ms
[info] [ergo-tool:2393]     analysis:  99,239.25 ms
[info] [ergo-tool:2393]     (clinit):  13,460.75 ms
[info] [ergo-tool:2393]     universe:  15,393.69 ms
[info] [ergo-tool:2393]      (parse):   3,645.10 ms
[info] [ergo-tool:2393]     (inline):   5,056.85 ms
[info] [ergo-tool:2393]    (compile):  26,131.81 ms
[info] [ergo-tool:2393]      compile:  38,958.14 ms
[info] [ergo-tool:2393]        image:   7,937.35 ms
[info] [ergo-tool:2393]        write:   2,322.71 ms
[info] [ergo-tool:2393]      [total]: 200,278.13 ms
[success] Total time: 208 s, completed Dec 23, 2019 10:43:40 PM
```

This will generate a native executable located in `./target/graalvm-native-image/ergo-tool`.     
Now you can call it from there using the following command and obtain the same usage message.

```shell
$ target/graalvm-native-image/ergo-tool
Please specify command name and parameters.
...
```
The generated `ergo-tool` executable is self-contained and can be moved to any directory
which is on the PATH environment variable.

NOTE, if you receive warning message mentioning `sunec` library like 
```
WARNING: The sunec native library, required by the SunEC provider ...
```
you need to run ergo-tool providing the location of the library using the following command
```
$ DYLD_LIBRARY_PATH=$GRAAL_HOME/jre/lib target/graalvm-native-image/ergo-tool
```
Here `$GRAAL_HOME` is environment variable pointing to the installation of GraalVM.

### Download ErgoTool Release Package 

Release packages are published on the
[releases](https://github.com/aslesarenko/ergo-tool/releases) page. Please download the
appropriate archive and extract it locally on you computer.

## Commands

The unit of execution in ErgoTool is a command. The command is specified by its name
when the tool is started and it can be followed by options and parameters. 
```
$ ./ergo-tool --conf config.json address mainnet "some secrete mnemonic phrase"
```
The command option is given by the name `conf` (with `--` prefix) and the value
`config.json` separated by the whitespace. Options (name-value pairs) can go anywhere in
the command line. All other components which remain after removing all the options are the
command name and parameters (`address`, `mainnet` and the mnemonic in the example).

For further detail of how a command line is parsed see this
[description](https://aslesarenko.github.io/ergo-tool/api/org/ergoplatform/appkit/ergotool/CmdLineParser$.html#parseOptions(args:Seq[String]):(Map[String,String],Seq[String])).

### Supported Commands
Click on the command name to open its detailed description.

 Command     |  Description       
-------------|--------------------
 [address](https://aslesarenko.github.io/ergo-tool/api/org/ergoplatform/appkit/ergotool/AddressCmd.html)     | `<networkType> <mnemonic>` <br/> Returns the address for a given mnemonic and password pair. Where `networkType` = `mainnet` or `testnet`
checkAddress | `<networkType> <mnemonic> <address>` <br/> Check the given mnemonic and password pair correspond to the given address
createStorage | `[<storageDir>="storage"] [<storageFileName>="secret.json"]` <br/> Creates an encrypted storage file for the mnemonic entered by user
  extractStorage | `<storage file> <property> <networkType>` <br/> Reads the file, unlocks it using password and extract the requested property from the given storage file. Where `property` is one of `address`, `masterKey`, `publicKey`, `secretKey`
  listAddressBoxes | `address [<limit>=10]` <br/> list top `limit=10` confirmed unspent boxes owned by the given `address`
  mnemonic | generate new mnemonic phrase using english words and default cryptographic strength
  send | `<wallet file> <recipientAddr> <amountToSend>` <br/> send the given `amountToSend` to the given `recipientAddr` using the given `wallet file` to sign the transaction (it will also request storage password)
         
## Contributions

All kinds of contributions are equally welcome. Don't hesitate to file a PR with fixes of
typos, documentation out of sync or something you believe should be fixed.
If you wish to start hacking on the code, you can file an issue with the description of
what you want to do. 
Not sure about your idea? Get in touch with a direct message.

#### ScalaDoc API Reference

ScalaDocs for the latest releases are always [available on project
site](https://aslesarenko.github.io/ergo-tool/api/org/ergoplatform/appkit/ergotool/ErgoTool$.html).
Please submit a PR if
you find typos or mistakes.

#### Preparing for native image generation

You may need to re-generate reflection and resources configs for native-image utility. 
To do that run ErgoToolSpec with `native-image-agent` configured.
The following should be added to command line.
```
-agentlib:native-image-agent=config-merge-dir=graal/META-INF/native-image
```

After that you will have to review changes made in the files and remove unnecessary
declarations.

#### Repository organization
TODO

## References

- [Ergo](https://ergoplatform.org/)
- [Ergo Appkit](https://github.com/aslesarenko/ergo-appkit)
- [Introduction to Appkit](https://ergoplatform.org/en/blog/2019_12_03_top5/)
- [Appkit Examples](https://github.com/aslesarenko/ergo-appkit-examples)
- [ErgoTool ScalaDocs](https://aslesarenko.github.io/ergo-tool/api/org/ergoplatform/appkit/ergotool/ErgoTool$.html)
