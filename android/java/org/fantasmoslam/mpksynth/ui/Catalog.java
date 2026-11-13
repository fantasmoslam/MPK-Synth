/* SPDX-License-Identifier: GPL-3.0-only */
package org.fantasmoslam.mpksynth.ui;

import java.util.ArrayList;
import java.util.Locale;

/**
 * The synth models and their controls, banks and presets, as the native synth describes
 * them (android/native/mpk). The app reads them through JNI; the preview and tests read the
 * same text from a desktop build of the tables.
 */
public final class Catalog {
    public static final int FMT_NUMBER=0,FMT_LIST=1,FMT_BIPOLAR=2,FMT_SWITCH=3;
    public static final int UI_SLIDER=0,UI_KNOB=1,UI_SWITCH=2;
    /** In a bank: 100 + k is control k of the drum pad hit last. */
    public static final int PAD_CONTROL=100;

    public static final class Control{
        public final int id;public final String label,section;public final int min,max,def,fmt,ui;public final String[] names;
        Control(int id,String row){
            String[] f=row.split("\t",-1);
            this.id=id;label=f[0];section=f[1];min=num(f[2]);max=num(f[3]);def=num(f[4]);fmt=num(f[5]);ui=num(f[6]);
            names=f.length>7&&!f[7].isEmpty()?f[7].split("\\|"):new String[0];
        }
        /** The value as the panel shows it: 0-10 like the originals' scales, or the switch's name. */
        public String text(int v){
            if((fmt==FMT_LIST||fmt==FMT_SWITCH)&&v-min>=0&&v-min<names.length)return names[v-min];
            if(fmt==FMT_BIPOLAR){int c=(min+max)/2;int d=v-c;return d==0?"0":(d>0?"+":"")+d;}
            if(max==127)return String.format(Locale.ROOT,"%.1f",v*10/127f);
            return Integer.toString(v);
        }
        public boolean isSwitch(){return ui==UI_SWITCH||fmt==FMT_SWITCH;}
    }
    public static final class Bank{
        public final String name;public final int[] ids=new int[8];
        Bank(String row){
            String[] f=row.split("\t",-1);name=f[0];String[] n=f[1].split(",");
            for(int k=0;k<8;k++)ids[k]=k<n.length?num(n[k]):-1;
        }
        Bank(String name,int[] ids){this.name=name;for(int k=0;k<8;k++)this.ids[k]=k<ids.length?ids[k]:-1;}
    }
    public static final class Model{
        public final int id;public final String name,inspired;
        public final Control[] controls;public final Bank[] banks;public final String[] presets;
        /**
         * The screen's tabs, each the 8 controls the MPK's knobs turn while it is open: the
         * model's knob banks, then the controls no bank holds, 8 to a tab.
         */
        public final Bank[] tabs;
        Model(int id,String row,String[] params,String[] banks,String[] presets){
            this.id=id;String[] f=row.split("\t",-1);name=f[0];inspired=f.length>1?f[1]:"";
            controls=new Control[params.length];for(int i=0;i<params.length;i++)controls[i]=new Control(i,params[i]);
            this.banks=new Bank[banks.length];for(int i=0;i<banks.length;i++)this.banks[i]=new Bank(banks[i]);
            this.presets=presets;
            tabs=tabs();
        }
        private Bank[] tabs(){
            boolean[] held=new boolean[controls.length];
            for(Bank b:banks)for(int id:b.ids){
                if(id>=PAD_CONTROL){for(int i=4;i<held.length;i++)held[i]=true;}   // the Kit's pad controls follow the last pad
                else if(id>=0&&id<held.length)held[id]=true;
            }
            ArrayList<Integer> rest=new ArrayList<>();
            for(int i=0;i<held.length;i++)if(!held[i])rest.add(i);
            ArrayList<Bank> t=new ArrayList<>(java.util.Arrays.asList(banks));
            for(int i=0;i<rest.size();i+=8){
                int[] ids=new int[Math.min(8,rest.size()-i)];
                for(int k=0;k<ids.length;k++)ids[k]=rest.get(i+k);
                String sec=controls[ids[0]].section;
                t.add(new Bank(t.isEmpty()||sec.length()>8?"MORE":sec,ids));
            }
            return t.toArray(new Bank[0]);
        }
        /** The control with this label in a section, or null. */
        public Control find(String section,String label){
            for(Control c:controls)if(c.section.equals(section)&&c.label.equals(label))return c;
            return null;
        }
        /** The panel's sections, in order. */
        public String[] sections(){
            ArrayList<String> s=new ArrayList<>();
            for(Control c:controls)if(!s.contains(c.section))s.add(c.section);
            return s.toArray(new String[0]);
        }
        public boolean isKit(){return name.equals("Kit");}
    }

    /** Where the tables come from. */
    public interface Source{
        String[] models();String[] params(int model);String[] banks(int model);String[] presets(int model);
        int[] presetValues(int model,int preset);String padName(int pad);
    }

    public final Model[] models;
    private final Source source;
    public Catalog(Source source){
        this.source=source;
        String[] rows=source.models();
        models=new Model[rows.length];
        for(int i=0;i<rows.length;i++)models[i]=new Model(i,rows[i],source.params(i),source.banks(i),source.presets(i));
    }
    public Model model(int id){return id>=0&&id<models.length?models[id]:models[0];}
    public int[] presetValues(int model,int preset){return source.presetValues(model,preset);}
    public String padName(int pad){return source.padName(pad);}
    public int find(String name){for(Model m:models)if(m.name.equals(name))return m.id;return -1;}

    static int num(String s){try{return Integer.parseInt(s.trim());}catch(NumberFormatException e){return 0;}}
}
