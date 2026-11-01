// SPDX-License-Identifier: GPL-3.0-only
import("paleo.lib");
// frame drum: a wide hide stretched on a bent-wood hoop, played by hand; p1 = where it is hit (0 centre boom, 1 rim tek)
pos = p1 : min(1);
hand = strike(2.5 - 2*pos, 2000 + 6000*pos)*g;
skin = modal(f,(1,1.59,2.14,2.30,2.65,2.92,3.16),
             (0.5,0.35,0.25,0.2,0.15,0.12,0.1),
             (1-0.7*pos,0.6,0.45*(0.4+pos),0.4,0.3*(0.3+pos),0.25,0.2*(0.2+pos)), hand*20);
hoop = hand : fi.resonbp(f*5.2,6,1) : *(0.5*pos);
tek = no.noise*en.ar(0.0002,0.025,trig)*g*pos : fi.bandpass(1,1500,6000) : *(3.5);
process = (skin*0.0008 + hoop*2.5 + tek) : ma.tanh;
