/* SPDX-License-Identifier: GPL-3.0-only */
package org.fantasmoslam.mpksynth.ui;

import java.util.Locale;

/**
 * The LEARN tab: the lessons down the left, and the open step on the right with its
 * explanation, a picture that moves, and what it is waiting for you to do. Steps about the
 * SOUND screen open that screen with the step along the bottom (Home.coach).
 */
final class Learn {
    private final Home home;
    Learn(Home home){this.home=home;}

    void draw(float x,float y,float w,float h){
        Ui ui=home.ui;Gfx g=ui.g;State s=home.s;Tutorial t=home.tutorial;
        float lw=Math.min(330,w*.27f);
        ui.panel(x,y,lw,h);
        g.text("LESSONS",x+16,y+28,Gfx.MONO_MEDIUM,12,Theme.PHOSPHOR,Gfx.LEFT,.14f);
        float iy=y+42,ih=Math.min(46,(h-42-70)/Tutorial.LESSONS.length-6);
        for(int i=0;i<Tutorial.LESSONS.length;i++){
            final int l=i;
            boolean cur=!s.glossary&&i==t.lesson,done=t.done(i);
            String id="lesson."+i;
            float by=iy+i*(ih+6);
            g.rect(x+10,by,lw-20,ih,cur?Theme.SELECTED_FILL:ui.held(id)?Theme.TRACK:Theme.RAISED);
            g.frame(x+10,by,lw-20,ih,cur?2:1,cur?Theme.REQUEST:Theme.BORDER);
            g.text(Integer.toString(i+1),x+28,by+ih/2+5,Gfx.MONO_MEDIUM,13,cur?Theme.REQUEST:Theme.MUTED,Gfx.CENTER,0);
            g.text(Text.fit(g,Tutorial.LESSONS[i].title,lw-96,13),x+48,by+ih/2+5,Gfx.MONO,13,cur?Theme.REQUEST:Theme.TEXT,Gfx.LEFT,0);
            if(done)check(g,x+lw-32,by+ih/2,8,Theme.PHOSPHOR);
            ui.hit(id,x+10,by,lw-20,ih,()->home.openLesson(l));
        }
        ui.button("glossary",x+10,y+h-58,lw-20,46,"WORDS TO KNOW",s.glossary?Ui.SELECTED:Ui.NORMAL,true,()->s.glossary=!s.glossary);

        float cx=x+lw+16,cw=w-lw-16;
        ui.panel(cx,y,cw,h);
        if(s.glossary){glossary(cx,y,cw,h);return;}
        Tutorial.Lesson lesson=t.current();Tutorial.Step st=t.currentStep();
        g.text(("LESSON "+(t.lesson+1)+" OF "+Tutorial.LESSONS.length+" · "+lesson.title).toUpperCase(Locale.ROOT),cx+24,y+32,Gfx.MONO,11,Theme.MUTED,Gfx.LEFT,.1f);
        g.text("STEP "+(t.step+1)+" OF "+lesson.steps.length,cx+cw-24,y+32,Gfx.MONO,11,Theme.MUTED,Gfx.RIGHT,.1f);
        g.text(Text.fit(g,st.title,cw-48,Gfx.DISPLAY,22),cx+24,y+72,Gfx.DISPLAY,22,Theme.REQUEST,Gfx.LEFT,.02f);
        boolean picture=st.picture!=Tutorial.PIC_NONE;
        float tw=picture?cw*.42f:cw-48;
        float ty=Text.block(g,st.text,cx+24,y+92,tw,Gfx.MONO,15,Theme.TEXT,1.55f);
        // what the step waits for
        float sy=Math.max(ty+18,y+92);
        if(st.wait!=Tutorial.NEXT){
            boolean done=t.complete();
            float sh=56;
            g.rect(cx+24,sy,tw,sh,done?0xff1c2f1d:0xff2b2716);g.frame(cx+24,sy,tw,sh,1,done?Theme.PHOSPHOR:Theme.REQUEST);
            if(done){
                check(g,cx+48,sy+sh/2,10,Theme.PHOSPHOR);
                g.text("DONE. NICE!",cx+68,sy+sh/2+5,Gfx.MONO_MEDIUM,14,Theme.PHOSPHOR,Gfx.LEFT,.08f);
            }else{
                float pulse=(float)(.5+.5*Math.sin(ui.now/220.0));
                g.circle(cx+46,sy+sh/2,5+2*pulse,Theme.REQUEST);
                String what=st.count>1?"YOUR TURN  "+t.progress()+" / "+st.count:"YOUR TURN";
                g.text(what,cx+64,sy+sh/2+5,Gfx.MONO_MEDIUM,14,Theme.REQUEST,Gfx.LEFT,.08f);
                if(st.region!=null)ui.button("showme",cx+24+tw-150,sy+8,140,sh-16,"SHOW ME",Ui.SELECTED,true,()->{s.tab=State.TAB_SOUND;s.coached=true;});
            }
        }
        if(picture)picture(st.picture,cx+24+tw+28,y+92,cw-tw-76,h-92-90);
        // step dots and the buttons
        float by=y+h-70;
        ui.button("learn.back",cx+24,by,140,50,"BACK",Ui.NORMAL,t.lesson>0||t.step>0,home::back);
        boolean waiting=st.wait!=Tutorial.NEXT&&!t.complete();
        String next=waiting?"SKIP":t.last()?"FINISH":t.step==lesson.steps.length-1?"NEXT LESSON":"NEXT";
        ui.button("learn.next",cx+cw-24-180,by,180,50,next,waiting?Ui.NORMAL:Ui.SELECTED,true,home::next);
        float dw=lesson.steps.length*22,dx=cx+cw/2-dw/2+11;
        for(int i=0;i<lesson.steps.length;i++){
            if(i==t.step)g.circle(dx+i*22,by+25,6,Theme.REQUEST);
            else g.circle(dx+i*22,by+25,4,i<t.step?Theme.PHOSPHOR:Theme.TRACK);
        }
    }

