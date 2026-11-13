/* SPDX-License-Identifier: GPL-3.0-only
 * MPK Synth's native side: the synth (mpk/engine.c) and the looper (looper.h) on a
 * low-latency AAudio stream, and the JNI calls the app makes (org.fantasmoslam.mpksynth.Synth).
 *
 * Threads. The synth and the looper have one owner: the audio callback while the stream
 * runs, the activity thread only while it is closed. Everything the app sends (MIDI you
 * play, models and control values, looper buttons) goes through one queue the callback
 * drains at the start of each block. The app keeps its own copy of every control value,
 * so nothing is ever read back from the audio thread; the model tables it reads are
 * constant.
 */
#include <aaudio/AAudio.h>
#include <android/log.h>
#include <jni.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include "mpk/engine.h"
#include "output_stage.h"
#include "looper.h"

#define JNI(name) Java_org_fantasmoslam_mpksynth_Synth_##name
#define TAG "MpkSynth"

static AAudioStream *stream;
static int booted, sample_rate=48000, burst, buffer_frames, sharing, performance;
static _Atomic unsigned master_value=700, panic_requested, peak_q15, output_level=1, limited_frames;
static _Atomic int stream_error;
static _Atomic unsigned callbacks, late_callbacks, max_us;
static pt_output_t output_stage;
static float output_gain(void){return (float)(1u<<atomic_load(&output_level));}
static uint64_t nanos(void){struct timespec t;clock_gettime(CLOCK_MONOTONIC,&t);return (uint64_t)t.tv_sec*1000000000ull+(uint64_t)t.tv_nsec;}

/* ---- the looper, and what the app can read of it ---------------------------------- */
static lp_looper looper;
/* state, length, position, events, dropped: written by the owner after each block. */
static _Atomic unsigned loop_shown[5];
static void to_engine(void *user,uint32_t m){(void)user;mpe_midi(m&255,(m>>8)&127,(m>>16)&127);}
static void publish_loop(void){
    atomic_store_explicit(&loop_shown[0],(unsigned)looper.state,memory_order_relaxed);
    atomic_store_explicit(&loop_shown[1],looper.length,memory_order_relaxed);
    atomic_store_explicit(&loop_shown[2],looper.pos,memory_order_relaxed);
    atomic_store_explicit(&loop_shown[3],looper.n,memory_order_relaxed);
    atomic_store_explicit(&loop_shown[4],looper.full,memory_order_relaxed);
}

/* ---- the queue: MIDI you play and the app's commands -------------------------------- */
/* Entries are status|d1<<8|d2<<16 for MIDI; tags 0xf1 a control value (part, id, value),
 * 0xf2 a part's model, 0xf4 a looper button. MIDI from JNI never carries 0xf0..0xff. */
static pthread_mutex_t queue_mutex=PTHREAD_MUTEX_INITIALIZER;
static uint32_t queue[512];
static _Atomic unsigned queue_w, queue_r;
static int push(uint32_t m){
    pthread_mutex_lock(&queue_mutex);
    unsigned w=atomic_load(&queue_w),r=atomic_load_explicit(&queue_r,memory_order_acquire);
    int ok=w-r<512;
    if(ok){queue[w&511]=m;atomic_store_explicit(&queue_w,w+1,memory_order_release);}
    pthread_mutex_unlock(&queue_mutex);
    return ok;
}
static uint32_t pack(unsigned tag,unsigned part,unsigned id,int value){
    return tag|(part&3u)<<8|(id&127u)<<10|((uint32_t)value&0x7fffu)<<17;
}
static void apply(uint32_t m){
    unsigned tag=m&255,part=(m>>8)&3,id=(m>>10)&127;
    int value=(int)(m>>17);if(value&0x4000)value-=0x8000;
    switch(tag){
    case 0xf1:mpe_param(part,id,value);return;
    case 0xf2:mpe_part_model(part,(unsigned)value);return;
    case 0xf4:lp_command(&looper,value,to_engine,NULL);return;
    default:
        if(tag<0x80||tag>=0xf0)return;
        mpe_midi(tag,(m>>8)&127,(m>>16)&127);
        lp_live(&looper,m);
    }
}
/* Drains the queue (bounded, in case a controller floods it). */
static void drain(unsigned limit){
    if(atomic_exchange(&panic_requested,0)){
        mpe_panic();lp_command(&looper,LP_CMD_STOP,to_engine,NULL);
        atomic_store(&queue_r,atomic_load(&queue_w));
    }
    unsigned r=atomic_load_explicit(&queue_r,memory_order_relaxed),w=atomic_load_explicit(&queue_w,memory_order_acquire);
    for(unsigned i=0;r!=w&&i<limit;++i,++r)apply(queue[r&511]);
    atomic_store_explicit(&queue_r,r,memory_order_release);
}

