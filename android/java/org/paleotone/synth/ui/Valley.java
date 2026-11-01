/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth.ui;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

/**
 * A made-up valley, generated from a seed: the outline, seven terrain regions, a river
 * and lake, sixteen stations along a route, and two landmarks. No real geography is
 * used. World units run 0..1000 across and 0..600 down; 120 units are one kilometre.
 * The same seed always gives the same valley, so a saved run redraws its map exactly.
 */
public final class Valley {
    public static final float WIDTH=1000,HEIGHT=600,KM=120;
    public static final int FOREST=0,SWAMP=1,PLAINS=2,VOLCANIC=3,DESERT=4,RIDGE=5,MANGROVE=6,WATER=7;
    public static final String[] KEY={"FOREST / CANOPY","SWAMP / REEDS","PLAINS / GRASS","VOLCANIC / ASH",
        "DESERT / DUNES","ROCK / RIDGE","MANGROVE / ROOTS","RIVER / LAKE"};
    /** The status-strip word for a region (LOWLAND on the boards). */
    public static final String[] HABITAT={"CANOPY","LOWLAND","GRASSLAND","ASH COUNTRY","DRYLAND","HIGHLAND","DELTA"};
    /** What hides a creature in each terrain, and the ground that takes its prints. */
    public static final String[] COVER={"trees","reeds","grass","ash","dunes","rocks","roots"};
    public static final String[] GROUND={"leaf litter","mud","dust","ash","sand","scree","silt"};

    /**
     * Terrain glyphs, from Map-Assets/Valley-0472-Overview.svg: per terrain the pattern
     * cell size, the stroke colour, and strokes as x,y point lists in cell units.
     */
    static final float[][] CELL={{30,30},{26,23},{25,22},{28,26},{40,25},{38,30},{28,26},{32,18}};
    static final int[] GLYPH_INK={0xff608267,0xff608b73,0xff73876b,0xff809075,0xff65755a,0xff85987b,0xff608b70,0xff7dbda5};
    static final float[][][] GLYPH={
        {{15,4,8,16,13,16,13,23,17,23,17,16,22,16,15,4}},
        {{8,18,8,8},{8,12,4,7},{8,15,13,8},{16,20,23,20},{2,22,11,22}},
        {{5,16,7,12,9,16},{19,6,20,6}},
        {{4,19,12,6,22,19,4,19},{12,6,10,19},{18,23,23,21}},
        {{-4,14,0.11f,10.39f,4.44f,8.22f,9,7.5f,13.78f,8.22f,18.78f,10.39f,24,14,29.14f,17.61f,33.89f,19.78f,38.25f,20.5f,42.22f,19.78f,45.81f,17.61f,49,14},
         {5,23,7.97f,20.47f,10.89f,18.89f,13.75f,18.25f,16.56f,18.56f,19.31f,19.81f,22,22}},
        {{2,28,19,3,35,28},{8,27,19,12,29,27},{19,3,19,12}},
        {{14,5,14,15},{14,11,7,6},{14,11,21,6},{14,15,6,23},{14,15,14,24},{14,15,23,23}},
        {{1,6,3.33f,4.89f,5.67f,4.22f,8,4,10.33f,4.22f,12.67f,4.89f,15,6,17.33f,7.11f,19.67f,7.78f,22,8,24.33f,7.78f,26.67f,7.11f,29,6},
         {10,15,12.33f,13.89f,14.67f,13.22f,17,13,19.33f,13.22f,21.67f,13.89f,24,15}}};

    public static final class Region{public int terrain;public String name;public float[] poly;public float labelX,labelY;public float seedX,seedY;}
    public static final class Place{public final float x,y;public final String name;Place(float x,float y,String name){this.x=x;this.y=y;this.name=name;}}

    public final int seed;
    public final float[] outline;
    public final Region[] regions;
    /** River centre line, already trimmed to the valley. */
    public final float[] river;
    public final float[] lake,pond;
    public final String riverName,lakeName;
    public final float riverLabelX,riverLabelY,lakeLabelX,lakeLabelY;
    public final Place[] stations,landmarks;
    /** The outline's bounding box. */
    public final float minX,minY,maxX,maxY;

