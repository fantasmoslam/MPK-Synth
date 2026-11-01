/* SPDX-License-Identifier: GPL-3.0-only
 * Native platform adapter for the existing portable firmware build.
 * Engine functions have exactly one owner: the audio callback while running,
 * the lifecycle thread only after the stream is closed. No JNI here.
 */
#include "engine.h"
#include <string.h>
#ifndef PT_ENGINE_DRAWS
/* The engine runs the firmware's whole UI frame, every state change of ui_draw
 * included, but rasterizes nothing: each canvas opens 0 x 0, so all drawing clips
 * away. display.c draws the picture on the UI thread from pt_snapshot. */
#define GFX_HOOKS 0
#define GFX_HOOK_TEXT(x0, y0, x1, y1, s, flags) ((void)0)
#define GFX_HOOK_BLIT(x, y, r0) ((void)0)
#define GFX_HOOK_BEGIN() (w = h = 0)
#define GFX_HOOK_PIXELS(n) ((void)0)
#endif
#include "../../web/emu/felucca_web.c"
#include "controls.h"
#include "converter.h"

static float cache_l[128], cache_r[128];
static unsigned cache_pos = 128;
static pt_converter conv;
static void next(float *v) {
    if (cache_pos == 128) {
        web_render(128);
        memcpy(cache_l, web_out_l(), sizeof cache_l);
        memcpy(cache_r, web_out_r(), sizeof cache_r);
        cache_pos = 0;
    }
    v[0] = cache_l[cache_pos]; v[1] = cache_r[cache_pos++];
}
void pt_prepare(unsigned rate) { conv_prepare(&conv, web_sample_rate(), rate); }
void pt_boot(const uint8_t *saved, size_t size) {
    web_nor_erase();
    size_t nor_size = web_nor_size(), project_size = web_project_size();
    int valid = saved && size == 16 + nor_size + project_size &&
        !memcmp(saved, "PTANDROID001", 12);
    if (valid) memcpy(web_nor(), saved + 16, nor_size);
    web_boot();
    /* Finish the firmware splash before the first output callback. */
    for (unsigned i = 0; i < 180; ++i) web_render(128);
    if (valid && project_size <= web_project_import_capacity()) {
        memcpy(web_project_import_buffer(), saved + 16 + nor_size, project_size);
        web_project_restore((uint32_t)project_size);
    }
    cache_pos = 128; conv_reset(&conv);
}
void pt_render(float *out, unsigned frames, unsigned rate) {
    if (rate != conv.rate) pt_prepare(rate);   /* (tests; the app prepares before start) */
    conv_run(&conv, out, frames, next);
}
void pt_keys(uint32_t mask) { web_keys(mask); }
void pt_buttons(uint32_t mask) { web_buttons(mask); }
void pt_encoder(unsigned role, int steps) { web_enc(role, steps); }
void pt_master(unsigned value) { web_master(value); }
void pt_midi(unsigned status, unsigned d1, unsigned d2) { web_midi(status, d1, d2); }
void pt_panic(void) {
    web_keys(0); web_buttons(0);
    /* Direct voice kill also covers a full MIDI queue or detached controller. */
    for (unsigned k = 0; k < NTRK; ++k) {
        trk_all_off(&trk[k]);
    }
}
unsigned pt_playing(void) { return web_playing(); }
size_t pt_save(uint8_t *out, size_t cap) {
    size_t n = web_nor_size(), p = web_project_size();
    if (cap < 16+n+p) return 0;
    uint8_t *project = web_project_capture();
    if (!project) return 0;
    memset(out, 0, 16); memcpy(out, "PTANDROID001", 12);
    memcpy(out+16, web_nor(), n); memcpy(out+16+n, project, p);
    return 16+n+p;
}

unsigned pt_control_count(void){return PT_CONTROL_COUNT;}
const char *pt_control_label(unsigned slot){return slot<PT_CONTROL_COUNT?controls[slot].label:"";}
void pt_control_read(unsigned slot,int *value,int *min,int *max,char *text){control_read(slot,value,min,max,text);}
void pt_control_set(unsigned slot,int value){
    if(slot==pt_control_count()){transport_req=value?1:2;return;}
    if(slot>=pt_control_count())return;
    const pt_control *c=&controls[slot];
    const param_desc_t *d=c->track<0?&GP[c->id]:&TP[c->id];
    value=param_fit(d,value);
    if(c->track<0){
        if(c->id==G_DREAM&&!value){pt_scape(PT_MUSIC_STOP);return;}
        if(c->id==G_SBIOME)scape_biome((unsigned)value);
        song.g[c->id]=(int16_t)value;
    }else{trk[c->track].p[c->id]=(int16_t)value;motion_capture(&trk[c->track],c->id,(int16_t)value);}
    ui.force=1;
}

