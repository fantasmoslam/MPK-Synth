// SPDX-License-Identifier: GPL-3.0-only
import("paleo.lib");
// hollow log (slit) drum: a wooden tongue over a hollow trunk; short, knocky, pitched
tongue = modal(f,(1,2.76,5.4),(0.38,0.12,0.05),(1,0.35,0.12), strike(2,4000)*g);
hollow = tongue : fi.resonbp(f*0.5,4,0.6);
process = (tongue*0.9 + hollow) * 0.12;   // clean: no saturation
