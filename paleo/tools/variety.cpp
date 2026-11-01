/* SPDX-License-Identifier: GPL-3.0-only */
/* paleo-variety: runs the conductor without rendering any audio and measures how much it repeats itself.
   paleo-variety SEEDS MINUTES [OUTDIR]  writes OUTDIR/trace-SEED.txt (one line per drum bar or phrase). */
#include "../engine/paleo.h"
#include "../engine/conductor.h"
#include <cstdio>
#include <cstdlib>
#include <string>
#include <vector>
int main(int argc, char** argv) {
  int seeds = argc > 1 ? atoi(argv[1]) : 5; double minutes = argc > 2 ? atof(argv[2]) : 10; std::string out = argc > 3 ? argv[3] : ".";
  paleo::Options o; o.lead = paleo::Lead::all; o.threads = 1; paleo::Engine e(o);
  for (int s = 1; s <= seeds; s++) {
    std::vector<std::string> tr; paleo::Conductor c(e, uint32_t(s * 7919)); c.set_trace(&tr); c.start(0);
    bool press = argc > 4; int next = 1;   /* with a 4th argument: press every action in turn, one every 25 s, and move the notes */
    for (double t = 0; t < minutes * 60; t += .02) {
      if (press && t >= next * 25.0) { if (next % 3 == 0) c.note(next % 8, (next * 37) % 101); else c.action(next % 8); next++; }
      c.tick(t);
    }
    c.stop(); for (double t = minutes * 60; c.running() && t < minutes * 60 + 120; t += .02) c.tick(t);
    if (press && s == 1) for (auto& m : c.marks()) printf("%6.1f %s\n", m.t, m.text.c_str());
    FILE* f = fopen((out + "/trace-" + std::to_string(s) + ".txt").c_str(), "w"); for (auto& l : tr) fprintf(f, "%s\n", l.c_str()); fclose(f);
    e.cancel_from(0);
  }
  return 0;
}
