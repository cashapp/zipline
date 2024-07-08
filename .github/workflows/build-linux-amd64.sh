#!/bin/bash

set -e
set -x

mkdir -p build/jni/amd64/
mkdir -p zipline/src/jvmMain/resources/jni/amd64/
cmake3 -S zipline/src/jvmMain/ -B build/jni/amd64/ \
  -DQUICKJS_VERSION="$(cat zipline/native/quickjs/VERSION)" \
  -DCMAKE_SYSTEM_PROCESSOR=x86_64
cmake3 --build build/jni/amd64/ --verbose
