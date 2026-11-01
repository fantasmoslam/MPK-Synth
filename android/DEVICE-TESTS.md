# SM-T510 hardware record — October 28, 2026 (America/Phoenix)

Tester: Dylan Spaniel / Fantasmoslam. These are user-reported observations, not
instrumented latency, power or complete device-acceptance measurements.

## Android 0.1.0-alpha, installed development-signed APK

Device: Samsung SM-T510, Android 15, unofficial LineageOS 22.2-20260910.
Native source: `ac60499e651ac93889bdbe1e67e960e1297a73af`.

| Behavior | Observed result |
| --- | --- |
| App launches / native sound | Keys play and sound good |
| Multitouch | Five fingers dragged rapidly across keys; no noticed stutter |
| Key highlighting | Working |
| Firmware screen | Present and working |
| Sequencer tracking | Reported spot on |
| Save session, close, reopen | Changed sound returns |
| Speakers and wired headphones at maximum Android/app volume | Output subjectively too quiet |
| Plugging in headphone cable | Playback stops; manual start restores it |

The sustained 10–15 minute soak was suggested but no completed soak result was
reported. External MIDI controllers, physical latency and numeric underrun data
have not been reported as tested. Do not convert subjective clean playing into
a claim of measured zero underruns or certified low latency.

## 0.1.1-alpha changes awaiting device acceptance

The native adapter retains the portable renderer's normalization of the FM-1's
24-bit codec output. That firmware path deliberately leaves 6 dB of reserve
(`OUT_SHIFT=7`), so the Android output now defaults to 2x gain (+6.02 dB). AUDIO
offers original 1x or boost 4x as alternatives. Gain transitions are smoothed;
an instantaneous-attack stereo-linked limiter with 50 ms recovery bounds digital
output at 0.98. This is a source-level correction and bounded-output test, not
a measurement of the tablet's analog loudness.

AAudio errors are consumed by the foreground Activity's polling loop. The Activity
closes the old stream on its lifecycle thread, waits a short route-settling interval,
and retries up to five opens with backoff. An output-device event can rearm a pending recovery;
after five failed opens the user can tap START to retry. Manual Stop, backgrounding and lost audio focus
cancel retries. Synth/project state is not rebooted. Save and buffer changes
preserve explicit paused intent.

Verify the signed update preserves the saved project, is louder on the same patch
at the same master/media settings, remains clean with dense playing, and resumes
after both plugging and unplugging wired headphones without a manual start.
Also verify pressing STOP during recovery prevents later automatic playback.

## 0.1.1-alpha hardware follow-up

Dylan reports a slight jitter on unplugging headphones, followed by immediate
automatic recovery. Speaker volume is reasonable for the tablet; headphones are
great and he would not want them louder. Output gain is accepted; do not increase
it further by default. The brief route-transition jitter remains a polish item.

## 0.1.2-alpha kiosk boundary

Adds optional Home/device-owner lock task and maintenance/ADB escape paths. Dylan
reports that everything works as it should. Individual provisioning, emergency
broadcast and reboot results were not separately recorded. See KIOSK.md. Audio DSP and output gain are
unchanged from the accepted 0.1.1 build.


## 0.2.0-alpha workspace and samples — pending hardware acceptance

Host checks pass for 47 parameter descriptors, range clamping, track isolation,
tempo/biome changes, project persistence, WAV PCM/float conversion, invalid-file
rejection and actual packaged starter decoding. Native sample tests exercise
8-voice bounds, note release, panic, pitch/rate conversion and end-of-file behavior.
The APK compiles and verifies; no Android emulator or physical tablet was available
for this release's UI/picker checks. Do not describe host tests as device acceptance.

On the tablet:

1. Install with `adb install -r`; confirm the prior project and kiosk provisioning remain.
2. Check all four tabs, legibility, original keys/encoders and title/⋮ maintenance escape.
3. In MIXER, change one track at a time; confirm level/mute/pan and sequence Play/Stop.
4. In SCAPE, test biome/layers and evolution. Confirm New phrases cancels without edits.
5. In LIBRARY, load Flute C4 and Jungle C3, play rapidly with several fingers, change
   root note and switch back to instrument. Listen for glitches and inspect xruns.
6. Import a known valid WAV via the file picker; cancel once, then try an invalid file.
   Confirm kiosk resumes and an earlier loaded sample remains usable after failure.
7. Delete the active sample; confirm firmware mode returns. Reopen/reboot and check
   selected sample/root/mode, library contents and session retention.
8. Repeat wired-headphone connect/disconnect while playing a sample, then run a
   sustained session to assess CPU, underruns and temperature. External MIDI still
   requires hardware verification, including note release on controller detach.


## 0.2.0-alpha hardware follow-up

