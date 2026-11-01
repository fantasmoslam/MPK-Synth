/* SPDX-License-Identifier: GPL-3.0-only */
package org.paleotone.synth.ui;

/**
 * The run clock and its end. The clock counts while the environment or music plays. At a timed run's
 * length (or End run on any run) the clock holds, the whole mix fades over five
 * seconds (an active take records the fade), then the take is saved and audio stops.
 * Stop All during the fade ends at once, keeping what was recorded.
 */
public final class Run {
    public static final int FADE_MS=5000;
    public static final long MIN_CUSTOM=5*60000L,MAX_CUSTOM=240*60000L;
    private final UiState s;private final Engine e;

    public Run(UiState state,Engine engine){s=state;e=engine;}

    /** Called regularly from the app with the time since the last call. */
    public void tick(long now,long dt,boolean audio){
        if(s.runPhase==UiState.RUNNING){
            if(audio&&dt>0)s.runElapsed+=dt;
            s.expedition().tick(Math.max(0,dt),audio,s.runLength);
            if(s.runLength>0&&s.runElapsed>=s.runLength){s.runElapsed=s.runLength;end(now);}
        }else if(s.runPhase==UiState.ENDING&&(now-s.fadeStart>=FADE_MS||e.faded()))finish();
    }
    /** Enters the closing fade (End run, or the timer). */
    public void end(long now){
        if(s.runPhase!=UiState.RUNNING)return;
        s.runPhase=UiState.ENDING;s.fadeStart=now;s.endDismissed=false;
        s.screen=UiState.Screen.EXPEDITION;s.quickMix=false;
        e.fade(FADE_MS);
    }
    /** Ends now: saves any active take and stops all sound. */
    public void finish(){
        if(s.runPhase==UiState.ENDED)return;
        s.runPhase=UiState.ENDED;
        Recorder r=e.recorder();if(r!=null&&r.state()==Recorder.RECORDING)r.stop();
        e.stopAll();
    }
    /** Stop All: during the fade it ends the run at once; otherwise it only stops sound. */
    public void stopAll(){
        if(s.runPhase==UiState.ENDING)finish();
        else e.stopAll();
    }
    /** Begins a new run with the chosen length. Earlier takes are kept. */
    public void newRun(){
        Recorder r=e.recorder();if(r!=null&&r.state()==Recorder.RECORDING)r.stop();
        if(s.runPhase==UiState.ENDING)e.fade(0);
        s.runNumber++;s.runElapsed=0;s.runLength=s.nextRunLength;s.runPhase=UiState.RUNNING;s.endDismissed=false;
        s.beginExpedition();
    }
    public static String lengthText(long ms){return ms==0?"Continuous":(ms/60000)+" min";}
}
