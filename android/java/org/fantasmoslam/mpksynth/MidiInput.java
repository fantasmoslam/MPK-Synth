/* SPDX-License-Identifier: GPL-3.0-only */
package org.fantasmoslam.mpksynth;

import android.content.Context;
import android.media.midi.*;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.io.IOException;
import org.fantasmoslam.mpksynth.ui.Mpk;
import org.fantasmoslam.mpksynth.ui.MpkRouter;

/**
 * Opens the MPK mini by itself whenever it is plugged in while the app is in front, and
 * closes it when the app leaves. Its keys, pads and joystick go through MpkRouter to the
 * synth on the MIDI thread; its knobs and buttons come to the app on the main thread.
 */
final class MidiInput {
    /** The MPK's controls, on the main thread. */
    interface Listener{
        void knob(int knob,int value);
        void transport(MpkRouter.Transport button,boolean pressed);
        /** A key (pad false) or pad was struck. */
        void played(boolean pad,int note);
        /** The joystick's bend, at most every 30 ms while it moves. */
        void joystick(int bend);
        void connected(boolean on);
    }
    private final Listener listener;
    private final MidiManager manager;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private MidiDevice device;
    private MidiOutputPort port;
    private volatile MpkRouter router;
    private volatile int keyPart,padPart=MpkRouter.DEFAULT_PAD_PART;
    private int generation;
    /** True while the app is in front: only then does a plugged-in MPK open. */
    private boolean live;
    private final MidiManager.DeviceCallback callback=new MidiManager.DeviceCallback(){
        @Override public void onDeviceRemoved(MidiDeviceInfo info){
            if(device!=null&&device.getInfo().getId()==info.getId())close();
        }
        @Override public void onDeviceAdded(MidiDeviceInfo info){if(live)connect();}
    };

    MidiInput(Context context,Listener listener){
        this.listener=listener;
        manager=(MidiManager)context.getSystemService(Context.MIDI_SERVICE);
        if(manager!=null)manager.registerDeviceCallback(callback,handler);
    }
    /** The parts the keys and pads play; the app sets them as the screen changes. */
    void parts(int keys,int pads){
        keyPart=keys;padPart=pads;
        MpkRouter r=router;if(r!=null){r.keyPart(keys);r.padPart(pads);}
    }
    void resume(){live=true;connect();}
    void pause(){live=false;close();}
    void destroy(){close();if(manager!=null)manager.unregisterDeviceCallback(callback);}

    private static String name(MidiDeviceInfo info){
        return info.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME,"MIDI controller");
    }
    private void connect(){
        if(manager==null||device!=null)return;
        for(MidiDeviceInfo info:manager.getDevices()){
            if(!Mpk.isMpk(name(info)))continue;
            for(MidiDeviceInfo.PortInfo p:info.getPorts())if(p.getType()==MidiDeviceInfo.PortInfo.TYPE_OUTPUT){
                open(info,p.getPortNumber());return;   // the first output port carries keys, knobs and pads
            }
        }
    }
    private void open(MidiDeviceInfo info,int portNumber){
        final int request=++generation;
        manager.openDevice(info,opened->{
            if(opened==null)return;
            if(request!=generation||!live){try{opened.close();}catch(IOException ignored){}return;}
            device=opened;port=device.openOutputPort(portNumber);
            if(port==null){close();return;}
            MpkRouter r=new MpkRouter(Synth::midi,new MpkRouter.Controls(){
                public void knob(int k,int v){handler.post(()->listener.knob(k,v));}
                public void transport(MpkRouter.Transport b,boolean pressed){handler.post(()->listener.transport(b,pressed));}
                public void played(boolean pad,int note){handler.post(()->listener.played(pad,note));}
                long lastBend;
                public void joystick(int bend){
                    long now=SystemClock.uptimeMillis();
                    if(now-lastBend>=30||bend==0){lastBend=now;handler.post(()->listener.joystick(bend));}
                }
            });
            r.keyPart(keyPart);r.padPart(padPart);
            router=r;
            port.connect(new Parser(r));
            listener.connected(true);
        },handler);
    }
    void close(){
        ++generation;
        try{if(port!=null)port.close();}catch(IOException ignored){}
        MpkRouter r=router;router=null;if(r!=null)r.release();
        try{if(device!=null)device.close();}catch(IOException ignored){}
        boolean was=device!=null;
        port=null;device=null;
        if(was)listener.connected(false);
    }

    /** Running status, messages split across packets, real-time bytes and SysEx skipped. */
    static final class Parser extends MidiReceiver {
        private final MpkRouter mpk;
        private int status,count,first;
        private boolean sysex;
        Parser(MpkRouter mpk){this.mpk=mpk;}
        @Override public void onSend(byte[] bytes,int offset,int size,long timestamp){
            for(int i=offset;i<offset+size;++i){
                int b=bytes[i]&255;
                if(b>=0xf8)continue;
                if(b==0xf0){sysex=true;status=0;count=0;continue;}
                if(b==0xf7){sysex=false;continue;}
                if((b&128)!=0){sysex=false;status=b<0xf0?b:0;count=0;continue;}
                if(sysex||status==0)continue;
                int type=status&0xf0;
                if(type==0xc0||type==0xd0){mpk.message(status,b,0);continue;}
                if(count++==0)first=b;
                else{count=0;mpk.message(status,first,b);}
            }
        }
    }
}
