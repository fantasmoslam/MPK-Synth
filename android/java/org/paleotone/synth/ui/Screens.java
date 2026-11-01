/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth.ui;

import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * The round-three shell: header, navigation (Focus rail or Performance dock), the
 * screens, the fixed bottom bar and the choice sheet. Layout is in units on an
 * 800-unit-high landscape screen; widths flex from 1280 (SM-T510) to 1333 (Tab S6 Lite).
 */
public final class Screens {
    public static final String[] BIOMES={"Water","Jungle","Tundra","Forest","Plains","Mountains","Canyons","Caldera"};
    public static final String[] MOODS={"Minor","Dorian","Phrygian","Harmonic minor","Major","Mixolydian"};

    private final Ui ui;private final UiState s;private final Engine e;
    private Gfx g;private float W,H;
    private String sheetTitle;private String[] sheetOptions;private int sheetSelected;private IntConsumer sheetPick;

    private final FieldScreens field;
    public Screens(Ui ui,UiState state,Engine engine){this.ui=ui;s=state;e=engine;run=new Run(state,engine);field=new FieldScreens(ui,state,this);}

    public void draw(){
        g=ui.g;W=ui.width;H=ui.height;
        s.expedition(); // names the run from its valley before the header draws
        g.rect(0,0,W,H,Theme.BACKGROUND);
        header();
        boolean focus=s.layout==UiState.Layout.FOCUS;
        if(focus)rail();else dock();
        float cx=focus?140:17,cy=94,cw=W-17-cx,ch=focus?591:529;
        switch(s.screen){
            case INSTRUMENT:if(focus)instrumentFocus();else instrumentPerformance();break;
            case SETTINGS:settings(cx,cy,cw,ch);break;
            case RECORDING:recording(cx,cy,cw,ch);break;
            case EXPEDITION:if(s.runPhase!=UiState.RUNNING&&!s.endDismissed)runEnd(cx,cy,cw,ch);else field.expedition(cx,cy,cw,ch,focus);break;
            case JOURNAL:field.journal(cx,cy,cw,ch,focus);break;
            case BESTIARY:field.bestiary(cx,cy,cw,ch,focus);break;
            case START:field.start(cx,cy,cw,ch,focus);break;
            case MIXER:mixer(cx,cy,cw,ch,focus);break;
            case SOUND_DESIGN:soundDesign(cx,cy,cw,ch,focus);break;
            case LIBRARY:library(cx,cy,cw,ch);break;
            default:
        }
        if(s.quickMix)quickMix(cx,cy,cw,ch);
        if(runSheet)runLengthSheet(cx,cy,cw,ch);
        if(sheetOptions!=null)sheet(cx,cy,cw,ch);
        bottomBar();
        notice();
    }

    // ---- header ----------------------------------------------------------------------
    private void header(){
        g.text("PALEOTONE",16,42,Gfx.DISPLAY,23.5f,Theme.PHOSPHOR,Gfx.LEFT,.02f);
        g.text(s.screen.label.toUpperCase(Locale.ROOT),16,68,Gfx.MONO,13,Theme.MUTED,Gfx.LEFT,.16f);
        titleHold();
        g.line(247,19,247,67,1,Theme.BORDER);
        g.text("CURRENT RUN",261,27,Gfx.MONO,7.5f,Theme.MUTED,Gfx.LEFT,.14f);
        g.text(s.runName,261,46,Gfx.MONO,13,Theme.TEXT,Gfx.LEFT,.03f);
        g.text(s.leader+" / "+s.flavour,261,64,Gfx.MONO,9,Theme.MUTED,Gfx.LEFT,.06f);
        float ex=W-506;
        String key=listName(Engine.MOOD,MOODS).toUpperCase(Locale.ROOT)+"   "+e.text(Engine.TEMPO).toUpperCase(Locale.ROOT);
        g.text(key,ex-13,47,Gfx.MONO,12,Theme.PHOSPHOR,Gfx.RIGHT,.04f);
        g.text(runCaption(),ex,32,Gfx.MONO,7.5f,s.runPhase==UiState.ENDING?Theme.REQUEST:Theme.MUTED,Gfx.LEFT,.12f);
        g.text(runClock(),ex,52,Gfx.MONO,14,Theme.TEXT,Gfx.LEFT,0);
        runBar(ex,60,130);
        ui.hit("header.runlength",ex-6,20,150,48,this::openRunLength);
        boolean focus=s.layout==UiState.Layout.FOCUS;
        ui.button("layout.focus",W-17-96-5-54,22,54,43,"Focus",focus?Ui.SELECTED:Ui.NORMAL,true,()->setLayout(UiState.Layout.FOCUS));
        ui.button("layout.performance",W-17-96,22,96,43,"Performance",focus?Ui.NORMAL:Ui.SELECTED,true,()->setLayout(UiState.Layout.PERFORMANCE));
        g.line(16,81,W-16,81,1,Theme.BORDER);
    }
    private void setLayout(UiState.Layout l){s.layout=l;}
    private long holdStart=-1;private boolean holdFired;
    /** Holding the PALEOTONE title still for three seconds opens kiosk maintenance (KIOSK.md). */
    private void titleHold(){
        ui.hitDrag("title",8,8,232,70,new Ui.Drag(){
            float x0,y0;
            public void start(float x,float y){x0=x;y0=y;holdStart=ui.now;holdFired=false;}
            public void move(float x,float y){if(Math.abs(x-x0)>16||Math.abs(y-y0)>16)holdStart=-1;}
            public void end(){holdStart=-1;}
        });
        if(holdStart>=0&&!holdFired&&ui.now-holdStart>=3000){holdFired=true;holdStart=-1;e.action("kiosk");}
    }
    static String clock(long ms){
        long t=ms/1000,h=t/3600,m=t/60%60,sec=t%60;
        return h>0?String.format(Locale.ROOT,"%d:%02d:%02d",h,m,sec):String.format(Locale.ROOT,"%02d:%02d",m,sec);
    }

    // ---- navigation ------------------------------------------------------------------
    private void rail(){
        for(int i=0;i<UiState.NAVIGATION.length;i++){
            final UiState.Screen n=UiState.NAVIGATION[i];
            boolean on=s.screen==n||(n==UiState.Screen.INSTRUMENT&&s.screen==UiState.Screen.SOUND_DESIGN)||(n==UiState.Screen.LIBRARY&&s.screen==UiState.Screen.RECORDING);
            ui.button("nav."+n,16,94+i*59,112,51,n.label,on?Ui.SELECTED:Ui.NORMAL,true,()->go(n));
        }
    }
    private void dock(){
        int count=UiState.NAVIGATION.length;float gap=10,w=(W-34-gap*(count-1))/count;
        for(int i=0;i<count;i++){
            final UiState.Screen n=UiState.NAVIGATION[i];
            boolean on=s.screen==n||(n==UiState.Screen.INSTRUMENT&&s.screen==UiState.Screen.SOUND_DESIGN)||(n==UiState.Screen.LIBRARY&&s.screen==UiState.Screen.RECORDING);
            ui.button("nav."+n,17+i*(w+gap),634,w,53,n.label,on?Ui.SELECTED:Ui.NORMAL,true,()->go(n));
        }
    }
    void go(UiState.Screen n){
        if(n==UiState.Screen.EXPEDITION&&s.screen==n){s.mapOpen=false;s.encounterOpen=false;s.plotting=false;}
        s.screen=n;sheetOptions=null;s.quickMix=false;runSheet=false;
    }
    void goButton(String id,float x,float y,float w,float h,String label,final UiState.Screen n){ui.button(id,x,y,w,h,label,Ui.NORMAL,true,()->go(n));}

