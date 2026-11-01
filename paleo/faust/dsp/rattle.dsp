// SPDX-License-Identifier: GPL-3.0-only
import("paleo.lib");
// seed rattle in a gourd: many tiny grains, dark, with a shake envelope
env = en.ar(0.01,0.09,t);
grains = no.noise * (no.noise : abs : >(0.985)) ;
process = grains : fi.resonbp(2200,1.5,1) : fi.lowpass(2,5500) : *(env*g*3);
