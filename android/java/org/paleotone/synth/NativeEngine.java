/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth;

import android.content.SharedPreferences;
import org.paleotone.synth.ui.Engine;

/** The interface's view of the native engine: a 10 Hz snapshot of the control table, and commands. */
final class NativeEngine implements Engine {
    private final MainActivity activity;
    private final String[] labels;
    private int[] state;private String[] texts;
    private int master;
    private final SharedPreferences scapePrefs;

    NativeEngine(MainActivity activity){
        this.activity=activity;labels=Native.controlLabels();
        state=Native.controls();texts=Native.controlTexts();
        scapePrefs=activity.getSharedPreferences("scape",0);
        master=activity.getPreferences(0).getInt("master",700);Native.master(master);
    }
    void refresh(){state=Native.controls();texts=Native.controlTexts();}

    public int value(int slot){return slot<labels.length?state[slot*3]:0;}
    public int min(int slot){return slot<labels.length?state[slot*3+1]:0;}
    public int max(int slot){return slot<labels.length?state[slot*3+2]:0;}
    public String text(int slot){return slot<labels.length?texts[slot]:"";}
    public void set(int slot,int value){
        Native.control(slot,value);
        if(slot<labels.length)state[slot*3]=Math.max(min(slot),Math.min(max(slot),value));
    }
    public boolean scape(int command){
        if(command==SCAPE_START_ENVIRONMENT&&value(HABITAT)==0)set(HABITAT,scapePrefs.getInt("environmentLevel",80));
        if(command==SCAPE_DRONE_ON&&value(DRONE)==0)set(DRONE,scapePrefs.getInt("droneLevel",40));
        if(command==SCAPE_PAUSE_ENVIRONMENT&&value(HABITAT)>0)scapePrefs.edit().putInt("environmentLevel",value(HABITAT)).apply();
        if(command==SCAPE_START_ENVIRONMENT||command==SCAPE_PLAY_MUSIC)activity.ensureAudio();
        boolean ok=Native.scape(command);
        if(!ok)activity.notice("Control queue busy — try again");
        return ok;
    }
    public boolean audioOn(){return activity.audioRunning();}
    public boolean musicPlaying(){return state[labels.length*3]!=0;}
    public int master(){return master;}
    public void setMaster(int value){master=value;Native.master(value);activity.getPreferences(0).edit().putInt("master",value).apply();}
    public void stopAll(){if(value(HABITAT)>0)scapePrefs.edit().putInt("environmentLevel",value(HABITAT)).apply();activity.stopAllScape();}
    public void saveSession(){activity.action(0);}
    public void action(String name){
        switch(name){
            case "audio":activity.action(3);break;
            case "midi":activity.action(2);break;
            case "kiosk":activity.maintenance();break;
            case "about":activity.action(5);break;
            default:
        }
    }
}
