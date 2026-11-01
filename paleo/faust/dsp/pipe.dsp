// SPDX-License-Identifier: GPL-3.0-only
import("paleo.lib");
// drone pipe: a long wooden tube, hummed and buzzed; the mouth slowly moves the throat formants (p1) inside it
env = g : si.smooth(ba.tau2pole(0.3));
buzz = os.sawtooth(f*(1+no.lfnoise(3)*0.003)) * (1 + no.lfnoise(25)*0.2);
tube = buzz : fi.resonbp(f*3,6,0.5) + buzz*0.3;
process = (throat(p1,0.7,tube)*0.9 + tube*0.1 : fi.lowpass(2,2200)) * env;