    // ---- INSTRUMENT --------------------------------------------------------------------
    private void instrumentFocus(){
        float x0=141,right=W-255;
        g.text("NOTES TO THE CONDUCTOR",x0-1,109,Gfx.MONO,12,Theme.PHOSPHOR,Gfx.LEFT,.06f);
        g.text("YOUR REQUEST / LIVE RESPONSE",right,109,Gfx.MONO,11,Theme.MUTED,Gfx.RIGHT,.04f);
        float gap=11,w=(right-x0-gap*3)/4,h=235;
        for(int i=0;i<8;i++)noteCard(i,x0+(i%4)*(w+gap),129+(i/4)*(h+10),w,h,72,false);
        controlRow(x0,620,right-x0,65,false);
        liveBalance(W-241,94,224,591);
    }
    private void instrumentPerformance(){
        g.text("NOTES TO THE CONDUCTOR",17,107,Gfx.MONO,12,Theme.PHOSPHOR,Gfx.LEFT,.06f);
        g.text("YOUR REQUEST / LIVE RESPONSE",W-17,107,Gfx.MONO,11,Theme.MUTED,Gfx.RIGHT,.04f);
        float gap=10,w=(W-34-gap*3)/4,h=206;
        for(int i=0;i<8;i++)noteCard(i,17+(i%4)*(w+gap),127+(i/4)*(h+10),w,h,88,true);
        controlRow(17,558,W-34,65,true);
    }
    private void noteCard(final int i,float x,float y,float w,float h,float d,boolean performance){
        ui.panel(x,y,w,h);
        g.text(UiState.NOTE_NAMES[i],x+13,y+(performance?28:26),Gfx.DISPLAY,performance?16:15,Theme.TEXT,Gfx.LEFT,.02f);
        g.text("YOUR NOTE",x+w-13,y+24,Gfx.MONO,9.5f,Theme.REQUEST,Gfx.RIGHT,.06f);
        float kx=x+(performance?w*.26f:w*.28f),ky=y+(performance?107:122);
        int v=s.notes[i];
        ui.knob("note."+i,kx,ky,d,v,0,100,false,Integer.toString(Math.round(ui.shown("note."+i,v))),value->s.notes[i]=value);
        float gx=x+w*(performance?.73f:.75f)-5,gy=y+(performance?66:86),gh=performance?75:70;
        ui.gauge(gx,gy,10,gh,s.response[i],UiState.RESPONSE_NAMES[i],s.responseText[i],performance?14:13);
        g.text(UiState.NOTE_ENDS[i],x+13,y+h-16,Gfx.MONO,10,Theme.MUTED,Gfx.LEFT,.05f);
    }
    private void controlRow(float x,float y,float w,float h,boolean performance){
        float gap=8,unit=(w-gap*5)/6.4f;
        float bw=unit*1.05f,mw=unit*1.15f,hw=unit*.8f,sw=unit*1.05f,lw=w-bw-mw-hw*2-sw-gap*5;
        float cx=x;
        ui.tile("biome",cx,y,bw,h,"BIOME",listName(Engine.BIOME,BIOMES).toUpperCase(Locale.ROOT),
            ()->choose("Biome",BIOMES,e.value(Engine.BIOME),v->e.set(Engine.BIOME,v)));cx+=bw+gap;
        ui.tile("mood",cx,y,mw,h,"MUSIC MOOD",listName(Engine.MOOD,MOODS).toUpperCase(Locale.ROOT),
            ()->choose("Music mood",MOODS,e.value(Engine.MOOD),v->e.set(Engine.MOOD,v)));cx+=mw+gap;
        g.text("DRIFT / HARMONY",cx,y+11,Gfx.MONO,9.5f,Theme.MUTED,Gfx.LEFT,.06f);
        boolean hold=e.value(Engine.HOLD)!=0;
        ui.button("hold",cx,y+20,hw,h-20,"Hold",hold?Ui.SELECTED:Ui.NORMAL,true,()->e.set(Engine.HOLD,hold?0:1));cx+=hw+gap/2;
        boolean canNext=e.habitatEngine()&&e.musicPlaying()&&e.value(Engine.EVOLVING)!=0;
        ui.button("next",cx,y+20,hw,h-20,"Next",Ui.NORMAL,canNext,()->e.scape(Engine.SCAPE_NEXT_HARMONY));cx+=hw+gap*1.5f;
        ui.button("sounddesign",cx,y,sw,h,"Sound design",Ui.NORMAL,true,()->go(UiState.Screen.SOUND_DESIGN));cx+=sw+gap;
        ui.button("levels",cx,y,lw,h,"Levels & parts",Ui.NORMAL,true,()->go(UiState.Screen.MIXER));
    }
    private void liveBalance(float x,float y,float w,float h){
        ui.panel(x,y,w,h);
        g.text("LIVE BALANCE",x+14,y+29,Gfx.DISPLAY,14.5f,Theme.PHOSPHOR,Gfx.LEFT,.02f);
        if(e.habitatEngine()){
            fader("balance.environment","Environment",x,y+63,w,e.value(Engine.HABITAT),e.min(Engine.HABITAT),e.max(Engine.HABITAT),v->e.set(Engine.HABITAT,v));
            fader("balance.drone","Drone",x,y+147,w,e.value(Engine.DRONE),e.min(Engine.DRONE),e.max(Engine.DRONE),v->e.set(Engine.DRONE,v));
        }else{waitingFader("Environment",x,y+63,w);waitingFader("Drone",x,y+147,w);}
        fader("balance.master","Master",x,y+231,w,e.master(),0,1023,e::setMaster);
        ui.button("quickmix.open",x+14,y+296,w-28,47,"Open quick mix",Ui.NORMAL,true,()->s.quickMix=true);
        g.line(x+14,y+383,x+w-14,y+383,1,Theme.BORDER);
        g.text("CURRENT RUN",x+14,y+409,Gfx.MONO,11,Theme.MUTED,Gfx.LEFT,.06f);
        g.text(s.runName,x+14,y+437,Gfx.MONO,14,Theme.TEXT,Gfx.LEFT,.03f);
        g.text(title(s.leader),x+14,y+463,Gfx.MONO,13,Theme.TEXT,Gfx.LEFT,0);
        g.text(title(s.flavour),x+14,y+484,Gfx.MONO,13,Theme.TEXT,Gfx.LEFT,0);
        fieldRecord(x+14,y+497,w-28,31);
        g.text("Amber: your request",x+14,y+553,Gfx.MONO,10.5f,Theme.MUTED,Gfx.LEFT,0);
        g.text("Green: the conductor",x+14,y+571,Gfx.MONO,10.5f,Theme.MUTED,Gfx.LEFT,0);
    }
    private void fader(String id,String label,float x,float y,float w,float value,float min,float max,IntConsumer set){
        float v=ui.shown(id,value);int pct=Math.round(max>min?(v-min)*100/(max-min):0);
        g.text(label,x+14,y,Gfx.MONO,13,Theme.TEXT,Gfx.LEFT,0);
        g.text(pct+"%",x+w-14,y,Gfx.MONO,13,Theme.PHOSPHOR,Gfx.RIGHT,0);
        ui.fader(id,x+16,y+24,w-50,value,min,max,set);
        g.text("0",x+14,y+45,Gfx.MONO,9.5f,Theme.MUTED,Gfx.LEFT,0);
        g.text("100",x+w-14,y+45,Gfx.MONO,9.5f,Theme.MUTED,Gfx.RIGHT,0);
    }
    /** A level that waits for the new habitat engine: label, an empty track, no handle. */
    private void waitingFader(String label,float x,float y,float w){
        g.text(label,x+14,y,Gfx.MONO,13,Theme.DIM,Gfx.LEFT,0);
        g.text("NEW ENGINE",x+w-14,y,Gfx.MONO,9.5f,Theme.MUTED,Gfx.RIGHT,.06f);
        g.rect(x+16,y+21,w-50,6,0xff202b1e);
        g.text("Returns with the new habitat",x+14,y+45,Gfx.MONO,9.5f,Theme.DIM,Gfx.LEFT,0);
    }
    /** The understated wax-cylinder strip. */
    private void fieldRecord(float x,float y,float w,float h){
        g.rect(x,y,w,h,0xff141c13);g.line(x,y,x+w,y,1,Theme.BORDER);g.line(x,y+h,x+w,y+h,1,Theme.BORDER);
        g.rect(x+6,y+6,5,h-12,0xff4c5c46);g.rect(x+w-12,y+6,5,h-12,0xff4c5c46);
        g.text("0472 / FIELD RECORD",x+20,y+h/2+3,Gfx.MONO,8.5f,Theme.MUTED,Gfx.LEFT,.04f);
    }
    private static String title(String s){
        StringBuilder b=new StringBuilder();boolean up=true;
        for(char c:s.toCharArray()){b.append(up?Character.toUpperCase(c):Character.toLowerCase(c));up=c==' '||c=='.';}
        String t=b.toString();return t.equals("Wellington")?"Dr. Wellington":t.equals("Veronica")?"Miss Veronica":t;
    }

