/* SPDX-License-Identifier: GPL-3.0-only */
package org.fantasmoslam.mpksynth.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

/**
 * MPK Synth's screen. A header (tabs, the MPK's status, ALL NOTES OFF, volume) and the four
 * parts sit on top; below them the SOUND tab shows the keys part's synth with every control,
 * and the LEARN tab the lessons. Layout is in units of 1/800 of the screen height.
 *
 * Nothing here turns the audio off: the only button that silences anything is ALL NOTES OFF,
 * and it only ends notes.
 */
public final class Home {
    public final Ui ui;public final State s;public final Rig rig;public final Host host;
    public final Mpk mpk=new Mpk();
    public final Tutorial tutorial=new Tutorial();
    final Learn learn;
    static final float M=24,HEADER=64,PARTS_Y=72,PARTS_H=100,SOUND_Y=184,COACH_H=104;
    /** Where the lessons point on the SOUND screen, from the last frame drawn. */
    final HashMap<String,float[]> regions=new HashMap<>();

    public Home(Ui ui,State s,Rig rig,Host host){this.ui=ui;this.s=s;this.rig=rig;this.host=host;learn=new Learn(this);}

    // ---- the MPK, on the main thread ----------------------------------------------------
    /** A knob on the MPK moved. */
    public void knob(int k,int value,long now){
        mpk.move(k,value,target(s.keyPart,k),now);lastKnobAt=now;
        event(Tutorial.KNOB,k,now);
    }
    /** Play, Stop or Record on the MPK was pressed. The looper arrives in the next update. */
    public void transport(MpkRouter.Transport button,long now){
        s.notice("The looper comes in the next update: "+button.name()+" does nothing yet",now);
    }
    /** What the MPK did last, so the lesson pictures can light it up. */
    int lastKey=-1,lastPadNote=-1,lastBend;long lastKeyAt,lastPadAt,lastKnobAt;
    /** A key or pad was struck on the MPK. */
    public void played(boolean pad,int note,long now){
        if(pad){lastPadNote=note;lastPadAt=now;}else{lastKey=note;lastKeyAt=now;}
        if(pad&&note>=MpkRouter.PAD_LOW&&note<=MpkRouter.PAD_HIGH){
            int part=s.padPart;
            if(rig.model(part).isKit()){
                int p=note-MpkRouter.PAD_LOW;
                if(p!=rig.lastPad){rig.lastPad=p;if(rig.model(s.keyPart).isKit())mpk.releaseAll();}
                s.flashPad[part]=p;s.flashAt[part]=now;
            }
        }
        event(pad?Tutorial.PAD:Tutorial.KEY,note,now);
    }
    public void joystick(int bend,long now){lastBend=bend;event(Tutorial.JOYSTICK,bend,now);}
    public void connected(boolean on,long now){
        s.mpkConnected=on;mpk.connected(on);
        if(on)event(Tutorial.CONNECTED,0,now);
    }

    /** What knob k drives on a part: the control in its slot of the part's bank. */
    Mpk.Target target(int part,int k){
        Catalog.Model m=rig.model(part);if(m.banks.length==0)return null;
        int slot=m.banks[rig.bank(part)].ids[k];if(slot<0)return null;
        int id=rig.resolve(part,slot);
        Catalog.Control c=rig.control(part,id);if(c==null)return null;
        return new ControlTarget(part,m.id,c);
    }
    final class ControlTarget implements Mpk.Target{
        final int part,model;final Catalog.Control c;
        ControlTarget(int part,int model,Catalog.Control c){this.part=part;this.model=model;this.c=c;}
        public String key(){return part+":"+model+":"+c.id;}
        public int min(){return c.min;}
        public int max(){return c.max;}
        public int value(){return rig.value(part,c.id);}
        public void set(int v){rig.set(part,c.id,v);}
    }
    /** The knob (0-7) that drives control id on a part, or -1. */
    int knobFor(int part,int id){
        Catalog.Model m=rig.model(part);if(m.banks.length==0)return -1;
        int[] ids=m.banks[rig.bank(part)].ids;
        for(int k=0;k<8;k++)if(ids[k]>=0&&rig.resolve(part,ids[k])==id)return k;
        return -1;
    }

