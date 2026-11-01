/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth.ui;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

/**
 * The expedition screens: Expedition (survey, dials, encounter card, actions), the
 * Valley Survey map, an Encounter, the Journal, the Bestiary and Start. They draw from
 * UiState.expedition(), the seeded stand-in, until the conductor's own expedition runs
 * on the tablet.
 */
final class FieldScreens {
    private final Ui ui;private final UiState s;private final Screens sc;private Gfx g;
    FieldScreens(Ui ui,UiState state,Screens screens){this.ui=ui;s=state;sc=screens;}

    // ---- text helpers ------------------------------------------------------------------
    static ArrayList<String> wrap(Gfx g,String text,int font,float size,float width){
        ArrayList<String> lines=new ArrayList<>();
        StringBuilder line=new StringBuilder();
        for(String word:text.split(" ")){
            String next=line.length()==0?word:line+" "+word;
            if(line.length()>0&&g.textWidth(next,font,size,0)>width){lines.add(line.toString());line=new StringBuilder(word);}
            else{line.setLength(0);line.append(next);}
        }
        if(line.length()>0)lines.add(line.toString());
        return lines;
    }
    /** Draws wrapped text and returns the baseline after the last line. */
    private float para(String text,float x,float y,float width,int font,float size,float lead,int color,int maxLines){
        ArrayList<String> lines=wrap(g,text,font,size,width);
        for(int i=0;i<lines.size()&&i<maxLines;i++){g.text(lines.get(i),x,y,font,size,color,Gfx.LEFT,0);y+=lead;}
        return y;
    }
    private void centred(String text,float cx,float y,float width,int font,float size,float lead,int color){
        for(String l:wrap(g,text,font,size,width)){g.text(l,cx,y,font,size,color,Gfx.CENTER,0);y+=lead;}
    }
    private void title(String t,float x,float y){g.text(t,x,y,Gfx.DISPLAY,14,Theme.PHOSPHOR,Gfx.LEFT,.02f);}