/* ---- audio -------------------------------------------------------------------------- */
static aaudio_data_callback_result_t audio_callback(AAudioStream *s,void *user,void *data,int32_t frames){
    (void)s;(void)user;
    uint64_t start=nanos();
    float *out=data;
    drain(128);
    lp_advance(&looper,(uint32_t)frames,to_engine,NULL);
    publish_loop();
    mpe_render(out,(unsigned)frames);
    float master=atomic_load(&master_value)/1023.0f;
    float peak=pt_output_process(&output_stage,out,(unsigned)frames,output_gain()*master*master);
    atomic_store(&limited_frames,output_stage.limited);
    atomic_store(&peak_q15,(unsigned)(peak*32767));
    unsigned us=(unsigned)((nanos()-start)/1000);
    if(us>atomic_load(&max_us))atomic_store(&max_us,us);
    if((uint64_t)us*(unsigned)sample_rate>(uint64_t)frames*1000000u)atomic_fetch_add(&late_callbacks,1);
    atomic_fetch_add(&callbacks,1);
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}
static void audio_error(AAudioStream *s,void *u,aaudio_result_t error){(void)s;(void)u;atomic_store(&stream_error,error);}
static void stop(void){
    if(stream){
        __android_log_print(ANDROID_LOG_INFO,TAG,"stopped: %u callbacks, %u late, max %u us, underruns %d",
            atomic_load(&callbacks),atomic_load(&late_callbacks),atomic_load(&max_us),AAudioStream_getXRunCount(stream));
        AAudioStream_requestStop(stream);
        AAudioStream_close(stream);stream=NULL;   /* close waits for callbacks: we own the engine after it */
    }
    if(booted){
        drain(~0u);
        /* The loop stops with the audio and is kept; nothing is left sounding. */
        lp_command(&looper,LP_CMD_STOP,to_engine,NULL);
        mpe_panic();publish_loop();
    }
}

