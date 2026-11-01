/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth.ui;

import java.util.Locale;

/**
 * Draws the flat top-down valley map into a box at any zoom. Terrain, water, routes and
 * creatures scale with the zoom; every label is drawn at one fixed on-screen size, so a
 * small or zoomed-out map stays readable instead of shrinking its names.
 */
final class ValleyMap {
    private ValleyMap(){}
    static final int MINI=0,SURVEY=1,FULL=2;
    /** Label sizes in screen units, the same at every zoom. */
    static final float REGION=10.5f,PLACE=9,WATER_NAME=9.5f,CREATURE=8.5f,YOU=12.5f,YOU_PLACE=9;
    static final int GROUND=0xff192316,OUTSIDE=0xff101910,BORDER=0xff52674d,OUTLINE=0xff718767,
        BANK=0xff88b69e,RIVER=0xff173f31,LAKE=0xff152c26,LAKE_EDGE=0xff8cc3af,TRAIL=0xffe0ead4,AHEAD=0xffa0d1a2,
        HERD=0xffb7edb6,HALO=0xff101910,LABEL=0xffbfd1ad,PLACE_INK=0xffdce7cc,WATER_INK=0xffa9dbc5,YOU_INK=0xffffbd60,
        YOU_PLACE_INK=0xffe5cf9a,STATION_FILL=0xff172717,STATION_EDGE=0xffe0e9ce;

    /** The scale that fits the whole valley in a box, centred on the valley's middle. */
    static float fit(Valley v,float w,float h){return Math.min(w/((v.maxX-v.minX)*1.08f),h/((v.maxY-v.minY)*1.1f));}
    static float midX(Valley v){return (v.minX+v.maxX)/2;}
    static float midY(Valley v){return (v.minY+v.maxY)/2;}