    // ---- shared pieces -----------------------------------------------------------------
    private void screenTitle(String title,float x,float y){g.text(title,x+1,y+37,Gfx.DISPLAY,22,Theme.TEXT,Gfx.LEFT,.02f);}
    /** Tabs drawn right-aligned from the right edge; each entry is {label, width}. */
    private void tabs(String id,float right,float y,String[] labels,float[] widths,int selected,IntConsumer pick){
        float x=right;for(float w:widths)x-=w;x-=12*(widths.length-1);
        for(int i=0;i<labels.length;i++){
            final int n=i;
            ui.button(id+"."+i,x,y,widths[i],47,labels[i],i==selected?Ui.SELECTED:Ui.NORMAL,true,()->pick.accept(n));
            x+=widths[i]+12;
        }
    }
    /** The interface's full name for a list value; the firmware's own text is abbreviated for its small screen. */
    private String listName(int slot,String[] names){
        int v=e.value(slot)-e.min(slot);
        return v>=0&&v<names.length?names[v]:e.text(slot);
    }
    private String role(int part){return e.text(Engine.ROLE+part).toUpperCase(Locale.ROOT);}
    private void chooseSlot(String title,final int slot){
        String[] names=e.names(slot);if(names==null||names.length==0)return;
        choose(title,names,e.value(slot)-e.min(slot),v->e.set(slot,e.min(slot)+v));
    }
    static String note(int n){
        n=Math.max(0,Math.min(127,n));
        return new String[]{"C","C#","D","D#","E","F","F#","G","G#","A","A#","B"}[n%12]+(n/12-1);
    }

    // ---- MIXER -------------------------------------------------------------------------
    private void mixer(float x,float y,float w,float h,boolean focus){
        screenTitle("MIXER",x,y);
        tabs("mixer.tab",x+w,y+4,new String[]{"Parts","Habitat layers","Sound design"},new float[]{68,143,127},s.mixerPage,
            n->{if(n==2)go(UiState.Screen.SOUND_DESIGN);else s.mixerPage=n;});
        float top=y+65,bottom=y+h;
        if(s.mixerPage==0){
            float outH=128,gap=13,cw=(w-gap*3)/4;
            for(int p=0;p<4;p++)partCard(p,x+p*(cw+gap),top,cw,bottom-outH-10-top,!focus);
            output(x,bottom-outH,w,outH);
        }else if(e.habitatEngine())layers(x,top,w,bottom-top);
        else{
            ui.panel(x,top,w,bottom-top);
            g.text("HABITAT LAYERS",x+13,top+29,Gfx.DISPLAY,14.5f,Theme.PHOSPHOR,Gfx.LEFT,.02f);
            g.text("The old DINOBOX habitat is switched off. Wind, water, rain, insects, frogs, birds, drips, rumble",x+13,top+64,Gfx.MONO,12.5f,Theme.TEXT,Gfx.LEFT,0);
            g.text("and the drone return here with the new habitat engine.",x+13,top+85,Gfx.MONO,12.5f,Theme.TEXT,Gfx.LEFT,0);
        }
    }
    private void partCard(final int p,float x,float y,float w,float h,boolean compact){
        ui.panel(x,y,w,h);
        g.text("PART "+(p+1),x+15,y+28,Gfx.MONO,10.5f,Theme.MUTED,Gfx.LEFT,.06f);
        g.text(role(p),x+15,y+62,Gfx.DISPLAY,18,Theme.PHOSPHOR,Gfx.LEFT,.02f);
        Engine.Voice v=e.voice(p);
        String voice=v==null?"—":(v.preset==null||v.preset.isEmpty()?v.engineName:v.preset).toUpperCase(Locale.ROOT);
        float ty=y+(compact?78:80),th=compact?52:60;
        ui.tile("part.voice."+p,x+15,ty,w-30,th,"VOICE",voice,()->{s.designPart=p;go(UiState.Screen.SOUND_DESIGN);});
        float ly=y+(compact?158:176);
        fader("part.level."+p,"Level",x,ly,w,e.value(Engine.LEVEL+p),e.min(Engine.LEVEL+p),e.max(Engine.LEVEL+p),val->e.set(Engine.LEVEL+p,val));
        float my=y+(compact?220:250),mh=compact?52:60,half=(w-30-10)/2;
        muteButton("part.mute."+p,x+15,my+(mh-47)/2,half,47,Engine.MUTE+p);
        panTile("part.pan."+p,x+15+half+10,my,half,mh,p);
        float ry=y+(compact?278:318);
        ui.tile("part.role."+p,x+15,ry,w-30,compact?46:60,"ROLE",role(p),()->chooseSlot("Part "+(p+1)+" role",Engine.ROLE+p));
    }
    private void muteButton(String id,float x,float y,float w,float h,final int slot){
        final boolean muted=e.value(slot)!=0;
        ui.button(id,x,y,w,h,muted?"Muted":"Mute",muted?Ui.SELECTED:Ui.NORMAL,true,()->e.set(slot,muted?0:1));
    }
    private void panTile(String id,float x,float y,float w,float h,final int p){
        int slot=Engine.PAN+p;
        String text=e.value(slot)==0?"CENTRE":e.text(slot).toUpperCase(Locale.ROOT);
        ui.dragTile(id,x,y,w,h,"PAN",text,e.value(slot),e.min(slot),e.max(slot),4,val->e.set(Engine.PAN+p,val),()->e.set(Engine.PAN+p,0));
    }
    private void output(float x,float y,float w,float h){
        ui.panel(x,y,w,h);
        g.text("OUTPUT",x+13,y+29,Gfx.DISPLAY,14.5f,Theme.PHOSPHOR,Gfx.LEFT,.02f);
        g.text("Habitat levels remain one tap away",x+w-13,y+27,Gfx.MONO,11.5f,Theme.MUTED,Gfx.RIGHT,0);
        ui.hit("output.layers",x+w-280,y+6,270,32,()->s.mixerPage=1);
        float fw=(w-380)/2;
        if(e.habitatEngine())fader("output.environment","Environment",x,y+60,fw,e.value(Engine.HABITAT),e.min(Engine.HABITAT),e.max(Engine.HABITAT),v->e.set(Engine.HABITAT,v));
        else waitingFader("Environment",x,y+60,fw);
        fader("output.master","Master",x+fw,y+60,fw,e.master(),0,1023,e::setMaster);
        float tx=x+fw*2+10;
        ui.dragTile("output.tempo",tx,y+49,x+w-13-tx,58,"TEMPO",e.text(Engine.TEMPO).toUpperCase(Locale.ROOT),
            e.value(Engine.TEMPO),e.min(Engine.TEMPO),e.max(Engine.TEMPO),3,v->e.set(Engine.TEMPO,v),null);
    }
    private void layers(float x,float y,float w,float h){
        float lh=h-136;
        ui.panel(x,y,w,lh);
        g.text("HABITAT LAYERS",x+13,y+29,Gfx.DISPLAY,14.5f,Theme.PHOSPHOR,Gfx.LEFT,.02f);
        g.text("ENVIRONMENT",x+w-13,y+27,Gfx.MONO,11,Theme.MUTED,Gfx.RIGHT,.04f);
        float gap=10,cw=(w-26-gap*3)/4,ch=(lh-56-gap)/2;
        for(int i=0;i<8;i++)layerCard(i,x+13+(i%4)*(cw+gap),y+43+(i/4)*(ch+gap),cw,ch);
        float dy=y+lh+10,dh=h-lh-10;
        ui.panel(x,dy,w,dh);
        g.text("ENVIRONMENT DRONE",x+13,dy+29,Gfx.DISPLAY,14.5f,Theme.PHOSPHOR,Gfx.LEFT,.02f);
        final boolean held=e.audioOn()&&e.value(Engine.DRONE)>0;
        ui.button("drone.toggle",x+13,dy+52,132,48,held?"Drone  HELD":"Drone  OFF",held?Ui.SELECTED:Ui.NORMAL,true,
            ()->e.scape(held?Engine.SCAPE_DRONE_OFF:Engine.SCAPE_DRONE_ON));
        float fw=w*.53f;
        fader("drone.level","Drone level",x+140,dy+60,fw,e.value(Engine.DRONE),e.min(Engine.DRONE),e.max(Engine.DRONE),v->e.set(Engine.DRONE,v));
        g.text("Drone is separate from a musical Bed part",x+w-13,dy+83,Gfx.MONO,12,Theme.TEXT,Gfx.RIGHT,0);
    }
    private void layerCard(final int i,float x,float y,float w,float h){
        g.rect(x,y,w,h,Theme.PANEL);g.frame(x,y,w,h,1,Theme.BORDER);
        g.text(Engine.LAYERS[i],x+11,y+37,Gfx.DISPLAY,13.5f,Theme.TEXT,Gfx.LEFT,.02f);
        final int slot=Engine.LAYER+i;final boolean muted=s.layerMuted[i]>=0;
        ui.button("layer.mute."+i,x+w-75,y+9,64,47,muted?"Muted":"Mute",muted?Ui.SELECTED:Ui.NORMAL,true,()->{
            if(s.layerMuted[i]>=0){e.set(slot,s.layerMuted[i]);s.layerMuted[i]=-1;}
            else{s.layerMuted[i]=e.value(slot);e.set(slot,0);}
        });
        fader("layer.level."+i,"Level",x-3,y+h-62,w+6,e.value(slot),e.min(slot),e.max(slot),v->{s.layerMuted[i]=-1;e.set(slot,v);});
    }

