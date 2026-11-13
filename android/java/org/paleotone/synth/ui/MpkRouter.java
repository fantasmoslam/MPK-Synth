/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth.ui;

/**
 * Routes everything the MPK mini Plus sends, on the MIDI thread, before it reaches the engine.
 * The engine's MIDI IN is CH1-4 (its default): channels 1-4 play parts 1-4 and every other
 * channel is ignored, so the MPK's own channels are rewritten to the part each control plays.
 *
 * - Keys (any channel but 10) play the keys part, which follows the part open in Sound design.
 * - Pads (channel 10, notes 36-51, bank A and B) play the pads part (part 4 unless chosen), notes
 *   and velocity as sent, so a DRUM voice plays them as its General MIDI kit. Pad pressure (poly
 *   aftertouch, which the engine ignores) becomes channel aftertouch on that part: the strongest
 *   pressure of the pads held, the AT source of the part's modulation matrix.
 * - The joystick has no direction (centre 0, either extreme 127), so its two axes bend opposite
 *   ways on the keys part: sideways (CC12) bends up, up and down (CC2) bends down, by the part's
 *   bend range (2 semitones unless set by RPN). Bends add; releasing it returns to pitch.
 * - Knobs (CC70-77) and Play, Stop, Record (CC118, 117, 119) never reach the engine: they go to
 *   {@link Controls}, which the app runs on its main thread. So does every key or pad struck, so the
 *   app can start its audio if something (STOP ALL on screen) turned it off.
 *
 * A note always ends on the channel it started on, so changing parts while holding keys or pads
 * leaves nothing hanging. Safe to call from the MIDI thread while the main thread changes parts.
 */
public final class MpkRouter {
    /** Where messages for the engine go (status with channel, data bytes). */
    public interface Out{void midi(int status,int d1,int d2);}
    /** The MPK's own controls, called on the MIDI thread: post them to the main thread. */
    public interface Controls{
        void knob(int knob,int value);void transport(Transport button,boolean pressed);
        /** A key or pad was struck: the app makes sure its audio is running, so the MPK always sounds. */
        default void played(){}
    }
    public enum Transport{PLAY,STOP,RECORD}

    public static final int PAD_LOW=36,PAD_HIGH=51,DEFAULT_PAD_PART=3;
    private final Out out;private final Controls controls;
    private volatile int keyPart,padPart=DEFAULT_PAD_PART;
    /** The channel each key (by note) and pad (by note) started on, or -1. */
    private final int[] keyChannel=new int[128],padChannel=new int[128],padPressure=new int[128];
    private int joyX,joyY,bendChannel=-1,bendSent=8192,pressureSent;

    public MpkRouter(Out out,Controls controls){
        this.out=out;this.controls=controls;
        java.util.Arrays.fill(keyChannel,-1);java.util.Arrays.fill(padChannel,-1);
    }

    public int keyPart(){return keyPart;}
    public int padPart(){return padPart;}
    /** The keys (and joystick) play this part from the next note. */
    public void keyPart(int part){keyPart=Math.max(0,Math.min(3,part));}
    /** The pads play this part from the next hit. */
    public void padPart(int part){padPart=Math.max(0,Math.min(3,part));}

    /** One complete channel message from the MPK (status 0x80-0xEF). */
    public synchronized void message(int status,int d1,int d2){
        int type=status&0xf0,ch=(status&0x0f)+1;
        if(ch==Mpk.PAD_CHANNEL){pad(type,d1,d2);return;}
        if(type==0xb0&&ch==1){
            int k=Mpk.knob(status,d1);
            if(k>=0){controls.knob(k,d2);return;}
            switch(d1){
                case Mpk.CC_PLAY:controls.transport(Transport.PLAY,d2>0);return;
                case Mpk.CC_STOP:controls.transport(Transport.STOP,d2>0);return;
                case Mpk.CC_RECORD:controls.transport(Transport.RECORD,d2>0);return;
                case Mpk.CC_JOYSTICK_X:joyX=d2;bend();return;
                case Mpk.CC_JOYSTICK_Y:joyY=d2;bend();return;
                default:
            }
        }
        int part=keyPart;
        switch(type){
            case 0x90:
                if(d2>0){
                    if(keyChannel[d1]>=0&&keyChannel[d1]!=part)out.midi(0x80|keyChannel[d1],d1,0); // retriggered on a new part
                    keyChannel[d1]=part;out.midi(0x90|part,d1,d2);controls.played();return;
                }
                // velocity 0 is a note off
            case 0x80:{
                int c=keyChannel[d1]>=0?keyChannel[d1]:part;keyChannel[d1]=-1;
                out.midi(0x80|c,d1,0);return;
            }
            case 0xe0:out.midi(0xe0|part,d1,d2);return;
            default:out.midi(type|part,d1,d2); // sustain, channel pressure and the rest follow the keys
        }
    }

    private void pad(int type,int note,int value){
        if(note<PAD_LOW||note>PAD_HIGH)return;
        if(type==0x90&&value>0){
            int part=padPart;
            if(padChannel[note]>=0&&padChannel[note]!=part)out.midi(0x80|padChannel[note],note,0);
            padChannel[note]=part;padPressure[note]=0;out.midi(0x90|part,note,value);controls.played();
        }else if(type==0x80||type==0x90){
            int c=padChannel[note]>=0?padChannel[note]:padPart;padChannel[note]=-1;padPressure[note]=0;
            out.midi(0x80|c,note,0);pressure(c);
        }else if(type==0xa0&&padChannel[note]>=0){
            padPressure[note]=value;pressure(padChannel[note]);
        }
    }
    /** Channel aftertouch on the pads' part: the strongest pressure among its held pads. */
    private void pressure(int channel){
        int max=0;
        for(int n=PAD_LOW;n<=PAD_HIGH;n++)if(padChannel[n]==channel)max=Math.max(max,padPressure[n]);
        if(max!=pressureSent){out.midi(0xd0|channel,max,0);pressureSent=max;}
    }
    /** Sideways bends up, up and down bends down; a part change returns the old part to pitch. */
    private void bend(){
        int part=keyPart;
        int value=8192+Math.round(joyX*8191f/127f)-Math.round(joyY*8192f/127f);
        value=Math.max(0,Math.min(16383,value));
        if(bendChannel>=0&&bendChannel!=part&&bendSent!=8192)out.midi(0xe0|bendChannel,0,64);
        if(value!=bendSent||bendChannel!=part){out.midi(0xe0|part,value&127,value>>7);bendSent=value;bendChannel=part;}
    }

    /** Ends every note and bend this router started (controller unplugged, app paused). */
    public synchronized void release(){
        for(int n=0;n<128;n++){
            if(keyChannel[n]>=0){out.midi(0x80|keyChannel[n],n,0);keyChannel[n]=-1;}
            if(padChannel[n]>=0){out.midi(0x80|padChannel[n],n,0);padChannel[n]=-1;padPressure[n]=0;}
        }
        if(bendChannel>=0&&bendSent!=8192)out.midi(0xe0|bendChannel,0,64);
        joyX=joyY=0;bendSent=8192;bendChannel=-1;pressureSent=0;
    }
}
