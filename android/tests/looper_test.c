/* SPDX-License-Identifier: GPL-3.0-only */
#include "../native/looper.h"
#include <assert.h>
#include <stdio.h>

static lp_looper l;
static uint32_t sent[4096];static unsigned nsent;
static void out(void *user,uint32_t m){(void)user;assert(nsent<4096);sent[nsent++]=m;}
static uint32_t msg(unsigned s,unsigned d1,unsigned d2){return s|d1<<8|d2<<16;}
static void block(void){lp_advance(&l,480,out,0);}   /* 10 ms at 48 kHz */
static unsigned count(uint32_t m){unsigned n=0;for(unsigned i=0;i<nsent;++i)n+=sent[i]==m;return n;}

int main(void){
    lp_init(&l,48000);
    /* Play does nothing on an empty looper; Stop neither. */
    lp_command(&l,LP_CMD_PLAY,out,0);assert(l.state==LP_EMPTY);
    lp_command(&l,LP_CMD_STOP,out,0);assert(l.state==LP_EMPTY&&nsent==0);

    /* Nothing is recorded until Record. */
    lp_live(&l,msg(0x90,60,100));assert(l.n==0);
    lp_live(&l,msg(0x80,60,0));assert(l.n==0);

    /* First take: a note at 0.1 s held 0.2 s, on part 2; take closes at 1 s with Play. */
    lp_command(&l,LP_CMD_RECORD,out,0);assert(l.state==LP_RECORDING);
    for(int i=0;i<10;++i)block();
    lp_live(&l,msg(0x91,64,90));
    for(int i=0;i<20;++i)block();
    lp_live(&l,msg(0x81,64,0));
    for(int i=0;i<70;++i)block();
    assert(nsent==0);   /* the first take is never played back while it records */
    lp_command(&l,LP_CMD_PLAY,out,0);
    assert(l.state==LP_PLAYING&&l.length==48000&&l.n==2&&l.pos==0);

    /* It loops: two passes play the note twice, each on and off. */
    for(int i=0;i<200;++i)block();
    assert(count(msg(0x91,64,90))==2&&count(msg(0x81,64,0))==2);
    printf("PASS: first take sets a 1.000 s loop that plays back on its part\n");

    /* Live playing does not record while the loop only plays. */
    lp_live(&l,msg(0x90,40,80));lp_live(&l,msg(0x80,40,0));assert(l.n==2);

    /* Overdub: Record adds a note; it plays from the next pass, never twice in one. */
    lp_command(&l,LP_CMD_RECORD,out,0);assert(l.state==LP_OVERDUB);
    for(int i=0;i<50;++i)block();   /* 0.5 s */
    nsent=0;
    lp_live(&l,msg(0x92,36,127));
    block();block();
    lp_live(&l,msg(0x82,36,0));
    assert(count(msg(0x92,36,127))==0);
    lp_command(&l,LP_CMD_RECORD,out,0);assert(l.state==LP_PLAYING&&l.n==4);
    while(l.pos!=0)block();
    nsent=0;for(int i=0;i<100;++i)block();
    assert(count(msg(0x92,36,127))==1&&count(msg(0x82,36,0))==1&&count(msg(0x91,64,90))==1);
    printf("PASS: overdub adds to the loop from the next pass\n");

    /* A note held when overdub ends still gets its note-off into the loop. */
    lp_command(&l,LP_CMD_RECORD,out,0);
    lp_live(&l,msg(0x90,50,70));
    lp_command(&l,LP_CMD_RECORD,out,0);assert(l.state==LP_PLAYING);
    block();
    lp_live(&l,msg(0x80,50,0));
    assert(l.n==6);
    printf("PASS: a note held past the end of overdub still ends in the loop\n");

    /* Stop in the middle of a loop note ends it, and keeps the loop. */
    while(l.pos<=4800)block();   /* 0.1 s: the part 2 note starts */
    assert(l.sounding[1][64]);
    nsent=0;
    lp_command(&l,LP_CMD_STOP,out,0);
    assert(l.state==LP_STOPPED&&count(msg(0x81,64,0))==1&&!l.sounding[1][64]&&l.n==6);
    for(int i=0;i<200;++i)block();
    assert(count(msg(0x91,64,90))==0);   /* stopped: silent */
    printf("PASS: Stop ends the loop's notes and keeps the loop\n");

    /* Play after Stop starts from the top. */
    lp_command(&l,LP_CMD_PLAY,out,0);assert(l.state==LP_PLAYING&&l.pos==0);
    nsent=0;for(int i=0;i<100;++i)block();
    assert(count(msg(0x91,64,90))==1);
    printf("PASS: Play after Stop plays the loop again from the top\n");

    /* Clear forgets it. */
    lp_command(&l,LP_CMD_CLEAR,out,0);assert(l.state==LP_EMPTY&&l.n==0&&l.length==0);
    lp_command(&l,LP_CMD_PLAY,out,0);assert(l.state==LP_EMPTY);

    /* A take shorter than 0.15 s is dropped. */
    lp_command(&l,LP_CMD_RECORD,out,0);block();lp_live(&l,msg(0x90,60,1));
    lp_command(&l,LP_CMD_RECORD,out,0);assert(l.state==LP_EMPTY&&l.n==0);
    /* Stop closes a take and stays stopped. */
    lp_command(&l,LP_CMD_RECORD,out,0);for(int i=0;i<30;++i)block();
    lp_command(&l,LP_CMD_STOP,out,0);assert(l.state==LP_STOPPED&&l.length==14400);
    /* Record while stopped overdubs from the top. */
    lp_command(&l,LP_CMD_RECORD,out,0);assert(l.state==LP_OVERDUB&&l.pos==0);
    printf("PASS: clear, short takes, Stop closing a take, Record from stopped\n");

    /* A take that runs to the maximum closes itself and plays. */
    lp_command(&l,LP_CMD_CLEAR,out,0);lp_command(&l,LP_CMD_RECORD,out,0);
    while(l.state==LP_RECORDING)block();
    assert(l.state==LP_PLAYING&&l.length==LP_MAX_SECONDS*48000u);
    /* A full loop drops events instead of overrunning. */
    lp_command(&l,LP_CMD_RECORD,out,0);
    for(unsigned i=0;i<LP_MAX_EVENTS+10;++i)lp_live(&l,msg(0xb0,1,i&127));
    assert(l.n==LP_MAX_EVENTS&&l.full==10&&l.cursor<=l.n);
    for(unsigned i=1;i<l.n;++i)assert(l.ev[i-1].at<=l.ev[i].at);
    printf("PASS: a 240 s take closes itself; a full loop drops events, stays sorted\n");
    return 0;
}