    private void glossary(float x,float y,float w,float h){
        Gfx g=home.ui.g;
        g.text("WORDS TO KNOW",x+24,y+40,Gfx.DISPLAY,20,Theme.REQUEST,Gfx.LEFT,.04f);
        int n=Tutorial.GLOSSARY.length,half=(n+1)/2;
        float colW=(w-72)/2,rowH=Math.min(76,(h-80)/half);
        for(int i=0;i<n;i++){
            float gx=x+24+(i/half)*(colW+24),gy=y+70+(i%half)*rowH;
            g.text(Tutorial.GLOSSARY[i][0],gx,gy+16,Gfx.MONO_MEDIUM,14,Theme.PHOSPHOR,Gfx.LEFT,.04f);
            Text.block(g,Tutorial.GLOSSARY[i][1],gx,gy+20,colW,Gfx.MONO,13,Theme.TEXT,1.4f);
        }
    }

    static void check(Gfx g,float cx,float cy,float r,int color){
        g.polyline(new float[]{cx-r,cy,cx-r*.3f,cy+r*.7f,cx+r,cy-r*.8f},false,r*.35f,color,0);
    }

    // ---- pictures -------------------------------------------------------------------------
    void picture(int id,float x,float y,float w,float h){
        Gfx g=home.ui.g;
        g.rect(x,y,w,h,0xff0e140e);g.frame(x,y,w,h,1,Theme.TRACK);
        float pad=18;x+=pad;y+=pad;w-=2*pad;h-=2*pad;
        switch(id){
            case Tutorial.PIC_MPK:mpk(x,y,w,h,-1);break;
            case Tutorial.PIC_KEYS:mpk(x,y,w,h,Tutorial.PIC_KEYS);break;
            case Tutorial.PIC_PADS:mpk(x,y,w,h,Tutorial.PIC_PADS);break;
            case Tutorial.PIC_KNOBS:mpk(x,y,w,h,Tutorial.PIC_KNOBS);break;
            case Tutorial.PIC_JOYSTICK:mpk(x,y,w,h,Tutorial.PIC_JOYSTICK);break;
            case Tutorial.PIC_TRANSPORT:mpk(x,y,w,h,Tutorial.PIC_TRANSPORT);break;
            case Tutorial.PIC_SCREEN_PARTS:parts(x,y,w,h);break;
            case Tutorial.PIC_SCREEN_SOUND:sound(x,y,w,h);break;
            case Tutorial.PIC_FLOW:flow(x,y,w,h);break;
            case Tutorial.PIC_OSC:osc(x,y,w,h);break;
            case Tutorial.PIC_FILTER:filter(x,y,w,h);break;
            case Tutorial.PIC_ENVELOPE:envelope(x,y,w,h);break;
            case Tutorial.PIC_LFO:lfo(x,y,w,h);break;
            case Tutorial.PIC_PICKUP:pickup(x,y,w,h);break;
            case Tutorial.PIC_KIT:kit(x,y,w,h);break;
            default:
        }
    }
    private float pulse(){return (float)(.5+.5*Math.sin(home.ui.now/200.0));}
    private int hot(){int a=(int)(110+145*pulse());return a<<24|(Theme.REQUEST&0xffffff);}
    private boolean recent(long at){return home.ui.now-at<220;}

