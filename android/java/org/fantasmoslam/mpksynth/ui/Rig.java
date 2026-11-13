/* SPDX-License-Identifier: GPL-3.0-only */
package org.fantasmoslam.mpksynth.ui;

/**
 * The four parts' sounds: which model each plays, the value of every control, and the
 * preset it came from. The app owns these values and sends every change to the synth, so
 * the screen never waits on the audio thread. Saved and restored as text.
 */
public final class Rig {
    public interface Sink{void model(int part,int model);void param(int part,int id,int value);}
    public static final int PARTS=4;

    public final Catalog catalog;
    private Sink sink;
    private final int[] model=new int[PARTS],preset=new int[PARTS],bank=new int[PARTS];
    private final int[][] values=new int[PARTS][];
    private final boolean[] edited=new boolean[PARTS];
    /** The drum pad hit last (0-15): the Kit's PAD bank shows its controls. */
    public int lastPad=0;

    public Rig(Catalog catalog,Sink sink){
        this.catalog=catalog;this.sink=sink;
        int poly=Math.max(0,catalog.find("Poly 106")),kit=catalog.find("Kit");
        String[] start={"A11 Brass Set 1","A55 Synth Bass II","B11 Strings",null};
        for(int p=0;p<PARTS;p++){
            int m=p==3&&kit>=0?kit:poly;
            int pr=start[p]==null?0:indexOf(catalog.model(m).presets,start[p]);
            load(p,m,Math.max(0,pr));
        }
    }
    public void sink(Sink sink){this.sink=sink;}

    public Catalog.Model model(int part){return catalog.model(model[part]);}
    public int value(int part,int id){int[] v=values[part];return id>=0&&id<v.length?v[id]:0;}
    public String presetName(int part){Catalog.Model m=model(part);return preset[part]>=0&&preset[part]<m.presets.length?m.presets[preset[part]]:"";}
    public int preset(int part){return preset[part];}
    public boolean edited(int part){return edited[part];}
    public int bank(int part){return Math.min(bank[part],model(part).banks.length-1);}
    public void bank(int part,int b){bank[part]=Math.max(0,Math.min(model(part).banks.length-1,b));}

    /** A control the user changed: from the screen or a knob. */
    public void set(int part,int id,int value){
        Catalog.Control c=control(part,id);if(c==null)return;
        value=Math.max(c.min,Math.min(c.max,value));
        if(values[part][id]==value)return;
        values[part][id]=value;edited[part]=true;
        if(sink!=null)sink.param(part,id,value);
    }
    public Catalog.Control control(int part,int id){Catalog.Control[] c=model(part).controls;return id>=0&&id<c.length?c[id]:null;}
    /** The control a bank slot points at: 100+k is the last pad's control k on the Kit. */
    public int resolve(int part,int slot){
        if(slot>=Catalog.PAD_CONTROL)return 4+lastPad*4+(slot-Catalog.PAD_CONTROL);
        return slot;
    }

    /** A part changes model: it starts from that model's first preset. */
    public void setModel(int part,int m){if(m!=model[part])load(part,m,0);}
    public void preset(int part,int p){load(part,model[part],p);}
    private void load(int part,int m,int p){
        Catalog.Model mm=catalog.model(m);
        model[part]=mm.id;preset[part]=p;edited[part]=false;bank[part]=0;
        int[] v=catalog.presetValues(mm.id,p);
        values[part]=v!=null&&v.length==mm.controls.length?v.clone():defaults(mm);
        sendPart(part);
    }
    private static int[] defaults(Catalog.Model m){int[] v=new int[m.controls.length];for(int i=0;i<v.length;i++)v[i]=m.controls[i].def;return v;}
    private void sendPart(int part){
        if(sink==null)return;
        sink.model(part,model[part]);
        for(int i=0;i<values[part].length;i++)sink.param(part,i,values[part][i]);
    }
    /** Sends every part again (after the synth starts). */
    public void sendAll(){for(int p=0;p<PARTS;p++)sendPart(p);}

    /** One line per part: model name | preset | edited | values. */
    public String save(){
        StringBuilder b=new StringBuilder();
        for(int p=0;p<PARTS;p++){
            b.append(model(p).name).append('|').append(preset[p]).append('|').append(edited[p]?1:0).append('|');
            for(int i=0;i<values[p].length;i++){if(i>0)b.append(',');b.append(values[p][i]);}
            b.append('\n');
        }
        return b.toString();
    }
    /** Restores what save wrote; anything that no longer fits is left at its preset. */
    public void restore(String text){
        if(text==null)return;
        String[] lines=text.split("\n");
        for(int p=0;p<PARTS&&p<lines.length;p++){
            String[] f=lines[p].split("\\|",-1);if(f.length<4)continue;
            int m=catalog.find(f[0]);if(m<0)continue;
            int pr=Catalog.num(f[1]);
            if(pr<0||pr>=catalog.model(m).presets.length)pr=0;
            load(p,m,pr);
            String[] v=f[3].split(",");
            if(v.length==values[p].length){
                for(int i=0;i<v.length;i++){Catalog.Control c=control(p,i);values[p][i]=Math.max(c.min,Math.min(c.max,Catalog.num(v[i])));}
                edited[p]=f[2].equals("1");
                sendPart(p);
            }
        }
    }
    static int indexOf(String[] a,String s){for(int i=0;i<a.length;i++)if(a[i].equals(s))return i;return -1;}
}
