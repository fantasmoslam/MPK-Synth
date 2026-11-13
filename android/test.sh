#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-only
# The synth, looper, output stage and interface logic, on a desktop compiler and JDK (no Android SDK).
set -eu
cd "$(dirname "$0")/.."
B=build/android-tests
rm -rf "$B";mkdir -p "$B"
MPK="android/native/mpk/engine.c android/native/mpk/poly106.c android/native/mpk/kit.c"
cc -O2 -std=gnu11 -Wall -Wno-implicit-fallthrough android/tests/synth_test.c $MPK -lm -o $B/synth_test
$B/synth_test
cc -O2 -std=c11 -Wall android/tests/looper_test.c -o $B/looper_test
$B/looper_test
cc -O2 -std=c11 android/tests/output_test.c -lm -o $B/output_test
$B/output_test
cc -O2 -std=gnu11 -Wall -Wno-implicit-fallthrough android/tools/tables.c $MPK -lm -o $B/tables
$B/tables > $B/tables.txt
javac -encoding UTF-8 -d $B android/java/org/fantasmoslam/mpksynth/AudioRecovery.java android/tests/AudioRecoveryTest.java
java -ea -cp $B org.fantasmoslam.mpksynth.AudioRecoveryTest
javac -encoding UTF-8 -d $B android/java/org/fantasmoslam/mpksynth/ui/*.java android/tests/TableSource.java \
    android/tests/MpkTest.java android/tests/MpkRouterTest.java android/tests/RigTest.java android/tests/TutorialTest.java android/tests/HomeTest.java
for t in MpkTest MpkRouterTest RigTest TutorialTest HomeTest;do java -ea -cp $B org.fantasmoslam.mpksynth.ui.$t $B/tables.txt;done