    // ---- the lessons ----------------------------------------------------------------------
    void event(int kind,int value,long now){tutorial.event(kind,value,now);ui.invalidate();}
    /** The lesson step changed: a step that points at the SOUND screen takes you there and back. */
    void stepChanged(){
        Tutorial.Step st=tutorial.currentStep();
        if(st.region!=null&&s.tab==State.TAB_LEARN){s.tab=State.TAB_SOUND;s.coached=true;}
        else if(st.region==null&&s.coached){s.tab=State.TAB_LEARN;s.coached=false;}
        if(st.wait==Tutorial.CONNECTED&&s.mpkConnected)tutorial.already(Tutorial.CONNECTED,ui.now);
    }
    public void openLesson(int l){tutorial.open(l);s.glossary=false;stepChanged();}
    void next(){tutorial.next();stepChanged();}
    void back(){tutorial.back();stepChanged();}

    // ---- drawing ---------------------------------------------------------------------------
    public void draw(){
        Gfx g=ui.g;float w=ui.width,h=ui.height;
        if(tutorial.tick(ui.now))stepChanged();
        regions.clear();
        g.rect(0,0,w,h,Theme.BACKGROUND);
        header(w);
        if(s.tab==State.TAB_LEARN){learn.draw(M,PARTS_Y,w-2*M,h-PARTS_Y-M);}
        else{
            parts(M,PARTS_Y,w-2*M,PARTS_H);
            float bottom=h-M-(s.coached?COACH_H+12:0);
            sound(M,SOUND_Y,w-2*M,bottom-SOUND_Y);
            if(s.coached)coach(M,h-M-COACH_H,w-2*M,COACH_H);
            if(s.picking!=State.PICK_NONE)picker(w,h);
            else if(s.coached&&!tutorial.complete())pointAt(tutorial.currentStep().region);
        }
    }

    // ---- header -------------------------------------------------------------------------
    private void header(float w){
        Gfx g=ui.g;
        g.text("MPK SYNTH",M,42,Gfx.DISPLAY,20,Theme.TEXT,Gfx.LEFT,.08f);
        float x=M+g.textWidth("MPK SYNTH",Gfx.DISPLAY,20,.08f)+22;
        ui.button("tab.sound",x,14,104,40,"SOUND",s.tab==State.TAB_SOUND?Ui.SELECTED:Ui.NORMAL,true,()->{s.tab=State.TAB_SOUND;s.coached=false;});
        ui.button("tab.learn",x+110,14,104,40,"LEARN",s.tab==State.TAB_LEARN?Ui.SELECTED:Ui.NORMAL,true,()->{s.tab=State.TAB_LEARN;s.coached=false;s.picking=State.PICK_NONE;});
        float cx=x+236;
        boolean on=s.mpkConnected;
        String chip=on?"MPK MINI CONNECTED":"PLUG IN THE MPK MINI";
        float cw=g.textWidth(chip,Gfx.MONO,11,.06f)+34;
        g.rect(cx,19,cw,30,on?0xff1c2f1d:Theme.RAISED);g.frame(cx,19,cw,30,1,on?Theme.PHOSPHOR:Theme.BORDER);
        g.circle(cx+14,34,4,on?Theme.PHOSPHOR:Theme.DIM);
        g.text(chip,cx+26,38,Gfx.MONO,11,on?Theme.PHOSPHOR:Theme.MUTED,Gfx.LEFT,.06f);
        float after=cx+cw+14;
        if(!host.audioOn()){g.text("AUDIO OFF",after,38,Gfx.MONO_MEDIUM,11,Theme.REQUEST,Gfx.LEFT,.06f);after+=86;}
        float fw=170,fx=w-M-fw;
        g.text("VOLUME",fx-14,38,Gfx.MONO,11,Theme.MUTED,Gfx.RIGHT,.08f);
        ui.fader("master",fx,34,fw,host.master(),0,1023,host::master);
        float px=fx-80-150;
        ui.button("panic",px,14,150,40,"ALL NOTES OFF",Ui.NORMAL,true,()->{host.panic();s.notice("All notes off",ui.now);});
        if(!s.notice.isEmpty()&&ui.now<s.noticeUntil){
            float left=after,right=px-14;
            if(right-left>80)g.text(Text.fit(g,s.notice,right-left,12),(left+right)/2,39,Gfx.MONO,12,Theme.REQUEST,Gfx.CENTER,0);
        }
    }