    // ---- Expedition ---------------------------------------------------------------------
    void expedition(float x,float y,float w,float h,boolean focus){
        g=ui.g;Expedition ex=s.expedition();
        if(s.mapOpen){map(x,y,w,h,focus);return;}
        statusStrip(x,y,w,ex);
        if(s.encounterOpen&&!ex.encounters.isEmpty()){encounter(x,y,w,h,focus,ex);return;}
        s.encounterOpen=false;
        if(focus){
            float rw=340,rx=x+w-rw;
            survey(x,y+55,rx-13-x,466,ex);
            dials(rx,y+55,rw,143);
            card(rx,y+210,rw,200,ex,true);
            sc.goButton("expedition.instrument",rx,y+474,rw,47,"INSTRUMENT",UiState.Screen.INSTRUMENT);
        }else{
            float rw=330,rx=x+w-rw;
            card(x,y+53,rx-10-x,150,ex,false);
            survey(x,y+213,rx-10-x,246,ex);
            dials(rx,y+53,rw,144);
            responses(rx,y+207,rw,252,true);
        }
        actions(x,y+h-58,w,58,ex);
    }
    private void statusStrip(float x,float y,float w,Expedition ex){
        String[] items={"VALLEY "+ex.valley.number(),"DAY "+ex.day()+" / "+ex.time(),ex.habitat(),ex.weather(),
            String.format(Locale.ROOT,"STATION %02d / %02d",ex.station+1,ex.valley.stations.length)};
        float cx=x+7;
        for(int i=0;i<items.length;i++){
            if(i>0){g.line(cx,y+8,cx,y+36,1,Theme.BORDER);cx+=13;}
            g.text(items[i],cx,y+27,Gfx.MONO,12,Theme.TEXT,Gfx.LEFT,.04f);
            cx+=g.textWidth(items[i],Gfx.MONO,12,.04f)+13;
        }
        float bx=x+w-67;
        if(s.runPhase==UiState.ENDED)ui.button("expedition.summary",x+w-120,y,120,43,"Run summary",Ui.NORMAL,true,()->s.endDismissed=false);
        else ui.button("expedition.endrun",bx,y,67,43,"End run",Ui.NORMAL,s.runPhase==UiState.RUNNING,()->sc.run.end(ui.now));
        if(s.encounterOpen&&!s.mapOpen)ui.button("expedition.back",bx-(s.runPhase==UiState.ENDED?173:120),y,110,43,"Back to survey",Ui.NORMAL,true,()->s.encounterOpen=false);
    }
    private void survey(float x,float y,float w,float h,Expedition ex){
        ui.panel(x,y,w,h);
        title("SURVEY",x+11,y+27);
        float mx=x+10,my=y+38,mw=w-20,mh=h-38-(h>300?58:52);
        Valley.Place p=ex.place();
        ValleyMap.draw(g,ex,mx,my,mw,mh,p.x,p.y,h>300?2.2f:2.0f,ValleyMap.SURVEY,s.override);
        mapCaption(mx,my+mh-22,mw,ex,9);
        ui.hit("survey.map",mx,my,mw,mh,()->openMap(true));
        ui.button("survey.open",x+10,y+h-(h>300?54:50),92,h>300?45:42,"Open map",Ui.NORMAL,true,()->openMap(false));
    }
    private void openMap(boolean centre){
        s.mapOpen=true;s.mapTools=false;
        if(centre){Valley.Place p=s.expedition().place();s.mapX=p.x;s.mapY=p.y;s.mapScale=0;}
    }
    private void mapCaption(float x,float y,float w,Expedition ex,float size){
        g.rect(x,y,w,22,0xe0101910);
        g.text("VALLEY "+ex.valley.number()+" / "+ex.place().name,x+8,y+15,Gfx.MONO,size,0xffb9c7af,Gfx.LEFT,.03f);
        g.text(s.override!=null?"YOUR PATH / OVERRIDE":"ROUTE AHEAD / CONDUCTOR",x+w-8,y+15,Gfx.MONO,size,s.override!=null?Theme.REQUEST:0xffb9c7af,Gfx.RIGHT,.03f);
    }
    /** Danger and Majesty, the same two notes as on INSTRUMENT, with their live response. */
    private void dials(float x,float y,float w,float h){
        ui.panel(x,y,w,h);
        for(int k=0;k<2;k++){
            final int i=k;float col=x+w/4+k*w/2;
            g.text(UiState.NOTE_NAMES[i],col,y+24,Gfx.DISPLAY,12.5f,Theme.PHOSPHOR,Gfx.CENTER,.02f);
            float kx=col-20,ky=y+h/2+12;int v=s.notes[i];
            ui.knob("note."+i,kx,ky,62,v,0,100,false,Integer.toString(Math.round(ui.shown("note."+i,v))),value->s.notes[i]=value);
            String text=s.responseText[i]!=null?s.responseText[i]:Float.isNaN(s.response[i])?"IDLE":Integer.toString(Math.round(s.response[i]*100));
            ui.gauge(col+38,y+48,8,52,s.response[i],UiState.RESPONSE_NAMES[i],text,11);
        }
    }
    /** The conductor's response list: Danger, Majesty, Density and Silence, or all six others in Performance. */
    private void responses(float x,float y,float w,float h,boolean six){
        ui.panel(x,y,w,h);
        g.text("CONDUCTOR RESPONSE",x+11,y+26,Gfx.DISPLAY,12.5f,Theme.PHOSPHOR,Gfx.LEFT,.02f);
        if(six){
            float cw=(w-30)/2;
            for(int k=0;k<6;k++){
                int i=k+2;float cx=x+11+(k%2)*(cw+8),cy=y+52+(k/2)*((h-62)/3);
                g.text(title(UiState.NOTE_NAMES[i]),cx,cy,Gfx.MONO,11.5f,Theme.TEXT,Gfx.LEFT,0);
                g.text(UiState.RESPONSE_NAMES[i],cx,cy+18,Gfx.MONO,7.5f,Theme.MUTED,Gfx.LEFT,.06f);
                ui.bar(cx+46,cy+12,cw-90,6,s.response[i]);
                g.text(responseText(i),cx+cw-2,cy+19,Gfx.MONO,9.5f,Float.isNaN(s.response[i])?Theme.DIM:Theme.PHOSPHOR,Gfx.RIGHT,0);
            }
        }else{
            int[] rows={0,1,2,7};
            for(int k=0;k<4;k++){
                int i=rows[k];float ry=y+58+k*((h-70)/4);
                g.text(title(UiState.NOTE_NAMES[i]),x+11,ry,Gfx.MONO,12,Theme.TEXT,Gfx.LEFT,0);
                g.text(UiState.RESPONSE_NAMES[i],x+80,ry-1,Gfx.MONO,7.5f,Theme.MUTED,Gfx.LEFT,.06f);
                ui.bar(x+128,ry-7,w-128-56,7,s.response[i]);
                g.text(responseText(i),x+w-11,ry,Gfx.MONO,11,Float.isNaN(s.response[i])?Theme.DIM:Theme.PHOSPHOR,Gfx.RIGHT,0);
            }
        }
    }
    private String responseText(int i){
        if(s.responseText[i]!=null)return s.responseText[i];
        return Float.isNaN(s.response[i])?"—":Integer.toString(Math.round(s.response[i]*100));
    }
    private static String title(String s){return s.charAt(0)+s.substring(1).toLowerCase(Locale.ROOT);}
    private void card(float x,float y,float w,float h,Expedition ex,boolean focus){
        ui.panel(x,y,w,h);
        Expedition.Encounter e=ex.current();
        if(e==null){
            title("EXPEDITION LOG",x+11,y+27);
            float ty=para(ex.log,x+11,y+56,w-22,Gfx.MONO,12.5f,19,Theme.TEXT,focus?5:3);
            if(!ex.encounters.isEmpty())ui.button("card.last",x+11,y+h-(focus?59:52),focus?160:150,focus?47:42,"Last encounter",Ui.NORMAL,true,this::openEncounter);
            return;
        }
        String head=e.identified()?(e.danger()?"DANGER / ":"MAJESTY / ")+Expedition.SPECIES[e.species].name.toUpperCase(Locale.ROOT):ex.encounterCaption(e);
        title(head,x+11,y+27);
        float sx=x+(focus?58:50),sy=y+(focus?88:66);
        if(e.identified()){
            ValleyMap.silhouette(g,e.species,sx,sy,focus?64:46,Theme.PHOSPHOR);
            if(focus)g.text("FIELD OBSERVATION",x+11,y+h-74,Gfx.MONO,7.5f,Theme.MUTED,Gfx.LEFT,.06f);
        }else g.text("NO VISUAL CONTACT",sx,sy+4,Gfx.MONO,8,Theme.MUTED,Gfx.CENTER,.08f);
        if(focus)para(ex.cardLine(e),x+118,y+66,w-130,Gfx.MONO,12.5f,19,Theme.TEXT,4);
        else para(ex.cardLine(e),x+110,y+70,w-125,Gfx.MONO,12.5f,19,Theme.TEXT,2);
        ui.button("card.view",x+11,y+h-(focus?59:52),focus?150:140,focus?47:42,"View encounter",Ui.NORMAL,true,this::openEncounter);
    }
    private void openEncounter(){s.encounterOpen=true;s.encounterStage=-1;}
    private void actions(float x,float y,float w,float h,Expedition ex){
        float gap=9,bw=(w-gap*7)/8;
        for(int i=0;i<8;i++){
            final int a=i;
            ui.button("action."+i,x+i*(bw+gap),y,bw,h,Expedition.ACTIONS[i],ex.requested==i?Ui.SELECTED:Ui.NORMAL,s.runPhase==UiState.RUNNING,
                ()->{ex.act(a);s.encounterStage=-1;});
        }
    }

