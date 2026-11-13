/* SPDX-License-Identifier: GPL-3.0-only
 * Kit: the Roland TR-808's sixteen instruments on the MPK's 16 pads (notes 36-51): bass
 * drum, snare, the three toms and three congas (on the 808 a switch picks toms or congas;
 * here both have pads), rim shot, claves, hand clap, maracas, cowbell, cymbal, and open and
 * closed hi-hat.
 *
 * Each voice is tuned and timed from the frequency and decay chart in Roland's TR-808
 * Service Notes (1981, page 14), and the metal voices from Werner, Abel and Smith's circuit
 * analysis of the 808 cymbal (ICMC 2014). How each control maps, and what is still an
 * approximation, is in docs/sound-sources/kit.md.
 */
#include "model.h"
#include "dsp.h"
#include <string.h>

#define PADS 16
#define PAD_LOW 36
enum { BD, SD, LT, MT, HT, LC, MC, HC, RS, CL, CP, MA, CB, CY, OH, CH };

/* The service notes' chart, per instrument: frequency in Hz at the tuning knob's low, mid
 * (12 o'clock) and high settings, decay time in ms at the decay knob's short, mid and long
 * settings (one value where the 808 has no such knob), and output amplitude in volts
 * peak-to-peak at normal and at full accent. */
typedef struct { float hz[3], hz2, ms[3], vpp, vpp_acc; const char *name; } voice808;
static const voice808 v808[16] = {
    [BD] = { { 56, 56, 56 }, 0, { 50, 300, 800 }, 3.5f, 10, "BASS DRUM" },
    [SD] = { { 238, 238, 238 }, 476, { 60, 60, 60 }, 3, 10, "SNARE" },
    [LT] = { { 80, 90, 100 }, 0, { 200, 200, 200 }, 3.5f, 12, "LOW TOM" },
    [MT] = { { 120, 135, 160 }, 0, { 130, 130, 130 }, 3, 11, "MID TOM" },
    [HT] = { { 165, 185, 220 }, 0, { 100, 100, 100 }, 3.5f, 12, "HI TOM" },
    [LC] = { { 165, 185, 220 }, 0, { 180, 180, 180 }, 3.5f, 12, "LOW CONGA" },
    [MC] = { { 250, 280, 310 }, 0, { 100, 100, 100 }, 3, 10, "MID CONGA" },
    [HC] = { { 370, 400, 455 }, 0, { 80, 80, 80 }, 3.5f, 12, "HI CONGA" },
    [RS] = { { 1667, 1667, 1667 }, 455, { 10, 10, 10 }, 3, 10, "RIM SHOT" },
    [CL] = { { 2500, 2500, 2500 }, 0, { 25, 25, 25 }, 2.5f, 8, "CLAVES" },
    [CP] = { { 0, 0, 0 }, 0, { 100, 100, 100 }, 2, 6, "HAND CLAP" },   /* printed 6, 2: read as swapped */
    [MA] = { { 0, 0, 0 }, 0, { 25, 30, 35 }, 3, 5, "MARACAS" },
    [CB] = { { 540, 540, 540 }, 800, { 50, 50, 50 }, 3.5f, 12, "COWBELL" },
    [CY] = { { 0, 0, 0 }, 0, { 350, 800, 1200 }, 3.5f, 7, "CYMBAL" },
    [OH] = { { 0, 0, 0 }, 0, { 90, 450, 600 }, 3.5f, 7, "OPEN HAT" },
    [CH] = { { 0, 0, 0 }, 0, { 50, 50, 50 }, 3, 6, "CLOSED HAT" },
};
/* Which instrument each pad plays, from note 36, close to the General MIDI drum map. */
static const int pad[PADS] = { BD, RS, SD, CP, CL, LT, CH, MT, MA, HT, OH, LC, MC, CY, HC, CB };

/* Parameters: four kit-wide, then four for each pad. */
enum { K_LEVEL, K_DRIVE, K_VELOCITY, K_CHOKE, K_PADS };
#define PAD_PARAM(padi, k) (K_PADS + (padi) * 4 + (k))
#define P_COUNT (K_PADS + PADS * 4)

