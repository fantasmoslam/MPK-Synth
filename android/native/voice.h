/* SPDX-License-Identifier: GPL-3.0-only */
#pragma once
/* One EDIT parameter of a part's voice, as the firmware's editor sees it (controls.h voice_read). */
typedef struct {char label[8];int fmt,min,max,value,bipolar;char text[16];} pt_voice_param;
