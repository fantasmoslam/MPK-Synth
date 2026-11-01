/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth.ui;

import java.util.ArrayList;

/** Host checks of the round-three interface rules, without Android. */
public final class UiTest {
    /** Records what is drawn; text widths are approximate. */
    static class RecordingGfx implements Gfx {
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
        public String[] names(int s){return new String[]{"Bed","Melody","Bass","Pulse"};}
        public Voice voice(int part){Param[] p=new Param[8];for(int k=0;k<8;k++)p[k]=new Param(k==7?"-":"P"+k,k==0,0,k==1?127:6,v[40+k%8],k==2,"x");return new Voice(0,"Analog","Warm",p);}
        public String[] valueNames(int part,int param){return new String[]{"A","B","C"};}
        int lastParam=-1,lastParamValue;
        public void setParam(int part,int param,int value){lastParam=param;lastParamValue=value;}
        public Choice[] engines(){return new Choice[]{new Choice(0,"Analog"),new Choice(2,"Phase")};}
        public Choice[] presets(int engine){return new Choice[]{new Choice(0,"Warm")};}
        int engineSet=-1;
        public void setEngine(int part,int engine){engineSet=engine;}
        public void setPreset(int part,int preset){}
        public Library library(){return null;}
        public boolean habitatEngine(){return true;}
        final FakeRecorder recorder=new FakeRecorder();
        public Recorder recorder(){return recorder;}
        int fadeMs=-1;boolean faded;
        public void fade(int ms){fadeMs=ms;}
        public boolean faded(){return faded;}
    }

    static final class FakeRecorder implements Recorder {
        int state=READY;int stops;String name;
        public int state(){return state;}
        public long takeMillis(){return 1000;}
        public String takeName(){return name;}
        public String error(){return state==ERROR?"disk full":null;}
        public float[] levels(){return new float[0];}
        public long freeBytes(){return 4_000_000_000L;}
        public String destination(){return "Music/Paleotone";}
        public void start(String run){state=RECORDING;name=run+"-take-01.wav";}
        public void stop(){if(state==RECORDING){state=SAVED;stops++;}}
        public void retry(){state=SAVED;}
        public void discard(){state=READY;}
        public Take[] takes(){return name==null?new Take[0]:new Take[]{new Take(name,1,state,null)};}
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

        // Sound design: every voice change goes through the sheet to the engine.
        state.layout=UiState.Layout.FOCUS;state.screen=UiState.Screen.SOUND_DESIGN;
        RecordingGfx dg=new RecordingGfx();ui.begin(dg,1280,800,40);screens.draw();ui.end();
        if(!dg.texts.contains("P1")||dg.texts.contains("-"))throw new AssertionError("sound design shows used parameters and hides blank ones");
        float cw=(1280-17-140-13*3)/4f,ch=(591-98-13)/2f;
        float px=140+cw+cw/2,py=94+98+ch*.635f;  // second card's knob
        ui.down(4,px,py);ui.move(4,px,py-60);ui.up(4,px,py-60);
        if(engine.lastParam!=1||engine.lastParamValue<=0)throw new AssertionError("dragging a parameter knob sets it: "+engine.lastParam);
        // Habitat layer mute remembers the level and restores it.
        state.screen=UiState.Screen.MIXER;state.mixerPage=1;engine.v[Engine.LAYER+2]=40;
        ui.begin(new RecordingGfx(),1280,800,50);screens.draw();ui.end();
        float lcw=(1123-26-30)/4f,lx=140+13+2*(lcw+10)+lcw-75+32,ly=94+65+43+9+23;
        ui.down(5,lx,ly);ui.up(5,lx,ly);
        if(engine.v[Engine.LAYER+2]!=0||state.layerMuted[2]!=40)throw new AssertionError("layer mute sets 0 and remembers the level");
        ui.begin(new RecordingGfx(),1280,800,60);screens.draw();ui.end();ui.down(5,lx,ly);ui.up(5,lx,ly);
        if(engine.v[Engine.LAYER+2]!=40||state.layerMuted[2]!=-1)throw new AssertionError("unmute restores the level");
        // Quick mix opens over any screen and keeps Stop All live.
        state.screen=UiState.Screen.INSTRUMENT;state.quickMix=true;engine.stopped=false;
        RecordingGfx qg=new RecordingGfx();ui.begin(qg,1280,800,70);screens.draw();ui.end();
        if(!qg.texts.contains("QUICK MIX"))throw new AssertionError("quick mix drawn");
        ui.down(6,323+64,723+23);ui.up(6,323+64,723+23);
        if(!engine.stopped)throw new AssertionError("Stop All works under quick mix");
        state.quickMix=false;

