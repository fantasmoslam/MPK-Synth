/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth.ui;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

/**
 * The expedition the screens show: where the company is in the valley, the herds,
 * the current encounter, the field journal and the bestiary.
 *
 * This is a seeded stand-in. The conductor's own expedition simulation has not been
 * ported to the tablet yet, so this model moves the company from station to station
 * while the run clock counts (or when you choose Move on), and raises encounters from
 * a fixed catalogue. Everything follows from the valley seed and the choices made, so a
 * saved run comes back the same. The eight actions are your requests; the stand-in
 * answers them simply until the conductor can.
 */
public final class Expedition {
    public static final String[] ACTIONS={"WAIT","MOVE ON","FOLLOW HERD","SHELTER","INTERACT","DEFEND","OBSERVE","TRACK"};
    public static final int WAIT=0,MOVE_ON=1,FOLLOW_HERD=2,SHELTER=3,INTERACT=4,DEFEND=5,OBSERVE=6,TRACK=7;
    public static final String[] STAGES={"Signs","Glimpse","Reveal","Naming","Actions","Outcome","Feeling","Coda"};
    public static final String[] TIMES={"MORNING","MIDDAY","DUSK"};
    static final String[] WEATHER={"CLEAR","LIGHT RAIN","MIST","WIND","HEAT","OVERCAST"};
    static final String[] DAYS={"FIRST","SECOND","THIRD","FOURTH","FIFTH","SIXTH"};

    // ---- creatures ---------------------------------------------------------------------
    public static final int SAUROPOD=0,THEROPOD=1,ARMOURED=2,HORNED=3,RUNNER=4,FLYER=5,SWIMMER=6;
    public static final String[] GROUPS={"Giant","Hunter","Armoured","Horned and crested","Small and swift","Flyer","Water hunter"};
    public static final class Species{
        public final String name,diet,period,habitat;public final int group;public final boolean danger;
        Species(String name,int group,boolean danger,String diet,String period,String habitat){
            this.name=name;this.group=group;this.danger=danger;this.diet=diet;this.period=period;this.habitat=habitat;}
    }
    public static final Species[] SPECIES={
        new Species("Allosaurus",THEROPOD,true,"meat eater","Late Jurassic","floodplain"),
        new Species("Tyrannosaurus",THEROPOD,true,"meat eater","Late Cretaceous","forest edge"),
        new Species("Triceratops",HORNED,false,"plant eater","Late Cretaceous","open plain"),
        new Species("Parasaurolophus",HORNED,false,"plant eater","Late Cretaceous","river bank"),
        new Species("Velociraptor",RUNNER,true,"meat eater","Late Cretaceous","dry scrub"),
        new Species("Spinosaurus",SWIMMER,true,"fish eater","Mid Cretaceous","river delta"),
        new Species("Iguanodon",HORNED,false,"plant eater","Early Cretaceous","woodland"),
        new Species("Pachycephalosaurus",HORNED,false,"plant eater","Late Cretaceous","upland"),
        new Species("Edmontosaurus",HORNED,false,"plant eater","Late Cretaceous","coastal plain"),
        new Species("Carnotaurus",THEROPOD,true,"meat eater","Late Cretaceous","dry plain"),
        new Species("Dilophosaurus",THEROPOD,true,"meat eater","Early Jurassic","riverside"),
        new Species("Protoceratops",HORNED,false,"plant eater","Late Cretaceous","desert"),
        new Species("Gallimimus",RUNNER,false,"omnivore","Late Cretaceous","open plain"),
        new Species("Stegosaurus",ARMOURED,false,"plant eater","Late Jurassic","fern prairie"),
        new Species("Ankylosaurus",ARMOURED,false,"plant eater","Late Cretaceous","forest floor"),
        new Species("Brachiosaurus",SAUROPOD,false,"plant eater","Late Jurassic","river plain"),
        new Species("Diplodocus",SAUROPOD,false,"plant eater","Late Jurassic","river plain"),
        new Species("Therizinosaurus",THEROPOD,false,"plant eater","Late Cretaceous","forest"),
        new Species("Compsognathus",RUNNER,true,"meat eater","Late Jurassic","scrub"),
        new Species("Microraptor",FLYER,false,"meat eater","Early Cretaceous","forest"),
        new Species("Oviraptor",RUNNER,false,"omnivore","Late Cretaceous","desert"),
        new Species("Pteranodon",FLYER,false,"fish eater","Late Cretaceous","coast"),
        new Species("Quetzalcoatlus",FLYER,false,"meat eater","Late Cretaceous","open plain"),
        new Species("Deinosuchus",SWIMMER,true,"meat eater","Late Cretaceous","estuary")};
    /** Which species turn up in each terrain (Valley.FOREST..MANGROVE). */
    private static final int[][] FOUND={{1,6,14,17,19,15,16},{3,8,10,5,23,16},{2,12,22,9,16,13,4},{0,9,7,13,22},
        {11,20,4,18,12},{7,13,22,0,15},{5,23,3,8,21}};

