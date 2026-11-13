/* SPDX-License-Identifier: GPL-3.0-only
 * Kit: an analog-style drum machine for the MPK's 16 pads (notes 36-51, the General MIDI
 * drum map): kick, side stick, two snares, clap, five toms, closed, pedal and open hats,
 * crash and ride. Every sound is synthesised the way the classic drum machines did it:
 * sine drums with a falling pitch, filtered noise, and six detuned square waves for the
 * metal. Each pad has its own tune, decay, tone and level; closed and pedal hats cut off
 * the open hat.
 */
#include "model.h"
#include "dsp.h"
#include <string.h>

#define PADS 16
#define PAD_LOW 36
enum { KICK, RIM, SNARE, CLAP, SNARE2, TOM, HAT_C, HAT_P, HAT_O, CRASH, RIDE };
/* Per pad (by note from 36): what it is, its base pitch in Hz and its pan (-1..1). */
static const struct { int kind; float hz, pan; const char *name; } pad[PADS] = {
    { KICK, 52, 0, "KICK" }, { RIM, 1700, -0.1f, "STICK" }, { SNARE, 185, 0.05f, "SNARE" }, { CLAP, 1150, -0.05f, "CLAP" },
    { SNARE2, 215, 0.05f, "SNARE 2" }, { TOM, 82, -0.45f, "TOM 1" }, { HAT_C, 1, 0.35f, "HAT" }, { TOM, 98, -0.3f, "TOM 2" },
    { HAT_P, 1, 0.35f, "PEDAL" }, { TOM, 116, -0.15f, "TOM 3" }, { HAT_O, 1, 0.35f, "OPEN HAT" }, { TOM, 138, 0.05f, "TOM 4" },
    { TOM, 164, 0.2f, "TOM 5" }, { CRASH, 1, -0.4f, "CRASH" }, { TOM, 196, 0.35f, "TOM 6" }, { RIDE, 1, 0.45f, "RIDE" },
};

/* Parameters: four kit-wide, then tune, decay, tone, level for each pad. */
enum { K_LEVEL, K_DRIVE, K_VELOCITY, K_CHOKE, K_PADS };
#define PAD_PARAM(padi, k) (K_PADS + (padi) * 4 + (k))
#define P_COUNT (K_PADS + PADS * 4)

#define PADP(n, s) \
    {"TUNE", s, 0, 127, 64, MP_FMT_BIPOLAR, MP_UI_KNOB, 0}, {"DECAY", s, 0, 127, 64, MP_FMT_NUMBER, MP_UI_KNOB, 0}, \
    {"TONE", s, 0, 127, 64, MP_FMT_NUMBER, MP_UI_KNOB, 0}, {"LEVEL", s, 0, 127, 100, MP_FMT_NUMBER, MP_UI_KNOB, 0}
static const mp_param params[P_COUNT] = {
    {"LEVEL", "KIT", 0, 127, 100, MP_FMT_NUMBER, MP_UI_KNOB, 0},
    {"DRIVE", "KIT", 0, 127, 20, MP_FMT_NUMBER, MP_UI_KNOB, 0},
    {"VELOCITY", "KIT", 0, 127, 90, MP_FMT_NUMBER, MP_UI_KNOB, 0},
    {"HAT CHOKE", "KIT", 0, 1, 1, MP_FMT_SWITCH, MP_UI_SWITCH, "OFF|ON"},
    PADP(0, "KICK"), PADP(1, "STICK"), PADP(2, "SNARE"), PADP(3, "CLAP"), PADP(4, "SNARE 2"), PADP(5, "TOM 1"),
    PADP(6, "HAT"), PADP(7, "TOM 2"), PADP(8, "PEDAL"), PADP(9, "TOM 3"), PADP(10, "OPEN HAT"), PADP(11, "TOM 4"),
    PADP(12, "TOM 5"), PADP(13, "CRASH"), PADP(14, "TOM 6"), PADP(15, "RIDE"),
};