    /** The MPK mini Plus from above, with the part a step is about lit up, and what you play on it. */
    private void mpk(float x,float y,float w,float h,int focus){
        Gfx g=home.ui.g;
        float bw=Math.min(w,h*1.7f),bh=bw/1.7f,bx=x+(w-bw)/2,by=y+(h-bh)/2;
        g.rect(bx,by,bw,bh,0xff222622);g.frame(bx,by,bw,bh,2,0xff3c443b);
        float top=bh*.5f,u=bw/100;
        // pads, 2 rows of 4, in the middle
        float px=bx+24*u,py=by+5*u,ps=(top-9*u)/2,pg=1.6f*u;
        // joystick and transport, left
        float jx=bx+11*u,jy=py+ps*.55f,jr=Math.min(6*u,ps*.5f);
        g.circle(jx,jy,jr,0xff151815);
        float off=Math.max(-1,Math.min(1,home.lastBend/8192f))*jr*.5f;
        g.circle(jx+off,jy,jr*.5f,0xff606860);
        if(focus==Tutorial.PIC_JOYSTICK)g.ring(jx,jy,jr+4,3,hot());
        g.text("JOYSTICK",jx,jy-jr-6,Gfx.MONO,8.5f,Theme.MUTED,Gfx.CENTER,.06f);
        float ty=py+ps+pg+ps*.35f;
        String[] tl={"REC","PLAY","STOP"};
        for(int i=0;i<3;i++){
            float tx=bx+3.5f*u+i*5.5f*u;
            g.rect(tx,ty,4.5f*u,3*u,0xff3a3f3a);
            if(focus==Tutorial.PIC_TRANSPORT)g.frame(tx-2,ty-2,4.5f*u+4,3*u+4,2,hot());
            g.text(tl[i],tx+2.25f*u,ty+3*u+11,Gfx.MONO,8,Theme.MUTED,Gfx.CENTER,0);
        }
        int padLit=recent(home.lastPadAt)?(home.lastPadNote-MpkRouter.PAD_LOW)%8:-1;
        for(int i=0;i<8;i++){
            int col=i%4,row=1-i/4;
            float qx=px+col*(ps+pg),qy=py+row*(ps+pg);
            g.rect(qx,qy,ps,ps,i==padLit?0xffa8efb3:0xff3b4a5a);
            g.frame(qx,qy,ps,ps,1,0xff111411);
        }
        if(focus==Tutorial.PIC_PADS)g.frame(px-5,py-5,4*ps+3*pg+10,2*ps+pg+10,3,hot());
        g.text("PADS",px+(4*ps+3*pg)/2,py+2*ps+pg+14,Gfx.MONO,9,Theme.MUTED,Gfx.CENTER,.06f);
        // knobs, 2 rows of 4, level with the pads
        float kx=px+4*ps+3*pg+6*u,kg=(bx+bw-3*u-kx)/4,kr=Math.min(kg*.36f,ps*.3f);
        boolean knobLit=recent(home.lastKnobAt+200);
        for(int i=0;i<8;i++){
            float ccx=kx+kg*(i%4)+kg/2,ccy=py+(i/4)*(ps+pg)+ps*.4f;
            boolean lit=knobLit&&home.mpk.lastKnob()==i;
            g.circle(ccx,ccy,kr,lit?0xffa8efb3:0xff121512);g.ring(ccx,ccy,kr,1.5f,0xff5a625a);
            g.text("K"+(i+1),ccx,ccy+kr+11,Gfx.MONO,8.5f,Theme.MUTED,Gfx.CENTER,0);
        }
        if(focus==Tutorial.PIC_KNOBS)g.frame(kx-4,py-5,kg*4+8,2*ps+pg+10,3,hot());
        // 37 keys: 22 white
        float kyTop=by+top+2*u,kh=bh-top-5*u,kx0=bx+3*u,kw=(bw-6*u)/22;
        int keyLit=recent(home.lastKeyAt+150)?home.lastKey:-1;
        int[] whiteSemis={0,2,4,5,7,9,11};
        for(int i=0;i<22;i++){
            int note=48+(i/7)*12+whiteSemis[i%7];
            g.rect(kx0+i*kw+1,kyTop,kw-2,kh,note==keyLit?0xffa8efb3:0xffe6e4da);
        }
        for(int i=0;i<21;i++){
            int n=i%7;if(n==2||n==6)continue;
            int note=48+(i/7)*12+whiteSemis[n]+1;
            g.rect(kx0+(i+1)*kw-kw*.32f,kyTop,kw*.64f,kh*.6f,note==keyLit?0xff5fb36d:0xff1a1c1a);
        }
        if(focus==Tutorial.PIC_KEYS)g.frame(kx0-5,kyTop-5,22*kw+10,kh+10,3,hot());
        g.text(home.s.mpkConnected?"CONNECTED: TRY IT":"NOT CONNECTED",bx+bw,by-8,Gfx.MONO,10,home.s.mpkConnected?Theme.PHOSPHOR:Theme.MUTED,Gfx.RIGHT,.08f);
    }

