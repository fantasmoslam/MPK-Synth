/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth.ui;

import java.util.ArrayList;

/** Host checks of the round-three interface rules, without Android. */
public final class UiTest {
    /** Records what is drawn; text widths are approximate. */
    static final class RecordingGfx implements Gfx {
        final ArrayList<float[]> lines=new ArrayList<>();final ArrayList<String> texts=new ArrayList<>();
        public void rect(float x,float y,float w,float h,int c){}
        public void frame(float x,float y,float w,float h,float width,int c){}
        public void line(float x1,float y1,float x2,float y2,float w,int c){lines.add(new float[]{x1,y1,x2,y2,w,c});}
        public void circle(float cx,float cy,float r,int c){}
        public void ring(float cx,float cy,float r,float w,int c){}
        public void arc(float cx,float cy,float r,float s,float sw,float w,int c){}
        public void polygon(float[] xy,int c){}
        public void polyline(float[] xy,boolean closed,float w,int c,float dash){}
        public void text(String s,float x,float y,int f,float size,int c,int a,float t){texts.add(s);}
        public float textWidth(String s,int f,float size,float t){return s.length()*size*.6f;}
        public void clip(float x,float y,float w,float h){}
        public void unclip(){}
    }
    static final class FakeEngine implements Engine {
        final int[] v=new int[48];int scapeCommands;boolean stopped;
        public int value(int s){return v[s];}
        public int min(int s){return 0;}
        public int max(int s){return 100;}
        public String text(int s){return Integer.toString(v[s]);}
        public void set(int s,int value){v[s]=value;}
        public boolean scape(int c){scapeCommands++;return true;}
        public boolean audioOn(){return true;}
        public boolean musicPlaying(){return true;}
        public int master(){return 700;}
        public void setMaster(int value){}
        public void stopAll(){stopped=true;}
        public void saveSession(){}
        public void action(String name){}
    }

    static float pointerDegrees(RecordingGfx g){
        for(float[] l:g.lines)if((int)l[5]==Theme.REQUEST&&Math.abs(l[4]-3.2f)<.01f){
            double deg=Math.toDegrees(Math.atan2(l[2]-l[0],-(l[3]-l[1])));return (float)((deg+360)%360);
        }
        throw new AssertionError("no knob pointer drawn");
    }
    static RecordingGfx knob(float value,boolean bipolar){
        Ui ui=new Ui();RecordingGfx g=new RecordingGfx();ui.begin(g,1280,800,0);
        ui.knob("k",100,100,72,value,0,100,bipolar,"x",v->{});ui.end();return g;
    }
    static void near(float a,float b,String what){if(Math.abs(a-b)>1)throw new AssertionError(what+": "+a+" vs "+b);}

    public static void main(String[] args){
        // Knob zero (review amendment 2): unipolar minimum at 7 o'clock, maximum at 5, middle at 12.
        near(pointerDegrees(knob(0,false)),210,"minimum points to 7 o'clock");
        near(pointerDegrees(knob(100,false)),150,"maximum points to 5 o'clock");
        near(pointerDegrees(knob(50,false)),0,"half points to 12");
        RecordingGfx bip=knob(50,true);
        boolean tick=false;for(float[] l:bip.lines)if((int)l[5]==Theme.PHOSPHOR&&l[0]==100&&l[2]==100)tick=true;
        if(!tick)throw new AssertionError("bipolar knob shows a centre tick");
        for(float[] l:knob(50,false).lines)if((int)l[5]==Theme.PHOSPHOR)throw new AssertionError("unipolar knob has no centre tick");

        // Both layouts draw every screen, and switching keeps every value.
        UiState state=new UiState();FakeEngine engine=new FakeEngine();Ui ui=new Ui();Screens screens=new Screens(ui,state,engine);
        for(UiState.Layout layout:UiState.Layout.values())for(UiState.Screen screen:UiState.Screen.values())
            for(float width:new float[]{1280,1333}){
                state.layout=layout;state.screen=screen;RecordingGfx g=new RecordingGfx();
                ui.begin(g,width,800,0);screens.draw();ui.end();
                if(!g.texts.contains("STOP ALL"))throw new AssertionError("Stop All on "+layout+" "+screen);
            }

        // Drag a conductor note in Focus; the value holds through a layout switch.
        state.layout=UiState.Layout.FOCUS;state.screen=UiState.Screen.INSTRUMENT;state.notes[0]=34;
        ui.begin(new RecordingGfx(),1280,800,0);screens.draw();ui.end();
        float kx=141+((1280-255-141-33)/4f)*.28f,ky=129+122;
        ui.down(1,kx,ky);ui.move(1,kx,ky-36);
        if(state.notes[0]<=34)throw new AssertionError("dragging up raises Danger: "+state.notes[0]);
        int dragged=state.notes[0];ui.up(1,kx,ky-36);
        state.layout=UiState.Layout.PERFORMANCE;ui.begin(new RecordingGfx(),1280,800,10);screens.draw();ui.end();
        if(state.notes[0]!=dragged)throw new AssertionError("layout switch keeps notes");

        // Stop All stays reachable while a choice sheet is open.
        state.layout=UiState.Layout.FOCUS;screens.choose("Biome",Screens.BIOMES,0,v->{});
        ui.begin(new RecordingGfx(),1280,800,20);screens.draw();ui.end();
        ui.down(2,323+64,723+23);ui.up(2,323+64,723+23);
        if(!engine.stopped)throw new AssertionError("Stop All works over a sheet");

        // Every Performance dock button sits above the bottom bar.
        state.layout=UiState.Layout.PERFORMANCE;state.screen=UiState.Screen.MIXER;
        ui.begin(new RecordingGfx(),1280,800,30);screens.draw();ui.end();
        ui.down(3,1263-20,660);ui.up(3,1263-20,660);
        if(state.screen!=UiState.Screen.SETTINGS)throw new AssertionError("last dock button (Settings) is reachable above the bar");

        // Saved state round trip.
        UiState copy=new UiState();copy.load(state.save());
        if(copy.notes[0]!=dragged||copy.layout!=state.layout)throw new AssertionError("UI state round trip");
        System.out.println("PASS: interface rules (knob zero, layouts, dragging, Stop All over sheets, dock, saved state)");
    }
}
