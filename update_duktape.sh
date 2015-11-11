#!/bin/bash

SRC_LOCATION=duktape/src/main/jni/
#Download the things
wget -O duktape.tar.xz $(curl -s https://api.github.com/repos/svaarala/duktape/releases/latest | grep 'browser_' | cut -d\" -f4) 
#Extract the release
mkdir tmp
tar xvfC duktape.tar.xz tmp/
#Copy over the new sources
cp -R tmp/duktape-*/src/ $SRC_LOCATION
#Cleanup after ourselves
rm duktape.tar.xz
rm -r tmp
