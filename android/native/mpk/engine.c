/* SPDX-License-Identifier: GPL-3.0-only
 * The synth: four parts, each playing one model on its own MIDI channel (1-4). It takes
 * channel messages, keeps each part's controls, and renders the stereo mix. Called only
 * from the audio thread while audio runs (and from the app's thread while it is stopped).
 */
#include "engine.h"
#include "dsp.h"
#include <string.h>

static const mp_model *const models[] = { &mp_poly106, &mp_kit };
#define NMODELS (sizeof models / sizeof models[0])
_Static_assert(NMODELS <= 32, "model ids fit");

typedef struct {
    const mp_model *m;
    int model, bend_param;
    int16_t p[MP_MAX_PARAMS];
    mp_perf perf;
    int bend_raw;
    uint8_t held[128], sustained[128];
    _Alignas(16) uint8_t state[MP_STATE_BYTES];
} part;
static part parts[MPE_PARTS];
static float rate = 48000;
static float bl[256], br[256];

unsigned mpe_models(void) { return NMODELS; }
const mp_model *mpe_model(unsigned i) { return i < NMODELS ? models[i] : 0; }
int mpe_find(const mp_model *m, const char *label) {
    for (unsigned i = 0; i < m->nparams; ++i) if (!strcmp(m->params[i].label, label)) return (int)i;
    return -1;
}
static void bend_update(part *pt) {
    float range = pt->bend_param >= 0 ? pt->p[pt->bend_param] : 2.0f;
    pt->perf.bend = (pt->bend_raw - 8192) / 8192.0f * range;
}

void mpe_part_model(unsigned i, unsigned model) {
    if (i >= MPE_PARTS || model >= NMODELS) return;
    part *pt = &parts[i];
    pt->m = models[model]; pt->model = (int)model;
    for (unsigned k = 0; k < MP_MAX_PARAMS; ++k) pt->p[k] = k < pt->m->nparams ? pt->m->params[k].def : 0;
    pt->bend_param = mpe_find(pt->m, "BEND");
    memset(pt->held, 0, sizeof pt->held); memset(pt->sustained, 0, sizeof pt->sustained);
    memset(&pt->perf, 0, sizeof pt->perf); pt->bend_raw = 8192;
    pt->m->init(pt->state, rate);
}
int mpe_part_model_of(unsigned i) { return i < MPE_PARTS && parts[i].m ? parts[i].model : -1; }

void mpe_init(float sample_rate) {
    rate = sample_rate > 0 ? sample_rate : 48000;
    /* The defaults: part 1 Poly 106, the pads part (4) the Kit. */
    for (unsigned i = 0; i < MPE_PARTS; ++i) mpe_part_model(i, i == 3 ? 1 : 0);
}
void mpe_set_rate(float sample_rate) {
    if (sample_rate <= 0 || sample_rate == rate) return;
    rate = sample_rate;
    for (unsigned i = 0; i < MPE_PARTS; ++i) if (parts[i].m) parts[i].m->init(parts[i].state, rate);
}

void mpe_param(unsigned i, unsigned id, int value) {
    if (i >= MPE_PARTS || !parts[i].m || id >= parts[i].m->nparams) return;
    const mp_param *d = &parts[i].m->params[id];
    parts[i].p[id] = (int16_t)(value < d->min ? d->min : value > d->max ? d->max : value);
    if ((int)id == parts[i].bend_param) bend_update(&parts[i]);
}
int mpe_get(unsigned i, unsigned id) { return i < MPE_PARTS && id < MP_MAX_PARAMS ? parts[i].p[id] : 0; }
void mpe_preset_values(const mp_model *m, unsigned preset, int16_t *out) {
    for (unsigned k = 0; k < MP_MAX_PARAMS; ++k) out[k] = k < m->nparams ? m->params[k].def : 0;
    if (preset >= m->npresets) return;
    for (const int16_t *s = m->presets[preset].set; s[0] >= 0; s += 2) if ((unsigned)s[0] < m->nparams) out[s[0]] = s[1];
}

static void off(part *pt, int note) {
    if (!pt->held[note]) return;
    pt->held[note] = 0;
    if (pt->perf.sustain) { pt->sustained[note] = 1; return; }
    pt->m->note_off(pt->state, pt->p, note);
}
void mpe_midi(unsigned status, unsigned d1, unsigned d2) {
    unsigned ch = status & 15, type = status & 0xf0;
    if (ch >= MPE_PARTS || !parts[ch].m) return;
    part *pt = &parts[ch];
    d1 &= 127; d2 &= 127;
    switch (type) {
    case 0x90:
        if (d2) { pt->held[d1] = 1; pt->sustained[d1] = 0; pt->m->note_on(pt->state, pt->p, (int)d1, (int)d2); break; }
        /* fall through: velocity 0 is a note off */
    case 0x80: off(pt, (int)d1); break;
    case 0xa0: if (pt->m->poly_pressure) pt->m->poly_pressure(pt->state, (int)d1, d2 / 127.0f); break;
    case 0xb0:
        if (d1 == 1) pt->perf.mod = d2 / 127.0f;
        else if (d1 == 64) {
            int down = d2 >= 64;
            if (pt->perf.sustain && !down) {
                pt->perf.sustain = 0;
                for (int n = 0; n < 128; ++n) if (pt->sustained[n]) { pt->sustained[n] = 0; pt->m->note_off(pt->state, pt->p, n); }
            }
            pt->perf.sustain = down;
        } else if (d1 == 120 || d1 == 123) {
            memset(pt->held, 0, sizeof pt->held); memset(pt->sustained, 0, sizeof pt->sustained);
            pt->perf.sustain = 0; pt->m->all_off(pt->state);
        }
        break;
    case 0xd0: pt->perf.pressure = d1 / 127.0f; break;
    case 0xe0: pt->bend_raw = (int)(d1 | d2 << 7); bend_update(pt); break;
    default: break;
    }
}
void mpe_panic(void) {
    for (unsigned i = 0; i < MPE_PARTS; ++i) if (parts[i].m) {
        part *pt = &parts[i];
        memset(pt->held, 0, sizeof pt->held); memset(pt->sustained, 0, sizeof pt->sustained);
        pt->perf.sustain = 0; pt->perf.pressure = 0; pt->bend_raw = 8192; bend_update(pt);
        pt->m->all_off(pt->state);
    }
}

void mpe_render(float *out, unsigned frames) {
    while (frames) {
        unsigned n = frames < 256 ? frames : 256;
        memset(bl, 0, n * sizeof bl[0]); memset(br, 0, n * sizeof br[0]);
        for (unsigned i = 0; i < MPE_PARTS; ++i) if (parts[i].m) parts[i].m->render(parts[i].state, parts[i].p, &parts[i].perf, bl, br, n);
        for (unsigned k = 0; k < n; ++k) {
            float l = bl[k], r = br[k];
            out[2 * k] = isfinite(l) ? l : 0; out[2 * k + 1] = isfinite(r) ? r : 0;
        }
        out += 2 * n; frames -= n;
    }
}
