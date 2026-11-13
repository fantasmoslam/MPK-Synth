/* SPDX-License-Identifier: GPL-3.0-only
 * Poly 106: a six-voice polysynth modelled on the Roland Juno-106.
 *
 * Per voice: one oscillator with sawtooth, pulse (width set by hand or swept by the LFO)
 * and a square sub-oscillator an octave down, plus noise; a 4-position high-pass; a
 * resonant 4-pole low-pass driven by the envelope, the LFO and the keyboard; and a VCA
 * on the envelope or a plain gate. One envelope (ADSR) shapes both filter and VCA, as on
 * the original. One LFO with a delayed fade-in is shared by all voices. The voices are
 * summed in mono and widened by the two-speed stereo chorus.
 *
 * Everything the Juno-106 computes digitally (envelopes, LFO, LFO delay, the VCF and PWM
 * control voltages) follows its voice CPU's ROM; the presets are its 128 factory patches.
 * docs/sound-sources/poly106.md lists the sources and what is still approximate.
 */
#include "model.h"
#include "dsp.h"
#include <string.h>

enum {
    P_LFO_RATE, P_LFO_DELAY, P_RANGE, P_DCO_LFO, P_PWM, P_PWM_MODE, P_PULSE, P_SAW, P_SUB, P_NOISE,
    P_HPF, P_FREQ, P_RES, P_ENV_POL, P_ENV, P_VCF_LFO, P_KYBD, P_VCA_MODE, P_LEVEL,
    P_ATTACK, P_DECAY, P_SUSTAIN, P_RELEASE, P_CHORUS, P_BEND, P_VELOCITY, P_COUNT
};

static const mp_param params[P_COUNT] = {
    [P_LFO_RATE] = {"RATE", "LFO", 0, 127, 50, MP_FMT_NUMBER, MP_UI_SLIDER, 0},
    [P_LFO_DELAY] = {"DELAY", "LFO", 0, 127, 0, MP_FMT_NUMBER, MP_UI_SLIDER, 0},
    [P_RANGE] = {"RANGE", "DCO", 0, 2, 1, MP_FMT_LIST, MP_UI_SWITCH, "16'|8'|4'"},
    [P_DCO_LFO] = {"LFO", "DCO", 0, 127, 0, MP_FMT_NUMBER, MP_UI_SLIDER, 0},
    [P_PWM] = {"PWM", "DCO", 0, 105, 0, MP_FMT_NUMBER, MP_UI_SLIDER, 0},
    [P_PWM_MODE] = {"PWM BY", "DCO", 0, 1, 0, MP_FMT_SWITCH, MP_UI_SWITCH, "LFO|MAN"},
    [P_PULSE] = {"PULSE", "DCO", 0, 1, 1, MP_FMT_SWITCH, MP_UI_SWITCH, "OFF|ON"},
    [P_SAW] = {"SAW", "DCO", 0, 1, 1, MP_FMT_SWITCH, MP_UI_SWITCH, "OFF|ON"},
    [P_SUB] = {"SUB", "DCO", 0, 127, 0, MP_FMT_NUMBER, MP_UI_SLIDER, 0},
    [P_NOISE] = {"NOISE", "DCO", 0, 127, 0, MP_FMT_NUMBER, MP_UI_SLIDER, 0},
    [P_HPF] = {"FREQ", "HPF", 0, 3, 1, MP_FMT_LIST, MP_UI_SLIDER, "0|1|2|3"},
    [P_FREQ] = {"FREQ", "VCF", 0, 127, 80, MP_FMT_NUMBER, MP_UI_SLIDER, 0},
    [P_RES] = {"RES", "VCF", 0, 127, 0, MP_FMT_NUMBER, MP_UI_SLIDER, 0},
    [P_ENV_POL] = {"ENV", "VCF", 0, 1, 0, MP_FMT_SWITCH, MP_UI_SWITCH, "+|-"},
    [P_ENV] = {"ENV", "VCF", 0, 127, 40, MP_FMT_NUMBER, MP_UI_SLIDER, 0},
    [P_VCF_LFO] = {"LFO", "VCF", 0, 127, 0, MP_FMT_NUMBER, MP_UI_SLIDER, 0},
    [P_KYBD] = {"KYBD", "VCF", 0, 127, 64, MP_FMT_NUMBER, MP_UI_SLIDER, 0},
    [P_VCA_MODE] = {"MODE", "VCA", 0, 1, 0, MP_FMT_SWITCH, MP_UI_SWITCH, "ENV|GATE"},
    [P_LEVEL] = {"LEVEL", "VCA", 0, 127, 100, MP_FMT_NUMBER, MP_UI_SLIDER, 0},
    [P_ATTACK] = {"A", "ENV", 0, 127, 0, MP_FMT_NUMBER, MP_UI_SLIDER, 0},
    [P_DECAY] = {"D", "ENV", 0, 127, 55, MP_FMT_NUMBER, MP_UI_SLIDER, 0},
    [P_SUSTAIN] = {"S", "ENV", 0, 127, 90, MP_FMT_NUMBER, MP_UI_SLIDER, 0},
    [P_RELEASE] = {"R", "ENV", 0, 127, 35, MP_FMT_NUMBER, MP_UI_SLIDER, 0},
    [P_CHORUS] = {"CHORUS", "CHORUS", 0, 3, 1, MP_FMT_LIST, MP_UI_SWITCH, "OFF|I|II|I+II"},
    [P_BEND] = {"BEND", "CONTROL", 0, 12, 2, MP_FMT_NUMBER, MP_UI_KNOB, 0},
    [P_VELOCITY] = {"VELOCITY", "CONTROL", 0, 127, 30, MP_FMT_NUMBER, MP_UI_KNOB, 0},
};

/* The 128 factory patches of the Juno-106 (groups A and B), as the 18 bytes of its
 * patch dump: the 16 sliders in dump order, then switch bytes 1 and 2. The bytes are
 * copied unchanged from the factory SysEx data; J106() decodes them onto the controls
 * above. Sources, and how each byte maps, are in docs/sound-sources/poly106.md.
 *
 * Dump byte order: LFO rate, LFO delay, DCO LFO, DCO PWM, noise, VCF freq, VCF res,
 * VCF env, VCF LFO, VCF kybd, VCA level, A, D, S, R, sub.
 * Switch byte 1: bit 0-2 range 16'/8'/4', 3 pulse, 4 saw, 5 chorus off, 6 chorus I (else II).
 * Switch byte 2: bit 0 PWM manual (else LFO), 1 env polarity -, 2 VCA gate (else env),
 * 3-4 HPF (11 = position 0 ... 00 = position 3).
 * The Juno-106 has no velocity, so these presets turn it off. */
