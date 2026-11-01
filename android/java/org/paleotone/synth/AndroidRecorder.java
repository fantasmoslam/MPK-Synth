/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import java.io.*;
import java.util.ArrayList;
import java.util.Locale;
import org.paleotone.synth.ui.Recorder;

/**
 * Takes of the final stereo mix as 16-bit WAV. While recording, a writer thread drains
 * the native tap into a private part file; stopping drains the rest, completes the WAV
 * header and copies the file to Music/Paleotone. Only a completed copy is reported
 * saved. A take that fails to save keeps its part file for Retry, including after the
 * app restarts, and blocks new takes until it is saved or discarded.
 */
final class AndroidRecorder implements Recorder {
    private static final int LEVELS=90;
    private final MainActivity activity;private final SharedPreferences prefs;private final File dir;
    private final Handler main=new Handler(Looper.getMainLooper());
    /** One take: its file name, part file, length and state. Guarded by this. */
    private static final class Entry{String name;File part;float seconds;int state;String uri;}
    private final ArrayList<Entry> takes=new ArrayList<>();
    private Entry current;
    private volatile int state=READY;private volatile long frames;private volatile int rate=48000;
    private volatile String error;private volatile boolean stopping;
    private final float[] levels=new float[LEVELS];private int levelCount;
    private Thread writer;

    AndroidRecorder(MainActivity activity){
        this.activity=activity;prefs=activity.getSharedPreferences("takes",0);
        dir=new File(activity.getFilesDir(),"takes");dir.mkdirs();
        load();
    }

    // ---- persistence -------------------------------------------------------------------------
    private synchronized void load(){
        String saved=prefs.getString("list","");
        for(String line:saved.split("\n")){
            String[] f=line.split("\t",-1);if(f.length<5)continue;
            Entry t=new Entry();t.name=f[0];t.seconds=Float.parseFloat(f[1]);t.state=Integer.parseInt(f[2]);
            t.part=f[3].isEmpty()?null:new File(f[3]);t.uri=f[4].isEmpty()?null:f[4];
            if(t.state!=SAVED){
                // A take interrupted by the app closing: never claim it was saved.
                if(t.part==null||!t.part.exists()||t.part.length()<=44)continue;
                t.state=ERROR;
            }
            takes.add(t);
        }
        for(Entry t:takes)if(t.state==ERROR){current=t;state=ERROR;error="the app closed before this take was saved";frames=(long)(t.seconds*rate);break;}
        File[] parts=dir.listFiles();
        if(parts!=null)for(File p:parts){
            boolean known=false;for(Entry t:takes)if(p.equals(t.part))known=true;
            if(!known)p.delete();
        }
    }
    private synchronized void persist(){
        StringBuilder b=new StringBuilder();
        for(int i=0;i<takes.size()&&i<60;i++){
            Entry t=takes.get(i);
            b.append(t.name.replace('\t',' ')).append('\t').append(t.seconds).append('\t').append(t.state).append('\t')
             .append(t.part==null?"":t.part.getPath()).append('\t').append(t.uri==null?"":t.uri).append('\n');
        }
        prefs.edit().putString("list",b.toString()).apply();
    }

    // ---- Recorder ----------------------------------------------------------------------------
    public int state(){return state;}
    public long takeMillis(){return frames*1000/Math.max(1,rate);}
    public synchronized String takeName(){return current==null?null:current.name;}
    public String error(){return error;}
    public synchronized float[] levels(){
        float[] out=new float[levelCount];
        System.arraycopy(levels,LEVELS-levelCount,out,0,levelCount);return out;
    }
    public long freeBytes(){
        try{
            File root=Build.VERSION.SDK_INT>=29?Environment.getExternalStorageDirectory():activity.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
            return Math.min(root.getUsableSpace(),activity.getFilesDir().getUsableSpace());
        }catch(RuntimeException e){return -1;}
    }
    public String destination(){return Build.VERSION.SDK_INT>=29?"Music/Paleotone":"Android/data/org.paleotone.synth/files/Music/Paleotone";}