static const mp_preset presets[] = {
    MP_PRESET("Classic", K_LEVEL, 100),
    MP_PRESET("Boom Kit", PAD_PARAM(0, 0), 52, PAD_PARAM(0, 1), 100, PAD_PARAM(0, 2), 40, PAD_PARAM(2, 1), 76, K_DRIVE, 40),
    MP_PRESET("Tight Kit", PAD_PARAM(0, 1), 40, PAD_PARAM(2, 0), 76, PAD_PARAM(2, 1), 44, PAD_PARAM(2, 2), 90, PAD_PARAM(10, 1), 44, K_DRIVE, 10),
    MP_PRESET("Dirty Kit", K_DRIVE, 110, PAD_PARAM(0, 2), 90, PAD_PARAM(3, 1), 90),
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
    float t, amp, vel;          /* seconds since hit */
    float ph[6], ph2;
    svf f1, f2;
    mp_onepole hp;
} drum;
typedef struct {
    float rate;
    drum d[PADS];
    mp_noise noise;
} state;

static float knob(const int16_t *p, int i) { return p[i] / 127.0f; }

static void init(void *mem, float rate) {
    state *s = mem; memset(s, 0, sizeof *s); s->rate = rate; s->noise.s = 7919u;
}
static void note_on(void *mem, const int16_t *p, int note, int velocity) {
    state *s = mem; int i = note - PAD_LOW;
    if (i < 0 || i >= PADS) return;
    drum *d = &s->d[i];
    memset(d, 0, sizeof *d);
    float sens = knob(p, K_VELOCITY);
    d->on = 1; d->vel = 1.0f - sens + sens * (velocity / 127.0f);
    /* Closed and pedal hats cut off the open hat, as on the machines. */
    if (p[K_CHOKE] && (pad[i].kind == HAT_C || pad[i].kind == HAT_P)) for (int k = 0; k < PADS; ++k) if (pad[k].kind == HAT_O) s->d[k].on = 0;
}
static void note_off(void *mem, const int16_t *p, int note) { (void)mem; (void)p; (void)note; }   /* drums ring out */
static void all_off(void *mem) { state *s = mem; for (int i = 0; i < PADS; ++i) s->d[i].on = 0; }

/* Six square waves at the classic machine's metallic ratios. */
static float metal(drum *d, float scale, float rate) {
    static const float f[6] = { 205.3f, 304.4f, 369.6f, 522.7f, 540.0f, 800.0f };
    float x = 0;
    for (int k = 0; k < 6; ++k) { d->ph[k] += f[k] * scale / rate; if (d->ph[k] >= 1) d->ph[k] -= 1; x += d->ph[k] < 0.5f ? 1.0f : -1.0f; }
    return x / 6.0f;
}

