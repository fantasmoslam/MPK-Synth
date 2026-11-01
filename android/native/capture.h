/* SPDX-License-Identifier: GPL-3.0-only */
#pragma once
/* Recording tap and run-end fade on the final stereo mix (after the limiter).
 * The audio callback fades and writes; one reader thread drains. Lock-free:
 * the callback never blocks or allocates, and drops a whole burst (counted) when
 * the reader falls more than the ring behind rather than overwrite unread audio. */
#include <stdatomic.h>
#include <stdint.h>
#include <string.h>

#define PT_CAPTURE_SAMPLES (1u<<20)   /* interleaved stereo int16: ~10.9 s at 48 kHz */
typedef struct {
    int16_t ring[PT_CAPTURE_SAMPLES];
    _Atomic unsigned w, r, dropped, on;
    _Atomic unsigned fade_total, fade_left;   /* frames; fade_total 0 means unity gain */
} pt_capture;

static void capture_start(pt_capture *c){
    atomic_store(&c->on,0);
    atomic_store(&c->r,atomic_load(&c->w));atomic_store(&c->dropped,0);
    atomic_store(&c->on,1);
}
static void capture_stop(pt_capture *c){atomic_store(&c->on,0);}
/* Begins a fade to silence over frames (0 restores unity gain at once). */
static void capture_fade(pt_capture *c,unsigned frames){
    atomic_store(&c->fade_left,frames);atomic_store(&c->fade_total,frames);
}
static int capture_faded(pt_capture *c){return atomic_load(&c->fade_total)&&!atomic_load(&c->fade_left);}

/* Audio thread: apply the fade, then record. */
static void capture_process(pt_capture *c,float *out,unsigned frames){
    unsigned total=atomic_load_explicit(&c->fade_total,memory_order_relaxed);
    if(total){
        unsigned left=atomic_load_explicit(&c->fade_left,memory_order_relaxed);
        for(unsigned i=0;i<frames;++i){
            float t=(float)left/(float)total,g=t*t;   /* quadratic: sounds even to the ear */
            out[2*i]*=g;out[2*i+1]*=g;
            if(left)--left;
        }
        atomic_store_explicit(&c->fade_left,left,memory_order_relaxed);
    }
    if(!atomic_load_explicit(&c->on,memory_order_acquire))return;
    unsigned w=atomic_load_explicit(&c->w,memory_order_relaxed),r=atomic_load_explicit(&c->r,memory_order_acquire);
    unsigned need=frames*2;
    if(need>PT_CAPTURE_SAMPLES-(w-r)){atomic_fetch_add_explicit(&c->dropped,frames,memory_order_relaxed);return;}
    for(unsigned i=0;i<need;++i){
        float x=out[i]*32767.f;
        x=x>32767.f?32767.f:x<-32768.f?-32768.f:x;
        c->ring[(w+i)&(PT_CAPTURE_SAMPLES-1)]=(int16_t)(x<0?x-.5f:x+.5f);
    }
    atomic_store_explicit(&c->w,w+need,memory_order_release);
}
/* Reader thread: copies up to max samples (a multiple of 2) and returns how many. */
static unsigned capture_read(pt_capture *c,int16_t *dst,unsigned max){
    unsigned r=atomic_load_explicit(&c->r,memory_order_relaxed),w=atomic_load_explicit(&c->w,memory_order_acquire);
    unsigned n=w-r;if(n>max)n=max&~1u;
    for(unsigned i=0;i<n;){
        unsigned at=(r+i)&(PT_CAPTURE_SAMPLES-1),run=PT_CAPTURE_SAMPLES-at;
        if(run>n-i)run=n-i;
        memcpy(dst+i,c->ring+at,run*sizeof *dst);i+=run;
    }
    atomic_store_explicit(&c->r,r+n,memory_order_release);
    return n;
}