    // ---- Encounter ------------------------------------------------------------------------
    private void encounter(float x,float y,float w,float h,boolean focus,Expedition ex){
        Expedition.Encounter e=ex.encounters.get(ex.encounters.size()-1);
        int live=Math.min(7,e.stage);
        int stage=s.encounterStage<0||s.encounterStage>live?live:s.encounterStage;
        float rw=focus?319:300,rx=x+w-rw,mw=rx-x-10,top=y+53,mh=focus?466:404;
        ui.panel(x,top,mw,mh);
        boolean known=stage>=2;
        String cap=(known?(e.danger()?"DANGER":"MAJESTY"):"UNIDENTIFIED")+" / "+Expedition.STAGES[stage].toUpperCase(Locale.ROOT);
        g.text(cap,x+12,top+24,Gfx.MONO,9.5f,Theme.MUTED,Gfx.LEFT,.06f);
        g.text(known?Expedition.SPECIES[e.species].name:"Something in the "+Valley.COVER[e.terrain],x+12,top+58,Gfx.PRINT,focus?30:26,Theme.TEXT,Gfx.LEFT,0);
        float scy=top+(focus?205:170);
        if(known){
            ValleyMap.silhouette(g,e.species,x+mw/2,scy,(focus?150:120)*Expedition.shapeScale(e.species),Theme.PHOSPHOR);
            g.text("FIELD OBSERVATION",x+mw/2,scy+(focus?106:86),Gfx.MONO,8,Theme.MUTED,Gfx.CENTER,.06f);
        }else if(stage==1){
            ValleyMap.silhouette(g,e.species,x+mw/2,scy,(focus?150:120)*Expedition.shapeScale(e.species),0xff2a3d2b);
            g.text("A SHAPE, NOT YET CLEAR",x+mw/2,scy+(focus?106:86),Gfx.MONO,10,Theme.MUTED,Gfx.CENTER,.1f);
        }else g.text("NO VISUAL CONTACT",x+mw/2,scy+4,Gfx.MONO,13,Theme.MUTED,Gfx.CENTER,.12f);
        float tabsY=top+mh-58;
        ArrayList<String> lines=wrap(g,ex.stageLine(e,stage),Gfx.PRINT,focus?21:19,mw-24);
        float ly=tabsY-14-(lines.size()-1)*(focus?26:23);
        for(String l:lines){g.text(l,x+12,ly,Gfx.PRINT,focus?21:19,Theme.TEXT,Gfx.LEFT,0);ly+=focus?26:23;}
        g.line(x+12,tabsY,x+mw-12,tabsY,1,Theme.BORDER);
        float tw=(mw-24)/8;
        for(int i=0;i<8;i++){
            final int n=i;float tx=x+12+i*tw;boolean on=i==stage,reached=i<=live;
            int ink=on?Theme.REQUEST:reached?Theme.MUTED:Theme.DIM;
            g.text(String.format(Locale.ROOT,"%02d",i+1),tx+tw/2,tabsY+22,Gfx.MONO,10.5f,ink,Gfx.CENTER,0);
            g.text(Expedition.STAGES[i],tx+tw/2,tabsY+38,Gfx.MONO,9,ink,Gfx.CENTER,0);
            if(on)g.rect(tx+tw*.08f,tabsY+46,tw*.84f,2,Theme.REQUEST);
            if(reached)ui.hit("stage."+i,tx,tabsY+4,tw,50,()->s.encounterStage=n==live?-1:n);
        }
        // right column: response, then position
        responses(rx,top,rw,focus?199:192,false);
        float py=top+(focus?210:202),ph=mh-(focus?210:202);
        ui.panel(rx,py,rw,ph);
        g.text("POSITION",rx+11,py+24,Gfx.DISPLAY,12.5f,Theme.PHOSPHOR,Gfx.LEFT,.02f);
        Valley.Place p=ex.place();
        float bx=rx+8,by=py+34,bw=rw-16,bh=ph-42;
        ValleyMap.draw(g,ex,bx,by,bw,bh,p.x,p.y,1.1f,ValleyMap.MINI,s.override);
        mapCaption(bx,by+bh-22,bw,ex,7.5f);
        ui.hit("position.map",bx,by,bw,bh,()->openMap(true));
        actions(x,y+h-58,w,58,ex);
    }

