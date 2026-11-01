/* SPDX-License-Identifier: GPL-3.0-only */
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include "../native/capture.h"

int main(void){
    static pt_capture c;float out[512];int16_t got[1u<<16];
    /* Off: nothing is recorded and audio passes unchanged. */
    for(int i=0;i<512;++i)out[i]=.5f;
    capture_process(&c,out,256);assert(capture_read(&c,got,4096)==0&&out[0]==.5f);
    /* On: samples arrive in order, scaled and clipped to 16 bits. */
    capture_start(&c);
    for(int i=0;i<512;++i)out[i]=i%2?-2.f:(float)i/512;
    capture_process(&c,out,256);
    unsigned n=capture_read(&c,got,4096);assert(n==512);
    assert(got[0]==0&&got[1]==-32768&&got[2]==(int16_t)(2.f/512*32767+.5f));
    /* Wrap-around across the ring end keeps continuity. */
    unsigned total=0;int16_t expect=0;
    for(int block=0;block<6000;++block){
        for(int i=0;i<512;i+=2){out[i]=(float)((block*256+i/2)%30000)/32767.f;out[i+1]=0;}
        capture_process(&c,out,256);
        if(block%3==2){unsigned k;while((k=capture_read(&c,got,sizeof got/sizeof *got))){
            for(unsigned i=0;i<k;i+=2){assert(got[i]==expect);expect=(int16_t)((expect+1)%30000);}total+=k;}}
    }
    assert(total>PT_CAPTURE_SAMPLES&&atomic_load(&c.dropped)==0);
    /* A reader that stalls loses whole bursts, counted, never overwrites unread audio. */
    capture_read(&c,got,sizeof got/sizeof *got);
    for(unsigned i=0;i<PT_CAPTURE_SAMPLES/512+4;++i)capture_process(&c,out,256);
    assert(atomic_load(&c.dropped)>0);
    /* Fade: monotonically down to silence over the frames given, then silent; 0 restores. */
    capture_stop(&c);capture_fade(&c,1000);
    float last=1;
    for(int b=0;b<8;++b){
        for(int i=0;i<512;++i)out[i]=1;
        capture_process(&c,out,256);
        for(int i=0;i<512;i+=2){assert(out[i]<=last+1e-6f);last=out[i];}
    }
    assert(last==0&&capture_faded(&c));
    capture_fade(&c,0);for(int i=0;i<512;++i)out[i]=1;capture_process(&c,out,256);assert(out[0]==1&&!capture_faded(&c));
    puts("PASS: recording tap order, 16-bit scaling and clipping, ring wrap, overflow counted, run-end fade");
    return 0;
}
