# ErgoTool: A command line utility for Ergo

## Introdution
 TODO
 
## Generate Native Executable 

Sbt is configured to generate native image using GraalVM's [native-image]().
Use the following command
```shell 
$ sbt graalvm-native-image:packageBin
```
     
## Contributions

You may need to re-generate reflection and resources configs for native-image. 
To do that run ErgoToolSpec with `native-image-agent` configured.

```
-agentlib:native-image-agent=config-merge-dir=graal/META-INF/native-image
```

After that you will have to review changes made in the files and remove unnecessary
declarations.

## References

- [Ergo]()
- [Ergo Appkit]()
- [Introduction to Appkit]()
- [Appkit Examples]()
