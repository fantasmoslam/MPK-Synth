/* SPDX-License-Identifier: GPL-3.0-only */
/* paleo-expedition: runs the live Scape conductor against the PALEO engine exactly as the app will, but faster
   than real time, and writes what it played. A script can press actions and move notes at given times, as a
   player would.
   paleo-expedition OUT.wav [--seed N] [--minutes M] [--script FILE] [--log OUT.json] [--creatures DIR] [--threads N]
   Script lines:  TIME action WAIT|MOVE_ON|FOLLOW_HERD|SHELTER|INTERACT|DEFEND|OBSERVE|TRACK
                  TIME note Danger|Majesty|Density|Pace|Space|Wildness|Drift|Silence VALUE
                  TIME scene NAME URGENCY        TIME stop          (TIME in seconds, or m:ss) */
#ifndef NOMINMAX
#define NOMINMAX
#endif
#define MINIAUDIO_IMPLEMENTATION
#define MA_NO_DEVICE_IO
#define MA_NO_ENCODING
#define MA_NO_GENERATION
#include "../third_party/miniaudio.h"
#include "../engine/paleo.h"
#include "../engine/conductor.h"
#include "creatures.h"
#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>
#if defined(__x86_64__) || defined(_M_X64)
#include <xmmintrin.h>
#endif

struct Cue { double t; std::string kind, name; int value = 0; };
static double parse_t(const std::string& s) {
  size_t c = s.find(':'); if (c == std::string::npos) return atof(s.c_str());
  return atof(s.substr(0, c).c_str()) * 60 + atof(s.substr(c + 1).c_str());
}
static int find_name(const std::string& n, const char* (*name)(int), int count) {
  std::string a = n; for (auto& ch : a) if (ch == '_') ch = ' ';
  for (int i = 0; i < count; i++) if (strcasecmp(a.c_str(), name(i)) == 0) return i;
  fprintf(stderr, "unknown name %s\n", n.c_str()); exit(2);
}
static std::string esc(const std::string& s) { std::string o; for (char c : s) { if (c == '"' || c == '\\') o += '\\'; o += c; } return o; }

