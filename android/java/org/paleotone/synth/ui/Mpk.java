/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth.ui;

import java.util.Locale;

/**
 * The Akai MPK mini Plus with its saved MPC program, as verified with a MIDI monitor:
 * knobs 1-8 send channel 1 CC70-77 (absolute 0-127); Play, Stop and Record CC118, 117
 * and 119; the joystick CC12 (sideways) and CC2 (up and down); pads channel 10, notes
 * 36-43 (bank A) and 44-51 (bank B). The keys play on any other channel.
 *
 * The eight knobs drive the eight edit parameters of the part open in Sound design.
 * Each knob uses pickup (soft takeover): after its parameter changes from anywhere else
 * (a preset, another part or voice, the screen) the knob does nothing until it reaches
 * the parameter's value, so the sound never jumps.
 */
public final class Mpk {
    public static final String DEVICE="MPK mini";
    public static final int KNOB_CC=70,KNOBS=8,PAD_CHANNEL=10;
    public static final int CC_PLAY=118,CC_STOP=117,CC_RECORD=119,CC_JOYSTICK_X=12,CC_JOYSTICK_Y=2;
    /** How long the knob strip stays up after the last knob move, in ms. */
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

    /** Physical position of each knob 0-127, or -1 until it first moves. */
    private final int[] position={-1,-1,-1,-1,-1,-1,-1,-1};
    private final boolean[] picked=new boolean[KNOBS];
    /** What each knob is bound to (part and voice) and the value it last sent, with when. */
    private final int[] boundPart=new int[KNOBS],boundEngine=new int[KNOBS],sent=new int[KNOBS];
    private final long[] sentAt=new long[KNOBS];
    private int lastKnob=-1;
    private long lastMove=Long.MIN_VALUE/2;
    private boolean connected;

    public Mpk(){java.util.Arrays.fill(boundPart,-1);}

    public void connected(boolean on){connected=on;if(!on)java.util.Arrays.fill(picked,false);}
    public boolean connected(){return connected;}
    public boolean pickedUp(int k){return picked[k];}
    public int position(int k){return position[k];}

    /** A knob moved to raw 0-127. Sends the parameter change through the engine when the knob has picked up. */
    public void move(int k,int raw,Engine e,int part,long now){
        if(k<0||k>=KNOBS)return;
        raw=Math.max(0,Math.min(127,raw));
        int previous=position[k];position[k]=raw;lastKnob=k;lastMove=now;
        Engine.Voice v=e.voice(part);
        Engine.Param p=v==null?null:v.params[k];
        if(p==null||!p.used()||p.max<=p.min){picked[k]=false;return;}
        // An outside change releases the knob: another part or voice, or a value it did not send.
        if(boundPart[k]!=part||boundEngine[k]!=v.engine||(picked[k]&&p.value!=sent[k]&&now-sentAt[k]>SETTLE_MS))picked[k]=false;
        boundPart[k]=part;boundEngine[k]=v.engine;
        float span=p.max-p.min;
        float current=(p.value-p.min)/span,pos=raw/127f;
        if(!picked[k]){
            // Landing on it (within a step either way, or one value of a short list), or passing it.
            boolean near=Math.abs(pos-current)<=Math.max(1.5f/127f,.5f/span);
            boolean crossed=previous>=0&&(previous/127f-current)*(pos-current)<=0;
            if(!near&&!crossed)return;
            picked[k]=true;sent[k]=p.value;sentAt[k]=now;
        }
        int target=p.min+Math.round(pos*span);
        if(target!=sent[k]){e.setParam(part,k,target);sent[k]=target;sentAt[k]=now;}
    }

    /** Releases every knob, for example after a part, voice or preset change from the screen. */
    public void releaseAll(){java.util.Arrays.fill(picked,false);}

    /** Whether the knob strip is showing at this time. */
    public boolean showing(long now){return now-lastMove<SHOW_MS;}

    /**
     * Pickup hint for one knob: empty when it is in control, otherwise which way to turn it
     * to take over ("TURN UP" or "TURN DOWN"), or "TURN" before it has moved at all.
     */
    public String hint(int k,Engine.Param p){
        if(p==null||!p.used())return "";
        if(picked[k])return "";
        if(position[k]<0)return "TURN";
        float current=p.max>p.min?(p.value-p.min)/(float)(p.max-p.min):0;
        return position[k]/127f<current?"TURN UP":"TURN DOWN";
    }

    /**
     * The strip of eight knobs over the bottom of any screen, for a few seconds after a knob
     * moves: each knob's number, parameter name and value, with the knob you turned
     * highlighted and a pickup hint where the knob has not taken over yet.
     */
    public void draw(Ui ui,Engine e,int part,float x,float y,float w){
        if(!showing(ui.now))return;
        Gfx g=ui.g;Engine.Voice v=e.voice(part);
        float h=86,gap=6,cw=(w-16-gap*7)/8,bh=h-26;
        g.rect(x,y,w,h,Theme.PANEL);g.frame(x,y,w,h,1,Theme.REQUEST);
        ui.block(x,y,w,h);
        String title=String.format(Locale.ROOT,"MPK KNOBS · PART %02d%s",part+1,v==null?"":" · "+v.engineName.toUpperCase(Locale.ROOT));
        g.text(title,x+10,y+14,Gfx.MONO,9.5f,Theme.MUTED,Gfx.LEFT,.06f);
        for(int k=0;k<KNOBS;k++){
            Engine.Param p=v==null?null:v.params[k];
            float cx=x+8+k*(cw+gap),cy=y+20;
            boolean used=p!=null&&p.used(),active=k==lastKnob;
            g.rect(cx,cy,cw,bh,active?Theme.SELECTED_FILL:Theme.RAISED);
            g.frame(cx,cy,cw,bh,1,active?Theme.REQUEST:Theme.BORDER);
            g.text("K"+(k+1),cx+6,cy+13,Gfx.MONO,9.5f,used&&picked[k]?Theme.PHOSPHOR:Theme.DIM,Gfx.LEFT,0);
            if(!used){g.text("—",cx+cw/2,cy+34,Gfx.MONO,12,Theme.DIM,Gfx.CENTER,0);continue;}
            g.text(p.label,cx+cw-6,cy+13,Gfx.MONO,9.5f,Theme.TEXT,Gfx.RIGHT,.04f);
            String hint=hint(k,p);
            g.text(fit(g,p.text,cw-8,12),cx+cw/2,cy+32,Gfx.MONO,12,hint.isEmpty()?Theme.REQUEST:Theme.MUTED,Gfx.CENTER,0);
            // value bar, with the physical knob's position marked while it has not picked up
            float bx=cx+6,bw=cw-12,by=cy+40;
            float t=p.max>p.min?(p.value-p.min)/(float)(p.max-p.min):0;
            g.rect(bx,by,bw,3,Theme.TRACK);g.rect(bx,by,bw*Math.max(0,Math.min(1,t)),3,Theme.PHOSPHOR);
            if(!hint.isEmpty()&&position[k]>=0)g.rect(bx+bw*position[k]/127f-1,by-3,2,9,Theme.REQUEST);
            if(!hint.isEmpty())g.text(hint,cx+cw/2,cy+54,Gfx.MONO,8.5f,Theme.REQUEST,Gfx.CENTER,.04f);
        }
    }
    private static String fit(Gfx g,String s,float w,float size){
        if(s==null)return "";
        while(s.length()>1&&g.textWidth(s,Gfx.MONO,size,0)>w)s=s.substring(0,s.length()-1);
        return s;
    }
}
