#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-only
set -eu
cd "$(dirname "$0")/.."
python3 tools/build.py --gen-only
mkdir -p build/android-tests
FIRMWARE="-O2 -fwrapv -ffp-contract=off -std=gnu11 -w -Ibuild/gen -Ifirmware/src"
# The display copy of the firmware, localized as android/build.py does.
cc $FIRMWARE -c android/native/display.c -o build/android-tests/display_full.o
objcopy --wildcard --keep-global-symbol='pt_display_*' build/android-tests/display_full.o build/android-tests/display.o
cc $FIRMWARE android/native/engine.c android/tests/engine_test.c build/android-tests/display.o -lm \
    -o build/android-tests/engine_test
PT_SAVE_PATH=build/android-tests/session.bin build/android-tests/engine_test
PT_LOAD_PATH=build/android-tests/session.bin build/android-tests/engine_test
cc -O2 -std=c11 android/tests/output_test.c -lm -o build/android-tests/output_test
cc -O2 -std=c11 android/tests/converter_test.c -lm -o build/android-tests/converter_test
build/android-tests/converter_test
# The engine without drawing must match the drawing engine bit for bit.
cc $FIRMWARE android/tests/display_test.c build/android-tests/display.o -lm -o build/android-tests/display_dry
cc $FIRMWARE -DPT_ENGINE_DRAWS android/tests/display_test.c build/android-tests/display.o -lm \
    -o build/android-tests/display_draws
dry=$(build/android-tests/display_dry 120)
draws=$(build/android-tests/display_draws 120)
[ "$dry" = "$draws" ] || { echo "FAIL: audio or UI state differs without drawing: $dry vs $draws"; exit 1; }
echo "PASS: 120 s of random panel, MIDI and Scape input; audio and every display snapshot identical without drawing ($dry)"
build/android-tests/output_test
cc -O2 -std=c11 android/tests/converter_test.c -lm -o build/android-tests/converter_test
build/android-tests/converter_test
# The engine without drawing must match the drawing engine bit for bit.
cc $FIRMWARE android/tests/display_test.c build/android-tests/display.o -lm -o build/android-tests/display_dry
cc $FIRMWARE -DPT_ENGINE_DRAWS android/tests/display_test.c build/android-tests/display.o -lm \
    -o build/android-tests/display_draws
dry=$(build/android-tests/display_dry 120)
draws=$(build/android-tests/display_draws 120)
[ "$dry" = "$draws" ] || { echo "FAIL: audio or UI state differs without drawing: $dry vs $draws"; exit 1; }
echo "PASS: 120 s of random panel, MIDI and Scape input; audio and every display snapshot identical without drawing ($dry)"
javac -d build/android-tests android/java/org/paleotone/synth/AudioRecovery.java android/tests/AudioRecoveryTest.java
java -ea -cp build/android-tests org.paleotone.synth.AudioRecoveryTest
javac -d build/android-tests android/java/org/paleotone/synth/KioskSession.java android/tests/KioskSessionTest.java
java -ea -cp build/android-tests org.paleotone.synth.KioskSessionTest
cc -O2 -std=c11 android/tests/capture_test.c -o build/android-tests/capture_test
build/android-tests/capture_test
cc -O2 -std=c11 android/tests/sample_test.c -lm -o build/android-tests/sample_test
build/android-tests/sample_test
javac -d build/android-tests android/java/org/paleotone/synth/WavSample.java android/tests/WavSampleTest.java
python3 -c 'from android.build import starter_samples; from pathlib import Path; starter_samples(Path("build/android-tests/starter"))'
java -ea -cp build/android-tests org.paleotone.synth.WavSampleTest "build/android-tests/starter/Flute C4.wav" "build/android-tests/starter/Jungle C3.wav"
cc $FIRMWARE android/tests/scape_test.c -lm -o build/android-tests/scape_test
build/android-tests/scape_test
javac -encoding UTF-8 -d build/android-tests android/java/org/paleotone/synth/ui/*.java android/tests/UiTest.java
java -ea -cp build/android-tests org.paleotone.synth.ui.UiTest
# PALEO and the live Scape conductor, through the tablet's bridge.
c++ -O2 -std=c++17 -w -pthread -Ipaleo android/tests/paleo_bridge_test.cpp android/native/paleo_bridge.cpp \
    paleo/engine/paleo.cpp paleo/engine/conductor.cpp paleo/faust/gen/[!he]*.cpp paleo/faust/gen/horn.cpp \
    paleo/faust/gen/harmonium.cpp -o build/android-tests/paleo_bridge_test
build/android-tests/paleo_bridge_test
