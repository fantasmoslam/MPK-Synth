/* SPDX-License-Identifier: GPL-3.0-only */
package org.fantasmoslam.mpksynth;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import org.fantasmoslam.mpksynth.ui.Catalog;
import org.fantasmoslam.mpksynth.ui.Home;
import org.fantasmoslam.mpksynth.ui.MpkRouter;
import org.fantasmoslam.mpksynth.ui.Rig;
import org.fantasmoslam.mpksynth.ui.State;

/**
 * MPK Synth: the synth sounds whenever the app is in front. Audio opens on resume and closes
 * on pause, and nothing else turns it off. Every part's sound, the parts the keys and pads
 * play, and how far you are through the lessons are saved when the app leaves.
 */
public final class MainActivity extends Activity {
    private static final String TAG="MpkSynth";
    private SynthView view;
    private NativeSynth synth;
    private MidiInput midi;
    private final State state=new State();
    private SharedPreferences prefs;
    private Rig rig;
    private AudioManager audio;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final AudioRecovery recovery=new AudioRecovery();
    private boolean resumed,running,focusHeld;
    private long lastReport;
    private static final int BUFFERS=3;

    private final AudioManager.OnAudioFocusChangeListener focus=change->{
        if(change==AudioManager.AUDIOFOCUS_GAIN){focusHeld=true;if(resumed)startAudio();}
        else if(change<0){
            stopAudio();focusHeld=false;
            notice("Another app took the audio. Play a key to bring it back");
        }
    };
    private final AudioDeviceCallback devices=new AudioDeviceCallback(){
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] changed){routeChanged(changed);}
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] changed){routeChanged(changed);}
    };
    private void routeChanged(AudioDeviceInfo[] changed){
        if(!resumed||!focusHeld)return;
        for(AudioDeviceInfo d:changed)if(d.isSink()){if(recovery.pending())recovery.begin(SystemClock.uptimeMillis());break;}
    }
    private final Runnable refresh=new Runnable(){public void run(){
        if(!resumed)return;
        long now=SystemClock.uptimeMillis();
        if(running&&Synth.error()!=0){
            Synth.stop();running=false;
            if(focusHeld){recovery.begin(now);notice("Audio output changed, reconnecting…");}
        }
        if(focusHeld&&recovery.due(now)){
            if(openAudio()){recovery.cancel();notice("Audio reconnected");}
            else{recovery.failed(now);if(recovery.exhausted()){recovery.cancel();notice("No audio output. Play a key to try again");}}
        }
        if(running&&now-lastReport>=10000){   // adb logcat -s MpkSynth
            lastReport=now;String r=Synth.report();if(r!=null)Log.i(TAG,Build.MODEL+" | "+r);
        }
        midi.parts(state.keyPart,state.padPart);
        synth.retry();
        view.tick();
        handler.postDelayed(this,33);
    }};

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        prefs=getSharedPreferences("mpksynth",MODE_PRIVATE);
        Synth.init();
        Synth.level(1);   // +6 dB with the limiter
        synth=new NativeSynth(this,prefs);
        rig=new Rig(new Catalog(synth),synth);synth.rig=rig;
        rig.restore(prefs.getString("rig",null));
        state.load(prefs.getString("parts",null));
        view=new SynthView(this,state,rig,synth);setContentView(view);
        Home home=view.home;
        home.tutorial.loadDone(prefs.getLong("lessons",0));
        int lesson=prefs.getInt("lesson",0);
        if(!prefs.getBoolean("started",false)){state.tab=State.TAB_LEARN;home.openLesson(0);}   // the first run starts with the lessons
        else home.tutorial.open(lesson);
        midi=new MidiInput(this,new MidiInput.Listener(){
            public void knob(int k,int value){home.knob(k,value,SystemClock.uptimeMillis());view.ui.invalidate();}
            public void transport(MpkRouter.Transport b,boolean pressed){
                if(pressed){home.transport(b,SystemClock.uptimeMillis());view.ui.invalidate();}
            }
            public void played(boolean pad,int note){MainActivity.this.played();home.played(pad,note,SystemClock.uptimeMillis());}
            public void joystick(int bend){home.joystick(bend,SystemClock.uptimeMillis());}
            public void connected(boolean on){
                home.connected(on,SystemClock.uptimeMillis());
                notice(on?"MPK mini connected":"MPK mini unplugged");
            }
        });
        audio.registerAudioDeviceCallback(devices,handler);
    }
    @Override protected void onResume(){
        super.onResume();resumed=true;
        startAudio();midi.resume();handler.post(refresh);
    }
    @Override protected void onPause(){
        resumed=false;handler.removeCallbacks(refresh);
        midi.pause();stopAudio();
        prefs.edit().putString("rig",rig.save()).putString("parts",state.save())
            .putLong("lessons",view.home.tutorial.saveDone()).putInt("lesson",view.home.tutorial.lesson)
            .putBoolean("started",true).apply();
        if(focusHeld)audio.abandonAudioFocus(focus);
        focusHeld=false;
        super.onPause();
    }
    @Override protected void onDestroy(){audio.unregisterAudioDeviceCallback(devices);midi.destroy();super.onDestroy();}
    @Override public void onBackPressed(){
        new AlertDialog.Builder(this).setTitle("Leave MPK Synth?")
            .setMessage("Your sounds are saved.")
            .setPositiveButton("Leave",(d,w)->finish())
            .setNegativeButton("Keep playing",null).show();
    }

    boolean audioRunning(){return running;}
    /** Something was played: if the audio is off (another app took it), try to bring it back. */
    void played(){
        long now=SystemClock.uptimeMillis();
        if(!running&&resumed&&now-lastTry>=500){lastTry=now;startAudio();}
    }
    private long lastTry;
    void notice(String text){state.notice(text,SystemClock.uptimeMillis());if(view!=null)view.ui.invalidate();}

    private void startAudio(){
        if(!resumed)return;
        recovery.cancel();
        if(!focusHeld){
            int r=audio.requestAudioFocus(focus,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN);
            if(r!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){notice("Audio is busy in another app. Play a key to try again");return;}
            focusHeld=true;
        }
        if(!openAudio())recovery.begin(SystemClock.uptimeMillis());
    }
    private boolean openAudio(){
        String error=Synth.start(BUFFERS);running=error==null;
        if(running)synth.retry();
        if(!running)notice("Audio error: "+error);
        return running;
    }
    private void stopAudio(){recovery.cancel();Synth.stop();running=false;}
}
