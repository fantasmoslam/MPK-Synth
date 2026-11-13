# MPK Synth for Android — 0.5.0

## How it fits together

- **Audio** (`native/synth.c`): one AAudio low-latency stream. The synth has one owner, the
  audio callback while it runs; everything else reaches it through a single queue of MIDI and
  commands. Opening and closing follow the app coming to the front and leaving, and nothing else
  turns the audio off. ALL NOTES OFF only ends notes.
- **Models** (`native/mpk/`): each model is a table of controls (range, default, panel section,
  how it is drawn), knob banks, presets, and the functions that play it. `engine.c` runs four
  parts and routes MIDI channels 1-4 to them.
- **The app's side** (`java/.../ui/Rig.java`): the app owns every control's value and sends each
  change to the synth, so the screen never reads from the audio thread. The parts are saved as
  text when the app leaves.
- **The MPK** (`ui/MpkRouter.java`, `ui/Mpk.java`): keys go to the keys part, pads (channel 10,
  notes 36-51) to the pads part, pad pressure becomes channel pressure, the joystick bends.
  Knobs CC70-77 drive the 8 controls of the lit bank, with pickup. Play, Stop and Record (CC118,
  117, 119) are kept for the looper.
- **The screen** (`ui/Home.java`, `ui/Learn.java`): drawn through `Gfx` in units of 1/800 of the
  screen height, on the tablet by `SynthView` and on a desktop by `preview/Preview.java`.
- **Lessons** (`ui/Tutorial.java`): lessons of steps; a step waits for an event (a key, a pad,
  a knob, a part, a preset) and moves on by itself. Steps about the SOUND screen open it and
  point at the place to touch.

## Models are described once

The JNI layer serves the native tables as text (`models`, `params`, `banks`, `presets`,
`presetValues`); `tools/tables.c` prints the same text for the preview and the Java tests, so
the screen and tests always use the real tables.

## Install

```sh
adb install -r MPK-Synth-0.5.0-alpha.apk
```

The APK is signed with the project's permanent key in CI (`ANDROID_KEYSTORE_BASE64`), so it
updates over earlier builds. Device checks are in [DEVICE-TESTS.md](DEVICE-TESTS.md).
