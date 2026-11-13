/* SPDX-License-Identifier: GPL-3.0-only */
package org.fantasmoslam.mpksynth;

/** Nonblocking retry schedule. Activity owns intent/focus and cancels on Stop/pause. */
final class AudioRecovery {
    private boolean pending;
    private int attempts;
    private long retryAt;
    void begin(long now){pending=true;attempts=0;retryAt=now+150;}
    boolean due(long now){return pending && attempts<5 && now>=retryAt;}
    void failed(long now){++attempts;retryAt=now+Math.min(2000,250L<<(attempts-1));}
    void cancel(){pending=false;attempts=0;}
    boolean pending(){return pending;}
    boolean exhausted(){return pending&&attempts>=5;}
}