        // Run timer: counts only with audio, ends at its length with a five-second fade that the take records.
        UiState rs=new UiState();FakeEngine re=new FakeEngine();Run run=new Run(rs,re);
        rs.runLength=60000;rs.runElapsed=0;
        run.tick(0,30000,false);if(rs.runElapsed!=0)throw new AssertionError("run clock waits for audio");
        run.tick(0,50000,true);run.tick(50000,20000,true);
        if(rs.runPhase!=UiState.ENDING||rs.runElapsed!=60000||re.fadeMs!=Run.FADE_MS)throw new AssertionError("timed end holds the clock and fades");
        re.recorder.start("RUN");run.tick(52000,2000,true);
        if(rs.runPhase!=UiState.ENDING||re.recorder.state!=Recorder.RECORDING||re.stopped)throw new AssertionError("the fade runs its five seconds while recording");
        run.tick(55000,3000,true);
        if(rs.runPhase!=UiState.ENDED||re.recorder.state!=Recorder.SAVED||!re.stopped)throw new AssertionError("after the fade the take saves and sound stops");
        // Stop All during the fade ends at once and keeps the take; a new run uses the next length.
        rs=new UiState();re=new FakeEngine();run=new Run(rs,re);rs.runLength=0;rs.nextRunLength=15*60000L;
        run.tick(0,100000,true);if(rs.runPhase!=UiState.RUNNING)throw new AssertionError("continuous runs never time out");
        re.recorder.start("RUN");run.end(1000);run.stopAll();
        if(rs.runPhase!=UiState.ENDED||re.recorder.stops!=1||!re.stopped)throw new AssertionError("Stop All during the fade saves the take and stops");
        run.newRun();if(rs.runLength!=15*60000L||rs.runElapsed!=0||rs.runPhase!=UiState.RUNNING||rs.runNumber!=2)throw new AssertionError("new run");
        // The end card and recording screen draw in both layouts.
        Screens rsScreens=new Screens(new Ui(),rs,re);
        for(UiState.Layout l:UiState.Layout.values())for(int phase=0;phase<3;phase++){
            rs.layout=l;rs.runPhase=phase;rs.screen=UiState.Screen.EXPEDITION;rs.endDismissed=false;RecordingGfx rg=new RecordingGfx();
            Ui u=new Ui();Screens sc=new Screens(u,rs,re);u.begin(rg,1280,800,0);sc.draw();u.end();
            if(phase==UiState.ENDED&&!rg.texts.contains("Run complete."))throw new AssertionError("run complete card");
            if(phase==UiState.ENDING&&!rg.texts.contains("The expedition is closing."))throw new AssertionError("closing card");
        }
        rs.runPhase=UiState.RUNNING;

        // Saved state round trip.
        UiState copy=new UiState();copy.load(state.save());
        if(copy.notes[0]!=dragged||copy.layout!=state.layout)throw new AssertionError("UI state round trip");
        state.layerMuted[5]=22;state.designPart=2;copy=new UiState();copy.load(state.save());
        if(copy.layerMuted[5]!=22||copy.designPart!=2)throw new AssertionError("layer mutes and part survive saving");
        state.runElapsed=123456;state.runLength=0;state.nextRunLength=60*60000L;state.runPhase=UiState.ENDING;state.runNumber=4;
        copy=new UiState();copy.load(state.save());
        if(copy.runElapsed!=123456||copy.runLength!=0||copy.nextRunLength!=60*60000L||copy.runPhase!=UiState.ENDED||copy.runNumber!=4)
            throw new AssertionError("run clock survives saving; an interrupted fade comes back ended");
        copy=new UiState();copy.load("FOCUS;LEGACY;1;2;3;4;5;6;7;8");
        if(copy.screen!=UiState.Screen.INSTRUMENT||copy.notes[7]!=8)throw new AssertionError("a saved legacy screen falls back to INSTRUMENT");
        expedition();
        System.out.println("PASS: interface rules (knob zero, layouts, dragging, Stop All over sheets and quick mix, dock, sound design, layer mutes, run timer and end, saved state, valley, expedition, map, journal, bestiary, start)");
    }