    /** Filled silhouettes, about 50 units across, centred on 0,0 (herb and pred from the map SVG). */
    static final float[][] SHAPES={
        {-12,1,-9.28f,-1.61f,-6.44f,-3.44f,-3.5f,-4.5f,-0.44f,-4.78f,2.72f,-4.28f,6,-3,10,-16,14,-18,17,-16,13,-11,11,3,7,7,6,15,2,15,1,6,-7,7,-10,15,-14,15,-13,5,-21,2,-27,4},
        {-19,-4,-6,-2,-3.36f,-4.31f,-0.78f,-5.89f,1.75f,-6.75f,4.22f,-6.89f,6.64f,-6.31f,9,-5,13,-12,22,-10,24,-5,15,-2,8,4,13,10,9,13,2,6,-1,13,7,20,-3,19,-7,10,-12,19,-18,19,-10,4},
        {-28,2,-20,-2,-16,-8,-13,-5,-9,-13,-5,-7,-1,-15,3,-8,7,-12,10,-5,14,-6,18,-3,22,-2,24,1,20,2,14,2,12,3,12,12,9,12,8,5,0,6,-6,6,-6,13,-10,13,-11,5,-18,3},
        {-24,4,-16,-6,-6,-9,4,-8,10,-6,12,-14,16,-12,17,-6,24,-10,21,-4,25,-1,26,3,21,4,14,5,11,6,11,13,7,13,6,7,-4,7,-8,7,-8,13,-12,13,-13,6,-20,5},
        {-19,-4,-6,-2,-3.36f,-4.31f,-0.78f,-5.89f,1.75f,-6.75f,4.22f,-6.89f,6.64f,-6.31f,9,-5,13,-12,22,-10,24,-5,15,-2,8,4,13,10,9,13,2,6,-1,13,7,20,-3,19,-7,10,-12,19,-18,19,-10,4},
        {-32,-2,-16,-8,-4,-3,0,-6,3,-12,4,-6,16,-8,32,-2,14,-2,4,2,2,8,-2,2,-14,-2},
        {-32,2,-14,-2,-2,-4,12,-3,22,-2,32,0,22,2,12,3,8,3,10,8,6,8,4,4,-8,4,-10,4,-8,9,-12,9,-14,3}};
    public static float[] shape(int species){return SHAPES[SPECIES[species].group];}
    /** Small and swift creatures draw smaller. */
    public static float shapeScale(int species){int g=SPECIES[species].group;return g==RUNNER?.75f:g==SAUROPOD?1.1f:1f;}

    // ---- state ---------------------------------------------------------------------------
    public static final class Encounter{
        public final int species,station,terrain;public int stage;
        Encounter(int species,int station,int terrain){this.species=species;this.station=station;this.terrain=terrain;}
        public boolean danger(){return SPECIES[species].danger;}
        public boolean identified(){return stage>=2;}
    }
    public static final class Herd{public float x,y,homeX,homeY;public int species,count;}
    public static final class Entry{
        public String title,place,header,quote,quoteBy;public String[] paragraphs;public int species=-1,station,day;
    }

