/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.IntConsumer;

/**
 * An immediate-mode control kit. Each frame the screens draw their controls and
 * register where they can be touched; touches are matched against the regions of the
 * last frame drawn. Several fingers can hold several controls at once.
 */
public final class Ui {
    public interface Drag{void start(float x,float y);void move(float x,float y);void end();}
    /**
     * One or more fingers on a map: update gets the fingers' centre and spread (0 for one
     * finger) whenever they move or a finger lands or lifts; end reports a tap when no
     * finger moved.
     */
    public interface Pan{void update(float cx,float cy,float spread,int fingers);void end(boolean tap,float x,float y);}
    public static final int NORMAL=0,SELECTED=1,STOP=2,QUIET=3,RECORD=4;

    private static final class Hit{
        final String id;final float x,y,w,h;final Runnable tap;final Drag drag;Pan pan;
        Hit(String id,float x,float y,float w,float h,Runnable tap,Drag drag){this.id=id;this.x=x;this.y=y;this.w=w;this.h=h;this.tap=tap;this.drag=drag;}
        boolean contains(float px,float py){return px>=x&&py>=y&&px<x+w&&py<y+h;}
    }
    private static final class Pointer{Hit hit;float downX,downY,x,y;boolean moved;}
    /** A value you set recently; engine snapshots may lag behind it for a moment. */
    private static final class Pending{float value;long since;}

    public Gfx g;
    public float width,height;
    public long now;
    private ArrayList<Hit> hits=new ArrayList<>(),building=new ArrayList<>();
    private final HashMap<Integer,Pointer> pointers=new HashMap<>();
    private final HashMap<String,Float> dragging=new HashMap<>();
    private final HashMap<String,Pending> pending=new HashMap<>();
    private boolean dirty=true;

    public void begin(Gfx g,float width,float height,long now){this.g=g;this.width=width;this.height=height;this.now=now;building=new ArrayList<>();}
    public void end(){hits=building;dirty=false;}
    public void invalidate(){dirty=true;}
    public boolean dirty(){return dirty||!pointers.isEmpty();}
    public boolean touching(){return !pointers.isEmpty();}

    // ---- touch -------------------------------------------------------------------
    public void down(int id,float x,float y){
        Hit target=null;
        for(int i=hits.size()-1;i>=0;i--)if(hits.get(i).contains(x,y)){target=hits.get(i);break;}
        if(target==null)return;
        if(target.pan==null)for(Pointer p:pointers.values())if(p.hit.id.equals(target.id))return; // one finger per control
        Pointer p=new Pointer();p.hit=target;p.downX=p.x=x;p.downY=p.y=y;pointers.put(id,p);
        if(target.pan!=null){panMoved=pinchCount(target.id)>1;pan(target);}
        if(target.drag!=null)target.drag.start(x,y);
        dirty=true;
    }
    public void move(int id,float x,float y){
        Pointer p=pointers.get(id);if(p==null)return;
        if(Math.abs(x-p.downX)>8||Math.abs(y-p.downY)>8)p.moved=true;
        p.x=x;p.y=y;
        if(p.hit.pan!=null){if(p.moved)panMoved=true;pan(p.hit);}
        if(p.hit.drag!=null)p.hit.drag.move(x,y);
        dirty=true;
    }
    public void up(int id,float x,float y){
        Pointer p=pointers.remove(id);if(p==null)return;
        if(p.hit.pan!=null){
            if(pinchCount(p.hit.id)==0)p.hit.pan.end(!panMoved&&!p.moved,x,y);else pan(p.hit);
            dirty=true;return;
        }
        if(p.hit.drag!=null)p.hit.drag.end();
        else if(p.hit.tap!=null&&p.hit.contains(x,y))p.hit.tap.run();
        dirty=true;
    }
    public void cancel(){
        for(Pointer p:pointers.values()){if(p.hit.drag!=null)p.hit.drag.end();if(p.hit.pan!=null)p.hit.pan.end(false,p.x,p.y);}
        pointers.clear();dirty=true;
    }
    private boolean panMoved;
    private int pinchCount(String hitId){int n=0;for(Pointer p:pointers.values())if(p.hit.id.equals(hitId))n++;return n;}
    private void pan(Hit h){
        float cx=0,cy=0;int n=0;
        for(Pointer p:pointers.values())if(p.hit.id.equals(h.id)){cx+=p.x;cy+=p.y;n++;}
        if(n==0)return;cx/=n;cy/=n;
        float spread=0;
        if(n>1)for(Pointer p:pointers.values())if(p.hit.id.equals(h.id))spread+=(float)Math.hypot(p.x-cx,p.y-cy);
        h.pan.update(cx,cy,n>1?spread/n:0,n);
    }
    public boolean held(String id){for(Pointer p:pointers.values())if(p.hit.id.equals(id))return true;return false;}

