/* SPDX-License-Identifier: GPL-3.0-only */
package org.fantasmoslam.mpksynth.ui;

/** MPK mini Plus knobs: the message map and pickup (soft takeover). */
public final class MpkTest {
    static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}

    /** A control with a range and a value, as a part's control is to a knob. */
    static final class Control implements Mpk.Target{
        final String key;final int min,max;int value,sets;
        Control(String key,int min,int max,int value){this.key=key;this.min=min;this.max=max;this.value=value;}
        public String key(){return key;}
        public int min(){return min;}
        public int max(){return max;}
        public int value(){return value;}
        public void set(int v){value=v;sets++;}
    }

    public static void main(String[] args){
        check(Mpk.knob(0xb0,70)==0&&Mpk.knob(0xb0,77)==7,"CC70-77 on channel 1 are the knobs");
        check(Mpk.knob(0xb0,69)<0&&Mpk.knob(0xb0,78)<0,"neighbouring CCs are not knobs");
        check(Mpk.knob(0xb1,70)<0,"other channels are not knobs");
        check(Mpk.knob(0x90,70)<0,"notes are not knobs");
        check(Mpk.isMpk("MPK mini Plus")&&Mpk.isMpk("Akai MPK Mini 3")&&!Mpk.isMpk("SMK-37"),"device names");

        Mpk m=new Mpk();m.connected(true);long t=1000;
        Control freq=new Control("0:0:11",0,127,64);
        m.move(0,10,freq,t+=10);
        check(freq.value==64&&!m.pickedUp(0),"a knob away from the value does not jump it");
        check(m.hint(0,freq).equals("TURN UP"),"hint says which way");
        m.move(0,40,freq,t+=10);check(freq.value==64,"still below");
        m.move(0,70,freq,t+=10);
        check(m.controls(0,freq)&&freq.value==70,"crossing the value takes over: "+freq.value);
        check(m.hint(0,freq).isEmpty(),"no hint once in control");
        m.move(0,30,freq,t+=10);check(freq.value==30,"then it follows freely");

        // A change from elsewhere (preset, screen) releases the knob once the value has settled.
        freq.value=100;
        m.move(0,32,freq,t+=1000);
        check(!m.pickedUp(0)&&freq.value==100,"an outside change releases the knob");
        check(m.hint(0,freq).equals("TURN UP"),"and asks for pickup again");
        m.move(0,99,freq,t+=10);check(m.pickedUp(0)&&freq.value==99,"landing within a step picks up");

        // Another control (another part, bank or model) needs pickup too.
        Control other=new Control("1:0:11",0,127,20);
        m.move(0,120,other,t+=10);
        check(!m.controls(0,other)&&other.value==20&&freq.value==99,"a new control needs pickup");
        m.move(0,21,other,t+=10);check(m.controls(0,other)&&other.value==21,"picked up");
        check(!m.controls(0,freq),"the knob no longer drives the old control");

        // A short list: one value either way of the position picks up, then steps follow.
        Control range=new Control("0:0:2",0,2,1);
        m.move(1,127,range,t+=10);check(!m.pickedUp(1),"far from the list value");
        m.move(1,60,range,t+=10);check(m.pickedUp(1)&&range.value==1,"on the list value, which stays 1");
        m.move(1,127,range,t+=10);check(range.value==2,"the top of the knob is the last value");

        // No control in the slot: nothing happens.
        m.move(7,0,null,t+=10);m.move(7,127,null,t+=10);
        check(m.hint(7,null).isEmpty()&&!m.pickedUp(7),"an empty slot does nothing");

        m.releaseAll();check(!m.pickedUp(1),"release all");
        m.move(1,127,range,t+=10);
        m.connected(false);check(!m.pickedUp(1),"disconnect releases");
        check(m.lastKnob()==1&&m.showing(t)&&!m.showing(t+Mpk.SHOW_MS+1),"the knob turned last shows for a while");
        System.out.println("PASS: MPK mini knobs: CC70-77 map, pickup on start, outside changes, new controls and lists, empty slots");
    }
}