Dylan confirms successful install via `adb install --no-streaming -r` after the
streaming installer stalled. The workspace launches and starter playback works.
Canceling the WAV picker returns to Paleotone with kiosk still active. Jungle
sample playback and switching back to the firmware instrument work. The mixer
sequence and per-track level/mute/pan checks work as expected. Slider handles are
reported too small and difficult to press. Successful import of an external WAV,
invalid import, delete/persistence, Scape confirmation and sustained-load/MIDI
checks are still not individually recorded.

## 0.2.1-alpha slider usability fix

Mixer, Scape and sample-root sliders now use a 36 dp visible handle, an 8 dp track
and a 64 dp tall touch area, with amber feedback while pressed. The native SeekBar
continues to handle track tapping, dragging and scroll gestures. Explicit labels
are supplied for accessibility. Audio and session formats are unchanged. The APK
build/signature are checked; actual touch comfort needs tablet verification.


## 0.2.1-alpha follow-up and Scape issues

Dylan accepts the larger slider handles ("very nice"). Scape initiation was unclear;
the master, drone and evolution controls did not explain independent playback.
Dylan reports drone volume sticking after adjustment, and evolution off leaving
the sequence running. The latter is confirmed in the old adapter: the flag only
controlled evolution. The exact cause of the perceived drone sticking was not
reproduced on physical hardware. Do not claim that testing identified a universal
DSP defect; the redesign adds explicit drone off, stable fader acknowledgement,
clear state/scope and a stream-closing Stop All.

## 0.3.0-alpha Scape redesign — pending hardware acceptance

New fixed transport/status and Environment/Music/Detail panels. Music Stop now
stops transport. Generate requires confirmation, preserves playback state and is
blocked during song chains. Start preserves existing patterns and layer mutes.
Stop All closes audio and clears voice/effect/tape/filter/DMA/ring/cache history.

Automated adapter tests exercise drone zero/off/restoration, environment mute and
level restoration, real transport start/stop including old evolution-off, pattern
preservation on Play, explicit generation without auto-start, chain guard and Stop
All cleanup. A signal-level silence check excludes intentional independent
ambience/tape hiss generators. Tests cover engine state, not Android screen layout.

Tablet checks:
1. Environment starts/pauses without starting the sequence. Biome choices load the
   named layer balances; dragging and releasing master/drone does not jump backward.
2. Drone off stops its new tone; effects may decay. The Music transport is separate.
3. Music Play starts the sequence; Music Stop stops it, with Environment left alone.
4. Generate cancellation preserves patterns; confirm only after saving if desired.
5. Stop All is immediately silent. Start again and check no old held notes/tails
   return. Check the loaded sample and original keys still play after restarting.
6. Confirm all three panels, named selectors, large sliders, mixer, kiosk exit,
   session restore and headphone recovery on the SM-T510.

## 0.4.0-alpha round-three interface — pending hardware acceptance

The old firmware panel, its on-screen keyboard and the four-tab workspace are removed.
Check on the SM-T510 (and the Tab S6 Lite when available), in Focus and Performance:

1. Header, rail or dock and bottom bar fit; Stop All works from every screen, over a choice
   sheet and over Quick mix. Holding the PALEOTONE title for three seconds opens maintenance.
2. INSTRUMENT: notes knobs drag with one finger each; Biome and Music mood change the sound;
   Hold and Next behave as before.
3. Mixer → Parts: level, mute, pan (drag sideways, tap for centre) and role act on the right
   part only; Tempo drags; Environment and Master match Live balance.
4. Mixer → Habitat layers: each level moves its layer; Mute silences it and restores the old
   level; Drone toggles and its level moves.
5. Sound design: change part, voice and preset; turn each knob; open a list parameter and pick a
   value; confirm the sound changes and the displayed values match what you hear. Save session,
   restart, and confirm the edits are kept.
6. Library: Preview each starter, import a WAV, change its root note, Load it, switch the playing
   source and play it from a MIDI controller; Delete it.
7. Recording: Record, play for a minute, Stop recording; confirm SAVING then TAKE SAVED, and that the
   file in Music/Paleotone plays on the PC at the right speed and pitch. Record again and press Stop All.
   Record and leave the app; reopen and confirm the take is saved.
8. Run timer: choose Custom 5 min on Start, Start a new run, start the environment with a take recording;
   at 5:00 confirm the five-second fade, Run complete, the take saved and including the fade.
9. Expedition: Move on walks the route on the Survey; an encounter card appears; View encounter shows
   its stages and tapping an earlier stage shows its text. Danger and Majesty match INSTRUMENT.
10. Valley Survey: pan with one finger, pinch to zoom, and confirm the labels stay the same size at
    every zoom. View tools: Zoom +/-, Centre on me, Whole valley. Plot a path with a few taps; the
    footer reads YOUR PATH / OVERRIDE; Clear path returns to ROUTE AHEAD.
11. Journal and Bestiary: the encounter's page and plate appear; Open bestiary selects it; Read
    first encounter returns to its page. Start: pick Jonesy and a new seed, Begin expedition, and
    confirm a new valley, base camp and the header naming the new run.