    private static final String[][] NOUNS={{"CANOPY","WOOD","PINES"},{"FEN","MARSH","MIRE"},{"PLAIN","MEADOW","FLATS"},
        {"ASH FIELDS","CINDERS","SCORIA"},{"BASIN","DUNES","SANDS"},{"RIDGE","CRAGS","SCARP"},{"DELTA","ROOTS","TANGLE"}};
    private static final String[] ADJECTIVES={"FERN","REED","GLASS","DRY","SALT","AMBER","IRON",
        "MOSS","CEDAR","HOLLOW","BRIGHT","LONG","STILL","RED","GREY","OLD","HIGH","LOW","BROKEN","QUIET"};
    private static final String[] STATION_NAMES={"REED CROSSING","RIDGE POST","FERN HOLLOW","ASH STEP","LONG FORD","HERON BANK",
        "STONE WELL","CEDAR CAMP","DRY WASH","ROOT BRIDGE","SALT LICK","OWL ROCK","MOSS STEPS","LOOKOUT","SPRING HEAD",
        "LOW FORD","WIND GAP","CLAY BANK","THREE PINES","SHALE SHELF","FIG CAMP","CROSSWAYS","EMBER RISE","NARROWS"};
    private static final String[] LANDMARK_NAMES={"OBSIDIAN ARCH","STANDING STONES","BROKEN TOOTH","THE NEEDLE","GREAT FIG",
        "HOT SPRING","SPLIT BOULDER","FALLEN GIANT"};
    private static final String[] WATER_WORDS={"LANTERN","MIRROR","HERON","STILL","COLD","GLASS","DEEP","BLACK"};
    private static final String[] RIVER_WORDS={"SILVER","SLOW","BRIGHT","CLAY","WINDING","GREEN","TALL"};

