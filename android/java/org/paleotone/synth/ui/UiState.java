/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth.ui;

/**
 * Everything the screens show that is not engine state. Both layouts draw from this
 * one object, so switching Focus and Performance never changes a value.
 */
public final class UiState {
    public enum Layout{FOCUS,PERFORMANCE}
    public enum Screen{
        EXPEDITION("Expedition"),INSTRUMENT("INSTRUMENT"),MIXER("Mixer"),LIBRARY("Library"),
        JOURNAL("Journal"),BESTIARY("Bestiary"),START("Start"),SETTINGS("Settings"),SOUND_DESIGN("INSTRUMENT"),RECORDING("Library");
        public final String label;
        Screen(String label){this.label=label;}
    }
    public static final Screen[] NAVIGATION={Screen.EXPEDITION,Screen.INSTRUMENT,Screen.MIXER,Screen.LIBRARY,
        Screen.JOURNAL,Screen.BESTIARY,Screen.START,Screen.SETTINGS};

    /** The eight notes to the conductor, in board order. */
    public static final String[] NOTE_NAMES={"DANGER","MAJESTY","DENSITY","PACE","SPACE","WILDNESS","DRIFT","SILENCE"};
    public static final String[] NOTE_ENDS={"CALM / THREAT","MODEST / MAJESTIC","SPARSE / FULL","SLOW / QUICK",
        "NEAR / FAR","STILL / LIVELY","STAY / WANDER","CONTINUOUS / GAPS"};
    public static final String[] RESPONSE_NAMES={"THREAT","AWE","PARTS","PHRASES","DEPTH","HABITAT","HARMONY","MUSIC"};

    public Layout layout=Layout.FOCUS;
    public Screen screen=Screen.INSTRUMENT;
    /** Mixer sub-page: 0 parts, 1 habitat layers. */
    public int mixerPage;
    /** The part sound design edits, 0..3. */
    public int designPart;
    /** Quick mix open over the current screen. */
    public boolean quickMix;
    /** Library: the selected sound's id, and the tab (0 sound files, 1 recordings). */
    public String librarySelected;
    public int libraryTab,libraryPage;
    /**
     * Habitat layer mutes. The engine has no layer mute, so muting sets the level to 0
     * and remembers it here; -1 means not muted.
     */
    public final int[] layerMuted={-1,-1,-1,-1,-1,-1,-1,-1};
    /** Your requests, 0..100. */
    public final int[] notes={34,62,52,38,60,18,44,55};
    /** The conductor's response, 0..1, or NaN while nothing answers it. */
    public final float[] response={Float.NaN,Float.NaN,Float.NaN,Float.NaN,Float.NaN,Float.NaN,Float.NaN,Float.NaN};
    /** Engine wording for each response (for example SLOW, FAR, 3 / 4); null shows a dash. */
    public final String[] responseText=new String[8];

    public String runName="LOWLAND STUDY",leader="WELLINGTON",flavour="CINEMATIC";
    /** Run clock in milliseconds; length 0 is Continuous. */
    public long runElapsed,runLength=30*60000L;
    /** The length the next run uses; changes made during a run apply to the next one. */
    public long nextRunLength=30*60000L;
    /** RUNNING, then ENDING during the five-second fade, then ENDED. */
    public static final int RUNNING=0,ENDING=1,ENDED=2;
    public int runPhase;
    /** When the fade began (Ui.now clock), and whether the run-end card was put away. */
    public long fadeStart;
    public boolean endDismissed;
    /** Run number shown in take names and on the end screens. */
    public int runNumber=1;
    public String notice;
    public long noticeUntil;

    /** Compact text form for app preferences. */
    public String save(){
        StringBuilder b=new StringBuilder();b.append(layout.name()).append(';').append(screen.name());
        for(int n:notes)b.append(';').append(n);
        for(int n:layerMuted)b.append(';').append(n);
        b.append(';').append(designPart).append(';').append(mixerPage);
        b.append(';').append(runElapsed).append(';').append(runLength).append(';').append(nextRunLength)
         .append(';').append(runPhase==ENDING?ENDED:runPhase).append(';').append(runNumber);
        return b.toString();
    }
    public void load(String saved){
        if(saved==null)return;
        try{
            String[] p=saved.split(";");
            layout=Layout.valueOf(p[0]);
            for(int i=0;i<8&&i+2<p.length;i++)notes[i]=Math.max(0,Math.min(100,Integer.parseInt(p[i+2])));
            for(int i=0;i<8&&i+10<p.length;i++)layerMuted[i]=Math.max(-1,Math.min(100,Integer.parseInt(p[i+10])));
            if(p.length>18)designPart=Math.max(0,Math.min(3,Integer.parseInt(p[18])));
            if(p.length>19)mixerPage=Math.max(0,Math.min(1,Integer.parseInt(p[19])));
            if(p.length>24){
                runElapsed=Math.max(0,Long.parseLong(p[20]));runLength=Math.max(0,Long.parseLong(p[21]));
                nextRunLength=Math.max(0,Long.parseLong(p[22]));runPhase=Math.max(0,Math.min(ENDED,Integer.parseInt(p[23])));
                runNumber=Math.max(1,Integer.parseInt(p[24]));endDismissed=runPhase==ENDED;
            }
            screen=Screen.valueOf(p[1]); // last: screens from older versions (the legacy panel) fall back to INSTRUMENT
        }catch(RuntimeException ignored){}
    }
}
