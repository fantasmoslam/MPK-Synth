/* SPDX-License-Identifier: GPL-3.0-only
 * Poly 106: a six-voice polysynth modelled on the Roland Juno-106.
 *
 * Per voice: one oscillator with sawtooth, pulse (width set by hand or swept by the LFO)
 * and a square sub-oscillator an octave down, plus noise; a 4-position high-pass; a
 * resonant 4-pole low-pass driven by the envelope, the LFO and the keyboard; and a VCA
 * on the envelope or a plain gate. One envelope (ADSR) shapes both filter and VCA, as on
 * the original. One LFO with a delayed fade-in is shared by all voices. The voices are
 * summed in mono and widened by the two-speed stereo chorus.
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
    [P_PWM] = {"PWM", "DCO", 0, 127, 0, MP_FMT_NUMBER, MP_UI_SLIDER, 0},
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

static const mp_preset presets[] = {
    MP_PRESET("Init", P_FREQ, 80),
    MP_PRESET("Brass Section", P_PULSE, 0, P_FREQ, 46, P_RES, 14, P_ENV, 72, P_ATTACK, 24, P_DECAY, 62, P_SUSTAIN, 70, P_RELEASE, 30, P_CHORUS, 1),
    MP_PRESET("Warm Pad", P_PWM, 64, P_LFO_RATE, 38, P_FREQ, 58, P_RES, 10, P_ENV, 22, P_ATTACK, 72, P_DECAY, 80, P_SUSTAIN, 110, P_RELEASE, 82, P_CHORUS, 2),
    MP_PRESET("Rave Hoover", P_RANGE, 0, P_PWM, 110, P_LFO_RATE, 52, P_FREQ, 88, P_ENV, 30, P_DECAY, 70, P_SUSTAIN, 100, P_RELEASE, 40, P_CHORUS, 3, P_SUB, 60),
    MP_PRESET("Funky Bass", P_RANGE, 0, P_PULSE, 0, P_SUB, 100, P_FREQ, 28, P_RES, 44, P_ENV, 74, P_DECAY, 44, P_SUSTAIN, 12, P_RELEASE, 12, P_CHORUS, 0, P_VELOCITY, 60),
    MP_PRESET("Strings", P_PULSE, 0, P_HPF, 2, P_FREQ, 76, P_ENV, 0, P_ATTACK, 50, P_DECAY, 60, P_SUSTAIN, 127, P_RELEASE, 62, P_CHORUS, 2, P_DCO_LFO, 8, P_LFO_DELAY, 70, P_LFO_RATE, 60),
    MP_PRESET("Square Lead", P_SAW, 0, P_PWM, 40, P_PWM_MODE, 1, P_FREQ, 72, P_RES, 40, P_ENV, 30, P_DCO_LFO, 16, P_LFO_DELAY, 80, P_LFO_RATE, 62, P_CHORUS, 0, P_RELEASE, 20),
    MP_PRESET("Glass Keys", P_SAW, 0, P_PWM, 112, P_PWM_MODE, 1, P_RANGE, 2, P_FREQ, 92, P_ENV, 0, P_DECAY, 74, P_SUSTAIN, 0, P_RELEASE, 64, P_CHORUS, 1),
    MP_PRESET("Sub Bass", P_RANGE, 0, P_PULSE, 0, P_SAW, 0, P_SUB, 127, P_FREQ, 40, P_ENV, 10, P_DECAY, 30, P_SUSTAIN, 100, P_RELEASE, 10, P_CHORUS, 0),
    MP_PRESET("Wobble Bass", P_RANGE, 0, P_PULSE, 0, P_FREQ, 30, P_RES, 72, P_ENV, 10, P_VCF_LFO, 84, P_LFO_RATE, 70, P_SUB, 70, P_CHORUS, 0, P_SUSTAIN, 127),
    MP_PRESET("Poly Sync Pluck", P_FREQ, 34, P_RES, 58, P_ENV, 96, P_DECAY, 40, P_SUSTAIN, 0, P_RELEASE, 40, P_CHORUS, 1, P_VELOCITY, 70),
    MP_PRESET("Organ", P_SAW, 0, P_PWM, 0, P_PWM_MODE, 1, P_SUB, 90, P_FREQ, 100, P_ENV, 0, P_SUSTAIN, 127, P_RELEASE, 8, P_VCA_MODE, 1, P_CHORUS, 3, P_LEVEL, 80),
};

#define VOICES 6
#define CHORUS_LEN 2048
typedef struct {
    int note, gate;          /* note -1 when free */
    unsigned age;
    float phase, sub, vel, gate_amp;
    mp_env env;
    mp_ladder vcf;
    mp_onepole hpf;
} voice;
typedef struct {
    float rate;
    voice v[VOICES];
    unsigned clock;
    mp_noise noise;
    float lfo_phase, lfo_fade;
    int held;
    float chorus[CHORUS_LEN];
    unsigned cw;
    float cl_phase;
    mp_onepole wet;
    mp_onepole bass;         /* HPF position 0: a gentle bass boost */
} state;

static float slider(const int16_t *p, int id) { return p[id] / 127.0f; }
/* 1.5 ms .. max seconds; a cubic curve gives the fine control at the short end that the
 * original's sliders have. */
static float seconds(const int16_t *p, int id, float max) { float t = slider(p, id); return 0.0015f + (max - 0.0015f) * t * t * t; }