    // ---- SOUND DESIGN ------------------------------------------------------------------
    private static final String[][] LONG_NAMES={
        {"WAVE","Waveform"},{"WAVE2","Second wave"},{"DTN","Detune"},{"DTUN","Detune"},{"MIX","Oscillator mix"},{"NOIS","Noise"},
        {"CUT","Cutoff"},{"RES","Resonance"},{"DRV","Drive"},{"KTR","Key tracking"},{"CALL","Call"},{"SIZE","Size"},
        {"SWOOP","Swoop"},{"LEN","Length"},{"GROWL","Growl"},{"BRTH","Breath"},{"MOUTH","Mouth"},{"WILD","Wildness"},
        {"DCW","Wave shaping"},{"ENV","Envelope"},{"LINE","Line"},{"SUB","Sub"},{"CHIP","Chip"},{"DUTY","Duty cycle"},
        {"CRSH","Crush"},{"SWP","Sweep"},{"VIB","Vibrato"},{"ARP","Arpeggio"},{"TONE","Tone"},{"SET","Sample set"},
        {"TUNE","Tune"},{"BITS","Bits"},{"LOOP","Loop"},{"VOWL","Vowel"},{"VOWL2","Second vowel"},{"TALK","Talk"},
        {"SHIFT","Formant shift"},{"BUZZ","Buzz"},{"Q","Formant width"},{"RAND","Randomness"},{"INT2","Interval 2"},
        {"INT3","Interval 3"},{"MODE","Mode"},{"PW","Pulse width"},{"REG","Registration"},{"BODY","Body"},{"TOP","Top"},
        {"PERC","Percussion"},{"CLICK","Key click"},{"ROTR","Rotor"},{"SRC","Source"},{"POS","Position"},{"DENS","Density"},
        {"PTCH","Pitch"},{"SPRD","Spread"},{"MODEL","Model"},{"STRC","Structure"},{"BRIT","Brightness"},{"DAMP","Damping"},
        {"ACC","Accent"},{"BOW","Bow"},{"EXC","Exciter"},{"KIT","Kit"},{"DECY","Decay"},{"SNAP","Snap"},{"KICK","Kick"},
        {"COLR","Colour"},{"FREQ","Frequency"},{"TRK","Tracking"},{"DRFT","Drift"},{"ALG","Algorithm"},{"FB","Feedback"},
        {"MLVL","Modulator level"},{"MRAT","Modulator ratio"},{"MEG","Modulator envelope"},{"VMOD","Velocity to mod"},
        {"SLOT","Operator"},{"DRN","Drone"},{"DLVL","Drone level"},{"5TH","Fifth"},{"RASP","Rasp"},{"PULS","Pulse"},{"WOBL","Wobble"}};
    static String longName(String label){for(String[] n:LONG_NAMES)if(n[0].equals(label))return n[1];return "";}

    private void soundDesign(float x,float y,float w,float h,boolean focus){
        screenTitle("SOUND DESIGN",x,y);
        final int part=s.designPart;final Engine.Voice v=e.voice(part);
        float bx=x+w-177;
        ui.button("design.back",bx,y+1,177,52,"Back to INSTRUMENT",Ui.NORMAL,true,()->go(UiState.Screen.INSTRUMENT));
        String preset=v==null||v.preset==null||v.preset.isEmpty()?"—":v.preset.toUpperCase(Locale.ROOT);
        float pw=Math.max(118,g.textWidth(preset,Gfx.MONO,13,.04f)+26);bx-=12+pw;
        ui.tile("design.preset",bx,y+1,pw,52,"PRESET",preset,v==null?null:()->{
            final Engine.Choice[] list=e.presets(v.engine);if(list==null||list.length==0)return;
            String[] names=new String[list.length];int sel=-1;
            for(int i=0;i<list.length;i++){names[i]=list[i].name;if(list[i].name.equalsIgnoreCase(v.preset))sel=i;}
            choose("Preset",names,sel,n->e.setPreset(part,list[n].id));
        });
        String engine=v==null?"—":v.engineName.toUpperCase(Locale.ROOT);
        float ew=Math.max(76,g.textWidth(engine,Gfx.MONO,13,.04f)+26);bx-=12+ew;
        ui.tile("design.voice",bx,y+1,ew,52,"VOICE",engine,()->{
            final Engine.Choice[] list=e.engines();if(list==null||list.length==0)return;
            String[] names=new String[list.length];int sel=-1;
            for(int i=0;i<list.length;i++){names[i]=list[i].name;if(v!=null&&list[i].id==v.engine)sel=i;}
            choose("Voice for part "+(part+1),names,sel,n->e.setEngine(part,list[n].id));
        });
        String partText=String.format(Locale.ROOT,"%02d / %s",part+1,role(part));
        float qw=Math.max(95,g.textWidth(partText,Gfx.MONO,13,.04f)+26);bx-=12+qw;
        ui.tile("design.part",bx,y+1,qw,52,"PART",partText,()->{
            String[] names=new String[4];for(int i=0;i<4;i++)names[i]=String.format(Locale.ROOT,"%02d / %s",i+1,role(i));
            choose("Part",names,part,n->s.designPart=n);
        });
        if(v==null){g.text("The engine has not reported this part's voice yet.",x+1,y+100,Gfx.MONO,13,Theme.MUTED,Gfx.LEFT,0);return;}
        g.text(engine+" / VOICE PARAMETERS",x+1,y+77,Gfx.MONO,12,Theme.PHOSPHOR,Gfx.LEFT,.04f);
        g.text("EDIT SELECTED PART",x+w,y+76,Gfx.MONO,10.5f,Theme.MUTED,Gfx.RIGHT,.04f);
        float top=y+98,gap=focus?13:10,cw=(w-gap*3)/4,ch=(h-98-gap)/2;
        for(int k=0;k<8;k++)paramCard(part,k,v.params[k],x+(k%4)*(cw+gap),top+(k/4)*(ch+gap),cw,ch);
    }
    private void paramCard(final int part,final int k,final Engine.Param p,float x,float y,float w,float h){
        if(p==null||!p.used()){
            g.rect(x,y,w,h,Theme.BACKGROUND);g.frame(x,y,w,h,1,0xff2f3d2b);
            g.text("—",x+w/2,y+h/2+6,Gfx.MONO,16,Theme.DIM,Gfx.CENTER,0);return;
        }
        ui.panel(x,y,w,h);
        g.text(p.label,x+w/2,y+33,Gfx.DISPLAY,17,Theme.TEXT,Gfx.CENTER,.02f);
        g.text(longName(p.label),x+w/2,y+51,Gfx.MONO,10.5f,Theme.MUTED,Gfx.CENTER,0);
        String id="design."+part+"."+k;
        if(p.list){
            float by=y+h*.43f,bh=Math.min(80,h*.33f);
            ui.tile(id,x+12,by,w-24,bh,"VALUE",p.text.toUpperCase(Locale.ROOT),()->{
                String[] names=e.valueNames(part,k);if(names==null||names.length==0)return;
                choose(p.label,names,p.value-p.min,n->e.setParam(part,k,p.min+n));
            });
            return;
        }
        boolean compact=h<230;
        float d=Math.min(78,h*(compact?.31f:.33f)),ky=y+h*(compact?.6f:.635f);
        ui.knob(id,x+w/2,ky,d,p.value,p.min,p.max,p.bipolar,"",n->e.setParam(part,k,n));
        g.text(p.text,x+w/2,y+h-22,Gfx.MONO,17,Theme.REQUEST,Gfx.CENTER,0);
    }

