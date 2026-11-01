/* SPDX-License-Identifier: GPL-3.0-only */
#include "../native/engine.h"
#include "../native/display.h"
#include <assert.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
static float audio[1024*2];
int main(void) {
    const char *load=getenv("PT_LOAD_PATH");
    if(load) {
        FILE*f=fopen(load,"rb");assert(f);
        uint8_t *before=malloc(1100000),*after=malloc(1100000);assert(before&&after);
        size_t n=fread(before,1,1100000,f);fclose(f);assert(n>1048592);
        pt_boot(before,n);assert(pt_save(after,1100000)==n);
        /* Compare the exact portable project after a fresh process boot. */
        assert(!memcmp(before+1048592,after+1048592,n-1048592));
        pt_render(audio,1024,48000);
        for(unsigned i=0;i<2048;++i)assert(isfinite(audio[i]));
        free(before);free(after);puts("PASS: session restored in a fresh process; exact project round trip");return 0;
    }
    pt_boot(NULL,0);
    assert(pt_control_count()==47);
    for(unsigned i=0;i<pt_control_count();++i){
        int v,lo,hi;char t[64];pt_control_read(i,&v,&lo,&hi,t);
        assert(lo<=v&&v<=hi&&t[0]&&pt_control_label(i)[0]);
    }
    int v,lo,hi,other;char text[64];
    pt_control_read(1,&other,&lo,&hi,text);
    pt_control_set(0,9999);pt_control_read(0,&v,&lo,&hi,text);assert(v==hi);
    pt_control_set(0,-9999);pt_control_read(0,&v,&lo,&hi,text);assert(v==lo);
    pt_control_read(1,&v,&lo,&hi,text);assert(v==other);
    pt_control_set(0,104);pt_control_set(8,-31);pt_control_read(8,&v,&lo,&hi,text);assert(v==-31);
    pt_control_set(12,96);pt_control_read(12,&v,&lo,&hi,text);assert(v==96);
    pt_control_set(39,1);pt_control_read(39,&v,&lo,&hi,text);assert(v==1);
    pt_control_set(13,0);pt_control_set(14,0);pt_control_set(999,123);
    puts("PASS: 47 tablet controls, range clamping, per-track isolation, tempo and biome");
    pt_keys(1u<<7);
    double sum=0; float peak=0;
    clock_t start=clock();
    for(unsigned block=0;block<1200;++block) {
        unsigned frames=block%3==0?192:block%3==1?384:257;
        pt_render(audio,frames,48000);
        for(unsigned i=0;i<frames*2;++i) {
            assert(isfinite(audio[i]));assert(fabsf(audio[i])<=1.05);
            sum+=fabsf(audio[i]);if(fabsf(audio[i])>peak)peak=fabsf(audio[i]);
        }
        if(block==100)pt_keys(0);
        if(block==150){pt_midi(0x90,60,100);}
        if(block==200){pt_midi(0x80,60,0);}
        if(block==250){pt_buttons(1u<<10);}
        if(block==260){pt_buttons(0);}
        if(block==500){pt_encoder(0,1);pt_encoder(1,1);}
    }
    assert(sum>1);pt_panic();
    /* The screen is drawn by the display copy from the engine's snapshot. */
    uint8_t *snap=malloc(PT_SNAPSHOT_MAX);assert(snap);uint32_t size=pt_snapshot(snap,PT_SNAPSHOT_MAX);
    assert(size>2&&!pt_snapshot(snap,100));
    pt_display_boot();assert(pt_display_apply(snap,size));assert(!pt_display_apply(snap,1));
    assert(pt_display_playing()==pt_playing());
    for(unsigned i=0;i<pt_control_count();++i){
        int a,b,c,d,e,f;char x[64],y[64];pt_control_read(i,&a,&b,&c,x);pt_display_control_read(i,&d,&e,&f,y);
        assert(a==d&&b==e&&c==f&&!strcmp(x,y));
    }
    /* Sound design: the engine writes a voice, the display copy reads it back with the firmware's own descriptors. */
    {
        unsigned engines[32],n=pt_engine_list(engines,32);assert(n>=12&&engines[0]==0);
        for(unsigned i=0;i<n;++i)assert(pt_engine_name(engines[i])[0]&&engines[i]!=13);
        unsigned presets[128],np=pt_preset_list(0,presets,128);assert(np>0&&pt_preset_name(0,presets[0])[0]);
        pt_voice_engine(1,0);pt_voice_preset(1,presets[np>1]);pt_voice_set(1,1,20);pt_voice_set(1,2,999);
        size=pt_snapshot(snap,PT_SNAPSHOT_MAX);assert(pt_display_apply(snap,size));
        char engine[16],preset[24];pt_voice_param p[8];
        assert(pt_display_voice(1,engine,preset,p)==0&&!strcmp(engine,"ANALOG")&&!strcmp(preset,pt_preset_name(0,presets[np>1])));
        assert(!strcmp(p[1].label,"DTN")&&p[1].value==20&&!strcmp(p[1].text,"20 ct")&&!p[1].bipolar);
        assert(p[2].value==p[2].max);                       /* clamped to the descriptor's range */
        char names[128][16];unsigned nn=pt_display_voice_names(1,0,names,128);
        assert(p[0].fmt==8&&nn==(unsigned)(p[0].max-p[0].min+1)&&names[0][0]);
        assert(pt_display_voice_names(1,1,names,128)==0);
        pt_voice_engine(1,13);                               /* SLICE is never offered */
        size=pt_snapshot(snap,PT_SNAPSHOT_MAX);assert(pt_display_apply(snap,size));
        assert(pt_display_voice(1,engine,preset,p)==0);
        unsigned nr=pt_display_control_names(35,names,64);assert(nr>=2&&names[0][0]);   /* track role choices */
        assert(pt_display_control_names(12,names,64)==0);                                   /* tempo is not a list */
        puts("PASS: sound design reads effective voice descriptors, clamps writes, lists engines and presets");
    }
    free(snap);
    uint32_t *pixels=calloc(240*240,sizeof *pixels);assert(pixels);pt_display_screen(pixels);
    unsigned nonblack=0;for(unsigned i=0;i<240*240;++i){assert((pixels[i]>>24)==255);if(pixels[i]&0xffffff)++nonblack;}
    assert(nonblack>100);free(pixels);
    uint8_t *saved=malloc(1100000);assert(saved);
    size_t n=pt_save(saved,1100000);assert(n>1048576&&n<1100000);assert(!memcmp(saved,"PTANDROID001",12));
    assert(pt_save(saved,100)==0);
    const char *path=getenv("PT_SAVE_PATH");if(path){FILE*f=fopen(path,"wb");assert(f);assert(fwrite(saved,1,n,f)==n);fclose(f);}
    free(saved);
    printf("PASS: variable callbacks, 44.1->48 kHz, notes, MIDI, sequencer, display snapshot and controls, save; peak %.4f; CPU %.2fs\n",peak,(double)(clock()-start)/CLOCKS_PER_SEC);
}
