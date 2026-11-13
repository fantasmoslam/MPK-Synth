/* SPDX-License-Identifier: GPL-3.0-only */
package org.fantasmoslam.mpksynth.ui;

import java.util.HashSet;

/**
 * The built-in course: lessons made of steps. A step explains one thing with a picture and,
 * where there is something to do, waits until you have actually done it (played a key,
 * turned a knob, chosen a preset) before it moves on. The app reports what you do through
 * event(); the LEARN tab draws the lessons (Learn.java).
 */
public final class Tutorial {
    /** What a step waits for. NEXT waits for the Next button. */
    public static final int NEXT=0,CONNECTED=1,KEY=2,PAD=3,JOYSTICK=4,KNOB=5,PART_KEYS=6,PART_PADS=7,MODEL=8,PRESET=9,BANK=10,
        SCREEN_KNOB=11,PAD_BANK_B=12,BEND_UP=13,BEND_DOWN=14;
    /** The picture a step shows. */
    public static final int PIC_NONE=0,PIC_MPK=1,PIC_KEYS=2,PIC_PADS=3,PIC_KNOBS=4,PIC_JOYSTICK=5,PIC_TRANSPORT=6,
        PIC_SCREEN_PARTS=7,PIC_SCREEN_SOUND=8,PIC_FLOW=9,PIC_OSC=10,PIC_FILTER=11,PIC_ENVELOPE=12,PIC_LFO=13,PIC_PICKUP=14,PIC_KIT=15;

    public static final class Step{
        public final String title,text;public final int picture,wait,count;
        /** The region of the SOUND screen to point at while you do it, or null. */
        public final String region;
        Step(String title,String text,int picture,int wait,int count,String region){
            this.title=title;this.text=text;this.picture=picture;this.wait=wait;this.count=count;this.region=region;
        }
        public boolean needsSound(){return region!=null;}
    }
    public static final class Lesson{
        public final String title,blurb;public final Step[] steps;
        Lesson(String title,String blurb,Step... steps){this.title=title;this.blurb=blurb;this.steps=steps;}
    }
    static Step info(String title,String text,int picture){return new Step(title,text,picture,NEXT,1,null);}
    static Step doIt(String title,String text,int picture,int wait){return new Step(title,text,picture,wait,1,null);}
    static Step doIt(String title,String text,int picture,int wait,int count){return new Step(title,text,picture,wait,count,null);}
    static Step onScreen(String title,String text,int wait,String region){return new Step(title,text,PIC_NONE,wait,1,region);}