    public final Valley valley;
    public final String leader;
    public int station;
    /** Your latest request, -1 for none. */
    public int requested=-1;
    public final ArrayList<Encounter> encounters=new ArrayList<>();
    public final Herd[] herds;
    public String log="The expedition chooses its way through the valley. Your notes influence its decisions.";
    private long stationClock,stageClock,herdClock;

    public Expedition(int seed,String leader){
        valley=new Valley(seed);this.leader=leader;
        Random r=new Random(seed*7919L+17);
        herds=new Herd[3];
        for(int i=0;i<3;i++){
            Herd h=new Herd();
            Valley.Place near=valley.stations[3+i*4+r.nextInt(3)];
            for(int k=0;k<200;k++){
                h.x=near.x+(r.nextFloat()-.5f)*260;h.y=near.y+(r.nextFloat()-.5f)*180;
                if(!Valley.inside(valley.outline,h.x,h.y)||Valley.inside(valley.lake,h.x,h.y))continue;
                if(Valley.distanceToLine(valley.river,h.x,h.y)<35)continue;
                boolean clear=true;
                for(Valley.Place p:valley.stations)if(Math.hypot(p.x-h.x,p.y-h.y)<55)clear=false;
                for(int j=0;j<i;j++)if(Math.hypot(herds[j].homeX-h.x,herds[j].homeY-h.y)<150)clear=false;
                if(clear)break;
            }
            h.homeX=h.x;h.homeY=h.y;
            int[] pool=FOUND[valley.terrainAt(h.x,h.y)];
            if(i==2){h.species=pick(pool,r,true);h.count=1;}
            else{h.species=pick(pool,r,false);h.count=3+r.nextInt(6);}
            herds[i]=h;
        }
    }
    private static int pick(int[] pool,Random r,boolean danger){
        for(int k=0;k<40;k++){int s=pool[r.nextInt(pool.length)];if(SPECIES[s].danger==danger&&SPECIES[s].group!=FLYER)return s;}
        return danger?0:16;
    }

    public Valley.Place place(){return valley.stations[station];}
    public int day(){return 1+station/3;}
    public String time(){return TIMES[station%3];}
    public String weather(){return WEATHER[new Random(valley.seed*31L+day()).nextInt(WEATHER.length)];}
    public int terrain(){Valley.Place p=place();return valley.terrainAt(p.x,p.y);}
    public String habitat(){return Valley.HABITAT[terrain()];}
    public String stationLabel(int i){return String.format(Locale.ROOT,"%02d / %s",i+1,valley.stations[i].name);}
    public Encounter current(){
        if(encounters.isEmpty())return null;
        Encounter e=encounters.get(encounters.size()-1);
        return e.station==station&&e.stage<STAGES.length?e:null;
    }
    public boolean finished(){return station>=valley.stations.length-1;}

    /** Herd and predator positions drift slowly around where they were first seen. */
    public void tick(long dt,boolean live,long runLength){
        herdClock+=dt;
        float t=herdClock/1000f;
        for(int i=0;i<herds.length;i++){
            Herd h=herds[i];
            h.x=h.homeX+(float)Math.sin(t*.011f+i*2.1f)*26;h.y=h.homeY+(float)Math.cos(t*.008f+i*1.3f)*16;
        }
        if(!live)return;
        Encounter e=current();
        if(e!=null){stageClock+=dt;if(stageClock>=20000){stageClock=0;advanceStage(e);}return;}
        stationClock+=dt;
        long per=runLength>0?Math.max(30000,runLength/15):120000;
        if(stationClock>=per){stationClock=0;arrive();}
    }

