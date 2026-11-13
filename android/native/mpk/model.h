/* SPDX-License-Identifier: GPL-3.0-only
 * A synth model: its controls (each with a range, a default, a panel section and the
 * knob bank it sits in), its presets, and the functions that play it. Each of the four
 * parts runs one model in its own state; the engine (engine.c) owns the parts.
 */
#pragma once
#include <stdint.h>

#define MP_MAX_PARAMS 96
#define MP_BANKS 8

/* How the app shows a value. */
enum { MP_FMT_NUMBER, MP_FMT_LIST, MP_FMT_BIPOLAR, MP_FMT_SWITCH };
/* How the panel draws a control. */
enum { MP_UI_SLIDER, MP_UI_KNOB, MP_UI_SWITCH };

typedef struct {
    const char *label, *section;
    int16_t min, max, def;
    uint8_t fmt, ui;
    /* For MP_FMT_LIST and MP_FMT_SWITCH: the value names joined by '|'. */
    const char *names;
} mp_param;

/* A preset: its name and the values that differ from the defaults, as id, value, ...,
 * ending with -1. */
typedef struct {
    const char *name;
    const int16_t *set;
} mp_preset;
#define MP_PRESET(name, ...) { name, (const int16_t[]){ __VA_ARGS__, -1 } }

/* What a part plays with on each block, besides its parameter values. */
typedef struct {
    float bend;        /* semitones */
    float pressure;    /* channel aftertouch 0..1 */
    float mod;         /* mod wheel 0..1 */
    int sustain;       /* sustain pedal down */
} mp_perf;

typedef struct mp_model {
    const char *name, *inspired;
    unsigned nparams;
    const mp_param *params;
    /* Knob banks: a name and the 8 parameters on the MPK's knobs (-1 = none). */
    unsigned nbanks;
    const char *bank_names[MP_BANKS];
    int8_t banks[MP_BANKS][8];
    unsigned npresets;
    const mp_preset *presets;
    /* The model's state lives in a part's buffer of this many bytes at most. */
    void (*init)(void *state, float rate);
    void (*note_on)(void *state, const int16_t *p, int note, int velocity);
    void (*note_off)(void *state, const int16_t *p, int note);
    /* Polyphonic pressure on one held note (pads), 0..1. */
    void (*poly_pressure)(void *state, int note, float value);
    void (*all_off)(void *state);
    /* Adds stereo output into l and r. */
    void (*render)(void *state, const int16_t *p, const mp_perf *perf, float *l, float *r, unsigned frames);
} mp_model;

#define MP_STATE_BYTES (64 * 1024)

extern const mp_model mp_poly106, mp_kit;