    public static final Lesson[] LESSONS={
        new Lesson("Plug in the MPK","Connect the keyboard and check the app sees it.",
            info("Welcome to MPK Synth","MPK Synth turns this tablet into a synthesizer you play from your Akai MPK mini Plus. These lessons teach everything step by step. Each one waits for you to try the thing it explains, so you learn by doing.",PIC_MPK),
            doIt("Connect the keyboard","Plug the MPK into the tablet with its USB cable (through a USB-C adapter if needed). The app finds it by itself. When it does, the badge at the top of the screen turns green and says MPK MINI CONNECTED.",PIC_MPK,CONNECTED),
            info("Turn it up","The VOLUME slider at the top right is the app's master volume. The tablet's own volume buttons work too. Start low with headphones, then bring it up.",PIC_NONE)),
        new Lesson("Play the keys and pads","Make your first sounds.",
            doIt("Play a key","Press any key on the MPK. You should hear a sound straight away.",PIC_KEYS,KEY),
            doIt("Play a few more","Play three different keys. Lower keys are lower notes. Hold a key down and the sound keeps going until you let go.",PIC_KEYS,KEY,3),
            doIt("Hit a pad","The 8 square pads play drums. Hit any pad. Hit harder and it plays louder: the pads feel how hard you hit them.",PIC_PADS,PAD),
            doIt("Pads 9 to 16","The pads have two banks of 8. Press PAD BANK on the MPK to switch to bank B, then hit a pad. Bank B plays the other 8 drums (hats, toms, cymbals).",PIC_PADS,PAD_BANK_B)),
        new Lesson("Parts: who plays what","Four sounds at once, and how to pick which one you play.",
            info("Four parts","The synth has four parts, shown across the top of the screen. Each part is its own instrument with its own sound. The keys play one part and the pads play another, so you can play a bass on the keys over drums on the pads.",PIC_SCREEN_PARTS),
            onScreen("Choose the keys part","Tap PART 2 at the top. The KEYS badge moves to it: the keys now play part 2. Then play a key to hear it.",PART_KEYS,"parts"),
            doIt("Hear the difference","Play a key. Part 2 has a different sound from part 1.",PIC_KEYS,KEY),
            onScreen("Choose the pads part","Each part also has a PADS button. Tap PADS on part 4 (the drums) to make sure the pads play the drum kit.",PART_PADS,"parts"),
            onScreen("Back to part 1","Tap PART 1 so the keys play part 1 again.",PART_KEYS,"parts")),
        new Lesson("Pick a sound","Presets, and the synth models.",
            info("Presets","A preset is a finished sound someone has set up for you. Every synth model comes with a set of them. Changing preset is the quickest way to find a sound you like.",PIC_SCREEN_SOUND),
            onScreen("Step through presets","On the SOUND screen, tap the › arrow next to the preset name. Play a key after each change to hear it. Try two or three.",PRESET,"preset"),
            onScreen("See them all","Tap the preset's name to open the full list, then tap one to choose it.",PRESET,"preset"),
            info("Models","A model is a whole synthesizer. Each one is built to sound and work like a famous instrument: Poly 106 is modelled on the Roland Juno-106, and Kit is a classic analog drum machine. More models are on the way: Voyage, Raw 20, Patch 26 and Big 80.",PIC_SCREEN_SOUND)),
        new Lesson("What a synth is made of","Oscillator, filter, amplifier, envelope and LFO, in plain words.",
            info("The signal path","Every sound here travels the same road: an OSCILLATOR makes a raw tone, a FILTER shapes how bright it is, and an AMPLIFIER sets how loud it is. An ENVELOPE moves things over the length of a note, and an LFO wobbles things up and down.",PIC_FLOW),
            info("Oscillator","The oscillator makes the raw sound. Its wave shape sets the character: a SAW is bright and buzzy, a PULSE (square) is hollow, and a SUB adds weight an octave below. On the Poly 106 these are in the DCO section.",PIC_OSC),
            info("Filter","The filter takes brightness away. Its cutoff (FREQ) says how much: low is dark and muffled, high is bright and open. RES (resonance) adds a whistle at the cutoff point. This is the most played control on any synth.",PIC_FILTER),
            onScreen("Try the filter","Hold a note and turn knob K1 on the MPK, which is the filter's FREQ on the Poly 106. Hear the sound go dark and bright.",KNOB,"panel"),
            info("Envelope","The envelope shapes each note over time with four stages: ATTACK (how fast it starts), DECAY (how fast it falls), SUSTAIN (the level while you hold the key) and RELEASE (how long it rings after you let go).",PIC_ENVELOPE),
            info("LFO","The LFO is a slow wobble. Sent to pitch it makes vibrato; to the filter it makes a wah-wah; to the pulse width it makes a shimmer. RATE sets its speed and DELAY lets it fade in after you start a note.",PIC_LFO)),
        new Lesson("The 8 knobs","How the MPK's knobs control the sound, and pickup.",
            info("Knob banks","The synth has more controls than the MPK has knobs, so its controls are grouped into banks of 8. The bank on the knobs is lit up on screen, and each lit control is labelled K1 to K8 like the MPK's knobs.",PIC_KNOBS),
            doIt("Turn a knob","Turn any of the MPK's 8 knobs while you play. The control it moves lights up on screen.",PIC_KNOBS,KNOB),
            onScreen("Change bank","Tap another bank name above the panel (DCO or ENV on the Poly 106). The knobs now control that bank.",BANK,"banks"),
            info("Pickup","When a sound changes (a new preset, or you moved a control on screen), the MPK's knobs are no longer where the sound is. So a knob does nothing until you turn it past the sound's value, and the sound never jumps. Until then the screen says TURN UP or TURN DOWN under it, with a mark where the knob really is.",PIC_PICKUP),
            onScreen("Touch controls","You can also drag any slider or knob on the screen with your finger, and tap a switch to change it.",SCREEN_KNOB,"panel")),
        new Lesson("Joystick and pressure","Bend notes.",
            doIt("Bend up","Hold a note and push the joystick sideways. The note bends up.",PIC_JOYSTICK,BEND_UP),
            doIt("Bend down","Now push the joystick up or down. The note bends down. Let go and it springs back to the right pitch.",PIC_JOYSTICK,BEND_DOWN),
            info("Bend range","Each model has a BEND control for how far the joystick bends, in semitones (2 is a whole tone, 12 a full octave).",PIC_NONE)),
        new Lesson("The Poly 106","A tour of the six-voice classic.",
            info("About it","The Poly 106 is modelled on the Roland Juno-106 (1984), loved for its warm, simple sound and its famous chorus. It plays six notes at once.",PIC_SCREEN_SOUND),
            info("Read the panel left to right","LFO (the wobble), DCO (the oscillator: RANGE for octave, SAW and PULSE switches, PWM, SUB and NOISE), HPF (cuts bass), VCF (the filter), VCA (loudness), ENV (the envelope) and CHORUS. That is the order the sound flows through.",PIC_SCREEN_SOUND),
            onScreen("The chorus","Tap the CHORUS switch and try OFF, I, II and I+II while you hold a chord. Chorus makes the sound wide and lush; it is half of what makes this synth famous.",SCREEN_KNOB,"panel"),
            info("Recipes","Bass: RANGE 16', SAW on, SUB up, FREQ low, ENV up, short DECAY. Pad: slow ATTACK and RELEASE, PWM on LFO, CHORUS II. Brass: SAW only, medium FREQ, ENV up, a little ATTACK.",PIC_NONE)),
        new Lesson("The drum kit","Play and shape the drums.",
            info("The Kit","Part 4 plays the Kit, an analog-style drum machine. Each of the 16 pads is its own drum: kick, snare, clap, hats, toms and cymbals.",PIC_KIT),
            onScreen("Put the knobs on the Kit","Tap PART 4. The keys and the MPK's knobs now work on the Kit, and the SOUND screen shows its 16 pads.",PART_KEYS,"parts"),
            doIt("Choose a drum to shape","Hit a pad on the MPK, or tap one on screen. It becomes the drum the knobs shape: K1 TUNE, K2 DECAY, K3 TONE and K4 LEVEL. K5 to K7 set the whole kit's LEVEL, DRIVE and how much VELOCITY matters.",PIC_KIT,PAD),
            onScreen("Shape it","Hit the pad again while you turn K1 to K4, or drag the controls on screen. Then tap PART 1 to put the keys back on part 1.",PART_KEYS,"panel")),
        new Lesson("Saving your sound","Nothing to remember.",
            info("It saves itself","Every change you make is kept. When you leave the app and come back, every part sounds the way you left it. A part whose sound you have changed shows a * after its preset name.",PIC_SCREEN_PARTS),
            info("Back to the preset","To undo your changes, choose the same preset again from the list.",PIC_NONE)),
        new Lesson("When something goes wrong","No sound, no MPK, a stuck note.",
            info("No sound","Check the VOLUME slider at the top and the tablet's own volume. If the top bar says AUDIO OFF, play a key: the app turns audio back on.",PIC_NONE),
            info("MPK not found","If the badge says PLUG IN THE MPK MINI: unplug the cable and plug it in again, check the adapter is a data adapter, and make sure the MPK's lights are on.",PIC_MPK),
            info("A stuck note","If a note keeps sounding, tap ALL NOTES OFF at the top of the screen.",PIC_NONE)),
    };