static float play(state *s, int i, drum *d, const int16_t *p) {
    const float rate = s->rate, t = d->t;
    float tune = exp2f((knob(p, PAD_PARAM(i, 0)) - 0.5f) * 2.0f);              /* +-1 octave */
    float decay = exp2f((knob(p, PAD_PARAM(i, 1)) - 0.5f) * 4.0f);             /* x0.25 .. x4 */
    float tone = knob(p, PAD_PARAM(i, 2));
    float hz = pad[i].hz * tune, y = 0;
    switch (pad[i].kind) {
    case KICK: {
        float f = hz * (1.0f + 2.5f * expf(-t / 0.035f));
        d->ph2 += f / rate; if (d->ph2 >= 1) d->ph2 -= 1;
        float body = sinf(MP_TAU * d->ph2) * expf(-t / (0.35f * decay));
        float click = t < 0.004f ? mp_white(&s->noise) * (1.0f - t / 0.004f) * tone : 0;
        y = body + click * 0.6f;
        if (t > 3.0f * decay) d->on = 0;
        break;
    }
    case RIM: {
        d->ph2 += hz / rate; if (d->ph2 >= 1) d->ph2 -= 1;
        y = (sinf(MP_TAU * d->ph2) * 0.6f + svf_bp(&d->f1, mp_white(&s->noise), 2600 + 2000 * tone, 4, rate)) * expf(-t / (0.012f * decay));
        if (t > 0.2f * decay) d->on = 0;
        break;
    }
    case SNARE: case SNARE2: {
        d->ph[0] += hz / rate; if (d->ph[0] >= 1) d->ph[0] -= 1;
        d->ph[1] += hz * 1.78f / rate; if (d->ph[1] >= 1) d->ph[1] -= 1;
        float body = (sinf(MP_TAU * d->ph[0]) + 0.6f * sinf(MP_TAU * d->ph[1])) * expf(-t / 0.09f);
        mp_onepole_set(&d->hp, 900 + 5000 * tone, rate);
        float snappy = mp_hp1(&d->hp, mp_white(&s->noise)) * expf(-t / ((pad[i].kind == SNARE2 ? 0.14f : 0.2f) * decay));
        y = body * (1.1f - tone * 0.6f) + snappy * (0.6f + tone * 0.6f);
        if (t > 1.5f * decay) d->on = 0;
        break;
    }
    case CLAP: {
        /* Three quick bursts, then the tail. */
        float burst = t < 0.03f ? expf(-fmodf(t, 0.01f) / 0.003f) : expf(-(t - 0.03f) / (0.18f * decay));
        y = svf_bp(&d->f1, mp_white(&s->noise), hz * (0.7f + tone * 0.8f), 2.2f, rate) * burst * 1.6f;
        if (t > 1.5f * decay) d->on = 0;
        break;
    }
    case TOM: {
        float f = hz * (1.0f + 0.45f * expf(-t / 0.08f));
        d->ph2 += f / rate; if (d->ph2 >= 1) d->ph2 -= 1;
        y = sinf(MP_TAU * d->ph2) * expf(-t / (0.32f * decay)) + mp_white(&s->noise) * expf(-t / 0.01f) * 0.15f * tone;
        if (t > 2.5f * decay) d->on = 0;
        break;
    }
    case HAT_C: case HAT_P: case HAT_O: case CRASH: case RIDE: {
        float m = metal(d, tune, rate);
        float bp = pad[i].kind == RIDE ? 4200.0f : 8500.0f;
        float x = svf_bp(&d->f1, m + (pad[i].kind == CRASH ? mp_white(&s->noise) * 0.4f : 0), bp * (0.7f + tone * 0.6f), 1.2f, rate);
        mp_onepole_set(&d->hp, pad[i].kind == RIDE ? 2500.0f : 6000.0f, rate);
        x = mp_hp1(&d->hp, x);
        float len = pad[i].kind == HAT_C ? 0.045f : pad[i].kind == HAT_P ? 0.03f : pad[i].kind == HAT_O ? 0.38f : pad[i].kind == CRASH ? 1.4f : 1.1f;
        y = x * expf(-t / (len * decay)) * (pad[i].kind == CRASH ? 2.2f : 4.5f);
        if (pad[i].kind == RIDE) { d->ph2 += 620 * tune / rate; if (d->ph2 >= 1) d->ph2 -= 1; y += sinf(MP_TAU * d->ph2) * 0.15f * expf(-t / (0.5f * decay)); }
        if (t > 6.0f * len * decay) d->on = 0;
        break;
    }
    default: d->on = 0;
    }
    d->t += 1.0f / rate;
    return y * d->vel * knob(p, PAD_PARAM(i, 3));
}

static void render(void *mem, const int16_t *p, const mp_perf *perf, float *l, float *r, unsigned frames) {
    (void)perf; state *s = mem;
    float level = knob(p, K_LEVEL) * 0.7f, drive = 1.0f + knob(p, K_DRIVE) * 6.0f;
    for (int i = 0; i < PADS; ++i) {
        drum *d = &s->d[i];
        if (!d->on) continue;
        float pl = sqrtf(0.5f * (1.0f - pad[i].pan)), pr = sqrtf(0.5f * (1.0f + pad[i].pan));
        for (unsigned n = 0; n < frames && d->on; ++n) {
            float y = mp_sat(play(s, i, d, p) * drive) / drive * level * 1.2f;
            l[n] += y * pl; r[n] += y * pr;
        }
    }
}

const mp_model mp_kit = {
    .name = "Kit", .inspired = "classic analog drum machines",
    .nparams = P_COUNT, .params = params,
    /* 100 + k: parameter k (tune, decay, tone, level) of the pad you hit last. */
    .nbanks = 1,
    .bank_names = { "PAD" },
    .banks = { { 100, 101, 102, 103, K_LEVEL, K_DRIVE, K_VELOCITY, -1 } },
    .npresets = sizeof presets / sizeof presets[0], .presets = presets,
    .init = init, .note_on = note_on, .note_off = note_off, .poly_pressure = 0, .all_off = all_off, .render = render,
};

/* The name of each pad, for the app. */
const char *mp_kit_pad_name(int i) { return i >= 0 && i < PADS ? pad[i].name : ""; }
