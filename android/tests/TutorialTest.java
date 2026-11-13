/* SPDX-License-Identifier: GPL-3.0-only */
package org.fantasmoslam.mpksynth.ui;

import java.util.Arrays;
import java.util.List;

/** The lessons: every step is well formed, steps wait for what they ask, and progress is kept. */
public final class TutorialTest {
    static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}

    public static void main(String[] args){
        List<String> regions=Arrays.asList("parts","preset","panel","banks");
        int steps=0;
        for(Tutorial.Lesson l:Tutorial.LESSONS){
            check(l.steps.length>0&&!l.title.isEmpty()&&!l.blurb.isEmpty(),"lesson "+l.title);
            for(Tutorial.Step s:l.steps){
                steps++;
                check(!s.title.isEmpty()&&s.text.length()>20,"step text: "+s.title);
                check(s.region==null||regions.contains(s.region),"step points at a real region: "+s.region);
                check(s.picture>=Tutorial.PIC_NONE&&s.picture<=Tutorial.PIC_KIT,"picture: "+s.title);
                check(s.wait>=Tutorial.NEXT&&s.wait<=Tutorial.BEND_DOWN&&s.count>=1,"wait: "+s.title);
            }
        }
        check(Tutorial.LESSONS.length>=10&&steps>=40,"a full course: "+Tutorial.LESSONS.length+" lessons, "+steps+" steps");

        Tutorial t=new Tutorial();long now=1000;
        // Lesson 2, step 2: three different keys.
        t.open(1);t.next();
        check(t.currentStep().wait==Tutorial.KEY&&t.currentStep().count==3,"play three keys");
        t.event(Tutorial.KEY,60,now);t.event(Tutorial.KEY,60,now);
        check(t.progress()==1&&!t.complete(),"the same key twice counts once");
        t.event(Tutorial.PAD,36,now);check(t.progress()==1,"a pad is not a key");
        t.event(Tutorial.KEY,62,now);t.event(Tutorial.KEY,64,now+=10);
        check(t.complete(),"three keys done");
        check(!t.tick(now+Tutorial.ADVANCE_MS-1),"it waits a moment so you see it worked");
        check(t.tick(now+Tutorial.ADVANCE_MS)&&t.step==2,"then moves on by itself");
        // Pads bank B needs a note from 44 up.
        t.next();check(t.currentStep().wait==Tutorial.PAD_BANK_B,"bank B step");
        t.event(Tutorial.PAD,40,now);check(!t.complete(),"bank A does not count");
        t.event(Tutorial.PAD,45,now);check(t.complete(),"bank B does");
        // The joystick bends both ways.
        t.open(6);
        t.event(Tutorial.JOYSTICK,-3000,now);check(!t.complete(),"down is not up");
        t.event(Tutorial.JOYSTICK,3000,now);check(t.complete(),"up");
        t.next();t.event(Tutorial.JOYSTICK,-3000,now);check(t.complete(),"down");
        // Info steps wait for Next, and events do nothing there.
        t.open(0);t.event(Tutorial.KEY,60,now);check(!t.complete()&&t.currentStep().wait==Tutorial.NEXT,"info steps wait for Next");
        // Going through every step finishes every lesson.
        t.open(0);
        for(int i=0;i<steps;i++)t.next();
        for(int l=0;l<Tutorial.LESSONS.length;l++)check(t.done(l),"lesson "+(l+1)+" done");
        long mask=t.saveDone();Tutorial u=new Tutorial();u.loadDone(mask);
        check(u.done(0)&&u.done(Tutorial.LESSONS.length-1)&&u.anyDone(),"progress saved");
        // Back steps into the previous lesson's last step.
        u.open(2);u.back();check(u.lesson==1&&u.step==Tutorial.LESSONS[1].steps.length-1,"back crosses lessons");
        u.open(0);u.back();check(u.lesson==0&&u.step==0,"back stops at the start");
        System.out.println("PASS: tutorial: "+Tutorial.LESSONS.length+" lessons, "+steps+" steps, each waits for what it asks, advances by itself, progress saved");
    }
}