    // ---- Valley survey map ------------------------------------------------------------
    private float mapFit;
    private float mapScale(){return s.mapScale<=0?mapFit:Math.max(mapFit,Math.min(mapFit*6,s.mapScale));}
    private void setScale(float k){s.mapScale=Math.max(mapFit,Math.min(mapFit*6,k));if(s.mapScale<=mapFit*1.001f)s.mapScale=0;}
    private void clampCentre(){Valley v=s.expedition().valley;s.mapX=Math.max(v.minX,Math.min(v.maxX,s.mapX));s.mapY=Math.max(v.minY,Math.min(v.maxY,s.mapY));}
    private void map(float x,float y,float w,float h,boolean focus){
        final Expedition ex=s.expedition();
        g.text("VALLEY SURVEY",x+1,y+37,Gfx.DISPLAY,22,Theme.TEXT,Gfx.LEFT,.02f);
        Valley.Region here=ex.valley.regions[Math.max(0,ex.valley.regionAt(ex.place().x,ex.place().y))];
        g.text("TOP-DOWN / "+here.name,x+w-310,y+32,Gfx.MONO,11.5f,Theme.TEXT,Gfx.RIGHT,.04f);
        ui.button("map.back",x+w-298,y+3,176,48,"Back to expedition",Ui.NORMAL,true,()->{s.mapOpen=false;s.plotting=false;s.mapTools=false;});
        ui.button("map.tools",x+w-108,y+3,108,48,"View tools",s.mapTools?Ui.SELECTED:Ui.NORMAL,true,()->s.mapTools=!s.mapTools);
        final float mx=x,my=y+64,mw=w-214,mh=h-64-(focus?77:72);
        mapFit=ValleyMap.fit(ex.valley,mw,mh);
        if(s.mapScale<=0){s.mapX=ValleyMap.midX(ex.valley);s.mapY=ValleyMap.midY(ex.valley);}
        final float scale=mapScale();
        clampCentre();
        ui.panel(mx,my,mw,mh);
        ValleyMap.draw(g,ex,mx+1,my+1,mw-2,mh-2,s.mapX,s.mapY,scale,ValleyMap.FULL,s.override);
        mapCaption(mx+1,my+mh-23,mw-2,ex,9.5f);
        ui.hitPan("map.view",mx,my,mw,mh-23,new Ui.Pan(){
            float lx,ly,ls;int lf;
            public void update(float cx,float cy,float spread,int fingers){
                float k=mapScale();
                if(fingers==lf){
                    if(k>mapFit*1.001f||fingers>1){s.mapX-=(cx-lx)/k;s.mapY-=(cy-ly)/k;}
                    if(fingers>1&&ls>4&&spread>4){
                        float wx=s.mapX+(cx-(mx+mw/2))/k,wy=s.mapY+(cy-(my+mh/2))/k;
                        setScale(k*spread/ls);float nk=mapScale();
                        if(s.mapScale>0){s.mapX=wx-(cx-(mx+mw/2))/nk;s.mapY=wy-(cy-(my+mh/2))/nk;}
                    }
                    clampCentre();
                }
                lx=cx;ly=cy;ls=spread;lf=fingers;
            }
            public void end(boolean tap,float px,float py){
                lf=0;
                if(!tap||!s.plotting)return;
                float k=mapScale(),wx=s.mapX+(px-(mx+mw/2))/k,wy=s.mapY+(py-(my+mh/2))/k;
                if(!Valley.inside(ex.valley.outline,wx,wy))return;
                float[] o=s.override==null?new float[0]:s.override;
                float[] n=new float[o.length+2];System.arraycopy(o,0,n,0,o.length);n[o.length]=wx;n[o.length+1]=wy;
                s.override=n;
            }
        });
        if(s.mapTools){
            float tw=330,th=170,tx=mx+mw-tw-10,ty=my+10;
            g.rect(tx,ty,tw,th,0xff1d291a);g.frame(tx,ty,tw,th,1,Theme.BORDER);ui.block(tx,ty,tw,th);
            g.text("VIEW TOOLS",tx+14,ty+30,Gfx.DISPLAY,14,Theme.PHOSPHOR,Gfx.LEFT,.02f);
            float bw=(tw-38)/2;
            ui.button("tools.zoomin",tx+14,ty+46,bw,48,"Zoom +",Ui.NORMAL,scale<mapFit*5.9f,()->{setScale(mapScale()*1.5f);if(s.mapScale>0&&scale<=mapFit*1.001f){Valley.Place p=ex.place();s.mapX=p.x;s.mapY=p.y;}});
            ui.button("tools.zoomout",tx+24+bw,ty+46,bw,48,"Zoom -",Ui.NORMAL,scale>mapFit*1.001f,()->setScale(mapScale()/1.5f));
            ui.button("tools.centre",tx+14,ty+106,bw,48,"Centre on me",Ui.NORMAL,true,()->{
                if(s.mapScale<=0)s.mapScale=mapFit*2;Valley.Place p=ex.place();s.mapX=p.x;s.mapY=p.y;});
            ui.button("tools.whole",tx+24+bw,ty+106,bw,48,"Whole valley",Ui.NORMAL,true,()->setScale(0));
        }
        legend(x+w-200,my,200,focus);
        g.text(s.plotting?"TAP THE MAP TO ADD POINTS TO YOUR PATH":"DRAG TO PAN / PINCH TO ZOOM",x,y+h-30,Gfx.MONO,11,s.plotting?Theme.REQUEST:Theme.TEXT,Gfx.LEFT,.04f);
        ui.button("map.plot",x+w-238,y+h-55,120,48,s.plotting?"Done":"Plot a path",s.plotting?Ui.SELECTED:Ui.NORMAL,s.runPhase==UiState.RUNNING,()->s.plotting=!s.plotting);
        ui.button("map.clear",x+w-108,y+h-55,108,48,"Clear path",s.override!=null?Ui.SELECTED:Ui.NORMAL,s.override!=null,()->{s.override=null;s.plotting=false;});
    }
    private void legend(float x,float y,float w,boolean focus){
        float kh=focus?210:206;
        ui.panel(x,y,w,kh);
        g.text("TERRAIN KEY",x+11,y+24,Gfx.DISPLAY,11,Theme.PHOSPHOR,Gfx.LEFT,.02f);
        for(int t=0;t<8;t++){
            float ry=y+36+t*21.2f;
            g.rect(x+11,ry,34,18,ValleyMap.GROUND);g.frame(x+11,ry,34,18,1,Theme.BORDER);
            g.clip(x+12,ry+1,32,16);
            float k=.55f;
            ValleyMap.glyph(g,t,x+28-Valley.CELL[t][0]*k/2,ry+9-Valley.CELL[t][1]*k/2,k,1);
            g.unclip();
            g.text(Valley.KEY[t],x+54,ry+13,Gfx.MONO,9,Theme.TEXT,Gfx.LEFT,.02f);
        }
        float oy=y+kh+11,oh=160;
        ui.panel(x,oy,w,oh);
        g.text("ON THE MAP",x+11,oy+24,Gfx.DISPLAY,11,Theme.PHOSPHOR,Gfx.LEFT,.02f);
        g.line(x+11,oy+45,x+40,oy+45,3,ValleyMap.TRAIL);g.text("Travelled trail",x+54,oy+49,Gfx.MONO,9.5f,Theme.TEXT,Gfx.LEFT,0);
        g.polyline(new float[]{x+11,oy+67,x+40,oy+67},false,3,ValleyMap.AHEAD,6);g.text("Route ahead",x+54,oy+71,Gfx.MONO,9.5f,Theme.TEXT,Gfx.LEFT,0);
        g.polyline(new float[]{x+11,oy+89,x+40,oy+89},false,3,Theme.REQUEST,6);g.text("Your path",x+54,oy+93,Gfx.MONO,9.5f,Theme.TEXT,Gfx.LEFT,0);
        g.ring(x+25,oy+110,6,2,Theme.REQUEST);g.text("You are here",x+54,oy+114,Gfx.MONO,9.5f,Theme.TEXT,Gfx.LEFT,0);
        g.text("Square / station",x+11,oy+135,Gfx.MONO,9,Theme.TEXT,Gfx.LEFT,0);
        g.text("Diamond / landmark   Animals / herd",x+11,oy+151,Gfx.MONO,8.5f,Theme.TEXT,Gfx.LEFT,0);
    }