    public static final String[][] GLOSSARY={
        {"Part","One of the 4 instruments the synth plays at once."},
        {"Model","A whole synthesizer, built to sound like a famous one."},
        {"Preset","A finished sound you can choose and change."},
        {"Oscillator (DCO, VCO)","Makes the raw tone."},
        {"Filter (VCF)","Takes away brightness. FREQ is how much, RES adds a whistle."},
        {"HPF","High-pass filter: takes away bass."},
        {"Amplifier (VCA)","Sets the loudness."},
        {"Envelope (ENV, ADSR)","Shapes a note over time: Attack, Decay, Sustain, Release."},
        {"LFO","A slow wobble for vibrato, wah or shimmer."},
        {"PWM","Pulse width modulation: the pulse wave's shape moving, for a shimmering sound."},
        {"Chorus","Doubles the sound slightly out of tune for width and richness."},
        {"Pickup","An MPK knob waits until it reaches the sound's value before taking over."},
        {"Bank","A group of 8 controls on the MPK's knobs."},
        {"Velocity","How hard you play a key or pad."},
        {"Bend","Sliding a note's pitch up or down with the joystick."},
    };

    public int lesson=0,step=0;
    private final boolean[] done=new boolean[LESSONS.length];
    private int progress;
    private final HashSet<Integer> seen=new HashSet<>();
    /** When the current step was completed (ms), or 0. */
    public long completedAt;
    /** The step moves on this long after you did it, so you see that it worked. */
    public static final long ADVANCE_MS=900;