#define J106_RANGE(s1) (((s1) & 4) ? 2 : ((s1) & 2) ? 1 : 0)
#define J106_CHORUS(s1) (((s1) & 0x20) ? 0 : ((s1) & 0x40) ? 1 : 2)
#define J106(name, lrate, ldelay, dlfo, pwm, noise, freq, res, env, vlfo, kybd, level, a, d, s, r, sub, s1, s2) \
    MP_PRESET(name, P_LFO_RATE, lrate, P_LFO_DELAY, ldelay, P_DCO_LFO, dlfo, P_PWM, pwm, P_NOISE, noise, \
        P_FREQ, freq, P_RES, res, P_ENV, env, P_VCF_LFO, vlfo, P_KYBD, kybd, P_LEVEL, level, \
        P_ATTACK, a, P_DECAY, d, P_SUSTAIN, s, P_RELEASE, r, P_SUB, sub, \
        P_RANGE, J106_RANGE(s1), P_PULSE, ((s1) >> 3) & 1, P_SAW, ((s1) >> 4) & 1, P_CHORUS, J106_CHORUS(s1), \
        P_PWM_MODE, (s2) & 1, P_ENV_POL, ((s2) >> 1) & 1, P_VCA_MODE, ((s2) >> 2) & 1, P_HPF, 3 - (((s2) >> 3) & 3), \
        P_VELOCITY, 0)

