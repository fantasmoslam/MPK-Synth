// SPDX-License-Identifier: GPL-3.0-only
import("paleo.lib");
// bowed bone: a long bone bar rubbed with a resined stick; glassy, singing, slightly unstable partials
env = g : si.smooth(ba.tau2pole(0.35));
press = env*(1+no.lfnoise(9)*0.25);
rub = no.noise*press : fi.bandpass(1,f*0.8,f*12);
body = modal(f,(1,2.756,5.404,8.933),(3,2,1.2,0.6),(1,0.35,0.15,0.06), rub*0.004);
process = body : fi.highpass(1,40) : ma.tanh : *(0.6);
