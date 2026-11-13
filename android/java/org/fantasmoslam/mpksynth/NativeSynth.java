/* SPDX-License-Identifier: GPL-3.0-only */
package org.fantasmoslam.mpksynth;

import android.content.SharedPreferences;
import org.fantasmoslam.mpksynth.ui.Catalog;
import org.fantasmoslam.mpksynth.ui.Host;
import org.fantasmoslam.mpksynth.ui.Rig;

/**
 * The app's side of the native synth: it reads the model tables, sends every value the
 * screen and knobs change, and plays notes you tap on screen. When the command queue is
 * full a part is sent again in full on the next tick, so no change is lost.
 */
final class NativeSynth implements Catalog.Source,Rig.Sink,Host {
    private final MainActivity activity;
    private final SharedPreferences prefs;
    private int master;
    private final boolean[] resend=new boolean[Rig.PARTS];
    Rig rig;

    NativeSynth(MainActivity activity,SharedPreferences prefs){
        this.activity=activity;this.prefs=prefs;
        master=prefs.getInt("master",700);Synth.master(master);
    }

    // Catalog.Source
    public String[] models(){return Synth.models();}
    public String[] params(int model){return Synth.params(model);}
    public String[] banks(int model){return Synth.banks(model);}
    public String[] presets(int model){return Synth.presets(model);}
    public int[] presetValues(int model,int preset){return Synth.presetValues(model,preset);}
    public String padName(int pad){return Synth.padName(pad);}

    // Rig.Sink
    public void model(int part,int model){if(!Synth.partModel(part,model))resend[part]=true;}
    public void param(int part,int id,int value){if(!Synth.param(part,id,value))resend[part]=true;}
    /** Sends again any part a full queue dropped. */
    void retry(){
        if(rig==null)return;
        for(int p=0;p<Rig.PARTS;p++)if(resend[p]){
            resend[p]=false;
            if(!Synth.partModel(p,rig.model(p).id)){resend[p]=true;continue;}
            for(int i=0;i<rig.model(p).controls.length;i++)if(!Synth.param(p,i,rig.value(p,i))){resend[p]=true;break;}
        }
    }

    // Host
    public int master(){return master;}
    public void master(int value){master=value;Synth.master(value);prefs.edit().putInt("master",value).apply();}
    public boolean audioOn(){return activity.audioRunning();}
    public void audition(int part,int note,boolean on){
        if(on){activity.played();Synth.midi(0x90|part,note,100);}else Synth.midi(0x80|part,note,0);
    }
    public void panic(){Synth.panic();}
}
