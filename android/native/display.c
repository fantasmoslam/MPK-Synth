/* SPDX-License-Identifier: GPL-3.0-only
 * The firmware's screen, drawn off the audio thread. This file compiles the
 * portable firmware a second time; the build makes every symbol of that copy
 * local except the pt_display_* entry points below, so it cannot touch the
 * engine's state. Snapshots use the firmware's own second-screen projection
 * (remote_state.c) and renderer (web_remote_apply), as the FM-1 companion does.
 */
#include "display.h"
#include "../../web/emu/felucca_web.c"
#include "controls.h"

static uint8_t booted, playing, scape_busy;
void pt_display_boot(void) {
    if (booted) return;
    web_nor_erase(); web_boot(); booted = 1;
}
int pt_display_apply(const uint8_t *snapshot, uint32_t size) {
    if (!booted || size < 2 || size - 2 > RS_MAX) return 0;
    memcpy(rs_buf, snapshot + 2, size - 2);
    if (!web_remote_apply(size - 2)) return 0;
    playing = snapshot[0]; scape_busy = snapshot[1];
    return 1;
}
void pt_display_screen(uint32_t *argb) {
    const uint16_t *pixels = web_screen();
    for (unsigned i = 0; i < 240*240; ++i) {
        unsigned c = (pixels[i] >> 8) | ((pixels[i] & 255) << 8);
        unsigned r=(c>>11)&31, g=(c>>5)&63, b=c&31;
        argb[i] = 0xff000000u | ((r<<3 | r>>2)<<16) |
            ((g<<2 | g>>4)<<8) | (b<<3 | b>>2);
    }
}
void pt_display_control_read(unsigned slot, int *value, int *min, int *max, char *text) {
    control_read(slot, value, min, max, text);
}
unsigned pt_display_playing(void) { return playing; }
unsigned pt_display_scape_busy(void) { return scape_busy; }
unsigned pt_display_voice(unsigned tr, char *engine, char *preset, pt_voice_param *params) {
    return voice_read(tr, engine, preset, params);
}
unsigned pt_display_voice_names(unsigned tr, unsigned k, char names[][16], unsigned cap) {
    return voice_names(tr, k, names, cap);
}
unsigned pt_display_control_names(unsigned slot, char names[][16], unsigned cap) {
    return control_names(slot, names, cap);
}