    public Valley(int seed){
        this.seed=seed;
        Random r=new Random(0x9a1e0L*31+seed);
        // outline: an irregular ring around the centre
        int n=14;outline=new float[n*2];
        float phase=r.nextFloat()*6.28f;
        for(int i=0;i<n;i++){
            double a=i*Math.PI*2/n+(r.nextFloat()-.5f)*.25f;
            float k=.84f+r.nextFloat()*.2f+.05f*(float)Math.sin(a*2+phase);
            outline[i*2]=500+(float)Math.cos(a)*450*k;
            outline[i*2+1]=300+(float)Math.sin(a)*262*k;
        }
        float a0x=Float.MAX_VALUE,a0y=Float.MAX_VALUE,a1x=-Float.MAX_VALUE,a1y=-Float.MAX_VALUE;
        for(int i=0;i<n;i++){a0x=Math.min(a0x,outline[i*2]);a1x=Math.max(a1x,outline[i*2]);a0y=Math.min(a0y,outline[i*2+1]);a1y=Math.max(a1y,outline[i*2+1]);}
        minX=a0x;minY=a0y;maxX=a1x;maxY=a1y;
        // regions: Voronoi cells of seven seeds, one per land terrain
        int[] order={FOREST,SWAMP,PLAINS,VOLCANIC,DESERT,RIDGE,MANGROVE};
        for(int i=order.length-1;i>0;i--){int j=r.nextInt(i+1),t=order[i];order[i]=order[j];order[j]=t;}
        float[] sx=new float[7],sy=new float[7];
        for(int i=0,tries=0;i<7;tries++){
            float x=80+r.nextFloat()*840,y=70+r.nextFloat()*460;
            if(!inside(outline,x,y))continue;
            boolean ok=true;for(int j=0;j<i;j++)if(Math.hypot(x-sx[j],y-sy[j])<(tries>4000?60:190))ok=false;
            if(ok){sx[i]=x;sy[i]=y;i++;}
        }
        float[] box={-20,-20,1020,-20,1020,620,-20,620};
        regions=new Region[7];
        ArrayList<String> used=new ArrayList<>();
        for(int i=0;i<7;i++){
            float[] poly=box;
            for(int j=0;j<7&&poly.length>=6;j++)if(j!=i)poly=clipHalf(poly,sx[i],sy[i],sx[j],sy[j]);
            Region g=new Region();g.terrain=order[i];g.poly=poly;g.seedX=sx[i];g.seedY=sy[i];
            float[] c=centroidInside(poly);g.labelX=c[0];g.labelY=c[1];
            String adjective;
            do{
                adjective=ADJECTIVES[r.nextInt(ADJECTIVES.length)];
                if(r.nextFloat()<.3f){
                    float dx=g.labelX-500,dy=(g.labelY-300)*1.6f;
                    if(Math.max(Math.abs(dx),Math.abs(dy))>150)adjective=Math.abs(dx)>Math.abs(dy)?(dx<0?"WEST":"EAST"):(dy<0?"NORTH":"SOUTH");
                }
            }while(used.contains(adjective));
            used.add(adjective);g.name=adjective+" "+NOUNS[g.terrain][r.nextInt(3)];regions[i]=g;
        }
        // river: top edge to bottom edge, wandering
        float x0=320+r.nextFloat()*360,x1=320+r.nextFloat()*360,amp=40+r.nextFloat()*50,f=1.2f+r.nextFloat()*1.3f,ph=r.nextFloat()*6.28f;
        ArrayList<float[]> raw=new ArrayList<>();
        for(int i=0;i<=13;i++){
            float t=i/13f,y=-30+t*660;
            raw.add(new float[]{x0+(x1-x0)*t+amp*(float)Math.sin(t*Math.PI*f+ph)+(r.nextFloat()-.5f)*24,y});
        }
        river=trim(smooth(raw,5),outline);
        riverName=RIVER_WORDS[r.nextInt(RIVER_WORDS.length)]+" RIVER";
        // the route: base camp near one edge, the far gate on the other side
        double a0=r.nextFloat()*Math.PI*2;
        float[] start=edgePoint(a0,.72f),end=edgePoint(a0+Math.PI+(r.nextFloat()-.5f)*.7f,.74f);
        stations=new Place[16];
        ArrayList<String> names=new ArrayList<>();for(String s:STATION_NAMES)names.add(s);
        float nx=-(end[1]-start[1]),ny=end[0]-start[0];float nl=(float)Math.hypot(nx,ny);nx/=nl;ny/=nl;
        float wobble=r.nextFloat()*6.28f;
        for(int i=0;i<16;i++){
            float t=i/15f;
            float off=i==0||i==15?0:(float)Math.sin(t*Math.PI*1.6f+wobble)*55+(r.nextFloat()-.5f)*40;
            float x=start[0]+(end[0]-start[0])*t+nx*off,y=start[1]+(end[1]-start[1])*t+ny*off;
            for(int k=0;k<40&&!inside(outline,x,y);k++){
                x+=(500-x)*.04f+nx*6;y+=(300-y)*.04f+ny*6;
            }
            String name=i==0?"BASE CAMP":i==15?gateName(end):names.remove(r.nextInt(names.size()));
            stations[i]=new Place(x,y,name);
        }
        // beside the river, at the spot furthest from the route
        float[] route=new float[stations.length*2];
        for(int i=0;i<stations.length;i++){route[i*2]=stations[i].x;route[i*2+1]=stations[i].y;}
        float lx=500,ly=300,side=1,best=-1;
        int pts=river.length/2;
        for(int k=0;k<24;k++){
            int li=pts/5+r.nextInt(Math.max(1,pts*3/5));float sd=r.nextBoolean()?1:-1;
            float cx=river[li*2]+sd*(62+r.nextFloat()*20),cy=river[li*2+1]+(r.nextFloat()-.5f)*30;
            if(!inside(outline,cx,cy))continue;
            float d=distanceToLine(route,cx,cy);
            if(d>best){best=d;lx=cx;ly=cy;side=sd;}
        }
        float lr=Math.max(30,Math.min(64,best-16));
        lake=blob(r,lx,ly,lr,lr*.63f,8);
        lakeName=WATER_WORDS[r.nextInt(WATER_WORDS.length)]+(r.nextBoolean()?" LAKE":" MERE");
        lakeLabelX=lx;lakeLabelY=ly+4;
        float rlx=river[0],rly=river[1],rbest=-1;
        for(int i=pts/8;i<pts*7/8;i++){
            float cx=river[i*2]+(side>0?-50:50),cy=river[i*2+1];
            float d=Math.min(distanceToLine(route,cx,cy),(float)Math.hypot(cx-lx,cy-ly)-lr);
            if(inside(outline,cx,cy)&&d>rbest){rbest=d;rlx=cx;rly=cy;}
        }
        riverLabelX=rlx;riverLabelY=rly;
        // a pond away from the river
        float px=0,py=0;
        for(int tries=0;tries<200;tries++){
            px=150+r.nextFloat()*700;py=110+r.nextFloat()*380;
            if(inside(outline,px,py)&&distanceToLine(river,px,py)>110&&Math.hypot(px-lx,py-ly)>140&&distanceToLine(route,px,py)>45)break;
        }
        pond=blob(r,px,py,22,13,5);
        // two landmarks off the route
        landmarks=new Place[2];
        ArrayList<String> lm=new ArrayList<>();for(String s:LANDMARK_NAMES)lm.add(s);
        for(int i=0;i<2;i++){
            float x=0,y=0;
            for(int tries=0;tries<300;tries++){
                x=120+r.nextFloat()*760;y=90+r.nextFloat()*420;
                if(inside(outline,x,y)&&!inside(lake,x,y)&&distanceToLine(river,x,y)>40&&distanceToStations(x,y)>70
                    &&(i==0||Math.hypot(x-landmarks[0].x,y-landmarks[0].y)>200))break;
            }
            landmarks[i]=new Place(x,y,lm.remove(r.nextInt(lm.size())));
        }
    }

