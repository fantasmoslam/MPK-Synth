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
        ExampleEngine(){v[TEMPO]=72;v[HABITAT]=60;v[DRONE]=40;v[EVOLVING]=1;v[HOLD]=1;v[BIOME]=1;v[MOOD]=0;}
        public int value(int slot){return v[slot];}
        public int min(int slot){return 0;}
        public int max(int slot){return slot==BIOME?7:slot==MOOD?5:slot==TEMPO?240:100;}
        public String text(int slot){
            if(slot==TEMPO)return v[slot]+" BPM";
            if(slot==BIOME)return Screens.BIOMES[v[slot]];
            if(slot==MOOD)return Screens.MOODS[v[slot]];
            return Integer.toString(v[slot]);
        }
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

    public static void main(String[] args)throws Exception{
        File out=new File(args[0]);out.mkdirs();String fonts=args[1];
        String[][] shots={
            {"focus-instrument","FOCUS","INSTRUMENT"},{"performance-instrument","PERFORMANCE","INSTRUMENT"},
            {"focus-settings","FOCUS","SETTINGS"},{"performance-expedition","PERFORMANCE","EXPEDITION"},
            {"focus-instrument-biome-sheet","FOCUS","INSTRUMENT"}};
        for(String[] shot:shots)for(int width:new int[]{1920,2000}){
            float scale=1.5f;BufferedImage img=new BufferedImage(width,1200,BufferedImage.TYPE_INT_RGB);
            Graphics2D g=img.createGraphics();g.scale(scale,scale);
            UiState state=new UiState();state.layout=UiState.Layout.valueOf(shot[1]);state.screen=UiState.Screen.valueOf(shot[2]);
            state.runElapsed=8*60000+42000;state.runLength=30*60000;
            Engine engine=new ExampleEngine();Ui ui=new Ui();Screens screens=new Screens(ui,state,engine);
            if(shot[0].endsWith("sheet"))screens.choose("Biome",Screens.BIOMES,1,v->{});
            ui.begin(new Java2dGfx(g,fonts),width/scale,1200/scale,0);screens.draw();ui.end();
            g.dispose();
            ImageIO.write(img,"png",new File(out,shot[0]+(width==2000?"-2000":"")+".png"));
        }
    }
}
