// SPDX-License-Identifier: GPL-3.0-only
import("paleo.lib");
// bone rasp: a notched bone scraped with a stick over a hollow gourd; f = how fast the notches go by (teeth per second).
// Each notch knocks the gourd, so the scrape is a run of small hollow "bonks"; the stick lands with a bigger one.
env = en.asr(0.008,1,0.03,t)*g;
rate = f*(1+0.12*no.lfnoise(4));
teeth = os.lf_imptrain(rate)*env;
tick = teeth : fi.resonbp(1500,4,1) : *(0.25);                       // the bone notch itself, kept small
land = strike(1.2,2500)*g;                                            // the stick landing on the bone
gourd(x) = x : modal(230,(1,1.62,2.41,3.3),(0.12,0.08,0.05,0.03),(1,0.5,0.3,0.12)); // hollow gourd body
process = (tick + gourd(teeth*0.6 + land*4)*0.0025) : fi.lowpass(2,4500) : ma.tanh;