int main(int argc, char** argv) {
#if defined(__x86_64__) || defined(_M_X64)
  _mm_setcsr(_mm_getcsr() | 0x8040);
#endif
  if (argc < 2) { fprintf(stderr, "usage: paleo-expedition OUT.wav [options]\n"); return 2; }
  uint32_t seed = 1; double minutes = 8; std::string script, logp, cdir; paleo::Options o; o.lead = paleo::Lead::all; o.ostinato = paleo::Ostinato::mouthbow;
  for (int i = 2; i + 1 < argc; i += 2) {
    std::string k = argv[i], v = argv[i + 1];
    if (k == "--seed") seed = uint32_t(strtoul(v.c_str(), nullptr, 10)); else if (k == "--minutes") minutes = atof(v.c_str());
    else if (k == "--script") script = v; else if (k == "--log") logp = v; else if (k == "--creatures") cdir = v;
    else if (k == "--threads") o.threads = atoi(v.c_str());
  }
  std::vector<Cue> cues;
  if (!script.empty()) {
    std::ifstream f(script); std::string line;
    while (std::getline(f, line)) {
      if (line.empty() || line[0] == '#') continue; std::istringstream s(line); std::string t; Cue c; s >> t >> c.kind >> c.name >> c.value; c.t = parse_t(t); cues.push_back(c);
    }
    std::stable_sort(cues.begin(), cues.end(), [](const Cue& a, const Cue& b) { return a.t < b.t; });
  }
  paleo::Engine e(o); int nc = paleo_load_creatures(e, cdir, "paleo/scores/x"); fprintf(stderr, "creature library: %d calls\n", nc);
  paleo::Conductor c(e, seed); c.start(0);
  int sr = o.sample_rate; const int BL = 192; size_t maxN = size_t(minutes * 60 * sr);
  std::vector<float> L, R; L.reserve(maxN + sr * 30); R.reserve(maxN + sr * 30);
  std::vector<double> loads; size_t ci = 0; double end_at = -1;
  std::string gauge_json; int next_g = 0; float buf_l[BL], buf_r[BL];
  auto t0 = std::chrono::steady_clock::now();
  for (;;) {
    double now = e.seconds();
    while (ci < cues.size() && cues[ci].t <= now) {
      Cue& q = cues[ci++];
      if (q.kind == "action") c.action(find_name(q.name, paleo::action_name, paleo::kNumActions));
      else if (q.kind == "note") c.note(find_name(q.name, paleo::note_name, paleo::kNumNotes), q.value);
      else if (q.kind == "scene") c.request_scene(find_name(q.name, paleo::scene_name, paleo::kNumScenes), q.value);
      else if (q.kind == "stop") c.stop();
    }
    if (now >= minutes * 60 && c.running() && cues.empty()) c.stop();
    if (now >= minutes * 60 + 90 && c.running()) c.stop();
    c.tick(now);
    if (!c.running() && end_at < 0) end_at = now + 12;
    if (end_at > 0 && now >= end_at) break;
    if (now >= next_g) {
      float g[paleo::kNumGauges]; c.gauges(g); char b[256];
      snprintf(b, sizeof b, "%s[%d,%d,%.3f,%.3f,%.3f]", gauge_json.empty() ? "" : ",", next_g, c.scene(), std::isnan(g[0]) ? 0 : g[0], c.energy(), std::isnan(g[1]) ? 0 : g[1]);
      gauge_json += b; next_g++;
    }
    auto a = std::chrono::steady_clock::now(); e.render(buf_l, buf_r, BL);
    loads.push_back(std::chrono::duration<double>(std::chrono::steady_clock::now() - a).count() / (double(BL) / sr));
    if (loads.back() > 3 && getenv("PALEO_DEBUG")) fprintf(stderr, "slow block at %.2f s: %.0f%%, %d voices\n", now, 100 * loads.back(), e.active_voices());
    L.insert(L.end(), buf_l, buf_l + BL); R.insert(R.end(), buf_r, buf_r + BL);
  }
  double run = std::chrono::duration<double>(std::chrono::steady_clock::now() - t0).count();
  FILE* f = fopen(argv[1], "wb"); unsigned n = unsigned(L.size()), data = n * 8;
  auto w32 = [&](unsigned x) { fwrite(&x, 4, 1, f); }; auto w16 = [&](unsigned short x) { fwrite(&x, 2, 1, f); };
  fwrite("RIFF", 1, 4, f); w32(36 + data); fwrite("WAVEfmt ", 1, 8, f); w32(16); w16(3); w16(2); w32(sr); w32(sr * 8); w16(8); w16(32);
  fwrite("data", 1, 4, f); w32(data); for (unsigned i = 0; i < n; i++) { fwrite(&L[i], 4, 1, f); fwrite(&R[i], 4, 1, f); } fclose(f);
  std::sort(loads.begin(), loads.end());
  printf("seed %u: %.1f s in %.1f s on %d threads; 4 ms block load median %.0f%%, 99th %.0f%%, worst %.0f%%\n", seed, double(n) / sr, run, e.threads(),
         100 * loads[loads.size() / 2], 100 * loads[loads.size() * 99 / 100], 100 * loads.back());
  if (!logp.empty()) {
    FILE* j = fopen(logp.c_str(), "w"); fprintf(j, "{\"seed\":%u,\"length\":%.2f,\"marks\":[", seed, double(n) / sr);
    bool first = true; for (auto& m : c.marks()) { fprintf(j, "%s[%.2f,\"%s\"]", first ? "" : ",", m.t, esc(m.text).c_str()); first = false; }
    fprintf(j, "],\"gauges\":[%s]}\n", gauge_json.c_str()); fclose(j);
  }
  return 0;
}
