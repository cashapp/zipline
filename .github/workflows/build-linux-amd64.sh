#!/bin/bash

# Run this in Docker with the Zipline project mounted at /zipline.
#
# This script runs on CentOS 7 with devtoolset-7. We deliberately use an
# old distro so our artifacts will run on similarly old distros. We need
# to use Docker because GitHub actions doesn't offer virtual environments
# for obsolete distros.
#
# We need the distribution's libc and libcxx to be old-enough so its outputs
# work for our users. We also need a compiler that's new-enough that it has
# `stdatomic.h`. Yuck.

set -ex

mkdir -p build/jni/amd64/
mkdir -p zipline/src/jvmMain/resources/jni/amd64/
cmake -S zipline/src/jvmMain/ -B build/jni/amd64/ \
  -DQUICKJS_VERSION="$(cat zipline/native/quickjs/VERSION)" \
  -DCMAKE_SYSTEM_PROCESSOR=x86_64
cmake --build build/jni/amd64/ --verbose
cp -v build/jni/amd64/libquickjs.* zipline/src/jvmMain/resources/jni/amd64/
