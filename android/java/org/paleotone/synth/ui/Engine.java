/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth.ui;

/**
 * What the interface reads from and sends to the app. The tablet backs it with the
 * native engine; previews back it with fixed example values. Slots are the rows of
 * android/native/controls.h.
 */
public interface Engine {
    int TEMPO=12,HABITAT=13,DRONE=14,DANGER=21,EVOLVING=23,HOLD=24,BIOME=39,MOOD=40;
    int SCAPE_START_ENVIRONMENT=1,SCAPE_PAUSE_ENVIRONMENT=2,SCAPE_DRONE_ON=3,SCAPE_DRONE_OFF=4,
        SCAPE_PLAY_MUSIC=5,SCAPE_STOP_MUSIC=6,SCAPE_NEXT_HARMONY=9;
    int value(int slot);
    int min(int slot);
    int max(int slot);
    /** The engine's own formatted value, with unit. */
    String text(int slot);
    void set(int slot,int value);
    boolean scape(int command);
    boolean audioOn();
    boolean musicPlaying();
    int master();
    void setMaster(int value);
    void stopAll();
    void saveSession();
    /** Settings rows that open the app's existing dialogs. */
    void action(String name);
}
