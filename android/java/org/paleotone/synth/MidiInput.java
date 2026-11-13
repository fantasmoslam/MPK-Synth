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
    /** MPK mini controls, on the main thread: knobs (0-7, raw 0-127), transport buttons, and connected on open and close. */
    interface Mpk{void knob(int knob,int value);void transport(org.paleotone.synth.ui.MpkRouter.Transport button,boolean pressed);void connected(boolean on);}
    /** Routes the MPK's keys, pads and joystick to parts (MIDI thread); null while no MPK is open. */
    private volatile org.paleotone.synth.ui.MpkRouter router;
    private volatile int keyPart,padPart=org.paleotone.synth.ui.MpkRouter.DEFAULT_PAD_PART;
    /** The parts the MPK's keys and pads play; the app sets them as the screen changes. */
    void parts(int keys,int pads){
        keyPart=keys;padPart=pads;
        org.paleotone.synth.ui.MpkRouter r=router;if(r!=null){r.keyPart(keys);r.padPart(pads);}
    }
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
            // The MPK's knobs and buttons drive the app; its keys, pads and joystick are routed to parts.
            org.paleotone.synth.ui.MpkRouter r=null;
            if(isMpk){
                r=new org.paleotone.synth.ui.MpkRouter(Native::midi,new org.paleotone.synth.ui.MpkRouter.Controls(){
                    public void knob(int k,int v){handler.post(()->mpk.knob(k,v));}
                    public void transport(org.paleotone.synth.ui.MpkRouter.Transport b,boolean pressed){handler.post(()->mpk.transport(b,pressed));}
                });
                r.keyPart(keyPart);r.padPart(padPart);
            }
            router=r;
            port.connect(new Parser(r));
            if(isMpk) mpk.connected(true);
        },handler);
    }
    void close() {
        ++generation;
        try{if(port!=null)port.close();}catch(IOException ignored){}
        org.paleotone.synth.ui.MpkRouter r=router;router=null;if(r!=null)r.release();
        try{if(device!=null)device.close();}catch(IOException ignored){}
        port=null;device=null;Native.panic();
        mpk.connected(false);
    }
    void destroy() {close();if(manager!=null)manager.unregisterDeviceCallback(callback);}
    private void message(String text){new AlertDialog.Builder(context).setMessage(text).setPositiveButton("OK",null).show();}
    /* Running status, fragmented messages, real-time interleaving and SysEx skip. */
    static final class Parser extends MidiReceiver {
        int status, count, first;
        boolean sysex;
        /** Null for other controllers: every message goes to the engine as sent. */
        private final org.paleotone.synth.ui.MpkRouter mpk;
        Parser(org.paleotone.synth.ui.MpkRouter mpk){this.mpk=mpk;}
        @Override public void onSend(byte[] bytes,int offset,int size,long timestamp) {
            for(int i=offset;i<offset+size;++i) {
                int b=bytes[i]&255;
                if(b>=0xf8) continue;
                if(b==0xf0){sysex=true;status=0;count=0;continue;}
                if(b==0xf7){sysex=false;continue;}
                if((b&128)!=0){sysex=false;status=b<0xf0?b:0;count=0;continue;}
                if(sysex || status==0)continue;
                int type=status&0xf0;
                if(type==0xc0 || type==0xd0){if(mpk!=null)mpk.message(status,b,0);else Native.midi(status,b,0);continue;}
                if(count++==0) first=b;
                else {
                    count=0;
                    if(mpk!=null) mpk.message(status,first,b); else Native.midi(status,first,b);
                }
            }
        }
    }
}
