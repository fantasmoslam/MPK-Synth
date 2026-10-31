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
        JOURNAL("Journal"),BESTIARY("Bestiary"),START("Start"),SETTINGS("Settings"),LEGACY("Legacy panel");
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
    /** Mixer sub-page: 0 tracks, 1 Scape layers. */
    public int mixerPage;
    /** Your requests, 0..100. */
    public final int[] notes={34,62,52,38,60,18,44,55};
    /** The conductor's response, 0..1, or NaN while nothing answers it. */
    public final float[] response={Float.NaN,Float.NaN,Float.NaN,Float.NaN,Float.NaN,Float.NaN,Float.NaN,Float.NaN};
    /** Engine wording for each response (for example SLOW, FAR, 3 / 4); null shows a dash. */
    public final String[] responseText=new String[8];

    public String runName="LOWLAND STUDY",leader="WELLINGTON",flavour="CINEMATIC";
    /** Run clock in milliseconds; length 0 is Continuous. */
    public long runElapsed,runLength;
    public String notice;
    public long noticeUntil;

    /** Compact text form for app preferences. */
    public String save(){
        StringBuilder b=new StringBuilder();b.append(layout.name()).append(';').append(screen.name());
        for(int n:notes)b.append(';').append(n);
        return b.toString();
    }
    public void load(String saved){
        if(saved==null)return;
        try{
            String[] p=saved.split(";");
            layout=Layout.valueOf(p[0]);screen=Screen.valueOf(p[1]);
            for(int i=0;i<8&&i+2<p.length;i++)notes[i]=Math.max(0,Math.min(100,Integer.parseInt(p[i+2])));
        }catch(RuntimeException ignored){}
    }
}
