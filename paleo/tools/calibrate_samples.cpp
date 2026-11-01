/* SPDX-License-Identifier: GPL-3.0-only */
/* paleo-calibrate-samples: sets the recorded instruments' mix levels (k in kParts) against the parts already
   measured. It plays a short phrase on each part alone and prints, for each recorded instrument, the k that puts
   its active level where its reference part sits: a lead against the fiddle, a low or pad voice against the horn,
   a plucked or struck one against the mouth bow. Run from the repo root after paleo/samples/build.py. */
#define MINIAUDIO_IMPLEMENTATION
#define MA_NO_DEVICE_IO
#define MA_NO_ENCODING
#define MA_NO_GENERATION
#include "../third_party/miniaudio.h"
#include "../engine/paleo.h"
#include "creatures.h"
#include <cmath>
#include <cstdio>
#include <string>
#include <vector>

static double active_rms(const std::string& part, int midi, double dur) {
  paleo::Options o; o.lead = paleo::Lead::all; o.threads = 1; paleo::Engine e(o);
  std::vector<paleo::Event> sc; const int steps[4] = {0, 2, 4, 7};
  for (int i = 0; i < 4; i++) { paleo::Event ev; ev.t = .2 + i * dur; ev.d = dur * .95; ev.part = part; ev.midi = midi + steps[i]; ev.vel = .8f; sc.push_back(ev); }
  e.load(sc);
  int n = int((4 * dur + .5) * 48000); std::vector<float> L((size_t)n), R((size_t)n); e.render(L.data(), R.data(), n);
  /* active: the 50 ms windows within 30 dB of the loudest */
  std::vector<double> w; for (int s = 0; s + 2400 <= n; s += 2400) { double a = 0; for (int k = s; k < s + 2400; k++) a += .5 * (L[size_t(k)] * L[size_t(k)] + R[size_t(k)] * R[size_t(k)]); w.push_back(a / 2400); }
  double pk = 0; for (double x : w) pk = std::max(pk, x); double sum = 0; int c = 0;
  for (double x : w) if (x > pk * 1e-3) { sum += x; c++; }
  return c ? std::sqrt(sum / c) : 0;
}

int main() {
  int z = paleo_load_samples("paleo/creatures/lib"); fprintf(stderr, "%d recorded notes\n", z);
  struct R { const char* part; const char* ref; int midi; double dur; double db; };
  /* db: where it sits against its reference (a lead a little under the fiddle, plucks with the mouth bow) */
  const R list[] = {
    {"ocarina", "fiddle", 76, 1.2, -3}, {"recorder", "fiddle", 74, 1.2, -3}, {"trecorder", "fiddle", 67, 1.2, -3},
    {"psaltery", "fiddle", 69, 1.2, -2}, {"glass", "fiddle", 79, 1.5, -4}, {"folkharp", "mouthbow", 60, .8, 0}, {"dantranh", "mouthbow", 62, .8, 0},
    {"kalimba", "mouthbow", 65, .8, -1}, {"mbira", "mouthbow", 60, .8, -1}, {"balafon", "mouthbow", 62, .8, -1}, {"marimba", "mouthbow", 60, .8, -1},
  };
  for (auto& r : list) {
    double ref = active_rms(r.ref, r.midi, r.dur), got = active_rms(r.part, r.midi, r.dur);
    double k = got > 0 ? ref / got * std::pow(10, r.db / 20) : 0;
    printf("%-11s ref %-8s %7.4f  got %7.4f  k x %.4f\n", r.part, r.ref, ref, got, k);
  }
  return 0;
}