    public String number(){return String.format(Locale.ROOT,"%04d",seed);}

    private static String gateName(float[] p){
        float dx=p[0]-500,dy=p[1]-300;
        if(Math.abs(dx)>Math.abs(dy)*1.6f)return dx<0?"WEST GATE":"EAST GATE";
        return dy<0?"NORTH GATE":"SOUTH GATE";
    }
    private float[] edgePoint(double a,float k){
        float x=500,y=300;
        for(int i=0;i<200;i++){float nx=x+(float)Math.cos(a)*3,ny=y+(float)Math.sin(a)*3;if(!inside(outline,nx,ny))break;x=nx;y=ny;}
        return new float[]{500+(x-500)*k,300+(y-300)*k};
    }
    private float distanceToStations(float x,float y){
        float d=1e9f;for(Place p:stations)d=Math.min(d,(float)Math.hypot(x-p.x,y-p.y));return d;
    }

    /** Index of the region at a point, or -1 outside the valley. */
    public int regionAt(float x,float y){
        if(!inside(outline,x,y))return -1;
        int best=0;float d=Float.MAX_VALUE;
        for(int i=0;i<regions.length;i++){float dd=(x-regions[i].seedX)*(x-regions[i].seedX)+(y-regions[i].seedY)*(y-regions[i].seedY);if(dd<d){d=dd;best=i;}}
        return best;
    }
    public int terrainAt(float x,float y){int i=regionAt(x,y);return i<0?PLAINS:regions[i].terrain;}