    /** The four parts, as at the top of the SOUND screen. */
    private void parts(float x,float y,float w,float h){
        Gfx g=home.ui.g;Rig rig=home.rig;State s=home.s;
        float gap=8,ch=Math.min(h*.5f,110),cw=(w-3*gap)/4,cy=y+(h-ch)/2-10;
        for(int p=0;p<4;p++){
            float cx=x+p*(cw+gap);boolean keys=p==s.keyPart,pads=p==s.padPart;
            g.rect(cx,cy,cw,ch,keys?Theme.SELECTED_FILL:Theme.PANEL);g.frame(cx,cy,cw,ch,keys?2:1,keys?Theme.REQUEST:Theme.BORDER);
            g.text("PART "+(p+1),cx+8,cy+18,Gfx.MONO,9,Theme.MUTED,Gfx.LEFT,.08f);
            g.text(Text.fit(g,rig.model(p).name.toUpperCase(Locale.ROOT),cw-16,Gfx.DISPLAY,11),cx+8,cy+40,Gfx.DISPLAY,11,keys?Theme.REQUEST:Theme.PHOSPHOR,Gfx.LEFT,0);
            g.text(Text.fit(g,rig.presetName(p),cw-16,10),cx+8,cy+58,Gfx.MONO,10,Theme.TEXT,Gfx.LEFT,0);
            if(keys){g.rect(cx+8,cy+ch-28,52,20,Theme.REQUEST);g.text("KEYS",cx+34,cy+ch-14,Gfx.MONO_MEDIUM,9,Theme.BACKGROUND,Gfx.CENTER,.08f);}
            if(pads){g.rect(cx+cw-60,cy+ch-28,52,20,Theme.PHOSPHOR);g.text("PADS",cx+cw-34,cy+ch-14,Gfx.MONO_MEDIUM,9,Theme.BACKGROUND,Gfx.CENTER,.08f);}
        }
        g.text("KEYS = the part the MPK's keys play",x,cy+ch+30,Gfx.MONO,11,Theme.REQUEST,Gfx.LEFT,0);
        g.text("PADS = the part the MPK's pads play",x,cy+ch+50,Gfx.MONO,11,Theme.PHOSPHOR,Gfx.LEFT,0);
    }