static void init(void *mem, float rate) {
    state *s = mem; memset(s, 0, sizeof *s);
    s->rate = rate; s->noise.s = 22222u;
    for (int i = 0; i < VOICES; ++i) s->v[i].note = -1;
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
    if (!s->held) s->lfo_fade = 0;   /* the LFO delay restarts when you play after a pause */
    if (v->note < 0) { memset(&v->vcf, 0, sizeof v->vcf); v->hpf.z = 0; v->env.y = 0; v->gate_amp = 0; }
    v->note = note; v->gate = 1; v->age = ++s->clock;
    float sens = slider(p, P_VELOCITY);
    v->vel = 1.0f - sens + sens * (velocity / 127.0f);
    mp_env_gate(&v->env, 1);
    ++s->held;
}
static void note_off(void *mem, const int16_t *p, int note) {
    (void)p; state *s = mem;
    for (int i = 0; i < VOICES; ++i) if (s->v[i].note == note && s->v[i].gate) {
        s->v[i].gate = 0; mp_env_gate(&s->v[i].env, 0); if (s->held > 0) --s->held;
    }
}
static void all_off(void *mem) {
    state *s = mem;
    for (int i = 0; i < VOICES; ++i) { s->v[i].note = -1; s->v[i].gate = 0; s->v[i].env.stage = MP_ENV_IDLE; s->v[i].env.y = 0; }
    s->held = 0;
}

static void render(void *mem, const int16_t *p, const mp_perf *perf, float *l, float *r, unsigned frames) {
    state *s = mem;
    const float rate = s->rate;
    /* Controls, once per block. */
    float lfo_hz = mp_expmap(slider(p, P_LFO_RATE), 0.1f, 22.0f);
    float lfo_delay = slider(p, P_LFO_DELAY) * 3.0f;
    float range = (float)(p[P_RANGE] - 1) * 12.0f;
    float vib = slider(p, P_DCO_LFO); vib = vib * vib * 3.0f;              /* semitones */
    float pwm = slider(p, P_PWM);
    int pwm_lfo = p[P_PWM_MODE] == 0, pulse_on = p[P_PULSE] != 0, saw_on = p[P_SAW] != 0;
    float sub = slider(p, P_SUB) * 0.8f, noise = slider(p, P_NOISE) * 0.6f;
    static const float hpf_hz[4] = { 0, 20, 240, 720 };
    int hpf = p[P_HPF];
    float cutoff_oct = slider(p, P_FREQ) * 9.6f;                            /* above 24 Hz */
    float res = slider(p, P_RES); res = res * 0.98f;
    float env_amt = slider(p, P_ENV) * 8.0f * (p[P_ENV_POL] ? -1.0f : 1.0f);  /* octaves */
    float vcf_lfo = slider(p, P_VCF_LFO) * 3.0f;
    float kybd = slider(p, P_KYBD);
    int gate_mode = p[P_VCA_MODE] != 0;
    float level = slider(p, P_LEVEL);
    float a = seconds(p, P_ATTACK, 3.0f), d = seconds(p, P_DECAY, 12.0f), su = slider(p, P_SUSTAIN), re = seconds(p, P_RELEASE, 12.0f);
    float bend = perf->bend;
    int mix_count = pulse_on + saw_on;
    float osc_gain = mix_count == 2 ? 0.5f : 0.7f;
    for (int i = 0; i < VOICES; ++i) if (hpf > 0) mp_onepole_set(&s->v[i].hpf, hpf_hz[hpf], rate);
    /* Chorus speeds and depths (ms), after the original's three modes. */
    static const float ch_hz[4] = { 0, 0.513f, 0.863f, 9.75f }, ch_depth[4] = { 0, 1.85f, 1.85f, 0.25f }, ch_mid[4] = { 0, 3.5f, 3.5f, 3.3f };
    int chorus = p[P_CHORUS];

    for (unsigned n = 0; n < frames; ++n) {
        /* The LFO: a triangle, faded in after the delay. */
        s->lfo_phase += lfo_hz / rate; if (s->lfo_phase >= 1) s->lfo_phase -= 1;
        float lfo = mp_tri(s->lfo_phase);
        if (s->lfo_fade < 1) s->lfo_fade = lfo_delay <= 0 ? 1 : fminf(1, s->lfo_fade + 1.0f / (lfo_delay * rate));
        float lfo_f = lfo * s->lfo_fade;
        float width = pwm_lfo ? 0.5f - 0.44f * pwm * (0.5f + 0.5f * lfo) : 0.5f - 0.44f * pwm;
        float mono = 0;
        for (int i = 0; i < VOICES; ++i) {
            voice *v = &s->v[i];
            if (v->note < 0) continue;
            float e = mp_env_run(&v->env, a, d, su, re, rate);
            float target = gate_mode ? (v->gate ? 1.0f : 0.0f) : e;
            v->gate_amp += (target - v->gate_amp) * (gate_mode ? 0.01f : 1.0f);
            if (!v->gate && v->env.stage == MP_ENV_IDLE && v->gate_amp < 1e-4f) { v->note = -1; continue; }
            float pitch = v->note + range + bend + vib * lfo_f;
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
            if (hpf > 0) x = mp_hp1(&v->hpf, x);
            float oct = cutoff_oct + env_amt * e + vcf_lfo * lfo_f + kybd * (v->note - 60) / 12.0f;
            float fc = 24.0f * exp2f(mp_clampf(oct, 0, 10.0f));
            float y = mp_ladder_run(&v->vcf, x * 0.6f, fc, res, 1.0f, rate);
            mono += y * v->gate_amp * v->vel;
        }
        mono *= level * 0.55f;
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