    // ---- QUICK MIX ---------------------------------------------------------------------
    private void quickMix(float x,float y,float w,float h){
        float qh=300,qy=y+h-qh;
        g.rect(x,qy,w,qh,Theme.PANEL);g.frame(x,qy,w,qh,1,Theme.BORDER);g.rect(x,qy,w,3,Theme.PHOSPHOR);
        ui.block(x,qy,w,qh);
        g.text("QUICK MIX",x+19,qy+53,Gfx.DISPLAY,19,Theme.TEXT,Gfx.LEFT,.02f);
        g.text("Return to the same place when closed",x+192,qy+51,Gfx.MONO,12,Theme.MUTED,Gfx.LEFT,0);
        ui.button("quickmix.close",x+w-87,qy+21,68,47,"Close",Ui.NORMAL,true,()->s.quickMix=false);
        float cw=(w-38)/4;
        for(int p=0;p<4;p++){
            final int part=p;float cx=x+19+p*cw;
            g.line(cx,qy+92,cx,qy+250,1,Theme.BORDER);
            g.text(role(p),cx+13,qy+106,Gfx.DISPLAY,15,Theme.TEXT,Gfx.LEFT,.02f);
            fader("quick.level."+p,"Level",cx,qy+130,cw-12,e.value(Engine.LEVEL+p),e.min(Engine.LEVEL+p),e.max(Engine.LEVEL+p),v->e.set(Engine.LEVEL+part,v));
            float half=(cw-38)/2;
            muteButton("quick.mute."+p,cx+13,qy+194,half,47,Engine.MUTE+p);
            panTile("quick.pan."+p,cx+13+half+10,qy+189,half,58,p);
        }
    }

    // ---- LIBRARY -----------------------------------------------------------------------
    private void library(float x,float y,float w,float h){
        final Library lib=e.library();
        screenTitle("LIBRARY",x,y);
        Library.Sound[] sounds=lib==null?new Library.Sound[0]:lib.sounds();
        String count=sounds.length+(sounds.length==1?" SOUND FILE":" SOUND FILES");
        float cw=g.textWidth(count,Gfx.MONO,12,.04f);
        g.text(count,x+w,y+33,Gfx.MONO,12,Theme.MUTED,Gfx.RIGHT,.04f);
        float right=x+w-cw-16;
        ui.button("library.import",right-102,y+4,102,47,"Import WAV",Ui.NORMAL,lib!=null&&lib.status()==null,()->lib.importWav());
        right-=114;
        tabs("library.tab",right,y+4,new String[]{"Sound files","Recordings"},new float[]{118,110},s.libraryTab,n->s.libraryTab=n);
        float top=y+65,gw=(w-16)*.515f;
        if(s.libraryTab==1){libraryRecordings(x,top,w,h-65);return;}
        Library.Sound selected=null;
        for(Library.Sound sound:sounds)if(sound.id.equals(s.librarySelected))selected=sound;
        if(selected==null&&sounds.length>0){selected=sounds[0];s.librarySelected=selected.id;}
        int per=6,pages=Math.max(1,(sounds.length+per-1)/per);
        if(s.libraryPage>=pages)s.libraryPage=pages-1;
        float tw=(gw-13)/2,th=139;
        if(sounds.length==0){
            ui.panel(x,top,gw,th);
            g.text(lib!=null&&lib.status()!=null?lib.status():"No sound files yet. Import a WAV to begin.",x+16,top+40,Gfx.MONO,12.5f,Theme.MUTED,Gfx.LEFT,0);
        }
        for(int i=s.libraryPage*per,j=0;i<sounds.length&&j<per;i++,j++)
            soundTile(sounds[i],x+(j%2)*(tw+13),top+(j/2)*(th+13),tw,th,sounds[i]==selected);
        if(pages>1){
            float py=top+3*(th+13);
            ui.button("library.previous",x,py,100,44,"Previous",Ui.NORMAL,s.libraryPage>0,()->s.libraryPage--);
            ui.button("library.next",x+110,py,100,44,"Next",Ui.NORMAL,s.libraryPage<pages-1,()->s.libraryPage++);
            g.text((s.libraryPage+1)+" / "+pages,x+226,py+28,Gfx.MONO,11,Theme.MUTED,Gfx.LEFT,0);
        }
        float dx=x+gw+16,dw=x+w-dx;
        soundDetail(lib,selected,dx,top,dw,246);
        playingSource(lib,dx,top+258,dw,205);
    }
    private void soundTile(final Library.Sound sound,float x,float y,float w,float h,boolean selected){
        boolean pressed=ui.held("library.sound."+sound.id);
        g.rect(x,y,w,h,selected?Theme.SELECTED_FILL:pressed?Theme.TRACK:Theme.PANEL);g.frame(x,y,w,h,1,selected?Theme.REQUEST:Theme.BORDER);
        float rx=x+12,ry=y+21,rw=w-24,rh=68;
        g.rect(rx,ry,rw,rh,0xff27361f);g.line(rx,ry,rx+rw,ry,1.5f,0xff8ea888);g.line(rx,ry+rh,rx+rw,ry+rh,1.5f,0xff8ea888);
        g.frame(rx+5,ry+4,9,rh-8,1,0xff8ea888);g.frame(rx+rw-58,ry+4,9,rh-8,1,0xff8ea888);
        g.clip(rx+20,ry,rw-82,rh);
        g.text(sound.name,rx+22,ry+rh/2+5,Gfx.MONO,14,selected?Theme.REQUEST:Theme.TEXT,Gfx.LEFT,0);
        g.unclip();
        g.text(String.format(Locale.ROOT,"ROOT %s / %.1f s",note(sound.root),sound.seconds),x+12,y+h-27,Gfx.MONO,9.5f,Theme.MUTED,Gfx.LEFT,.04f);
        ui.hit("library.sound."+sound.id,x,y,w,h,()->s.librarySelected=sound.id);
    }
    private void soundDetail(final Library lib,final Library.Sound sound,float x,float y,float w,float h){
        ui.panel(x,y,w,h);
        if(sound==null){g.text("Choose or import a sound file.",x+16,y+36,Gfx.MONO,12.5f,Theme.MUTED,Gfx.LEFT,0);return;}
        g.clip(x,y,w,h);
        g.text(sound.name.toUpperCase(Locale.ROOT),x+16,y+31,Gfx.DISPLAY,14.5f,Theme.PHOSPHOR,Gfx.LEFT,.02f);
        g.unclip();
        g.line(x+16,y+47,x+w-16,y+47,1,Theme.BORDER);
        ui.waveform(x+16,y+56,w-32,92,sound.peaks,0xff8fd39b);
        g.line(x+16,y+152,x+w-16,y+152,1,Theme.BORDER);
        String status=lib.status();
        boolean busy=status!=null,loaded=sound.id.equals(lib.loaded());
        float bw=(w-32-36)/4,by=y+172;
        ui.button("library.load",x+16,by,bw,47,loaded?"Loaded":"Load sound",loaded?Ui.SELECTED:Ui.NORMAL,!busy,loaded?null:()->lib.load(sound.id));
        ui.button("library.preview",x+16+bw+12,by,bw,47,"Preview",Ui.NORMAL,!busy,()->lib.preview(sound.id));
        ui.dragTile("library.root."+sound.id,x+16+(bw+12)*2,by-5,bw,58,"ROOT NOTE",note(sound.root),sound.root,0,127,10,n->lib.setRoot(sound.id,n),null);
        ui.button("library.delete",x+16+(bw+12)*3,by,bw,47,"Delete…",Ui.SELECTED,!busy,()->
            choose("Delete "+sound.name+"?",new String[]{"Delete from library"},-1,n->lib.delete(sound.id)));
        if(busy)g.text(status,x+16,y+h-6,Gfx.MONO,10.5f,Theme.REQUEST,Gfx.LEFT,0);
    }
    private void playingSource(final Library lib,float x,float y,float w,float h){
        ui.panel(x,y,w,h);
        g.text("PLAYING SOURCE",x+16,y+31,Gfx.DISPLAY,14.5f,Theme.PHOSPHOR,Gfx.LEFT,.02f);
        final boolean sample=lib!=null&&lib.sampleSource();
        String loaded=null;
        if(lib!=null&&lib.loaded()!=null)for(Library.Sound sound:lib.sounds())if(sound.id.equals(lib.loaded()))loaded=sound.name;
        g.clip(x,y,w,h);
        g.text(sample?"IMPORTED SAMPLE"+(loaded==null?"":" · "+loaded.toUpperCase(Locale.ROOT)):"PALEOTONE SOUNDS",x+22,y+63,Gfx.MONO,13,Theme.PHOSPHOR,Gfx.LEFT,.04f);
        g.unclip();
        float bw=(w-44)/2;
        ui.button("library.source",x+16,y+84,bw,47,sample?"Use Paleotone sounds":"Use imported sample",Ui.NORMAL,
            lib!=null&&(sample||lib.loaded()!=null)&&lib.status()==null,()->lib.useSample(!sample));
        ui.button("library.instrument",x+28+bw,y+84,bw,47,"INSTRUMENT",Ui.NORMAL,true,()->go(UiState.Screen.INSTRUMENT));
        g.text("Keyboard/MIDI uses this source. Expedition parts keep",x+16,y+160,Gfx.MONO,12.5f,Theme.TEXT,Gfx.LEFT,0);
        g.text("their own voices.",x+16,y+181,Gfx.MONO,12.5f,Theme.TEXT,Gfx.LEFT,0);
    }