static const mp_preset presets[] = {
    J106("A11 Brass Set 1",  20,  49,   0, 102,   0,  35,  13,  58,   0,  86, 108,   3,  49,  45,  32,   0,  81,  17),
    J106("A12 Brass Swell",   6,  48,   0,  56,   0,  43,  17,  26,   0,  84,  75,  64, 118,  38,  37,  70,  82,  25),
    J106("A13 Trumpet",  52,  45,   8, 102,   0,  55,  34,  24,   1,  59, 127,   5,  66,  48,  16,   0,  50,   9),
    J106("A14 Flutes",  60,  43,   1,   0,   0,  55,  32,  10,  11,  41, 127,  23,  81,   0,  18,   0,  50,   1),
    J106("A15 Moving Strings",  63,   0,   0,  39,   0,  77,  20,   4,   0, 111,  34,  13,  87,  88,  35,  14,  26,  16),
    J106("A16 Brass & Strings",  35,   0,   0,  56,   0,  76,  17,   4,   0,  41,  78,  44,  66,  53,  44,  23,  73,  24),
    J106("A17 Choir",  59,  14,  13,  25,   0,  59,  94,   2,   0,  62, 127,  68,  11, 127,  48,   0,  74,   9),
    J106("A18 Piano I",  20,  49,   0,  80,   0,  65,  12,  10,   0,  27, 103,   0,  66,   0,  30,  86,  42,  17),
    J106("A21 Organ I",  54,  15,   0,  53,   0,  43,  76,  14,   1, 127, 100,   0,  10,  82,   0,  23,  41,  28),
    J106("A22 Organ II",  44,  15,   0,  53,   0,  53,  76,  14,   1,  85,  74,   0,  10,  82,   0,  58,  74,  28),
    J106("A23 Combo Organ",  75,  21,   9,  57,   0,  63,  70,   4,   0, 109,  96,   0,  48,  43,  46,  61,  44,  13),
    J106("A24 Calliope",  82,  40,  11,   0,   0,  87,  27,  17,   0,  56,  89,   7, 127, 127,   6,  48,  74,  11),
    J106("A25 Donald Pluck",  76,  21,   9,  57,   0,  73, 105,  15,   0,  78,  82,   2,   5,  43,  10, 127,  44,   7),
    J106("A26 Celeste (1 oct. up)",  28,   0,   0,   0,   0,  33,  24,  54,   0,  38,  96,   0,  44,   0,  81,  15,  44,  25),
    J106("A27 Elect. Piano I",  59,   0,   0,   0,   0,  16,  31,  61,   6,  35, 127,   1,  85,  43,  40,   0,  41,   1),
    J106("A28 Elect. Piano II",   0,   0,   0,  71,   0,  50,  69,   7,   0,  80, 102,   0,  68,   0,  22,   0,  73,  17),
    J106("A31 Clock Chimes (1 oct. up)",  59,   0,   0,   0,  22,  44, 127,   0,   0, 127, 104,   0,  48,   0,  51, 127,  68,  27),
    J106("A32 Steel Drums",  33,  53,   0,  32,   9,  71,  46,  26,   0, 127, 127,   0,  26,   0,  37,  33,  74,  27),
    J106("A33 Xylophone",   0,   0,   0,   0,   0,  29,  24,  54,   0,  51, 127,   0,  29,  29,  38,  15,  44,  25),
    J106("A34 Brass III",  52,  20,   0,  35,   0,  66,  24,  11,   0,  12, 127,  58, 100,  94,  37,  22,  82,  25),
    J106("A35 Fanfare",  47,   0,   0,  70,   0,  44,   0,  32,   0,  67,  33,  72, 104,  75,  49,  50,  89,  24),
    J106("A36 String III",  48,  27,   0, 102,   0,  71,  14,   0,   0,  84,  73,  63,  31, 127,  45,   0,  26,  16),
    J106("A37 Pizzicato",  60,  18,   0, 102,   0,  66,   2,   5,   0,  42, 127,   0,  11,   0,  12,   0,  90,   0),
    J106("A38 High Strings",  58,  14,   0, 102,   0,  84,   8,   2,   0,  71,  77,  18,  44, 127,  40,   0,  12,   8),
    J106("A41 Bass clarinet",  52,  45,   8,   0,   0,  48,  36,  25,   8,  58, 104,  11,  75,   0,  25,   0,  41,  17),
    J106("A42 English Horn",  47,  45,   9, 102,   0,  70,  48,   7,   0,  27, 127,   8,  81,  26,  16,   0,  42,   1),
    J106("A43 Brass Ensemble",  52,  45,   0, 102,   0,  46,  44,  29,   1,  59, 103,  16, 103,  97,  34,  31,  82,  17),
    J106("A44 Guitar",  86,  58,   0,  65,   0,  12,  15,  71,   0,  30,  96,   0,  52,  54,  31,   0,  57,  17),
    J106("A45 Koto",  90,  28,   0,  94,   0,  40,  58,  29,   0,  75, 127,   0,  56,   0,  39,   2,  74,   1),
    J106("A46 Dark Pluck",  31,   0,   0,  87,   0,  38,  61,  27,   0,  71,  90,   0,  52,  15,  63,  97,  10,  25),
    J106("A47 Funky I",   4,   0,   0,  73,   0,  31,  23,  51,   0,  41,  71,   0,  30,  36,   2,  64,  89,  29),
    J106("A48 Synth Bass I (unison)",  58,  16,   1,  57,  50,  32,   0,  66,   0,  35,  75,   0,  34,   0,  36, 102,  41,  25),
    J106("A51 Lead I",  72,  88,  18,   0,   0,  40,  97,  36,   0,  52,  99,   0,  45,  73,   0,   0,  42,  29),
    J106("A52 Lead II",  24,  88,   0,  53,   0,  45,   0,  43,   0,  52,  56,   1,  23,  91,  75,  79,  57,  20),
    J106("A53 Lead III",  86,  71,  21, 102,   0,  60,  31,  18,   1,  76, 127,   0,  66,  48,  11,   0,  50,  21),
    J106("A54 Funky II",   4,   0,   0,  48,   0,   5,  23,  81,   0,  41, 100,   0,  30,  39,   2,  57,  74,  21),
    J106("A55 Synth Bass II",  90,  21,   0,   0,   0,  43,   0,  47,   0,   0,  78,   0,  37,   9,  22,  45,  49,  28),
    J106("A56 Funky III",   4,   0,   0,   9, 101,  38,  54,  45,   0,  10,  79,   0,  24,   0,   2, 127,  57,  29),
    J106("A57 Thud Wah",  62,   0,   0, 102,   8,  78,  89,  34,   0,   0, 106,   0,   0, 102,  45,  32,  26,  26),
    J106("A58 Going Up",   0,   0,   0, 100,  28,  79, 127,  37,   0,  65,  75,   0, 108,  18, 127,   0,  34,  19),
    J106("A61 Piano II",   1,   0,   0,  72,   0,  47,   0,  39,   0,   5,  78,   0,  98,   0,  32, 115,  42,  16),
    J106("A62 Clav",  66,  15,   1, 102,   0,   7,  11,  86,   2,   0, 120,   0,  39,  48,  14,   0,  73,  21),
    J106("A63 Frontier Organ",  79,  15,   3,  85,   0,  69,  54,   0,   0,  52, 127,   0,   0, 127,   0,  29,  42,  28),
    J106("A64 Snare Drum (unison)",  52,  27,   0,   0,  91,  94,  11,  17,   0,   0, 101,   0,  25,   0,  30,  64,  34,  17),
    J106("A65 Tom Toms (unison)",  62,  16,   8,   0, 127,  53,   4,  40,   0,  21, 101,   0,  30,  15,  40,  58,  33,  24),
    J106("A66 Timpani (unison)",   0,   0,   0,  35, 127,  25,   0,  54,   0,  24, 127,   1,  56,  26,  71,  47,  33,  17),
    J106("A67 Shaker",  21,  71,   0,  58, 127,  89,  45,   1,  17,  80, 127,  10,   9,   0,   0,   0,  34,   1),
    J106("A68 Synth Pad",  56,   0,   5,  43,   0,  37,   0,  84,   0, 127,  51,   0,  85,  75,  62,  37,  10,  24),
    J106("A71 Sweep I",  78,   0,   0, 102,  28, 115,  40,  67,   0,   0,  97,   0,  63, 127,  82,  42,  12,  18),
    J106("A72 Pluck Sweep",  68,  48,   0,  68,   0,  60, 111,   9,   6,  98,  45,   0,  91,   0,  77, 125,  26,   8),
    J106("A73 Repeater",  77,  20,   9, 101,   0,  97,  30,  56,   0,  57,  87,  14,   0,  41,   0,  44,  74,  23),
    J106("A74 Sweep II",  88,  72,  15,  97,   0,  66, 102,  68,   0,  70, 101,   0,  89,  53,  78,  48,  74,  25),
    J106("A75 Pluck Bell",  51,   0,   5,  17,   0,  39,   0,  49,   0, 127,  49,   0,  85,  75,  53,  49,  12,  24),
    J106("A76 Dark Synth Piano", 127,   0,   0,   0,   0,  56,  86,   6,   0,  71,  79,   0,  47,   0,  50, 127,  26,  26),
    J106("A77 Sustainer",  71,   0,   5,  46,   0,  39,   0,  84,   0, 127,  57,   0,  85,  75,  70,  49,  42,  24),
    J106("A78 Wah Release",  89,  14,   1,  25,   0,  72,  88,  19,   0,  66, 104,   0,  84,   0,  29,  87,  10,  19),
    J106("A81 Gong (play low chords)", 127,   0,   0, 102,  56,  70, 107,   5,   0,  72,  91,   3,  48, 127,  98,   0,  73,  19),
    J106("A82 Resonance Funk",  59,   0,   0,   0,   0,  26, 127,  45,   0,  89, 127,   0,  19,   0,  22,   0,  33,  17),
    J106("A83 Drum Booms (1 oct. down)",   0,   0,   0, 102, 127,  42,   0,  34,   0,  58, 127,   0,  36,  15,  49,  46,  33,  25),
    J106("A84 Dust Storm",   0,   0,   0,   0, 127,  52,  85,   0,  44,  94, 127,  88,  91,  28,  85,   0,  33,  17),
    J106("A85 Rocket Men",   8,  32,   0, 102,  97,  73, 114,   8,   0,  56, 127,   0,  89, 127, 104,   0,  36,   3),
    J106("A86 Hand Claps",  59,   0,   0,   0, 127,  17,  88,  54,   0,  55, 127,   1,  11,   0,   8,   0,  33,   5),
    J106("A87 FX Sweep", 127,  65, 116, 102, 127,  71,  44,  83,   0,  94, 127,   0,  94,   0, 112, 127,  41,  18),
    J106("A88 Caverns",   0,   0,   0, 102, 127,  68, 118,   0,   0,  69, 107,   0,  13,  38,  47,   0,  33,   8),
    J106("B11 Strings",  57,  45,   0,  55,   0,  85,   0,   0,   0, 108,  52,  59,  32,  86,  40,   0,  26,  24),
    J106("B12 Violin",  66,  45,  20,   0,   0,  77,   0,   0,   0, 120, 110,  43,  45,  57,  26,   0,  50,  24),
    J106("B13 Chorus Vibes",  72,  45,   0,   0,   0,  10,   0,  59,   0,  23,  88,   0,  89,  47,  74,   0,  74,  25),
    J106("B14 Organ 1",  45,  42,   0,  73,   0,  60,  72,  14,   0, 127,  58,   0,   0,   0,   0,  97,  10,  29),
    J106("B15 Harpsichord 1",  23,   0,   0,  54,   0, 127, 127,  90,   0,  86, 125,   0,  60,   0,  31,  35,  44,   0),
    J106("B16 Recorder",  78,   0,   0,   8,   0,   6,   0,  46,   0, 127, 127,   5,  21, 127,  30,   0,  42,  17),
    J106("B17 Perc. Pluck",  62,  38,   8,   0,   0,   0,   0,  90,   0,  73,  65,   0,  16,  78, 116,   0,  74,  25),
    J106("B18 Noise Sweep", 127,   0,   0,   0, 127,   2,   0,  77,   0, 104,  92,  14,  78, 108, 120,   0,  33,  24),
    J106("B21 Space Chimes",  99,   0,   0,   0,   0,  70, 127,   0,   0,  74,  85,   0,  25,   0,  60,   0,  76,  24),
    J106("B22 Nylon Guitar",  72,  45,   0,  29,   0,  57,   0,  26,   0,  25, 115,   0,  89,   0,  32,   0,  42,   1),
    J106("B23 Orchestral Pad",  22,  45,   0, 105,   0,  33,   0,  55,   0,  36,   0,  29,  88,  50,  52, 127,  26,  24),
    J106("B24 Bright Pluck",   0,   0,   0,  58,   0,  57,   0,  40,   0,  86, 104,   0,  25,  42,  44,   0,  42,  17),
    J106("B25 Organ Bell",  78,   0,   6,  68,   0,  61,   0,   0,   0, 127,  62,   0,   0, 127,  26,  85,  90,  25),
    J106("B26 Accordion",   0,   0,   0,  64,   0,  77,   0,  14,   0,  74,  66,   8,  11, 110,   9,  80,  90,   1),
    J106("B27 FX Rise 1", 116,   0,   0,   0,   0, 108, 127,  16, 127,  64,   0,   0,   0, 127,  84,   0,  68,  26),
    J106("B28 FX Rise 2", 108,   0,   0,   0,   0,  94, 127,  80,  23, 127,   0,   0, 127,  51,  71,   0,   2,  26),
    J106("B31 Brass",  51, 127,   0,  73,   0,   0,   0,  94,   0, 127,  72,   3,  44,  51,  11,   0,  82,  28),
    J106("B32 Helicopter", 106,   0,   0,  48,   0,  82,   5,  93, 127,   0,  72,   0,   0,  35,  76, 127,  10,  27),
    J106("B33 Lute",  52,   0,   2, 105,   0,  29,   0,  35,   0,  86, 127,   0,  48,  34,  87,   0,  42,  25),
    J106("B34 Chorus Funk",  77,  94,   0,  73,   0,  47,  34,  53,   0,  65,  42,   0,  11,  34,   0,  79,  90,  29),
    J106("B35 Tomita",  78,   0,   0, 105,   0,  49, 125,  15,   2, 127,  57,   0,  14,  79,   0,   0,  36,  31),
    J106("B36 FX Sweep 1", 127,   0,   0,   0,   0,  58, 127,  16,  76,  64,   0,   0,   0, 127,  85,   0,  68,  24),
    J106("B37 Sharp Reed",  21,   0,   0,  73,   0,  33,   0,  53,   0,  65,  55,   2,  18, 112,   0,   0,  60,  28),
    J106("B38 Bass Pluck",   0,   0,   0,  60,   0,  37,  29,  28,   0,  43,  92,   0,  42,  35,   0,   0,  57,  24),
    J106("B41 Resonant Rise",  52,   0,  15,   0,   0,  66, 107,  31,   0,  73,  62,   0,  52,  39,   0,   0,  17,  30),
    J106("B42 Harpsichord 2",   0,   0,   0, 105,   0, 127,  95,   0,   0, 127, 107,   0,  45,   0,   0,  35,  60,  17),
    J106("B43 Dark Ensemble",   0,   0,   0,  57,   0,  55,   0,   0,   0, 107,  67,  21,   0, 127,  35,  75,  90,  27),
    J106("B44 Contact Wah",  78,   0,   0,  81,   0,  61, 101,  46,   0, 127, 127,   0,   0,   0,   0,   0,  42,  23),
    J106("B45 Noise Sweep 2", 127,   0,   0,   0, 127, 127,   0,  83,   0, 104, 116, 127,  79,  26,  83,   0,  68,  26),
    J106("B46 Glassy Wah",   0,  94,   0,  28,   0,  24,  34,  61,   0,  65, 112,   6,  18,  50,  38,   0,  42,  25),
    J106("B47 Phase Ensemble",  11,  45,   5, 105,   0,  79,   0,   0,   0, 127,  99,  84,  45,  57,  49,   0,  10,  24),
    J106("B48 Chorused Bell",   0,  11,   0, 105,   0,  59, 127,   0,   0, 127,  62,   0,  62,   0,  57, 101,  76,  24),
    J106("B51 Clav",  50,  11,   0,  91,   0,  32,   0,  63,   0,   0, 108,   0,  43,  55,   0,   7,  41,   9),
    J106("B52 Organ 2",  81,   0,   0, 104,   0,  54, 106,   7,   0, 127,  79,   0,   0,   0,   0,   0,  81,  29),
    J106("B53 Bassoon",  84,  38,   9,  90,   0,  35,   0,  43,   9,  98, 127,   5, 127, 106,   2,   0,  41,   1),
    J106("B54 Auto Release Noise Sweep", 127,   0,   0,   0, 127, 127,   0, 127,   0, 104, 107,   0,  79,  57,  83,   0,  68,  26),
    J106("B55 Brass Ensemble",  25,  94,   0,  73,   0,  47,  34,  35,   0,  65,  39,   6,  68,  67,  38,   0,  90,  24),
    J106("B56 Ethereal",  69,   0,   0, 105,   0,  47, 120,   0,   0, 127,  60,  92,  50, 127,  55,  59,  26,  24),
    J106("B57 Chorus Bell 2",  72,  45,   0,  29,   0,   0, 102,  62,   0,  90,  30,   0, 117,   0, 120,   0,  90,  25),
    J106("B58 Blizzard",   1,   0,   0, 105, 127,  56,  89,   7,  38, 121, 127,  78,  98,  87,  78,   0,  36,   0),
    J106("B61 E. Piano with Tremolo",  44,   0,   0,  21,   0,  22,   0,  35,   7, 107, 103,   0,  65,  60,  98, 127,  44,  16),
    J106("B62 Clarinet",  69,  22,   9,   0,   0,  35,   0,  43,   7,  98, 104,   5, 127, 106,   7,   0,  42,   1),
    J106("B63 Thunder", 127,   0,   0,   0, 127,   2,   0,  77,   0, 104, 107,   0,  22, 108, 120,   0,  33,  24),
    J106("B64 Reedy Organ",  35,  18,   0,  73,   0,  33,   0,  53,   0,  65,  69,   3,  44, 109,   0,   0,  42,  28),
    J106("B65 Flute / Horn",  34,   0,   0,  59,   0,  25,   0,  42,   0,  65,  22,  21,  73,  71,  48,   0,  25,  24),
    J106("B66 Toy Rhodes",  30,  45, 127,   0,   0,  58, 127,   0,   0, 127, 107,   0,  89,   0,  39,   0,  36,   0),
    J106("B67 Surf's Up",   1,   0,   0, 105, 127,  84,  11,   0,  49,  54,  75,  33,  97, 127, 121,   0,  36,  10),
    J106("B68 OW Bass",  50,   0,   0,  50,   0,  63,  84,  65,   0, 127,  70, 127,   0, 107,   0,  47,  57,  31),
    J106("B71 Piccolo",  70,  28,   0,  14,   0,   4,   0,  63,  16,  98,  87,  16, 103, 106,  21,   0,  60,   9),
    J106("B72 Melodic Taps",  99,   0,   0,   0, 127,  72, 112,   0,   0, 127, 127,   0,  24,   0,  24,   0,  65,   0),
    J106("B73 Meow Brass",  51, 127,   0,  73,   0,  45, 100,  35,   0,  65,  84,   4,  90,   0,  27,   0,  50,  28),
    J106("B74 Violin (high)",  71,  45,  22,   0,   0,  89,   0,   0,   3, 120,  85,  43,  45,  57,  26,   0,  52,  24),
    J106("B75 High Bells",  30,  45, 127,   0,   0,  76, 127,   0,   0, 127, 111,   0,  22,  52,  59,   0,  36,   0),
    J106("B76 Rolling Wah",  36,   0,   0, 105,   0,  60,   9,   0, 127,   0,  77,  34,  55, 127, 105,   0,  42,  24),
    J106("B77 Ping Bell",   0,  11,   0, 104,   0,  76, 127,   0,   0, 127,  83,   0,  23,  23,  57,   0,  76,  24),
    J106("B78 Brassy Organ",  16,   0,   0,  68,   0,   8,   0,  87,   0,  52,  14,   0,  36, 109,   0,  56,  89,  24),
    J106("B81 Low Dark Strings",  21,  45,   5,  81,   0,  71,   0,   0,   0, 103,  40,  83,  23, 109,  42,   0,  25,  24),
    J106("B82 Piccolo Trumpet",  51, 127,   0,  73,   0,   0,   0,  94,   0, 127,  97,   3,  44,  51,  11,   0,  50,  28),
    J106("B83 Cello",  57,  45,  21,   0,   0,  75,   0,   3,   1,  45, 109,  48,  65,  90,  34,   0,  49,  24),
    J106("B84 High Strings",  80,   0,  10,  70,   0,  92,   0,   0,   0,  71,  48,  27,  57,  57,  41,   0,  28,  24),
    J106("B85 Rocket Men", 108,   0,   0,   0,   0, 110, 127,  80,  63, 127,   0,   0, 127,  51,  89,   0,   2,  26),
    J106("B86 Forbidden Planet",  50,  11,   0,  44,   0,  29,   4,  88,   5,  95,  79,   0,  48,  23,  42,   9,  76,   1),
    J106("B87 Froggy",  78,   0,   0,   0,   0,  55, 127,  43,   0, 127,  77,   0,   0,   0,   0, 127,  41,  23),
    J106("B88 Owgan",  50,   0,   0,  45,   0,  38,  84,  32,   0, 127, 101,   0,  49,  55,   0,  56,  57,  25),
};

