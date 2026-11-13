/* SPDX-License-Identifier: GPL-3.0-only */
#pragma once
#include "model.h"

#define MPE_PARTS 4
/* Boots the four parts with their default models at a sample rate. */
void mpe_init(float sample_rate);
/* A new device rate: the parts keep their sounds and lose their notes. */
void mpe_set_rate(float sample_rate);
unsigned mpe_models(void);
const mp_model *mpe_model(unsigned i);
/* A parameter's index by its label, or -1. */
int mpe_find(const mp_model *m, const char *label);
/* A part plays a model from its default values. */
void mpe_part_model(unsigned part, unsigned model);
int mpe_part_model_of(unsigned part);
void mpe_param(unsigned part, unsigned id, int value);
int mpe_get(unsigned part, unsigned id);
/* A preset's values (every parameter) for a model. Any thread: static tables only. */
void mpe_preset_values(const mp_model *m, unsigned preset, int16_t *out);
/* A channel message: channels 1-4 play parts 1-4. */
void mpe_midi(unsigned status, unsigned d1, unsigned d2);
void mpe_panic(void);
/* Stereo, interleaved; overwrites out. */
void mpe_render(float *out, unsigned frames);
const char *mp_kit_pad_name(int pad);
