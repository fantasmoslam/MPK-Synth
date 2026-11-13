# MPK Synth

An Android synth for the Akai MPK mini Plus, built on the work of
[Paleotone](https://github.com/fantasmoslam/Paleotone): the same native engine (the FM-1 firmware's synth,
run on the tablet), the PALEO sound engine and Scape conductor, and the round-three tablet interface.
It installs as `org.fantasmoslam.mpksynth`, beside Paleotone rather than over it.

This repository started as Paleotone's Android branch with its full history, trimmed to what the app builds
from, tests with, or could use. Everything removed (the FM-1 firmware packaging and loader, the web
installer, editor and site, the firmware host test suite, the VST side project) is still in this repository's
history and in Paleotone.

## Layout

- `android/` — the app: Java interface and MIDI, native audio bridge, build script, tests, preview renderer.
  Start with [android/README.md](android/README.md).
- `firmware/src`, `firmware/hal` — the synth engine the app runs (compiled for Android through
  `web/emu/felucca_web.c` and `tests/hostsim.c`).
- `paleo/` — the PALEO engine, Faust voices, creature and instrument libraries, phrases, scores and tools.
- `assets/` — CC0 samples, fonts and icons; `tools/` — the generators `tools/build.py --gen-only` runs.
- `docs/` — Scape design notes and the original Paleotone README.

## Build and test

```sh
python3 -m pip install pillow==12.3.0 fonttools==4.61.1 numpy==2.3.5
sh android/test.sh                    # native engine, PALEO bridge and interface tests (no SDK needed)
sh android/preview/render.sh build/preview   # interface screenshots on a desktop JDK
python3 android/build.py              # the APK; needs the Android SDK/NDK listed in android/build.py
```

CI (`.github/workflows/android-native.yml`) builds and tests the APK on pushes to `main` and on pull
requests that touch `android/`. A permanent signing key goes in the `ANDROID_KEYSTORE_BASE64` secret.

## License

GPL-3.0-only, as Paleotone; see [LICENSE](LICENSE) and [LICENSING.md](LICENSING.md).
