/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth.ui;

/**
 * Recording takes of the final stereo mix. A take starts at the current moment and
 * does not restart the run. Saving is separate from saved: a file is only reported
 * saved once it has been written and closed. A take that could not be saved is kept
 * for another attempt, and no new take can start until it is saved or discarded.
 */
public interface Recorder {
    int READY=0,RECORDING=1,SAVING=2,SAVED=3,ERROR=4;
    final class Take{
        public final String name;public final float seconds;public final int state;public final String detail;
        public Take(String name,float seconds,int state,String detail){this.name=name;this.seconds=seconds;this.state=state;this.detail=detail;}
    }
    /** The state of the current take (READY when there is none). */
    int state();
    /** Elapsed time of the current or last take. */
    long takeMillis();
    /** File name of the current or last take, or null. */
    String takeName();
    /** Why the last save failed, or null. */
    String error();
    /** Recent levels of the recorded mix, 0..1, oldest first; may be empty. */
    float[] levels();
    /** Free space where takes are saved, in bytes, or -1 if unknown. */
    long freeBytes();
    /** Where takes are saved, for display. */
    String destination();
    /** Begins a new take named after the run. */
    void start(String runName);
    /** Stops capture and saves the take. */
    void stop();
    void retry();
    /** Gives up on a take that could not be saved. */
    void discard();
    /** Takes, newest first. */
    Take[] takes();
}