#define PADP(s) \
    {"TUNE", s, 0, 127, 64, MP_FMT_BIPOLAR, MP_UI_KNOB, 0}, {"DECAY", s, 0, 127, 64, MP_FMT_NUMBER, MP_UI_KNOB, 0}, \
    {"TONE", s, 0, 127, 64, MP_FMT_NUMBER, MP_UI_KNOB, 0}, {"LEVEL", s, 0, 127, 100, MP_FMT_NUMBER, MP_UI_KNOB, 0}
/* The 808 snare's knobs are TONE and SNAPPY: its second slot is SNAPPY. */
#define PADP_SD(s) \
    {"TUNE", s, 0, 127, 64, MP_FMT_BIPOLAR, MP_UI_KNOB, 0}, {"SNAPPY", s, 0, 127, 64, MP_FMT_NUMBER, MP_UI_KNOB, 0}, \
    {"TONE", s, 0, 127, 64, MP_FMT_NUMBER, MP_UI_KNOB, 0}, {"LEVEL", s, 0, 127, 100, MP_FMT_NUMBER, MP_UI_KNOB, 0}
static const mp_param params[P_COUNT] = {
    {"LEVEL", "KIT", 0, 127, 100, MP_FMT_NUMBER, MP_UI_KNOB, 0},
    {"DRIVE", "KIT", 0, 127, 0, MP_FMT_NUMBER, MP_UI_KNOB, 0},
    {"VELOCITY", "KIT", 0, 127, 90, MP_FMT_NUMBER, MP_UI_KNOB, 0},
    {"HAT CHOKE", "KIT", 0, 1, 1, MP_FMT_SWITCH, MP_UI_SWITCH, "OFF|ON"},
    PADP("BASS DRUM"), PADP("RIM SHOT"), PADP_SD("SNARE"), PADP("HAND CLAP"), PADP("CLAVES"), PADP("LOW TOM"),
    PADP("CLOSED HAT"), PADP("MID TOM"), PADP("MARACAS"), PADP("HI TOM"), PADP("OPEN HAT"), PADP("LOW CONGA"),
    PADP("MID CONGA"), PADP("CYMBAL"), PADP("HI CONGA"), PADP("COWBELL"),
};

/* Every knob at 12 o'clock is the service notes' reference setting. The other two kits are
 * its SHORT and LONG decay settings for the three voices that have a DECAY knob. */
#define DECAYS(v) PAD_PARAM(0, 1), v, PAD_PARAM(13, 1), v, PAD_PARAM(10, 1), v
static const mp_preset presets[] = {
    MP_PRESET("TR-808", K_LEVEL, 100),
    MP_PRESET("TR-808 short decays", DECAYS(0)),
    MP_PRESET("TR-808 long decays", DECAYS(127)),
};

/* A state-variable band-pass. */
typedef struct { float lp, bp; } svf;
static float svf_bp(svf *f, float x, float hz, float q, float rate) {
    float g = 2.0f * sinf(MP_PI * mp_clampf(hz, 20, rate * 0.24f) / rate);
    float hp = x - f->lp - f->bp / q;
    f->bp += g * hp; f->lp += g * f->bp;
    return f->bp;
}

typedef struct {
    int on;
    float t, amp;               /* seconds since hit; volts / 10 */
    float ph[6], ph2;
    svf f1, f2;
    mp_onepole hp, lp;
} drum;
typedef struct {
    float rate;
    drum d[PADS];
    mp_noise noise;
} state;

static float knob(const int16_t *p, int i) { return p[i] / 127.0f; }
/* A knob through the chart's three points (low / 12 o'clock / high), log-interpolated. */
static float chart(const float v[3], float k) {
    if (v[0] <= 0) return v[1];
    float a = k < 0.5f ? v[0] : v[1], b = k < 0.5f ? v[1] : v[2], t = k < 0.5f ? k * 2 : k * 2 - 1;
    return a * powf(b / a, t);
}
/* Exponential fall to -20 dB (a tenth) in the chart's decay time. */
static float fall(float t, float ms) { return expf(-t * 2.302585f / (ms * 0.001f)); }

