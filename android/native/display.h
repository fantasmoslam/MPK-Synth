/* SPDX-License-Identifier: GPL-3.0-only */
#pragma once
#include <stdint.h>
/* A second, drawing-only copy of the firmware. Owned by the UI thread: it never
 * makes sound and shares no state with the engine. */
void pt_display_boot(void);
/* Import a pt_snapshot and draw it. Returns 0 if the snapshot was rejected. */
int pt_display_apply(const uint8_t *snapshot, uint32_t size);
void pt_display_screen(uint32_t *argb);
void pt_display_control_read(unsigned slot, int *value, int *min, int *max, char *text);
unsigned pt_display_playing(void);
unsigned pt_display_scape_busy(void);
#include "voice.h"
/* Sound design reads on the last snapshot: part tr's engine index, names and eight
 * EDIT parameters; the value names of an F_ENUM parameter (count, 0 if not a list). */
unsigned pt_display_voice(unsigned tr, char *engine, char *preset, pt_voice_param *params);
unsigned pt_display_voice_names(unsigned tr, unsigned k, char names[][16], unsigned cap);
/* Every value's text of a control with at most cap values (0 when longer). */
unsigned pt_display_control_names(unsigned slot, char names[][16], unsigned cap);