    // ---- parts --------------------------------------------------------------------------
    private void parts(float x,float y,float w,float h){
        Gfx g=ui.g;float gap=12,cw=(w-gap*3)/4;
        regions.put("parts",new float[]{x,y,w,h});
        for(int p=0;p<Rig.PARTS;p++){
            final int part=p;
            float px=x+p*(cw+gap);
            boolean keys=p==s.keyPart,pads=p==s.padPart;
            boolean pressed=ui.held("part."+p);
            g.rect(px,y,cw,h,keys?Theme.SELECTED_FILL:pressed?Theme.TRACK:Theme.PANEL);
            g.frame(px,y,cw,h,keys?2:1,keys?Theme.REQUEST:Theme.BORDER);
            float tw=cw-28-84;
            g.text("PART "+(p+1),px+14,y+22,Gfx.MONO,11,Theme.MUTED,Gfx.LEFT,.1f);
            g.text(Text.fit(g,rig.model(p).name.toUpperCase(Locale.ROOT),tw,Gfx.DISPLAY,15),px+14,y+52,Gfx.DISPLAY,15,keys?Theme.REQUEST:Theme.PHOSPHOR,Gfx.LEFT,.04f);
            g.text(Text.fit(g,rig.presetName(p)+(rig.edited(p)?" *":""),tw,13),px+14,y+78,Gfx.MONO,13,Theme.TEXT,Gfx.LEFT,0);
            ui.hit("part."+p,px,y,cw-90,h,()->keys(part));
            float bx=px+cw-12-76;
            if(keys)badge(bx,y+14,"KEYS",Theme.REQUEST);
            else ui.button("keys."+p,bx,y+14,76,30,"KEYS",Ui.QUIET,true,()->keys(part));
            if(pads)badge(bx,y+h-44,"PADS",Theme.PHOSPHOR);
            else ui.button("pads."+p,bx,y+h-44,76,30,"PADS",Ui.QUIET,true,()->{s.padPart=part;event(Tutorial.PART_PADS,part,ui.now);});
        }
    }
    private void keys(int part){
        if(s.keyPart!=part){s.keyPart=part;mpk.releaseAll();s.picking=State.PICK_NONE;}
        event(Tutorial.PART_KEYS,part,ui.now);
    }
    private void badge(float x,float y,String label,int color){
        Gfx g=ui.g;
        g.rect(x,y,76,30,color);g.text(label,x+38,y+19.5f,Gfx.MONO_MEDIUM,11,Theme.BACKGROUND,Gfx.CENTER,.1f);
    }

    // ---- the keys part's sound ------------------------------------------------------------
    private void sound(float x,float y,float w,float h){
        Gfx g=ui.g;
        final int part=s.keyPart;Catalog.Model m=rig.model(part);
        float sw=290;
        selector("model",x,y,sw-40,"PART "+(part+1)+" MODEL",m.name,()->stepModel(-1),()->stepModel(1),()->open(State.PICK_MODEL));
        selector("preset",x+sw-28,y,sw+40,"PRESET",rig.presetName(part)+(rig.edited(part)?" *":""),()->stepPreset(-1),()->stepPreset(1),()->open(State.PICK_PRESET));
        regions.put("preset",new float[]{x+sw-28,y,sw+40,48});
        // the knob banks, right-aligned
        float bw=96,bx=x+w-m.banks.length*(bw+8)+8;
        float ix=x+2*sw+28,iw=bx-110-ix;
        if(!m.inspired.isEmpty()&&iw>120){
            g.text("MODELLED ON",ix,y+20,Gfx.MONO,9.5f,Theme.MUTED,Gfx.LEFT,.1f);
            g.text(Text.fit(g,m.inspired,iw,13),ix,y+40,Gfx.MONO,13,Theme.TEXT,Gfx.LEFT,0);
        }
        g.text("MPK KNOBS",bx-14,y+30,Gfx.MONO,10.5f,Theme.MUTED,Gfx.RIGHT,.1f);
        for(int b=0;b<m.banks.length;b++){
            final int bank=b;
            ui.button("bank."+b,bx+b*(bw+8),y,bw,48,m.banks[b].name,b==rig.bank(part)?Ui.SELECTED:Ui.NORMAL,true,()->{
                if(rig.bank(part)!=bank){rig.bank(part,bank);mpk.releaseAll();}
                event(Tutorial.BANK,bank,ui.now);
            });
        }
        if(m.banks.length>0)regions.put("banks",new float[]{bx,y,m.banks.length*(bw+8)-8,48});
        float py=y+60,ph=h-60;
        regions.put("panel",new float[]{x,py,w,ph});
        if(m.isKit())kit(part,m,x,py,w,ph);else panel(part,m,x,py,w,ph);
    }

