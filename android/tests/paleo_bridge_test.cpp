// SPDX-License-Identifier: GPL-3.0-only
// The tablet's bridge to PALEO and the live conductor, driven as the audio callback drives it, about
// eight times faster than real time: a note set before anything begins, Play music, an action,
// Stop music into the coda, then silence.
#include "../native/paleo_bridge.h"
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <string>
#include <thread>
#include <vector>

static int fail(const char* why) { printf("FAIL: %s\n", why); return 1; }

int main() {
  paleo_library_dir("paleo/creatures/lib");
  paleo_note(0, 80);                                   // Danger, before an expedition exists
  if (paleo_note_value(0) != 80) return fail("note before start not kept");
  paleo_prepare(44100);
  const unsigned sr = 44100, F = 192; std::vector<float> buf(2 * F);
  double t = 0, sum = 0; long n = 0; bool acted = false, stopped = false, played = false; std::string seen;
  paleo_music(1);
  for (;;) {
    for (auto& x : buf) x = 0;
    paleo_mix(buf.data(), F, 1.f); t += double(F) / sr;
    for (float x : buf) { if (!std::isfinite(x)) return fail("non-finite sample"); sum += double(x) * x; n++; }
    if (paleo_state() == 2) played = true;
    if (played && !acted && t > 20) { paleo_action(5); acted = true; }          // DEFEND
    if (acted && !stopped && t > 30) { paleo_music(0); stopped = true; }
    char m[1024]; paleo_marks(m, sizeof m); seen += m;
    if (stopped && paleo_state() == 0) break;
    if (t > 240) return fail("did not reach the end of the coda");
    std::this_thread::sleep_for(std::chrono::microseconds(500));
  }
  double rms = std::sqrt(sum / double(n));
  if (!played) return fail("never played");
  if (rms < 1e-3) return fail("silent");
  if (seen.find("action: DEFEND") == std::string::npos && seen.find("action: defend") == std::string::npos) return fail("action not taken");
  if (seen.find("the end") == std::string::npos) return fail("no coda");
  printf("PASS: PALEO bridge played %.0f s (rms %.3f), took an action, and ended through the coda\n", t, rms);
  return 0;
}