    // ---- RECORDING ---------------------------------------------------------------------
    private static final String[] STATE_TITLES={"READY TO RECORD","RECORDING","SAVING TAKE","TAKE SAVED","TAKE NOT SAVED"};
    private Recorder recorder(){return e.recorder();}
    private void startTake(){
        Recorder r=recorder();if(r==null)return;
        if(r.state()==Recorder.ERROR){go(UiState.Screen.RECORDING);return;}
        r.start(s.runName);
    }
    private void recording(float x,float y,float w,float h){
        screenTitle("RECORDING",x,y);
        Recorder r=recorder();
        ui.tile("recording.length",x+w-95,y+1,95,52,"RUN LENGTH",Run.lengthText(s.nextRunLength).toUpperCase(Locale.ROOT),this::openRunLength);
        ui.button("recording.back",x+w-95-12-152,y+4,152,47,"Back to Library",Ui.NORMAL,true,()->{s.libraryTab=1;go(UiState.Screen.LIBRARY);});
        float top=y+65,lw=(w-14)*.57f,rx=x+lw+14,rw=x+w-rx;
        ui.panel(x,top,lw,h-65);
        int state=r==null?Recorder.READY:r.state();
        boolean live=state==Recorder.RECORDING;
        if(live)g.circle(x+21,top+29,5,Theme.REQUEST);else g.ring(x+21,top+29,4.5f,1.2f,Theme.MUTED);
        g.text(STATE_TITLES[state],x+37,top+36,Gfx.DISPLAY,16,state==Recorder.ERROR?Theme.REQUEST:Theme.TEXT,Gfx.LEFT,.02f);
        g.text(clock(r==null?0:r.takeMillis()),x+16,top+96,Gfx.MONO_MEDIUM,44,live?Theme.REQUEST:Theme.TEXT,Gfx.LEFT,0);
        g.text("TAKE ELAPSED",x+17,top+113,Gfx.MONO,9.5f,Theme.MUTED,Gfx.LEFT,.06f);
        g.line(x+16,top+126,x+lw-16,top+126,1,Theme.BORDER);
        float[] levels=r==null?null:r.levels();
        if(levels==null||levels.length==0)g.polyline(new float[]{x+16,top+164,x+lw-16,top+164},false,3,0xff8fd39b,7);
        else ui.waveform(x+16,top+130,lw-32,68,levels,live?0xff8fd39b:0xff6f9a72);
        g.line(x+16,top+202,x+lw-16,top+202,1,Theme.BORDER);
        g.text("STEREO OUTPUT",x+16,top+230,Gfx.MONO,12.5f,Theme.TEXT,Gfx.LEFT,.04f);
        g.text("Environment + music + played samples",x+16,top+251,Gfx.MONO,11,Theme.MUTED,Gfx.LEFT,0);
        String name=r==null?null:r.takeName();
        if(name!=null&&state!=Recorder.READY)g.text(name,x+16,top+285,Gfx.MONO,12,Theme.TEXT,Gfx.LEFT,0);
        long free=r==null?-1:r.freeBytes();
        if(free>=0){
            long minutes=free/(48000L*4*60);
            g.text(String.format(Locale.ROOT,"Free space %.1f GB, about %s of recording. Saved to %s.",free/1e9,minutes>=120?(minutes/60)+" h":minutes+" min",r.destination()),
                x+16,top+312,Gfx.MONO,11,Theme.MUTED,Gfx.LEFT,0);
        }
        float by=top+h-65-108,bw=lw-32;
        String note;
        switch(state){
            case Recorder.RECORDING:
                ui.button("recording.stop",x+16,by,bw,47,"Stop recording",Ui.SELECTED,true,()->r.stop());
                note="Stopping saves this take. The run keeps going.";break;
            case Recorder.SAVING:
                ui.button("recording.saving",x+16,by,bw,47,"Saving…",Ui.NORMAL,false,null);
                note="Writing the file. It is not saved until this finishes.";break;
            case Recorder.ERROR:
                ui.button("recording.retry",x+16,by,(bw-12)/2,47,"Retry save",Ui.SELECTED,true,()->r.retry());
                ui.button("recording.discard",x+16+(bw+12)/2,by,(bw-12)/2,47,"Discard take",Ui.NORMAL,true,()->
                    choose("Discard this take?",new String[]{"Discard the take"},-1,n->r.discard()));
                note=r.error()==null?"The take is kept for another save attempt.":"Save failed: "+r.error()+". The take is kept for another attempt.";break;
            default:
                ui.button("recording.start",x+16,by,bw,47,state==Recorder.SAVED?"Start a new take":"Start recording",Ui.SELECTED,r!=null,this::startTake);
                note=state==Recorder.SAVED?"Saved. A new take starts from the moment you press it.":"A take starts from this moment. The run clock keeps its own time.";
        }
        g.text(note,x+16,by+70,Gfx.MONO,11,Theme.MUTED,Gfx.LEFT,0);
        // This run
        float py=top;
        ui.panel(rx,py,rw,175);
        g.text("THIS RUN",rx+13,py+27,Gfx.DISPLAY,13,Theme.PHOSPHOR,Gfx.LEFT,.02f);
        g.text(s.runName,rx+13,py+55,Gfx.MONO,14,Theme.TEXT,Gfx.LEFT,.03f);
        g.text(title(s.leader)+" / "+title(s.flavour),rx+13,py+78,Gfx.MONO,12,Theme.TEXT,Gfx.LEFT,0);
        g.text(runCaption(),rx+13,py+100,Gfx.MONO,7.5f,Theme.MUTED,Gfx.LEFT,.12f);
        g.text(runClock(),rx+13,py+119,Gfx.MONO,13,Theme.TEXT,Gfx.LEFT,0);
        runBar(rx+13,py+127,Math.min(300,rw-120));
        if(s.runPhase==UiState.RUNNING)ui.button("recording.endrun",rx+rw-104,py+96,91,40,"End run",Ui.NORMAL,true,()->run.end(ui.now));
        g.text(s.runLength==0?"Continuous: the run ends when you choose End run.":"At "+clock(s.runLength)+" the run fades out, stops, and saves any active take.",
            rx+13,py+158,Gfx.MONO,11,Theme.TEXT,Gfx.LEFT,0);
        // Takes
        py+=187;
        Recorder.Take[] takes=r==null?new Recorder.Take[0]:r.takes();
        int rows=Math.min(5,Math.max(1,takes.length));
        float th=48+rows*33;
        ui.panel(rx,py,rw,th);
        g.text("TAKES",rx+13,py+27,Gfx.DISPLAY,13,Theme.PHOSPHOR,Gfx.LEFT,.02f);
        takeRows(takes,rx+13,py+38,rw-26,5);
        py+=th+12;
        if(py+92<=top+h-65){
            ui.panel(rx,py,rw,92);
            g.text("RECORDING & SESSION",rx+13,py+27,Gfx.DISPLAY,13,Theme.PHOSPHOR,Gfx.LEFT,.02f);
            g.text("Recording saves audio.",rx+13,py+54,Gfx.MONO,12,Theme.TEXT,Gfx.LEFT,0);
            g.text("Save session keeps your controls and expedition.",rx+13,py+74,Gfx.MONO,12,Theme.TEXT,Gfx.LEFT,0);
        }
    }
    private void takeRows(Recorder.Take[] takes,float x,float y,float w,int max){
        if(takes.length==0){g.line(x,y,x+w,y,1,Theme.BORDER);g.text("No takes yet.",x,y+22,Gfx.MONO,11.5f,Theme.MUTED,Gfx.LEFT,0);return;}
        for(int i=0;i<takes.length&&i<max;i++){
            Recorder.Take t=takes[i];float ry=y+i*33;
            g.line(x,ry,x+w,ry,1,Theme.BORDER);
            g.clip(x,ry,w*.62f,33);g.text(t.name,x,ry+22,Gfx.MONO,11.5f,t.state==Recorder.SAVED?Theme.TEXT:Theme.REQUEST,Gfx.LEFT,0);g.unclip();
            String status=t.state==Recorder.SAVED?"Saved / "+clock((long)(t.seconds*1000)):t.state==Recorder.RECORDING?"Recording":t.state==Recorder.SAVING?"Saving":"Save needed";
            g.text(status,x+w,ry+22,Gfx.MONO,11.5f,t.state==Recorder.SAVED?Theme.TEXT:Theme.REQUEST,Gfx.RIGHT,0);
        }
    }
    private void libraryRecordings(float x,float y,float w,float h){
        ui.panel(x,y,w,h);
        g.text("RECORDINGS",x+16,y+32,Gfx.DISPLAY,14.5f,Theme.PHOSPHOR,Gfx.LEFT,.02f);
        Recorder r=recorder();
        if(r!=null)g.text("16-bit stereo WAV, saved to "+r.destination(),x+16,y+56,Gfx.MONO,11.5f,Theme.MUTED,Gfx.LEFT,0);
        ui.button("library.recording",x+w-176,y+12,160,47,"Open recording",Ui.NORMAL,true,()->go(UiState.Screen.RECORDING));
        takeRows(r==null?new Recorder.Take[0]:r.takes(),x+16,y+80,w-32,12);
    }