/* ---- The Juno-106's voice CPU ------------------------------------------------------------
 * On the Juno-106 the envelopes, the LFO, the LFO delay and every VCF and PWM control
 * voltage are computed by the voice board's uPD7811 CPU (IC29) in a main loop that runs
 * about every 4.23 ms, from tables in its ROM. The tables below are copied from the ROM
 * disassembly and the arithmetic follows it; see docs/sound-sources/poly106.md. Each
 * control's 0-127 value is the same 7-bit value the Juno-106 sends in its patch dump. */

/* The main loop's period: not set by a clock but by how long the code takes, measured on
 * hardware as 4.2335 ms on average (about 236 runs a second). */
#define TICK_HZ (1000.0f / 4.2335f)
#define ENV_MAX 0x3FFF

/* $0B60: attack increment per tick, by the A slider (0 = instant, 127 = 0x15: 3.3 s). Also
 * the LFO delay's hold-off rate. */
static const uint16_t rom_attack[128] = {
    0x4000, 0x2000, 0x1000, 0x0aaa, 0x0800, 0x0666, 0x0555, 0x0492, 0x0400, 0x038e, 0x0333, 0x02e9, 0x02ab, 0x0276, 0x0249, 0x0222,
    0x0200, 0x01e2, 0x01c7, 0x01af, 0x0199, 0x0186, 0x0174, 0x0164, 0x0155, 0x0148, 0x013b, 0x012f, 0x0124, 0x011a, 0x0111, 0x0108,
    0x0100, 0x00f8, 0x00f1, 0x00ea, 0x00e4, 0x00dd, 0x00d8, 0x00d2, 0x00cd, 0x00c8, 0x00c3, 0x00bf, 0x00ba, 0x00b6, 0x00b2, 0x00ae,
    0x00ab, 0x00a7, 0x00a4, 0x00a1, 0x009e, 0x009b, 0x0098, 0x0095, 0x0092, 0x0090, 0x008d, 0x008b, 0x0089, 0x0086, 0x0084, 0x0082,
    0x007f, 0x007d, 0x007a, 0x0077, 0x0074, 0x0072, 0x006f, 0x006c, 0x0069, 0x0067, 0x0064, 0x0061, 0x005e, 0x005c, 0x0059, 0x0056,
    0x0053, 0x0050, 0x004e, 0x004b, 0x0048, 0x0045, 0x0042, 0x0040, 0x003f, 0x003d, 0x003c, 0x003a, 0x0039, 0x0037, 0x0036, 0x0034,
    0x0033, 0x0031, 0x0030, 0x002e, 0x002d, 0x002b, 0x002a, 0x0028, 0x0027, 0x0025, 0x0024, 0x0022, 0x0021, 0x0021, 0x0020, 0x0020,
    0x001f, 0x001f, 0x001e, 0x001e, 0x001d, 0x001d, 0x001c, 0x001c, 0x001b, 0x001b, 0x001a, 0x0019, 0x0018, 0x0017, 0x0016, 0x0015,
};
/* $0D60: decay and release, the fraction (of 65536) of the distance left kept each tick. */
static const uint16_t rom_decay[128] = {
    0x1000, 0x3000, 0x5000, 0x7000, 0x9000, 0xa000, 0xa800, 0xb000, 0xb800, 0xc000, 0xc800, 0xd000, 0xd800, 0xe000, 0xe800, 0xf000,
    0xf080, 0xf100, 0xf180, 0xf200, 0xf280, 0xf300, 0xf380, 0xf400, 0xf480, 0xf500, 0xf580, 0xf600, 0xf680, 0xf700, 0xf780, 0xf800,
    0xf880, 0xf900, 0xf980, 0xfa00, 0xfa80, 0xfb00, 0xfb80, 0xfc00, 0xfc80, 0xfd00, 0xfd80, 0xfe00, 0xfe0c, 0xfe18, 0xfe24, 0xfe30,
    0xfe3c, 0xfe48, 0xfe54, 0xfe60, 0xfe6c, 0xfe78, 0xfe84, 0xfe90, 0xfe9c, 0xfea8, 0xfeb4, 0xfec0, 0xfecc, 0xfed8, 0xfee4, 0xfef0,
    0xfefc, 0xff08, 0xff0c, 0xff10, 0xff14, 0xff18, 0xff1c, 0xff20, 0xff24, 0xff28, 0xff2c, 0xff30, 0xff34, 0xff38, 0xff3c, 0xff40,
    0xff44, 0xff48, 0xff4c, 0xff50, 0xff54, 0xff58, 0xff5c, 0xff60, 0xff64, 0xff68, 0xff6c, 0xff70, 0xff74, 0xff78, 0xff7c, 0xff80,
    0xff84, 0xff88, 0xff8c, 0xff90, 0xff94, 0xff98, 0xff9c, 0xffa0, 0xffa4, 0xffa8, 0xffac, 0xffb0, 0xffb4, 0xffb8, 0xffbc, 0xffc0,
    0xffc4, 0xffc8, 0xffcc, 0xffd0, 0xffd4, 0xffd8, 0xffdc, 0xffe0, 0xffe4, 0xffe8, 0xffec, 0xfff0, 0xfff1, 0xfff2, 0xfff3, 0xfff4,
};
/* $0C60: LFO step per tick, by the RATE slider. The LFO climbs 0..0x1FFF and back, then the
 * same below zero, so one cycle is 4 * ceil(8192 / step) ticks: 0.04 Hz to 29.5 Hz. */