    public void hit(String id,float x,float y,float w,float h,Runnable tap){building.add(new Hit(id,x,y,w,h,tap,null));}
    public void hitDrag(String id,float x,float y,float w,float h,Drag drag){building.add(new Hit(id,x,y,w,h,null,drag));}
    public void hitPan(String id,float x,float y,float w,float h,Pan pan){Hit t=new Hit(id,x,y,w,h,null,null);t.pan=pan;building.add(t);}
    /** Swallows touches under an overlay. */
    public void block(float x,float y,float w,float h){building.add(new Hit("block@"+x+","+y,x,y,w,h,()->{},null));}

    /**
     * The value to show for a control: yours while dragging, or for 750 ms after
     * release until the engine catches up; otherwise the engine's.
     */
    public float shown(String id,float engine){
        Float d=dragging.get(id);if(d!=null)return d;
        Pending p=pending.get(id);
        if(p!=null){if(Math.abs(p.value-engine)<.5f||now-p.since>750)pending.remove(id);else return p.value;}
        return engine;
    }
    private void release(String id,float value){dragging.remove(id);Pending p=new Pending();p.value=value;p.since=now;pending.put(id,p);}

    // ---- drawing helpers -----------------------------------------------------------
    public void panel(float x,float y,float w,float h){g.rect(x,y,w,h,Theme.PANEL);g.frame(x,y,w,h,1,Theme.BORDER);}

    public void button(String id,float x,float y,float w,float h,String label,int style,boolean enabled,Runnable tap){
        int fill=Theme.RAISED,edge=Theme.BORDER,ink=Theme.TEXT;float edgeWidth=1;int font=Gfx.MONO;float size=13;
        if(style==SELECTED||style==RECORD){fill=Theme.SELECTED_FILL;edge=Theme.REQUEST;ink=Theme.REQUEST;}
        if(style==STOP){fill=Theme.SELECTED_FILL;edge=Theme.REQUEST;ink=Theme.REQUEST;edgeWidth=2;font=Gfx.DISPLAY;size=13;}
        if(style==QUIET){fill=Theme.BACKGROUND;}
        boolean pressed=held(id);
        if(pressed)fill=style==NORMAL||style==QUIET?Theme.TRACK:0xff3a3219;
        if(!enabled){fill=Theme.BACKGROUND;edge=0xff2f3d2b;ink=Theme.DIM;}
        g.rect(x,y,w,h,fill);g.frame(x,y,w,h,edgeWidth,edge);
        if(style==RECORD&&enabled){
            float tw=g.textWidth(label,font,size,0);float dot=5;float cx=x+w/2-(tw+dot*2+8)/2+dot;
            g.circle(cx,y+h/2,dot,Theme.REQUEST);
            g.text(label,cx+dot+8,y+h/2+size*.36f,font,size,ink,Gfx.LEFT,0);
        }else g.text(label,x+w/2,y+h/2+size*.36f,font,size,ink,Gfx.CENTER,style==STOP?.06f:0);
        if(enabled&&tap!=null)hit(id,x,y,w,h,tap);
    }

