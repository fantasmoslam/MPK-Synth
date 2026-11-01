// SPDX-License-Identifier: GPL-3.0-only
import("paleo.lib");
// bullroarer: a flat wooden slat whirled on a cord; roaring hum whose pitch and loudness swell with each turn
env = g : si.smooth(ba.tau2pole(0.8));
spin = 0.7 + 0.4*env;                                   // turns per second
turn = os.osc(spin);
ff = f*(0.8+0.4*env)*(1+0.12*turn);
roar = no.noise : fi.resonbp(ff,5,1) + (no.noise : fi.resonbp(ff*2.1,6,0.4)) + os.osc(ff)*0.1;
process = roar*env*(0.55+0.45*turn) : fi.lowpass(2,3000) : *(0.6);