    /** Every control of a model, in its panel sections, wrapped into as many rows as fit. */
    private void panel(int part,Catalog.Model m,float x,float y,float w,float h){
        String[] sections=m.sections();
        ArrayList<int[]> ids=new ArrayList<>();int total=0;
        for(String sec:sections){
            ArrayList<Integer> l=new ArrayList<>();
            for(Catalog.Control c:m.controls)if(c.section.equals(sec))l.add(c.id);
            int[] a=new int[l.size()];for(int i=0;i<a.length;i++)a[i]=l.get(i);
            ids.add(a);total+=a.length;
        }
        float gap=10,minSlot=74;
        int rows=Math.max(1,(int)Math.ceil(total*minSlot/w));
        rows=Math.min(rows,3);
        // balanced rows: break after the section that passes each row's share
        ArrayList<ArrayList<Integer>> rowSections=new ArrayList<>();
        int done=0,row=0;rowSections.add(new ArrayList<>());
        for(int i=0;i<sections.length;i++){
            int n=ids.get(i).length;
            float share=(float)total*(row+1)/rows;
            if(!rowSections.get(row).isEmpty()&&done+n/2f>share&&row<rows-1){row++;rowSections.add(new ArrayList<>());}
            rowSections.get(row).add(i);done+=n;
        }
        float rh=(h-gap*(rowSections.size()-1))/rowSections.size();
        for(int r=0;r<rowSections.size();r++){
            ArrayList<Integer> secs=rowSections.get(r);
            int n=0;for(int i:secs)n+=ids.get(i).length;
            float sx=x,avail=w-gap*(secs.size()-1),ry=y+r*(rh+gap);
            for(int i:secs){
                float sw=avail*ids.get(i).length/n;
                section(part,sections[i],ids.get(i),sx,ry,sw,rh);
                sx+=sw+gap;
            }
        }
    }

    /** One section of a panel: its name, then its controls side by side. */
    private void section(int part,String name,int[] ids,float x,float y,float w,float h){
        Gfx g=ui.g;ui.panel(x,y,w,h);
        g.rect(x,y,w,26,Theme.RAISED);g.line(x,y+26,x+w,y+26,1,Theme.BORDER);
        g.text(name,x+10,y+18,Gfx.MONO_MEDIUM,11,Theme.PHOSPHOR,Gfx.LEFT,.14f);
        float slot=w/ids.length;
        for(int i=0;i<ids.length;i++)control(part,ids[i],x+i*slot,y+26,slot,h-26);
    }