    // ---- geometry helpers ----------------------------------------------------------------
    public static boolean inside(float[] p,float x,float y){
        boolean in=false;
        for(int i=0,j=p.length/2-1;i<p.length/2;j=i++){
            float xi=p[i*2],yi=p[i*2+1],xj=p[j*2],yj=p[j*2+1];
            if((yi>y)!=(yj>y)&&x<(xj-xi)*(y-yi)/(yj-yi)+xi)in=!in;
        }
        return in;
    }
    /** Keeps the part of a polygon closer to (ax,ay) than to (bx,by). */
    private static float[] clipHalf(float[] p,float ax,float ay,float bx,float by){
        float mx=(ax+bx)/2,my=(ay+by)/2,dx=bx-ax,dy=by-ay;
        ArrayList<Float> out=new ArrayList<>();int n=p.length/2;
        for(int i=0;i<n;i++){
            float x1=p[i*2],y1=p[i*2+1],x2=p[(i+1)%n*2],y2=p[(i+1)%n*2+1];
            float s1=(x1-mx)*dx+(y1-my)*dy,s2=(x2-mx)*dx+(y2-my)*dy;
            if(s1<=0){out.add(x1);out.add(y1);}
            if((s1<=0)!=(s2<=0)){float t=s1/(s1-s2);out.add(x1+(x2-x1)*t);out.add(y1+(y2-y1)*t);}
        }
        float[] r=new float[out.size()];for(int i=0;i<r.length;i++)r[i]=out.get(i);return r;
    }
    /** The centre of the polygon's part inside the valley, sampled on a grid. */
    private float[] centroidInside(float[] poly){
        float sx=0,sy=0;int c=0;
        for(float y=10;y<600;y+=12)for(float x=10;x<1000;x+=12)if(inside(poly,x,y)&&inside(outline,x,y)){sx+=x;sy+=y;c++;}
        if(c==0)return new float[]{poly[0],poly[1]};
        return new float[]{sx/c,sy/c};
    }
    private static float[] blob(Random r,float cx,float cy,float rx,float ry,int n){
        float[] p=new float[n*2];
        for(int i=0;i<n;i++){double a=i*Math.PI*2/n;float k=.8f+r.nextFloat()*.35f;p[i*2]=cx+(float)Math.cos(a)*rx*k;p[i*2+1]=cy+(float)Math.sin(a)*ry*k;}
        return p;
    }
    private static ArrayList<float[]> smooth(ArrayList<float[]> pts,int steps){
        ArrayList<float[]> out=new ArrayList<>();
        for(int i=0;i<pts.size()-1;i++){
            float[] p0=pts.get(Math.max(0,i-1)),p1=pts.get(i),p2=pts.get(i+1),p3=pts.get(Math.min(pts.size()-1,i+2));
            for(int k=0;k<steps;k++){
                float t=k/(float)steps,t2=t*t,t3=t2*t;
                float x=.5f*(2*p1[0]+(-p0[0]+p2[0])*t+(2*p0[0]-5*p1[0]+4*p2[0]-p3[0])*t2+(-p0[0]+3*p1[0]-3*p2[0]+p3[0])*t3);
                float y=.5f*(2*p1[1]+(-p0[1]+p2[1])*t+(2*p0[1]-5*p1[1]+4*p2[1]-p3[1])*t2+(-p0[1]+3*p1[1]-3*p2[1]+p3[1])*t3);
                out.add(new float[]{x,y});
            }
        }
        out.add(pts.get(pts.size()-1));
        return out;
    }
    /** The run of a polyline inside a polygon, cut exactly at the boundary. */
    private static float[] trim(ArrayList<float[]> pts,float[] poly){
        ArrayList<Float> out=new ArrayList<>();boolean was=false;
        for(int i=0;i<pts.size();i++){
            float[] p=pts.get(i);boolean in=inside(poly,p[0],p[1]);
            if(i>0&&in!=was){
                float[] a=pts.get(i-1),b=p;float lo=0,hi=1;
                for(int k=0;k<20;k++){float m=(lo+hi)/2;boolean mi=inside(poly,a[0]+(b[0]-a[0])*m,a[1]+(b[1]-a[1])*m);if(mi==was)lo=m;else hi=m;}
                float m=was?lo:hi;out.add(a[0]+(b[0]-a[0])*m);out.add(a[1]+(b[1]-a[1])*m);
                if(!in&&!out.isEmpty())break;
            }
            if(in){out.add(p[0]);out.add(p[1]);}
            was=in;
        }
        float[] r=new float[out.size()];for(int i=0;i<r.length;i++)r[i]=out.get(i);return r;
    }
    public static float distanceToLine(float[] line,float x,float y){
        float d=Float.MAX_VALUE;
        for(int i=0;i+3<line.length;i+=2){
            float ax=line[i],ay=line[i+1],bx=line[i+2],by=line[i+3],dx=bx-ax,dy=by-ay;
            float l=dx*dx+dy*dy,t=l==0?0:Math.max(0,Math.min(1,((x-ax)*dx+(y-ay)*dy)/l));
            d=Math.min(d,(float)Math.hypot(x-ax-dx*t,y-ay-dy*t));
        }
        return d;
    }
}
