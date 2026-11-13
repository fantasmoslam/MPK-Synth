/* SPDX-License-Identifier: GPL-3.0-only */
package org.fantasmoslam.mpksynth.ui;

/** What the screen asks of the app beyond the sound's values. */
public interface Host {
    int master();void master(int value);
    boolean audioOn();
    /** A note you tap on screen (a drum pad), on a part. */
    void audition(int part,int note,boolean on);
    /** Ends every note on every part. */
    void panic();
}
