/* SPDX-License-Identifier: GPL-3.0-only */
package org.fantasmoslam.mpksynth;
public final class AudioRecoveryTest {
    public static void main(String[] args){
        AudioRecovery r=new AudioRecovery();
        assert !r.due(1000);
        r.begin(1000);assert !r.due(1149);assert r.due(1150);
        r.failed(1150);assert !r.due(1399);assert r.due(1400);
        r.cancel();assert !r.due(999999);assert !r.pending();
        r.begin(2000);
        long now=2150;
        for(int i=0;i<5;++i){assert r.due(now);r.failed(now);now+=3000;}
        assert r.exhausted();assert !r.due(now);
        r.begin(now);assert !r.exhausted();assert r.due(now+150);
        r.cancel();assert !r.pending();
        System.out.println("PASS: route debounce, bounded backoff, cancellation, exhaustion, new-route retry");
    }
}
