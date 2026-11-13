/* SPDX-License-Identifier: GPL-3.0-only
 * The looper: records the MIDI you play (after the MPK's routing, so each event already
 * carries its part's channel) and plays it back in a loop, on the audio thread.
 *
 * The first take sets the loop's length: Record starts it, and Record or Play closes it and
 * plays it back (Stop closes it and stays stopped). While the loop plays, Record toggles
 * overdub, which adds what you play to the loop. Stop silences the loop and keeps it; Clear
 * forgets it. Live playing always reaches the synth, whatever the looper is doing.
 *
 * Events are kept sorted by their frame in the loop. Playback walks them with a cursor, so a
 * block costs only the events it plays. Owned by one thread; no locks, no allocation.
 */
#pragma once
#include <stdint.h>
#include <string.h>

enum { LP_EMPTY, LP_RECORDING, LP_PLAYING, LP_OVERDUB, LP_STOPPED };
enum { LP_CMD_RECORD=1, LP_CMD_PLAY, LP_CMD_STOP, LP_CMD_CLEAR };
#define LP_MAX_EVENTS 8192u
/* A first take shorter than this is a mistake, not a loop. */
#define LP_MIN_SECONDS_X100 15u
#define LP_MAX_SECONDS 240u

typedef struct { uint32_t at, msg; } lp_event;
/* Where the looper sends playback (status | d1<<8 | d2<<16). */
typedef void (*lp_out)(void *user, uint32_t msg);

typedef struct {
    int state;
    uint32_t length, pos, rate;   /* frames */
    unsigned n, cursor;           /* events; the next one to play */
    unsigned full;                /* events dropped because the loop was full */
    lp_event ev[LP_MAX_EVENTS];
    /* Notes the loop has sounding (to end them on Stop), and live notes whose note-on was
     * recorded but whose note-off has not arrived yet (so it is recorded even after
     * recording stops, and the loop never holds a note forever). */
    uint8_t sounding[16][128], open[16][128];
} lp_looper;

static inline void lp_init(lp_looper *l, uint32_t rate) { memset(l, 0, sizeof *l); l->rate = rate ? rate : 48000; }
static inline int lp_recording(const lp_looper *l) { return l->state == LP_RECORDING || l->state == LP_OVERDUB; }

/* Ends every note the loop has sounding, and returns each part's bend and sustain to rest. */
static inline void lp_silence(lp_looper *l, lp_out out, void *user) {
    for (unsigned c = 0; c < 16; ++c) {
        int any = 0;
        for (unsigned n = 0; n < 128; ++n) if (l->sounding[c][n]) { out(user, (0x80u | c) | n << 8); l->sounding[c][n] = 0; any = 1; }
        if (any) { out(user, (0xe0u | c) | 0u << 8 | 64u << 16); out(user, (0xb0u | c) | 64u << 8); }
    }
}
static inline int lp_insert(lp_looper *l, unsigned at_index, uint32_t at, uint32_t msg) {
    if (l->n >= LP_MAX_EVENTS) { ++l->full; return 0; }
    memmove(&l->ev[at_index + 1], &l->ev[at_index], (l->n - at_index) * sizeof l->ev[0]);
    l->ev[at_index].at = at; l->ev[at_index].msg = msg; ++l->n; return 1;
}
/* Records at the current position, just ahead of the cursor: it plays from the next pass. */
static inline void lp_record_here(lp_looper *l, uint32_t msg) {
    uint32_t at = l->state == LP_RECORDING ? l->pos : (l->length ? l->pos % l->length : 0);
    if (lp_insert(l, l->cursor, at, msg)) ++l->cursor;
}

/* The first take ends: its length is where we are. Too short, and the loop is forgotten. */
static inline int lp_close(lp_looper *l) {
    if ((uint64_t)l->pos * 100u < (uint64_t)LP_MIN_SECONDS_X100 * l->rate) {
        uint32_t rate = l->rate; lp_init(l, rate); return 0;
    }
    l->length = l->pos; l->pos = 0; l->cursor = 0;
    return 1;
}

