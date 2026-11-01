/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth;

import android.content.SharedPreferences;
import android.os.SystemClock;
import java.util.HashMap;
import org.paleotone.synth.ui.Engine;
import org.paleotone.synth.ui.Library;
import org.paleotone.synth.ui.Recorder;

/** The interface's view of the native engine: a 10 Hz snapshot of the control table, and commands. */
final class NativeEngine implements Engine {
    private final MainActivity activity;
    private final String[] labels;
    private int[] state;private String[] texts;
    private int master;
    private final SharedPreferences scapePrefs;
    private final AndroidLibrary library;private final AndroidRecorder recorder;
    private final Voice[] voices=new Voice[4];private final long[] voiceRead=new long[4];
    private final HashMap<Integer,String[]> names=new HashMap<>();
    private Choice[] engines;private final HashMap<Integer,Choice[]> presets=new HashMap<>();

    NativeEngine(MainActivity activity,AndroidLibrary library,AndroidRecorder recorder){
        this.activity=activity;this.library=library;this.recorder=recorder;labels=Native.controlLabels();
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
    public String[] names(int slot){
        String[] n=names.get(slot);
        if(n==null){n=Native.controlNames(slot);if(n==null)n=new String[0];names.put(slot,n);}
        return n;
    }
    public boolean scape(int command){
        if(command==SCAPE_START_ENVIRONMENT&&value(HABITAT)==0)set(HABITAT,scapePrefs.getInt("environmentLevel",80));
        if(command==SCAPE_DRONE_ON&&value(DRONE)==0)set(DRONE,scapePrefs.getInt("droneLevel",40));
        if(command==SCAPE_PAUSE_ENVIRONMENT&&value(HABITAT)>0)scapePrefs.edit().putInt("environmentLevel",value(HABITAT)).apply();
        if(command==SCAPE_START_ENVIRONMENT||command==SCAPE_PLAY_MUSIC)activity.ensureAudio();
        // Music is the new conductor; the old Scape's music stays off.
        if(command==SCAPE_PLAY_MUSIC||command==SCAPE_STOP_MUSIC){Native.paleoMusic(command==SCAPE_PLAY_MUSIC);return true;}
        boolean ok=Native.scape(command);
        if(!ok)activity.notice("Control queue busy — try again");
        return ok;
    }
    public boolean audioOn(){return activity.audioRunning();}
    /** Loading or playing; the coda after Stop music counts as stopped. */
    public boolean musicPlaying(){int s=Native.paleoState();return s==1||s==2;}
    public int master(){return master;}
    public void setMaster(int value){master=value;Native.master(value);activity.getPreferences(0).edit().putInt("master",value).apply();}
    public void stopAll(){if(value(HABITAT)>0)scapePrefs.edit().putInt("environmentLevel",value(HABITAT)).apply();activity.stopAllScape();}
    public void saveSession(){activity.action(0);}
    public Voice voice(int part){
        if(part<0||part>3)return null;
        long now=SystemClock.uptimeMillis();
        if(voices[part]!=null&&now-voiceRead[part]<100)return voices[part];
        String[] r=Native.voice(part);if(r==null||r.length<11)return voices[part];
        Param[] params=new Param[8];
        for(int k=0;k<8;k++){
            String[] f=r[3+k].split("\t",-1);if(f.length<7)continue;
            params[k]=new Param(f[0],Integer.parseInt(f[1])==F_ENUM,Integer.parseInt(f[2]),Integer.parseInt(f[3]),
                Integer.parseInt(f[4]),!f[5].equals("0"),f[6]);
        }
        voices[part]=new Voice(Integer.parseInt(r[0]),r[1],r[2],params);voiceRead[part]=now;
        return voices[part];
    }
    /** The firmware's F_ENUM descriptor format (firmware/src/core.h). */
    private static final int F_ENUM=8;
    public String[] valueNames(int part,int param){return Native.voiceNames(part,param);}
    public void setParam(int part,int param,int value){
        if(!Native.voiceSet(part,param,value))activity.notice("Control queue busy — try again");
        voiceRead[part]=0;
    }
    private static Choice[] choices(String[] rows){
        if(rows==null)return new Choice[0];
        Choice[] c=new Choice[rows.length];
        for(int i=0;i<rows.length;i++){String[] f=rows[i].split("\t",2);c[i]=new Choice(Integer.parseInt(f[0]),f.length>1?f[1]:"");}
        return c;
    }
    public Choice[] engines(){if(engines==null)engines=choices(Native.engines());return engines;}
    public Choice[] presets(int engine){
        Choice[] c=presets.get(engine);if(c==null){c=choices(Native.presets(engine));presets.put(engine,c);}
        return c;
    }
    public void setEngine(int part,int engine){
        if(!Native.voiceEngine(part,engine))activity.notice("Control queue busy — try again");
        voiceRead[part]=0;
    }
    public void setPreset(int part,int preset){
        if(!Native.voicePreset(part,preset))activity.notice("Control queue busy — try again");
        voiceRead[part]=0;
    }
    public Library library(){return library;}
    public boolean habitatEngine(){return false;}
    public boolean musicEngine(){return true;}
    public void conductorNote(int note,int value){Native.paleoNote(note,value);}
    public int conductorNoteValue(int note){return Native.paleoNoteValue(note);}
    public void conductorAction(int action){Native.paleoAction(action);}
    public float[] conductorGauges(){return Native.paleoGauges();}
    public String conductorGaugeText(int gauge){return Native.paleoGaugeText(gauge);}
    public int conductorScene(){return Native.paleoSceneNow();}
    public Recorder recorder(){return recorder;}
    public void fade(int ms){Native.fade(ms);}
    public boolean faded(){return Native.faded();}
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
