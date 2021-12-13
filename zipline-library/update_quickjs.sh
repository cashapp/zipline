#!/bin/bash

set -e

SRC_LOCATION=zipline/native/quickjs

# Download the things: provide a link to the release binary (e.g. https://bellard.org/quickjs/quickjs-2019-07-09.tar.xz)
wget -O quickjs.tar.xz $1
# Extract the release
mkdir tmp
tar xvfC quickjs.tar.xz tmp/
# Clear target location
rm $SRC_LOCATION/*
# Copy over the sources we care about
cp tmp/quickjs-*/cutils.* $SRC_LOCATION
cp tmp/quickjs-*/libreg*.* $SRC_LOCATION
cp tmp/quickjs-*/libuni*.* $SRC_LOCATION
cp tmp/quickjs-*/list.h $SRC_LOCATION
cp tmp/quickjs-*/quickjs.* $SRC_LOCATION
cp tmp/quickjs-*/quickjs-atom.* $SRC_LOCATION
cp tmp/quickjs-*/quickjs-opcode.* $SRC_LOCATION
cp tmp/quickjs-*/VERSION $SRC_LOCATION
# Cleanup after ourselves
rm quickjs.tar.xz
rm -r tmp