static inline void lp_command(lp_looper *l, int command, lp_out out, void *user) {
    switch (command) {
    case LP_CMD_RECORD:
        if (l->state == LP_EMPTY) { uint32_t rate = l->rate; lp_init(l, rate); l->state = LP_RECORDING; }
        else if (l->state == LP_RECORDING) l->state = lp_close(l) ? LP_PLAYING : LP_EMPTY;
        else if (l->state == LP_PLAYING) l->state = LP_OVERDUB;
        else if (l->state == LP_OVERDUB) l->state = LP_PLAYING;
        else if (l->state == LP_STOPPED) { l->pos = 0; l->cursor = 0; l->state = LP_OVERDUB; }
        break;
    case LP_CMD_PLAY:
        if (l->state == LP_RECORDING) l->state = lp_close(l) ? LP_PLAYING : LP_EMPTY;
        else if (l->state == LP_OVERDUB) l->state = LP_PLAYING;
        else if (l->state == LP_STOPPED) { l->pos = 0; l->cursor = 0; l->state = LP_PLAYING; }
        break;
    case LP_CMD_STOP:
        if (l->state == LP_RECORDING) l->state = lp_close(l) ? LP_STOPPED : LP_EMPTY;
        else if (l->state == LP_PLAYING || l->state == LP_OVERDUB) l->state = LP_STOPPED;
        lp_silence(l, out, user);
        if (l->state == LP_STOPPED) { l->pos = 0; l->cursor = 0; }
        break;
    case LP_CMD_CLEAR: {
        lp_silence(l, out, user);
        uint32_t rate = l->rate; lp_init(l, rate); break;
    }
    default: break;
    }
}

/* A message you played (already routed to its part). Records it when the looper is
 * recording, or when it ends a note whose start was recorded. Messages that are not
 * worth looping (program changes, system messages) are left out. */
static inline void lp_live(lp_looper *l, uint32_t msg) {
    unsigned status = msg & 0xf0u, c = msg & 15u, d1 = (msg >> 8) & 127u, d2 = (msg >> 16) & 127u;
    int on = status == 0x90 && d2 > 0, off = status == 0x80 || (status == 0x90 && d2 == 0);
    if (status < 0x80 || status == 0xc0 || status >= 0xf0) return;
    if (off) {
        if (!l->open[c][d1]) return;
        l->open[c][d1] = 0;
        if (l->state == LP_EMPTY) return;
        lp_record_here(l, (0x80u | c) | d1 << 8);
        return;
    }
    if (!lp_recording(l)) return;
    if (on) l->open[c][d1] = 1;
    lp_record_here(l, msg);
}

static inline void lp_emit(lp_looper *l, uint32_t msg, lp_out out, void *user) {
    unsigned status = msg & 0xf0u, c = msg & 15u, d1 = (msg >> 8) & 127u, d2 = (msg >> 16) & 127u;
    if (status == 0x90 && d2 > 0) {
        if (l->sounding[c][d1]) out(user, (0x80u | c) | d1 << 8);   /* retrigger, never stack */
        l->sounding[c][d1] = 1;
    } else if (status == 0x80 || status == 0x90) {
        if (!l->sounding[c][d1]) return;
        l->sounding[c][d1] = 0;
    }
    out(user, msg);
}

/* Advances one audio block of frames: plays the events that fall in it, wrapping at the
 * loop's end. The first take only counts its length (and stops itself at the maximum). */
static inline void lp_advance(lp_looper *l, uint32_t frames, lp_out out, void *user) {
    if (l->state == LP_RECORDING) {
        l->pos += frames;
        if (l->pos >= LP_MAX_SECONDS * l->rate) l->state = lp_close(l) ? LP_PLAYING : LP_EMPTY;
        return;
    }
    if ((l->state != LP_PLAYING && l->state != LP_OVERDUB) || !l->length) return;
    while (frames) {
        uint32_t step = l->length - l->pos < frames ? l->length - l->pos : frames, end = l->pos + step;
        while (l->cursor < l->n && l->ev[l->cursor].at < end) lp_emit(l, l->ev[l->cursor++].msg, out, user);
        l->pos = end; frames -= step;
        if (l->pos >= l->length) { l->pos = 0; l->cursor = 0; }
    }
}