JNIEXPORT void JNICALL JNI(init)(JNIEnv *e,jclass c){
    (void)e;(void)c;
    if(booted)return;
    mpe_init((float)sample_rate);booted=1;
    lp_init(&looper,(uint32_t)sample_rate);publish_loop();
}
JNIEXPORT jstring JNICALL JNI(start)(JNIEnv *env,jclass cls,jint buffers){
    (void)cls;
    if(!booted)return (*env)->NewStringUTF(env,"Engine is not initialized");
    if(stream&&!atomic_load(&stream_error))return NULL;
    stop();atomic_store(&stream_error,0);
    atomic_store(&callbacks,0);atomic_store(&late_callbacks,0);atomic_store(&max_us,0);
    AAudioStreamBuilder *builder=NULL;
    aaudio_result_t result=AAudio_createStreamBuilder(&builder);
    if(result!=AAUDIO_OK)return (*env)->NewStringUTF(env,AAudio_convertResultToText(result));
    AAudioStreamBuilder_setDirection(builder,AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setChannelCount(builder,2);
    AAudioStreamBuilder_setFormat(builder,AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setPerformanceMode(builder,AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder,AAUDIO_SHARING_MODE_EXCLUSIVE);
    AAudioStreamBuilder_setUsage(builder,AAUDIO_USAGE_GAME);
    AAudioStreamBuilder_setDataCallback(builder,audio_callback,NULL);
    AAudioStreamBuilder_setErrorCallback(builder,audio_error,NULL);
    result=AAudioStreamBuilder_openStream(builder,&stream);
    if(result!=AAUDIO_OK){
        AAudioStreamBuilder_setSharingMode(builder,AAUDIO_SHARING_MODE_SHARED);
        result=AAudioStreamBuilder_openStream(builder,&stream);
    }
    AAudioStreamBuilder_delete(builder);
    if(result!=AAUDIO_OK){stream=NULL;return (*env)->NewStringUTF(env,AAudio_convertResultToText(result));}
    int rate=AAudioStream_getSampleRate(stream);
    burst=AAudioStream_getFramesPerBurst(stream);
    sharing=AAudioStream_getSharingMode(stream);
    performance=AAudioStream_getPerformanceMode(stream);
    if(rate<8000||rate>192000||burst<1||AAudioStream_getChannelCount(stream)!=2||AAudioStream_getFormat(stream)!=AAUDIO_FORMAT_PCM_FLOAT){
        stop();return (*env)->NewStringUTF(env,"Unsupported audio configuration");
    }
    if(rate!=sample_rate){
        /* A loop is counted in frames: at a new device rate it would play at the wrong speed. */
        if(looper.state!=LP_EMPTY)lp_command(&looper,LP_CMD_CLEAR,to_engine,NULL);
        sample_rate=rate;
        mpe_set_rate((float)rate);
    }
    looper.rate=(uint32_t)sample_rate;publish_loop();
    if(buffers<2)buffers=2;
    if(buffers>8)buffers=8;
    AAudioStream_setBufferSizeInFrames(stream,burst*buffers);
    buffer_frames=AAudioStream_getBufferSizeInFrames(stream);
    pt_output_init(&output_stage,(unsigned)sample_rate,output_gain());
    result=AAudioStream_requestStart(stream);
    if(result!=AAUDIO_OK){stop();return (*env)->NewStringUTF(env,AAudio_convertResultToText(result));}
    __android_log_print(ANDROID_LOG_INFO,TAG,"started: %d Hz, burst %d, buffer %d, %s, %s",sample_rate,burst,buffer_frames,
        sharing==AAUDIO_SHARING_MODE_EXCLUSIVE?"exclusive":"shared",performance==AAUDIO_PERFORMANCE_MODE_LOW_LATENCY?"low latency":"normal");
    return NULL;
}
JNIEXPORT void JNICALL JNI(stop)(JNIEnv *e,jclass c){(void)e;(void)c;stop();}
JNIEXPORT jint JNICALL JNI(error)(JNIEnv *e,jclass c){(void)e;(void)c;return atomic_load(&stream_error);}
JNIEXPORT jboolean JNICALL JNI(running)(JNIEnv *e,jclass c){(void)e;(void)c;return stream!=NULL;}
JNIEXPORT void JNICALL JNI(level)(JNIEnv *e,jclass c,jint value){(void)e;(void)c;if(value>=0&&value<=2)atomic_store(&output_level,(unsigned)value);}
JNIEXPORT void JNICALL JNI(master)(JNIEnv *e,jclass c,jint value){(void)e;(void)c;atomic_store(&master_value,value<0?0:value>1023?1023:(unsigned)value);}
JNIEXPORT jint JNICALL JNI(peak)(JNIEnv *e,jclass c){(void)e;(void)c;return (jint)atomic_load(&peak_q15);}
JNIEXPORT void JNICALL JNI(panic)(JNIEnv *e,jclass c){
    (void)e;(void)c;
    if(!stream&&booted){mpe_panic();lp_command(&looper,LP_CMD_STOP,to_engine,NULL);publish_loop();return;}
    atomic_store(&panic_requested,1);
}
/* One channel message you played, already routed to its part. */
JNIEXPORT void JNICALL JNI(midi)(JNIEnv *e,jclass c,jint status,jint d1,jint d2){
    (void)e;(void)c;
    if(status<0x80||status>=0xf0)return;
    if(!push((unsigned)status|((unsigned)(d1&127)<<8)|((unsigned)(d2&127)<<16)))atomic_store(&panic_requested,1);
}
JNIEXPORT jstring JNICALL JNI(report)(JNIEnv *env,jclass cls){
    (void)cls;
    if(!stream)return NULL;
    char text[256];
    snprintf(text,sizeof text,"%d Hz | burst %d | buffer %d | %s / %s | underruns %d | late %u | max %u us | limits %u",
        sample_rate,burst,buffer_frames,sharing==AAUDIO_SHARING_MODE_EXCLUSIVE?"exclusive":"shared",
        performance==AAUDIO_PERFORMANCE_MODE_LOW_LATENCY?"low latency":"normal",AAudioStream_getXRunCount(stream),
        atomic_load(&late_callbacks),atomic_load(&max_us),atomic_load(&limited_frames));
    return (*env)->NewStringUTF(env,text);
}

/* ---- models and parts ------------------------------------------------------------------ */
static jobjectArray strings(JNIEnv *e,unsigned n){return (*e)->NewObjectArray(e,(jsize)n,(*e)->FindClass(e,"java/lang/String"),NULL);}
static void put(JNIEnv *e,jobjectArray a,unsigned i,const char *s){jstring t=(*e)->NewStringUTF(e,s);(*e)->SetObjectArrayElement(e,a,(jsize)i,t);(*e)->DeleteLocalRef(e,t);}
static jboolean command(uint32_t m){
    if(!stream){apply(m);publish_loop();return JNI_TRUE;}
    return push(m)?JNI_TRUE:JNI_FALSE;
}
/* "name\tinspired by" per model. */
JNIEXPORT jobjectArray JNICALL JNI(models)(JNIEnv *e,jclass c){
    (void)c;unsigned n=mpe_models();char line[160];
    jobjectArray a=strings(e,n);if(!a)return NULL;
    for(unsigned i=0;i<n;++i){snprintf(line,sizeof line,"%s\t%s",mpe_model(i)->name,mpe_model(i)->inspired);put(e,a,i,line);}
    return a;
}
/* "label\tsection\tmin\tmax\tdefault\tformat\tdrawn as\tnames" per control. */
JNIEXPORT jobjectArray JNICALL JNI(params)(JNIEnv *e,jclass c,jint model){
    (void)c;const mp_model *m=mpe_model((unsigned)model);if(!m)return NULL;
    char line[256];jobjectArray a=strings(e,m->nparams);if(!a)return NULL;
    for(unsigned i=0;i<m->nparams;++i){
        const mp_param *p=&m->params[i];
        snprintf(line,sizeof line,"%s\t%s\t%d\t%d\t%d\t%d\t%d\t%s",p->label,p->section,p->min,p->max,p->def,p->fmt,p->ui,p->names?p->names:"");
        put(e,a,i,line);
    }
    return a;
}
/* "name\tid,id,..." per knob bank (8 ids; -1 none; 100+k the last pad's control k). */
JNIEXPORT jobjectArray JNICALL JNI(banks)(JNIEnv *e,jclass c,jint model){
    (void)c;const mp_model *m=mpe_model((unsigned)model);if(!m)return NULL;
    char line[128];jobjectArray a=strings(e,m->nbanks);if(!a)return NULL;
    for(unsigned b=0;b<m->nbanks;++b){
        int n=snprintf(line,sizeof line,"%s\t",m->bank_names[b]);
        for(int k=0;k<8;++k)n+=snprintf(line+n,sizeof line-(size_t)n,k?",%d":"%d",m->banks[b][k]);
        put(e,a,b,line);
    }
    return a;
}
JNIEXPORT jobjectArray JNICALL JNI(presets)(JNIEnv *e,jclass c,jint model){
    (void)c;const mp_model *m=mpe_model((unsigned)model);if(!m)return NULL;
    jobjectArray a=strings(e,m->npresets);if(!a)return NULL;
    for(unsigned i=0;i<m->npresets;++i)put(e,a,i,m->presets[i].name);
    return a;
}
/* Every control's value in a preset. */
JNIEXPORT jintArray JNICALL JNI(presetValues)(JNIEnv *e,jclass c,jint model,jint preset){
    (void)c;const mp_model *m=mpe_model((unsigned)model);if(!m||preset<0)return NULL;
    int16_t v[MP_MAX_PARAMS];jint out[MP_MAX_PARAMS];
    mpe_preset_values(m,(unsigned)preset,v);
    for(unsigned i=0;i<m->nparams;++i)out[i]=v[i];
    jintArray a=(*e)->NewIntArray(e,(jsize)m->nparams);if(a)(*e)->SetIntArrayRegion(e,a,0,(jsize)m->nparams,out);
    return a;
}
JNIEXPORT jstring JNICALL JNI(padName)(JNIEnv *e,jclass c,jint pad){(void)c;return (*e)->NewStringUTF(e,mp_kit_pad_name(pad));}
/* A part plays a model from its defaults; then the app sends the values it wants. */
JNIEXPORT jboolean JNICALL JNI(partModel)(JNIEnv *e,jclass c,jint part,jint model){
    (void)e;(void)c;if(part<0||part>3||model<0||(unsigned)model>=mpe_models())return JNI_FALSE;
    return command(pack(0xf2,(unsigned)part,0,model));
}
JNIEXPORT jboolean JNICALL JNI(param)(JNIEnv *e,jclass c,jint part,jint id,jint value){
    (void)e;(void)c;if(part<0||part>3||id<0||id>=MP_MAX_PARAMS||value<-16384||value>16383)return JNI_FALSE;
    return command(pack(0xf1,(unsigned)part,(unsigned)id,value));
}

/* ---- the looper ------------------------------------------------------------------------ */
JNIEXPORT jboolean JNICALL JNI(loop)(JNIEnv *e,jclass c,jint button){
    (void)e;(void)c;if(button<LP_CMD_RECORD||button>LP_CMD_CLEAR)return JNI_FALSE;
    return command(pack(0xf4,0,0,button));
}
/* [state, length, position, events, dropped, sample rate] */
JNIEXPORT jintArray JNICALL JNI(loopState)(JNIEnv *e,jclass c){
    (void)c;jint v[6];
    for(unsigned i=0;i<5;++i)v[i]=(jint)atomic_load_explicit(&loop_shown[i],memory_order_relaxed);
    v[5]=sample_rate;
    jintArray a=(*e)->NewIntArray(e,6);if(a)(*e)->SetIntArrayRegion(e,a,0,6,v);
    return a;
}
