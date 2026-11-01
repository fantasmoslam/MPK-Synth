/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth.ui;

/**
 * What the interface reads from and sends to the app. The tablet backs it with the
 * native engine; previews back it with fixed example values. Slots are the rows of
 * android/native/controls.h.
 */
public interface Engine {
    int LEVEL=0,MUTE=4,PAN=8,TEMPO=12,HABITAT=13,DRONE=14,DANGER=21,EVOLVING=23,HOLD=24,LAYER=27,ROLE=35,BIOME=39,MOOD=40;
    int SCAPE_START_ENVIRONMENT=1,SCAPE_PAUSE_ENVIRONMENT=2,SCAPE_DRONE_ON=3,SCAPE_DRONE_OFF=4,
        SCAPE_PLAY_MUSIC=5,SCAPE_STOP_MUSIC=6,SCAPE_NEXT_HARMONY=9;
    String[] LAYERS={"WIND","WATER","RAIN","INSECTS","FROGS","BIRDS","DRIPS","RUMBLE"};

    /** One entry of a list the engine offers: a voice (engine) or a preset. */
    final class Choice{
        public final int id;public final String name;
        public Choice(int id,String name){this.id=id;this.name=name;}
    }
    /** One of a voice's eight edit parameters, with the engine's own descriptor and wording. */
    final class Param{
        public final String label,text;public final int min,max,value;public final boolean list,bipolar;
        public Param(String label,boolean list,int min,int max,int value,boolean bipolar,String text){
            this.label=label;this.list=list;this.min=min;this.max=max;this.value=value;this.bipolar=bipolar;this.text=text;
        }
        /** Engines leave unused slots blank or "-". */
        public boolean used(){return label!=null&&!label.isEmpty()&&!label.equals("-");}
    }
    /** A part's voice: its engine (by id and name), preset name and parameters. */
    final class Voice{
        public final int engine;public final String engineName,preset;public final Param[] params;
        public Voice(int engine,String engineName,String preset,Param[] params){this.engine=engine;this.engineName=engineName;this.preset=preset;this.params=params;}
    }

    int value(int slot);
    int min(int slot);
    int max(int slot);
    /** The engine's own formatted value, with unit. */
    String text(int slot);
    /** The text of every value of a short list control (role, biome, mood), from min. */
    String[] names(int slot);
    void set(int slot,int value);
    boolean scape(int command);
    boolean audioOn();
    boolean musicPlaying();
    int master();
    void setMaster(int value);
    void stopAll();
    void saveSession();
    /** Settings rows that open the app's existing dialogs. */
    void action(String name);

    /** Sound design: part 0..3. */
    Voice voice(int part);
    /** Value names of a list parameter. */
    String[] valueNames(int part,int param);
    void setParam(int part,int param,int value);
    Choice[] engines();
    Choice[] presets(int engine);
    void setEngine(int part,int engine);
    void setPreset(int part,int preset);

    /** Imported sounds for the keyboard and MIDI layer. */
    Library library();
    /**
     * False while the old DINOBOX Scape is switched off and the new conductor and habitat
     * are not yet ported: the environment, drone, layers and music controls wait.
     */
    boolean habitatEngine();
    /** True when the new Scape conductor (PALEO) is in this build: Play music and the notes, actions and gauges work. */
    default boolean musicEngine(){return habitatEngine();}
    /** The player's notes to the conductor, 0..100 (Danger, Majesty, Density, Pace, Space, Wildness, Drift, Silence).
     *  Kept before an expedition starts and carried into it. */
    default void conductorNote(int note,int value){}
    default int conductorNoteValue(int note){return 50;}
    /** An expedition action, in Expedition.ACTIONS order: the music turns within a beat or two. */
    default void conductorAction(int action){}
    /** The conductor's eight gauges 0..1 (Threat, Awe, Parts, Phrases, Depth, Habitat, Harmony, Music); negative = not answering. */
    default float[] conductorGauges(){return null;}
    /** A gauge's word, e.g. "WALKING" or "bII"; empty when it has none. */
    default String conductorGaugeText(int gauge){return "";}
    /** The scene playing now (0 dawn .. 12 coda), -1 when silent. */
    default int conductorScene(){return -1;}
    /** Recording takes of the final mix. */
    Recorder recorder();
    /** Fades the whole mix to silence over ms; 0 restores full level. */
    void fade(int ms);
    /** True once a fade has reached silence. */
    boolean faded();
}