    /** Your request to the company. The stand-in answers it at once. */
    public void act(int action){
        requested=action;
        Encounter e=current();
        switch(action){
            case MOVE_ON:
                if(e!=null&&e.stage<5&&e.danger()){log="The way is blocked. The company waits for the animal to move.";break;}
                if(e!=null)e.stage=STAGES.length;
                arrive();break;
            case FOLLOW_HERD:{
                Herd h=nearestHerd(false);
                if(e==null&&h!=null){begin(h.species);log="The company follows the herd towards "+valley.stations[Math.min(station,15)].name.toLowerCase(Locale.ROOT)+".";}
                else if(e!=null)advanceStage(e);
                break;}
            case SHELTER:case DEFEND:
                if(e!=null){if(e.stage<5)e.stage=5;else advanceStage(e);}
                else log=action==SHELTER?"The company shelters and listens. Nothing comes.":"The company closes ranks. The valley stays quiet.";
                break;
            case TRACK:
                if(e==null){Herd h=nearestHerd(true);if(h!=null)begin(h.species);}
                else advanceStage(e);
                break;
            case WAIT:case INTERACT:case OBSERVE:
                if(e!=null)advanceStage(e);
                else log=action==WAIT?"The company rests at "+title(place().name)+".":"Nothing stirs near "+title(place().name)+" yet.";
                break;
            default:
        }
        stageClock=0;
    }
    private Herd nearestHerd(boolean includePredator){
        Herd best=null;float d=Float.MAX_VALUE;Valley.Place p=place();
        for(Herd h:herds){if(!includePredator&&SPECIES[h.species].danger)continue;float dd=(float)Math.hypot(h.x-p.x,h.y-p.y);if(dd<d){d=dd;best=h;}}
        return best;
    }
    private void advanceStage(Encounter e){
        e.stage++;
        if(e.stage>=STAGES.length)log="The encounter at "+title(place().name)+" is in the journal.";
    }
    private void arrive(){
        if(finished()){log="The company has reached "+title(place().name)+", the end of the route.";return;}
        station++;stationClock=0;
        log="Arrived at "+stationLabel(station)+".";
        Random r=new Random(valley.seed*131L+station*977L);
        if(station>1&&r.nextFloat()<.5f){
            int[] pool=FOUND[terrain()];begin(pool[r.nextInt(pool.length)]);
        }
    }
    private void begin(int species){
        Encounter old=current();if(old!=null)old.stage=STAGES.length;
        encounters.add(new Encounter(species,station,terrain()));stageClock=0;
        log="Something moves near "+title(place().name)+".";
    }

    // ---- bestiary --------------------------------------------------------------------------
    /** The encounter that first recorded a species (identified), or -1. */
    public int firstEncounter(int species){
        for(int i=0;i<encounters.size();i++){Encounter e=encounters.get(i);if(e.species==species&&e.identified())return i;}
        return -1;
    }
    public int discovered(){int n=0;for(int i=0;i<SPECIES.length;i++)if(firstEncounter(i)>=0)n++;return n;}

