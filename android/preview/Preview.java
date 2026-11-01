/* SPDX-License-Identifier: GPL-3.0-only */
import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;
import org.paleotone.synth.ui.*;

/**
 * Desktop screenshots of the tablet interface for review, drawn by the same screen code
 * the app runs, through Java2D instead of Android's Canvas. Text rendering differs
 * slightly from the tablet. Usage: android/preview/render.sh OUTPUT_DIR
 */
public final class Preview {
    static final class Java2dGfx implements Gfx {
        final Graphics2D g;final Font[] fonts=new Font[5];
        final HashMap<String,Font> cache=new HashMap<>();
        Java2dGfx(Graphics2D g,String fontDir)throws Exception{
            this.g=g;
            String[] files={"IBMPlexMono-Regular.ttf","IBMPlexMono-Medium.ttf","Michroma-Regular.ttf","Caveat[wght].ttf","IMFeENrm28P.ttf"};
            for(int i=0;i<5;i++)fonts[i]=Font.createFont(Font.TRUETYPE_FONT,new File(fontDir,files[i]));
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);
        }
        Color c(int argb){return new Color(argb,true);}
        Font font(int f,float size,float tracking){
            String k=f+"/"+size+"/"+tracking;Font x=cache.get(k);
            if(x==null){Map<TextAttribute,Object> a=new HashMap<>();a.put(TextAttribute.TRACKING,tracking);x=fonts[f].deriveFont(size).deriveFont(a);cache.put(k,x);}
            return x;
        }
        public void rect(float x,float y,float w,float h,int color){g.setColor(c(color));g.fill(new Rectangle2D.Float(x,y,w,h));}
        public void frame(float x,float y,float w,float h,float width,int color){g.setColor(c(color));g.setStroke(new BasicStroke(width));g.draw(new Rectangle2D.Float(x+width/2,y+width/2,w-width,h-width));}
        public void line(float x1,float y1,float x2,float y2,float width,int color){g.setColor(c(color));g.setStroke(new BasicStroke(width,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER));g.draw(new Line2D.Float(x1,y1,x2,y2));}
        public void circle(float cx,float cy,float r,int color){g.setColor(c(color));g.fill(new Ellipse2D.Float(cx-r,cy-r,r*2,r*2));}
        public void ring(float cx,float cy,float r,float width,int color){g.setColor(c(color));g.setStroke(new BasicStroke(width));g.draw(new Ellipse2D.Float(cx-r,cy-r,r*2,r*2));}
        public void arc(float cx,float cy,float r,float start,float sweep,float width,int color){
            g.setColor(c(color));g.setStroke(new BasicStroke(width));g.draw(new Arc2D.Float(cx-r,cy-r,r*2,r*2,90-start,-sweep,Arc2D.OPEN));
        }
        Path2D.Float path(float[] xy,boolean closed){Path2D.Float p=new Path2D.Float();p.moveTo(xy[0],xy[1]);for(int i=2;i<xy.length;i+=2)p.lineTo(xy[i],xy[i+1]);if(closed)p.closePath();return p;}
        public void polygon(float[] xy,int color){g.setColor(c(color));g.fill(path(xy,true));}
        public void polyline(float[] xy,boolean closed,float width,int color,float dash){
            g.setColor(c(color));g.setStroke(dash>0?new BasicStroke(width,BasicStroke.CAP_BUTT,BasicStroke.JOIN_ROUND,10,new float[]{dash,dash},0):new BasicStroke(width,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            g.draw(path(xy,closed));
        }
        public void text(String s,float x,float y,int f,float size,int color,int align,float tracking){
            Font ft=font(f,size,tracking);g.setFont(ft);g.setColor(c(color));
            float w=(float)ft.getStringBounds(s,g.getFontRenderContext()).getWidth();
            g.drawString(s,align==CENTER?x-w/2:align==RIGHT?x-w:x,y);
        }
        public float textWidth(String s,int f,float size,float tracking){return (float)font(f,size,tracking).getStringBounds(s,g.getFontRenderContext()).getWidth();}
        public void clip(float x,float y,float w,float h){g.setClip(new Rectangle2D.Float(x,y,w,h));}
        public void unclip(){g.setClip(null);}
    }

    /** Fixed example values matching the round-three boards. */
    static final class ExampleEngine implements Engine {
        final int[] v=new int[48];
        static final String[] ROLES={"Bed","Melody","Bass","Pulse","Free"};
        ExampleEngine(){
            v[TEMPO]=72;v[HABITAT]=60;v[DRONE]=30;v[EVOLVING]=1;v[HOLD]=1;v[BIOME]=1;v[MOOD]=0;
            int[] levels={58,44,62,34};for(int i=0;i<4;i++){v[LEVEL+i]=levels[i];v[ROLE+i]=i;}
            v[MUTE+3]=1;v[PAN+1]=12;
            int[] layers={32,25,0,5,12,18,8,6};for(int i=0;i<8;i++)v[LAYER+i]=layers[i];
        }
        public int value(int slot){return v[slot];}
        public int min(int slot){return slot>=PAN&&slot<PAN+4?-31:0;}
        public int max(int slot){return slot==BIOME?7:slot==MOOD?5:slot==TEMPO?240:slot>=ROLE&&slot<ROLE+4?4:slot>=MUTE&&slot<MUTE+4?1:slot>=PAN&&slot<PAN+4?31:100;}
        public String text(int slot){
            if(slot==TEMPO)return v[slot]+" BPM";
            if(slot==BIOME)return Screens.BIOMES[v[slot]];
            if(slot==MOOD)return Screens.MOODS[v[slot]];
            if(slot>=ROLE&&slot<ROLE+4)return ROLES[v[slot]];
            if(slot>=PAN&&slot<PAN+4)return v[slot]==0?"C":Math.abs(v[slot])+(v[slot]<0?" L":" R");
            return Integer.toString(v[slot]);
        }
        public String[] names(int slot){
            if(slot==BIOME)return Screens.BIOMES;if(slot==MOOD)return Screens.MOODS;
            if(slot>=ROLE&&slot<ROLE+4)return ROLES;return new String[0];
        }
        public Voice voice(int part){
            return new Voice(0,"Analog",new String[]{"Warm bed","Air / flute","Low / round","Soft pulse"}[part],new Param[]{
                new Param("WAVE",true,0,6,1,false,"SAW"),new Param("DTN",false,0,127,10,false,"10 ct"),
                new Param("MIX",false,0,127,64,false,"50%"),new Param("NOIS",false,0,127,0,false,"0%"),
                new Param("CUT",false,0,127,90,false,"1.2 kHz"),new Param("RES",false,0,127,30,false,"24%"),
                new Param("DRV",false,0,127,0,false,"0%"),new Param("KTR",false,-64,63,0,true,"0%")});
        }
        public String[] valueNames(int part,int param){return new String[]{"SINE","SAW","SQUARE","TRI","PULSE","SUPER","NOISE"};}
        public void setParam(int part,int param,int value){}
        public Choice[] engines(){String[] n={"Analog","Saurus","Phase","Lofi","Sample","Voice","Trio","Wheel","Grain","Phys","Drum","Noise","FM6","Roar"};
            Choice[] c=new Choice[n.length];for(int i=0;i<n.length;i++)c[i]=new Choice(i,n[i]);return c;}
        public Choice[] presets(int engine){return new Choice[]{new Choice(0,"Warm bed"),new Choice(1,"Glass"),new Choice(2,"Reed")};}
        public void setEngine(int part,int engine){}
        public void setPreset(int part,int preset){}
        final Library library=new Library(){
            float[] wave(int seed){float[] p=new float[120];java.util.Random r=new java.util.Random(seed);for(int i=0;i<p.length;i++)p[i]=.35f+.6f*r.nextFloat();return p;}
            final Sound[] list={new Sound("a","Flute C4.wav",60,1.2f,wave(1)),new Sound("b","Jungle C3.wav",48,8f,wave(2)),
                new Sound("c","Riverbank study.wav",48,8f,wave(3)),new Sound("d","Lowland calls.wav",48,8f,wave(4))};
            public Sound[] sounds(){return list;}
            public String loaded(){return "c";}
            public boolean sampleSource(){return true;}
            public void useSample(boolean on){}
            public void load(String id){}
            public void preview(String id){}
            public void setRoot(String id,int note){}
            public void delete(String id){}
            public void importWav(){}
            public String status(){return null;}
        };
        public Library library(){return library;}
        int recState=Recorder.READY;
        final Recorder recorder=new Recorder(){
            public int state(){return recState;}
            public long takeMillis(){return recState==READY?0:314000;}
            public String takeName(){return recState==READY?null:"Lowland-study-0472-take-03.wav";}
            public String error(){return recState==ERROR?"No space left on device":null;}
            public float[] levels(){if(recState==READY)return new float[0];float[] p=new float[90];java.util.Random r=new java.util.Random(9);for(int i=0;i<p.length;i++)p[i]=.3f+.65f*r.nextFloat();return p;}
            public long freeBytes(){return 21_400_000_000L;}
            public String destination(){return "Music/Paleotone";}
            public void start(String run){}
            public void stop(){}
            public void retry(){}
            public void discard(){}
            public Take[] takes(){
                Take[] t={new Take("Lowland-study-0472-take-01.wav",161,SAVED,null),new Take("Lowland-study-0472-take-02.wav",58,SAVED,null)};
                if(recState==READY)return t;
                return new Take[]{t[0],t[1],new Take("Lowland-study-0472-take-03.wav",314,recState,null)};
            }
        };
        public boolean habitatEngine(){return false;}
        public Recorder recorder(){return recorder;}
        public void fade(int ms){}
        public boolean faded(){return false;}
        public void set(int slot,int value){v[slot]=value;}
        public boolean scape(int command){return true;}
        public boolean audioOn(){return true;}
        public boolean musicPlaying(){return true;}
        public int master(){return 665;}
        public void setMaster(int value){}
        public void stopAll(){}
        public void saveSession(){}
        public void action(String name){}
    }

    /** Walks the stand-in expedition to station 7 with a few encounters, then meets a herd. */
    static void walk(UiState state,int stage){
        Expedition ex=state.expedition();
        while(ex.station<6){
            if(ex.current()!=null)for(int i=0;i<8&&ex.current()!=null;i++)ex.act(Expedition.OBSERVE);
            else ex.act(Expedition.MOVE_ON);
        }
        if(ex.current()!=null)for(int i=0;i<8&&ex.current()!=null;i++)ex.act(Expedition.OBSERVE);
        ex.act(Expedition.FOLLOW_HERD);
        for(int i=0;i<stage;i++)ex.act(Expedition.OBSERVE);
        ex.requested=Expedition.OBSERVE;
    }
    public static void main(String[] args)throws Exception{
        File out=new File(args[0]);out.mkdirs();String fonts=args[1];
        String[][] shots={
            {"focus-instrument","FOCUS","INSTRUMENT"},{"performance-instrument","PERFORMANCE","INSTRUMENT"},
            {"focus-settings","FOCUS","SETTINGS"},{"performance-expedition","PERFORMANCE","EXPEDITION"},
            {"focus-instrument-biome-sheet","FOCUS","INSTRUMENT"},
            {"focus-mixer","FOCUS","MIXER"},{"performance-mixer","PERFORMANCE","MIXER"},
            {"focus-mixer-layers","FOCUS","MIXER"},{"performance-mixer-layers","PERFORMANCE","MIXER"},
            {"focus-sounddesign","FOCUS","SOUND_DESIGN"},{"performance-sounddesign","PERFORMANCE","SOUND_DESIGN"},
            {"focus-sounddesign-voice-sheet","FOCUS","SOUND_DESIGN"},
            {"focus-quickmix","FOCUS","INSTRUMENT"},{"performance-quickmix","PERFORMANCE","MIXER"},
            {"focus-library","FOCUS","LIBRARY"},{"performance-library","PERFORMANCE","LIBRARY"},
            {"focus-library-recordings","FOCUS","LIBRARY"},
            {"focus-recording-ready","FOCUS","RECORDING"},{"focus-recording-recording","FOCUS","RECORDING"},
            {"focus-recording-error","FOCUS","RECORDING"},{"performance-recording-recording","PERFORMANCE","RECORDING"},
            {"focus-instrument-recording","FOCUS","INSTRUMENT"},
            {"focus-runlength","FOCUS","START"},{"focus-runend-ending","FOCUS","EXPEDITION"},
            {"focus-runend-ended","FOCUS","EXPEDITION"},{"focus-runend-ended-no-take","FOCUS","EXPEDITION"},
            {"performance-runend-ended","PERFORMANCE","EXPEDITION"},
            {"focus-expedition","FOCUS","EXPEDITION"},{"focus-expedition-idle","FOCUS","EXPEDITION"},
            {"focus-map","FOCUS","EXPEDITION"},{"performance-map","PERFORMANCE","EXPEDITION"},{"focus-map-tools","FOCUS","EXPEDITION"},
            {"focus-map-zoom","FOCUS","EXPEDITION"},{"focus-map-override","FOCUS","EXPEDITION"},
            {"focus-encounter-naming","FOCUS","EXPEDITION"},{"focus-encounter-signs","FOCUS","EXPEDITION"},{"performance-encounter-naming","PERFORMANCE","EXPEDITION"},
            {"focus-journal","FOCUS","JOURNAL"},{"performance-journal","PERFORMANCE","JOURNAL"},
            {"focus-bestiary","FOCUS","BESTIARY"},{"performance-bestiary","PERFORMANCE","BESTIARY"},
            {"focus-start","FOCUS","START"},{"performance-start","PERFORMANCE","START"}};
        for(String[] shot:shots)for(int width:new int[]{1920,2000}){
            float scale=1.5f;BufferedImage img=new BufferedImage(width,1200,BufferedImage.TYPE_INT_RGB);
            Graphics2D g=img.createGraphics();g.scale(scale,scale);
            UiState state=new UiState();state.layout=UiState.Layout.valueOf(shot[1]);state.screen=UiState.Screen.valueOf(shot[2]);
            state.runElapsed=8*60000+42000;state.runLength=30*60000;
            ExampleEngine engine=new ExampleEngine();Ui ui=new Ui();Screens screens=new Screens(ui,state,engine);
            if(shot[0].contains("layers"))state.mixerPage=1;
            if(shot[0].contains("quickmix"))state.quickMix=true;
            if(shot[0].contains("recordings"))state.libraryTab=1;
            if(shot[0].equals("focus-library")||shot[0].equals("performance-library"))state.librarySelected="c";
            if(shot[0].contains("-recording")&&!shot[0].contains("library"))engine.recState=Recorder.RECORDING;
            if(shot[0].endsWith("error"))engine.recState=Recorder.ERROR;
            if(shot[0].contains("runend-ended")&&!shot[0].endsWith("no-take"))engine.recState=Recorder.SAVED;
            if(shot[0].contains("runend-ending"))engine.recState=Recorder.RECORDING;
            if(shot[0].contains("runend")){state.runElapsed=state.runLength;state.runPhase=shot[0].contains("ending")?UiState.ENDING:UiState.ENDED;}
            if(!shot[0].endsWith("idle"))walk(state,shot[0].contains("signs")?0:3);
            if(shot[0].contains("map")){state.mapOpen=true;}
            if(shot[0].endsWith("tools"))state.mapTools=true;
            if(shot[0].endsWith("zoom")){Valley.Place p=state.expedition().place();state.mapScale=2.2f;state.mapX=p.x;state.mapY=p.y;}
            if(shot[0].endsWith("override")){
                Valley v=state.expedition().valley;Valley.Place a=v.landmarks[0],p=state.expedition().place();
                state.override=new float[]{(p.x*2+a.x)/3,(p.y*2+a.y)/3+18,a.x,a.y};state.plotting=true;
            }
            if(shot[0].contains("encounter"))state.encounterOpen=true;
            if(shot[0].contains("bestiary")){Expedition ex=state.expedition();state.bestiarySelected=ex.encounters.get(ex.encounters.size()-1).species;}
            if(shot[0].contains("start"))state.nextLeader=1;
            long now=shot[0].contains("ending")?3000:0;
            if(shot[0].endsWith("runlength"))screens.openRunLength();
            if(shot[0].endsWith("biome-sheet"))screens.choose("Biome",Screens.BIOMES,1,v->{});
            if(shot[0].endsWith("voice-sheet")){Engine.Choice[] c=engine.engines();String[] n=new String[c.length];for(int i=0;i<n.length;i++)n[i]=c[i].name;screens.choose("Voice for part 1",n,0,v->{});}
            ui.begin(new Java2dGfx(g,fonts),width/scale,1200/scale,now);screens.draw();ui.end();
            g.dispose();
            ImageIO.write(img,"png",new File(out,shot[0]+(width==2000?"-2000":"")+".png"));
        }
    }
}