static const uint16_t rom_lfo_speed[128] = {
    0x0005, 0x000f, 0x0019, 0x0028, 0x0037, 0x0046, 0x0050, 0x005a, 0x0064, 0x006e, 0x0078, 0x0082, 0x008c, 0x0096, 0x00a0, 0x00aa,
    0x00b4, 0x00be, 0x00c8, 0x00d2, 0x00dc, 0x00e6, 0x00f0, 0x00fa, 0x0104, 0x010e, 0x0118, 0x0122, 0x012c, 0x0136, 0x0140, 0x014a,
    0x0154, 0x015e, 0x0168, 0x0172, 0x017c, 0x0186, 0x0190, 0x019a, 0x01a4, 0x01ae, 0x01b8, 0x01c2, 0x01cc, 0x01d6, 0x01e0, 0x01ea,
    0x01f4, 0x01fe, 0x0208, 0x0212, 0x021c, 0x0226, 0x0230, 0x023a, 0x0244, 0x024e, 0x0258, 0x0262, 0x026c, 0x0276, 0x0280, 0x028a,
    0x029a, 0x02aa, 0x02ba, 0x02ca, 0x02da, 0x02ea, 0x02fa, 0x030a, 0x031a, 0x032a, 0x033a, 0x034a, 0x035a, 0x036a, 0x037a, 0x038a,
    0x039a, 0x03aa, 0x03ba, 0x03ca, 0x03da, 0x03ea, 0x03fa, 0x040a, 0x041a, 0x042a, 0x043a, 0x044a, 0x045a, 0x046a, 0x047a, 0x048a,
    0x04be, 0x04f2, 0x0526, 0x055a, 0x058e, 0x05c2, 0x05f6, 0x062c, 0x0672, 0x06b8, 0x0708, 0x0758, 0x07a8, 0x07f8, 0x085c, 0x08c0,
    0x0924, 0x0988, 0x09ec, 0x0a50, 0x0ab4, 0x0b18, 0x0b7c, 0x0be0, 0x0c58, 0x0cd0, 0x0d48, 0x0dde, 0x0e74, 0x0f0a, 0x0fa0, 0x1000,
};
/* $0A80: DCO LFO (vibrato) depth, by the DCO LFO slider: gentle below 64, steeper above. */
static const uint8_t rom_lfo_depth[128] = {
    0x00, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d,
    0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1a, 0x1b, 0x1c, 0x1d,
    0x1e, 0x1f, 0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28, 0x29, 0x2a, 0x2b, 0x2c, 0x2d,
    0x2e, 0x2f, 0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3a, 0x3b, 0x3c, 0x3d,
    0x3e, 0x40, 0x42, 0x44, 0x46, 0x48, 0x4a, 0x4c, 0x4e, 0x50, 0x52, 0x54, 0x56, 0x58, 0x5a, 0x5c,
    0x5e, 0x60, 0x62, 0x64, 0x66, 0x68, 0x6a, 0x6c, 0x6e, 0x70, 0x72, 0x74, 0x76, 0x78, 0x7a, 0x7c,
    0x80, 0x84, 0x88, 0x8c, 0x90, 0x94, 0x98, 0x9c, 0xa0, 0xa4, 0xa8, 0xac, 0xb0, 0xb4, 0xb8, 0xbc,
    0xc0, 0xc4, 0xc8, 0xcc, 0xd0, 0xd4, 0xd8, 0xdc, 0xe0, 0xe4, 0xe8, 0xec, 0xf0, 0xf8, 0xff, 0xff,
};
/* $0B30: how fast the LFO fades in once the delay's hold-off is over, by DELAY / 16. */
static const uint16_t rom_lfo_fade[8] = { 0xffff, 0x0419, 0x020c, 0x015e, 0x0100, 0x0100, 0x0100, 0x0100 };

