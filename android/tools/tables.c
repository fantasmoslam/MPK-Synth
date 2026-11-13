/* SPDX-License-Identifier: GPL-3.0-only
 * Prints the synth's model tables as the app's JNI layer serves them (android/native/synth.c),
 * for the desktop preview and the Java tests:
 *   M name\tinspired         a model; the lines after it are its own
 *   P label\tsection\t...    a control
 *   B name\tid,id,...         a knob bank
 *   R name\tv,v,...           a preset and every control's value
 *   D name                    a Kit pad's name, 16 of them, after the models
 */
#include <stdio.h>
#include "../native/mpk/engine.h"
#include "../native/mpk/model.h"

int main(void){
    mpe_init(48000);
    for(unsigned i=0;i<mpe_models();++i){
        const mp_model *m=mpe_model(i);
        printf("M %s\t%s\n",m->name,m->inspired);
        for(unsigned j=0;j<m->nparams;++j){
            const mp_param *p=&m->params[j];
            printf("P %s\t%s\t%d\t%d\t%d\t%d\t%d\t%s\n",p->label,p->section,p->min,p->max,p->def,p->fmt,p->ui,p->names?p->names:"");
        }
        for(unsigned b=0;b<m->nbanks;++b){
            printf("B %s\t",m->bank_names[b]);
            for(int k=0;k<8;++k)printf(k?",%d":"%d",m->banks[b][k]);
            printf("\n");
        }
        for(unsigned r=0;r<m->npresets;++r){
            int16_t v[MP_MAX_PARAMS];mpe_preset_values(m,r,v);
            printf("R %s\t",m->presets[r].name);
            for(unsigned j=0;j<m->nparams;++j)printf(j?",%d":"%d",v[j]);
            printf("\n");
        }
    }
    for(int d=0;d<16;++d)printf("D %s\n",mp_kit_pad_name(d));
    return 0;
}
