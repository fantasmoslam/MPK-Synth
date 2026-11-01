/* SPDX-License-Identifier: GPL-3.0-only */
/* paleo-check: runs the live conductor without rendering audio and checks what it wrote, before anyone listens.
   paleo-check SEEDS MINUTES
   For each seed it asks for every kind of scene in turn, keeps every note (dropping what a rewrite cancelled), and
   reports:
     clash    share of the time two pitched parts hold notes a semitone apart (a minor second or major seventh)
              together for 0.3 s or more, with the pairs of parts that clash most
     tritone  share of the time two parts sound a tritone apart
     flams    drum hits of different drums 8 to 45 ms apart, per minute (they sound like one hit smeared into two
              or three)
     busy     share of the time five or more pitched parts sound at once */
#include "../engine/paleo.h"
#include "../engine/conductor.h"
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <map>
#include <string>
#include <vector>

struct Note { double t, end; std::string part; int midi; float vel; int handle; };
struct Log { std::vector<Note> notes; };
static void on_event(void* ctx, int kind, const paleo::Event& e, int handle, double t) {
  Log& L = *static_cast<Log*>(ctx);
  if (kind == 0) L.notes.push_back({e.t, e.t + std::max(.05, e.d), e.part, e.midi, e.vel, 0});
  else if (kind == 1) L.notes.push_back({e.t, 1e9, e.part, e.midi, e.vel, handle});
  else if (kind == 2) { for (auto& n : L.notes) if (n.handle == handle && n.end > t) n.end = t; }
  else if (kind == 3) {
    L.notes.erase(std::remove_if(L.notes.begin(), L.notes.end(), [&](const Note& n) { return n.t >= t - 1e-9; }), L.notes.end());
    for (auto& n : L.notes) if (n.handle == 0 && n.end > t && n.t < t) n.end = std::max(n.end, n.end);   /* ringing notes ring on */
  }
}
static bool unpitched(const std::string& p) {
  return p == "taiko" || p == "bigdrum" || p == "rattle" || p == "stone" || p == "subdrop" || p == "framedrum" || p == "claypot" || p == "rasp";
}
static int scene_index(const char* n) { for (int i = 0; i < paleo::kNumScenes; i++) if (std::string(paleo::scene_name(i)) == n) return i; return 1; }
static bool drum(const std::string& p) { return p == "taiko" || p == "bigdrum" || p == "rattle" || p == "logdrum" || p == "slitdrum" || p == "framedrum" || p == "claypot"; }