/* $083D: value * coeff / 65536, as the ROM does it (the low-by-low product is skipped). */
static uint16_t rom_scale(uint16_t v, uint16_t c) {
    unsigned vh = v >> 8, vl = v & 0xff, ch = c >> 8, cl = c & 0xff;
    return (uint16_t)(vh * ch + ((vh * cl) >> 8) + ((vl * ch) >> 8));
}

/* The VCF's control voltage, in the CPU's 14-bit units (the FREQ slider gives value * 128),
 * to Hz. The service manual's calibration puts 1568 on the 12-bit DAC (6272 here) at 248 Hz
 * and two octaves of keyboard tracking above it at 992 Hz: 1142 units an octave. */
static float vcf_hz(float cv) { return 248.0f * exp2f((mp_clampf(cv, 0, ENV_MAX) - 6272.0f) / 1142.0f); }

#define VOICES 6
#define CHORUS_LEN 2048
enum { ENV_ATTACK, ENV_DECAY, ENV_RELEASE, ENV_DONE };
typedef struct {
    int note, gate;          /* note -1 when free */
    unsigned age;
    float phase, sub, vel, gate_amp;
    int stage;
    uint16_t env, env_prev;  /* the CPU's envelope, 0..ENV_MAX, this tick and the last */
    mp_ladder vcf;
    mp_onepole hpf;
} voice;
typedef struct {
    float rate, tick;
    voice v[VOICES];
    unsigned clock;
    mp_noise noise;
    /* The LFO: a 0..0x1FFF value and a counter whose bit 0 says falling, bit 1 negative. */
    uint16_t lfo;
    uint8_t lfo_flag;
    float lfo_out, lfo_prev;
    /* The LFO delay: a hold-off, then a fade-in; depth 0..255. */
    int delay_armed, delay_stage;
    uint16_t holdoff, fade;
    float depth, depth_prev;
    int held;
    float chorus[CHORUS_LEN];
    unsigned cw;
    float cl_phase;
    mp_onepole wet;
    mp_onepole bass;         /* HPF position 0: a gentle bass boost */
} state;

