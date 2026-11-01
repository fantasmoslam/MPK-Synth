/* SPDX-License-Identifier: GPL-3.0-only */
#pragma once
/* The tablet's control table. Included after the firmware by both the engine
 * (which sets controls) and the display copy (which reads them for the UI). */
typedef struct {const char *label;int track;unsigned id;} pt_control;
/* Track >=0 is explicit: dragging a mixer fader cannot edit another selected track. */
static const pt_control controls[]={
 {"Track 1 level",0,P_LEVEL},{"Track 2 level",1,P_LEVEL},{"Track 3 level",2,P_LEVEL},{"Track 4 level",3,P_LEVEL},
 {"Track 1 mute",0,P_MUTE},{"Track 2 mute",1,P_MUTE},{"Track 3 mute",2,P_MUTE},{"Track 4 mute",3,P_MUTE},
 {"Track 1 pan",0,P_PAN},{"Track 2 pan",1,P_PAN},{"Track 3 pan",2,P_PAN},{"Track 4 pan",3,P_PAN},
 {"Tempo",-1,G_BPM},{"Habitat level",-1,G_SCAPE},{"Drone level",-1,G_SDRN},
 {"Day / night",-1,G_STIME},{"Distance",-1,G_SDIST},{"Music activity",-1,G_DDENS},
 {"Evolution",-1,G_DDRFT},{"Motion",-1,G_SMOTION},{"Creature call rate",-1,G_SCRATE},
 {"Danger",-1,G_SDNGR},{"Creature size",-1,G_SDSIZ},{"Evolving music",-1,G_DREAM},
 {"Hold harmony",-1,G_SCHOLD},{"Next harmony",-1,G_SCNEXT},{"New phrases",-1,G_SCNEW},
 {"Wind",-1,G_SL0},{"Water",-1,G_SL1},{"Rain",-1,G_SL2},{"Insects",-1,G_SL3},
 {"Frogs",-1,G_SL4},{"Birds",-1,G_SL5},{"Drips",-1,G_SL6},{"Rumble",-1,G_SL7},
 {"Track 1 role",-1,G_SROLE0},{"Track 2 role",-1,G_SROLE1},{"Track 3 role",-1,G_SROLE2},{"Track 4 role",-1,G_SROLE3},
 {"Biome",-1,G_SBIOME},{"Music mood",-1,G_DMOOD},{"Call track",-1,G_SCTRK},
 {"Pitch spread",-1,G_SCSPRD},{"Bed",-1,G_SBED},{"Melody",-1,G_SMELO},{"Bass",-1,G_SBASS},{"Pulse",-1,G_SPULSE}
};
#define PT_CONTROL_COUNT (sizeof controls/sizeof controls[0])
static void control_read(unsigned slot,int *value,int *min,int *max,char *text){
    if(slot>=PT_CONTROL_COUNT){*value=*min=*max=0;text[0]=0;return;}
    const pt_control *c=&controls[slot];
    const param_desc_t *d=c->track<0?&GP[c->id]:&TP[c->id];
    *value=c->track<0?song.g[c->id]:trk[c->track].p[c->id];*min=d->min;*max=d->max;
    uint8_t before=fmt_named;
    char formatted[64];const char *unit=0;param_format(d,*value,formatted,&unit);
    fmt_named=before;
    snprintf(text,64,"%s%s%s",formatted,unit&&*unit?" ":"",unit?unit:"");
}

/* Every value's text of a control with a short range (role, biome, mood), in order from min. */
static unsigned control_names(unsigned slot,char names[][16],unsigned cap){
    if(slot>=PT_CONTROL_COUNT)return 0;
    const pt_control *c=&controls[slot];const param_desc_t *d=c->track<0?&GP[c->id]:&TP[c->id];
    if(d->max-d->min>=(int)cap)return 0;
    unsigned n=0;
    for(int v=d->min;v<=d->max;++v,++n){
        uint8_t before=fmt_named;char formatted[64];const char *unit=0;param_format(d,v,formatted,&unit);fmt_named=before;
        snprintf(names[n],16,"%s%s%s",formatted,unit&&*unit?" ":"",unit?unit:"");
    }
    return n;
}

/* Sound design (round-three interface). A part's voice as the firmware's own editor
 * sees it: the effective descriptor of each of the engine's eight EDIT parameters
 * (track_desc applies the engine's mode-dependent labels and value names), its value
 * and the firmware's formatted text. Read by the display copy from snapshots. */
#include "voice.h"
static unsigned voice_read(unsigned tr,char *engine,char *preset,pt_voice_param *out){
    const track_t *t=&trk[tr%NTRK];unsigned ei=eng_idx(t->eng_req);const engine_t *e=ENGINES[ei];
    snprintf(engine,16,"%s",e->name);
    snprintf(preset,24,"%s",t->user?"USER":e->npresets&&t->preset<e->npresets?e->presets[t->preset].name:"");
    for(unsigned k=0;k<8;++k){
        const param_desc_t *d=track_desc(t,P_E0+k);pt_voice_param *p=&out[k];
        snprintf(p->label,sizeof p->label,"%s",d->label?d->label:"");
        p->fmt=d->fmt;p->min=d->min;p->max=d->max;p->value=t->p[P_E0+k];
        p->bipolar=d->fmt==F_BIPCT||d->fmt==F_SEMI||d->min<0;
        uint8_t before=fmt_named;char formatted[64];const char *unit=0;param_format(d,p->value,formatted,&unit);fmt_named=before;
        snprintf(p->text,sizeof p->text,"%s%s%s",formatted,unit&&*unit?" ":"",unit?unit:"");
    }
    return ei;
}
/* The value names of an F_ENUM edit parameter (count returned; 0 if not a list). */
static unsigned voice_names(unsigned tr,unsigned k,char names[][16],unsigned cap){
    const track_t *t=&trk[tr%NTRK];const param_desc_t *d=track_desc(t,P_E0+(k&7u));
    if(d->fmt!=F_ENUM)return 0;
    unsigned n=0;
    for(int v=d->min;v<=d->max&&n<cap;++v,++n){
        uint8_t before=fmt_named;char formatted[64];const char *unit=0;param_format(d,v,formatted,&unit);fmt_named=before;
        snprintf(names[n],16,"%s",formatted);
    }
    return n;
}
