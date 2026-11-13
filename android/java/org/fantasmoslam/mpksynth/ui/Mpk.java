/* SPDX-License-Identifier: GPL-3.0-only */
package org.fantasmoslam.mpksynth.ui;

import java.util.Locale;

/**
 * The Akai MPK mini Plus with its saved MPC program, as verified with a MIDI monitor:
 * knobs 1-8 send channel 1 CC70-77 (absolute 0-127); Play, Stop and Record CC118, 117
 * and 119; the joystick CC12 (sideways) and CC2 (up and down); pads channel 10, notes
 * 36-43 (bank A) and 44-51 (bank B). The keys play on any other channel.
 *
 * The eight knobs drive the eight controls of the highlighted bank of the part the keys play.
 * Each knob uses pickup (soft takeover): after its parameter changes from anywhere else
 * (a preset, another part or voice, the screen) the knob does nothing until it reaches
 * the parameter's value, so the sound never jumps.
 */
public final class Mpk {
    public static final String DEVICE="MPK mini";
    public static final int KNOB_CC=70,KNOBS=8,PAD_CHANNEL=10;
    public static final int CC_PLAY=118,CC_STOP=117,CC_RECORD=119,CC_JOYSTICK_X=12,CC_JOYSTICK_Y=2;
    /** How long the knob you turned stays highlighted, in ms. */
    static final long SHOW_MS=2500;
    /** The engine reads back a value it was sent a moment later; within this time a mismatch is not an outside change. */
    static final long SETTLE_MS=400;

    /** True for a device name that is an MPK mini (the Plus reports "MPK mini Plus"). */
    public static boolean isMpk(String deviceName){
        return deviceName!=null&&deviceName.toLowerCase(Locale.ROOT).contains(DEVICE.toLowerCase(Locale.ROOT));
    }
    /** The knob (0-7) a channel message is from, or -1. Status and data as received. */
    public static int knob(int status,int d1){
        return status==0xb0&&d1>=KNOB_CC&&d1<KNOB_CC+KNOBS?d1-KNOB_CC:-1;
    }

    /** What a knob controls right now: a part's control, by a key that names it. */
    public interface Target{
        /** Names the control (part, model and control), so a knob notices when it is moved to another. */
        String key();
        int min();int max();int value();
        void set(int value);
    }

    /** Physical position of each knob 0-127, or -1 until it first moves. */
    private final int[] position={-1,-1,-1,-1,-1,-1,-1,-1};
    private final boolean[] picked=new boolean[KNOBS];
    /** What each knob is bound to and the value it last sent, with when. */
    private final String[] bound=new String[KNOBS];
    private final int[] sent=new int[KNOBS];
    private final long[] sentAt=new long[KNOBS];
    private int lastKnob=-1;
    private long lastMove=Long.MIN_VALUE/2;
    private boolean connected;

    public void connected(boolean on){connected=on;if(!on)java.util.Arrays.fill(picked,false);}
    public boolean connected(){return connected;}
    public boolean pickedUp(int k){return picked[k];}
    public int position(int k){return position[k];}

    /** A knob moved to raw 0-127. Changes its target once the knob has picked it up. */
    public void move(int k,int raw,Target t,long now){
        if(k<0||k>=KNOBS)return;
        raw=Math.max(0,Math.min(127,raw));
        int previous=position[k];position[k]=raw;lastKnob=k;lastMove=now;
        if(t==null||t.max()<=t.min()){picked[k]=false;bound[k]=null;return;}
        int value=t.value();
        // An outside change releases the knob: another control, or a value it did not send.
        if(!t.key().equals(bound[k])||(picked[k]&&value!=sent[k]&&now-sentAt[k]>SETTLE_MS))picked[k]=false;
        bound[k]=t.key();
        float span=t.max()-t.min();
        float current=(value-t.min())/span,pos=raw/127f;
        if(!picked[k]){
            // Landing on it (within a step either way, or one value of a short list), or passing it.
            boolean near=Math.abs(pos-current)<=Math.max(1.5f/127f,.5f/span);
            boolean crossed=previous>=0&&(previous/127f-current)*(pos-current)<=0;
            if(!near&&!crossed)return;
            picked[k]=true;sent[k]=value;sentAt[k]=now;
        }
        int target=t.min()+Math.round(pos*span);
        if(target!=sent[k]){t.set(target);sent[k]=target;sentAt[k]=now;}
    }

    /** Releases every knob, for example after a part, voice or preset change from the screen. */
    public void releaseAll(){java.util.Arrays.fill(picked,false);}

    /** Whether a knob moved in the last few seconds. */
    public boolean showing(long now){return now-lastMove<SHOW_MS;}

    /**
     * Pickup hint for one knob: empty when it is in control, otherwise which way to turn it
     * to take over ("TURN UP" or "TURN DOWN"), or "TURN" before it has moved at all.
     */
    public String hint(int k,Target t){
        if(t==null||t.max()<=t.min())return "";
        if(picked[k]&&t.key().equals(bound[k]))return "";
        if(position[k]<0)return "TURN";
        float current=(t.value()-t.min())/(float)(t.max()-t.min());
        return position[k]/127f<current?"TURN UP":"TURN DOWN";
    }
    /** Whether knob k is in control of this target now. */
    public boolean controls(int k,Target t){return t!=null&&picked[k]&&t.key().equals(bound[k]);}

    /** The knob that moved last, or -1. */
    public int lastKnob(){return lastKnob;}
}