static void init(void *mem, float rate) {
    state *s = mem; memset(s, 0, sizeof *s); s->rate = rate; s->noise.s = 7919u;
}
static void note_on(void *mem, const int16_t *p, int note, int velocity) {
    state *s = mem; int i = note - PAD_LOW;
    if (i < 0 || i >= PADS) return;
    drum *d = &s->d[i];
    memset(d, 0, sizeof *d);
    const voice808 *v = &v808[pad[i]];
    /* Velocity plays the 808's accent: from the chart's normal amplitude up to its accent
     * amplitude; VELOCITY sets how much the pads' velocity counts. */
    float acc = knob(p, K_VELOCITY) * (velocity / 127.0f) + (1.0f - knob(p, K_VELOCITY)) * 0.5f;
    d->on = 1; d->amp = (v->vpp + (v->vpp_acc - v->vpp) * acc) / 10.0f;
    /* The closed hat cuts the open hat, as on the 808 (they share one envelope circuit). */
    if (p[K_CHOKE] && pad[i] == CH) for (int k = 0; k < PADS; ++k) if (pad[k] == OH) s->d[k].on = 0;
}
static void note_off(void *mem, const int16_t *p, int note) { (void)mem; (void)p; (void)note; }   /* drums ring out */
static void all_off(void *mem) { state *s = mem; for (int i = 0; i < PADS; ++i) s->d[i].on = 0; }

/* The six square-wave oscillators shared by cymbal, hats and (two of them) cowbell. */
static float metal(drum *d, float scale, float rate) {
    static const float f[6] = { 205.3f, 304.4f, 369.6f, 522.7f, 540.0f, 800.0f };
    float x = 0;
    for (int k = 0; k < 6; ++k) { d->ph[k] += f[k] * scale / rate; if (d->ph[k] >= 1) d->ph[k] -= 1; x += d->ph[k] < 0.4798f ? 1.0f : -1.0f; }
    return x / 6.0f;
}
static float sine(float *ph, float hz, float rate) { *ph += hz / rate; if (*ph >= 1) *ph -= 1; return sinf(MP_TAU * *ph); }

