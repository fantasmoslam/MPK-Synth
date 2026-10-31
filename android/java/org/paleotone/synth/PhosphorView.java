/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import org.paleotone.synth.ui.*;

/**
 * The round-three interface: one view that draws every screen through Gfx and routes
 * touches to its controls. It redraws only when something changes, at most ~30 times a
 * second, and at 10 Hz for engine state; drawing never touches the audio thread.
 */
final class PhosphorView extends View {
    final Ui ui=new Ui();
    final Screens screens;
    private final CanvasGfx gfx;
    private float scale=1;
    private long lastDraw;

    PhosphorView(Context context,UiState state,Engine engine){
        super(context);
        screens=new Screens(ui,state,engine);
        gfx=new CanvasGfx(context.getAssets());
        setBackgroundColor(Theme.BACKGROUND);
    }
    float scale(){return scale;}
    /** Called every 33 ms by the activity. */
    void tick(){long now=SystemClock.uptimeMillis();if(ui.dirty()||now-lastDraw>=100)invalidate();}

    @Override protected void onDraw(Canvas canvas){
        lastDraw=SystemClock.uptimeMillis();
        scale=getHeight()/800f;
        canvas.save();canvas.scale(scale,scale);gfx.canvas=canvas;
        ui.begin(gfx,getWidth()/scale,getHeight()/scale,lastDraw);screens.draw();ui.end();
        canvas.restore();
        if(listener!=null)listener.run();
    }
    Runnable listener;

    @Override public boolean onTouchEvent(MotionEvent e){
        int action=e.getActionMasked(),index=e.getActionIndex();
        switch(action){
            case MotionEvent.ACTION_DOWN:case MotionEvent.ACTION_POINTER_DOWN:
                ui.down(e.getPointerId(index),e.getX(index)/scale,e.getY(index)/scale);break;
            case MotionEvent.ACTION_MOVE:
                for(int i=0;i<e.getPointerCount();i++)ui.move(e.getPointerId(i),e.getX(i)/scale,e.getY(i)/scale);break;
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_POINTER_UP:
                ui.up(e.getPointerId(index),e.getX(index)/scale,e.getY(index)/scale);break;
            case MotionEvent.ACTION_CANCEL:ui.cancel();break;
            default:return true;
        }
        invalidate();return true;
    }

    /** Gfx on android.graphics.Canvas. */
    static final class CanvasGfx implements Gfx {
        Canvas canvas;
        private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG),stroke=new Paint(Paint.ANTI_ALIAS_FLAG),text=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
        private final Typeface[] fonts=new Typeface[5];
        private final RectF oval=new RectF();
        private final Path path=new Path();
        CanvasGfx(android.content.res.AssetManager assets){
            String[] files={"IBMPlexMono-Regular.ttf","IBMPlexMono-Medium.ttf","Michroma-Regular.ttf","Caveat[wght].ttf","IMFeENrm28P.ttf"};
            for(int i=0;i<5;i++){
                try{fonts[i]=Typeface.createFromAsset(assets,"fonts/"+files[i]);}catch(RuntimeException e){fonts[i]=Typeface.MONOSPACE;}
            }
            fill.setStyle(Paint.Style.FILL);stroke.setStyle(Paint.Style.STROKE);
        }
        private Paint line(float width,int color){stroke.setStrokeWidth(width);stroke.setColor(color);stroke.setPathEffect(null);stroke.setStrokeCap(Paint.Cap.BUTT);return stroke;}
        public void rect(float x,float y,float w,float h,int color){fill.setColor(color);canvas.drawRect(x,y,x+w,y+h,fill);}
        public void frame(float x,float y,float w,float h,float width,int color){canvas.drawRect(x+width/2,y+width/2,x+w-width/2,y+h-width/2,line(width,color));}
        public void line(float x1,float y1,float x2,float y2,float width,int color){canvas.drawLine(x1,y1,x2,y2,line(width,color));}
        public void circle(float cx,float cy,float r,int color){fill.setColor(color);canvas.drawCircle(cx,cy,r,fill);}
        public void ring(float cx,float cy,float r,float width,int color){canvas.drawCircle(cx,cy,r,line(width,color));}
        public void arc(float cx,float cy,float r,float start,float sweep,float width,int color){
            oval.set(cx-r,cy-r,cx+r,cy+r);canvas.drawArc(oval,start-90,sweep,false,line(width,color));
        }
        private Path path(float[] xy,boolean closed){path.reset();path.moveTo(xy[0],xy[1]);for(int i=2;i<xy.length;i+=2)path.lineTo(xy[i],xy[i+1]);if(closed)path.close();return path;}
        public void polygon(float[] xy,int color){fill.setColor(color);canvas.drawPath(path(xy,true),fill);}
        public void polyline(float[] xy,boolean closed,float width,int color,float dash){
            Paint p=line(width,color);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            if(dash>0)p.setPathEffect(new DashPathEffect(new float[]{dash,dash},0));
            canvas.drawPath(path(xy,closed),p);p.setPathEffect(null);
        }
        private Paint font(int f,float size,float tracking,int color){text.setTypeface(fonts[f]);text.setTextSize(size);text.setLetterSpacing(tracking);text.setColor(color);return text;}
        public void text(String s,float x,float y,int f,float size,int color,int align,float tracking){
            Paint p=font(f,size,tracking,color);
            p.setTextAlign(align==CENTER?Paint.Align.CENTER:align==RIGHT?Paint.Align.RIGHT:Paint.Align.LEFT);
            canvas.drawText(s,x,y,p);
        }
        public float textWidth(String s,int f,float size,float tracking){return font(f,size,tracking,0).measureText(s);}
        public void clip(float x,float y,float w,float h){canvas.save();canvas.clipRect(x,y,x+w,y+h);}
        public void unclip(){canvas.restore();}
    }
}