void pt_voice_set(unsigned tr,unsigned k,int value){
    track_t *t=&trk[tr%NTRK];unsigned id=P_E0+(k&7u);const param_desc_t *d=track_desc(t,id);
    if(d->max>d->min)t->p[id]=(int16_t)enum_orig(d,clamp(value,d->min,d->max));
    (void)motion_capture(t,id,t->p[id]);ui.force=1;
}
void pt_voice_engine(unsigned tr,unsigned engine){
    if(!eng_ok(engine)||engine==ENGI_SLICE)return;
    set_engine_of(&trk[tr%NTRK],engine);ui.force=1;
}
void pt_voice_preset(unsigned tr,unsigned preset){
    track_t *t=&trk[tr%NTRK];const engine_t *e=ENGINES[eng_idx(t->eng_req)];
    if(preset<e->npresets){apply_preset_to(t,preset);ui.force=1;}
}
unsigned pt_engine_list(unsigned *out,unsigned cap){
    unsigned n=0;for(unsigned i=0;i<NENG_SHOWN&&n<cap;++i){unsigned e=eng_vis(i);if(eng_ok(e)&&e!=ENGI_SLICE)out[n++]=e;}return n;
}
const char *pt_engine_name(unsigned engine){return engine<NENGINES?ENGINES[engine]->name:"";}
unsigned pt_preset_list(unsigned engine,unsigned *out,unsigned cap){
    if(engine>=NENGINES)return 0;const engine_t *e=ENGINES[engine];unsigned n=0;
    for(unsigned k=0;k<e->npresets&&n<cap;++k)if(!preset_hidden(e,k))out[n++]=k;return n;
}
const char *pt_preset_name(unsigned engine,unsigned preset){
    return engine<NENGINES&&preset<ENGINES[engine]->npresets?ENGINES[engine]->presets[preset].name:"";
}

/* Android performance actions. A switch has an audible action, not merely an
 * evolution flag. Pattern composition remains a separate confirmed action. */
static int scape_last_environment=80,scape_last_drone=40;
unsigned pt_scape_busy(void){return chain_busy()!=0;}
void pt_scape(unsigned command){
    switch(command){
    case PT_ENV_START:
        if(!song.g[G_SCAPE])song.g[G_SCAPE]=(int16_t)scape_last_environment;
        break;
    case PT_ENV_STOP:
        if(song.g[G_SCAPE]>0)scape_last_environment=song.g[G_SCAPE];
        song.g[G_SCAPE]=0;break;
    case PT_DRONE_START:
        if(song.g[G_SCAPE]>0&&!song.g[G_SDRN])song.g[G_SDRN]=(int16_t)scape_last_drone;
        break;
    case PT_DRONE_STOP:
        if(song.g[G_SDRN]>0)scape_last_drone=song.g[G_SDRN];
        song.g[G_SDRN]=0;break;
    case PT_MUSIC_START:
        if(chain_busy())return;
        song.g[G_DREAM]=1;
        if(!dream.valid)dr_rebuild();else dr_arm();
        transport_req=1;break;
    case PT_MUSIC_STOP:
        song.g[G_DREAM]=0;dream_forget();transport_req=0;seq_stop();pt_panic();break;
    case PT_SCAPE_STOP_ALL:
        pt_scape(PT_ENV_STOP);pt_scape(PT_DRONE_STOP);pt_scape(PT_MUSIC_STOP);
        /* Stop All is called with Android audio closed. Clear shared effects and
         * residual voices too, so a later resume cannot bring back an old drone. */
        for(unsigned k=0;k<NPART;++k){
            for(unsigned v=0;v<NVOICE;++v)trk[k].v[v].active=0;
            trk[k].xp_n=0;trk[k].tail=0;trk[k].dist_hp=trk[k].dist_lp1=trk[k].dist_lp2=0;
        }
        memset(dly_buf,0,sizeof dly_buf);memset(cho_buf,0,sizeof cho_buf);
        memset(rev_comb,0,sizeof rev_comb);memset(&rev_u,0,sizeof rev_u);memset(&fx,0,sizeof fx);
        memset(&sc,0,sizeof sc);memset(mx,0,sizeof mx);
        lc_l1=lc_l2=lc_r1=lc_r2=dc_l=dc_r=dce_l=dce_r=0;memset(lce,0,sizeof lce);
        sb_lp1=sb_lp2=sb_lp3=sb_lp4=sb_env=sb_h1=sb_h2=sb_hl=0;lim_env=LIM_T;
        memset(&clk,0,sizeof clk);click_req=0;memset(&pf,0,sizeof pf);
        memset(tp_buf,0,sizeof tp_buf);memset(&tp,0,sizeof tp);
        memset(abuf,0,sizeof abuf);ring_r=ring_w;cache_pos=128;conv_reset(&conv);break;
    case PT_SCAPE_GENERATE:
        if(chain_busy())return;
        dream_compose(dream.valid?dream.root:(unsigned)((trk[0].p[P_ROOT]%12+12)%12));break;
    case PT_SCAPE_NEXT:
        if(scape_music.active&&dream.valid)dr_chord();break;
    default:return;
    }
    ui.force=1;
}

unsigned pt_ui_frames(void){return felucca_dbg.ui_frames;}
/* The display projection after the latest UI frame: transport and chain flags,
 * then the firmware's own second-screen snapshot (remote_state.c). */
uint32_t pt_snapshot(uint8_t *out,uint32_t cap){
    if(cap<2+RS_MAX||!rs_capture())return 0;
    out[0]=(uint8_t)(song.playing!=0);out[1]=(uint8_t)(chain_busy()!=0);
    memcpy(out+2,rs_buf,rs_len);return 2+rs_len;
}
