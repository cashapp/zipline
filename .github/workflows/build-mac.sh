#!/bin/bash

MACOS_ARCH=""
CMAKE_ARCH=""

function usage {
  cat << EOF
  Usage ./build-mac.sh <opts>
  If no opts are provided, they will be auto-detected based on the build environment.
  Build needs to be run in root Zipline directory.

  -h    Usage

  -a    Mac chip architecture
        Options:
          - aarch64
          - x86_64
        Example: -a aarch64

  -c    cmake architecture
        Options:
          - arm64
          - x86_64
        Example: -c arm64
EOF
  exit 0
}

function autoDetect() {
  RAW_ARCH=$(uname -m)
  if [[ "$RAW_ARCH" == "arm64" ]]; then
    MACOS_ARCH="aarch64"
    CMAKE_ARCH="arm64"
  elif [[ "$RAW_ARCH" == "x86_64" ]]; then
    MACOS_ARCH="x86_64"
    CMAKE_ARCH="x86_64"
  else
    echo "Unable to detect Mac architecture."
    exit 1
  fi
}

function echoRun() {
  echo "$ $*"
  "$@"
  echo ""
}

function build() {
  echo "MACOS_ARCH=${MACOS_ARCH}"
  echo "CMAKE_ARCH=${CMAKE_ARCH}"
  echo ""

  # Clean the build/jni directory to prevent confusing failure cases
  echoRun rm -rf build/jni/$MACOS_ARCH

  # Build commands extracted from Github Actions
  echoRun mkdir -p build/jni/$MACOS_ARCH/
  echoRun mkdir -p zipline/src/jvmMain/resources/jni/$MACOS_ARCH/
  echoRun cmake -S zipline/src/jvmMain/ -B build/jni/$MACOS_ARCH/ \
    -DQUICKJS_VERSION="$(cat zipline/native/quickjs/VERSION)" \
    -DCMAKE_OSX_ARCHITECTURES=$CMAKE_ARCH
  echoRun cmake --build build/jni/$MACOS_ARCH/ --verbose
  echoRun cp -v build/jni/$MACOS_ARCH/libquickjs.* zipline/src/jvmMain/resources/jni/$MACOS_ARCH/

  echo "Build complete."
  exit 0
}

function processArguments {
  while getopts "h?a:c:" opt; do
    case "$opt" in
    h|\?)
        usage
        exit 0
        ;;
    a)
        MACOS_ARCH=${OPTARG}
        ;;
    c)
        CMAKE_ARCH=${OPTARG}
        ;;
    esac
  done

  shift $((OPTIND-1))
}

# main
autoDetect
processArguments "$@"
build

exit 1