    /** The SOUND screen in miniature, labelled. */
    private void sound(float x,float y,float w,float h){
        Gfx g=home.ui.g;
        float sy=y+10;
        box(x,sy,w*.38f,34,"MODEL","Poly 106");box(x+w*.4f,sy,w*.34f,34,"PRESET","Brass Section");
        for(int b=0;b<3;b++){float bx=x+w*.76f+b*(w*.08f);g.rect(bx,sy,w*.075f,34,b==0?Theme.SELECTED_FILL:Theme.RAISED);g.frame(bx,sy,w*.075f,34,1,b==0?Theme.REQUEST:Theme.BORDER);}
        g.text("KNOB BANKS",x+w,sy+52,Gfx.MONO,9,Theme.MUTED,Gfx.RIGHT,.06f);
        float py=sy+70,ph=h-120;
        String[] sec={"LFO","DCO","VCF","ENV"};int[] n={2,5,4,4};
        float sx=x,total=15;
        for(int i=0;i<sec.length;i++){
            float sw=(w-30)*n[i]/total;
            g.rect(sx,py,sw,ph,Theme.PANEL);g.frame(sx,py,sw,ph,1,Theme.BORDER);
            g.text(sec[i],sx+6,py+14,Gfx.MONO_MEDIUM,9,Theme.PHOSPHOR,Gfx.LEFT,.1f);
            for(int k=0;k<n[i];k++){
                float mx=sx+sw*(k+.5f)/n[i],top=py+30,bot=py+ph-14;
                g.rect(mx-1.5f,top,3,bot-top,0xff0b100b);
                float t=(float)(.5+.4*Math.sin(home.ui.now/900.0+i*1.7+k));
                g.rect(mx-7,top+(bot-top)*(1-t)-5,14,10,0xffd8d4c2);
                if(i==2&&k<4){g.frame(mx-9,py+18,18,10,1,Theme.REQUEST);}
            }
            sx+=sw+10;
        }
        Text.block(g,"Model and preset: the sound. Banks: which 8 controls the knobs play (K1-K8).",x,y+h-34,w,Gfx.MONO,10.5f,Theme.MUTED,1.4f);
    }
    private void box(float x,float y,float w,float h,String caption,String value){
        Gfx g=home.ui.g;
        g.rect(x,y,w,h,Theme.RAISED);g.frame(x,y,w,h,1,Theme.BORDER);
        g.text(caption,x+8,y+12,Gfx.MONO,8,Theme.MUTED,Gfx.LEFT,.08f);
        g.text(Text.fit(g,value,w-16,11),x+8,y+27,Gfx.MONO,11,Theme.PHOSPHOR,Gfx.LEFT,0);
    }

