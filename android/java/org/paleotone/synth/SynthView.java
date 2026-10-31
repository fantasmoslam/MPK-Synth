/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth;
import android.content.Context;
import android.graphics.*;
import android.util.SparseArray;
import android.view.*;

final class SynthView extends View {
    private static final int BG=0xff111919,PANEL=0xff1e2a29,EDGE=0xff40534a,
        TEXT=0xffeee8cf,MUTED=0xff9ba99b,AMBER=0xffedb75b,TEAL=0xff77c6aa;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Bitmap screen=Bitmap.createBitmap(240,240,Bitmap.Config.ARGB_8888);
    private final int[] pixels=new int[240*240];
    private final MainActivity activity;
    private final SparseArray<Touch> touches=new SparseArray<>();
    private final RectF[] actions=new RectF[6], knobs=new RectF[7], minus=new RectF[7],plus=new RectF[7],buttons=new RectF[14];
    private final RectF[] keys=new RectF[27];
    private final boolean[] black=new boolean[27];
    private final String[] buttonNames={"FX","SCALE","ENV","LFO","EDIT","GLOBAL","HOME","SAVE","ARP","SEQ","PLAY","REC","OCT −","OCT +"};
    private final String[] encoderNames={"SELECT","ENGINE","PRESET","KNOB 1","KNOB 2","KNOB 3","KNOB 4"};
    private final String[] actionNames={"SAVE SESSION","STOP","MIDI","AUDIO","PANIC","HELP"};
    private final RectF master=new RectF(470,530,1248,562);
    private float scale=1,dx,dy;
    private int keyMask,buttonMask,volume=700,peak;
    private float holdX,holdY;
    private final Runnable maintenanceHold;
    private boolean running;
    String notice="Starting native engine…";
    private String stats="";
    private static final class Touch {int kind,index;float y;Touch(int k,int i,float y){kind=k;index=i;this.y=y;}}
    SynthView(Context context){
        super(context);activity=(MainActivity)context;setFocusable(true);
        maintenanceHold=()->{release();activity.maintenance();};
        for(int i=0;i<6;++i)actions[i]=new RectF(470+i*131,16,594+i*131,52);
        for(int i=0;i<7;++i){
            float x=i<3?538+i*255:507+(i-3)*207;
            float y=i<3?146:302;
            knobs[i]=new RectF(x-44,y-44,x+44,y+44);
            minus[i]=new RectF(x-59,y+56,x-5,y+92);
            plus[i]=new RectF(x+5,y+56,x+59,y+92);
        }
        for(int i=0;i<14;++i){int col=i%7,row=i/7;buttons[i]=new RectF(470+col*112,418+row*52,574+col*112,462+row*52);}
        /* F3..G5, 16 white keys. Black hit regions are tested first. */
        int whites=0;
        for(int i=0;i<27;++i){int note=(53+i)%12;black[i]=note==1||note==3||note==6||note==8||note==10;if(!black[i])++whites;}
        float w=1232f/whites;int white=0;
        for(int i=0;i<27;++i){
            if(black[i])keys[i]=new RectF(24+white*w-w*.3f,608,24+white*w+w*.3f,712);
            else {keys[i]=new RectF(24+white*w,608,24+(white+1)*w-2,778);++white;}
        }
    }
    void update(String stats,int peak,boolean running){
        this.stats=stats;this.peak=peak;this.running=running;
        if(getVisibility()!=VISIBLE)return;
        Native.pixels(pixels);screen.setPixels(pixels,0,240,0,0,240,240);invalidate();
    }
    private void text(Canvas c,String s,float x,float y,float size,int color){paint.setColor(color);paint.setTextSize(size);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));c.drawText(s,x,y,paint);}
    private void rect(Canvas c,RectF r,int color){paint.setColor(color);c.drawRoundRect(r,7,7,paint);}
    private void label(Canvas c,RectF r,String s,int color){paint.setTextAlign(Paint.Align.CENTER);text(c,s,r.centerX(),r.centerY()+6,17,color);paint.setTextAlign(Paint.Align.LEFT);}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);c.drawColor(BG);
        scale=Math.min(getWidth()/1280f,getHeight()/800f);dx=(getWidth()-1280*scale)/2;dy=(getHeight()-800*scale)/2;
        c.save();c.translate(dx,dy);c.scale(scale,scale);
        text(c,"PALEOTONE",24,43,32,AMBER);text(c,"ANDROID / NATIVE ALPHA 0.4.0",252,40,12,MUTED);
        for(int i=0;i<6;++i){rect(c,actions[i],i==4?0xff573d32:PANEL);label(c,actions[i],i==1?(running?"STOP":"START"):actionNames[i],TEXT);}
        text(c,stats,24,74,13,MUTED);
        rect(c,new RectF(16,88,440,512),EDGE);
        paint.setFilterBitmap(false);c.drawBitmap(screen,null,new RectF(28,100,428,500),paint);
        text(c,"LIVE FIRMWARE VIEW",26,536,14,TEAL);
        text(c,"Use SELECT to navigate; hold buttons for more",26,560,13,MUTED);
        for(int i=0;i<7;++i){
            RectF r=knobs[i];
            paint.setTextAlign(Paint.Align.CENTER);text(c,encoderNames[i],r.centerX(),r.top-14,15,TEAL);paint.setTextAlign(Paint.Align.LEFT);
            paint.setColor(EDGE);c.drawCircle(r.centerX(),r.centerY(),44,paint);
            paint.setColor(PANEL);c.drawCircle(r.centerX(),r.centerY(),36,paint);
            paint.setColor(AMBER);paint.setStrokeWidth(4);c.drawLine(r.centerX(),r.top+9,r.centerX(),r.top+25,paint);
            rect(c,minus[i],PANEL);rect(c,plus[i],PANEL);label(c,minus[i],"−",TEXT);label(c,plus[i],"+",TEXT);
        }
        for(int i=0;i<14;++i){rect(c,buttons[i],(buttonMask&(1<<i))!=0?TEAL:PANEL);label(c,buttons[i],buttonNames[i],(buttonMask&(1<<i))!=0?BG:TEXT);}
        rect(c,master,PANEL);
        rect(c,new RectF(master.left,master.top,master.left+master.width()*volume/1023f,master.bottom),EDGE);
        label(c,master,"MASTER  "+Math.round(volume*100f/1023)+"%",TEXT);
        paint.setColor(EDGE);c.drawRect(24,577,1256,583,paint);
        paint.setColor(peak>31000?AMBER:TEAL);c.drawRect(24,577,24+1232*peak/32767f,583,paint);
        text(c,notice,24,601,13,MUTED);
        for(int pass=0;pass<2;++pass)for(int i=0;i<27;++i)if(black[i]==(pass==1)){
            rect(c,keys[i],(keyMask&(1<<i))!=0?TEAL:black[i]?0xff15201e:0xffdad7c5);
            if(!black[i]){
                int note=53+i;String[] names={"C","C♯","D","D♯","E","F","F♯","G","G♯","A","A♯","B"};
                paint.setTextAlign(Paint.Align.CENTER);text(c,names[note%12]+(note/12-1),keys[i].centerX(),762,15,BG);paint.setTextAlign(Paint.Align.LEFT);
            }
        }
        c.restore();
    }
    private int keyAt(float x,float y){for(int pass=1;pass>=0;--pass)for(int i=0;i<27;++i)if(black[i]==(pass==1)&&keys[i].contains(x,y))return i;return -1;}
    private void masks(){
        int k=0,b=0;
        for(int i=0;i<touches.size();++i){Touch t=touches.valueAt(i);if(t.kind==1&&t.index>=0)k|=1<<t.index;if(t.kind==2)b|=1<<t.index;}
        if(k!=keyMask){keyMask=k;Native.keys(k);}if(b!=buttonMask){buttonMask=b;Native.buttons(b);}
        invalidate();
    }
    void release(){removeCallbacks(maintenanceHold);touches.clear();keyMask=buttonMask=0;Native.keys(0);Native.buttons(0);invalidate();}
    @Override public boolean onTouchEvent(android.view.MotionEvent e){
        int action=e.getActionMasked(),index=e.getActionIndex();
        if(action==MotionEvent.ACTION_CANCEL){release();return true;}
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
            removeCallbacks(maintenanceHold);
            float x=(e.getX(index)-dx)/scale,y=(e.getY(index)-dy)/scale;
            int id=e.getPointerId(index),key=keyAt(x,y);Touch t=null;
            if(key>=0)t=new Touch(1,key,y);
            for(int i=0;t==null&&i<14;++i)if(buttons[i].contains(x,y))t=new Touch(2,i,y);
            for(int i=0;t==null&&i<7;++i){
                if(knobs[i].contains(x,y))t=new Touch(3,i,y);
                else if(minus[i].contains(x,y)){Native.encoder(i,-1);t=new Touch(6,i,y);}
                else if(plus[i].contains(x,y)){Native.encoder(i,1);t=new Touch(6,i,y);}
            }
            for(int i=0;t==null&&i<6;++i)if(actions[i].contains(x,y))t=new Touch(4,i,y);
            if(t==null&&master.contains(x,y)){t=new Touch(5,0,y);setMaster(x);}
            if(t==null&&touches.size()==0&&x>=24&&x<445&&y>=10&&y<55){
                t=new Touch(7,0,y);holdX=x;holdY=y;postDelayed(maintenanceHold,3000);
            }
            if(t!=null)touches.put(id,t);
        }else if(action==MotionEvent.ACTION_MOVE){
            for(int i=0;i<e.getPointerCount();++i){
                Touch t=touches.get(e.getPointerId(i));if(t==null)continue;
                float x=(e.getX(i)-dx)/scale,y=(e.getY(i)-dy)/scale;
                if(t.kind==1)t.index=keyAt(x,y);
                else if(t.kind==3){int steps=(int)((t.y-y)/8);if(steps!=0){Native.encoder(t.index,Math.max(-32,Math.min(32,steps)));t.y=y;}}
                else if(t.kind==5)setMaster(x);
                else if(t.kind==7&&(Math.abs(x-holdX)>16||Math.abs(y-holdY)>16))removeCallbacks(maintenanceHold);
            }
        }else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP){
            Touch t=touches.get(e.getPointerId(index));touches.remove(e.getPointerId(index));masks();
            if(t!=null&&t.kind==7)removeCallbacks(maintenanceHold);
            if(t!=null&&t.kind==4){float x=(e.getX(index)-dx)/scale,y=(e.getY(index)-dy)/scale;if(actions[t.index].contains(x,y))activity.action(t.index);}
            performClick();
        }
        masks();return true;
    }
    private void setMaster(float x){volume=Math.max(0,Math.min(1023,Math.round((x-master.left)/master.width()*1023)));Native.master(volume);}
    @Override public boolean performClick(){super.performClick();return true;}
}
