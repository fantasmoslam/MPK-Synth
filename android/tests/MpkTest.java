/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth.ui;

/** MPK mini Plus knobs: message mapping, pickup (soft takeover) and the knob strip. */
public final class MpkTest {
    static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}

    /** Part 0's voice: param 0 is 0..127, param 1 a four-value list, param 7 unused. */
    static final class Voices extends UiTest.FakeEngine {
        int engine,sets;final int[][] parts={{64,1,0,0,0,0,0,0},{20,0,0,0,0,0,0,0}};int[] value=parts[0];
        public Voice voice(int part){int[] value=parts[part];
            Param[] p=new Param[8];
            p[0]=new Param("CUT",false,0,127,value[0],false,value[0]+"");
            p[1]=new Param("WAVE",true,0,3,value[1],false,"W"+value[1]);
            for(int k=2;k<7;k++)p[k]=new Param("P"+k,false,0,100,value[k],false,value[k]+"");
            p[7]=new Param("-",false,0,0,0,false,"");
            return new Voice(engine,"Analog","Warm",p);
        }
        public void setParam(int part,int k,int v){parts[part][k]=v;sets++;}
    }

    public static void main(String[] args){
        // The verified MIDI map: channel 1 CC70-77 are knobs 1-8; nothing else is.
        check(Mpk.knob(0xb0,70)==0&&Mpk.knob(0xb0,77)==7,"CC70-77 on channel 1 are the knobs");
        check(Mpk.knob(0xb0,69)<0&&Mpk.knob(0xb0,78)<0,"neighbouring CCs are not knobs");
        check(Mpk.knob(0xb1,70)<0,"other channels are not knobs");
        check(Mpk.knob(0x90,70)<0,"notes are not knobs");
        check(Mpk.isMpk("MPK mini Plus")&&Mpk.isMpk("Akai MPK Mini 3")&&!Mpk.isMpk("SMK-37"),"device names");

        Voices e=new Voices();Mpk m=new Mpk();m.connected(true);long t=1000;
        // Knob 1 starts far below CUT (64): nothing happens until it reaches it.
        m.move(0,10,e,0,t+=10);
        check(e.value[0]==64&&!m.pickedUp(0),"a knob away from the value does not jump it");
        check(m.hint(0,e.voice(0).params[0]).equals("TURN UP"),"hint says which way");
        m.move(0,40,e,0,t+=10);
        check(e.value[0]==64,"still below");
        m.move(0,70,e,0,t+=10);
        check(m.pickedUp(0)&&e.value[0]==70,"crossing the value takes over: "+e.value[0]);
        m.move(0,30,e,0,t+=10);
        check(e.value[0]==30,"then it follows freely");

        // A change from elsewhere (preset, screen) releases the knob once the engine has settled.
        e.value[0]=100;
        m.move(0,32,e,0,t+=1000);
        check(!m.pickedUp(0)&&e.value[0]==100,"an outside change releases the knob");
        m.move(0,99,e,0,t+=10);
        check(m.pickedUp(0)&&e.value[0]==99,"landing within a step picks up");

        // Another part, or a new voice, releases it too.
        m.move(0,120,e,1,t+=10);
        check(!m.pickedUp(0)&&e.parts[1][0]==20&&e.value[0]==99,"a new part needs pickup");
        m.move(0,21,e,1,t+=10);check(m.pickedUp(0)&&e.parts[1][0]==21,"picked up on part 2");
        e.engine=3;e.parts[1][0]=60;m.move(0,40,e,1,t+=10);
        check(e.parts[1][0]==60,"no jump on a new voice");
        check(!m.pickedUp(0),"a new voice needs pickup");

        // A short list: one value either way of the position picks up, steps follow.
        m.move(1,127,e,0,t+=10);
        check(!m.pickedUp(1),"far from the list value");
        m.move(1,40,e,0,t+=10);
        check(m.pickedUp(1)&&e.value[1]==1,"crossing the list value, which stays 1");
        m.move(1,127,e,0,t+=10);check(e.value[1]==3,"top of the knob is the last value");

        // Unused slots do nothing.
        int sets=e.sets;m.move(7,0,e,0,t+=10);m.move(7,127,e,0,t+=10);
        check(e.sets==sets&&m.hint(7,e.voice(0).params[7]).isEmpty(),"unused slot ignored");

        // Disconnecting releases every knob.
        m.connected(false);check(!m.pickedUp(1),"disconnect releases");

        // The strip shows every knob's name and value after a move, and hides again.
        Ui ui=new Ui();UiTest.RecordingGfx g=new UiTest.RecordingGfx();
        ui.begin(g,1280,800,t);m.draw(ui,e,0,3,17,629,1246);ui.end();
        check(g.texts.contains("CUT")&&g.texts.contains("WAVE")&&g.texts.contains("K8"),"strip names the knobs: "+g.texts);
        g=new UiTest.RecordingGfx();ui.begin(g,1280,800,t+Mpk.SHOW_MS+1);m.draw(ui,e,0,3,17,629,1246);ui.end();
        check(g.texts.isEmpty(),"strip hides after a while");
        // Transport buttons do what the bottom bar does.
        UiState state=new UiState();UiTest.FakeEngine fe=new UiTest.FakeEngine();Screens screens=new Screens(new Ui(),state,fe);
        screens.mpkTransport(MpkRouter.Transport.PLAY,0);
        check(fe.scapeCommands==1&&state.notice.contains("music stopped"),"Play toggles the music: "+state.notice);
        screens.mpkTransport(MpkRouter.Transport.RECORD,0);
        check(fe.recorder.state==Recorder.RECORDING,"Record starts a take");
        screens.mpkTransport(MpkRouter.Transport.RECORD,0);
        check(fe.recorder.state==Recorder.SAVED&&fe.recorder.stops==1,"Record again stops it");
        screens.mpkTransport(MpkRouter.Transport.STOP,0);
        check(fe.stopped,"Stop is STOP ALL");
        // The pads part is saved with the interface.
        state.padPart=1;UiState again=new UiState();again.load(state.save());
        check(again.padPart==1,"pads part saved");
        UiState older=new UiState();older.load("FOCUS;INSTRUMENT");check(older.padPart==3,"older saves keep part 4 for pads");
        System.out.println("PASS: MPK mini knobs: CC70-77 mapping, pickup on start, part, voice and outside changes, the knob strip, transport buttons and the pads part");
    }
}
