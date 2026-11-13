/* SPDX-License-Identifier: GPL-3.0-only */
package org.fantasmoslam.mpksynth.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** The model tables as android/tools/tables.c prints them, for the preview and tests. */
public final class TableSource implements Catalog.Source {
    private final List<String> models=new ArrayList<>();
    private final List<List<String>> params=new ArrayList<>(),banks=new ArrayList<>(),presets=new ArrayList<>();
    private final List<List<int[]>> values=new ArrayList<>();
    private final List<String> pads=new ArrayList<>();
    public final List<String> sent=new ArrayList<>();

    public TableSource(String file)throws IOException{
        for(String line:Files.readAllLines(Paths.get(file),StandardCharsets.UTF_8)){
            if(line.length()<2)continue;
            String rest=line.substring(2);int m=models.size()-1;
            switch(line.charAt(0)){
                case 'M':models.add(rest);params.add(new ArrayList<>());banks.add(new ArrayList<>());presets.add(new ArrayList<>());values.add(new ArrayList<>());break;
                case 'P':params.get(m).add(rest);break;
                case 'B':banks.get(m).add(rest);break;
                case 'R':{
                    String[] f=rest.split("\t",-1);presets.get(m).add(f[0]);
                    String[] v=f[1].split(",");int[] a=new int[v.length];for(int i=0;i<a.length;i++)a[i]=Integer.parseInt(v[i]);
                    values.get(m).add(a);break;
                }
                case 'D':pads.add(rest);break;
                default:
            }
        }
    }
    public String[] models(){return models.toArray(new String[0]);}
    public String[] params(int model){return params.get(model).toArray(new String[0]);}
    public String[] banks(int model){return banks.get(model).toArray(new String[0]);}
    public String[] presets(int model){return presets.get(model).toArray(new String[0]);}
    public int[] presetValues(int model,int preset){return values.get(model).get(preset).clone();}
    public String padName(int pad){return pad>=0&&pad<pads.size()?pads.get(pad):"";}

    /** A Rig.Sink that records what it was sent. */
    public Rig.Sink sink(){
        return new Rig.Sink(){
            public void model(int part,int model){sent.add("model "+part+" "+model);}
            public void param(int part,int id,int value){sent.add("param "+part+" "+id+" "+value);}
        };
    }
}