    /** Oscillator, filter and amplifier in a row, with the envelope and LFO moving them. */
    private void flow(float x,float y,float w,float h){
        Gfx g=home.ui.g;
        float bw=w*.27f,bh=60,gap=(w-3*bw)/3,cy=y+h*.32f;
        String[] main={"OSCILLATOR","FILTER","AMPLIFIER"};String[] sub={"makes the tone","shapes brightness","sets loudness"};
        for(int i=0;i<3;i++){
            float bx=x+i*(bw+gap);
            g.rect(bx,cy-bh/2,bw,bh,Theme.RAISED);g.frame(bx,cy-bh/2,bw,bh,2,Theme.PHOSPHOR);
            g.text(main[i],bx+bw/2,cy-2,Gfx.MONO_MEDIUM,Math.min(12,bw/9),Theme.PHOSPHOR,Gfx.CENTER,.06f);
            g.text(Text.fit(g,sub[i],bw-8,9.5f),bx+bw/2,cy+18,Gfx.MONO,9.5f,Theme.MUTED,Gfx.CENTER,0);
            float ax=bx+bw+4,ex=i<2?bx+bw+gap-4:x+w-4;
            arrow(ax,cy,ex,cy,Theme.PHOSPHOR);
        }
        // the sound travelling along
        float t=(home.ui.now%2400)/2400f;
        g.circle(x+t*(w-8),cy-bh/2-10,4,Theme.REQUEST);
        g.text("OUT",x+w,cy-12,Gfx.MONO,9,Theme.MUTED,Gfx.RIGHT,0);
        float ly=y+h*.78f;
        float ex=x+bw+gap+bw*.3f,lx=x+bw*.2f;
        g.rect(ex-10,ly-20,bw+20,40,Theme.RAISED);g.frame(ex-10,ly-20,bw+20,40,1,Theme.REQUEST);
        g.text("ENVELOPE",ex+bw/2,ly+5,Gfx.MONO_MEDIUM,11,Theme.REQUEST,Gfx.CENTER,.06f);
        arrow(ex+bw*.3f,ly-22,ex+bw*.3f,cy+bh/2+4,Theme.REQUEST);
        arrow(ex+bw+10,ly-10,x+2*(bw+gap)+bw*.3f,cy+bh/2+4,Theme.REQUEST);
        g.rect(lx-10,ly-20,bw*.7f,40,Theme.RAISED);g.frame(lx-10,ly-20,bw*.7f,40,1,Theme.WATER);
        g.text("LFO",lx-10+bw*.35f,ly+5,Gfx.MONO_MEDIUM,11,Theme.WATER,Gfx.CENTER,.06f);
        arrow(lx+bw*.2f,ly-22,lx+bw*.2f,cy+bh/2+4,Theme.WATER);
        arrow(lx+bw*.6f,ly-10,ex-14,cy+bh/2+4,Theme.WATER);
    }
    private void arrow(float x1,float y1,float x2,float y2,int color){
        Gfx g=home.ui.g;
        g.line(x1,y1,x2,y2,2,color);
        double a=Math.atan2(y2-y1,x2-x1);float s=8;
        g.polygon(new float[]{x2,y2,x2-(float)(Math.cos(a-.45)*s),y2-(float)(Math.sin(a-.45)*s),x2-(float)(Math.cos(a+.45)*s),y2-(float)(Math.sin(a+.45)*s)},color);
    }

    /** Saw, pulse and sub, one above the other. */
    private void osc(float x,float y,float w,float h){
        Gfx g=home.ui.g;
        String[] names={"SAW · bright, buzzy","PULSE · hollow","SUB · an octave down, weight"};
        float rh=h/3,lw=w;
        for(int r=0;r<3;r++){
            float ry=y+r*rh,mid=ry+rh*.55f,amp=rh*.28f;
            g.text(names[r],x,ry+16,Gfx.MONO_MEDIUM,11,Theme.PHOSPHOR,Gfx.LEFT,.04f);
            int cycles=r==2?2:4,n=cycles*24;
            float[] xy=new float[(n+1)*2];
            for(int i=0;i<=n;i++){
                float ph=(i%24)/24f,v;
                if(r==0)v=1-2*ph;
                else if(r==1)v=ph<.5f?1:-1;
                else v=ph<.5f?1:-1;
                if(r==2)ph=0;
                xy[i*2]=x+lw*i/n;xy[i*2+1]=mid-v*amp;
            }
            // square edges are vertical: place steps exactly
            if(r>0){
                java.util.ArrayList<Float> pts=new java.util.ArrayList<>();
                int steps=cycles*2;float sw=lw/steps;
                for(int k=0;k<steps;k++){float v=k%2==0?1:-1;pts.add(x+k*sw);pts.add(mid-v*amp);pts.add(x+(k+1)*sw);pts.add(mid-v*amp);}
                xy=new float[pts.size()];for(int i=0;i<xy.length;i++)xy[i]=pts.get(i);
            }
            g.polyline(xy,false,2.4f,r==2?Theme.WATER:Theme.REQUEST,0);
        }
    }

