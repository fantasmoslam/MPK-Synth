#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-only
# Build the PALEO speed test for the tablets with the NDK (ANDROID_HOME set as for android/build.py),
# or for this computer with "host". Output: build/paleo-bench/<abi>/paleo-bench
set -e
ROOT=$(cd "$(dirname "$0")/../.." && pwd); SRC="$ROOT/android/paleo-bench"; OUT="$ROOT/build/paleo-bench"
FLAGS="-O2 -std=c++17 -w -fno-exceptions -fno-rtti"
if [ "$1" = host ]; then
  mkdir -p "$OUT/host"; ${CXX:-c++} $FLAGS "$SRC"/bench.cpp "$ROOT"/paleo/faust/gen/*.cpp -I"$ROOT"/paleo/faust -o "$OUT/host/paleo-bench"; exit 0
fi
NDK="$ANDROID_HOME/ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64/bin"
for pair in arm64-v8a:aarch64-linux-android28-clang++ armeabi-v7a:armv7a-linux-androideabi28-clang++; do
  abi=${pair%%:*}; cc=${pair#*:}; mkdir -p "$OUT/$abi"
  extra=""; [ "$abi" = armeabi-v7a ] && extra="-mfpu=neon -mfloat-abi=softfp"
  "$NDK/$cc" $FLAGS $extra -static-libstdc++ "$SRC"/bench.cpp "$ROOT"/paleo/faust/gen/*.cpp -I"$ROOT"/paleo/faust -o "$OUT/$abi/paleo-bench"
done