    /**
     * @param cx,cy the world point at the box centre
     * @param scale screen units per world unit
     * @param override your plotted path in world units, or null
     */
    static void draw(Gfx g,Expedition ex,float x,float y,float w,float h,float cx,float cy,float scale,int detail,float[] override){
        Valley v=ex.valley;
        final float ox=x+w/2-cx*scale,oy=y+h/2-cy*scale;
        g.clip(x,y,w,h);
        g.rect(x,y,w,h,OUTSIDE);
        g.polygon(tx(v.outline,ox,oy,scale),GROUND);
        // visible world rectangle
        float wx0=(x-ox)/scale,wy0=(y-oy)/scale,wx1=(x+w-ox)/scale,wy1=(y+h-oy)/scale;
        // faint survey grid
        for(int gx=100;gx<1000;gx+=100)if(gx>=wx0&&gx<=wx1)g.line(ox+gx*scale,Math.max(y,oy),ox+gx*scale,Math.min(y+h,oy+600*scale),.6f,0x7339513a);
        for(int gy=100;gy<600;gy+=100)if(gy>=wy0&&gy<=wy1)g.line(Math.max(x,ox),oy+gy*scale,Math.min(x+w,ox+1000*scale),oy+gy*scale,.6f,0x7339513a);
        // terrain glyphs
        float lw=Math.max(.8f,Math.min(2.2f,1.2f*scale));
        for(int t=0;t<7;t++){
            float cw=Valley.CELL[t][0],ch=Valley.CELL[t][1];
            for(float py=(float)Math.floor(wy0/ch)*ch-ch;py<wy1+ch;py+=ch)
                for(float px=(float)Math.floor(wx0/cw)*cw-cw;px<wx1+cw;px+=cw){
                    float mx=px+cw/2,my=py+ch/2;
                    int r=v.regionAt(mx,my);
                    if(r<0||v.regions[r].terrain!=t||Valley.inside(v.lake,mx,my)||Valley.inside(v.pond,mx,my))continue;
                    glyph(g,t,ox+px*scale,oy+py*scale,scale,lw);
                }
        }
        // region borders, clipped to the valley by drawing only interior pieces
        for(Valley.Region r:v.regions)borders(g,v,r.poly,ox,oy,scale);
        // water
        if(v.river.length>=4){
            g.polyline(tx(v.river,ox,oy,scale),false,Math.max(5,22*scale),BANK,0);
            g.polyline(tx(v.river,ox,oy,scale),false,Math.max(3.4f,17*scale),RIVER,0);
        }
        water(g,v.lake,ox,oy,scale,lw);water(g,v.pond,ox,oy,scale,lw);
        g.polyline(tx(v.outline,ox,oy,scale),true,1.8f,OUTLINE,0);
        // routes
        int st=ex.station;
        float[] past=new float[(st+1)*2];
        for(int i=0;i<=st;i++){past[i*2]=v.stations[i].x;past[i*2+1]=v.stations[i].y;}
        if(override!=null&&override.length>=2){
            float[] path=new float[override.length+2];path[0]=v.stations[st].x;path[1]=v.stations[st].y;
            System.arraycopy(override,0,path,2,override.length);
            if(path.length>=4)g.polyline(tx(path,ox,oy,scale),false,3,Theme.REQUEST,7);
        }else if(st<v.stations.length-1){
            float[] ahead=new float[(v.stations.length-st)*2];
            for(int i=st;i<v.stations.length;i++){ahead[(i-st)*2]=v.stations[i].x;ahead[(i-st)*2+1]=v.stations[i].y;}
            g.polyline(tx(ahead,ox,oy,scale),false,3,AHEAD,7);
        }
        if(past.length>=4)g.polyline(tx(past,ox,oy,scale),false,3.2f,TRAIL,0);
        // stations and landmarks
        for(int i=0;i<v.stations.length;i++){
            if(i==st)continue;
            float sx=ox+v.stations[i].x*scale,sy=oy+v.stations[i].y*scale;
            float s=detail==MINI?5:7;
            g.rect(sx-s/2,sy-s/2,s,s,STATION_FILL);g.frame(sx-s/2,sy-s/2,s,s,1.5f,STATION_EDGE);
        }
        for(Valley.Place p:v.landmarks){
            float sx=ox+p.x*scale,sy=oy+p.y*scale,d=detail==MINI?6:9;
            float[] dia={sx,sy-d,sx+d*.75f,sy,sx,sy+d,sx-d*.75f,sy};
            g.polygon(dia,STATION_FILL);g.polyline(dia,true,1.6f,STATION_EDGE,0);
        }
        // creatures
        float cs=Math.max(.55f,Math.min(1.15f,scale*.95f))*(detail==MINI?.8f:1);
        for(Expedition.Herd hd:ex.herds){
            float hx=ox+hd.x*scale,hy=oy+hd.y*scale;
            float[] shape=Expedition.shape(hd.species);float k=cs*Expedition.shapeScale(hd.species);
            if(hd.count>1){
                creature(g,shape,hx-17*k,hy+14*k,k*.95f);creature(g,shape,hx+19*k,hy+11*k,k*.95f);creature(g,shape,hx,hy,k);
            }else creature(g,shape,hx,hy,k);
        }
        // you are here
        float px=ox+v.stations[st].x*scale,py=oy+v.stations[st].y*scale;
        float r1=detail==MINI?10:15,r2=detail==MINI?5.5f:8.5f;
        g.ring(px,py,r1,2.2f,Theme.REQUEST);g.ring(px,py,r2,2.2f,Theme.REQUEST);
        g.line(px,py-r1-6,px,py-r1+2,2,Theme.REQUEST);g.line(px,py+r1-2,px,py+r1+6,2,Theme.REQUEST);
        g.line(px-r1-6,py,px-r1+2,py,2,Theme.REQUEST);g.line(px+r1-2,py,px+r1+6,py,2,Theme.REQUEST);
        g.circle(px,py,2.6f,Theme.REQUEST);
        // labels, all at fixed on-screen sizes, placed in priority order without overlapping
        Labels l=new Labels(g);
        l.taken(px-r1-4,py-r1-4,r1*2+8,r1*2+8);
        for(Expedition.Herd hd:ex.herds){float hx=ox+hd.x*scale,hy=oy+hd.y*scale,k=cs*(hd.count>1?1.6f:1);l.taken(hx-28*k,hy-18*k,56*k,36*k);}
        for(Valley.Place p:v.landmarks)l.taken(ox+p.x*scale-8,oy+p.y*scale-10,16,20);
        float you=detail==MINI?YOU_PLACE:YOU;
        String here=ex.stationLabel(st);
        float need=Math.max(g.textWidth("YOU ARE HERE",Gfx.MONO,you,.03f),g.textWidth(here,Gfx.MONO,YOU_PLACE,.03f));
        boolean left=px-r1-10-need>x+4;
        float lx=left?px-r1-10:px+r1+10;int al=left?Gfx.RIGHT:Gfx.LEFT;
        l.force("YOU ARE HERE",lx,py-6,you,YOU_INK,al);
        l.force(here,lx,py+(detail==MINI?6:9),YOU_PLACE,YOU_PLACE_INK,al);
        if(detail!=MINI){
            for(int i=0;i<v.stations.length;i++){
                boolean show=i==0||i==v.stations.length-1||i==st+1||(detail==FULL&&scale>1.6f);
                if(!show||i==st)continue;
                float sx=ox+v.stations[i].x*scale,sy=oy+v.stations[i].y*scale;
                l.place(ex.stationLabel(i),sx,sy,PLACE,PLACE_INK);
            }
            for(Valley.Place p:v.landmarks)l.place(p.name,ox+p.x*scale,oy+p.y*scale,PLACE,PLACE_INK);
            l.place(v.lakeName,ox+v.lakeLabelX*scale,oy+v.lakeLabelY*scale-WATER_NAME/2-4,WATER_NAME,WATER_INK);
            l.place(v.riverName,ox+v.riverLabelX*scale,oy+v.riverLabelY*scale-WATER_NAME/2-4,WATER_NAME,WATER_INK);
            for(Valley.Region r:v.regions)l.place(r.name,ox+r.labelX*scale,oy+r.labelY*scale-REGION/2-4,REGION,LABEL);
            for(Expedition.Herd hd:ex.herds)if(hd.count>1)
                l.place("HERD / "+hd.count,ox+hd.x*scale,oy+hd.y*scale+16*cs,CREATURE,0xffafe9ae);
        }
        if(detail==FULL){
            float nx=x+w-30,ny=y+22;
            g.line(nx,ny+30,nx,ny,1.5f,0xffb4c9a7);g.line(nx,ny,nx-6,ny+10,1.5f,0xffb4c9a7);g.line(nx,ny,nx+6,ny+10,1.5f,0xffb4c9a7);
            g.text("N",nx,ny-6,Gfx.MONO,10,LABEL,Gfx.CENTER,0);
            float km=Valley.KM*scale,bx=x+w-24-km,by=y+h-34;
            g.line(bx,by,bx+km,by,1.5f,0xffb4c9a7);
            g.line(bx,by-7,bx,by+1,1.5f,0xffb4c9a7);g.line(bx+km/2,by-7,bx+km/2,by+1,1.5f,0xffb4c9a7);g.line(bx+km,by-7,bx+km,by+1,1.5f,0xffb4c9a7);
            g.text("0",bx,by-11,Gfx.MONO,9,LABEL,Gfx.LEFT,0);g.text("0.5",bx+km/2,by-11,Gfx.MONO,9,LABEL,Gfx.CENTER,0);
            g.text("1 km",bx+km,by-11,Gfx.MONO,9,LABEL,Gfx.RIGHT,0);
        }
        g.unclip();
    }