    public void start(String runName){
        synchronized(this){
            if(state==RECORDING||state==SAVING||state==ERROR)return;
            Entry t=new Entry();t.name=uniqueName(runName);t.state=RECORDING;
            t.part=new File(dir,System.currentTimeMillis()+".part");
            current=t;takes.add(0,t);levelCount=0;
        }
        frames=0;error=null;stopping=false;rate=Native.sampleRate();
        final Entry t=current;
        try(RandomAccessFile f=new RandomAccessFile(t.part,"rw")){f.setLength(0);f.write(header(0,rate));}
        catch(IOException e){fail(t,e.getMessage());return;}
        state=RECORDING;persist();
        activity.ensureAudio();rate=Native.sampleRate();
        Native.recordStart();
        writer=new Thread(()->write(t),"PaleotoneRecorder");writer.start();
        activity.notice("Recording "+t.name);
    }
    private String uniqueName(String runName){
        String slug=runName.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("^-|-$","");
        if(slug.isEmpty())slug="paleotone";
        slug=Character.toUpperCase(slug.charAt(0))+slug.substring(1);
        int n=prefs.getInt("next",1);
        prefs.edit().putInt("next",n+1).apply();
        return String.format(Locale.ROOT,"%s-take-%02d.wav",slug,n);
    }
    /** Writer thread: drain the tap into the part file until stopped and empty. */
    private void write(Entry t){
        short[] samples=new short[16384];byte[] bytes=new byte[samples.length*2];
        float peak=0;long peakFrames=0;long levelEvery=Math.max(1,rate/4);
        try(FileOutputStream out=new FileOutputStream(t.part,true)){
            while(true){
                int n=Native.recordRead(samples);
                if(n==0){if(stopping)break;try{Thread.sleep(40);}catch(InterruptedException ignored){}continue;}
                for(int i=0;i<n;i++){short v=samples[i];bytes[2*i]=(byte)v;bytes[2*i+1]=(byte)(v>>8);peak=Math.max(peak,Math.abs(v)/32768f);}
                out.write(bytes,0,n*2);
                frames+=n/2;peakFrames+=n/2;
                if(peakFrames>=levelEvery){pushLevel(peak);peak=0;peakFrames=0;}
            }
            out.getFD().sync();
        }catch(IOException e){Native.recordStop();fail(t,e.getMessage());return;}
        synchronized(this){t.seconds=frames/(float)rate;}
        int dropped=Native.recordDropped();
        if(dropped>0)main.post(()->activity.notice(String.format(Locale.ROOT,"Recording fell behind: %.1f s missing from the take",dropped/(float)rate)));
        save(t);
    }
    private synchronized void pushLevel(float p){
        System.arraycopy(levels,1,levels,0,LEVELS-1);levels[LEVELS-1]=Math.min(1,p*1.4f);
        if(levelCount<LEVELS)levelCount++;
    }
    public void stop(){
        if(state!=RECORDING)return;
        Native.recordStop();
        synchronized(this){if(current!=null)current.state=SAVING;}
        state=SAVING;stopping=true;persist();
    }
    public void retry(){
        final Entry t;
        synchronized(this){if(state!=ERROR||current==null)return;t=current;t.state=SAVING;}
        state=SAVING;error=null;
        new Thread(()->save(t),"PaleotoneRecorderSave").start();
    }
    public void discard(){
        synchronized(this){
            if(state!=ERROR||current==null)return;
            if(current.part!=null)current.part.delete();
            takes.remove(current);current=null;
        }
        state=READY;error=null;frames=0;persist();
    }
    public synchronized Take[] takes(){
        Take[] out=new Take[takes.size()];
        for(int i=0;i<out.length;i++){Entry t=takes.get(i);out[i]=new Take(t.name,t==current&&t.state==RECORDING?takeMillis()/1000f:t.seconds,t.state,t.uri);}
        return out;
    }

    // ---- saving ------------------------------------------------------------------------------
    /** Completes the header from the part file's length, copies it out, then deletes the part. */
    private void save(Entry t){
        try{
            long data;
            try(RandomAccessFile f=new RandomAccessFile(t.part,"rw")){
                data=Math.max(0,f.length()-44)&~3L;
                if(data>0xffffffffL-36)throw new IOException("take is longer than a WAV file can hold");
                f.seek(0);f.write(header(data,rate));
            }
            synchronized(this){t.seconds=data/4f/rate;}
            String where=publish(t);
            synchronized(this){t.uri=where;t.state=SAVED;}
            t.part.delete();
            state=SAVED;persist();
            main.post(()->activity.notice("Saved "+t.name+" to "+destination()));
        }catch(IOException|RuntimeException e){fail(t,e.getMessage());}
    }
    private String publish(Entry t)throws IOException{
        if(Build.VERSION.SDK_INT>=29){
            ContentResolver resolver=activity.getContentResolver();
            ContentValues v=new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME,t.name);v.put(MediaStore.MediaColumns.MIME_TYPE,"audio/wav");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_MUSIC+"/Paleotone");v.put(MediaStore.MediaColumns.IS_PENDING,1);
            Uri uri=resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,v);
            if(uri==null)throw new IOException("Android refused the new file");
            try{
                try(OutputStream out=resolver.openOutputStream(uri);InputStream in=new FileInputStream(t.part)){
                    if(out==null)throw new IOException("cannot open the new file");
                    copy(in,out);
                }
                v.clear();v.put(MediaStore.MediaColumns.IS_PENDING,0);resolver.update(uri,v,null,null);
            }catch(IOException|RuntimeException e){resolver.delete(uri,null,null);throw e;}
            return uri.toString();
        }
        File folder=new File(activity.getExternalFilesDir(Environment.DIRECTORY_MUSIC),"Paleotone");
        if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("cannot create "+folder);
        File target=new File(folder,t.name);
        try(InputStream in=new FileInputStream(t.part);FileOutputStream out=new FileOutputStream(target)){copy(in,out);out.getFD().sync();}
        return target.getPath();
    }
    private static void copy(InputStream in,OutputStream out)throws IOException{byte[] b=new byte[1<<16];int n;while((n=in.read(b))>0)out.write(b,0,n);}
    private void fail(Entry t,String why){
        synchronized(this){t.state=ERROR;current=t;}
        error=why==null?"unknown error":why;state=ERROR;persist();
        main.post(()->activity.notice("Take not saved: "+error));
    }
    /** RIFF/WAVE header for 16-bit stereo PCM. */
    static byte[] header(long dataBytes,int rate){
        java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[]{'R','I','F','F'}).putInt((int)(36+dataBytes)).put(new byte[]{'W','A','V','E','f','m','t',' '})
         .putInt(16).putShort((short)1).putShort((short)2).putInt(rate).putInt(rate*4).putShort((short)4).putShort((short)16)
         .put(new byte[]{'d','a','t','a'}).putInt((int)dataBytes);
        return b.array();
    }
}
