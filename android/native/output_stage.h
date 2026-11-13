/* SPDX-License-Identifier: GPL-3.0-only */
#pragma once
#include <math.h>

/* Android output only: recover the codec's 6 dB reserve without changing firmware.
 * The limiter links stereo gain, attacks immediately, and releases over 50 ms.
 * Gain changes approach the requested value over a 5 ms time constant.
 */
typedef struct { float gain, limiter, gain_step, release; unsigned limited; } pt_output_t;
static inline void pt_output_init(pt_output_t *s, unsigned rate, float gain) {
    s->gain=gain; s->limiter=1; s->limited=0;
    s->gain_step=1.0f/(0.005f*rate); s->release=1.0f/(0.05f*rate);
}
static inline float pt_output_process(pt_output_t *s,float *audio,unsigned frames,float target) {
    float peak=0;
    for(unsigned i=0;i<frames;++i) {
        s->gain+=(target-s->gain)*s->gain_step;
        float l=audio[2*i],r=audio[2*i+1];
        if(!isfinite(l))l=0;
        if(!isfinite(r))r=0;
        l*=s->gain; r*=s->gain;
        float magnitude=fmaxf(fabsf(l),fabsf(r));
        float limit=magnitude>0.98f?0.98f/magnitude:1.0f;
        if(limit<s->limiter){s->limiter=limit;++s->limited;}
        else s->limiter+=(limit-s->limiter)*s->release;
        audio[2*i]=l*s->limiter; audio[2*i+1]=r*s->limiter;
        peak=fmaxf(peak,fmaxf(fabsf(audio[2*i]),fabsf(audio[2*i+1])));
    }
    return peak;
}