static float slider(const int16_t *p, int id) { return p[id] / 127.0f; }
static int byte(const int16_t *p, int id) { int v = p[id]; return v < 0 ? 0 : v > 127 ? 127 : v; }

static void init(void *mem, float rate) {
    state *s = mem; memset(s, 0, sizeof *s);
    s->rate = rate; s->noise.s = 22222u;
    for (int i = 0; i < VOICES; ++i) { s->v[i].note = -1; s->v[i].stage = ENV_DONE; }
    s->delay_armed = 1;
    mp_onepole_set(&s->wet, 9000, rate);
    mp_onepole_set(&s->bass, 90, rate);
}
static void note_on(void *mem, const int16_t *p, int note, int velocity) {
    state *s = mem; voice *v = 0;
    for (int i = 0; i < VOICES; ++i) if (s->v[i].note == note) { v = &s->v[i]; break; }
    if (!v) for (int i = 0; i < VOICES; ++i) if (s->v[i].note < 0) { v = &s->v[i]; break; }
    if (!v) {   /* steal the oldest, preferring released voices */
        unsigned best = 0;
        for (int i = 0; i < VOICES; ++i) {
            unsigned score = (s->clock - s->v[i].age) + (s->v[i].gate ? 0u : 0x40000000u);
            if (!v || score > best) { v = &s->v[i]; best = score; }
        }
    }
    if (v->note < 0) {
        memset(&v->vcf, 0, sizeof v->vcf); v->hpf.z = 0; v->env = v->env_prev = 0; v->gate_amp = 0;
        /* On the Juno-106 a voice's VCF never stops: at full resonance it is already
         * ringing when the VCA opens. Start it ringing rather than from silence. */
        if (p[P_RES] >= 120) v->vcf.s[0] = 0.3f;
    }
    v->note = note; v->gate = 1; v->age = ++s->clock;
    float sens = slider(p, P_VELOCITY);
    v->vel = 1.0f - sens + sens * (velocity / 127.0f);
    v->stage = ENV_ATTACK;   /* the attack climbs from wherever the envelope is */
    ++s->held;
}
static void note_off(void *mem, const int16_t *p, int note) {
    (void)p; state *s = mem;
    for (int i = 0; i < VOICES; ++i) if (s->v[i].note == note && s->v[i].gate) {
        s->v[i].gate = 0; s->v[i].stage = ENV_RELEASE; if (s->held > 0) --s->held;
    }
}
static void all_off(void *mem) {
    state *s = mem;
    for (int i = 0; i < VOICES; ++i) { s->v[i].note = -1; s->v[i].gate = 0; s->v[i].stage = ENV_DONE; s->v[i].env = s->v[i].env_prev = 0; }
    s->held = 0; s->delay_armed = 1;
}

/* One run of the CPU's main loop: the LFO, its delay, and every voice's envelope. */
static void cpu_tick(state *s, const int16_t *p) {
    /* The LFO ($074E): step up to 0x1FFF, back down to 0; each turn bumps the flag. */
    uint16_t step = rom_lfo_speed[byte(p, P_LFO_RATE)];
    if (!(s->lfo_flag & 1)) {
        unsigned up = (unsigned)s->lfo + step;
        if (up >= 0x2000) { s->lfo = 0x1FFF; s->lfo_flag++; } else s->lfo = (uint16_t)up;
    } else if (step > s->lfo) { s->lfo = 0; s->lfo_flag++; }
    else s->lfo -= step;
    s->lfo_prev = s->lfo_out;
    s->lfo_out = (s->lfo_flag & 2) ? -(float)s->lfo : (float)s->lfo;

    /* The LFO delay ($030D): it restarts when a key goes down after all were up. */
    int delay = byte(p, P_LFO_DELAY);
    if (s->held == 0) s->delay_armed = 1;
    else if (s->delay_armed) { s->delay_armed = 0; s->delay_stage = 0; s->holdoff = 0; s->fade = 0; }
    s->depth_prev = s->depth;
    if (s->delay_stage == 0) {
        s->holdoff = (uint16_t)(s->holdoff + rom_attack[delay]);
        s->depth = 0;
        if (s->holdoff >= 0x4000) s->delay_stage = 1;
    }
    if (s->delay_stage == 1) {
        unsigned f = (unsigned)s->fade + rom_lfo_fade[delay >> 4];
        if (f > 0xFFFF) { s->delay_stage = 2; s->depth = 255; } else { s->fade = (uint16_t)f; s->depth = (float)(f >> 8); }
    } else if (s->delay_stage == 2) s->depth = 255;

    /* The envelopes ($0500): a straight attack, then decay toward sustain and release
     * toward zero, each a fixed fraction of the distance left per tick. */
    uint16_t atk = rom_attack[byte(p, P_ATTACK)], dcy = rom_decay[byte(p, P_DECAY)], rel = rom_decay[byte(p, P_RELEASE)];
    uint16_t sus = (uint16_t)(byte(p, P_SUSTAIN) << 7);
    for (int i = 0; i < VOICES; ++i) {
        voice *v = &s->v[i];
        v->env_prev = v->env;
        if (v->note < 0) continue;
        switch (v->stage) {
        case ENV_ATTACK: {
            unsigned e = (unsigned)v->env + atk;
            if (e >= 0x4000) { v->env = ENV_MAX; v->stage = ENV_DECAY; } else v->env = (uint16_t)e;
            break;
        }
        case ENV_DECAY:
            v->env = v->env > sus ? (uint16_t)(rom_scale((uint16_t)(v->env - sus), dcy) + sus) : sus;
            break;
        case ENV_RELEASE:
            v->env = rom_scale(v->env, rel);
            if (v->env == 0) v->stage = ENV_DONE;
            break;
        default: v->env = 0;
        }
    }
}

