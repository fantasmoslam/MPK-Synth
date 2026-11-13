/* SPDX-License-Identifier: GPL-3.0-only */
package org.fantasmoslam.mpksynth.ui;

import java.util.ArrayList;

/** Fitting and wrapping text to a width. */
final class Text {
    private Text(){}
    static String fit(Gfx g,String s,float w,int font,float size){
        if(s==null)return "";
        if(g.textWidth(s,font,size,0)<=w)return s;
        while(s.length()>1&&g.textWidth(s+"…",font,size,0)>w)s=s.substring(0,s.length()-1);
        return s+"…";
    }
    static String fit(Gfx g,String s,float w,float size){return fit(g,s,w,Gfx.MONO,size);}
    /** Splits text into lines no wider than w, at spaces. */
    static String[] wrap(Gfx g,String s,float w,int font,float size){
        ArrayList<String> lines=new ArrayList<>();
        for(String para:s.split("\n")){
            StringBuilder line=new StringBuilder();
            for(String word:para.split(" ")){
                String next=line.length()==0?word:line+" "+word;
                if(line.length()>0&&g.textWidth(next,font,size,0)>w){lines.add(line.toString());line=new StringBuilder(word);}
                else{line.setLength(0);line.append(next);}
            }
            lines.add(line.toString());
        }
        return lines.toArray(new String[0]);
    }
    /** Draws wrapped text from a top-left corner; returns the y below the last line. */
    static float block(Gfx g,String s,float x,float y,float w,int font,float size,int color,float leading){
        float lh=size*leading;
        for(String line:wrap(g,s,w,font,size)){y+=lh;g.text(line,x,y-lh*.28f,font,size,color,Gfx.LEFT,0);}
        return y;
    }
}
