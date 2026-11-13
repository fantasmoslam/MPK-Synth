/* SPDX-License-Identifier: GPL-3.0-only
 * Small DSP building blocks shared by the synth models: band-limited oscillators
 * (polyBLEP), a resonant 4-pole ladder low-pass, one-pole filters, envelopes and noise.
 * Everything is plain C on floats, allocation-free, safe on the audio thread.
 */
#pragma once
#include <math.h>
#include <stdint.h>

#define MP_PI 3.14159265358979f
#define MP_TAU 6.28318530717959f

static inline float mp_clampf(float x, float lo, float hi) { return x < lo ? lo : x > hi ? hi : x; }
/* MIDI note to Hz (A4 = 440). */
static inline float mp_hz(float note) { return 440.0f * exp2f((note - 69.0f) / 12.0f); }
/* A soft clip that stays close to linear below about 0.5. */
static inline float mp_sat(float x) { float x2 = x * x; return x * (27.0f + x2) / (27.0f + 9.0f * x2); }
/* Map 0..1 exponentially onto lo..hi. */
static inline float mp_expmap(float t, float lo, float hi) { return lo * powf(hi / lo, mp_clampf(t, 0, 1)); }

/* ---- noise ------------------------------------------------------------------------ */
typedef struct { uint32_t s; } mp_noise;
static inline float mp_white(mp_noise *n) { n->s = n->s * 1664525u + 1013904223u; return (float)(int32_t)n->s * (1.0f / 2147483648.0f); }

/* ---- polyBLEP oscillators ----------------------------------------------------------- */
static inline float mp_blep(float t, float dt) {
    if (t < dt) { t /= dt; return t + t - t * t - 1.0f; }
    if (t > 1.0f - dt) { t = (t - 1.0f) / dt; return t * t + t + t + 1.0f; }
    return 0.0f;
}
/* Sawtooth from a phase in [0,1) and its increment. */
static inline float mp_saw(float ph, float dt) { return 2.0f * ph - 1.0f - mp_blep(ph, dt); }
/* Pulse with width w in (0,1). */
static inline float mp_pulse(float ph, float dt, float w) {
    float y = ph < w ? 1.0f : -1.0f;
    y += mp_blep(ph, dt);
    float p2 = ph - w; if (p2 < 0) p2 += 1.0f;
    y -= mp_blep(p2, dt);
    return y;
}
static inline float mp_tri(float ph) { return ph < 0.5f ? 4.0f * ph - 1.0f : 3.0f - 4.0f * ph; }

/* ---- filters ------------------------------------------------------------------------ */
/* One-pole low-pass / high-pass with coefficient from a cutoff. */
typedef struct { float z, a; } mp_onepole;
static inline void mp_onepole_set(mp_onepole *f, float hz, float rate) { f->a = 1.0f - expf(-MP_TAU * mp_clampf(hz, 1.0f, rate * 0.49f) / rate); }
static inline float mp_lp1(mp_onepole *f, float x) { f->z += f->a * (x - f->z); return f->z; }
static inline float mp_hp1(mp_onepole *f, float x) { f->z += f->a * (x - f->z); return x - f->z; }

/* A 4-pole transistor-ladder style low-pass (zero-delay-feedback, saturating feedback
 * path). res 0..1; near 1 it self-oscillates. drive > 1 pushes the input stage. */
typedef struct { float s[4]; } mp_ladder;
static inline float mp_ladder_run(mp_ladder *f, float x, float hz, float res, float drive, float rate) {
    float g = tanf(MP_PI * mp_clampf(hz, 10.0f, rate * 0.45f) / rate);
    float G = g / (1.0f + g);
    float k = res * 4.2f;
    /* Estimate the output from the current state, then solve the feedback loop once. */
    float S = 0, Gp = 1;
    for (int i = 3; i >= 0; --i) { S += f->s[i] * Gp / (1.0f + g); Gp *= G; }
    float G4 = G * G * G * G;
    float u = (x * drive - k * S) / (1.0f + k * G4);
    u = mp_sat(u);
    for (int i = 0; i < 4; ++i) {
        float v = (u - f->s[i]) * G;
        float y = v + f->s[i];
        f->s[i] = y + v;
        u = y;
    }
    /* Ladders lose level as resonance rises; give some back. */
    return u * (1.0f + res * 0.6f);
}

/* ---- envelopes ---------------------------------------------------------------------- */
enum { MP_ENV_IDLE, MP_ENV_ATTACK, MP_ENV_DECAY, MP_ENV_SUSTAIN, MP_ENV_RELEASE };
typedef struct { float y; int stage; } mp_env;
/* Exponential-style segments: attack overshoots toward 1.2 so it ends crisply. Times in s. */
static inline float mp_env_run(mp_env *e, float a, float d, float s, float r, float rate) {
    switch (e->stage) {
    case MP_ENV_ATTACK: {
        float c = 1.0f - expf(-1.0f / (fmaxf(a, 0.0005f) * rate * 0.55f));
        e->y += (1.25f - e->y) * c;
        if (e->y >= 1.0f) { e->y = 1.0f; e->stage = MP_ENV_DECAY; }
        break;
    }
    case MP_ENV_DECAY: {
        float c = 1.0f - expf(-1.0f / (fmaxf(d, 0.0005f) * rate * 0.25f));
        e->y += (s - e->y) * c;
        if (fabsf(e->y - s) < 1e-4f) { e->y = s; e->stage = MP_ENV_SUSTAIN; }
        break;
    }
    case MP_ENV_SUSTAIN: e->y = s; break;
    case MP_ENV_RELEASE: {
        float c = 1.0f - expf(-1.0f / (fmaxf(r, 0.0005f) * rate * 0.25f));
        e->y -= e->y * c;
        if (e->y < 1e-5f) { e->y = 0; e->stage = MP_ENV_IDLE; }
        break;
    }
    default: e->y = 0;
    }
    return e->y;
}
static inline void mp_env_gate(mp_env *e, int on) { e->stage = on ? MP_ENV_ATTACK : (e->stage == MP_ENV_IDLE ? MP_ENV_IDLE : MP_ENV_RELEASE); }
