/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth;
final class Native {
    static { System.loadLibrary("paleotone"); }
    static native void init(byte[] saved);
    static native String start(int buffers);
    static native void stop();
    static native int error();
    static native void level(int value);
    static native byte[] save();
    static native void master(int value);
    static native void panic();
    static native void midi(int status,int d1,int d2);
    static native String stats();
    /** Full diagnostics line for logcat, or null while audio is stopped. */
    static native String report();
    static native int peak();
    static native String[] controlLabels();
    static native int[] controls();
    static native String[] controlTexts();
    static native void control(int slot,int value);
    static native boolean scape(int command);
    static native boolean loadSample(float[] data,int rate,int root);
    static native void sampleMode(boolean enabled);
    static native void sampleRoot(int note);
    /** Every value's text of a short list control, from its minimum. */
    static native String[] controlNames(int slot);
    /** Sound design: [engine index, engine name, preset name, then eight "label\tfmt\tmin\tmax\tvalue\tbipolar\ttext"]. */
    static native String[] voice(int track);
    static native String[] voiceNames(int track,int param);
    static native boolean voiceSet(int track,int param,int value);
    static native boolean voiceEngine(int track,int engine);
    static native boolean voicePreset(int track,int preset);
    /** "index\tname" per engine or preset. */
    static native String[] engines();
    static native String[] presets(int engine);
    /** Recording tap on the final mix: interleaved stereo 16-bit at sampleRate(). */
    static native void recordStart();
    static native void recordStop();
    static native int recordRead(short[] out);
    static native int recordDropped();
    static native int sampleRate();
    /** Fades the final mix to silence over ms (0 restores full level); faded() once silent. */
    static native void fade(int ms);
    static native boolean faded();
}