    /** A low-pass filter's response, its cutoff sweeping, with a resonance peak. */
    private void filter(float x,float y,float w,float h){
        Gfx g=home.ui.g;
        float base=y+h-24,top=y+30;
        float cut=(float)(.3+.25*Math.sin(home.ui.now/1100.0));
        int n=80;float[] xy=new float[(n+1)*2];
        for(int i=0;i<=n;i++){
            float f=i/(float)n,d=f-cut;
            float v=d<0?1:Math.max(0,1-d*4.5f);
            v+=.35f*(float)Math.exp(-d*d*900);
            xy[i*2]=x+w*f;xy[i*2+1]=base-(base-top)*.7f*v;
        }
        g.line(x,base,x+w,base,1,Theme.DIM);
        g.polyline(xy,false,2.6f,Theme.REQUEST,0);
        float cx=x+w*cut;
        g.line(cx,top-8,cx,base,1,Theme.PHOSPHOR);
        g.text("FREQ (cutoff)",cx+6,top,Gfx.MONO_MEDIUM,10.5f,Theme.PHOSPHOR,Gfx.LEFT,.04f);
        g.text("RES peak",cx+6,base-(base-top)*.95f+28,Gfx.MONO,10,Theme.MUTED,Gfx.LEFT,0);
        g.text("LOW NOTES PASS",x,base+18,Gfx.MONO,10,Theme.MUTED,Gfx.LEFT,.06f);
        g.text("HIGH NOTES CUT",x+w,base+18,Gfx.MONO,10,Theme.MUTED,Gfx.RIGHT,.06f);
    }

    /** Attack, decay, sustain and release, with a note being played through them. */
    private void envelope(float x,float y,float w,float h){
        Gfx g=home.ui.g;
        float base=y+h-34,peak=y+30,sus=y+h*.45f;
        float a=x+w*.15f,d=x+w*.32f,r0=x+w*.72f,r1=x+w;
        g.line(x,base,x+w,base,1,Theme.DIM);
        g.polyline(new float[]{x,base,a,peak,d,sus,r0,sus,r1,base},false,3,Theme.REQUEST,0);
        String[] l={"A","D","S","R"};float[] lx={(x+a)/2,(a+d)/2,(d+r0)/2,(r0+r1)/2};
        String[] full={"ATTACK","DECAY","SUSTAIN","RELEASE"};
        for(int i=0;i<4;i++){
            g.text(l[i],lx[i],base+20,Gfx.DISPLAY,14,Theme.PHOSPHOR,Gfx.CENTER,0);
            g.text(full[i],lx[i],base-8,Gfx.MONO,8.5f,Theme.MUTED,Gfx.CENTER,.06f);
        }
        g.line(x,y+8,x,base,1,Theme.PHOSPHOR);g.text("KEY DOWN",x+4,y+14,Gfx.MONO,9,Theme.PHOSPHOR,Gfx.LEFT,.06f);
        g.line(r0,y+8,r0,base,1,Theme.PHOSPHOR);g.text("KEY UP",r0+4,y+14,Gfx.MONO,9,Theme.PHOSPHOR,Gfx.LEFT,.06f);
        float t=(home.ui.now%3000)/3000f,px=x+w*t;
        g.line(px,peak-6,px,base,1.5f,0x88eae7d5);
    }

    /** A slow wave, fading in after the delay. */
    private void lfo(float x,float y,float w,float h){
        Gfx g=home.ui.g;
        float mid=y+h*.5f,amp=h*.3f,phase=home.ui.now/600f;
        int n=120;float[] xy=new float[(n+1)*2];
        for(int i=0;i<=n;i++){
            float f=i/(float)n,fade=Math.min(1,Math.max(0,(f-.2f)/.3f));
            xy[i*2]=x+w*f;xy[i*2+1]=mid-(float)Math.sin(f*18-phase)*amp*fade;
        }
        g.line(x,mid,x+w,mid,1,Theme.DIM);
        g.polyline(xy,false,2.6f,Theme.WATER,0);
        g.text("DELAY",x+w*.1f,y+h-6,Gfx.MONO_MEDIUM,10.5f,Theme.PHOSPHOR,Gfx.CENTER,.06f);
        g.text("RATE = how fast it wobbles",x+w*.6f,y+h-6,Gfx.MONO_MEDIUM,10.5f,Theme.PHOSPHOR,Gfx.CENTER,.04f);
        g.text("note starts",x,y+14,Gfx.MONO,9,Theme.MUTED,Gfx.LEFT,0);
    }

