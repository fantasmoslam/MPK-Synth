// SPDX-License-Identifier: GPL-3.0-only
import("paleo.lib");
// gut fiddle: a bowed gut string on a hide-and-gourd body (bowed waveguide, long enough for low notes)
pmx = library("physmodels.lib")[maxLength = 9;];
env = g : si.smooth(ba.tau2pole(0.12));
vib = 1 + 0.004*os.osc(5.2)*en.asr(0.6,1,0.3,t);
len = pmx.f2l(f*vib);
str = pmx.violinModel(len, 0.55+0.2*env, env*0.9, 0.72);
body = str <: _*0.5, (_ <: fi.resonbp(f*1.0+0,2,0.6), fi.resonbp(280,3,0.5), fi.resonbp(520,4,0.3)  :> _) :> _;
process = body : fi.highpass(1,50) : fi.lowpass(2,5000);
