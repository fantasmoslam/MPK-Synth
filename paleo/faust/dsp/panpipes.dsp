// SPDX-License-Identifier: GPL-3.0-only
import("paleo.lib");
// reed panpipes: stopped cane tubes, hollow odd harmonics and a little breath, short puffed attacks
env = g : si.smooth(ba.tau2pole(0.025));
puff = no.noise*en.ar(0.002,0.04,trig) : fi.bandpass(1,f,f*6);
tone = os.osc(f) + 0.25*os.osc(3*f) + 0.08*os.osc(5*f);
breath = (no.noise : fi.resonbp(f,8,1) : fi.resonbp(f,8,1)) + (no.noise : fi.resonbp(3*f,10,1) : fi.resonbp(3*f,10,0.3));
process = ((tone*0.45 + breath*0.1 + (no.noise : fi.lowpass(2,4000))*0.005)*env + puff*0.25*g) : fi.lowpass(2,5000) : *(0.5);