    /** The sound's value and where the MPK knob really is; turning past the value picks it up. */
    private void pickup(float x,float y,float w,float h){
        Gfx g=home.ui.g;
        float t=(home.ui.now%4000)/4000f;
        float value=.65f,pos=t<.6f?.2f+t/.6f*.45f:value+(float)Math.sin((t-.6f)*12)*.12f;
        boolean picked=t>=.6f;
        float cx=x+w/2,cy=y+h*.42f,r=Math.min(w,h)*.22f;
        for(int i=0;i<=10;i++){float a=(float)Math.toRadians(210+30*i);g.line(cx+(float)Math.sin(a)*(r+6),cy-(float)Math.cos(a)*(r+6),cx+(float)Math.sin(a)*(r+12),cy-(float)Math.cos(a)*(r+12),1,Theme.DIM);}
        g.circle(cx,cy,r,0xff101610);g.ring(cx,cy,r,2,Theme.KNOB_RIM);
        float sv=picked?pos:value;
        float a=(float)Math.toRadians(210+300*sv);
        g.line(cx+(float)Math.sin(a)*r*.5f,cy-(float)Math.cos(a)*r*.5f,cx+(float)Math.sin(a)*r*.95f,cy-(float)Math.cos(a)*r*.95f,4,Theme.REQUEST);
        float b=(float)Math.toRadians(210+300*pos);
        g.circle(cx+(float)Math.sin(b)*(r+20),cy-(float)Math.cos(b)*(r+20),6,Theme.PHOSPHOR);
        g.text("SOUND'S VALUE",x,y+h-36,Gfx.MONO_MEDIUM,10.5f,Theme.REQUEST,Gfx.LEFT,.06f);
        g.text("WHERE THE MPK KNOB IS",x,y+h-16,Gfx.MONO_MEDIUM,10.5f,Theme.PHOSPHOR,Gfx.LEFT,.06f);
        g.text(picked?"PICKED UP: THE KNOB IS IN CONTROL":"TURN UP",x+w,y+h-16,Gfx.MONO_MEDIUM,11,picked?Theme.PHOSPHOR:Theme.MUTED,Gfx.RIGHT,.06f);
    }

    /** The Kit's 16 pads by name. */
    private void kit(float x,float y,float w,float h){
        Gfx g=home.ui.g;
        float side=Math.min(w,h),gap=8,ps=(side-3*gap)/4,gx=x+(w-side)/2,gy=y+(h-side)/2;
        int lit=recent(home.lastPadAt)?home.lastPadNote-MpkRouter.PAD_LOW:-1;
        for(int i=0;i<16;i++){
            int col=i%4,row=3-i/4;float px=gx+col*(ps+gap),py=gy+row*(ps+gap);
            g.rect(px,py,ps,ps,i==lit?0xff3c5a3b:Theme.RAISED);g.frame(px,py,ps,ps,1,i<8?Theme.BORDER:Theme.WATER);
            g.text(Integer.toString(i+1),px+6,py+14,Gfx.MONO,9,Theme.MUTED,Gfx.LEFT,0);
            g.text(Text.fit(g,home.rig.catalog.padName(i),ps-8,10),px+ps/2,py+ps-10,Gfx.MONO_MEDIUM,10,Theme.TEXT,Gfx.CENTER,0);
        }
        g.text("BANK A: 1-8   BANK B: 9-16",gx+side/2,gy-6,Gfx.MONO,9.5f,Theme.MUTED,Gfx.CENTER,.06f);
    }
}