    // ---- run clock, run length and run end -------------------------------------------
    public final Run run;
    private String runCaption(){return s.runPhase==UiState.ENDING?"RUN ENDING":s.runPhase==UiState.ENDED?"RUN COMPLETE":"RUN ELAPSED / LENGTH";}
    private String runClock(){return clock(s.runElapsed)+" / "+(s.runLength==0?"CONTINUOUS":clock(s.runLength));}
    private void runBar(float x,float y,float w){
        g.rect(x,y,w,2.5f,Theme.TRACK);
        float f=s.runLength==0?1:Math.min(1,(float)s.runElapsed/s.runLength);
        g.rect(x,y,w*f,2.5f,s.runLength==0?Theme.BORDER:Theme.PHOSPHOR);
    }
    private boolean runSheet;private long runChoice;private int customMinutes=45;
    public void openRunLength(){runSheet=true;runChoice=s.nextRunLength;sheetOptions=null;
        if(runChoice>0&&runChoice!=15*60000L&&runChoice!=30*60000L&&runChoice!=60*60000L&&runChoice!=120*60000L)customMinutes=(int)(runChoice/60000);}
    private void runLengthSheet(float x,float y,float w,float h){
        g.rect(x,y,w,h,Theme.SCRIM);ui.block(x,y,w,h);
        float pw=Math.min(850,w-40),ph=414,px=x+(w-pw)/2,py=y+(h-ph)/2;
        ui.panel(px,py,pw,ph);ui.block(px,py,pw,ph);
        g.text("NEW RUN / DURATION",px+25,py+31,Gfx.MONO,9.5f,Theme.REQUEST,Gfx.LEFT,.08f);
        g.text("Choose the run length",px+25,py+68,Gfx.DISPLAY,21,Theme.TEXT,Gfx.LEFT,.01f);
        long[] fixed={15*60000L,30*60000L,60*60000L,120*60000L,0};
        float bw=(pw-50-20)/3;
        boolean custom=true;for(long f:fixed)if(f==runChoice)custom=false;
        for(int i=0;i<6;i++){
            float bx=px+25+(i%3)*(bw+10),by=py+86+(i/3)*62;
            if(i<5){final long len=fixed[i];
                ui.button("run.length."+i,bx,by,bw,52,Run.lengthText(len),runChoice==len?Ui.SELECTED:Ui.NORMAL,true,()->runChoice=len);
            }else{
                if(custom)ui.dragTile("run.custom",bx,by,bw,52,"CUSTOM",customMinutes+" MIN",customMinutes,5,240,6,
                    n->{customMinutes=n;runChoice=n*60000L;},null);
                else ui.button("run.length.custom",bx,by,bw,52,"Custom…",Ui.NORMAL,true,()->runChoice=customMinutes*60000L);
            }
        }
        g.text("Current run: "+runClock().toLowerCase(Locale.ROOT)+".",px+25,py+230,Gfx.MONO,12,Theme.TEXT,Gfx.LEFT,0);
        g.text(custom?"Drag Custom sideways: 5 to 240 minutes. Applies when you begin a new run.":"This choice applies when you begin a new run.",px+25,py+249,Gfx.MONO,12,Theme.TEXT,Gfx.LEFT,0);
        g.line(px+25,py+268,px+pw-25,py+268,1,Theme.BORDER);
        g.text("WHEN A TIMED RUN ENDS",px+25,py+290,Gfx.DISPLAY,11.5f,Theme.PHOSPHOR,Gfx.LEFT,.02f);
        g.text("Fade out over 5 seconds, stop the expedition audio, then save any active recording.",px+25,py+310,Gfx.MONO,12,Theme.TEXT,Gfx.LEFT,0);
        g.text("Continuous runs end when you choose End run.",px+25,py+330,Gfx.MONO,9.5f,Theme.MUTED,Gfx.LEFT,0);
        float hw=(pw-50-10)/2;
        ui.button("run.cancel",px+25,py+ph-66,hw,44,"Cancel",Ui.NORMAL,true,()->runSheet=false);
        ui.button("run.use",px+35+hw,py+ph-66,hw,44,"Use "+Run.lengthText(runChoice).toLowerCase(Locale.ROOT).replace("continuous","Continuous"),Ui.SELECTED,true,()->{s.nextRunLength=runChoice;runSheet=false;});
    }
    /** The closing and run-complete card, on Expedition. */
    private void runEnd(float x,float y,float w,float h){
        float strip=Math.min(160,h-380-20),cw=Math.min(820,w-40),ch=(s.runPhase==UiState.ENDING?300:350)+strip+12,cx=x+(w-cw)/2,cy=y+(h-ch)/2;
        ui.panel(cx,cy,cw,ch);
        field.mapStrip(cx+12,cy+ch-strip-12,cw-24,strip);
        float mid=cx+cw/2;
        g.text((s.runName+" / "+s.leader).toUpperCase(Locale.ROOT),mid,cy+30,Gfx.MONO,9.5f,Theme.MUTED,Gfx.CENTER,.06f);
        g.text(s.runPhase==UiState.ENDING?"The expedition is closing.":"Run complete.",mid,cy+80,Gfx.PRINT,36,Theme.TEXT,Gfx.CENTER,0);
        String big=clock(s.runElapsed),small=" / "+(s.runLength==0?"continuous":clock(s.runLength));
        float bwid=g.textWidth(big,Gfx.MONO_MEDIUM,30,0),swid=g.textWidth(small,Gfx.MONO,16,0),sx=mid-(bwid+swid)/2;
        g.text(big,sx,cy+134,Gfx.MONO_MEDIUM,30,Theme.TEXT,Gfx.LEFT,0);g.text(small,sx+bwid,cy+134,Gfx.MONO,16,Theme.MUTED,Gfx.LEFT,0);
        Recorder r=recorder();int rs=r==null?Recorder.READY:r.state();
        if(s.runPhase==UiState.ENDING){
            long in=Math.min(Run.FADE_MS,Math.max(0,ui.now-s.fadeStart));
            g.circle(mid-118,cy+170,4,Theme.REQUEST);
            g.text("FADING OUT / "+clock(in)+" OF "+clock(Run.FADE_MS),mid+6,cy+175,Gfx.MONO,12.5f,Theme.REQUEST,Gfx.CENTER,.06f);
            g.text(rs==Recorder.RECORDING?"Recording the fade. Your take will save when the sound stops.":"Audio stops when the fade ends.",mid,cy+207,Gfx.MONO,12,Theme.TEXT,Gfx.CENTER,0);
            ui.button("runend.stopnow",mid-46,cy+232,92,47,"Stop now",Ui.SELECTED,true,run::finish);
            return;
        }
        String take;
        switch(rs){
            case Recorder.SAVED:take=r.takeName()==null?"NO RECORDING":"TAKE SAVED";break;
            case Recorder.SAVING:take="SAVING TAKE";break;
            case Recorder.ERROR:take="TAKE NOT SAVED";break;
            default:take="NO RECORDING";
        }
        boolean hadTake=rs!=Recorder.READY&&r.takeName()!=null;
        g.text("ENVIRONMENT STOPPED   MUSIC STOPPED   "+take,mid,cy+166,Gfx.MONO,9.5f,rs==Recorder.ERROR?Theme.REQUEST:Theme.PHOSPHOR,Gfx.CENTER,.06f);
        if(hadTake){
            g.text(r.takeName(),mid,cy+194,Gfx.MONO,12.5f,Theme.TEXT,Gfx.CENTER,0);
            g.text(clock(r.takeMillis())+" / stereo WAV",mid,cy+212,Gfx.MONO,10,Theme.MUTED,Gfx.CENTER,0);
        }else g.text("Nothing was recorded during this run.",mid,cy+200,Gfx.MONO,12,Theme.MUTED,Gfx.CENTER,0);
        float bw=(cw-50-12)/2,by=cy+228;
        ui.button("runend.view",cx+25,by,bw,44,hadTake?"View recording":"Recording",Ui.NORMAL,true,()->go(UiState.Screen.RECORDING));
        ui.button("runend.save",cx+37+bw,by,bw,44,"Save session",Ui.NORMAL,true,e::saveSession);
        ui.button("runend.new",cx+25,by+56,bw,44,"Start a new run",Ui.SELECTED,true,run::newRun);
        ui.button("runend.return",cx+37+bw,by+56,bw,44,"Return to expedition",Ui.NORMAL,true,()->s.endDismissed=true);
    }

