// SPDX-License-Identifier: GPL-3.0-only
import("paleo.lib");
// clay pot drum: a hand slaps the side hole of a big fired-clay pot; deep "bwoom" that bends down in pitch, plus a slap
bend = 1 + 0.45*en.ar(0.001,0.12,trig);
vol = en.ar(0.004,0.9,trig)*g;
bwoom = os.osc(f*bend)*vol + os.osc(2.03*f*bend)*vol*0.15;
slap = no.noise*en.ar(0.0003,0.015,trig)*g : fi.bandpass(1,700,3500) : *(0.5);
shell = strike(1,3000)*g : modal(f*4.3,(1,1.7,2.9),(0.2,0.1,0.05),(1,0.5,0.2)) : *(0.15);
process = (bwoom*0.7 + slap + shell) : ma.tanh;
