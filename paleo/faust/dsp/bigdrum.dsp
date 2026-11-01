// SPDX-License-Identifier: GPL-3.0-only
import("paleo.lib");
// big skin drum: a stretched hide, struck with the palm or a padded beater; the pitch sags as it rings
sag = 1 + 0.35*en.ar(0.0005,0.06,trig);
body = modal(f*sag,(1,1.59,2.14,2.30,2.65,2.92),(0.9,0.5,0.35,0.3,0.22,0.18),(1,0.55,0.35,0.3,0.2,0.15), strike(6,900)*g*30);
slap = no.noise*en.ar(0.0003,0.02,trig) : fi.bandpass(1,250,1500) : *(g*0.6);
process = (body*0.05 + slap) : ma.tanh;