    // ---- encounter wording ---------------------------------------------------------------
    public String encounterTitle(Encounter e){
        return e.identified()?SPECIES[e.species].name:"Something in the "+Valley.COVER[e.terrain];
    }
    public String encounterCaption(Encounter e){
        return (e.identified()?(e.danger()?"DANGER":"MAJESTY"):"UNIDENTIFIED")+" / "+STAGES[Math.min(e.stage,7)].toUpperCase(Locale.ROOT);
    }
    public String stageLine(Encounter e,int stage){
        Species s=SPECIES[e.species];String cover=Valley.COVER[e.terrain],ground=Valley.GROUND[e.terrain];
        switch(stage){
            case 0:return "Something moves in the "+cover+". Heavy prints interrupt the "+ground+". There is no visual contact yet.";
            case 1:return "A shape passes behind the "+cover+". It is larger than anyone wants to say out loud.";
            case 2:return s.name+". The company sees it plainly now, and nobody looks away.";
            case 3:return naming(s,cover);
            case 4:return s.danger?"The trail is blocked. Shelter, defend or wait: the company looks to you."
                                  :"Nobody moves. Your notes now decide how the company answers.";
            case 5:return s.danger?"It turns away into the "+cover+". The trail is clear, for now."
                                  :"The animals move on at their own pace. The way ahead is open.";
            case 6:return s.danger?"Hands are still shaking when the packs go back on."
                                  :"Someone laughs, quietly, at nothing. The light has changed.";
            default:return title(valley.stations[e.station].name)+" is quiet again. The expedition takes up the trail.";
        }
    }
    private static String naming(Species s,String cover){
        switch(s.group){
            case SAUROPOD:return "A long neck rises above the "+cover+". For a moment the whole company is still.";
            case THEROPOD:return s.danger?"The "+cover+" part. "+s.name+" stands across the trail. It has seen the expedition."
                                        :"Long claws hook a branch down. "+s.name+" feeds, and does not look round.";
            case ARMOURED:return "Plates and spikes catch the light. "+s.name+" does not hurry for anyone.";
            case HORNED:return "Heads come up together. "+s.name+" watches the company along the whole length of the "+cover+".";
            case RUNNER:return s.danger?"Quick shapes keep pace with the company. "+s.name+", more than one."
                                      :"A bright line of "+s.name+" crosses in front of the company, all legs and turning heads.";
            case FLYER:return "A shadow crosses the ground. "+s.name+" turns once over the valley on still wings.";
            default:return "Two eyes and a long dark back, just under the surface. "+s.name+" does not blink.";
        }
    }
    /** The short line on the Expedition card. */
    public String cardLine(Encounter e){
        Species s=SPECIES[e.species];
        if(e.stage<2)return "Something moves in the "+Valley.COVER[e.terrain]+".";
        if(e.stage>=5)return s.danger?"It has turned away. The trail is clear.":"The animals move on. The way is open.";
        switch(s.group){
            case SAUROPOD:return "The treeline moves. A long neck rises above it.";
            case THEROPOD:return s.danger?"It stands across the trail, watching.":"It feeds, and does not look round.";
            case ARMOURED:return "A slow, armoured shape crosses the path.";
            case HORNED:return "Heads come up together, watching.";
            case RUNNER:return "Quick shapes in the "+Valley.COVER[e.terrain]+".";
            case FLYER:return "A shadow turns over the valley.";
            default:return "Something lies just under the water.";
        }
    }

    // ---- journal -----------------------------------------------------------------------------
    private static final String[] COMPANIONS={"Halloran","Miss Okafor","Ms. Lucy"};
    private static final String[][] TITLES={{"The Long Crossing","Necks Above the Trees"},{"Across the Trail","The Watcher"},
        {"Plates in the Ferns","The Slow Walker"},{"The Standoff","Heads Up Together"},{"Swift Shapes","The Running Line"},
        {"Shadow Over the Valley","Still Wings"},{"Still Water","Eyes in the Shallows"}};