    public Lesson current(){return LESSONS[lesson];}
    public Step currentStep(){return LESSONS[lesson].steps[step];}
    public boolean done(int l){return done[l];}
    public int progress(){return progress;}
    public boolean complete(){return completedAt!=0;}

    public void open(int l){lesson=Math.max(0,Math.min(LESSONS.length-1,l));step=0;reset();}
    private void reset(){progress=0;seen.clear();completedAt=0;}
    public void next(){
        if(step<current().steps.length-1){step++;reset();return;}
        done[lesson]=true;
        if(lesson<LESSONS.length-1){lesson++;step=0;reset();}
        else reset();
    }
    public void back(){if(step>0){step--;reset();}else if(lesson>0){lesson--;step=LESSONS[lesson].steps.length-1;reset();}}
    public boolean last(){return lesson==LESSONS.length-1&&step==current().steps.length-1;}

    /** Something you did. Value tells distinct things apart (a key's note, a part). */
    public void event(int kind,int value,long now){
        Step s=currentStep();
        if(completedAt!=0||s.wait==NEXT)return;
        boolean match=kind==s.wait;
        if(s.wait==PAD_BANK_B)match=kind==PAD&&value>=44;
        if(s.wait==BEND_UP)match=kind==JOYSTICK&&value>1500;
        if(s.wait==BEND_DOWN)match=kind==JOYSTICK&&value<-1500;
        if(s.wait==PART_PADS&&kind==PART_PADS)match=true;
        if(!match)return;
        if(s.count>1){if(seen.add(value))progress++;}else progress=1;
        if(progress>=s.count)completedAt=now;
    }
    /** Called each frame: moves on a moment after a step is done. */
    public boolean tick(long now){
        if(completedAt!=0&&now-completedAt>=ADVANCE_MS){next();return true;}
        return false;
    }
    /** The step is already true (the MPK was connected before the step opened). */
    public void already(int kind,long now){event(kind,0,now);}

    /** Done lessons, as a bit mask for saving. */
    public long saveDone(){long m=0;for(int i=0;i<done.length;i++)if(done[i])m|=1L<<i;return m;}
    public void loadDone(long m){for(int i=0;i<done.length;i++)done[i]=(m>>i&1)!=0;}
    public boolean anyDone(){for(boolean d:done)if(d)return true;return false;}
}
