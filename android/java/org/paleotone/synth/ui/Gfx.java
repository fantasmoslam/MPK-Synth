/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth.ui;

/**
 * The drawing surface the interface paints on, in layout units (1 unit = 1 dp on
 * the tablet; the round-three boards are 1.5 px per unit). The tablet implements it
 * with android.graphics.Canvas, and the desktop preview with Java2D, so the same
 * screen code produces the app and its review screenshots.
 */
public interface Gfx {
    int MONO=0,MONO_MEDIUM=1,DISPLAY=2,HAND=3,PRINT=4;
    int LEFT=0,CENTER=1,RIGHT=2;
    void rect(float x,float y,float w,float h,int color);
    void frame(float x,float y,float w,float h,float width,int color);
    void line(float x1,float y1,float x2,float y2,float width,int color);
    void circle(float cx,float cy,float r,int color);
    void ring(float cx,float cy,float r,float width,int color);
    /** Degrees clockwise from 12 o'clock. */
    void arc(float cx,float cy,float r,float startDegrees,float sweepDegrees,float width,int color);
    /** Filled polygon from x,y pairs. */
    void polygon(float[] xy,int color);
    /** Open or closed polyline; dash 0 draws solid. */
    void polyline(float[] xy,boolean closed,float width,int color,float dash);
    /** Baseline text. Tracking is extra letter spacing in ems. */
    void text(String s,float x,float y,int font,float size,int color,int align,float tracking);
    float textWidth(String s,int font,float size,float tracking);
    void clip(float x,float y,float w,float h);
    void unclip();
}