    /** One control in its slot: its knob tag, label, the control itself, its value and pickup hint. */
    private void control(final int part,final int id,float x,float y,float w,float h){
        Gfx g=ui.g;Catalog.Control c=rig.control(part,id);if(c==null)return;
        int k=knobFor(part,id);
        Mpk.Target t=k>=0?target(part,k):null;
        boolean turned=k>=0&&k==mpk.lastKnob()&&mpk.showing(ui.now);
        if(turned){g.rect(x+3,y+3,w-6,h-6,Theme.SELECTED_FILL);g.frame(x+3,y+3,w-6,h-6,1,Theme.REQUEST);}
        float mid=x+w/2;
        // the MPK knob this control is on
        if(k>=0){
            boolean live=mpk.connected()&&mpk.controls(k,t);
            float tw=30;
            if(live){g.rect(mid-tw/2,y+8,tw,18,Theme.PHOSPHOR);g.text("K"+(k+1),mid,y+21,Gfx.MONO_MEDIUM,10.5f,Theme.BACKGROUND,Gfx.CENTER,.04f);}
            else{g.frame(mid-tw/2,y+8,tw,18,1,Theme.REQUEST);g.text("K"+(k+1),mid,y+21,Gfx.MONO_MEDIUM,10.5f,Theme.REQUEST,Gfx.CENTER,.04f);}
        }
        g.text(Text.fit(g,c.label,w-6,11.5f),mid,y+44,Gfx.MONO,11.5f,Theme.TEXT,Gfx.CENTER,.04f);
        int v=rig.value(part,id);
        String hint=k>=0&&mpk.connected()?mpk.hint(k,t):"";
        float top=y+56,bottom=y+h-(hint.isEmpty()?34:58);
        String key="c."+part+"."+rig.model(part).id+"."+id;
        if(c.isSwitch()||c.ui==Catalog.UI_SWITCH){
            int n=c.max-c.min+1;
            float bh=Math.min(40,(bottom-top+20-6*(n-1))/n),bw=Math.min(w-10,84);
            for(int i=0;i<n;i++){
                final int value=c.max-i;   // highest at the top, like a panel switch
                String label=value-c.min<c.names.length?c.names[value-c.min]:Integer.toString(value);
                ui.button(key+"."+value,mid-bw/2,top+i*(bh+6),bw,bh,Text.fit(g,label,bw-8,12),value==v?Ui.SELECTED:Ui.NORMAL,true,()->screen(part,id,value));
            }
        }else if(c.ui==Catalog.UI_KNOB){
            float d=Math.min(Math.min(w-30,74),bottom-top-30);
            ui.knob(key,mid,(top+bottom)/2-4,d,v,c.min,c.max,c.fmt==Catalog.FMT_BIPOLAR,"",n->screen(part,id,n));
            g.text(c.text(v),mid,bottom+18,Gfx.MONO,13,Theme.REQUEST,Gfx.CENTER,0);
        }else{
            float sw=Math.min(w-8,46);
            ui.slider(key,mid-sw/2,top,sw,bottom-top,v,c.min,c.max,n->screen(part,id,n));
            g.text(c.text(v),mid,bottom+20,Gfx.MONO,13,Theme.REQUEST,Gfx.CENTER,0);
        }
        if(!hint.isEmpty()){
            g.text(Text.fit(g,hint,w-4,9.5f),mid,y+h-22,Gfx.MONO,9.5f,Theme.MUTED,Gfx.CENTER,.04f);
            float bx=x+8,bw=w-16,by=y+h-14;
            float tt=(v-c.min)/(float)Math.max(1,c.max-c.min);
            g.rect(bx,by,bw,3,Theme.TRACK);g.rect(bx,by,bw*Math.max(0,Math.min(1,tt)),3,Theme.PHOSPHOR);
            if(mpk.position(k)>=0)g.rect(bx+bw*mpk.position(k)/127f-1,by-3,2,9,Theme.REQUEST);
        }
    }
    private void screen(int part,int id,int value){rig.set(part,id,value);event(Tutorial.SCREEN_KNOB,id,ui.now);}

