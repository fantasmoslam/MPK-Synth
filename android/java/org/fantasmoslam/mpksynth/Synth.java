/* SPDX-License-Identifier: GPL-3.0-only */
package org.fantasmoslam.mpksynth;

/** The native synth (android/native/synth.c). Call from the main thread, except midi. */
final class Synth {
    static { System.loadLibrary("mpksynth"); }
    private Synth(){}
    /** Boots the synth with its default parts. Once. */
    static native void init();
    /** Opens the audio stream; null, or why it failed. */
    static native String start(int buffers);
    static native void stop();
    static native boolean running();
    /** Nonzero once the stream has failed (a route change): stop and start again. */
    static native int error();
    /** Output gain: 0 unity, 1 +6 dB, 2 +12 dB, with a limiter. */
    static native void level(int value);
    /** Master volume 0-1023. */
    static native void master(int value);
    static native int peak();
    /** Ends every note on every part. */
    static native void panic();
    /** One channel message, already routed to its part (channel 1-4). Any thread. */
    static native void midi(int status,int d1,int d2);
    /** One diagnostics line for logcat, or null while audio is stopped. */
    static native String report();

    /** "name\tinspired by" per model. */
    static native String[] models();
    /** "label\tsection\tmin\tmax\tdef\tfmt\tui\tnames" per control. */
    static native String[] params(int model);
    /** "name\tid,id,..." per knob bank. */
    static native String[] banks(int model);
    static native String[] presets(int model);
    /** Every control's value in a preset. */
    static native int[] presetValues(int model,int preset);
    static native String padName(int pad);
    /** A part plays a model (from its defaults until values are sent). */
    static native boolean partModel(int part,int model);
    static native boolean param(int part,int id,int value);

    /** A looper command (stage 2). */
    static native boolean loop(int button);
    /** [state, length, position, events, dropped, sample rate]. */
    static native int[] loopState();
}
