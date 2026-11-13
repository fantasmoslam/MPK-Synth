/* SPDX-License-Identifier: GPL-3.0-only */
package org.fantasmoslam.mpksynth.ui;

import java.util.ArrayList;
import java.util.List;

/** The MPK mini Plus router: keys, pads, pressure, joystick, knobs and transport, from the verified mapping. */
public final class MpkRouterTest {
    static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}
    static final List<String> sent=new ArrayList<>(),controls=new ArrayList<>();
    static int played,pads,lastBend,program=-1;
    static String m(int s,int a,int b){return String.format("%02X %d %d",s,a,b);}
    static void expect(String what,String... messages){
        check(sent.equals(List.of(messages)),what+": "+sent);sent.clear();
    }

    public static void main(String[] args){
        MpkRouter r=new MpkRouter((s,a,b)->sent.add(m(s,a,b)),new MpkRouter.Controls(){
            public void knob(int k,int v){controls.add("knob "+k+" "+v);}
            public void transport(MpkRouter.Transport t,boolean p){controls.add(t+" "+p);}
            public void played(boolean pad,int note){played++;if(pad)pads++;}
            public void joystick(int bend){lastBend=bend;}
            public void program(int n){program=n;}
        });

        // Keys play the keys part (Sound design's), whatever channel they send on.
        r.keyPart(1);
        r.message(0x90,60,100);r.message(0x80,60,0);
        expect("keys on part 2","91 60 100","81 60 0");
        check(played==1,"a key tells the app, so it can start audio");
        r.message(0x92,62,90);r.message(0x92,62,0);
        expect("any key channel, velocity 0 ends it","91 62 90","81 62 0");

        // A note held across a part change ends where it began.
        r.message(0x90,64,80);r.keyPart(2);r.message(0x80,64,0);r.message(0x90,65,70);
        expect("held key ends on its own part","91 64 80","81 64 0","92 65 70");
        r.message(0x80,65,0);sent.clear();

        // Pads: channel 10 notes 36-51 play the pads part (part 4 by default), velocity kept.
        r.message(0x99,36,81);r.message(0x89,36,0);r.message(0x99,51,94);r.message(0x99,51,0);
        expect("pads on part 4","93 36 81","83 36 0","93 51 94","83 51 0");
        check(played==6&&pads==2,"pads tell the app too: "+played);
        r.message(0x99,60,100);expect("other channel-10 notes are ignored");

        // Pad pressure becomes channel aftertouch on the pads part: the strongest held pad.
        r.message(0x99,36,90);r.message(0x99,38,90);sent.clear();
        r.message(0xA9,36,40);r.message(0xA9,38,70);r.message(0xA9,36,50);r.message(0xA9,38,20);
        expect("strongest pressure","D3 40 0","D3 70 0","D3 50 0");
        r.message(0x89,36,0);r.message(0x89,38,0);
        expect("pressure falls as pads lift","83 36 0","D3 20 0","83 38 0","D3 0 0");

        // Pads part change, with a pad held.
        r.message(0x99,40,60);r.padPart(0);r.message(0x89,40,0);r.message(0x99,40,61);r.message(0x89,40,0);
        expect("held pad ends on its own part","93 40 60","83 40 0","90 40 61","80 40 0");

        // Joystick: sideways bends up, up/down bends down, on the keys part; centre is pitch.
        r.message(0xB0,12,127);expect("full sideways = top of bend","E2 127 127");
        r.message(0xB0,12,0);expect("back to centre","E2 0 64");
        r.message(0xB0,2,127);expect("full up/down = bottom of bend","E2 0 0");
        r.message(0xB0,12,127);expect("both axes cancel","E2 127 63");
        r.keyPart(0);r.message(0xB0,2,0);
        expect("part change returns the old part to pitch","E2 0 64","E0 127 127");
        r.message(0xB0,12,0);sent.clear();

        // Knobs and transport never reach the engine.
        r.message(0xB0,70,50);r.message(0xB0,77,7);
        r.message(0xB0,118,127);r.message(0xB0,117,127);r.message(0xB0,119,127);r.message(0xB0,119,0);
        expect("knobs and buttons are the app's");
        check(controls.equals(List.of("knob 0 50","knob 7 7","PLAY true","STOP true","RECORD true","RECORD false")),"controls: "+controls);

        // Other key-channel messages follow the keys part (sustain, channel pressure).
        r.message(0xB0,64,127);r.message(0xD0,30,0);
        expect("sustain and pressure follow the keys","B0 64 127","D0 30 0");

        // Unplugging ends everything that is sounding.
        r.message(0x90,48,90);r.message(0x99,44,90);r.message(0xB0,12,64);sent.clear();
        r.release();
        expect("release","80 44 0","80 48 0","E0 0 64");
        r.keyPart(0);r.message(0xb0,Mpk.CC_JOYSTICK_X,127);
        check(lastBend==8191,"the joystick reports its bend: "+lastBend);
        r.message(0xb0,Mpk.CC_JOYSTICK_X,0);check(lastBend==0,"and its return: "+lastBend);
        sent.clear();r.message(0xc9,2,0);
        check(program==2&&sent.isEmpty(),"a program change opens a tab and never reaches the engine");
        System.out.println("PASS: MPK router: keys follow the keys part, pads on their own part with pressure, joystick bend, knobs and transport kept from the engine, nothing left hanging");
    }
}
