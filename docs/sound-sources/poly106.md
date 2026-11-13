# Poly 106: sources

Poly 106 (`android/native/mpk/poly106.c`) models the Roland Juno-106. This page lists where
its presets and its control behaviour come from, how each stored value maps onto the
model, and what is still approximate.

## Sources

| # | Source | What it gives |
|---|--------|---------------|
| S1 | Juno-106 factory patch dump (groups A and B, 128 patches, 18 bytes each), as carried byte for byte in [shorepine/amy `amy/juno.py`](https://github.com/shorepine/amy/blob/main/amy/juno.py) (MIT) and in [Ultramaster KR-106 `Source/KR106_Presets_JUCE.h`](https://github.com/kayrockscreenprinting/ultramaster_kr106) (GPL-3.0, "decoded from factory SysEx data") | The 128 presets and their names |
| S2 | Juno-106 owner's manual, MIDI implementation, as reproduced in [ST-Log issue 33 (June 1989), "The ST and the Roland Juno-106"](https://www.atarimagazines.com/st-log/issue33/64_1_THE_ST_AND_THE_ROLAND_JUNO_106_SYNTHESIZER.php) | The patch dump's byte order and switch bits |
| S3 | Juno-106 voice CPU (IC29, uPD7811G) ROM disassembly, [ErroneousBosh/j106roms `ic29.txt`](https://github.com/ErroneousBosh/j106roms) | The envelope, LFO, LFO delay and DCO LFO tables, and the arithmetic that turns sliders into VCF, PWM and pitch control voltages |
| S4 | [Roland support: Juno-106 technical specifications](https://support.roland.com/hc/en-us/articles/201966419-Juno-106-Technical-Specifications) | LFO rate 0.1-30 Hz, LFO delay 0-3 s, attack 1.5 ms-3 s, decay and release 1.5 ms-12 s |
| S5 | Ultramaster KR-106 hardware measurements: [`Source/DSP/KR106LFO.h`](https://github.com/kayrockscreenprinting/ultramaster_kr106/blob/main/Source/DSP/KR106LFO.h) (LFO rates of a real unit), [`Source/DSP/KR106VcfFreqJ106.h`](https://github.com/kayrockscreenprinting/ultramaster_kr106/blob/main/Source/DSP/KR106VcfFreqJ106.h) and `J106DACHzTable.h` (VCF frequency at every DAC code, and the service manual's 248 Hz / 992 Hz calibration points), [`tools/adsr-calibration/README.md`](https://github.com/kayrockscreenprinting/ultramaster_kr106/blob/main/tools/adsr-calibration/README.md) (envelope times measured from factory patch recordings), `Source/DSP/KR106_DSP_SetParam.h` (VCA level range from a hardware recording) | The CPU's loop period, the VCF's Hz per control-voltage unit, cross-checks |

S1: the two projects ship byte-identical data for all 128 patches (checked). Both trace to
the same circulated factory dump, so that is one source, not two. Roland's own printed patch
sheet could not be retrieved to compare (Roland's owner's manual PDF is an image scan).
S3's author calls the disassembly "partial, inaccurate, incomplete, unofficial": the hex
tables are straight ROM dumps and are used as they are; the annotations were read as
guidance and checked against the code.

## Presets: dump bytes to controls

Each preset is written in `poly106.c` as its 18 dump bytes, unchanged, through the `J106()`
macro. The Juno-106 sends every slider as a 7-bit value 0-127, and Poly 106's sliders use the
same 0-127 values, so the 16 slider bytes go straight onto the controls; the model then
interprets them the way the Juno's CPU does (next section).

| Byte | Juno-106 | Poly 106 control |
|------|----------|------------------|
| 0 | LFO rate | LFO RATE |
| 1 | LFO delay | LFO DELAY |
| 2 | DCO LFO | DCO LFO |
| 3 | DCO PWM | DCO PWM (0-105: the hardware slider stops at about 105, R5/R6 per S3; the factory data never exceeds 105) |
| 4 | Noise | DCO NOISE |
| 5 | VCF freq | VCF FREQ |
| 6 | VCF res | VCF RES |
| 7 | VCF env | VCF ENV |
| 8 | VCF LFO | VCF LFO |
| 9 | VCF kybd | VCF KYBD |
| 10 | VCA level | VCA LEVEL |
| 11-14 | A, D, S, R | ENV A, D, S, R |
| 15 | Sub osc | DCO SUB |
| 16 bits 0-2 | Range 16' / 8' / 4' | DCO RANGE 0 / 1 / 2 |
| 16 bit 3, 4 | Pulse, saw on | DCO PULSE, SAW |
| 16 bits 5, 6 | Chorus off (1); chorus I (1) or II (0) | CHORUS OFF / I / II |
| 17 bit 0 | PWM manual (1) or LFO (0) | PWM BY |
| 17 bit 1 | VCF env polarity - (1) | VCF ENV +/- |
| 17 bit 2 | VCA gate (1) or env (0) | VCA MODE |
| 17 bits 3-4 | HPF: 11 = position 0 ... 00 = position 3 | HPF FREQ |

The bit layout agrees across S2, the switch handling in S3 ($0184: the HPF bits are inverted
before use) and both decoders in S1. Every preset also sets VELOCITY to 0, because the
Juno-106 has no velocity. Names are the factory names; the asterisks marking performance notes
("A26 Celeste* (1 oct. up)") are dropped because the app uses `*` to mark an edited sound.
Patches the factory list marks "(unison)" play polyphonically: Poly 106 has no unison mode.

## Controls: how the model follows the Juno-106

On the Juno-106 the voice CPU computes the envelopes, the LFO, the LFO delay and the VCF and
PWM control voltages in a main loop, from tables in its ROM (S3). Poly 106 runs the same loop
at the same rate and interpolates between its steps.

- **Loop rate.** The loop is not clocked; it takes about 4.2335 ms per pass, measured on a
  real unit through its LFO rates (S5). Poly 106 uses 236.2 passes a second.
- **Envelope.** Attack adds `rom_attack[A]` ($0B60) per pass to a 14-bit value until it reaches
  0x3FFF (a straight line: 1 pass at A=0, 3.3 s at A=127). Decay multiplies the distance above
  sustain by `rom_decay[D]` / 65536 ($0D60) per pass; release does the same toward zero with
  `rom_decay[R]`. The multiply is the ROM's own ($083D), which drops the low-by-low product.
  Sustain is the slider value * 128. *Check:* for A14 Flutes this gives a 10-90 % attack of
  159 ms and a decay time constant of 1.42 s; KR-106's recordings of a real Juno-106 measured
  150 ms and 1.31 s (S5).
- **LFO.** A triangle from `rom_lfo_speed[RATE]` ($0C60): 0.04 Hz to 29.5 Hz (Roland: 0.1-30 Hz,
  S4). *Check:* table steps 380, 698 and 1800 give 2.68, 4.92 and 11.81 Hz here; KR-106 measured
  2.686, 4.840 and 11.666 Hz on hardware (S5), within 2 %.
- **LFO delay.** A hold-off counting `rom_attack[DELAY]` per pass up to 0x4000, then a fade-in
  at `rom_lfo_fade[DELAY / 16]` ($0B30). It restarts when a key goes down after all were up.
- **DCO LFO.** Depth from `rom_lfo_depth[DCO LFO]` ($0A80), up to about +-4 semitones ($032D).
- **VCF control voltage** ($04D5): FREQ * 128, plus the LFO (VCF LFO * 2 scaled, up to about
  +-3.6 octaves), plus or minus the envelope times VCF ENV * 2 / 256, plus the keyboard: 96
  units a semitone from middle C at full KYBD. The service manual calibrates FREQ 49 at middle C
  to 248 Hz and the same two octaves up to 992 Hz (S5), which puts 1142 units in an octave:
  full KYBD tracks the keys 1:1, as specified. Hz = 248 * 2^((cv - 6272) / 1142). KR-106's
  per-code measurement of a real voice agrees within 3 % up to about 9 kHz (FREQ 96).
- **PWM** ($0761): the PWM slider * 2 / 256 times either the LFO swinging around the middle or,
  in manual, full scale.
- **VCA level.** About +-10 dB around the middle of the slider (S5: -9.8 / +9.9 dB measured).
  Poly 106 puts 0 dB at the top and -20 dB at the bottom.

## Still approximate

These parts are not yet backed by data, or only partly:

- **Pulse width per control voltage.** Poly 106 maps the PWM control voltage linearly onto a
  duty cycle from 50 % down to 5 % at full scale (13 % at the slider's top, 105), following
  KR-106's 50-95 % range; the Juno's actual duty cycle at each PWM value has not been measured.
- **Mix levels** of saw, pulse, sub and noise relative to each other, and the overall level
  between patches.
- **The VCF itself.** A generic 4-pole ladder stands in for the IR3109; its resonance curve,
  self-oscillation point and the knee above about 9 kHz (where the real filter tops out near
  50 kHz rather than climbing on) are not matched to measurements.
- **HPF** corners (240 Hz and 720 Hz for positions 2 and 3, a bass shelf at 0) and **chorus**
  (0.513 Hz and 0.863 Hz, 1.65-5.35 ms delay) were kept from the earlier version; they match
  commonly quoted figures but were not re-checked against the service manual in this pass.
- **Bender.** The Juno's bender has its own DCO, VCF and LFO sensitivity sliders, not stored in
  patches; Poly 106's BEND control only bends pitch.
- **Integer quirks.** Poly 106 follows the ROM's integer envelope and LFO exactly but computes
  the VCF and PWM voltages in floating point, so it does not reproduce wrap-around corner cases.
