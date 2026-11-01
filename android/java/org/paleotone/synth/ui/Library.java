/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth.ui;

/**
 * Imported WAV sounds. One at a time can be loaded into the sample layer, which then
 * answers the keyboard and MIDI in place of Paleotone's own sounds. Expedition parts
 * keep their own voices. Work that touches files happens off the drawing thread.
 */
public interface Library {
    final class Sound{
        public final String id,name;public final int root;public final float seconds;
        /** Peak levels for the waveform, 0..1; null until read. */
        public final float[] peaks;
        public Sound(String id,String name,int root,float seconds,float[] peaks){this.id=id;this.name=name;this.root=root;this.seconds=seconds;this.peaks=peaks;}
    }
    Sound[] sounds();
    /** The sound in the sample layer, or null. */
    String loaded();
    /** True when keyboard and MIDI play the loaded sound. */
    boolean sampleSource();
    void useSample(boolean on);
    void load(String id);
    /** Plays the sound once at its root note. */
    void preview(String id);
    void setRoot(String id,int note);
    void delete(String id);
    void importWav();
    /** What the library is doing or what went wrong, or null. */
    String status();
}