    /** The Kit: its 16 pads to tap, the controls of the pad hit last, and the kit's own. */
    private void kit(final int part,Catalog.Model m,float x,float y,float w,float h){
        Gfx g=ui.g;
        float side=Math.min(h,w*.42f),gap=10,pad=(side-34-gap*3)/4;
        ui.panel(x,y,side,h);
        g.text("PADS · TAP TO PLAY AND SHAPE",x+12,y+20,Gfx.MONO_MEDIUM,11,Theme.PHOSPHOR,Gfx.LEFT,.1f);
        float gx=x+(side-(pad*4+gap*3))/2,gy=y+30+(h-30-(pad*4+gap*3))/2;
        for(int i=0;i<16;i++){
            final int p=i;
            int col=i%4,row=3-i/4;   // pad 1 at the bottom left, as on the MPK
            float px=gx+col*(pad+gap),py=gy+row*(pad+gap);
            boolean sel=i==rig.lastPad,flash=s.flashPad[part]==i&&ui.now-s.flashAt[part]<160;
            String id="pad."+part+"."+i;
            boolean held=ui.held(id)||flash;
            g.rect(px,py,pad,pad,held?0xff3c5a3b:sel?Theme.SELECTED_FILL:Theme.RAISED);
            g.frame(px,py,pad,pad,sel?2:1,sel?Theme.REQUEST:Theme.BORDER);
            g.text(Integer.toString(i+1),px+8,py+18,Gfx.MONO,11,Theme.MUTED,Gfx.LEFT,0);
            g.text(Text.fit(g,rig.catalog.padName(i),pad-10,11),px+pad/2,py+pad-12,Gfx.MONO_MEDIUM,11,sel?Theme.REQUEST:Theme.TEXT,Gfx.CENTER,.04f);
            ui.hitDrag(id,px,py,pad,pad,new Ui.Drag(){
                public void start(float a,float b){
                    if(rig.lastPad!=p){rig.lastPad=p;mpk.releaseAll();}
                    s.flashPad[part]=p;s.flashAt[part]=ui.now;
                    host.audition(part,MpkRouter.PAD_LOW+p,true);event(Tutorial.PAD,MpkRouter.PAD_LOW+p,ui.now);
                }
                public void move(float a,float b){}
                public void end(){host.audition(part,MpkRouter.PAD_LOW+p,false);}
            });
        }
        float rx=x+side+12,rw=w-side-12,rh=(h-10)/2;
        int base=4+rig.lastPad*4;
        section(part,"PAD "+(rig.lastPad+1)+" · "+rig.catalog.padName(rig.lastPad),new int[]{base,base+1,base+2,base+3},rx,y,rw,rh);
        ArrayList<Integer> kitIds=new ArrayList<>();
        for(Catalog.Control c:m.controls)if(c.section.equals("KIT"))kitIds.add(c.id);
        int[] a=new int[kitIds.size()];for(int i=0;i<a.length;i++)a[i]=kitIds.get(i);
        section(part,"WHOLE KIT",a,rx,y+rh+10,rw,rh);
    }

    private void selector(String id,float x,float y,float w,String caption,String value,Runnable prev,Runnable next,Runnable open){
        Gfx g=ui.g;float h=48,bw=44;
        ui.button(id+".prev",x,y,bw,h,"‹",Ui.NORMAL,true,prev);
        ui.button(id+".next",x+w-bw,y,bw,h,"›",Ui.NORMAL,true,next);
        float tx=x+bw+6,tw=w-2*bw-12;boolean pressed=ui.held(id);
        g.rect(tx,y,tw,h,pressed?Theme.TRACK:Theme.RAISED);g.frame(tx,y,tw,h,1,Theme.BORDER);
        g.text(caption,tx+10,y+17,Gfx.MONO,9.5f,Theme.MUTED,Gfx.LEFT,.1f);
        g.text(Text.fit(g,value==null?"":value,tw-20,14),tx+10,y+38,Gfx.MONO,14,Theme.PHOSPHOR,Gfx.LEFT,.02f);
        ui.hit(id,tx,y,tw,h,open);
    }
    private void open(int what){s.picking=what;s.pickPage=-1;}
    private void stepModel(int by){
        int n=rig.catalog.models.length;
        chooseModel((rig.model(s.keyPart).id+by+n)%n);
    }
    private void stepPreset(int by){
        int n=rig.model(s.keyPart).presets.length;if(n==0)return;
        choosePreset((rig.preset(s.keyPart)+by+n)%n);
    }
    private void chooseModel(int m){rig.setModel(s.keyPart,m);mpk.releaseAll();event(Tutorial.MODEL,m,ui.now);}
    private void choosePreset(int p){rig.preset(s.keyPart,p);mpk.releaseAll();event(Tutorial.PRESET,p,ui.now);}

