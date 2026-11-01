// SPDX-License-Identifier: GPL-3.0-only
import("paleo.lib");
// animal horn: buzzing lips into a curved horn; scoops up into the note, voiced by the same throat as SAURUS
env = g : si.smooth(ba.tau2pole(0.06));
scoop = 1 - 0.06*en.ar(0.0,0.18,trig);
fl = f*scoop*(1 + no.lfnoise(9)*0.004);
lips = os.sawtooth(fl) * (1 + no.lfnoise(40)*0.15) + os.sawtooth(fl*0.5)*0.25;
bore = lips : fi.lowpass(2, 300 + 1700*env) ;
process = (throat(p1,0.85,bore)*0.8 + bore*0.2) * env : ef.cubicnl(0.35,0);