static void render(void *mem, const int16_t *p, const mp_perf *perf, float *l, float *r, unsigned frames) {
    state *s = mem;
    const float rate = s->rate;
    /* Controls, once per block, in the CPU's units. */
    float range = (float)(p[P_RANGE] - 1) * 12.0f;
    int pwm_lfo = p[P_PWM_MODE] == 0, pulse_on = p[P_PULSE] != 0, saw_on = p[P_SAW] != 0;
    float pwm = (float)(byte(p, P_PWM) * 2) / 256.0f;        /* $0771 */
    float sub = slider(p, P_SUB) * 0.8f, noise = slider(p, P_NOISE) * 0.6f;
    /* Every position blocks DC (the voice outputs are AC-coupled on the Juno too: a
     * narrow pulse otherwise carries a large offset). */
    static const float hpf_hz[4] = { 10, 20, 240, 720 };
    int hpf = p[P_HPF];
    float cutoff = (float)(byte(p, P_FREQ) << 7);              /* 0..0x3F80 */
    float res = slider(p, P_RES) * 0.98f;
    int env_neg = p[P_ENV_POL] != 0;
    float env_amt = (float)(byte(p, P_ENV) * 2) / 256.0f;
    float vcf_lfo = (float)(byte(p, P_VCF_LFO) * 2);
    float kybd = (float)(byte(p, P_KYBD) * 2) / 256.0f;
    float dco_lfo = (float)rom_lfo_depth[byte(p, P_DCO_LFO)];
    int gate_mode = p[P_VCA_MODE] != 0;
    /* The VCA level slider: about +-10 dB around its middle, here 0 dB at the top. */
    float level = 0.55f * powf(10.0f, slider(p, P_LEVEL) - 1.0f);
    float bend = perf->bend;
    int mix_count = pulse_on + saw_on;
    float osc_gain = mix_count == 2 ? 0.5f : 0.7f;
    for (int i = 0; i < VOICES; ++i) mp_onepole_set(&s->v[i].hpf, hpf_hz[hpf], rate);
    /* Chorus speeds and depths (ms), after the original's three modes. */
    static const float ch_hz[4] = { 0, 0.513f, 0.863f, 9.75f }, ch_depth[4] = { 0, 1.85f, 1.85f, 0.25f }, ch_mid[4] = { 0, 3.5f, 3.5f, 3.3f };
    int chorus = p[P_CHORUS];
    const float tick_step = TICK_HZ / rate;

    for (unsigned n = 0; n < frames; ++n) {
        s->tick += tick_step;
        if (s->tick >= 1.0f) { s->tick -= 1.0f; cpu_tick(s, p); }
        /* The CPU's outputs step every tick; the analog side smooths the steps. */
        float t = s->tick;
        float lfo = s->lfo_prev + (s->lfo_out - s->lfo_prev) * t;           /* +-0x1FFF */
        float depth = s->depth_prev + (s->depth - s->depth_prev) * t;       /* 0..255 */
        /* $032D: vibrato in 1/256 semitone, up to about +-4 semitones. */
        float vib = lfo * floorf(dco_lfo * depth / 256.0f) / 256.0f / 8.0f / 256.0f;
        /* $0352: the LFO's share of the VCF, up to about +-3.6 octaves. */
        float lfo_cv = lfo * floorf(vcf_lfo * depth / 256.0f) / 512.0f;
        /* $0761: PWM by LFO swings around the middle, by hand sits at the top. */
        float pw_src = pwm_lfo ? mp_clampf(8192.0f - lfo, 0, ENV_MAX) : (float)ENV_MAX;
        float width = 0.5f - 0.45f * (pw_src * pwm / ENV_MAX);
        float mono = 0;
        for (int i = 0; i < VOICES; ++i) {
            voice *v = &s->v[i];
            if (v->note < 0) continue;
            float env = (float)v->env_prev + ((float)v->env - (float)v->env_prev) * t;
            float e = env / ENV_MAX;
            float target = gate_mode ? (v->gate ? 1.0f : 0.0f) : e;
            v->gate_amp += (target - v->gate_amp) * (gate_mode ? 0.01f : 1.0f);
            if (!v->gate && v->stage == ENV_DONE && v->env_prev == 0 && v->gate_amp < 1e-4f) { v->note = -1; continue; }
            float pitch = v->note + range + bend + vib;
            float hz = mp_hz(pitch), dt = hz / rate;
            v->phase += dt;
            if (v->phase >= 1) { v->phase -= 1; v->sub = v->sub > 0 ? -1.0f : 1.0f; }
            if (v->sub == 0) v->sub = 1;
            float x = 0;
            if (saw_on) x += mp_saw(v->phase, dt);
            if (pulse_on) x += mp_pulse(v->phase, dt, width);
            x *= osc_gain;
            if (sub > 0) {
                /* A square an octave down, flipping each time the main phase wraps. */
                x += v->sub * sub * 0.5f;
            }
            if (noise > 0) x += mp_white(&s->noise) * noise * 0.5f;
            /* The analog circuit's own hiss, about -80 dB: it is what starts the VCF
             * ringing in patches with both waves off and resonance at the top (A82). */
            x += mp_white(&s->noise) * 1e-4f;
            x = mp_hp1(&v->hpf, x);
            /* $04D5: cutoff + LFO, +- envelope, + keyboard from middle C (96 units a
             * semitone at full KYBD, so full KYBD tracks the keys 1:1). */
            float cv = cutoff + lfo_cv + (env_neg ? -1.0f : 1.0f) * env * env_amt + ((float)v->note - 60.0f) * 96.0f * kybd;
            float y = mp_ladder_run(&v->vcf, x * 0.6f, vcf_hz(cv), res, 1.0f, rate);
            mono += y * v->gate_amp * v->vel;
        }
        mono *= level;
        if (hpf == 0) mono += mp_lp1(&s->bass, mono) * 0.5f;   /* position 0 lifts the bass */
        float outl = mono, outr = mono;
        if (chorus > 0) {
            s->chorus[s->cw & (CHORUS_LEN - 1)] = mono;
            s->cl_phase += ch_hz[chorus] / rate; if (s->cl_phase >= 1) s->cl_phase -= 1;
            float t = mp_tri(s->cl_phase);
            float wet[2];
            for (int k = 0; k < 2; ++k) {
                float ms = ch_mid[chorus] + ch_depth[chorus] * (k ? -t : t);
                float dly = ms * 0.001f * rate;
                float pos = (float)s->cw - dly;
                int i0 = (int)floorf(pos); float fr = pos - (float)i0;
                float a0 = s->chorus[(unsigned)i0 & (CHORUS_LEN - 1)], a1 = s->chorus[(unsigned)(i0 + 1) & (CHORUS_LEN - 1)];
                wet[k] = a0 + (a1 - a0) * fr;
            }
            ++s->cw;
            float w = mp_lp1(&s->wet, (wet[0] + wet[1]) * 0.5f) - (wet[0] + wet[1]) * 0.5f;   /* keep the dark BBD tone */
            outl = mono * 0.7f + (wet[0] + w) * 0.7f;
            outr = mono * 0.7f + (wet[1] + w) * 0.7f;
        }
        l[n] += outl; r[n] += outr;
    }
}

const mp_model mp_poly106 = {
    .name = "Poly 106", .inspired = "Roland Juno-106",
    .nparams = P_COUNT, .params = params,
    .nbanks = 3,
    .bank_names = { "VCF", "DCO", "ENV" },
    .banks = {
        { P_FREQ, P_RES, P_ENV, P_VCF_LFO, P_KYBD, P_HPF, P_LFO_RATE, P_LFO_DELAY },
        { P_RANGE, P_DCO_LFO, P_PWM, P_PWM_MODE, P_PULSE, P_SAW, P_SUB, P_NOISE },
        { P_ATTACK, P_DECAY, P_SUSTAIN, P_RELEASE, P_ENV_POL, P_VCA_MODE, P_LEVEL, P_CHORUS },
    },
    .npresets = sizeof presets / sizeof presets[0], .presets = presets,
    .init = init, .note_on = note_on, .note_off = note_off, .poly_pressure = 0, .all_off = all_off, .render = render,
};
