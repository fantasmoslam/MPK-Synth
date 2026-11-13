/* SPDX-License-Identifier: GPL-3.0-only */
package org.fantasmoslam.mpksynth.ui;

/**
 * Colours and sizes. Each instrument has its own look: the Poly 106 a black panel with white
 * print, pale slider caps and red LEDs; the Kit a warm grey drum machine with orange keys.
 * The header and parts use the Poly 106 look; the SOUND area switches to the instrument's.
 */
public final class Theme {
    private Theme(){}
    /** A look: the colours below, in the order of PANEL_106. */
    public static final int[] PANEL_106={
        0xff0f0f0f,0xff181818,0xff242424,0xff343434,0xff5c5c5c,0xfff1efe8,0xffb3b1aa,0xff74726d,
        0xffff4a2a,0xff2c1813,0xff3d1f17,0xff3a1c16,0xfff1efe8,0xffe9e6dc,0xff1e1e1e,0xff9c9a94,0xff6a2a1e};
    public static final int[] DRUMS={
        0xff0f0f0f,0xff2a2724,0xff3a3632,0xff4a4540,0xff70685d,0xfff4ecdc,0xffc6bba8,0xff857b6d,
        0xffff8a1e,0xff3b2614,0xff4d3018,0xff3d2a18,0xfff8d25a,0xfff2ece0,0xff2a2622,0xffb0a690,0xff7a4416};
    public static int BACKGROUND,PANEL,RAISED,TRACK,BORDER,TEXT,MUTED,DIM;
    /** The accent: an LED that is lit, a choice that is made, what to do next. */
    public static int REQUEST,SELECTED_FILL,PRESSED_SELECTED,LED_OFF;
    /** Section names and live state (the MPK connected, the pads' part). */
    public static int PHOSPHOR;
    /** Slider caps and knobs; a pad lit by a hit. */
    public static int CAP,KNOB_FACE,KNOB_RIM,HIT;
    static{use(PANEL_106);}
    public static void use(int[] c){
        BACKGROUND=c[0];PANEL=c[1];RAISED=c[2];TRACK=c[3];BORDER=c[4];TEXT=c[5];MUTED=c[6];DIM=c[7];
        REQUEST=c[8];SELECTED_FILL=c[9];PRESSED_SELECTED=c[10];LED_OFF=c[11];PHOSPHOR=c[12];CAP=c[13];
        KNOB_FACE=c[14];KNOB_RIM=c[15];HIT=c[16];
    }
    public static final int LIVE_FILL=0xff262626,WATER=0xff7fb2e0,CAP_LINE=0xff1a1a1a,SLOT=0xff050505,
        KNOB_BODY=0xff0c0c0c,KNOB_INNER=0xff3a3a3a,DISABLED_EDGE=0xff2e2e2e;
    public static final int MPK_BODY=0xff222222,MPK_EDGE=0xff3c3c3c,MPK_PAD=0xff3b4654,MPK_KEY=0xffe6e4da,MPK_BLACK_KEY=0xff1a1a1a,
        MPK_LIT=0xffff6a4a,MPK_CONTROL=0xff3a3a3a,SCREEN_DARK=0xff0b0b0b,COACH=0xff1d1412,CONNECTED_FILL=0xff1f1f1f;
    public static final int SCRIM=0xdd000000;
    public static final float BUTTON_HEIGHT=48;
}
