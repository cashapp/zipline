#!/bin/bash
# this builds a dynamic library for Mac OS X.
gcc -shared -std=c99 -o libduktape.dylib -Isrc/ duktape.c duktape-jni.c -lm -I$JAVA_HOME/include/ -I$JAVA_HOME/include/darwin/
