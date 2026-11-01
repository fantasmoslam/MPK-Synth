// SPDX-License-Identifier: GPL-3.0-only
// PALEO's reverb buses (hall, room, long tail): a stereo zita reverb shaped like the song's impulse responses,
// which were decaying noise with a 150 Hz low cut, a dark top and 20 ms before the first reflection.
import("stdfaust.lib");
rt60 = nentry("rt60", 3.4, 0.3, 20, 0.01);
dark = nentry("dark", 4500, 500, 16000, 1);
shape = fi.highpass(2, 150) : fi.lowpass(2, dark) : fi.lowpass(2, dark);
process = re.zita_rev1_stereo(20, 200, dark, rt60, rt60, 48000) : shape, shape;
