#!/bin/sh
# Render review screenshots of the tablet interface on a desktop JDK (no Android SDK needed).
# Usage: sh android/preview/render.sh OUTPUT_DIR
set -e
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
OUT=${1:-$ROOT/build/preview}
CLASSES=$(mktemp -d)
javac -encoding UTF-8 -d "$CLASSES" "$ROOT"/android/java/org/paleotone/synth/ui/*.java "$ROOT"/android/preview/Preview.java
java -Djava.awt.headless=true -cp "$CLASSES" Preview "$OUT" "$ROOT/assets/fonts"
rm -rf "$CLASSES"
echo "Screenshots in $OUT"
