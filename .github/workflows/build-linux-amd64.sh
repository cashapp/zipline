#!/bin/bash

# Run this in Docker with the Zipline project mounted at /zipline.
#
# We use Docker because GitHub actions doesn't offer virtual environments for old distros, and we
# need to build on old distros to run on old distros.

set -e
set -x

cd /zipline

dnf install gcc-c++ cmake3 java-11-openjdk-devel

mkdir -p build/jni/amd64/
mkdir -p zipline/src/jvmMain/resources/jni/amd64/
cmake3 -S zipline/src/jvmMain/ -B build/jni/amd64/ \
  -DQUICKJS_VERSION="$(cat zipline/native/quickjs/VERSION)" \
  -DCMAKE_SYSTEM_PROCESSOR=x86_64 \
  -DCMAKE_C_COMPILER=/usr/bin/gcc \
  -DCMAKE_CXX_COMPILER=/usr/bin/g++
cmake3 --build build/jni/amd64/ --verbose
