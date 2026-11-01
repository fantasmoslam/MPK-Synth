/* SPDX-License-Identifier: GPL-3.0-only */
/* The bridge between the tablet's audio code (C) and PALEO with the live Scape conductor (C++).
 *
 * One PALEO engine lives at the stream's sample rate. A background thread loads the creature
 * library, builds the engine when the rate is known, and ticks the conductor every 10 ms.
 * The audio callback only calls paleo_mix(), which never blocks: until the engine is ready it
 * adds nothing. Every other call is for the UI thread and returns at once. */
#ifndef PALEO_BRIDGE_H
#define PALEO_BRIDGE_H
#ifdef __cplusplus
extern "C" {
#endif

/* Where the creature library is: a folder with manifest.txt and the FLAC files.
 * On Android, paleo_library_assets() reads it from the APK instead. Call once. */
void paleo_library_dir(const char *dir);
/* Android: the library is in the APK's assets under creatures/ (an AAssetManager*, kept alive by the caller). */
void paleo_library_assets(void *asset_manager);
/* The audio stream's rate. Call before the stream starts (the engine rebuilds if it changed). */
void paleo_prepare(unsigned rate);
/* Audio thread: adds PALEO's music into interleaved stereo `out`, scaled by gain. */
void paleo_mix(float *out, unsigned frames, float gain);

/* 1: begin a new expedition (a fresh seed). 0: lead into the coda and fall silent. */
void paleo_music(int on);
/* End at once (Stop all): what is written is dropped, ringing notes die away. */
void paleo_halt(void);
/* 0 silent, 1 loading, 2 playing, 3 into the coda. */
int paleo_state(void);

/* The player's notes 0..100, kept before an expedition starts and carried into it. */
void paleo_note(int note, int value);
int paleo_note_value(int note);
/* An expedition action (Expedition.ACTIONS order). Ignored while silent. */
void paleo_action(int action);
/* Ask for a scene; urgency 0 after this scene, 1 soon, 2 on the next beat. */
void paleo_scene(int scene, int urgency);
/* The scene playing now (-1 none), and the eight gauges 0..1 (negative = not answering). */
int paleo_scene_now(void);
void paleo_gauges(float out[8]);
/* A gauge's word ("WALKING", "bII", ...), into buf; returns its length. */
int paleo_gauge_text(int gauge, char *buf, int cap);
/* The latest few things the conductor did, newest last, one per line. */
int paleo_marks(char *buf, int cap);
/* Share of real time the last PALEO render took (1.0 = too slow). */
float paleo_load(void);

#ifdef __cplusplus
}
#endif
#endif