    // ---- the model and preset list ----------------------------------------------------------
    private void picker(float w,float h){
        Gfx g=ui.g;
        g.rect(0,0,w,h,Theme.SCRIM);ui.block(0,0,w,h);
        final boolean models=s.picking==State.PICK_MODEL;
        Catalog.Model m=rig.model(s.keyPart);
        String[] list;
        if(models){list=new String[rig.catalog.models.length];for(int i=0;i<list.length;i++){Catalog.Model mm=rig.catalog.models[i];list[i]=mm.name+(mm.inspired.isEmpty()?"":" · "+mm.inspired);}}
        else list=m.presets;
        float px=M*3,py=60,pw=w-M*6,ph=h-120;
        ui.panel(px,py,pw,ph);
        g.text((models?"MODEL":"PRESET")+" FOR PART "+(s.keyPart+1)+(models?"":" · "+m.name.toUpperCase(Locale.ROOT)),
            px+20,py+38,Gfx.MONO_MEDIUM,14,Theme.TEXT,Gfx.LEFT,.08f);
        ui.button("pick.close",px+pw-20-110,py+14,110,40,"CLOSE",Ui.NORMAL,true,()->s.picking=State.PICK_NONE);
        int cols=models?2:4,rows=8,per=cols*rows,pages=Math.max(1,(list.length+per-1)/per);
        int current=models?m.id:rig.preset(s.keyPart);
        if(s.pickPage<0)s.pickPage=current<0?0:current/per;
        s.pickPage=Math.max(0,Math.min(pages-1,s.pickPage));
        float gx=px+20,gy=py+70,gap=8,cw=(pw-40-gap*(cols-1))/cols,ch=(ph-70-80-gap*(rows-1))/rows;
        for(int i=0;i<per;i++){
            final int n=s.pickPage*per+i;if(n>=list.length)break;
            float cx=gx+(i/rows)*(cw+gap),cy=gy+(i%rows)*(ch+gap);
            ui.button("pick."+n,cx,cy,cw,ch,Text.fit(g,list[n],cw-16,13),n==current?Ui.SELECTED:Ui.NORMAL,true,()->{
                if(models)chooseModel(n);else choosePreset(n);
                s.picking=State.PICK_NONE;
            });
        }
        if(pages>1){
            float by=py+ph-60;
            ui.button("pick.prev",px+20,by,120,44,"‹ PAGE",Ui.NORMAL,s.pickPage>0,()->s.pickPage--);
            ui.button("pick.next",px+pw-20-120,by,120,44,"PAGE ›",Ui.NORMAL,s.pickPage<pages-1,()->s.pickPage++);
            g.text((s.pickPage+1)+" / "+pages,px+pw/2,by+28,Gfx.MONO,13,Theme.MUTED,Gfx.CENTER,0);
        }
    }

    // ---- a lesson step on the SOUND screen ----------------------------------------------
    private void coach(float x,float y,float w,float h){
        Gfx g=ui.g;
        g.rect(x,y,w,h,0xff1d2414);g.frame(x,y,w,h,2,Theme.REQUEST);
        Tutorial.Step st=tutorial.currentStep();
        g.text("LESSON "+(tutorial.lesson+1)+" · "+tutorial.current().title.toUpperCase(Locale.ROOT),x+18,y+24,Gfx.MONO,10.5f,Theme.MUTED,Gfx.LEFT,.1f);
        g.text(st.title,x+18,y+50,Gfx.DISPLAY,15,Theme.REQUEST,Gfx.LEFT,.02f);
        float bw=110,right=x+w-14;
        Text.block(g,st.text,x+18,y+56,w-3*bw-80,Gfx.MONO,13,Theme.TEXT,1.4f);
        boolean done=tutorial.complete();
        ui.button("coach.next",right-bw,y+h/2-24,bw,48,done?"NEXT":"SKIP",done?Ui.SELECTED:Ui.NORMAL,true,this::next);
        ui.button("coach.back",right-2*bw-8,y+h/2-24,bw,48,"BACK",Ui.NORMAL,true,this::back);
        ui.button("coach.lessons",right-3*bw-16,y+h/2-24,bw,48,"LESSONS",Ui.QUIET,true,()->{s.tab=State.TAB_LEARN;s.coached=false;});
        if(done){float cx=right-3*bw-30;g.text("DONE",cx,y+h/2+5,Gfx.MONO_MEDIUM,13,Theme.PHOSPHOR,Gfx.RIGHT,.08f);Learn.check(g,cx-g.textWidth("DONE",Gfx.MONO_MEDIUM,13,.08f)-24,y+h/2,9,Theme.PHOSPHOR);}
    }
    /** A pulsing frame around the part of the screen a lesson step is about. */
    private void pointAt(String region){
        float[] r=region==null?null:regions.get(region);if(r==null)return;
        float pulse=(float)(.5+.5*Math.sin(ui.now/180.0));
        int alpha=(int)(120+135*pulse)<<24;
        ui.g.frame(r[0]-6,r[1]-6,r[2]+12,r[3]+12,3,alpha|(Theme.REQUEST&0xffffff));
    }
}
