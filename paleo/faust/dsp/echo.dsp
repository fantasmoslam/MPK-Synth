// SPDX-License-Identifier: GPL-3.0-only
// One ping-pong echo, as on the horns and the ostinato.
import("stdfaust.lib");
d = 0.375*ma.SR;
process = (+ : de.delay(48000,d) : fi.lowpass(1,3500)) ~ *(0.35), (+ : de.delay(48000,d) : fi.lowpass(1,3500)) ~ *(0.35);