    // ---- Journal -------------------------------------------------------------------------
    void journal(float x,float y,float w,float h,boolean focus){
        g=ui.g;Expedition ex=s.expedition();
        ArrayList<Expedition.Entry> j=ex.journal();
        int page=s.journalPage<0||s.journalPage>=j.size()?j.size()-1:s.journalPage;
        Expedition.Entry en=j.get(page);
        // field record list
        float lw=focus?164:148;
        int visible=Math.min(j.size(),focus?9:7);
        int first=Math.max(0,Math.min(page-visible/2,j.size()-visible));
        float lh=36+visible*55+4;
        ui.panel(x,y,lw,lh);
        g.text("FIELD RECORD",x+9,y+24,Gfx.DISPLAY,11,Theme.PHOSPHOR,Gfx.LEFT,.02f);
        for(int i=0;i<visible;i++){
            final int n=first+i;float by=y+36+i*55;boolean on=n==page;
            boolean pressed=ui.held("journal.entry."+n);
            g.rect(x+8,by,lw-16,47,on?Theme.SELECTED_FILL:pressed?Theme.TRACK:Theme.RAISED);g.frame(x+8,by,lw-16,47,1,on?Theme.REQUEST:Theme.BORDER);
            ArrayList<String> t=wrap(g,j.get(n).title,Gfx.MONO,11.5f,lw-28);
            float ty=by+(t.size()>1?20:28);
            for(int k=0;k<t.size()&&k<2;k++){g.text(t.get(k),x+lw/2,ty,Gfx.MONO,11.5f,on?Theme.REQUEST:Theme.TEXT,Gfx.CENTER,0);ty+=15;}
            ui.hit("journal.entry."+n,x+8,by,lw-16,47,()->s.journalPage=n);
        }
        // the page
        float pw=focus?w-lw-16-276:w-lw-16-276,px=x+lw+16,cw=pw-16;
        g.line(px,y,px+pw,y,1,Theme.BORDER);g.line(px,y+h,px+pw,y+h,1,Theme.BORDER);
        g.text(en.header,px+8,y+24,Gfx.MONO,9,Theme.MUTED,Gfx.LEFT,.06f);
        g.text(en.title,px+8,y+62,Gfx.PRINT,focus?34:30,Theme.TEXT,Gfx.LEFT,0);
        g.text(en.place,px+8,y+(focus?100:94),Gfx.PRINT,focus?18:17,Theme.MUTED,Gfx.LEFT,0);
        float ty=y+(focus?136:126),size=focus?19.5f:18,lead=focus?24:21.5f,limit=y+h-72;
        for(String p:en.paragraphs){
            for(String l:wrap(g,p,Gfx.HAND,size,cw)){if(ty>limit)break;g.text(l,px+8,ty,Gfx.HAND,size,0xffd6e8c8,Gfx.LEFT,0);ty+=lead;}
            ty+=7;
        }
        final int last=j.size()-1;final int pg=page;
        ui.button("journal.previous",px+8,y+h-60,112,48,"Previous page",Ui.NORMAL,page>0,()->s.journalPage=pg-1);
        g.text(String.format(Locale.ROOT,"ENTRY %d / %d",page+1,j.size()),px+pw/2,y+h-31,Gfx.MONO,10.5f,Theme.TEXT,Gfx.CENTER,.04f);
        ui.button("journal.next",px+pw-90,y+h-60,82,48,"Next page",Ui.NORMAL,page<last,()->s.journalPage=pg+1==last?-1:pg+1);
        // plate
        float qx=px+pw+17,qw=x+w-qx;
        g.line(qx-17,y,qx-17,y+h,1,Theme.BORDER);
        if(en.species>=0){
            Expedition.Species sp=Expedition.SPECIES[en.species];
            g.text("PLATE "+Expedition.roman(en.species+1),qx,y+32,Gfx.PRINT,22,Theme.PHOSPHOR,Gfx.LEFT,.02f);
            ValleyMap.silhouette(g,en.species,qx+qw/2,y+88,80*Expedition.shapeScale(en.species),Theme.PHOSPHOR);
            g.text("FIELD OBSERVATION",qx+qw/2,y+142,Gfx.MONO,7.5f,Theme.MUTED,Gfx.CENTER,.06f);
            g.text(sp.name,qx,y+176,Gfx.PRINT,26,Theme.TEXT,Gfx.LEFT,0);
            g.text("First seen: day "+en.day,qx,y+212,Gfx.PRINT,17,Theme.TEXT,Gfx.LEFT,0);
            g.text(en.title,qx,y+236,Gfx.PRINT,17,Theme.TEXT,Gfx.LEFT,0);
            g.text(Character.toUpperCase(sp.diet.charAt(0))+sp.diet.substring(1)+" / "+sp.period,qx,y+260,Gfx.PRINT,17,Theme.TEXT,Gfx.LEFT,0);
        }else{
            g.text("FIELD NOTES",qx,y+32,Gfx.PRINT,22,Theme.PHOSPHOR,Gfx.LEFT,.02f);
            g.text(en.title,qx,y+176,Gfx.PRINT,26,Theme.TEXT,Gfx.LEFT,0);
            g.text("Day "+en.day,qx,y+212,Gfx.PRINT,17,Theme.TEXT,Gfx.LEFT,0);
            g.text(Expedition.title(ex.valley.stations[en.station].name),qx,y+236,Gfx.PRINT,17,Theme.TEXT,Gfx.LEFT,0);
        }
        g.line(qx,y+284,qx+qw,y+284,1,Theme.BORDER);
        float qy=para(en.quote,qx,y+316,qw,Gfx.HAND,18,22,Theme.PHOSPHOR,6);
        g.text("— "+en.quoteBy,qx,qy,Gfx.HAND,18,Theme.PHOSPHOR,Gfx.LEFT,0);
        final int species=en.species;
        ui.button("journal.bestiary",qx,y+h-57,qw,48,"Open bestiary",Ui.NORMAL,true,()->{
            if(species>=0){s.bestiarySelected=species;s.bestiaryGroup=-1;s.bestiaryPage=order().indexOf(species)/12;}
            sc.go(UiState.Screen.BESTIARY);
        });
    }

