// SPDX-License-Identifier: GPL-3.0-only
// taiko: a big barrel drum struck with wooden sticks. Clean: no saturation.
// Low notes ring long like an odaiko; higher ones are short and tight like a shime.
import("paleo.lib");
size = 85/f : max(0.25) : min(2.6);                         // bigger drum, longer ring
sag = 1 + 0.08*en.ar(0.0003,0.05,trig);                   // the hide tightens for a moment as it is hit
stick = (en.ar(0.0001,0.0012,trig) + no.noise*en.ar(0.0001,0.004,trig)*0.4) : fi.highpass(1,40) : fi.lowpass(1,6000);
skin = modal(f*sag,(1,1.59,2.14,2.30,2.65,2.92,3.16,3.50),
             (1.1*size,0.7*size,0.45*size,0.4*size,0.3*size,0.25*size,0.2*size,0.15*size),
             (1,0.6,0.45,0.35,0.3,0.22,0.18,0.12), stick*g*25);
barrel = skin : fi.resonbp(f*1.4, 2, 0.5);                 // the hollow wooden body
crack = no.noise*en.ar(0.0001,0.012,trig)*g : fi.bandpass(1,1200,4500) : *(0.15);
process = (skin*0.0016 + barrel*0.002 + crack*60.0);
