# Paleotone Android — native alpha 0.4.0

Standalone synthesis for Dylan's Samsung SM-T510. Initial measured device configuration:
Android 15 / unofficial LineageOS 22.2-20260910, `armeabi-v7a,armeabi`, 48 kHz
stereo, FAST hardware output at 192 frames. MIDI and USB host are advertised;
the low-latency/pro-audio feature flags are absent. A hardware buffer spans 4 ms;
this is **not** a physical latency measurement or an accepted synth performance result.

The APK carries native libraries for both `armeabi-v7a` (the SM-T510's 32-bit userspace) and
`arm64-v8a` (64-bit tablets such as the Galaxy Tab S6 Lite SM-P610 on official LineageOS 23.2).
The 64-bit build has not yet been accepted on a device.

## Round-three interface (0.4.0, phases 1 to 3 and 5)

The signed-off round-three design (Focus by default, Performance switchable) is the whole
app; the old firmware panel and four-tab workspace are gone. `java/org/paleotone/synth/ui/`
holds the screens and controls as plain Java drawing through `Gfx`, so the same code runs on
the tablet (`PhosphorView`, Android Canvas) and on a desktop JDK for review screenshots:

```sh
sh android/preview/render.sh build/preview
```

- **Shell:** header, navigation rail and Performance dock, the fixed bottom bar (environment,
  music, Stop All, save, Quick mix), choice sheets (paged when long) and notices.
- **INSTRUMENT:** the eight notes to the conductor, Hold/Next, Biome, Music mood, Live balance.
- **Mixer → Parts:** per part voice (opens Sound design), level, mute, pan (drag sideways; tap
  for centre) and role; Output with Environment, Master and Tempo.
- **Mixer → Habitat layers:** eight layer levels with mute, and the environment drone. The engine
  has no layer mute, so mute sets the level to 0 and the interface remembers the old level.
- **Sound design:** part, voice (engine) and preset, and the voice's eight edit parameters with the
  firmware's own descriptors, ranges and wording (`Native.voice`). List parameters open a sheet;
  the rest are knobs. Blank parameter slots are shown empty.
- **Quick mix:** part levels, mutes and pans over any screen.
- **Library:** imported WAV sound files with waveform, Load, Preview, root note and Delete, and the
  playing source (imported sample or Paleotone sounds) for keyboard and MIDI. Preview plays the root
  note through the sample layer for up to four seconds.

- **Recording:** Record (bottom bar or Library → Recordings → Open recording) captures the final
  stereo mix after the limiter as 16-bit WAV at the output rate (48 kHz on the test tablets). The
  audio callback writes into a lock-free ring (`native/capture.h`, about 11 s); a writer thread
  drains it into a private part file, and Stop completes the WAV header and copies the take to
  Music/Paleotone (MediaStore; Android 9 uses the app's own Music folder). Only a completed copy is
  reported saved. A failed or interrupted take keeps its part file for Retry save (also after a
  restart) and blocks new takes until it is saved or discarded. Leaving the app or Stop All saves
  the active take.
- **Run timer:** 15, 30, 60, 120 minutes, Continuous, or Custom (5–240). The run clock counts while
  the environment or music plays; a new length applies to the next run (Start, or Start a new run). At the length, or
  on End run, the clock holds, the mix fades over five seconds (an active take records the fade),
  then the take saves and audio stops. Stop All or Stop now during the fade ends at once.
- **Run end:** the closing card during the fade, then Run complete with the take's name and length,
  or No recording, with a strip of the map below.
- **Valley map (phase 5):** a flat top-down valley generated from the run's valley seed
  (`ui/Valley.java`); no real geography is used. Seven terrain regions (forest, swamp, plains,
  volcanic, desert, ridge, mangrove) with the design's glyph textures, a river, a lake and a pond,
  sixteen stations along a route, two landmarks, herds and a predator, the travelled trail, the route
  ahead and your position. `ui/ValleyMap.java` draws it at any zoom: terrain and creatures scale,
  but every label is drawn at one fixed on-screen size (review amendment 1) and labels never overlap
  (lower-priority names are left out where there is no room).
- **Expedition:** status strip (valley, day and time, habitat, weather, station), Survey (opens the
  map), Danger and Majesty (the same notes as INSTRUMENT), the encounter card or the expedition log,
  and the eight actions. **Valley Survey** pans with one finger and zooms with a pinch; View tools has
  Zoom, Centre on me and Whole valley; Plot a path adds points with each tap and draws your path in
  amber until Clear path. **Encounter:** the eight stages (Signs to Coda) with the stage text, the
  creature once identified, Conductor response and a position map.
- **Journal:** base camp, each night camp and each identified encounter as a page in the leader's
  hand, with a plate for the creature. **Bestiary:** 24 species in pages of 12, recorded ones named,
  with Find species (group or name order) and Read first encounter. **Start:** choose the next run's
  leader (Dr. Wellington / Cinematic, Miss Veronica / Lo-fi, Jonesy / Dark ambient), valley seed and
  run length, then Begin expedition.

The expedition itself is a seeded stand-in (`ui/Expedition.java`) until the conductor's expedition
runs on the tablet: while the run clock counts it moves one station per fifteenth of the run (two
minutes on Continuous) and raises encounters from a fixed catalogue; the actions answer simply (Move on
goes to the next station, Observe and the others advance an encounter, Shelter and Defend end a
dangerous one). The leader's sound flavour and the actions reach the conductor once it is ported.

**The old DINOBOX Scape is switched off** (Dylan, 1 November 2026). Its habitat, drone, creature
calls and loop music came from the FM-1 firmware and do not belong in the rebuild. The firmware still
contains it; the tablet never starts it (`PT_OLD_SCAPE` in `native/android_audio.c` refuses Scape
commands, holds the habitat, drone, layer, evolving-music and transport controls, and clears a saved
session's Scape at launch). Environment, Drone, the habitat layers and Music show as waiting for the new
engine until the new conductor and habitat are ported. MIDI, the four synth parts, sound design and the
sample layer are unaffected. The run clock therefore stays still for now.

Knobs follow the review's zero rule: unipolar knobs sweep from 7 o'clock to 5 o'clock; only
engine-marked bipolar parameters point to 12 at zero, with a centre tick. Not built yet: the
notes are saved but not connected (their gauges show a dash or IDLE, phase 4).

Fonts (SIL OFL 1.1, licences in the APK's `assets/licenses`): Michroma, IBM Plex Mono,
Caveat and IM FELL English. `android/tests/UiTest.java` checks the knob zero rule, both
layouts on every screen at 1280 and 1333 units wide, dragging, layout switching, Stop All
over sheets and quick mix, the dock, sound design edits, layer mutes, the run timer and end, the
seeded valley (200 seeds), the stand-in expedition and journal, fixed-size map labels at two zooms,
pinch, pan and path plotting, and a new run taking the chosen leader and seed.
`android/tests/capture_test.c` checks the recording tap and the fade.

## Source baseline and scope

Based on integration PR #26, `0390a03f732c5eeec9f62cb2b139df8738396766`.
Firmware, browser and release routing source are unchanged. This is a native
Android platform adapter around `web/emu/felucca_web.c`, which already compiles
the real firmware engines, UI, SCAPE, effects, sequencer and flash-storage hooks
outside the FM-1. It does not use WebView, JavaScript or a network server.

Android's C AAudio API is used directly in this first build (API 28+), with
low-latency/exclusive requests, a shared fallback, and a query of actual stream
settings. Oboe remains a future option if measured driver behavior warrants its
quirk handling. No claim is made that exclusive or MMAP operation is obtained.
The stream selects its native rate. A polyphase windowed-sinc converter
(native/converter.h: 48 taps, 160 phases for 44.1→48 kHz) carries the 44.1 kHz
firmware engine to it: flat within 0.1 dB to 18 kHz, folded images below −65 dB,
0.54 ms of delay. The earlier linear converter lost 1.5 dB at 10 kHz and folded
images back into the audio band only 20 dB down there (android/tests/converter_test.c).

## Install on the tablet

Download the APK to the PC. In the same `platform-tools` PowerShell window:

```powershell
.\adb.exe -s 192.168.0.46:33862 install --no-streaming -r "$env:USERPROFILE\Downloads\Paleotone-Android-0.3.0-alpha.apk"
.\adb.exe -s 192.168.0.46:33862 shell am start -n org.paleotone.synth/.MainActivity
```

The wireless debugging address/port can change after reboot or toggling debugging.
Use the current main Wireless debugging page's connection address if needed.
Start with a low media volume and the tablet speaker or wired headphones.
Bluetooth is not a suitable route for assessing playable latency.

## Controls and storage

- Landscape panel: real 240x240 firmware display, 14 panel buttons, seven encoders,
  master slider and a multitouch F3–G5 keyboard. Drag encoders vertically or tap −/+.
- Firmware button holds retain their alternate behavior. SELECT, ENGINE and PRESET
  correspond to the physical SELECT, ALGORITHM and PRESETS encoders.
- **SAVE SESSION** briefly stops/closes the stream, captures the live portable
  project plus the 1 MiB simulated flash, writes an Android `AtomicFile`, and restarts.
- Going to another app stops audio and saves. A fresh process restores the project
  with the transport stopped. Unexpected power loss, a crash or force-stop can lose
  unsaved edits. This alpha does not promise periodic disk autosave.
- Firmware SAVE retains the firmware project menu and writes simulated flash in
  RAM; SAVE SESSION or leaving the app makes that flash durable on Android.
- Sessions are in app-private storage (`session.ptandroid`). Uninstalling removes them.
- MIDI selects Android MIDI output ports from attached controllers. Channel-message
  parsing handles fragments, running status and interspersed real-time bytes.
  External clock, SysEx and BLE discovery are not implemented. WAV import is available in LIBRARY.
- Audio reconnects automatically after stream errors while foreground and playing,
  with a short debounce and at most five retries. Stop, backgrounding and lost focus
  cancel retries. If recovery is exhausted, START provides an explicit retry.
- AUDIO offers Original (1x), Normal (2x / +6 dB, default), and Boost (4x / +12 dB).
  Normal restores the FM-1 codec reserve; a stereo-linked limiter bounds digital
  peaks at 0.98. Gain changes ramp and the limiter releases over 50 ms. Android media
  volume remains independent. Output level is retained in app preferences.
- AUDIO also offers 2/3/4/6 buffer bursts; changing it pauses audio and preserves
  whether playback was stopped. PANIC releases local/controller notes; an active sequencer
  can subsequently trigger more notes. PLAY controls transport.

## Tablet workspace and sample library (0.2.0)

In 0.2.1, these sliders use 36 dp handles, 8 dp tracks and 64 dp tall touch areas.

The top navigation keeps the existing **INSTRUMENT** panel and adds **MIXER**,
**SCAPE**, and **LIBRARY**. MIXER exposes each track's level, mute and pan, tempo,
and sequencer transport. SCAPE exposes habitat/drone levels, biome, environment
layers and evolving-music settings. These edit the same firmware state as the
original controls and are included in SAVE SESSION. New phrases explicitly asks
before replacing patterns; it requires no active song chain. Generation does not start transport.

LIBRARY includes CC0 Flute C4 and Jungle C3 starter files, prepared with 0.75 peak
headroom. Imported WAVs retain their recorded levels. **LOAD** activates a
sample; **PLAY ON KEYBOARD** opens the instrument keyboard. **USE INSTRUMENT**
returns keyboard/MIDI to the firmware sounds. The banner always identifies the
active mode. Each sample has an adjustable MIDI root note. Import uses the Android
file picker, copies to private storage, validates before saving, and deduplicates
identical files by SHA-256. DELETE removes only Paleotone's copy after confirmation.
No broad storage permission is requested. Starter files are installed once, so a
deleted starter is not recreated on every launch.

Supported: RIFF WAV PCM 8/16/24/32-bit or float32, mono/stereo, 8–192 kHz, up to
32 MiB and 4 million frames per file. Stereo is downmixed to mono; nonfinite float
values are zeroed. One decoded sample is shared by eight voices with linear pitch
conversion, a short attack/release and an end fade. Loading briefly pauses audio;
copying/decoding runs on a worker. No sample allocation or disk I/O occurs inside
the callback. Master and the accepted output limiter also apply to
sample audio. Since 0.4.0 there is no on-screen keyboard: the sample layer is played from
MIDI, and from Preview in Library. MIDI note-on/off and all-notes-off are supported in sample mode;
sustain pedal and other expression controls are not implemented for this layer.

**Imported samples are a separate playable layer, not new firmware track presets.**
They are not yet sequencer-triggered or assigned to Scape roles. Firmware sequencer
sounds continue independently. There is no looping, slicing, recording, multisample
mapping, or sample-library export in this release. Voice stealing can produce a
transient under heavy retriggering; long ambient files are memory-bounded rather
than streamed. Selection, root note and mode are retained separately from the
unchanged session format. Samples survive app updates, but uninstalling removes
the private library. Copies of original imports should be kept elsewhere.

Import temporarily leaves lock-task mode for the system picker, then restores
kiosk when returning. It retains device-owner/Home provisioning. Cancel and failed
imports also return to kiosk. On this dedicated personal instrument the picker is
an explicit maintenance route; it is not a secure public kiosk import workflow.
Physical verification of picker return/relatching is still required.

## Scape performance workspace (0.3.0)

Scape now has a fixed transport/status strip above three panels: **Environment**,
**Music**, and **Detail**. Start/Pause Environment, Play/Stop Music, and Stop All
remain visible while scrolling. Status badges distinguish actual environment,
sequence/music and drone state, including audio off. Biomes, moods, call routing
and track roles use named choice dialogs instead of numeric sliders. Large sliders
remain for continuous values. Readouts follow engine state; while dragging, stale
snapshots cannot reposition the handle. Final slider values are resubmitted on
release and await acknowledgement before accepting an older snapshot (bounded to
750 ms so future changes from the original controls still appear).

- Start Environment restores its last positive master level and preserves the
  layer balance, including intentional zero levels. Pause Environment sets its
  master to zero, including its procedural drone. Music is independent.
- Environment Drone has a dedicated on/off action and level. Its last positive
  level is remembered. It is distinct from a sustained instrument in a musical
  track. Normal environment/drone/musical stops allow effects/release tails.
- Play Music enables the director and starts the shared four-track sequencer;
  it does not silently replace patterns. Stop Music disables evolution **and**
  stops that sequencer, releasing its notes. The old Android evolution flag's
  off action also stops music. Mixer and Scape share the same transport.
- Hold Current Harmony holds evolution while notes continue; it is not Stop.
- Generate New Phrases replaces the four track patterns only after confirmation,
  using the chosen mood/roles, without starting transport. An active song chain
  disables generation and music-start controls. Stop Music cancels that chain.
- Stop All closes Android audio immediately, clears Scape/master/drone/music
  states, releases sample voices and discards old effect, output/filter, DMA,
  ring and resampling history. Starting Environment or Music reopens audio.
  The sample remains loaded. Existing separate ambience/tape settings and preset
  parameters are retained; fresh hiss/ambience configured there can return when
  audio resumes. Stop All itself is silent because the stream is closed.

Last positive environment/drone levels are retained in Android preferences. The
firmware session format, accepted output gain and device-owner provisioning stay
unchanged. Only the Android adapter changes; FM-1/browser firmware sources do not.
This redesign still needs physical tablet acceptance for layout, fader response,
state transitions, tails and Stop All/restart.

## Thread and timing boundaries

The audio callback is the sole live engine owner. JNI uses atomic key/button states,
atomic encoder deltas, coalesced control values and a bounded MIDI queue; MIDI producers serialize outside
the audio callback. Queue overflow requests a panic. There is no allocation, disk
I/O, Java callback or blocking mutex acquisition on the audio thread.

**Firmware screen drawing is off the audio thread.** The engine still runs the
firmware's whole UI frame (ui_input, and every state change ui_draw makes) but its
canvases open 0 × 0, so nothing is rasterized there (GFX_HOOK_BEGIN in engine.c).
When the UI has taken the previous picture, the callback captures the firmware's own
second-screen snapshot (remote_state.c, about 30 µs on a desktop CPU) into a lock-free
triple buffer. The UI thread draws it with a second, drawing-only copy of the
firmware (native/display.c, linked with every symbol local but its entry points),
which also formats the mixer and Scape readouts. On a desktop CPU this removed
screen drawing's 20% of render time and its spikes of up to 450 µs per callback.
android/tests/display_test.c drives 120 s of random panel, MIDI and Scape input and
requires the audio and every snapshot to be bit-identical with and without drawing
in the engine. The display copy matches the firmware's own drawing pixel for pixel
in about 75% of frames; the rest differ in about 1–3% of their pixels, because a
snapshot redraws its page whole: value-change digit rolls show the new value at once,
and the picture can be a frame ahead. The visible instrument panel refreshes at about
30 Hz; mixer readouts update at about 5 Hz and Scape at about 10 Hz. Hidden pages take
no snapshots.

**Diagnostics.** The panel shows the stream settings, AAudio underruns (xruns),
late callbacks (a callback that took longer than the audio it produced), render
time and the output latency the stream reports from its timestamps (HAL and DSP path,
not acoustic). Every 10 s while playing, and on each stop, a full line goes to logcat:

    adb logcat -d -s PaleotoneAudio

It names the tablet model and 32/64-bit build, then rate, burst, buffer and capacity,
sharing mode, seconds running, callbacks, underruns, late callbacks, the longest gap
between callbacks, average and maximum render time, current and maximum output
latency, screen snapshots and their maximum capture time. Counters restart with
each audio start.

Optional kiosk mode is available in 0.1.2; installing the app alone leaves it off.
Hold the PALEOTONE title for three seconds or use HELP → Kiosk / maintenance.
Device-owner provisioning is performed explicitly through ADB; then the menu can
set Home and enter lock-task mode. Maintenance clears the lock and Home policy.
See KIOSK.md for setup, rollback and the permission-gated ADB emergency exit.
No all-core pinning, root, governor changes, thermal overrides or package removal
is performed. Existing accepted audio gain is unchanged.

## Build

Linux x86-64, JDK 17, Python 3 and Android command-line tools:

```sh
sdkmanager 'platforms;android-35' 'build-tools;35.0.0' 'ndk;27.2.12479018'
export ANDROID_HOME=/path/to/android-sdk
python3 -m pip install pillow==12.3.0 fonttools==4.61.1 numpy==2.3.5
python3 android/build.py
sh android/test.sh
```

The APK is `build/android/Paleotone-Android-0.3.0-alpha.apk`.
This dependency-light build calls javac, D8, aapt2, the NDK compiler, zipalign and
apksigner directly. It requires no Gradle/Maven artifacts or proprietary FM-1 SDK.
Generated tables/fonts/samples use the repository's existing generator.

The development signing key is generated in `build/android-signing/debug.keystore`
and must be retained for updates to the installed APK. It is a **development** key,
with the conventional password `android` and alias `androiddebugkey`, not a production
release key. A different build environment generates a different key: retain it
with `ANDROID_KEYSTORE` or uninstall the old app before installing a differently
signed build (uninstalling deletes its private session).

CI signs with one permanent key stored in the repository secret
`ANDROID_KEYSTORE_BASE64` (the keystore file, base64-encoded), so every CI build
updates over the last. Local builds use the same key when that keystore is placed at
`build/android-signing/debug.keystore`. Without the secret, CI falls back to a
throwaway key and warns.

## Validation and remaining device checks

Host tests exercise variable-sized output callbacks, 44.1→48 kHz conversion, finite
audio, note on/off, MIDI channel messages, sequencer controls, display snapshots and
the drawing-free engine's equivalence, converter response,
save sizing and restoration of the exact portable project in a separate process.
Output tests cover gain, finite/stereo-linked limiting and recovery; Java policy tests
cover route debounce, bounded retry backoff and cancellation.
APK validation checks signing, launch activity, API levels, 32- and 64-bit ARM ABIs and
native dependencies. The original 0.1.0 APK passed user-reported multitouch playing, display/sequencer
tracking and save/restore. The 0.1.1 volume/headphone recovery changes also passed user testing, with a
brief jitter during unplug transitions. The user reports 0.1.2 works as expected;
specific emergency-exit and boot checks are not individually documented. The new
0.2.0 workspace/library requires physical-device acceptance. See DEVICE-TESTS.md for exact acceptance boundaries.

Device acceptance, in order:

1. Launch, play a note, release it; check multitouch and all panel controls.
2. Confirm sound and readable pixels; collect the displayed actual stream settings.
3. Run a sequencer pattern while moving macros; listen, then read underruns and
   late callbacks with `adb logcat -d -s PaleotoneAudio`.
4. Try a larger buffer if needed; compare sustained operation and heat.
5. SAVE SESSION, exit, reopen; verify project/preset retention and stopped transport.
6. Test audio-focus interruption, headphones/USB routing and controller detach.
7. Measure physical latency with suitable loopback equipment if required.
8. Test new library imports, sample mode, root note, reload after reopening and kiosk picker return.
9. Verify kiosk maintenance and emergency exit; do not reprovision an existing owner for updates.

## Licensing

GPL-3.0-only, retaining Felucca/GHOULBOX authorship and bundled component licenses.
The APK includes the repository license notices and sample attribution in assets.
The corresponding source is this repository at the exact Android implementation
commit together with its documented baseline; no binary-only engine is used.