    // ---- Bestiary ------------------------------------------------------------------------
    private ArrayList<Integer> order(){
        ArrayList<Integer> list=new ArrayList<>();
        for(int i=0;i<Expedition.SPECIES.length;i++)if(s.bestiaryGroup<0||Expedition.SPECIES[i].group==s.bestiaryGroup)list.add(i);
        if(s.bestiaryByName)list.sort((a,b)->Expedition.SPECIES[a].name.compareTo(Expedition.SPECIES[b].name));
        return list;
    }
    void bestiary(float x,float y,float w,float h,boolean focus){
        g=ui.g;Expedition ex=s.expedition();
        g.text("BESTIARY",x+1,y+37,Gfx.DISPLAY,22,Theme.TEXT,Gfx.LEFT,.02f);
        ArrayList<Integer> list=order();
        int per=12,pages=Math.max(1,(list.size()+per-1)/per);
        if(s.bestiaryPage>=pages)s.bestiaryPage=pages-1;
        final int page=s.bestiaryPage;
        String find=s.bestiaryGroup>=0?Expedition.GROUPS[s.bestiaryGroup].toUpperCase(Locale.ROOT):s.bestiaryByName?"ALL / BY NAME":"ALL / BY NUMBER";
        ui.tile("bestiary.find",x+w-588,y+2,150,51,"FIND SPECIES",find,()->{
            String[] o=new String[Expedition.GROUPS.length+2];o[0]="All, by number";o[1]="All, by name";
            for(int i=0;i<Expedition.GROUPS.length;i++)o[i+2]=Expedition.GROUPS[i];
            int sel=s.bestiaryGroup>=0?s.bestiaryGroup+2:s.bestiaryByName?1:0;
            sc.choose("Find species",o,sel,v->{s.bestiaryByName=v==1;s.bestiaryGroup=v>=2?v-2:-1;s.bestiaryPage=0;});
        });
        g.text(ex.discovered()+" DISCOVERED",x+w-425,y+32,Gfx.MONO,11.5f,Theme.TEXT,Gfx.LEFT,.04f);
        ui.button("bestiary.previous",x+w-284,y+3,122,48,"Previous page",Ui.NORMAL,page>0,()->s.bestiaryPage=page-1);
        g.text((page+1)+" / "+pages,x+w-130,y+32,Gfx.MONO,11.5f,Theme.TEXT,Gfx.CENTER,0);
        ui.button("bestiary.next",x+w-96,y+3,96,48,"Next page",Ui.NORMAL,page<pages-1,()->s.bestiaryPage=page+1);
        float dw=focus?339:310,gw=w-dw-17,top=y+66,rowGap=focus?9:8,rh=(h-66-rowGap*5)/6,cw=(gw-9)/2;
        for(int k=0;k<per&&page*per+k<list.size();k++){
            final int sp=list.get(page*per+k);
            float tx=x+(k%2)*(cw+9),ty=top+(k/2)*(rh+rowGap);
            boolean seen=ex.firstEncounter(sp)>=0,on=sp==s.bestiarySelected;
            boolean pressed=ui.held("bestiary."+sp);
            g.rect(tx,ty,cw,rh,on?Theme.SELECTED_FILL:pressed?Theme.TRACK:Theme.PANEL);g.frame(tx,ty,cw,rh,1,on?Theme.REQUEST:Theme.BORDER);
            String label=String.format(Locale.ROOT,"%02d / %s",sp+1,seen?Expedition.SPECIES[sp].name:"Not yet recorded");
            g.text(label,tx+11,ty+rh/2+5,Gfx.MONO,13,on?Theme.REQUEST:seen?Theme.TEXT:Theme.DIM,Gfx.LEFT,0);
            if(seen)ui.hit("bestiary."+sp,tx,ty,cw,rh,()->s.bestiarySelected=sp);
        }
        float dx=x+w-dw;
        ui.panel(dx,top,dw,h-66);
        int sel=s.bestiarySelected;
        if(sel<0||ex.firstEncounter(sel)<0){
            centred(ex.discovered()==0?"No species recorded yet. Encounters on the expedition fill these pages."
                :"Choose a recorded species to read its plate.",dx+dw/2,top+(h-66)/2-20,dw-50,Gfx.MONO,12.5f,19,Theme.MUTED);
        }else{
            Expedition.Species sp=Expedition.SPECIES[sel];
            g.text(sp.name,dx+14,top+44,Gfx.PRINT,focus?30:27,Theme.TEXT,Gfx.LEFT,0);
            float sy=top+(focus?140:118);
            ValleyMap.silhouette(g,sel,dx+dw/2,sy,(focus?150:120)*Expedition.shapeScale(sel),Theme.PHOSPHOR);
            g.text("FIELD OBSERVATION",dx+dw/2,sy+(focus?84:68),Gfx.MONO,7.5f,Theme.MUTED,Gfx.CENTER,.06f);
            g.text(Expedition.GROUPS[sp.group]+" / "+sp.diet,dx+14,sy+(focus?124:100),Gfx.MONO,12.5f,Theme.TEXT,Gfx.LEFT,0);
            g.text(sp.period+" / "+sp.habitat,dx+14,sy+(focus?147:121),Gfx.MONO,12.5f,Theme.TEXT,Gfx.LEFT,0);
            final int enc=ex.firstEncounter(sel);
            ui.button("bestiary.read",dx+14,top+h-66-(focus?142:128),dw-28,48,"Read first encounter",Ui.NORMAL,true,()->{
                s.journalPage=ex.journalIndex(enc);sc.go(UiState.Screen.JOURNAL);});
        }
        float cy=top+h-66-(focus?80:72);
        g.rect(dx+14,cy,dw-28,focus?60:56,Theme.RAISED);g.frame(dx+14,cy,dw-28,focus?60:56,1,Theme.BORDER);
        g.text("COLLECTION",dx+25,cy+21,Gfx.MONO,10,Theme.MUTED,Gfx.LEFT,.08f);
        g.text(ex.discovered()+" OF "+Expedition.SPECIES.length+" RECORDED / PAGED LIST",dx+25,cy+43,Gfx.MONO,12,Theme.PHOSPHOR,Gfx.LEFT,.04f);
    }

