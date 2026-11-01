/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.paleotone.synth.ui.Library;

/**
 * The sample library behind the round-three Library screen: imported WAV files, one of
 * which can be loaded into the eight-voice sample layer that answers keyboard and MIDI.
 * Files are read and decoded on a worker thread; the screen reads a snapshot.
 */
final class AndroidLibrary implements Library {
    private final MainActivity activity;private final SampleLibrary files;private final SharedPreferences prefs;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    /** Duration and waveform per file, filled in by the worker. */
    private final ConcurrentHashMap<String,float[]> peaks=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,Float> seconds=new ConcurrentHashMap<>();
    private volatile Sound[] sounds=new Sound[0];
    private String loaded;private boolean sampleMode,closed;private String status="Preparing starter sounds…";
    private int previewNote;private boolean previewing;
    private final Runnable previewEnd=()->{Native.midi(0x80,previewNote,0);Native.sampleMode(sampleMode);previewing=false;};
    /** A sound to preview as soon as it has loaded. */
    private String previewAfterLoad;

    AndroidLibrary(MainActivity activity){
        this.activity=activity;files=new SampleLibrary(activity);prefs=activity.getSharedPreferences("samples",0);
        worker.execute(()->{
            try{
                files.starters();scan();
                final SampleLibrary.Entry saved=files.find(prefs.getString("selected",""));
                main.post(()->{if(closed)return;status=null;if(saved!=null)load(saved.id(),prefs.getBoolean("mode",false));});
            }catch(Exception e){report(e);}
        });
    }

    /** Reads every file's length and waveform once; runs on the worker. */
    private void scan(){
        List<SampleLibrary.Entry> list=files.list();
        for(SampleLibrary.Entry entry:list)if(!peaks.containsKey(entry.id())){
            try{WavSample w=WavSample.read(entry.file);seconds.put(entry.id(),(float)w.data.length/w.rate);peaks.put(entry.id(),peaksOf(w.data));}
            catch(IOException|OutOfMemoryError e){seconds.put(entry.id(),0f);peaks.put(entry.id(),new float[0]);}
        }
        Sound[] next=new Sound[list.size()];
        for(int i=0;i<next.length;i++){
            SampleLibrary.Entry entry=list.get(i);Float sec=seconds.get(entry.id());
            next[i]=new Sound(entry.id(),entry.name,root(entry),sec==null?0:sec,peaks.get(entry.id()));
        }
        sounds=next;activity.runOnUiThread(activity::redraw);
    }
    static float[] peaksOf(float[] data){
        float[] p=new float[160];int per=Math.max(1,data.length/p.length);
        float top=1e-6f;
        for(int i=0;i<p.length;i++){
            float m=0;for(int j=i*per,end=Math.min(data.length,j+per);j<end;j++)m=Math.max(m,Math.abs(data[j]));
            p[i]=m;top=Math.max(top,m);
        }
        for(int i=0;i<p.length;i++)p[i]/=top;
        return p;
    }
    private int root(SampleLibrary.Entry e){return prefs.getInt("root."+e.id(),e.name.contains("C3")?48:60);}
    private void saveSelection(){
        SharedPreferences.Editor e=prefs.edit().putBoolean("mode",sampleMode);
        if(loaded!=null)e.putString("selected",loaded);else e.remove("selected");
        e.apply();
    }
    private void report(Throwable e){main.post(()->{if(closed)return;status=null;activity.notice("Library: "+e.getMessage());});}

    public Sound[] sounds(){return sounds;}
    public String loaded(){return loaded;}
    public boolean sampleSource(){return sampleMode&&loaded!=null;}
    public void useSample(boolean on){
        if(status!=null)return;
        if(on&&loaded==null){activity.notice("Load a sound first");return;}
        sampleMode=on;Native.sampleMode(on);saveSelection();
        activity.notice(on?"Keyboard and MIDI play the imported sample":"Keyboard and MIDI play Paleotone sounds");
    }
    public void load(String id){load(id,true);}
    private void load(final String id,final boolean enable){
        final SampleLibrary.Entry entry=files.find(id);if(entry==null||status!=null)return;
        status="Loading "+entry.name+"…";
        worker.execute(()->{
            try{
                final WavSample decoded=WavSample.read(entry.file);
                main.post(()->{
                    if(closed)return;status=null;
                    if(!activity.installSample(decoded,root(entry))){activity.notice("Cannot load the sound into the audio engine");return;}
                    loaded=id;sampleMode=enable;Native.sampleMode(enable);saveSelection();
                    activity.notice(String.format(Locale.ROOT,"Loaded %s · %.1f s · %d Hz",entry.name,(double)decoded.data.length/decoded.rate,decoded.rate));
                    if(id.equals(previewAfterLoad)){previewAfterLoad=null;preview(id);}
                });
            }catch(Exception e){report(e);}
            catch(OutOfMemoryError e){report(new IOException("Not enough memory to load this sound. Try a shorter WAV."));}
        });
    }
    /** Loads the sound if needed, then plays its root note through the sample layer for up to four seconds. */
    public void preview(String id){
        if(status!=null)return;
        if(!id.equals(loaded)){
            previewAfterLoad=id;load(id,sampleMode);
            return;
        }
        SampleLibrary.Entry entry=files.find(id);if(entry==null)return;
        activity.ensureAudio();
        if(previewing){main.removeCallbacks(previewEnd);Native.midi(0x80,previewNote,0);}
        previewNote=root(entry);previewing=true;
        Native.sampleMode(true);Native.midi(0x90,previewNote,100);
        Float sec=seconds.get(id);
        main.postDelayed(previewEnd,(long)(1000*Math.min(4,sec==null||sec<=0?2:sec+.2f)));
    }
    public void setRoot(String id,int note){
        note=Math.max(0,Math.min(127,note));
        prefs.edit().putInt("root."+id,note).apply();
        if(id.equals(loaded))Native.sampleRoot(note);
        Sound[] list=sounds.clone();
        for(int i=0;i<list.length;i++)if(list[i].id.equals(id))list[i]=new Sound(id,list[i].name,note,list[i].seconds,list[i].peaks);
        sounds=list;
    }
    public void delete(String id){
        final SampleLibrary.Entry entry=files.find(id);if(entry==null||status!=null)return;
        try{
            if(id.equals(loaded)){activity.installSample(null,60);loaded=null;sampleMode=false;Native.sampleMode(false);saveSelection();}
            files.delete(entry);peaks.remove(id);seconds.remove(id);
            activity.notice("Deleted "+entry.name);
        }catch(IOException e){report(e);}
        worker.execute(this::scan);
    }
    public void importWav(){
        if(status!=null)return;
        try{
            KioskController.suspendForPicker(activity);
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");
            activity.pickSample(i);
        }catch(RuntimeException e){KioskController.resumeAfterPicker(activity);report(e);}
    }
    /** The file picker's answer (MainActivity.onActivityResult). */
    void picked(Uri uri){
        KioskController.resumeAfterPicker(activity);if(uri==null)return;
        status="Importing WAV…";
        worker.execute(()->{
            try{
                final SampleLibrary.Entry entry=files.importUri(uri);scan();
                main.post(()->{if(closed)return;status=null;activity.notice("Imported "+entry.name);});
            }catch(Exception e){report(e);}
            catch(OutOfMemoryError e){report(new IOException("Not enough memory. Try a shorter WAV."));}
        });
    }
    public String status(){return status;}
    void close(){closed=true;main.removeCallbacks(previewEnd);worker.shutdown();}
}
