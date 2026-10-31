/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth.ui;

/** Round-three design tokens (Design-Tokens.json). Green is live, amber is your request or a selection. */
public final class Theme {
    private Theme(){}
    public static final int BACKGROUND=0xff111811,PANEL=0xff192317,PHOSPHOR=0xffa8efb3,REQUEST=0xfff0ad4e,
        TEXT=0xffeae7d5,MUTED=0xffb4c1ae,BORDER=0xff526d47,WATER=0xff88b69e;
    /** Derived shades used on the boards. */
    public static final int RAISED=0xff1f2b1d,SELECTED_FILL=0xff2b2716,TRACK=0xff2a3a28,DIM=0xff6f7f69,
        KNOB_FACE=0xff1a2219,KNOB_RIM=0xff8a9a84,SCRIM=0xcc0b100b;
    public static final float BUTTON_HEIGHT=48;
    public static final float KNOB_FOCUS=96,KNOB_PERFORMANCE=118;
}
