/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth;
import android.app.AlertDialog;
import android.content.Context;
import android.media.midi.*;
import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.util.ArrayList;

final class MidiInput {
    /** MPK mini knobs, on the main thread: knob 0-7, raw value 0-127. Connected true/false on open and close. */
    interface Mpk{void knob(int knob,int value);void connected(boolean on);}
    private final Mpk mpk;
    private final MidiManager manager;
    private final Context context;
    private MidiDevice device;
    private MidiOutputPort port;
    private int generation;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final MidiManager.DeviceCallback callback=new MidiManager.DeviceCallback() {
        @Override public void onDeviceRemoved(MidiDeviceInfo info) {
            if(device!=null && device.getInfo().getId()==info.getId()) close();
        }
        @Override public void onDeviceAdded(MidiDeviceInfo info) {if(live)autoConnect();}
    };
    MidiInput(Context context,Mpk mpk) {
        this.context=context;this.mpk=mpk;
        manager=(MidiManager)context.getSystemService(Context.MIDI_SERVICE);
        if(manager!=null) manager.registerDeviceCallback(callback,handler);
    }
    void choose() {
        if(manager==null) { message("Android MIDI service is unavailable."); return; }
        ArrayList<MidiDeviceInfo> devices=new ArrayList<>();
        ArrayList<Integer> ports=new ArrayList<>();
        ArrayList<String> names=new ArrayList<>();
        names.add("Disconnect controller");
        for(MidiDeviceInfo info:manager.getDevices()) {
            for(MidiDeviceInfo.PortInfo p:info.getPorts()) if(p.getType()==MidiDeviceInfo.PortInfo.TYPE_OUTPUT) {
                devices.add(info); ports.add(p.getPortNumber());
                String name=info.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME,"MIDI controller");
                names.add(name+" — "+(p.getName().isEmpty()?"port "+p.getPortNumber():p.getName()));
            }
        }
        if(names.size()==1) {message("Connect a USB MIDI controller, then tap MIDI again. No controller output ports are currently available.");return;}
        new AlertDialog.Builder(context).setTitle("MIDI input").setItems(names.toArray(new String[0]),(dialog,index)->{
            close(); if(index==0) return;
            open(devices.get(index-1),ports.get(index-1),true);
        }).show();
    }
    /** True while the app is in front: only then does a plugged-in MPK open by itself. */
    private boolean live;
    void resume() {live=true;autoConnect();}
    void pause() {live=false;close();}
    /** Opens an MPK mini by itself when one is plugged in and no controller is open. */
    private void autoConnect() {
        if(manager==null||device!=null) return;
        for(MidiDeviceInfo info:manager.getDevices()) {
            if(!org.paleotone.synth.ui.Mpk.isMpk(name(info))) continue;
            for(MidiDeviceInfo.PortInfo p:info.getPorts()) if(p.getType()==MidiDeviceInfo.PortInfo.TYPE_OUTPUT) {
                open(info,p.getPortNumber(),false); return;   // the first output port carries keys, knobs and pads
            }
        }
    }
    private static String name(MidiDeviceInfo info) {
        return info.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME,"MIDI controller");
    }
    private void open(MidiDeviceInfo info,int portNumber,boolean tell) {
        final int request=++generation;
        final boolean isMpk=org.paleotone.synth.ui.Mpk.isMpk(name(info));
        manager.openDevice(info,opened->{
            if(opened==null) {if(tell)message("Could not open MIDI controller.");return;}
            if(request!=generation) {try{opened.close();}catch(IOException ignored){} return;}
            device=opened; port=device.openOutputPort(portNumber);
            if(port==null) {close();if(tell)message("MIDI port is busy or unavailable.");return;}
            // The MPK's knobs (channel 1 CC70-77) drive Sound design instead of the firmware's CC map.
            port.connect(new Parser(isMpk?(k,v)->handler.post(()->mpk.knob(k,v)):null));
            if(isMpk) mpk.connected(true);
        },handler);
    }
    void close() {
        ++generation;
        try{if(port!=null)port.close();}catch(IOException ignored){}
        try{if(device!=null)device.close();}catch(IOException ignored){}
        port=null;device=null;Native.panic();
        mpk.connected(false);
    }
    void destroy() {close();if(manager!=null)manager.unregisterDeviceCallback(callback);}
    private void message(String text){new AlertDialog.Builder(context).setMessage(text).setPositiveButton("OK",null).show();}
    /* Running status, fragmented messages, real-time interleaving and SysEx skip. */
    interface Knobs{void knob(int knob,int value);}
    static final class Parser extends MidiReceiver {
        int status, count, first;
        boolean sysex;
        /** Null for other controllers: every message goes to the engine. */
        private final Knobs knobs;
        Parser(Knobs knobs){this.knobs=knobs;}
        @Override public void onSend(byte[] bytes,int offset,int size,long timestamp) {
            for(int i=offset;i<offset+size;++i) {
                int b=bytes[i]&255;
                if(b>=0xf8) continue;
                if(b==0xf0){sysex=true;status=0;count=0;continue;}
                if(b==0xf7){sysex=false;continue;}
                if((b&128)!=0){sysex=false;status=b<0xf0?b:0;count=0;continue;}
                if(sysex || status==0)continue;
                int type=status&0xf0;
                if(type==0xc0 || type==0xd0){Native.midi(status,b,0);continue;}
                if(count++==0) first=b;
                else {
                    count=0;
                    int k=knobs==null?-1:org.paleotone.synth.ui.Mpk.knob(status,first);
                    if(k>=0) knobs.knob(k,b); else Native.midi(status,first,b);
                }
            }
        }
    }
}
