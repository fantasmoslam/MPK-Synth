# MPK Synth

An Android synthesizer played from the Akai MPK mini Plus. Four parts play at once: the keys
play one, the pads another, and the eight knobs shape the sound on screen. Each part runs a
synth model with its own panel of controls, and a built-in course teaches everything from the
first key press to shaping a drum kit.

It installs as `org.fantasmoslam.mpksynth`.

## What is in 0.5.0

- **Poly 106**, a six-voice polysynth modelled on the Roland Juno-106: DCO with saw, pulse,
  PWM, sub and noise, high-pass, a resonant 4-pole filter, envelope, LFO and the two-mode chorus.
  Its 128 presets are the Juno-106's factory patches (A11-B88), and its envelopes, LFO and filter
  scaling follow the Juno-106's own firmware.
- **Kit**, the Roland TR-808's 16 instruments on the 16 pads (banks A and B), tuned and timed
  from Roland's TR-808 service notes, each pad with its own tune, decay, tone and level, and
  velocity playing the 808's accent. 3 kits.
- **The MPK mini Plus**, as it comes: keys, pads with pressure, joystick bend and the 8 knobs,
  which follow the bank of controls lit on screen with pickup, so a sound never jumps.
- **LEARN**, 11 lessons with moving pictures that wait for you to do each thing on the MPK or the
  screen, and a list of words to know.

Coming next, one at a time: a looper that works like Loopy Pro, then the Voyage (Moog Voyager),
Raw 20 (Korg MS-20), Patch 26 (ARP 2600) and Big 80 (Yamaha CS-80) models. The names of the
instruments these models are built to resemble belong to their owners; MPK Synth is not
affiliated with them.

## Where the sounds come from

Every model's presets and control behaviour must come from data about the real instrument
(factory patches, manuals, firmware, measurements), never from taste, and every number is
listed with its source. This applies to all future models too (Voyage, Raw 20, Patch 26,
Big 80). See [docs/sound-sources](docs/sound-sources/README.md).

## Layout

- `android/java` — the app: the screen (`ui/`, plain Java drawing through `Gfx`), MIDI, audio focus.
- `android/native` — the synth: `mpk/` holds the models and engine, `synth.c` the AAudio stream and
  JNI, `looper.h` the looper core for the next stage.
- `android/tests`, `android/test.sh` — tests that run on a desktop compiler and JDK.
- `android/preview` — screenshots of the real screen code on a desktop JDK.

## Build and test

```sh
sh android/test.sh                            # synth, looper and interface tests (no SDK needed)
sh android/preview/render.sh build/preview    # screenshots
python3 android/build.py                      # the APK; needs the Android SDK/NDK listed there
```

CI builds the signed APK on every pull request (`.github/workflows/android-native.yml`).

GPL-3.0-only; see [LICENSING.md](LICENSING.md).