int main(int argc, char** argv) {
  int seeds = argc > 1 ? atoi(argv[1]) : 3; double minutes = argc > 2 ? atof(argv[2]) : 6;
  const char* scenes[] = {"travel", "majesty", "herd", "stalk", "stampede", "tribe", "fight", "rest", "observe"};
  double tot_t = 0, tot_clash = 0, tot_tri = 0, tot_busy = 0; long tot_flams = 0; std::map<std::string, double> pairs;
  for (int s = 1; s <= seeds; s++) {
    Log L; paleo::Options o; o.lead = paleo::Lead::all; o.threads = 1; o.on_event = on_event; o.on_event_ctx = &L;
    paleo::Engine e(o); paleo::Conductor c(e, uint32_t(s * 7919 + 13)); c.start(0);
    int next = 1;
    for (double t = 0; t < minutes * 60; t += .02) {
      if (t >= next * 40.0) { c.request_scene(scene_index(scenes[(next + s) % 9]), 1); next++; }
      c.tick(t);
    }
    c.stop(); double T = minutes * 60;
    for (double t = T; c.running() && t < T + 120; t += .02) c.tick(t);
    for (auto& n : L.notes) if (n.end > 1e8) n.end = T + 120;
    /* clash and busy on a 20 ms grid */
    double end = 0; for (auto& n : L.notes) end = std::max(end, std::min(n.end, T + 120));
    int G = int(end / .02) + 1; std::vector<std::vector<int>> on((size_t)G);
    for (size_t i = 0; i < L.notes.size(); i++) {
      auto& n = L.notes[i]; if (unpitched(n.part) || drum(n.part) || n.vel < .12f) continue;
      for (int g = int(n.t / .02); g < std::min(G, int(n.end / .02)); g++) on[size_t(g)].push_back(int(i));
    }
    /* a rub counts when two notes a semitone (or a tritone) apart overlap for 0.3 s or more: a passing note
       brushing past a chord is music, two held notes grinding is a clash */
    std::vector<char> cl((size_t)G, 0), trg((size_t)G, 0);
    std::vector<int> idx; for (size_t i = 0; i < L.notes.size(); i++) { auto& n = L.notes[i]; if (!unpitched(n.part) && !drum(n.part) && n.vel >= .12f) idx.push_back(int(i)); }
    std::sort(idx.begin(), idx.end(), [&](int a, int b) { return L.notes[size_t(a)].t < L.notes[size_t(b)].t; });
    for (size_t a = 0; a < idx.size(); a++) {
      auto& x = L.notes[size_t(idx[a])];
      for (size_t b = a + 1; b < idx.size(); b++) {
        auto& y = L.notes[size_t(idx[b])]; if (y.t >= x.end) break;
        double s0 = std::max(x.t, y.t), s1 = std::min(x.end, y.end); if (s1 - s0 < .3) continue;
        int iv = ((x.midi - y.midi) % 12 + 12) % 12;
        if (iv == 1 || iv == 11) { std::string k = std::min(x.part, y.part) + " / " + std::max(x.part, y.part); pairs[k] += s1 - s0;
          for (int g = int(s0 / .02); g < std::min(G, int(s1 / .02)); g++) cl[(size_t)g] = 1; }
        if (iv == 6) for (int g = int(s0 / .02); g < std::min(G, int(s1 / .02)); g++) trg[(size_t)g] = 1;
      }
    }
    double clash = 0, tri = 0, busy = 0, sounding = 0;
    for (int g = 0; g < G; g++) {
      auto& v = on[(size_t)g]; if (v.empty()) continue; sounding++;
      std::map<std::string, int> parts; for (int i : v) parts[L.notes[size_t(i)].part]++;
      clash += cl[(size_t)g]; tri += trg[(size_t)g]; busy += parts.size() >= 5;
    }
    /* drum flams */
    std::vector<std::pair<double, std::string>> hits;
    for (auto& n : L.notes) if (drum(n.part) && n.vel > .1f) hits.push_back({n.t, n.part + std::to_string(n.midi)});
    std::sort(hits.begin(), hits.end()); long flams = 0;
    for (size_t i = 1; i < hits.size(); i++) { double d = hits[i].first - hits[i - 1].first; if (d > .008 && d < .045) flams++; }
    printf("seed %d: %.0f s, clash %.1f%%, tritone %.1f%%, busy %.1f%%, drum flams %.1f/min\n", s, end,
           100 * clash / std::max(1.0, sounding), 100 * tri / std::max(1.0, sounding), 100 * busy / std::max(1.0, sounding), flams / (end / 60));
    if (getenv("PARTS")) { std::map<std::string, int> cnt; for (auto& n : L.notes) if (n.vel > .1f) cnt[n.part]++;
      printf("   notes per minute:"); for (auto& kv : cnt) printf(" %s %.0f", kv.first.c_str(), kv.second / (end / 60)); printf("\n");
      std::map<std::string, std::pair<int, int>> rng; for (auto& n : L.notes) if (n.vel > .1f) { auto it = rng.find(n.part); if (it == rng.end()) rng[n.part] = {n.midi, n.midi}; else { it->second.first = std::min(it->second.first, n.midi); it->second.second = std::max(it->second.second, n.midi); } }
      printf("   note range:"); for (auto& kv : rng) if (!drum(kv.first) && !unpitched(kv.first)) printf(" %s %d-%d", kv.first.c_str(), kv.second.first, kv.second.second); printf("\n"); }
    tot_t += sounding; tot_clash += clash; tot_tri += tri; tot_busy += busy; tot_flams += flams;
  }
  printf("all: clash %.1f%%, tritone %.1f%%, busy %.1f%%\n", 100 * tot_clash / tot_t, 100 * tot_tri / tot_t, 100 * tot_busy / tot_t);
  std::vector<std::pair<double, std::string>> top; for (auto& kv : pairs) top.push_back({kv.second, kv.first});
  std::sort(top.rbegin(), top.rend());
  printf("clashing pairs (seconds):"); for (size_t i = 0; i < std::min<size_t>(8, top.size()); i++) printf("  %s %.0f", top[i].second.c_str(), top[i].first);
  printf("\n");
  return 0;
}
