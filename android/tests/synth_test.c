/* SPDX-License-Identifier: GPL-3.0-only
 * The synth core on the desktop: tuning, levels, every preset, the drum kit's pads,
 * sustain, panic, and the part-per-channel routing. */
#include "../native/mpk/engine.h"
#include <assert.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define RATE 48000
static float buf[RATE * 2 * 4];

static void run(float seconds) { mpe_render(buf, (unsigned)(seconds * RATE)); }
static float peak(unsigned frames) { float m = 0; for (unsigned i = 0; i < frames * 2; ++i) { assert(isfinite(buf[i])); m = fmaxf(m, fabsf(buf[i])); } return m; }
static float rms(unsigned from, unsigned frames) { double s = 0; for (unsigned i = from; i < from + frames; ++i) s += (double)buf[2 * i] * buf[2 * i]; return (float)sqrt(s / frames); }
/* The strongest period (autocorrelation) of the left channel, in Hz. */
static float pitch(unsigned from, unsigned frames, float lo, float hi) {
    int best = 0; double bestv = -1e30;
    for (int lag = (int)(RATE / hi); lag <= (int)(RATE / lo); ++lag) {
        double s = 0; for (unsigned i = from; i < from + frames; ++i) s += (double)buf[2 * i] * buf[2 * (i + lag)];
        if (s > bestv) { bestv = s; best = lag; }
    }
    /* Refine between neighbours. */
    double a = 0, b = 0, c = 0;
    for (unsigned i = from; i < from + frames; ++i) { a += buf[2 * i] * buf[2 * (i + best - 1)]; b += buf[2 * i] * buf[2 * (i + best)]; c += buf[2 * i] * buf[2 * (i + best + 1)]; }
    double shift = (a - c) / (2 * (a - 2 * b + c));
    return (float)(RATE / (best + shift));
}
static void preset(unsigned partno, unsigned m, unsigned p) {
    int16_t v[MP_MAX_PARAMS]; const mp_model *model = mpe_model(m);
    mpe_part_model(partno, m); mpe_preset_values(model, p, v);
    for (unsigned k = 0; k < model->nparams; ++k) mpe_param(partno, k, v[k]);
}

