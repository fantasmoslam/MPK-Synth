/* SPDX-License-Identifier: GPL-3.0-only */
package org.fantasmoslam.mpksynth.ui;

/** The screen's own state: the tab, which parts the MPK plays, the open list and the notice line. */
public final class State {
    public static final int TAB_SOUND=0,TAB_LEARN=1;
    public int tab=TAB_SOUND;
    /** The part the keys, joystick and knobs play; the part the pads play. */
    public int keyPart=0,padPart=MpkRouter.DEFAULT_PAD_PART;
    /** The model or preset list, while it is open. */
    public static final int PICK_NONE=0,PICK_MODEL=1,PICK_PRESET=2;
    public int picking=PICK_NONE,pickPage=0;
    public String notice="";public long noticeUntil;
    public boolean mpkConnected;
    /** The LEARN tab shows the glossary instead of a lesson. */
    public boolean glossary;
    /** A lesson step sent you to the SOUND screen, and brings you back when it is done. */
    public boolean coached;
    /** The drum pad each part's screen last flashed (MPK or touch), and when. */
    public final int[] flashPad={-1,-1,-1,-1};public final long[] flashAt=new long[4];

    public void notice(String text,long now){notice=text==null?"":text;noticeUntil=now+3500;}

    /** Saved as "keys,pads". */
    public String save(){return keyPart+","+padPart;}
    public void load(String s){
        if(s==null)return;
        String[] f=s.split(",");
        if(f.length>0)keyPart=clamp(Catalog.num(f[0]));
        if(f.length>1)padPart=clamp(Catalog.num(f[1]));
    }
    private static int clamp(int part){return Math.max(0,Math.min(Rig.PARTS-1,part));}
}
