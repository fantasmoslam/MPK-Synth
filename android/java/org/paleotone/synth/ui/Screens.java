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
    /** Where an existing Android page is shown (Mixer, Library, Legacy panel), in units; null when none. */
    public float[] hostRect;
    public UiState.Screen hosted;

    private String sheetTitle;private String[] sheetOptions;private int sheetSelected;private IntConsumer sheetPick;

    public Screens(Ui ui,UiState state,Engine engine){this.ui=ui;s=state;e=engine;}
    public int mixerPage(){return s.mixerPage;}

    public void draw(){
        g=ui.g;W=ui.width;H=ui.height;hostRect=null;hosted=null;
        g.rect(0,0,W,H,Theme.BACKGROUND);
        header();
        boolean focus=s.layout==UiState.Layout.FOCUS;
        if(focus)rail();else dock();
        float cx=focus?140:17,cy=94,cw=W-17-cx,ch=focus?591:529;
        switch(s.screen){
            case INSTRUMENT:if(focus)instrumentFocus();else instrumentPerformance();break;
            case SETTINGS:settings(cx,cy,cw,ch);break;
            case MIXER:mixerTabs(cx,cy,cw);hostRect=new float[]{cx,cy+58,cw,ch-58};hosted=s.screen;break;
            case LIBRARY:case LEGACY:hostRect=new float[]{cx,cy,cw,ch};hosted=s.screen;break;
            default:upcoming(cx,cy,cw,ch);
        }
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
        String key=e.text(Engine.MOOD).toUpperCase(Locale.ROOT)+"   "+e.text(Engine.TEMPO).toUpperCase(Locale.ROOT);
        g.text(key,ex-13,47,Gfx.MONO,12,Theme.PHOSPHOR,Gfx.RIGHT,.04f);
        g.text("RUN ELAPSED / LENGTH",ex,32,Gfx.MONO,7.5f,Theme.MUTED,Gfx.LEFT,.12f);
        g.text(clock(s.runElapsed)+" / "+(s.runLength==0?"CONTINUOUS":clock(s.runLength)),ex,52,Gfx.MONO,14,Theme.TEXT,Gfx.LEFT,0);
        g.rect(ex,60,130,2.5f,Theme.TRACK);
        float f=s.runLength==0?1:Math.min(1,(float)s.runElapsed/s.runLength);
        g.rect(ex,60,130*f,2.5f,s.runLength==0?Theme.BORDER:Theme.PHOSPHOR);
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
            boolean on=s.screen==n||(n==UiState.Screen.SETTINGS&&s.screen==UiState.Screen.LEGACY);
            ui.button("nav."+n,16,94+i*59,112,51,n.label,on?Ui.SELECTED:Ui.NORMAL,true,()->go(n));
        }
    }
    private void dock(){
        int count=UiState.NAVIGATION.length;float gap=10,w=(W-34-gap*(count-1))/count;
        for(int i=0;i<count;i++){
            final UiState.Screen n=UiState.NAVIGATION[i];
            boolean on=s.screen==n||(n==UiState.Screen.SETTINGS&&s.screen==UiState.Screen.LEGACY);
            ui.button("nav."+n,17+i*(w+gap),634,w,53,n.label,on?Ui.SELECTED:Ui.NORMAL,true,()->go(n));
        }
    }
    private void go(UiState.Screen n){s.screen=n;sheetOptions=null;}

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
        ui.tile("biome",cx,y,bw,h,"BIOME",e.text(Engine.BIOME).toUpperCase(Locale.ROOT),
            ()->choose("Biome",BIOMES,e.value(Engine.BIOME),v->e.set(Engine.BIOME,v)));cx+=bw+gap;
        ui.tile("mood",cx,y,mw,h,"MUSIC MOOD",e.text(Engine.MOOD).toUpperCase(Locale.ROOT),
            ()->choose("Music mood",MOODS,e.value(Engine.MOOD),v->e.set(Engine.MOOD,v)));cx+=mw+gap;
        g.text("DRIFT / HARMONY",cx,y+11,Gfx.MONO,9.5f,Theme.MUTED,Gfx.LEFT,.06f);
        boolean hold=e.value(Engine.HOLD)!=0;
        ui.button("hold",cx,y+20,hw,h-20,"Hold",hold?Ui.SELECTED:Ui.NORMAL,true,()->e.set(Engine.HOLD,hold?0:1));cx+=hw+gap/2;
        boolean canNext=e.musicPlaying()&&e.value(Engine.EVOLVING)!=0;
        ui.button("next",cx,y+20,hw,h-20,"Next",Ui.NORMAL,canNext,()->e.scape(Engine.SCAPE_NEXT_HARMONY));cx+=hw+gap*1.5f;
        ui.button("sounddesign",cx,y,sw,h,"Sound design",Ui.NORMAL,true,()->go(UiState.Screen.LEGACY));cx+=sw+gap;
        ui.button("levels",cx,y,lw,h,"Levels & parts",Ui.NORMAL,true,()->go(UiState.Screen.MIXER));
    }
    private void liveBalance(float x,float y,float w,float h){
        ui.panel(x,y,w,h);
        g.text("LIVE BALANCE",x+14,y+29,Gfx.DISPLAY,14.5f,Theme.PHOSPHOR,Gfx.LEFT,.02f);
        fader("balance.environment","Environment",x,y+63,w,e.value(Engine.HABITAT),e.min(Engine.HABITAT),e.max(Engine.HABITAT),v->e.set(Engine.HABITAT,v));
        fader("balance.drone","Drone",x,y+147,w,e.value(Engine.DRONE),e.min(Engine.DRONE),e.max(Engine.DRONE),v->e.set(Engine.DRONE,v));
        fader("balance.master","Master",x,y+231,w,e.master(),0,1023,e::setMaster);
        ui.button("quickmix.open",x+14,y+296,w-28,47,"Open quick mix",Ui.NORMAL,true,()->go(UiState.Screen.MIXER));
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

    /** Until phase 2 redraws them, Mixer shows the existing track and Scape pages. */
    private void mixerTabs(float x,float y,float w){
        ui.button("mixer.tracks",x,y,170,46,"Tracks",s.mixerPage==0?Ui.SELECTED:Ui.NORMAL,true,()->s.mixerPage=0);
        ui.button("mixer.scape",x+178,y,170,46,"Scape layers",s.mixerPage==1?Ui.SELECTED:Ui.NORMAL,true,()->s.mixerPage=1);
        g.text("Phase 1 shows the existing pages here. Phase 2 redraws them in this style.",x+366,y+28,Gfx.MONO,11,Theme.MUTED,Gfx.LEFT,0);
    }

    // ---- other screens -------------------------------------------------------------------
    private void upcoming(float x,float y,float w,float h){
        ui.panel(x,y,w,h);
        g.text(s.screen.label.toUpperCase(Locale.ROOT),x+24,y+44,Gfx.DISPLAY,20,Theme.PHOSPHOR,Gfx.LEFT,.02f);
        String what;
        switch(s.screen){
            case EXPEDITION:what="The expedition, encounters and the valley survey map are built in phase 5.";break;
            case JOURNAL:what="The field journal is built in phase 5.";break;
            case BESTIARY:what="The bestiary is built in phase 5.";break;
            case START:what="Planning a new run and choosing its leader is built in phase 5.";break;
            default:what="";
        }
        g.text(what,x+24,y+80,Gfx.MONO,13,Theme.MUTED,Gfx.LEFT,0);
    }
    private void settings(float x,float y,float w,float h){
        ui.panel(x,y,w,h);
        g.text("SETTINGS",x+24,y+44,Gfx.DISPLAY,20,Theme.PHOSPHOR,Gfx.LEFT,.02f);
        String[][] rows={{"Audio output and buffer","audio","Output level and buffer size. Changing the buffer pauses audio."},
            {"MIDI controllers","midi","Choose an attached controller's output port."},
            {"Legacy panel","legacy","The old firmware panel, kept for testing."},
            {"Kiosk and maintenance","kiosk","Enter setup, or exit to Android settings."},
            {"About Paleotone","about","Version, licences and source."}};
        for(int i=0;i<rows.length;i++){
            final String action=rows[i][1];float ry=y+72+i*66;
            ui.button("settings."+action,x+24,ry,260,50,rows[i][0],Ui.NORMAL,true,()->{
                if(action.equals("legacy"))go(UiState.Screen.LEGACY);else e.action(action);
            });
            g.text(rows[i][2],x+300,ry+30,Gfx.MONO,12,Theme.MUTED,Gfx.LEFT,0);
        }
    }

    // ---- choice sheet ----------------------------------------------------------------------
    public void choose(String title,String[] options,int selected,IntConsumer pick){sheetTitle=title;sheetOptions=options;sheetSelected=selected;sheetPick=pick;}
    private void sheet(float x,float y,float w,float h){
        g.rect(x,y,w,h,Theme.SCRIM);ui.block(x,y,w,h);
        int rows=(sheetOptions.length+1)/2;float pw=Math.min(560,w-40),ph=60+rows*58+66;
        float px=x+(w-pw)/2,py=y+(h-ph)/2;
        ui.panel(px,py,pw,ph);ui.block(px,py,pw,ph);
        g.text(sheetTitle.toUpperCase(Locale.ROOT),px+20,py+36,Gfx.DISPLAY,16,Theme.PHOSPHOR,Gfx.LEFT,.02f);
        float bw=(pw-50)/2;
        for(int i=0;i<sheetOptions.length;i++){
            final int n=i;
            ui.button("sheet."+i,px+20+(i%2)*(bw+10),py+60+(i/2)*58,bw,48,sheetOptions[i],i==sheetSelected?Ui.SELECTED:Ui.NORMAL,true,()->{
                IntConsumer p=sheetPick;sheetOptions=null;p.accept(n);
            });
        }
        ui.button("sheet.cancel",px+pw-140,py+ph-62,120,44,"Cancel",Ui.QUIET,true,()->sheetOptions=null);
    }

    // ---- bottom bar ------------------------------------------------------------------------
    private void bottomBar(){
        g.line(16,698,W-16,698,1,Theme.BORDER);
        boolean audio=e.audioOn(),env=audio&&e.value(Engine.HABITAT)>0,music=audio&&e.musicPlaying();
        ui.button("transport.environment",16,714,145,47,env?"Pause environment":"Start environment",Ui.NORMAL,true,
            ()->e.scape(env?Engine.SCAPE_PAUSE_ENVIRONMENT:Engine.SCAPE_START_ENVIRONMENT));
        g.text(env?"ENVIRONMENT LIVE":"ENVIRONMENT OFF",16+72.5f,777,Gfx.MONO,9,env?Theme.PHOSPHOR:Theme.MUTED,Gfx.CENTER,.04f);
        ui.button("transport.music",169,714,145,47,music?"Stop music":"Play music",Ui.NORMAL,true,
            ()->e.scape(music?Engine.SCAPE_STOP_MUSIC:Engine.SCAPE_PLAY_MUSIC));
        g.text(music?"MUSIC LIVE":"MUSIC STOPPED",169+72.5f,777,Gfx.MONO,9,music?Theme.PHOSPHOR:Theme.MUTED,Gfx.CENTER,.04f);
        ui.button("transport.stopall",323,723,128,47,"STOP ALL",Ui.STOP,true,e::stopAll);
        g.text(audio?"AUDIO ON":"AUDIO OFF",466,729,Gfx.MONO,9.5f,audio?Theme.PHOSPHOR:Theme.MUTED,Gfx.LEFT,.04f);
        g.text("REC OFF",466,762,Gfx.MONO,9.5f,Theme.MUTED,Gfx.LEFT,.04f);
        ui.button("record",W-529,723,128,47,"Record",Ui.NORMAL,false,null);
        ui.button("quickmix",W-393,723,86,47,"Quick mix",Ui.NORMAL,true,()->go(UiState.Screen.MIXER));
        ui.button("save",W-123,723,106,47,"Save session",Ui.NORMAL,true,e::saveSession);
    }
    private void notice(){
        if(s.notice==null||ui.now>s.noticeUntil)return;
        float tw=g.textWidth(s.notice,Gfx.MONO,12,0)+40,x=(W-tw)/2,y=650;
        g.rect(x,y,tw,34,Theme.PANEL);g.frame(x,y,tw,34,1,Theme.REQUEST);
        g.text(s.notice,W/2,y+22,Gfx.MONO,12,Theme.TEXT,Gfx.CENTER,0);
    }
}
