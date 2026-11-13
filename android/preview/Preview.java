/* SPDX-License-Identifier: GPL-3.0-only */
import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;
import org.fantasmoslam.mpksynth.ui.*;

/**
 * Desktop screenshots of the tablet interface for review, drawn by the same screen code
 * the app runs, through Java2D instead of Android's Canvas, with the synth's real model
 * tables (android/tools/tables.c). Text rendering differs slightly from the tablet.
 * Usage: android/preview/render.sh OUTPUT_DIR
 */
public final class Preview {
    static final class Java2dGfx implements Gfx {
        final Graphics2D g;final Font[] fonts=new Font[Gfx.FONTS.length];
        final HashMap<String,Font> cache=new HashMap<>();
        Java2dGfx(Graphics2D g,String fontDir)throws Exception{
            this.g=g;
            for(int i=0;i<fonts.length;i++)fonts[i]=Font.createFont(Font.TRUETYPE_FONT,new File(fontDir,Gfx.FONTS[i]));
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

    static final class StubHost implements Host {
        public int master(){return 700;}
        public void master(int v){}
        public boolean audioOn(){return true;}
        public void audition(int part,int note,boolean on){}
        public void panic(){}
    }
    interface Setup{void apply(Home home,State s,Rig rig);}

    static void shot(File out,String name,int width,int height,TableSource tables,String fonts,long now,Setup setup)throws Exception{
        float scale=height/800f;BufferedImage img=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=img.createGraphics();g.scale(scale,scale);
        Java2dGfx gfx=new Java2dGfx(g,fonts);
        State s=new State();Rig rig=new Rig(new Catalog(tables),null);Ui ui=new Ui();
        Home home=new Home(ui,s,rig,new StubHost());
        s.tab=State.TAB_SOUND;
        setup.apply(home,s,rig);
        // Two frames: the first registers the controls, the second draws with them.
        for(int f=0;f<2;f++){ui.begin(gfx,width/scale,800,now);home.draw();ui.end();}
        ImageIO.write(img,"png",new File(out,name+"-"+width+".png"));
    }
    static void lesson(Home h,int lesson,int step){h.tutorial.open(lesson);for(int i=0;i<step;i++)h.tutorial.next();}

    public static void main(String[] args)throws Exception{
        File out=new File(args[0]);out.mkdirs();String fonts=args[1];
        TableSource tables=new TableSource(args[2]);
        long now=100000;
        for(int width:new int[]{1920,2000}){
            int height=1200;
            shot(out,"sound-poly106",width,height,tables,fonts,now,(h,s,r)->{});
            shot(out,"sound-poly106-mpk",width,height,tables,fonts,now,(h,s,r)->{
                h.connected(true,now-5000);s.notice("MPK mini connected",now-500);
                h.knob(0,10,now-900);h.knob(1,30,now-800);h.knob(1,31,now-700);
            });
            shot(out,"sound-funky-bass",width,height,tables,fonts,now,(h,s,r)->{s.keyPart=1;r.bank(1,1);});
            shot(out,"sound-kit",width,height,tables,fonts,now,(h,s,r)->{s.keyPart=3;r.lastPad=2;});
            shot(out,"picker-presets",width,height,tables,fonts,now,(h,s,r)->{s.picking=State.PICK_PRESET;s.pickPage=-1;});
            shot(out,"picker-models",width,height,tables,fonts,now,(h,s,r)->{s.picking=State.PICK_MODEL;s.pickPage=-1;});
            shot(out,"coach-parts",width,height,tables,fonts,now,(h,s,r)->{s.tab=State.TAB_LEARN;lesson(h,2,1);h.openLesson(2);h.tutorial.next();s.tab=State.TAB_SOUND;s.coached=true;});
            int[][] lessons={{0,0},{0,1},{1,0},{1,2},{2,0},{3,0},{4,0},{4,1},{4,2},{4,4},{4,5},{5,0},{5,3},{6,0},{8,0},{10,1}};
            for(int[] l:lessons)shot(out,"learn-"+(l[0]+1)+"-"+(l[1]+1),width,height,tables,fonts,now,(h,s,r)->{s.tab=State.TAB_LEARN;lesson(h,l[0],l[1]);});
            shot(out,"learn-glossary",width,height,tables,fonts,now,(h,s,r)->{s.tab=State.TAB_LEARN;s.glossary=true;});
        }
    }
}
