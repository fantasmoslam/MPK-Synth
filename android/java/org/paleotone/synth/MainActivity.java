/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth;

import android.app.*;
import android.os.*;
import android.content.*;
import android.media.AudioManager;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.view.*;
import android.widget.Toast;
import android.util.AtomicFile;
import android.util.Log;
import java.io.*;

public final class MainActivity extends Activity {
    private SynthView panel;
    private Workspace workspace;
    private Shell shell;
    private NativeEngine engine;
    private final org.paleotone.synth.ui.UiState uiState=new org.paleotone.synth.ui.UiState();
    private long lastTick;
    private int tick;
    private MidiInput midi;
    private boolean running, resumed, focusHeld, wantAudio;
    private Boolean pickerAudio;
    private int buffers=3;
    private int outputLevel=1;
    private final AudioRecovery recovery=new AudioRecovery();
    private AudioManager audio;
    private AtomicFile session;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private long lastReport;
    private final AudioManager.OnAudioFocusChangeListener focus=change->{
        if(change==AudioManager.AUDIOFOCUS_GAIN){
            focusHeld=true;
            if(resumed&&wantAudio)startAudio();
        }else if(change<0) {
            if(running){panel.release();Native.stop();running=false;}
            focusHeld=false;recovery.cancel();
            if(change==AudioManager.AUDIOFOCUS_LOSS)wantAudio=false;
            panel.notice="Audio focus lost — tap START to resume";
        }
    };
    private final AudioDeviceCallback devices=new AudioDeviceCallback(){
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] changed){routeChanged(changed);}
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] changed){routeChanged(changed);}
    };
    private void routeChanged(AudioDeviceInfo[] changed){
        if(!resumed||!wantAudio||!focusHeld)return;
        for(AudioDeviceInfo device:changed)if(device.isSink()){
            if(recovery.pending())recovery.begin(SystemClock.uptimeMillis());
            break;
        }
    }
    private final Runnable refresh=new Runnable(){public void run(){
        if(!resumed)return;
        long now=SystemClock.uptimeMillis();
        if(running&&Native.error()!=0){
            panel.release();Native.stop();running=false;
            if(wantAudio&&focusHeld){recovery.begin(now);panel.notice="Audio route changed — reconnecting…";}
        }
        if(wantAudio&&focusHeld&&recovery.due(now)){
            if(openAudio()){
                recovery.cancel();panel.notice="Audio reconnected • project and sequencer retained";
            }else{
                recovery.failed(now);
                if(recovery.exhausted()){recovery.cancel();wantAudio=false;panel.notice="Audio unavailable — tap START to retry";}
            }
        }
        /* Every 10 s while playing: adb logcat -d -s PaleotoneAudio */
        if(running&&now-lastReport>=10000){
            lastReport=now;String report=Native.report();
            if(report!=null)Log.i("PaleotoneAudio",Build.MODEL+" | "+report);
        }
        panel.update(Native.stats(),Native.peak(),running||recovery.pending());
        workspace.update(running||recovery.pending());
        if(lastTick!=0&&running)uiState.runElapsed+=now-lastTick;
        lastTick=now;
        if(++tick%3==0)engine.refresh();
        shell.view.tick();
        handler.postDelayed(this,33);
    }};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        outputLevel=getPreferences(MODE_PRIVATE).getInt("outputLevel",1);
        if(outputLevel<0||outputLevel>2)outputLevel=1;
        Native.level(outputLevel);
        session=new AtomicFile(new File(getFilesDir(),"session.ptandroid"));
        byte[] saved=null;
        try { saved=session.readFully(); } catch(IOException ignored) {}
        Native.init(saved);
        panel=new SynthView(this);workspace=new Workspace(this,panel);
        engine=new NativeEngine(this);
        uiState.load(getPreferences(MODE_PRIVATE).getString("ui",null));
        shell=new Shell(this,workspace,new PhosphorView(this,uiState,engine));setContentView(shell);
        midi=new MidiInput(this);
        audio.registerAudioDeviceCallback(devices,handler);
    }
    @Override protected void onResume(){super.onResume();resumed=true;if(pickerAudio==null||pickerAudio)startAudio();pickerAudio=null;handler.post(refresh);KioskController.restore(this);}
    @Override public void onWindowFocusChanged(boolean focused){
        super.onWindowFocusChanged(focused);
        if(focused&&resumed)KioskController.restore(this);
    }
    void notice(String message){
        panel.notice=message;panel.invalidate();
        uiState.notice=message;uiState.noticeUntil=SystemClock.uptimeMillis()+3500;if(shell!=null)shell.view.ui.invalidate();
    }
    boolean audioRunning(){return running;}
    void maintenance(){panel.release();KioskController.menu(this);}
    @Override protected void onPause(){
        resumed=false;wantAudio=false;recovery.cancel();handler.removeCallbacks(refresh);
        panel.release();midi.close();Native.stop();running=false;
        writeSession();
        getPreferences(MODE_PRIVATE).edit().putString("ui",uiState.save()).apply();lastTick=0;
        if(focusHeld)audio.abandonAudioFocus(focus);focusHeld=false;
        super.onPause();
    }
    @Override protected void onDestroy(){audio.unregisterAudioDeviceCallback(devices);midi.destroy();workspace.close();super.onDestroy();}
    @Override public void onBackPressed(){
        if(KioskController.enabled(this)){maintenance();return;}
        new AlertDialog.Builder(this).setTitle("Leave Paleotone?")
            .setMessage("Save the session and close audio?")
            .setPositiveButton("Save and exit",(d,w)->finish())
            .setNegativeButton("Keep playing",null).show();
    }
    private void startAudio(){
        if(!resumed)return;
        wantAudio=true;recovery.cancel();
        if(!focusHeld){
            int result=audio.requestAudioFocus(focus,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN);
            if(result!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){panel.notice="Audio focus unavailable — tap START to retry";return;}
            focusHeld=true;
        }
        if(!openAudio())recovery.begin(SystemClock.uptimeMillis());
    }
    private boolean openAudio(){
        String error=Native.start(buffers);running=error==null;
        panel.notice=running?"Native Paleotone • hold panel buttons for alternate functions":"Audio error: "+error;
        return running;
    }
    void ensureAudio(){if(!running)startAudio();}
    void stopAllScape(){
        wantAudio=false;recovery.cancel();panel.release();Native.stop();running=false;
        Native.scape(7);notice("Stopped • environment, drone, sequence and audio off");
    }
    void pickSample(Intent intent){
        pickerAudio=wantAudio;
        try{startActivityForResult(intent,41);}catch(RuntimeException e){pickerAudio=null;throw e;}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==41)workspace.picked(result==RESULT_OK&&data!=null?data.getData():null);
    }
    boolean installSample(WavSample sample,int root){
        boolean resume=resumed&&wantAudio;recovery.cancel();panel.release();Native.stop();running=false;
        boolean ok=Native.loadSample(sample==null?null:sample.data,sample==null?44100:sample.rate,root);
        if(resume)startAudio();return ok;
    }
    private boolean writeSession(){
        byte[] data=Native.save();
        if(data==null){panel.notice="Session capture failed";return false;}
        FileOutputStream out=null;
        try{out=session.startWrite();out.write(data);session.finishWrite(out);return true;}
        catch(IOException e){if(out!=null)session.failWrite(out);panel.notice="Save failed: "+e.getMessage();return false;}
    }
    void action(int id){
        if(id==0){
            boolean resume=wantAudio;
            recovery.cancel();panel.release();Native.stop();running=false;
            boolean saved=writeSession();if(resume)startAudio();
            if(saved)Toast.makeText(this,"Session saved",Toast.LENGTH_SHORT).show();
            else new AlertDialog.Builder(this).setMessage("Session could not be saved. Check available storage.").setPositiveButton("OK",null).show();
        }else if(id==1){
            panel.release();
            if(wantAudio){wantAudio=false;recovery.cancel();Native.stop();running=false;}else startAudio();
        }else if(id==2)midi.choose();
        else if(id==3){
            new AlertDialog.Builder(this).setTitle("Audio settings")
                .setItems(new String[]{"Output level","Audio buffer"},(d,w)->{
                    if(w==0){
                        new AlertDialog.Builder(this).setTitle("Output level — stereo limiter enabled")
                            .setSingleChoiceItems(new String[]{"Original alpha level","Normal — +6 dB","Boost — +12 dB"},outputLevel,(dialog,level)->{
                                outputLevel=level;Native.level(level);
                                getPreferences(MODE_PRIVATE).edit().putInt("outputLevel",level).apply();dialog.dismiss();
                            }).setNegativeButton("Cancel",null).show();
                    }else{
                        String[] choices={"Responsive — 2 bursts","Balanced — 3 bursts","Stable — 4 bursts","Extra margin — 6 bursts"};
                        new AlertDialog.Builder(this).setTitle("Audio buffer — changing pauses audio")
                            .setItems(choices,(dialog,choice)->{
                                buffers=new int[]{2,3,4,6}[choice];
                                boolean resume=wantAudio;recovery.cancel();panel.release();Native.stop();running=false;
                                if(resume)startAudio();
                            }).show();
                    }
                }).show();
        }else if(id==4){Native.panic();panel.release();}
        else if(id==5){
            new AlertDialog.Builder(this).setTitle("Paleotone Android • early alpha")
                .setMessage("Play the multitouch keyboard. Drag encoder circles up/down, or tap their −/+ buttons. Hold any panel button for firmware alternate functions. SELECT moves through fields; ENGINE and PRESET choose sounds.\n\nSAVE SESSION briefly stops audio, writes the current project and flash, then resumes. Leaving the app also saves. Android force-stop, power loss, or a crash can lose unsaved changes. The firmware SAVE button retains its original project menu.\n\nMIDI lists Android controller output ports; connect via USB OTG. MIDI channel messages are supported; MIDI clock and SysEx are not yet bridged. LIBRARY imports uncompressed WAV files into a separate eight-voice sample layer; switch between sample and firmware keyboard/MIDI there. Samples are not mapped to sequencer tracks yet.\n\nThe 44.1 kHz firmware engine reaches the device rate through a windowed-sinc converter. The firmware screen is drawn off the audio thread from snapshots. Diagnostics show underruns, late callbacks and the output latency the audio stream reports (not acoustic latency); a full line is logged every 10 seconds under PaleotoneAudio.\n\nUse wired audio for testing. Audio reconnects automatically after an output disconnect. Tap AUDIO for Normal (+6 dB), Boost (+12 dB), or Original output. A stereo limiter protects digital peaks; begin with low Android media volume. Buffer changes pause audio. Kiosk mode is optional. Hold the PALEOTONE title for three seconds, or use Kiosk / maintenance here, to enter setup or exit to Android settings. Device-owner provisioning is required before enabling full kiosk mode.\n\nGPL-3.0-only. Paleotone builds on Felucca and GHOULBOX. Third-party notices are included in the APK assets. Full corresponding source accompanies this build.")
                .setNeutralButton("Kiosk / maintenance",(d,w)->maintenance())
                .setPositiveButton("Close",null).show();
        }
    }
}
