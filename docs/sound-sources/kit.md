# Kit (Roland TR-808): sources

The Kit (`android/native/mpk/kit.c`) models the Roland TR-808. This page lists where its
voices, controls and kits come from, and what is still approximate.

## Sources

| # | Source | What it gives |
|---|--------|---------------|
| S1 | Roland, *TR-808 Service Notes* (June 15, 1981), page 14, table "Amplitude / Frequency / Decay time" ([scan on the Internet Archive](https://archive.org/details/synthmanual-roland-tr-808-service-notes), read from the page image; Roland marks the values "typical and variable") | Every instrument's frequency at its tuning knob's low, mid and high settings; decay time at short, mid and long decay; output amplitude at normal and at accent |
| S2 | K. J. Werner, J. S. Abel, J. O. Smith, "The TR-808 Cymbal: a Physically-Informed, Circuit-Bendable, Digital Model", ICMC-SMC 2014 | The six square oscillators (205.3, 304.4, 369.6, 522.7 Hz, and trimmed to about 540 and 800 Hz), 47.98 % duty cycle, cymbal band-passes around 3440 Hz and 7100 Hz, the tone control acting mainly on the upper band, the 1 ms trigger |
| S3 | K. J. Werner, J. S. Abel, J. O. Smith, "A Physically-Informed, Circuit-Bendable, Digital Model of the Roland TR-808 Bass Drum Circuit", DAFx-14 | The bass drum's bridged-T resonator (56 Hz in Roland's chart), its pitch jumping more than an octave for about the first 6 ms, the 1 ms trigger and the tone control as a low-pass |

## The chart (S1), as used

| Pad | Instrument | Frequency, Hz (low / mid / high) | Decay, ms (short / mid / long) | Vpp normal / accent |
|-----|------------|----------------------------------|--------------------------------|---------------------|
| 1 | BD bass drum | 56 | 50 / 300 / 800 | 3.5 / 10 |
| 2 | RS rim shot | 1667 and 455 | 10 | 3 / 10 |
| 3 | SD snare | 476 and 238 | 60 | 3 / 10 |
| 4 | CP hand clap | - | 100 | 2 / 6 (printed 6 / 2; read as swapped, since every other accent is louder) |
| 5 | CL claves | 2500 | 25 | 2.5 / 8 |
| 6 | LT low tom | 80 / 90 / 100 | 200 | 3.5 / 12 |
| 7 | CH closed hat | - | 50 | 3 / 6 |
| 8 | MT mid tom | 120 / 135 / 160 | 130 | 3 / 11 |
| 9 | MA maracas | - | 25 / - / 35 | 3 / 5 |
| 10 | HT hi tom | 165 / 185 / 220 | 100 | 3.5 / 12 |
| 11 | OH open hat | - | 90 / 450 / 600 | 3.5 / 7 |
| 12 | LC low conga | 165 / 185 / 220 | 180 | 3.5 / 12 |
| 13 | MC mid conga | 250 / 280 / 310 | 100 | 3 / 10 |
| 14 | CY cymbal | - | 350 / 800 / 1200 | 3.5 / 7 |
| 15 | HC hi conga | 370 / 400 / 455 | 80 | 3.5 / 12 |
| 16 | CB cowbell | 800 and 540 | 50 | 3.5 / 12 |

On the 808 the toms and congas share three channels behind a switch; here each has its own
pad. Pads follow the General MIDI drum map where an 808 instrument has a GM slot.

## Controls

- **TUNE** follows the chart's low / mid / high frequencies where the 808 has a tuning knob
  (toms and congas: 0 = low, 64 = mid, 127 = high). On the other voices it is an extra the
  808 does not have: +-1 octave around the chart's frequency, centred at 64.
- **DECAY** follows the chart's short / mid / long times where the 808 has a decay knob (bass
  drum, cymbal, open hat; maracas' short and long). Elsewhere it is an extra, x0.5 to x2
  around the chart's time.
- **SNAPPY** (the snare's second knob, as on the 808) sets the snare's noise.
- **TONE** is the 808's tone knob on the bass drum, snare and cymbal. On other voices it
  shifts the voice's filter, which the 808 does not offer.
- **VELOCITY** plays the 808's accent: each voice's amplitude moves from the chart's normal
  value to its accent value with how hard the pad is hit.
- **HAT CHOKE**: the closed hat cuts the open hat, as on the 808.
- **DRIVE** is not on the 808 and is off by default.

Every knob at 12 o'clock (64) is the chart's reference setting. The three kits are that
setting ("TR-808") and the chart's short and long decay settings for the bass drum, cymbal
and open hat.

## Still approximate

- **Decay time.** The chart doesn't say what level its decay times are measured to. The Kit
  treats them as the time to fall to a tenth (-20 dB).
- **Waveshapes.** The bridged-T voices are drawn as decaying sines. The real circuits' shape,
  the bass drum's exact pitch sweep and its "sigh", and the snare noise's filter and length
  come from the papers' descriptions, not from measurements.
- **Filters with no published value:** the hand clap's band-pass (800-1600 Hz), the
  cowbell's band-pass (about 2640 Hz), the maracas' and hats' high-passes, and the hats'
  band-pass. Only the cymbal's 3440 Hz and 7100 Hz bands are sourced (S2).
- **Hand clap rhythm.** The three quick bursts within the 808's 30 ms clap pulse are not
  timed from data.
- **Mix.** The 808's mix output is mono, and so is the Kit. Relative levels follow the chart's
  amplitudes, not a measurement of the mix bus.
