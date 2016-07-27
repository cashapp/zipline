#!/bin/bash

SRC_LOCATION=duktape/src/main/jni/duktape

# Download the things: provide a link to the release binary (e.g. https://github.com/svaarala/duktape/releases/download/v1.4.0/duktape-1.4.0.tar.xz)
wget -O duktape.tar.xz $1
# Extract the release
mkdir tmp
tar xvfC duktape.tar.xz tmp/
# Copy over the new sources
cp tmp/duktape-*/src/* $SRC_LOCATION
# Cleanup after ourselves
rm duktape.tar.xz
rm -r tmp