    /** A labelled value tile that opens a choice (Biome, Music mood). */
    public void tile(String id,float x,float y,float w,float h,String caption,String value,Runnable tap){
        boolean pressed=held(id);
        g.rect(x,y,w,h,pressed?Theme.TRACK:Theme.RAISED);g.frame(x,y,w,h,1,Theme.BORDER);
        g.text(caption,x+11,y+h*.36f,Gfx.MONO,10.5f,Theme.MUTED,Gfx.LEFT,.08f);
        g.text(value,x+11,y+h*.36f+21,Gfx.MONO,13,Theme.PHOSPHOR,Gfx.LEFT,.04f);
        if(tap!=null)hit(id,x,y,w,h,tap);
    }

    /**
     * A value tile you drag sideways to change (pan, tempo, root note). A tap without
     * dragging runs tap, if given (pan returns to centre).
     */
    public void dragTile(final String id,float x,float y,float w,float h,String caption,String text,
                         float value,final float min,final float max,final float unitsPerStep,final IntConsumer set,final Runnable tap){
        boolean pressed=held(id);
        g.rect(x,y,w,h,pressed?Theme.TRACK:Theme.RAISED);g.frame(x,y,w,h,1,pressed?Theme.REQUEST:Theme.BORDER);
        g.text(caption,x+11,y+h*.36f,Gfx.MONO,10.5f,Theme.MUTED,Gfx.LEFT,.08f);
        g.text(text,x+11,y+h*.36f+21,Gfx.MONO,13,pressed?Theme.REQUEST:Theme.PHOSPHOR,Gfx.LEFT,.04f);
        g.text("‹ ›",x+w-11,y+h*.36f,Gfx.MONO,10.5f,Theme.DIM,Gfx.RIGHT,0);
        final float start=shown(id,value);
        hitDrag(id,x,y,w,h,new Drag(){
            float x0,cur;boolean moved;
            public void start(float px,float py){x0=px;cur=start;moved=false;}
            public void move(float px,float py){
                if(Math.abs(px-x0)>8)moved=true;
                if(!moved)return;
                float v=Math.max(min,Math.min(max,start+Math.round((px-x0)/unitsPerStep)));
                if(Math.round(v)!=Math.round(cur)){cur=v;dragging.put(id,cur);set.accept(Math.round(cur));}
            }
            public void end(){if(moved)release(id,Math.round(cur));else if(tap!=null)tap.run();}
        });
    }

    /** A sound's peak levels as vertical bars, centred on a line. */
    public void waveform(float x,float y,float w,float h,float[] peaks,int color){
        g.line(x,y+h/2,x+w,y+h/2,1,Theme.TRACK);
        if(peaks==null||peaks.length==0)return;
        int bars=Math.max(8,(int)(w/8));float step=w/bars;
        for(int i=0;i<bars;i++){
            float p=peaks[Math.min(peaks.length-1,i*peaks.length/bars)];
            float bh=Math.max(2,h*Math.min(1,p));
            g.rect(x+i*step+step*.2f,y+(h-bh)/2,Math.max(1.5f,step*.45f),bh,color);
        }
    }