    /** The seeded valley, the stand-in expedition, the map's touch handling and the expedition screens. */
    static void expedition(){
        // The same seed gives the same valley; another seed gives another one.
        Valley a=new Valley(472),b=new Valley(472),c=new Valley(9071);
        if(a.stations[7].x!=b.stations[7].x||!a.regions[3].name.equals(b.regions[3].name))throw new AssertionError("valley is seeded");
        if(a.stations[7].x==c.stations[7].x&&a.stations[7].y==c.stations[7].y)throw new AssertionError("seeds differ");
        for(int seed=0;seed<200;seed++){
            Valley v=new Valley(seed);
            if(v.stations.length!=16||v.regions.length!=7||v.river.length<8)throw new AssertionError("valley parts, seed "+seed);
            for(Valley.Place p:v.stations)if(!Valley.inside(v.outline,p.x,p.y))throw new AssertionError("station inside the valley, seed "+seed);
            for(Valley.Place p:v.stations)if(Valley.inside(v.lake,p.x,p.y))throw new AssertionError("no station in the lake, seed "+seed);
            java.util.HashSet<Integer> t=new java.util.HashSet<>();for(Valley.Region r:v.regions)t.add(r.terrain);
            if(t.size()!=7)throw new AssertionError("seven terrains, seed "+seed);
        }
        // Move on walks the route; encounters run through their eight stages; the journal and bestiary follow.
        Expedition ex=new Expedition(472,"WELLINGTON");
        if(ex.journal().size()!=1)throw new AssertionError("journal opens at base camp");
        int guard=0;
        while(ex.encounters.isEmpty()&&guard++<20)ex.act(Expedition.MOVE_ON);
        Expedition.Encounter e=ex.current();
        if(e==null||e.stage!=0||e.identified())throw new AssertionError("an encounter starts with signs");
        for(int i=0;i<7;i++)ex.act(Expedition.OBSERVE);
        if(e.stage!=7||!e.identified()||ex.firstEncounter(e.species)!=0||ex.discovered()!=1)throw new AssertionError("observing reaches the coda and records the species");
        boolean entry=false;for(Expedition.Entry j:ex.journal())if(j.species==e.species)entry=true;
        if(!entry)throw new AssertionError("the encounter is in the journal");
        for(int i=0;i<8;i++)if(ex.stageLine(e,i).isEmpty())throw new AssertionError("stage wording");
        ex.act(Expedition.OBSERVE);if(ex.current()!=null)throw new AssertionError("encounter ends after the coda");
        int at=ex.station;ex.act(Expedition.MOVE_ON);if(ex.station!=at+1)throw new AssertionError("move on");
        Expedition back=new Expedition(472,"WELLINGTON");back.load(ex.save());
        if(back.station!=ex.station||back.encounters.size()!=ex.encounters.size()||back.discovered()!=ex.discovered())throw new AssertionError("expedition round trip");
        // While the run clock counts, the company moves on by itself.
        Expedition timed=new Expedition(5,"VERONICA");timed.tick(60000,false,30*60000L);
        if(timed.station!=0)throw new AssertionError("the expedition waits for sound");
        for(int i=0;i<40&&timed.station==0;i++)timed.tick(30000,true,30*60000L);
        if(timed.station==0)throw new AssertionError("the expedition travels while the run plays");

        // Expedition screens in both layouts at both widths.
        UiState s=new UiState();FakeEngine fe=new FakeEngine();Ui ui=new Ui();Screens sc=new Screens(ui,s,fe);
        s.expedition().act(Expedition.FOLLOW_HERD);
        for(UiState.Layout l:UiState.Layout.values())for(float w:new float[]{1280,1333})for(int view=0;view<6;view++){
            s.layout=l;s.mapOpen=view==1;s.mapTools=view==1;s.encounterOpen=view==2;
            s.screen=view<3?UiState.Screen.EXPEDITION:view==3?UiState.Screen.JOURNAL:view==4?UiState.Screen.BESTIARY:UiState.Screen.START;
            RecordingGfx g=new RecordingGfx();ui.begin(g,w,800,0);sc.draw();ui.end();
            if(!g.texts.contains("STOP ALL"))throw new AssertionError("Stop All on "+l+" view "+view);
            if(view==0&&!g.texts.contains("YOU ARE HERE"))throw new AssertionError("survey shows the position");
            if(view==1&&(!g.texts.contains("TERRAIN KEY")||!g.texts.contains("Whole valley")))throw new AssertionError("map key and tools");
        }
        // Labels keep one on-screen size at every zoom (review amendment 1).
        s.layout=UiState.Layout.FOCUS;s.screen=UiState.Screen.EXPEDITION;s.mapOpen=true;s.mapTools=false;s.mapScale=0;
        float[] sizes=new float[2];
        for(int z=0;z<2;z++){
            if(z==1)s.mapScale=3;
            SizeGfx g=new SizeGfx();ui.begin(g,1280,800,0);sc.draw();ui.end();
            sizes[z]=g.size("YOU ARE HERE");
        }
        if(sizes[0]<=0||sizes[0]!=sizes[1])throw new AssertionError("map labels stay one size when zoomed: "+sizes[0]+" vs "+sizes[1]);
        // Pinch zooms, one finger pans, and a tap while plotting adds a point to your path.
        s.mapScale=0;ui.begin(new RecordingGfx(),1280,800,0);sc.draw();ui.end();
        float mx=140+400,my=94+64+200;
        ui.down(1,mx-40,my);ui.down(2,mx+40,my);ui.move(1,mx-90,my);ui.move(2,mx+90,my);ui.up(1,mx-90,my);ui.up(2,mx+90,my);
        if(s.mapScale<=0)throw new AssertionError("pinch zooms in");
        float cx=s.mapX;ui.begin(new RecordingGfx(),1280,800,0);sc.draw();ui.end();
        ui.down(3,mx,my);ui.move(3,mx-60,my);ui.up(3,mx-60,my);
        if(s.mapX<=cx)throw new AssertionError("dragging left pans east");
        s.plotting=true;s.override=null;s.mapScale=0;ui.begin(new RecordingGfx(),1280,800,0);sc.draw();ui.end();
        Valley.Place here=s.expedition().place();
        float k=ValleyMap.fit(s.expedition().valley,1123-214,591-64-77),tx=140+(1123-214)/2f+(here.x-s.mapX)*k+20,ty=94+64+(591-64-77)/2f+(here.y-s.mapY)*k;
        ui.down(4,tx,ty);ui.up(4,tx,ty);
        if(s.override==null||s.override.length!=2)throw new AssertionError("a tap adds a path point");
        UiState copy=new UiState();copy.load(s.save());
        if(copy.override==null||copy.override.length!=2||copy.valleySeed!=s.valleySeed||!copy.expedition().save().equals(s.expedition().save()))
            throw new AssertionError("expedition, seed and path survive saving");
        // Start: the chosen leader and seed take over when a new run begins.
        s.nextLeader=2;s.nextSeed=1234;sc.run.newRun();
        if(s.valleySeed!=1234||!s.leader.equals("JONESY")||s.expedition().station!=0||s.override!=null)throw new AssertionError("new run applies leader and seed");
    }
    /** Records the size each text was drawn at. */
    static final class SizeGfx extends RecordingGfx{
        final java.util.HashMap<String,Float> sizes=new java.util.HashMap<>();
        @Override public void text(String t,float x,float y,int f,float size,int c,int a,float tr){super.text(t,x,y,f,size,c,a,tr);sizes.put(t,size);}
        float size(String t){Float v=sizes.get(t);return v==null?-1:v;}
    }
}
