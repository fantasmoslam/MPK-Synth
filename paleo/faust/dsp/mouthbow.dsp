// SPDX-License-Identifier: GPL-3.0-only
import("paleo.lib");
// mouth bow: one slack string slapped with a stick, the player's mouth shaping its overtones (vowel p1)
str = pm.ks(pm.f2l(f*(1+0.012*en.ar(0.0005,0.08,trig))), 0.35, strike(1,3000)*g*2) + no.noise*en.ar(0.0003,0.006,trig)*g*0.2;
process = str <: _*0.35, throat(p1,1.0,_)*1.2 :> + : ef.cubicnl(0.4,0);
