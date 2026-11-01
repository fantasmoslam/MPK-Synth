// SPDX-License-Identifier: GPL-3.0-only
import("paleo.lib");
// throat choir: deep chanting voices, the same five-formant throat as SAURUS, sung on "oh"/"ah"
p2 = nentry("p2",5,3,8,0.001);                       // each singer's own vibrato rate
env = g : si.smooth(ba.tau2pole(0.15));
vib = 1 + 0.006*os.osc(p2)*en.asr(0.5,1,0.4,t) + 0.002*os.osc(p2*0.37);
fl = f*vib;
glot = os.sawtooth(fl) : fi.lowpass(2, 900+1600*env);      // softened glottal buzz
breath = no.noise*0.04 : fi.bandpass(1,400,3000);
process = (throat(p1,1.0,glot*(1+breath*2)) + glot*0.08) * env : fi.highpass(1,60);
