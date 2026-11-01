// SPDX-License-Identifier: GPL-3.0-only
import("paleo.lib");
// slit drum: a hollowed log with a tongue cut either side of a slit; woody, hollow, warm, clear pitch
mallet = strike(3,2500)*g;
tongue = modal(f,(1,2.02,3.9,5.6),(0.7,0.25,0.12,0.06),(1,0.35,0.15,0.05), mallet);
cavity = mallet : fi.resonbp(f*0.5,4,1) : *(0.3);
knock = strike(0.5,7000)*g : fi.resonbp(f*7.3,3,1) : *(0.08);
process = (tongue + cavity)*0.005 + knock : fi.lowpass(1,7000) : ma.tanh;
