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

set -e
set -x

cd /zipline
yum -y install centos-release-scl
yum -y install devtoolset-7 devtoolset-7-toolchain
. /opt/rh/devtoolset-7/enable
yum -y install epel-release
yum -y install cmake3
yum -y install java-1.8.0-openjdk-devel

mkdir -p build/jni/amd64/
mkdir -p zipline/src/jvmMain/resources/jni/amd64/
cmake3 -S zipline/src/jvmMain/ -B build/jni/amd64/ \
  -DQUICKJS_VERSION="$(cat zipline/native/quickjs/VERSION)" \
  -DCMAKE_SYSTEM_PROCESSOR=x86_64 \
  -DCMAKE_C_COMPILER=/opt/rh/devtoolset-7/root/usr/bin/gcc \
  -DCMAKE_CXX_COMPILER=/opt/rh/devtoolset-7/root/usr/bin/c++
cmake3 --build build/jni/amd64/ --verbose