    // ---- Start ---------------------------------------------------------------------------
    private static final String[] LEADER_NAMES={"Dr. Wellington","Miss Veronica","Jonesy"},PRONOUNS={"he / him","she / her","they / them"},
        FLAVOURS={"Cinematic","Lo-fi","Dark ambient"},TAGS={"THE PALEOTONE BASELINE","WARM / WORN / UNHURRIED","LOW / SPARSE / UNSETTLING"},
        BLURBS={"Wide swells, full Bed and Melody, a clear encounter arc.","Soft saturation, gentle wobble, softened highs and a laid-back Pulse.",
            "Low drones, sparse melody, rumble, drips and longer silences."};
    void start(float x,float y,float w,float h,boolean focus){
        g=ui.g;
        g.text("PLAN A NEW RUN",x+1,y+37,Gfx.DISPLAY,22,Theme.TEXT,Gfx.LEFT,.02f);
        g.text("Current run stays in the header. Choose the next expedition below.",x+w,y+31,Gfx.MONO,12,Theme.TEXT,Gfx.RIGHT,0);
        g.text("NEW RUN / LEADER CHOICE",x,y+79,Gfx.MONO,9.5f,Theme.REQUEST,Gfx.LEFT,.06f);
        g.text("Every conductor note remains adjustable.",x+w,y+79,Gfx.MONO,9,Theme.TEXT,Gfx.RIGHT,0);
        float gap=14,cw=(w-gap*2)/3,cy=y+97,ch=h-97-(focus?74:73);
        for(int i=0;i<3;i++){
            final int n=i;float cx=x+i*(cw+gap),mid=cx+cw/2;boolean on=s.nextLeader==i;
            g.rect(cx,cy,cw,ch,on?0xff201f13:Theme.PANEL);g.frame(cx,cy,cw,ch,1,on?Theme.REQUEST:Theme.BORDER);
            g.polyline(new float[]{mid-29,cy+13,mid+29,cy+13,mid+29,cy+72,mid-29,cy+72},true,1,Theme.MUTED,3);
            g.text(LEADER_NAMES[i].substring(LEADER_NAMES[i].lastIndexOf(' ')+1,LEADER_NAMES[i].lastIndexOf(' ')+2),mid,cy+56,Gfx.PRINT,32,Theme.PHOSPHOR,Gfx.CENTER,0);
            g.text(LEADER_NAMES[i],mid,cy+101,Gfx.PRINT,22,Theme.TEXT,Gfx.CENTER,0);
            g.text(PRONOUNS[i],mid,cy+126,Gfx.MONO,10,Theme.MUTED,Gfx.CENTER,.04f);
            g.text(FLAVOURS[i],mid,cy+160,Gfx.PRINT,23,Theme.TEXT,Gfx.CENTER,0);
            g.text(TAGS[i],mid,cy+184,Gfx.MONO,8.5f,Theme.PHOSPHOR,Gfx.CENTER,.06f);
            centred(BLURBS[i],mid,cy+214,cw-50,Gfx.MONO,12,19,Theme.TEXT);
            ui.button("start.leader."+i,cx+13,cy+ch-62,cw-26,48,on?"Selected":"Choose leader",on?Ui.SELECTED:Ui.NORMAL,true,()->s.nextLeader=n);
        }
        float by=y+h-(focus?61:60),bh=59;
        ui.dragTile("start.seed",x,by,144,bh,"VALLEY SEED",String.format(Locale.ROOT,"%04d",s.nextSeed),s.nextSeed,0,9999,10,
            v->s.nextSeed=v,()->s.nextSeed=new Random().nextInt(10000));
        ui.tile("start.length",x+155,by,145,bh,"RUN LENGTH",Run.lengthText(s.nextRunLength).toUpperCase(Locale.ROOT),sc::openRunLength);
        float rest=w-311,bw=(rest-12)/2;
        ui.button("start.begin",x+311,by,bw,bh,"Begin expedition",Ui.SELECTED,true,()->{sc.run.newRun();sc.go(UiState.Screen.EXPEDITION);});
        ui.button("start.continue",x+311+bw+12,by,bw,bh,"Continue saved run",Ui.NORMAL,s.runPhase==UiState.RUNNING,()->sc.go(UiState.Screen.EXPEDITION));
    }

    // ---- end-card map strip ------------------------------------------------------------------
    void mapStrip(float x,float y,float w,float h){
        g=ui.g;Expedition ex=s.expedition();Valley.Place p=ex.place();
        ValleyMap.draw(g,ex,x,y,w,h,p.x,p.y,1.7f,ValleyMap.SURVEY,s.override);
        mapCaption(x,y+h-22,w,ex,9);
    }
}
