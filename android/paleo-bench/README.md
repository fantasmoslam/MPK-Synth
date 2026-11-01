# PALEO speed test (Step 0)

Times every PALEO instrument, the hall reverb, the echo, sample playback and the busiest moment of STAMPEDE on
a tablet, so the engine can be planned around real numbers. It is a command-line program run through adb; it
changes nothing on the tablet.

- The Faust designs and their generated C++ now live in `paleo/faust/` (shared with the PALEO engine).
- `bench.cpp`: the test.
- `build.sh`: builds `build/paleo-bench/<abi>/paleo-bench` with the NDK (or `build.sh host` for a computer).

Run on the Tab S6 Lite (Windows PowerShell, from the folder holding the arm64-v8a build):

```
adb push .\paleo-bench /data/local/tmp/paleo-bench
adb shell chmod 755 /data/local/tmp/paleo-bench
adb shell /data/local/tmp/paleo-bench
```

It takes about two minutes and prints its results. The SM-T510 uses the armeabi-v7a build.
