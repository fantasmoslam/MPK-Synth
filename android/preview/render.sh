#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-only
# Render review screenshots of the tablet interface on a desktop JDK (no Android SDK needed).
# Usage: sh android/preview/render.sh OUTPUT_DIR
set -e
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT=${1:-$ROOT/build/preview}
WORK=$(mktemp -d)
cc -O2 -std=gnu11 -w "$ROOT"/android/tools/tables.c "$ROOT"/android/native/mpk/*.c -lm -o "$WORK/tables"
"$WORK/tables" > "$WORK/tables.txt"
javac -encoding UTF-8 -d "$WORK" "$ROOT"/android/java/org/fantasmoslam/mpksynth/ui/*.java "$ROOT"/android/tests/TableSource.java "$ROOT"/android/preview/Preview.java
java -Djava.awt.headless=true -cp "$WORK" Preview "$OUT" "$ROOT/assets/fonts" "$WORK/tables.txt"
rm -rf "$WORK"
echo "Screenshots in $OUT"
