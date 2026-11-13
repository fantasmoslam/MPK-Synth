/* SPDX-License-Identifier: GPL-3.0-only */
package org.fantasmoslam.mpksynth.ui;

/** The parts' sounds: defaults, changes, presets, the Kit's pad controls, and saving. Uses the real tables. */
public final class RigTest {
    static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}

    public static void main(String[] args)throws Exception{
        TableSource t=new TableSource(args[0]);Catalog c=new Catalog(t);
        Rig rig=new Rig(c,t.sink());
        check(rig.model(0).name.equals("Poly 106")&&rig.presetName(0).equals("Brass Section"),"part 1 starts as Poly 106 Brass Section");
        check(rig.presetName(1).equals("Funky Bass")&&rig.presetName(2).equals("Strings"),"parts 2 and 3: "+rig.presetName(1)+", "+rig.presetName(2));
        check(rig.model(3).isKit(),"part 4 is the drums");
        int expected=0;for(int p=0;p<Rig.PARTS;p++)expected+=1+rig.model(p).controls.length;
        check(t.sent.size()==expected,"every part's model and every value is sent at the start: "+t.sent.size()+" of "+expected);
        check(t.sent.get(0).equals("model 0 "+rig.model(0).id),"the model goes first");

        // A change is clamped, kept, marked and sent once.
        Catalog.Model poly=rig.model(0);
        Catalog.Control freq=poly.find("VCF","FREQ");check(freq!=null,"the Poly 106 has a VCF FREQ");
        t.sent.clear();
        rig.set(0,freq.id,500);
        check(rig.value(0,freq.id)==127&&rig.edited(0),"clamped to the control's range and marked edited");
        check(t.sent.size()==1&&t.sent.get(0).equals("param 0 "+freq.id+" 127"),"sent: "+t.sent);
        rig.set(0,freq.id,127);check(t.sent.size()==1,"an unchanged value is not sent again");
        rig.set(0,999,1);check(t.sent.size()==1,"no such control: nothing");

        // Choosing a preset again undoes the edits.
        t.sent.clear();rig.preset(0,rig.preset(0));
        check(!rig.edited(0)&&rig.value(0,freq.id)==c.presetValues(poly.id,rig.preset(0))[freq.id],"the preset again restores it");
        check(t.sent.size()==1+poly.controls.length,"and sends the whole part");
        // A new model starts at its first preset.
        int kit=c.find("Kit");
        rig.setModel(1,kit);check(rig.model(1).isKit()&&rig.preset(1)==0&&rig.bank(1)==0,"part 2 becomes a Kit");

        // Every preset's values are within their controls' ranges.
        for(Catalog.Model m:c.models)for(int p=0;p<m.presets.length;p++){
            int[] v=c.presetValues(m.id,p);check(v.length==m.controls.length,m.name+" preset "+p+" has every value");
            for(Catalog.Control cc:m.controls)check(v[cc.id]>=cc.min&&v[cc.id]<=cc.max,m.name+" "+m.presets[p]+" "+cc.label+" in range");
        }
        // Banks point at real controls (or the last pad's).
        for(Catalog.Model m:c.models)for(Catalog.Bank b:m.banks)for(int id:b.ids){
            if(id<0)continue;
            if(id>=Catalog.PAD_CONTROL)check(m.isKit()&&id<Catalog.PAD_CONTROL+4,"pad slots only on the Kit");
            else check(id<m.controls.length,m.name+" bank "+b.name+" points at a control");
        }
        // The Kit's pad slots follow the pad hit last.
        rig.lastPad=0;int k0=rig.resolve(3,Catalog.PAD_CONTROL);
        rig.lastPad=5;int k5=rig.resolve(3,Catalog.PAD_CONTROL+1);
        check(rig.control(3,k0).section.equals(rig.control(3,4).section)&&k5==4+5*4+1,"pad slots: "+k0+", "+k5);
        check(rig.control(3,k5).label.equals("DECAY"),"slot 2 is the pad's DECAY: "+rig.control(3,k5).label);
        rig.bank(0,99);check(rig.bank(0)==poly.banks.length-1,"bank clamped");

        // Saving and restoring keeps models, presets, edits and values.
        rig.set(2,freq.id,11);
        String saved=rig.save();
        Rig again=new Rig(c,null);again.restore(saved);
        for(int p=0;p<Rig.PARTS;p++){
            check(again.model(p).id==rig.model(p).id&&again.preset(p)==rig.preset(p)&&again.edited(p)==rig.edited(p),"part "+p+" restored");
            for(int i=0;i<rig.model(p).controls.length;i++)check(again.value(p,i)==rig.value(p,i),"part "+p+" value "+i);
        }
        check(again.value(2,freq.id)==11&&again.edited(2),"the edit survives");
        // Anything that no longer fits is left at its preset.
        Rig odd=new Rig(c,null);
        odd.restore("Nonesuch|0|1|1,2\nPoly 106|99|1|1,2,3\nKit|1|0|"+"\n");
        check(odd.presetName(0).equals("Brass Section"),"an unknown model is skipped");
        check(odd.model(1).name.equals("Poly 106")&&odd.preset(1)==0&&!odd.edited(1),"a bad preset falls back to the first, short values are ignored");
        check(odd.model(2).isKit()&&odd.preset(2)==1,"a model and preset without values");
        odd.restore(null);odd.restore("");
        System.out.println("PASS: rig: start sounds, changes clamped and sent once, presets, models, Kit pad slots, every preset in range, save and restore");
    }
}
