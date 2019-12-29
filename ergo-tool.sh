#!/usr/bin/env bash

DYLD_LIBRARY_PATH=$GRAAL_HOME/jre/lib target/graalvm-native-image/ergo-tool $*