static float play(state *s, int i, drum *d, const int16_t *p) {
    const float rate = s->rate, t = d->t;
    const int kind = pad[i];
    const voice808 *v = &v808[kind];
    float kt = knob(p, PAD_PARAM(i, 0)), kd = knob(p, PAD_PARAM(i, 1)), tone = knob(p, PAD_PARAM(i, 2));
    /* TUNE: through the chart's tuning range where the 808 has one, else +-1 octave. */
    int tunable = v->hz[0] != v->hz[2];
    float detune = tunable ? 1.0f : exp2f((kt - 0.5f) * 2.0f);
    float hz = chart(v->hz, kt) * detune;
    /* DECAY: through the chart's short/mid/long where the 808 has a knob, else x0.5..x2. */
    int has_decay = v->ms[0] != v->ms[2];
    float ms = has_decay ? chart(v->ms, kd) : v->ms[1] * exp2f((kd - 0.5f) * 2.0f);
    float y = 0, len = ms * 0.003f;    /* silent (-60 dB) after three decay times */
    switch (kind) {
    case BD: {
        /* A bridged-T ringing at 56 Hz; the trigger pulls it more than an octave up for
         * its first few ms. TONE opens the low-pass on the 1 ms trigger click. */
        float f = hz * (1.0f + 1.2f * expf(-t / 0.003f));
        d->ph2 += f / rate; if (d->ph2 >= 1) d->ph2 -= 1;
        float click = t < 0.001f ? 1.0f : 0.0f;
        mp_onepole_set(&d->lp, 200.0f + 4000.0f * tone * tone, rate);
        y = sinf(MP_TAU * d->ph2) * fall(t, ms) + mp_lp1(&d->lp, click) * 0.8f;
        break;
    }
    case SD: {
        /* Two bridged-T resonators, 238 and 476 Hz, and high-passed noise (SNAPPY). */
        float body = (sine(&d->ph[0], hz, rate) + 0.7f * sine(&d->ph[1], v->hz2 * detune, rate)) * fall(t, ms);
        mp_onepole_set(&d->hp, 1000.0f + 6000.0f * tone, rate);
        float snap = mp_hp1(&d->hp, mp_white(&s->noise)) * fall(t, 180.0f) * kd * 1.6f;
        y = body * 0.8f + snap;
        len = 0.6f;
        break;
    }
    case LT: case MT: case HT: case LC: case MC: case HC:
        /* Bridged-T resonators; TONE (not on the 808) adds a little noise to the toms. */
        y = sine(&d->ph2, hz, rate) * fall(t, ms);
        if (kind <= HT) y += mp_white(&s->noise) * fall(t, 15.0f) * 0.2f * tone;
        break;
    case RS:
        y = (sine(&d->ph[0], hz, rate) * 0.6f + sine(&d->ph[1], v->hz2 * detune, rate)) * fall(t, ms) * 0.7f;
        break;
    case CL:
        y = sine(&d->ph2, hz, rate) * fall(t, ms);
        break;
    case CP: {
        /* Band-passed noise: three quick bursts inside the 808's 30 ms pulse, then the tail. */
        float burst = t < 0.03f ? expf(-fmodf(t, 0.01f) / 0.003f) : fall(t - 0.03f, ms);
        y = svf_bp(&d->f1, mp_white(&s->noise), 800.0f + 800.0f * tone, 2.2f, rate) * burst * 1.6f;
        len = 0.03f + ms * 0.003f;
        break;
    }
    case MA:
        mp_onepole_set(&d->hp, 4000.0f + 4000.0f * tone, rate);
        y = mp_hp1(&d->hp, mp_white(&s->noise)) * fall(t, ms) * 1.4f;
        break;
    case CB: {
        /* The 540 Hz and 800 Hz squares through a band-pass. */
        d->ph[0] += hz / rate; if (d->ph[0] >= 1) d->ph[0] -= 1;
        d->ph[1] += v->hz2 * detune / rate; if (d->ph[1] >= 1) d->ph[1] -= 1;
        float sq = (d->ph[0] < 0.4798f ? 1.0f : -1.0f) + (d->ph[1] < 0.4798f ? 1.0f : -1.0f);
        y = svf_bp(&d->f1, sq * 0.5f, 2640.0f * (0.7f + 0.6f * tone), 2.5f, rate) * fall(t, ms) * 1.5f;
        break;
    }
    case CY: case OH: case CH: {
        float m = metal(d, detune, rate);
        if (kind == CY) {
            /* Bands around 3440 Hz and 7100 Hz; TONE sets how much of the upper band. */
            float lo = svf_bp(&d->f1, m, 3440.0f, 3.0f, rate), hi = svf_bp(&d->f2, m, 7100.0f, 3.0f, rate);
            mp_onepole_set(&d->hp, 3000.0f, rate);
            y = mp_hp1(&d->hp, lo * (1.2f - 0.8f * tone) + hi * (0.4f + 1.2f * tone)) * fall(t, ms) * 3.0f;
        } else {
            float x = svf_bp(&d->f1, m, 7100.0f * (0.8f + 0.4f * tone), 1.5f, rate);
            mp_onepole_set(&d->hp, 6000.0f, rate);
            y = mp_hp1(&d->hp, x) * fall(t, ms) * 4.5f;
        }
        break;
    }
    default: d->on = 0;
    }
    if (t > len) d->on = 0;
    d->t += 1.0f / rate;
    return y * d->amp * knob(p, PAD_PARAM(i, 3));
}

static void render(void *mem, const int16_t *p, const mp_perf *perf, float *l, float *r, unsigned frames) {
    (void)perf; state *s = mem;
    /* DRIVE is not on the 808: at 0 (the default) the voices pass clean. */
    float level = knob(p, K_LEVEL) * 0.5f, drive = 1.0f + knob(p, K_DRIVE) * 6.0f;
    for (int i = 0; i < PADS; ++i) {
        drum *d = &s->d[i];
        if (!d->on) continue;
        for (unsigned n = 0; n < frames && d->on; ++n) {
            float y = play(s, i, d, p);
            if (drive > 1.0f) y = mp_sat(y * drive) / drive;
            y *= level;
            l[n] += y; r[n] += y;   /* the 808's mix output is mono */
        }
    }
}

const mp_model mp_kit = {
    .name = "Kit", .inspired = "Roland TR-808",
    .nparams = P_COUNT, .params = params,
    /* 100 + k: parameter k (tune, decay, tone, level) of the pad you hit last. */
    .nbanks = 1,
    .bank_names = { "PAD" },
    .banks = { { 100, 101, 102, 103, K_LEVEL, K_DRIVE, K_VELOCITY, -1 } },
    .npresets = sizeof presets / sizeof presets[0], .presets = presets,
    .init = init, .note_on = note_on, .note_off = note_off, .poly_pressure = 0, .all_off = all_off, .render = render,
};

/* The name of each pad, for the app. */
const char *mp_kit_pad_name(int i) { return i >= 0 && i < PADS ? v808[pad[i]].name : ""; }