    /**
     * A conductor or parameter knob. Unipolar knobs sweep clockwise from 7 o'clock (minimum)
     * to 5 o'clock (maximum); bipolar knobs point to 12 at zero and show a centre tick.
     * Drag up or down anywhere in the touch area; move the finger sideways, away from the
     * knob, for fine adjustment.
     */
    public void knob(final String id,float cx,float cy,float d,float value,final float min,final float max,
                     boolean bipolar,String text,final IntConsumer set){
        float r=d/2;float v=shown(id,value);
        float t=max>min?(v-min)/(max-min):0;t=Math.max(0,Math.min(1,t));
        // ticks outside the rim
        for(int i=0;i<=10;i++){
            float a=(float)Math.toRadians(210+30*i);float r1=r+7,r2=r+(i%5==0?13:10);
            g.line(cx+(float)Math.sin(a)*r1,cy-(float)Math.cos(a)*r1,cx+(float)Math.sin(a)*r2,cy-(float)Math.cos(a)*r2,1,Theme.DIM);
        }
        if(bipolar)g.line(cx,cy-r-6,cx,cy-r-15,2,Theme.PHOSPHOR);
        g.circle(cx,cy,r,0xff101610);g.ring(cx,cy,r,2,Theme.KNOB_RIM);
        g.circle(cx,cy,r*.78f,Theme.KNOB_FACE);g.ring(cx,cy,r*.78f,1,0xff3b4a37);
        float a=(float)Math.toRadians(210+300*t);
        g.line(cx+(float)Math.sin(a)*r*.62f,cy-(float)Math.cos(a)*r*.62f,cx+(float)Math.sin(a)*r*.92f,cy-(float)Math.cos(a)*r*.92f,3.2f,Theme.REQUEST);
        g.text(text,cx,cy+r*.24f,Gfx.MONO,d*.25f,Theme.REQUEST,Gfx.CENTER,0);
        final float span=Math.max(1,max-min);
        float pad=Math.max(12,(64-d)/2);
        final float shownValue=v,knobX=cx,fineBeyond=Math.max(70,r+30);
        hitDrag(id,cx-r-pad,cy-r-pad,d+pad*2,d+pad*2,new Drag(){
            float lastY,cur;
            public void start(float x,float y){lastY=y;cur=shownValue;dragging.put(id,cur);}
            public void move(float x,float y){
                float fine=Math.abs(x-knobX)>fineBeyond?.25f:1f;
                cur=Math.max(min,Math.min(max,cur+(lastY-y)/180f*span*fine));lastY=y;
                dragging.put(id,cur);set.accept(Math.round(cur));
            }
            public void end(){release(id,Math.round(cur));}
        });
    }

    /** The conductor's live response: a vertical bar, or a dash while nothing answers. */
    public void gauge(float x,float y,float w,float h,float response,String caption,String text,float textSize){
        g.text(caption,x+w/2,y-9,Gfx.MONO,10,Theme.MUTED,Gfx.CENTER,.06f);
        g.rect(x,y,w,h,Theme.TRACK);
        if(!Float.isNaN(response)){float f=Math.max(0,Math.min(1,response));g.rect(x,y+h*(1-f),w,h*f,Theme.PHOSPHOR);}
        g.text(text==null?"—":text,x+w/2,y+h+textSize+8,Gfx.MONO,textSize,text==null?Theme.DIM:Theme.PHOSPHOR,Gfx.CENTER,0);
    }

    /** Horizontal response bar for the compact gauge list. */
    public void bar(float x,float y,float w,float h,float response){
        g.rect(x,y,w,h,Theme.TRACK);
        if(!Float.isNaN(response))g.rect(x,y,w*Math.max(0,Math.min(1,response)),h,Theme.PHOSPHOR);
    }

    /** A horizontal fader with a large handle; drag anywhere along it. */
    public void fader(final String id,final float x,float y,final float w,float value,final float min,final float max,final IntConsumer set){
        float v=shown(id,value);float t=max>min?(v-min)/(max-min):0;t=Math.max(0,Math.min(1,t));
        float track=6,hx=x+t*w;
        g.rect(x,y-track/2,w,track,Theme.TRACK);g.rect(x,y-track/2,hx-x,track,0xff8fd39b);
        g.rect(hx-10,y-13,20,26,held(id)?Theme.REQUEST:0xffc6f2cc);g.frame(hx-10,y-13,20,26,1,Theme.BACKGROUND);
        hitDrag(id,x-14,y-30,w+28,60,new Drag(){
            float cur;
            void to(float px){cur=min+Math.max(0,Math.min(1,(px-x)/w))*(max-min);dragging.put(id,cur);set.accept(Math.round(cur));}
            public void start(float px,float py){to(px);}
            public void move(float px,float py){to(px);}
            public void end(){release(id,Math.round(cur));}
        });
    }
}
