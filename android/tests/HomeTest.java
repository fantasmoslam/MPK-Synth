/* SPDX-License-Identifier: GPL-3.0-only */
package org.fantasmoslam.mpksynth.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * The screen, drawn headless: every model's panel and every lesson step draws inside the
 * screen; touches reach parts, banks and controls; MPK knobs drive the bank on screen; and a
 * lesson step about the SOUND screen takes you there and back.
 */
public final class HomeTest {
    static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}

    /** Records text, and checks everything drawn stays on the screen. */
    static final class RecordingGfx implements Gfx {
        final List<String> texts=new ArrayList<>();float w,h;final List<String> outside=new ArrayList<>();
        void in(float x,float y,String what){if(x<-1||y<-1||x>w+1||y>h+1)outside.add(what+" at "+x+","+y);}
        public void rect(float x,float y,float ww,float hh,int c){in(x,y,"rect");in(x+ww,y+hh,"rect end");}
        public void frame(float x,float y,float ww,float hh,float width,int c){}
        public void line(float x1,float y1,float x2,float y2,float width,int c){}
        public void circle(float cx,float cy,float r,int c){}
        public void ring(float cx,float cy,float r,float width,int c){}
        public void arc(float cx,float cy,float r,float s,float sw,float width,int c){}
        public void polygon(float[] xy,int c){}
        public void polyline(float[] xy,boolean closed,float width,int c,float dash){}
        public void text(String s,float x,float y,int f,float size,int c,int align,float tr){
            texts.add(s);
            float tw=textWidth(s,f,size,tr),left=align==CENTER?x-tw/2:align==RIGHT?x-tw:x;
            if(f!=DISPLAY){in(left,y,"text '"+s+"'");in(left+tw,y,"text end '"+s+"'");}
        }
        public float textWidth(String s,int f,float size,float tr){return s.length()*size*(f==DISPLAY?.9f:.6f)*(1+tr);}
        public void clip(float x,float y,float ww,float hh){}
        public void unclip(){}
    }
    static final class FakeHost implements Host {
        int panics;final List<String> notes=new ArrayList<>();
        public int master(){return 700;}
        public void master(int v){}
        public boolean audioOn(){return true;}
        public void audition(int part,int note,boolean on){notes.add(part+" "+note+" "+on);}
        public void panic(){panics++;}
    }

    static Ui ui;static Home home;static RecordingGfx g;static long now=10000;
    static final float W=1280,H=800;
    static void frame(){g=new RecordingGfx();g.w=W;g.h=H;ui.begin(g,W,H,now+=33);home.draw();ui.end();check(g.outside.isEmpty(),"drawn off screen: "+g.outside);}
    static void tap(float x,float y){ui.down(1,x,y);ui.up(1,x,y);frame();}
    static float[] region(String name){float[] r=home.regions.get(name);check(r!=null,"region "+name);return r;}

    public static void main(String[] args)throws Exception{
        TableSource t=new TableSource(args[0]);Catalog c=new Catalog(t);
        Rig rig=new Rig(c,t.sink());State s=new State();FakeHost host=new FakeHost();
        ui=new Ui();home=new Home(ui,s,rig,host);
        frame();frame();
        check(g.texts.contains("MPK SYNTH")&&g.texts.contains("ALL NOTES OFF"),"header");
        check(g.texts.contains("FREQ")&&g.texts.contains("CHORUS"),"the Poly 106 panel");
        // Every model draws on every part.
        for(Catalog.Model m:c.models){rig.setModel(0,m.id);frame();frame();check(g.texts.contains(m.name),"model "+m.name+" shown");}
        rig.setModel(0,c.find("Poly 106"));rig.preset(0,1);

        // Tap part 2 (its card, left of the badges): the keys now play it.
        float[] parts=region("parts");float cw=(parts[2]-36)/4;
        tap(parts[0]+cw+12+40,parts[1]+50);
        check(s.keyPart==1,"tapping a part gives it the keys");
        tap(parts[0]+40,parts[1]+50);check(s.keyPart==0,"and back");

        // Banks: the second bank moves the knobs to it.
        float[] banks=region("banks");
        tap(banks[0]+96+8+20,banks[1]+20);check(rig.bank(0)==1,"bank tab");
        Catalog.Bank bank=rig.model(0).banks[1];
        Mpk.Target k1=home.target(0,0);
        check(k1!=null&&k1.key().endsWith(":"+bank.ids[0]),"knob 1 drives the bank's first control: "+(k1==null?null:k1.key()));
        // An MPK knob picks up and then changes the control on the keys part.
        home.connected(true,now);
        int id=bank.ids[0];Catalog.Control ctl=rig.control(0,id);
        int raw=Math.round((rig.value(0,id)-ctl.min)*127f/(ctl.max-ctl.min));
        home.knob(0,raw,now);home.knob(0,127,now+=10);
        check(rig.value(0,id)==ctl.max,"the knob took over: "+rig.value(0,id));
        frame();check(g.texts.contains("K1"),"knob tags drawn");

        // ALL NOTES OFF only ends notes.
        frame();
        // The Kit: tap a pad on screen and it plays and becomes the pad the knobs shape.
        s.keyPart=3;frame();frame();
        float[] panel=region("panel");
        check(g.texts.contains("KICK")&&g.texts.contains("RIDE"),"the Kit's pads");
        float side=Math.min(panel[3],panel[2]*.42f),pad=(side-34-30)/4;
        float gx=panel[0]+(side-(pad*4+30))/2,gy=panel[1]+30+(panel[3]-30-(pad*4+30))/2;
        // pad 6 (second row from the bottom, second column)
        ui.down(2,gx+pad+10+pad/2,gy+2*(pad+10)+pad/2);
        check(rig.lastPad==5&&host.notes.contains("3 41 true"),"a tapped pad plays and is chosen: "+rig.lastPad+" "+host.notes);
        ui.up(2,0,0);check(host.notes.contains("3 41 false"),"and ends");
        frame();check(g.texts.contains("PAD 6 · TOM 1"),"its controls are shown: "+g.texts);
        // An MPK pad on the Kit's part chooses that pad.
        home.played(true,38,now);check(rig.lastPad==2,"an MPK pad chooses the drum");

        // The lessons: every step draws.
        s.tab=State.TAB_LEARN;s.keyPart=0;
        for(int l=0;l<Tutorial.LESSONS.length;l++){
            home.openLesson(l);
            for(int st=0;st<Tutorial.LESSONS[l].steps.length;st++){
                frame();
                check(g.texts.contains(Tutorial.LESSONS[l].steps[st].title),"step drawn: "+Tutorial.LESSONS[l].steps[st].title);
                if(st<Tutorial.LESSONS[l].steps.length-1){home.next();}
            }
        }
        s.glossary=true;frame();check(g.texts.contains("WORDS TO KNOW"),"glossary");s.glossary=false;

        // A step about the SOUND screen takes you there, waits, then brings you back.
        s.tab=State.TAB_LEARN;home.openLesson(2);home.next();
        check(s.tab==State.TAB_SOUND&&s.coached,"to the SOUND screen with the step");
        frame();check(g.texts.contains("Choose the keys part"),"the step is shown on the SOUND screen");
        parts=region("parts");
        tap(parts[0]+cw+12+40,parts[1]+50);
        check(home.tutorial.complete(),"tapping a part does the step");
        now+=Tutorial.ADVANCE_MS+10;frame();
        check(home.tutorial.step==2&&s.tab==State.TAB_LEARN&&!s.coached,"then back to the lesson");
        // The MPK connected before the lesson that waits for it: it is already done.
        home.openLesson(0);home.next();
        check(home.tutorial.complete(),"connected already counts");
        System.out.println("PASS: screen: every model and lesson step draws on screen; parts, banks, knobs, Kit pads and lesson coaching work by touch and MPK");
    }
}
