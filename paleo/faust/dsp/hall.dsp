// SPDX-License-Identifier: GPL-3.0-only
// The hall send for the speed test: a stereo algorithmic reverb in place of the song's convolution.
import("stdfaust.lib");
process = re.zita_rev1_stereo(20, 200, 6000, 3.4, 3.0, 48000);