    /** Field journal: base camp, each night camp and each encounter, oldest first. */
    public ArrayList<Entry> journal(){
        ArrayList<Entry> list=new ArrayList<>();
        int next=0;
        for(int st=0;st<=station;st++){
            if(st==0)list.add(baseCamp());
            else if(st%3==0)list.add(nightCamp(st));
            while(next<encounters.size()&&encounters.get(next).station==st){
                Encounter e=encounters.get(next);
                if(e.identified())list.add(encounterEntry(e,next));
                next++;
            }
        }
        return list;
    }
    /** Journal page index for an encounter. */
    public int journalIndex(int encounter){
        ArrayList<Entry> j=journal();Encounter e=encounters.get(encounter);
        for(int i=0;i<j.size();i++)if(j.get(i).species==e.species&&j.get(i).station==e.station)return i;
        return j.size()-1;
    }
    private String header(int st){return (leaderName().toUpperCase(Locale.ROOT)+" / "+DAYS[Math.min(5,st/3)]+" DAY / "+TIMES[st%3]);}
    private String leaderName(){
        String l=leader.toUpperCase(Locale.ROOT);
        return l.equals("WELLINGTON")?"Dr. Wellington":l.equals("VERONICA")?"Miss Veronica":title(leader);
    }
    private String placeLine(int st){
        Valley.Place p=valley.stations[st];int t=valley.terrainAt(p.x,p.y);
        String where=Valley.distanceToLine(valley.river,p.x,p.y)<60?"River bank":
            Math.hypot(p.x-valley.lakeLabelX,p.y-valley.lakeLabelY)<110?"Lake shore":title(p.name);
        return title(Valley.HABITAT[t])+" valley / "+where;
    }
    private Entry baseCamp(){
        Entry e=new Entry();e.station=0;e.day=1;e.title="Base Camp";e.place=placeLine(0);e.header=header(0);
        Valley.Place gate=valley.stations[valley.stations.length-1];
        e.paragraphs=new String[]{
            "We set up base camp at first light. The valley is wider than the survey promised, and quieter. "+COMPANIONS[1]+" counted the water skins twice and then a third time, which I have decided to take as a good sign.",
            "From the low rise above camp the land runs on towards "+title(gate.name)+". We will take the route as the valley allows it, and write down what we see. Nobody has walked it before us, as far as any of us can tell."};
        e.quote="“Sixteen stations, if the weather holds. Fewer if it doesn't.”";e.quoteBy=COMPANIONS[0];
        return e;
    }
    private Entry nightCamp(int st){
        Entry e=new Entry();e.station=st;e.day=1+st/3;e.title="Night camp";e.place=placeLine(st);e.header=header(st-1).replace("DUSK","NIGHT");
        String cover=Valley.COVER[valley.terrainAt(valley.stations[st].x,valley.stations[st].y)];
        e.paragraphs=new String[]{
            "We made camp at "+title(valley.stations[st-1].name)+" as the light went. The fire was small and nobody wanted it bigger. Out past the "+cover+" something called twice, a long way off, and was answered.",
            COMPANIONS[(st/3)%3]+" kept the first watch. I lay awake for a while listening to the valley settle, then slept better than I have in weeks."};
        e.quote="“Whatever that was, it was not interested in us.”";e.quoteBy=COMPANIONS[(st/3+1)%3];
        return e;
    }
    private Entry encounterEntry(Encounter en,int index){
        Species s=SPECIES[en.species];
        Entry e=new Entry();e.station=en.station;e.day=1+en.station/3;e.species=en.species;
        e.title=TITLES[s.group][index%2];e.place=placeLine(en.station);e.header=header(en.station);
        String cover=Valley.COVER[en.terrain],ground=Valley.GROUND[en.terrain];
        String a=COMPANIONS[index%3],b=COMPANIONS[(index+2)%3];
        String first;
        switch(s.group){
            case SAUROPOD:first="We came upon the great animals while the light was still caught in the upper branches. At first I took the movement for wind. Then one long neck rose above the "+cover+", followed by another, and the mistake became almost comic. "+a+" stopped mid-sentence and never finished it.";break;
            case ARMOURED:first="The "+cover+" gave way to a slow, heavy shape that did not hurry for anyone. Plates stood along its back like a row of grey slates. "+a+" whispered that it had not noticed us; I am not sure it cared.";break;
            case HORNED:first="There were several of them, grazing with their backs to the wind, and the nearest raised its head to look straight at us. The horns were larger than any drawing had prepared me for. "+a+" put the survey pole down very slowly.";break;
            case RUNNER:first=s.danger?"Small shapes moved in the "+cover+", too quick to follow. They kept pace with us for the best part of a mile, never closing, never leaving. "+a+" kept a hand on the lantern pole the whole way."
                                      :"They came through the "+cover+" in a quick, bright line, all legs and turning heads, and were past us before anyone could count them. "+a+" swore they ran in step.";break;
            case FLYER:first="A shadow crossed the "+ground+" and we all looked up together. It turned once over the valley on wide, still wings, unhurried, as if the air belonged to it. "+a+" forgot to sketch until it had gone.";break;
            case SWIMMER:first="The water at "+title(valley.stations[en.station].name)+" was too still. "+a+" noticed first: a long dark shape lying just under the surface, and a pair of eyes that did not blink. We backed away from the bank without turning round.";break;
            default:first=s.danger?"The "+cover+" parted and it was simply there, across the trail, closer than any of us would have chosen. It did not roar. It looked at us, one at a time, as if counting. "+a+" said nothing, which was the right thing to say."
                                  :"We heard it before we saw it: branches bending, then a long patient tearing. It was feeding, and it did not look round once. "+a+" crept close enough to sketch the claws.";
        }
        String second=s.danger?"We held still and let it decide. The "+ground+" under my hands was warm; I remember that more clearly than anything else. After a long minute it lowered its head and turned away into the "+cover+"."
                              :"We kept to the higher ground. The animals moved below us with a patience that made our own journey seem hurried and small. I could feel each deliberate step through the soles of my boots. "+b+" made notes without once looking down.";
        String third=s.danger?"Nobody spoke until we had put a good distance behind us. "+b+" says we were never in real danger. I have written that down as well, so that I can read it later and decide whether I believe it."
                             :"When the last of them had passed we waited a little longer. There was no practical reason to remain, and yet nobody moved to shoulder a pack. I have recorded the place as "+e.title.replace("The ","the ")+". It is a poor name for what we saw, but it will help us find it again.";
        e.paragraphs=new String[]{first,second,third};
        e.quote=s.danger?"“It knew we were there long before we knew it was.”":s.group==FLYER?"“Wings like that never need to hurry.”"
            :"“Their gait was steady. We need another observation before judging the herd's direction.”";
        e.quoteBy=b;
        return e;
    }
    static String title(String s){
        StringBuilder b=new StringBuilder();boolean up=true;
        for(char c:s.toCharArray()){b.append(up?Character.toUpperCase(c):Character.toLowerCase(c));up=c==' '||c=='.'||c=='-';}
        return b.toString();
    }
    public static String roman(int n){
        String[] r={"M","CM","D","CD","C","XC","L","XL","X","IX","V","IV","I"};int[] v={1000,900,500,400,100,90,50,40,10,9,5,4,1};
        StringBuilder b=new StringBuilder();for(int i=0;i<v.length;i++)while(n>=v[i]){n-=v[i];b.append(r[i]);}return b.toString();
    }

    // ---- saved state -------------------------------------------------------------------------
    public String save(){
        StringBuilder b=new StringBuilder();b.append(station).append(',').append(requested);
        for(Encounter e:encounters)b.append(',').append(e.species).append(':').append(e.station).append(':').append(e.terrain).append(':').append(e.stage);
        return b.toString();
    }
    public void load(String saved){
        if(saved==null||saved.isEmpty())return;
        try{
            String[] p=saved.split(",");
            station=Math.max(0,Math.min(valley.stations.length-1,Integer.parseInt(p[0])));
            requested=Math.max(-1,Math.min(7,Integer.parseInt(p[1])));
            encounters.clear();
            for(int i=2;i<p.length;i++){
                String[] q=p[i].split(":");
                Encounter e=new Encounter(Math.max(0,Math.min(SPECIES.length-1,Integer.parseInt(q[0]))),
                    Math.max(0,Math.min(station,Integer.parseInt(q[1]))),Math.max(0,Math.min(6,Integer.parseInt(q[2]))));
                e.stage=Math.max(0,Math.min(STAGES.length,Integer.parseInt(q[3])));encounters.add(e);
            }
            log="Returned to "+stationLabel(station)+".";
        }catch(RuntimeException ignored){}
    }
}
