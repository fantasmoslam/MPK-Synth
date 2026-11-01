// SPDX-License-Identifier: GPL-3.0-only
import("paleo.lib");
// bone flute: an end-blown vulture-bone flute; airy tone, a chiff at the start of each note, breath vibrato
env = g : si.smooth(ba.tau2pole(0.06));
chiff = no.noise*en.ar(0.005,0.06,trig) : fi.resonbp(f*2,3,1);
vib = 1 + 0.006*os.osc(4.8+no.lfnoise(1)*0.5)*en.asr(0.4,1,0.2,t);
ff = f*vib;
tone = os.osc(ff) + 0.18*os.osc(2*ff) + 0.05*os.osc(3*ff);
breath = no.noise : fi.resonbp(ff,10,1) : fi.resonbp(ff,10,1) : *(0.06);   // breath sits on the note (two narrow passes, so little hiss leaks)
air = no.noise : fi.bandpass(2,ff*2,4500) : *(0.006);
process = ((tone*0.6 + breath + air)*env*(1+no.lfnoise(6)*0.04) + chiff*0.25*g) : fi.lowpass(2,5500) : *(0.5);