    /** Reused point buffers for glyph strokes (the UI thread draws one frame at a time). */
    private static final float[][] SCRATCH=new float[64][];
    static void glyph(Gfx g,int t,float x,float y,float scale,float width){
        int ink=Valley.GLYPH_INK[t];
        for(float[] s:Valley.GLYPH[t]){
            float[] p=SCRATCH[s.length];if(p==null)p=SCRATCH[s.length]=new float[s.length];
            for(int i=0;i<s.length;i+=2){p[i]=x+s[i]*scale;p[i+1]=y+s[i+1]*scale;}
            if(p.length==4)g.line(p[0],p[1],p[2],p[3],width,ink);else g.polyline(p,false,width,ink,0);
        }
    }
    private static void water(Gfx g,float[] poly,float ox,float oy,float scale,float lw){
        float[] p=tx(poly,ox,oy,scale);
        g.polygon(p,LAKE);
        float x0=Float.MAX_VALUE,y0=Float.MAX_VALUE,x1=-Float.MAX_VALUE,y1=-Float.MAX_VALUE;
        for(int i=0;i<poly.length;i+=2){x0=Math.min(x0,poly[i]);x1=Math.max(x1,poly[i]);y0=Math.min(y0,poly[i+1]);y1=Math.max(y1,poly[i+1]);}
        float cw=Valley.CELL[Valley.WATER][0],ch=Valley.CELL[Valley.WATER][1];
        for(float py=(float)Math.floor(y0/ch)*ch;py<y1;py+=ch)
            for(float px=(float)Math.floor(x0/cw)*cw;px<x1;px+=cw)
                if(Valley.inside(poly,px+cw/2,py+ch/2))glyph(g,Valley.WATER,ox+px*scale,oy+py*scale,scale,lw);
        g.polyline(p,true,2,LAKE_EDGE,0);
    }
    /** Region edges, drawn only where they lie inside the valley outline. */
    private static void borders(Gfx g,Valley v,float[] poly,float ox,float oy,float scale){
        int n=poly.length/2;
        for(int i=0;i<n;i++){
            float ax=poly[i*2],ay=poly[i*2+1],bx=poly[(i+1)%n*2],by=poly[(i+1)%n*2+1];
            int steps=Math.max(1,(int)(Math.hypot(bx-ax,by-ay)/8));
            float sx=0,sy=0;boolean on=false;
            for(int k=0;k<=steps;k++){
                float t=k/(float)steps,x=ax+(bx-ax)*t,y=ay+(by-ay)*t;
                boolean in=Valley.inside(v.outline,x,y);
                if(in&&!on){sx=x;sy=y;on=true;}
                if((!in||k==steps)&&on){g.line(ox+sx*scale,oy+sy*scale,ox+x*scale,oy+y*scale,1.1f,BORDER);on=false;}
            }
        }
    }
    static void creature(Gfx g,float[] shape,float x,float y,float k){
        float[] p=new float[shape.length];
        for(int i=0;i<shape.length;i+=2){p[i]=x+shape[i]*k;p[i+1]=y+shape[i+1]*k;}
        g.polyline(p,true,2,0xff122012,0);g.polygon(p,HERD);
    }
    /** A large silhouette for the encounter, bestiary and journal plates. */
    static void silhouette(Gfx g,int species,float cx,float cy,float width,int color){
        float[] s=Expedition.shape(species);
        float x0=Float.MAX_VALUE,x1=-Float.MAX_VALUE,y0=Float.MAX_VALUE,y1=-Float.MAX_VALUE;
        for(int i=0;i<s.length;i+=2){x0=Math.min(x0,s[i]);x1=Math.max(x1,s[i]);y0=Math.min(y0,s[i+1]);y1=Math.max(y1,s[i+1]);}
        float k=width/(x1-x0);float[] p=new float[s.length];
        for(int i=0;i<s.length;i+=2){p[i]=cx+(s[i]-(x0+x1)/2)*k;p[i+1]=cy+(s[i+1]-(y0+y1)/2)*k;}
        g.polygon(p,color);
    }
    /** Places labels so they never overlap; a label that cannot fit near its spot is left out. */
    static final class Labels{
        final Gfx g;final java.util.ArrayList<float[]> used=new java.util.ArrayList<>();
        Labels(Gfx g){this.g=g;}
        void taken(float x,float y,float w,float h){used.add(new float[]{x,y,w,h});}
        boolean free(float x,float y,float w,float h){
            for(float[] u:used)if(x<u[0]+u[2]&&x+w>u[0]&&y<u[1]+u[3]&&y+h>u[1])return false;
            return true;
        }
        void force(String s,float x,float y,float size,int color,int align){
            float w=g.textWidth(s,Gfx.MONO,size,.03f),x0=align==Gfx.RIGHT?x-w:align==Gfx.CENTER?x-w/2:x;
            taken(x0-2,y-size,w+4,size*1.35f);halo(g,s,x,y,size,color,align);
        }
        /** Tries centred below, above, right and left of a point (x,y is the feature). */
        boolean place(String s,float x,float y,float size,int color){
            float w=g.textWidth(s,Gfx.MONO,size,.03f),h=size*1.35f;
            float[][] spots={{x-w/2,y+size+9},{x-w/2,y-9},{x+11,y+size/2},{x-11-w,y+size/2},{x-w/2,y+size*2+12},{x-w/2,y-size-12}};
            for(float[] p:spots)if(free(p[0]-2,p[1]-size,w+4,h)){taken(p[0]-2,p[1]-size,w+4,h);halo(g,s,p[0],p[1],size,color,Gfx.LEFT);return true;}
            return false;
        }
    }
    static void halo(Gfx g,String s,float x,float y,float size,int color,int align){
        float d=1.1f;
        g.text(s,x-d,y,Gfx.MONO,size,HALO,align,.03f);g.text(s,x+d,y,Gfx.MONO,size,HALO,align,.03f);
        g.text(s,x,y-d,Gfx.MONO,size,HALO,align,.03f);g.text(s,x,y+d,Gfx.MONO,size,HALO,align,.03f);
        g.text(s,x,y,Gfx.MONO,size,color,align,.03f);
    }
    static float[] tx(float[] p,float ox,float oy,float scale){
        float[] r=new float[p.length];
        for(int i=0;i<p.length;i+=2){r[i]=ox+p[i]*scale;r[i+1]=oy+p[i+1]*scale;}
        return r;
    }
    static String upper(String s){return s.toUpperCase(Locale.ROOT);}
}