int main(void) {
    setvbuf(stdout, NULL, _IONBF, 0);
    mpe_init(RATE);
    assert(mpe_models() >= 2 && !strcmp(mpe_model(0)->name, "Poly 106") && !strcmp(mpe_model(1)->name, "Kit"));
    assert(mpe_part_model_of(0) == 0 && mpe_part_model_of(3) == 1);

    /* Every model's tables are sound: ranges, defaults, banks, presets in range. */
    for (unsigned m = 0; m < mpe_models(); ++m) {
        const mp_model *d = mpe_model(m);
        assert(d->nparams <= MP_MAX_PARAMS && d->nbanks >= 1 && d->npresets >= 1);
        for (unsigned k = 0; k < d->nparams; ++k) assert(d->params[k].min <= d->params[k].def && d->params[k].def <= d->params[k].max);
        for (unsigned b = 0; b < d->nbanks; ++b) for (int k = 0; k < 8; ++k) { int id = d->banks[b][k]; assert(id < 0 || id >= 100 || (unsigned)id < d->nparams); }
        int16_t v[MP_MAX_PARAMS];
        for (unsigned p = 0; p < d->npresets; ++p) {
            mpe_preset_values(d, p, v);
            for (unsigned k = 0; k < d->nparams; ++k) assert(v[k] >= d->params[k].min && v[k] <= d->params[k].max);
        }
    }
    printf("PASS: model tables, banks and presets in range\n");

    /* Poly 106 in tune: A4 on part 1, saw only, filter open, no chorus. */
    preset(0, 0, 0);
    mpe_param(0, (unsigned)mpe_find(mpe_model(0), "PULSE"), 0);
    mpe_param(0, (unsigned)mpe_find(mpe_model(0), "CHORUS"), 0);
    mpe_midi(0x90, 69, 100); run(1.0f);
    float hz = pitch(RATE / 2, 4096, 100, 2000);
    printf("  A4 plays %.2f Hz\n", hz);
    assert(fabsf(hz - 440.0f) < 1.0f);
    /* Bend up 2 semitones (the default range). */
    mpe_midi(0xe0, 127, 127); run(0.5f);
    hz = pitch(RATE / 4, 4096, 100, 2000);
    assert(fabsf(hz - 440.0f * powf(2, 2.0f * 8191 / 8192 / 12)) < 1.5f);
    mpe_midi(0xe0, 0, 64);
    /* Range 16' is an octave down. */
    mpe_param(0, (unsigned)mpe_find(mpe_model(0), "RANGE"), 0); run(0.5f);
    hz = pitch(RATE / 4, 4096, 100, 2000);
    assert(fabsf(hz - 220.0f) < 1.0f);
    mpe_midi(0x80, 69, 0); run(3.0f);
    assert(rms(RATE * 2, RATE / 2) < 1e-4f);
    printf("PASS: Poly 106 in tune, bends 2 semitones, 16' an octave down, silent after release\n");

    /* Every preset: a full six-note chord at full velocity stays finite and below clipping
     * (the limiter catches the rest), sounds, and ends after release. */
    for (unsigned p = 0; p < mpe_model(0)->npresets; ++p) {
        mpe_panic(); preset(0, 0, p);
        static const int chord[6] = { 36, 48, 55, 60, 64, 67 };
        for (int k = 0; k < 6; ++k) mpe_midi(0x90, (unsigned)chord[k], 127);
        run(1.5f);
        float pk = peak(RATE * 3 / 2), loud = rms(RATE / 10, RATE / 5);
        printf("  %-16s peak %.2f, rms %.3f\n", mpe_model(0)->presets[p].name, pk, loud);
        assert(pk < 1.6f && loud > 0.01f);
        for (int k = 0; k < 6; ++k) mpe_midi(0x80, (unsigned)chord[k], 0);
        run(4.0f); run(4.0f);
        assert(rms(RATE * 3, RATE) < 1e-3f);
    }
    printf("PASS: every Poly 106 preset sounds, stays below clipping and ends after release\n");

    /* Six voices: a seventh note steals one, nothing breaks. */
    mpe_panic(); preset(0, 0, 0);
    for (int n = 0; n < 12; ++n) mpe_midi(0x90, (unsigned)(48 + n), 100);
    run(0.5f); assert(peak(RATE / 2) < 1.6f);
    mpe_panic(); run(0.2f); run(0.5f); assert(rms(0, RATE / 2) < 1e-4f);
    printf("PASS: voice stealing and panic\n");

    /* Sustain: a released note keeps sounding until the pedal lifts. */
    mpe_midi(0xb0, 64, 127); mpe_midi(0x90, 60, 100); run(0.2f); mpe_midi(0x80, 60, 0); run(1.0f);
    assert(rms(RATE / 2, RATE / 4) > 0.01f);
    mpe_midi(0xb0, 64, 0); run(3.0f); run(3.0f); assert(rms(RATE * 2, RATE / 2) < 1e-3f);
    printf("PASS: sustain pedal\n");

    /* Channels: part 2 (channel 2) has its own sound; channels 5-16 are ignored. */
    mpe_midi(0x94, 60, 100); run(0.3f); assert(peak(RATE * 3 / 10) == 0);
    preset(1, 0, 2); mpe_midi(0x91, 60, 100); run(0.5f); assert(rms(RATE / 4, RATE / 4) > 0.005f);
    mpe_panic(); run(2.0f);
    printf("PASS: one part per channel 1-4, other channels ignored\n");

    /* The kit: every pad sounds, louder with velocity, and dies away. */
    preset(3, 1, 0);
    for (int i = 0; i < 16; ++i) {
        mpe_midi(0x93, (unsigned)(36 + i), 120); run(0.1f);
        float loud = rms(0, RATE / 20);
        mpe_panic(); run(0.05f);
        mpe_midi(0x93, (unsigned)(36 + i), 30); run(0.1f);
        float soft = rms(0, RATE / 20);
        printf("  pad %2d %-9s rms %.3f (soft %.3f)\n", i + 1, mp_kit_pad_name(i), loud, soft);
        assert(loud > 0.01f && soft < loud && peak(RATE / 10) < 1.6f);
        run(4.0f); run(4.0f);
        assert(rms(RATE * 3, RATE / 2) < 1e-3f);
    }
    /* A closed hat cuts the open hat. */
    mpe_midi(0x93, 46, 120); run(0.05f); mpe_midi(0x93, 42, 120); run(0.3f);
    assert(rms(RATE / 5, RATE / 10) < 1e-3f);
    printf("PASS: all 16 drum pads sound, follow velocity, ring out, and the hat chokes\n");

    /* A rate change keeps the sound in tune. */
    mpe_set_rate(44100); mpe_panic(); preset(0, 0, 0);
    mpe_param(0, (unsigned)mpe_find(mpe_model(0), "PULSE"), 0); mpe_param(0, (unsigned)mpe_find(mpe_model(0), "CHORUS"), 0);
    mpe_midi(0x90, 69, 100); mpe_render(buf, 44100);
    { int best = 0; double bv = -1e30; for (int lag = 60; lag < 200; ++lag) { double s = 0; for (int i = 20000; i < 24096; ++i) s += (double)buf[2 * i] * buf[2 * (i + lag)]; if (s > bv) { bv = s; best = lag; } }
      assert(abs(best - 100) <= 1); }
    printf("PASS: in tune at 44.1 kHz too\n");
    return 0;
}