    // ---- other screens -------------------------------------------------------------------
    private void settings(float x,float y,float w,float h){
        ui.panel(x,y,w,h);
        g.text("SETTINGS",x+24,y+44,Gfx.DISPLAY,20,Theme.PHOSPHOR,Gfx.LEFT,.02f);
        String[][] rows={{"Audio output and buffer","audio","Output level and buffer size. Changing the buffer pauses audio."},
            {"MIDI controllers","midi","Choose an attached controller's output port."},
            {"Kiosk and maintenance","kiosk","Enter setup, or exit to Android settings."},
            {"About Paleotone","about","Version, licences and source."}};
        for(int i=0;i<rows.length;i++){
            final String action=rows[i][1];float ry=y+72+i*66;
            ui.button("settings."+action,x+24,ry,260,50,rows[i][0],Ui.NORMAL,true,()->e.action(action));
            g.text(rows[i][2],x+300,ry+30,Gfx.MONO,12,Theme.MUTED,Gfx.LEFT,0);
        }
    }

    // ---- choice sheet ----------------------------------------------------------------------
    public void choose(String title,String[] options,int selected,IntConsumer pick){
        sheetTitle=title;sheetOptions=options;sheetSelected=selected;sheetPick=pick;
        sheetPage=0;int per=sheetPerPage();if(selected>=0&&per>0)sheetPage=selected/per;
    }
    private int sheetPage;
    private int sheetColumns(){return sheetOptions.length>8?3:2;}
    private int sheetPerPage(){return sheetColumns()*(s.layout==UiState.Layout.FOCUS?7:6);}
    private void sheet(float x,float y,float w,float h){
        g.rect(x,y,w,h,Theme.SCRIM);ui.block(x,y,w,h);
        int cols=sheetColumns(),per=sheetPerPage(),pages=(sheetOptions.length+per-1)/per;
        if(sheetPage>=pages)sheetPage=Math.max(0,pages-1);
        int first=sheetPage*per,count=Math.min(per,sheetOptions.length-first);
        int rows=(count+cols-1)/cols;
        float pw=Math.min(cols==3?760:560,w-40),ph=60+Math.max(1,rows)*58+66;
        float px=x+(w-pw)/2,py=y+(h-ph)/2;
        ui.panel(px,py,pw,ph);ui.block(px,py,pw,ph);
        g.text(sheetTitle.toUpperCase(Locale.ROOT),px+20,py+36,Gfx.DISPLAY,16,Theme.PHOSPHOR,Gfx.LEFT,.02f);
        float bw=(pw-40-(cols-1)*10)/cols;
        for(int j=0;j<count;j++){
            final int n=first+j;
            ui.button("sheet."+n,px+20+(j%cols)*(bw+10),py+60+(j/cols)*58,bw,48,sheetOptions[n],n==sheetSelected?Ui.SELECTED:Ui.NORMAL,true,()->{
                IntConsumer p=sheetPick;sheetOptions=null;p.accept(n);
            });
        }
        if(pages>1){
            ui.button("sheet.previous",px+20,py+ph-62,100,44,"Previous",Ui.NORMAL,sheetPage>0,()->sheetPage--);
            ui.button("sheet.next",px+128,py+ph-62,100,44,"Next",Ui.NORMAL,sheetPage<pages-1,()->sheetPage++);
            g.text((sheetPage+1)+" / "+pages,px+244,py+ph-34,Gfx.MONO,11,Theme.MUTED,Gfx.LEFT,0);
        }
        ui.button("sheet.cancel",px+pw-140,py+ph-62,120,44,"Cancel",Ui.QUIET,true,()->sheetOptions=null);
    }

    // ---- bottom bar ------------------------------------------------------------------------
    private void bottomBar(){
        g.line(16,698,W-16,698,1,Theme.BORDER);
        boolean ready=e.habitatEngine(),musicReady=e.musicEngine();
        boolean audio=e.audioOn(),env=ready&&audio&&e.value(Engine.HABITAT)>0,music=musicReady&&audio&&e.musicPlaying();
        ui.button("transport.environment",16,714,145,47,env?"Pause environment":"Start environment",Ui.NORMAL,ready,
            ()->e.scape(env?Engine.SCAPE_PAUSE_ENVIRONMENT:Engine.SCAPE_START_ENVIRONMENT));
        g.text(!ready?"NEW ENGINE PENDING":env?"ENVIRONMENT LIVE":"ENVIRONMENT OFF",16+72.5f,777,Gfx.MONO,9,env?Theme.PHOSPHOR:Theme.MUTED,Gfx.CENTER,.04f);
        ui.button("transport.music",169,714,145,47,music?"Stop music":"Play music",Ui.NORMAL,musicReady,
            ()->e.scape(music?Engine.SCAPE_STOP_MUSIC:Engine.SCAPE_PLAY_MUSIC));
        g.text(!musicReady?"NEW ENGINE PENDING":music?"MUSIC LIVE":"MUSIC STOPPED",169+72.5f,777,Gfx.MONO,9,music?Theme.PHOSPHOR:Theme.MUTED,Gfx.CENTER,.04f);
        ui.button("transport.stopall",323,723,128,47,"STOP ALL",Ui.STOP,true,run::stopAll);
        g.text(audio?"AUDIO ON":"AUDIO OFF",466,729,Gfx.MONO,9.5f,audio?Theme.PHOSPHOR:Theme.MUTED,Gfx.LEFT,.04f);
        final Recorder r=recorder();int rs=r==null?Recorder.READY:r.state();
        String rec;int recInk=Theme.MUTED;
        switch(rs){
            case Recorder.RECORDING:rec="REC / "+clock(r.takeMillis());recInk=Theme.REQUEST;g.circle(470,758,3.5f,Theme.REQUEST);break;
            case Recorder.SAVING:rec="SAVING";recInk=Theme.REQUEST;break;
            case Recorder.SAVED:rec="TAKE SAVED";recInk=Theme.PHOSPHOR;break;
            case Recorder.ERROR:rec="SAVE NEEDED";recInk=Theme.REQUEST;break;
            default:rec="REC OFF";
        }
        g.text(rec,rs==Recorder.RECORDING?479:466,762,Gfx.MONO,9.5f,recInk,Gfx.LEFT,.04f);
        if(rs==Recorder.RECORDING)ui.button("record",W-529,723,128,47,"Stop recording",Ui.SELECTED,true,()->r.stop());
        else if(rs==Recorder.ERROR)ui.button("record",W-529,723,128,47,"Save needed",Ui.SELECTED,true,()->go(UiState.Screen.RECORDING));
        else ui.button("record",W-529,723,128,47,"Record",Ui.NORMAL,r!=null&&rs!=Recorder.SAVING,this::startTake);
        ui.button("quickmix",W-393,723,86,47,"Quick mix",s.quickMix?Ui.SELECTED:Ui.NORMAL,true,()->{s.quickMix=!s.quickMix;sheetOptions=null;});
        ui.button("save",W-123,723,106,47,"Save session",Ui.NORMAL,true,e::saveSession);
    }
    private void notice(){
        if(s.notice==null||ui.now>s.noticeUntil)return;
        float tw=g.textWidth(s.notice,Gfx.MONO,12,0)+40,x=(W-tw)/2,y=650;
        g.rect(x,y,tw,34,Theme.PANEL);g.frame(x,y,tw,34,1,Theme.REQUEST);
        g.text(s.notice,W/2,y+22,Gfx.MONO,12,Theme.TEXT,Gfx.CENTER,0);
    }
}
