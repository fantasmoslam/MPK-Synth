/* SPDX-License-Identifier: GPL-3.0-only */
/* The Scape conductor, live. A port of expedition v2's compose.py (approved 2026-10-31) from a fixed script to a
   composer that writes two bars ahead and rewrites whenever something changes. See conductor.h. */
#include "conductor.h"
#include "phrase_bank.h"
#include "groove_feel.h"
#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <map>
#include <mutex>
#include <random>

namespace paleo {
namespace {
const char* kSceneNames[kNumScenes] = {"dawn", "travel", "herd", "majesty", "stalk", "stampede", "aftermath", "tribe",
                                       "rest", "observe", "shelter", "fight", "coda"};
const char* kActionNames[kNumActions] = {"WAIT", "MOVE ON", "FOLLOW HERD", "SHELTER", "INTERACT", "DEFEND", "OBSERVE", "TRACK"};
const char* kNoteNames[kNumNotes] = {"Danger", "Majesty", "Density", "Pace", "Space", "Wildness", "Drift", "Silence"};

enum Style { kNone, kWalk, kHeartbeat, kStalkDrums, kGallop, kTribeDrums };
enum LeadKind { kLeadTravel, kLeadMajesty, kLeadDanger, kLeadCoda };
const int kScale[7] = {0, 2, 3, 5, 7, 8, 10};          /* natural minor */
const int LO = 31, MI = 36, HI = 55;                   /* taiko pitches: low, mid, high */

struct SceneDef {
  int min_bars, max_bars; float e0, e1, th0, th1; bool peak; Style drums;
  int drone;      /* drone pipe: semitones from the key (0 root, -5 the fifth below), or -99 none */
  float drone_v;
};
const SceneDef kDefs[kNumScenes] = {
  /* dawn      */ {16, 16, .15f, .45f, 0, 0, false, kWalk, 0, .8f},
  /* travel    */ {8, 14, .5f, .55f, 0, .1f, false, kWalk, 0, .75f},
  /* herd      */ {10, 14, .5f, .7f, 0, .05f, false, kWalk, 0, .75f},
  /* majesty   */ {12, 16, .35f, .7f, 0, 0, true, kHeartbeat, -5, .85f},
  /* stalk     */ {10, 16, .3f, .7f, .2f, 1.f, false, kStalkDrums, 0, .75f},
  /* stampede  */ {16, 24, .85f, 1.f, 1, 1, true, kGallop, 0, .85f},
  /* aftermath */ {8, 10, .4f, .2f, .3f, 0, false, kHeartbeat, 0, .7f},
  /* tribe     */ {14, 20, .55f, .9f, 0, .3f, true, kTribeDrums, 0, .6f},
  /* rest      */ {8, 12, .25f, .2f, 0, 0, false, kHeartbeat, 0, .65f},
  /* observe   */ {8, 12, .3f, .35f, 0, 0, false, kHeartbeat, -5, .7f},
  /* shelter   */ {8, 12, .2f, .15f, .3f, 0, false, kNone, -5, .6f},
  /* fight     */ {8, 12, .9f, .8f, 1, .7f, true, kGallop, 0, .85f},
  /* coda      */ {14, 14, .5f, .1f, 0, 0, false, kWalk, 0, .75f},
};
bool is_peak(int s) { return kDefs[s].peak; }
bool is_drum(const std::string& p) { return p == "taiko" || p == "bigdrum" || p == "logdrum" || p == "rattle"; }

/* Paleotone's own phrases, (scale degree, beats), from compose.py. The Markov chains learn from these and from the
   phrase bank of real music (phrase_bank.h, made by paleo/phrases/bank.py). */
typedef std::vector<std::pair<int, float>> Phrase;
const std::vector<Phrase> kPhrases[4] = {
  {{{0,1},{2,.5f},{4,1.5f},{3,1},{2,1},{1,1},{0,2}}, {{4,1},{5,1},{4,.5f},{2,.5f},{3,2},{1,1},{2,2}},
   {{0,.5f},{1,.5f},{2,1},{4,1},{7,2},{6,1},{4,2}}, {{2,1},{4,1},{3,1},{2,1},{0,3}}, {{4,1.5f},{3,.5f},{2,1},{0,1},{-1,1},{0,3}}},
  {{{0,2},{4,2},{7,4},{6,2},{4,4}}, {{4,3},{5,1},{7,2},{9,2},{7,4}}, {{7,2},{6,2},{4,2},{2,2},{4,6}},
   {{0,3},{2,1},{4,4},{3,2},{1,2},{0,6}}},
  {{{0,.5f},{0,.5f},{1,1},{0,1},{-1,.5f},{0,1.5f}}, {{0,1},{3,1},{1,1},{0,1}}, {{4,.5f},{3,.5f},{1,1},{0,2}},
   {{0,.5f},{1,.5f},{0,.5f},{-2,.5f},{0,2}}},
  {{{4,2},{3,2},{2,2},{0,4}}, {{2,2},{1,1},{0,1},{-1,2},{0,6}}, {{0,2},{2,2},{4,4},{2,2},{0,6}}},
};
typedef std::pair<int, float> Step;
struct Chain { std::map<std::pair<Step, Step>, std::vector<Step>> T; std::vector<std::pair<Step, Step>> starts; };
const Step kBegin{-100, 0}, kEnd{100, 0};

struct LeadVoice { const char* part; int oct; float vm; };
struct LeadPlan { int from; LeadKind kind; std::vector<LeadVoice> voices; int octave; float vel; float rest0, rest1; };
}  // namespace

const char* scene_name(int s) { return s >= 0 && s < kNumScenes ? kSceneNames[s] : "?"; }
const char* action_name(int a) { return a >= 0 && a < kNumActions ? kActionNames[a] : "?"; }
const char* note_name(int n) { return n >= 0 && n < kNumNotes ? kNoteNames[n] : "?"; }

struct Conductor::Impl {
  Engine& eng; std::mt19937 rng; uint32_t seed;
  std::recursive_mutex m;
  const Chain* chains = nullptr; std::map<int, Phrase> memory;
  int notes[kNumNotes]; bool notes_dirty = false; double last_rewrite = -1;

  /* time: every written bar has a start time and a tempo */
  std::vector<double> bar_t; std::vector<float> bar_bpm;
  bool started = false, stopping = false, finished = false; double now = 0;
  int wbar = 0;                    /* next bar to write */
  double cut = -1;                 /* while rewriting a bar: nothing before this time */

  struct Inst { int type = kTravel, b0 = 0, b1 = 16, key = 38; float e0, e1, th0, th1; int lead_alt = 0; int animal = 0;
                bool built = false; };
  Inst cur, nxt; bool have_cur = false; int quiet = 0, travels = 0;
  struct BarInfo { int scene; float e, th; int chord, key; int parts; int lead_notes; double t; };
  std::vector<BarInfo> info;

  /* chords: degree of the chord root per bar (2-bar steps), -6 = the flat second (the danger colour) */
  std::map<int, int> chords; int walk = 0;
  /* lead */
  double lead_t = 0; std::vector<double> onsets; int lead_count = 0;
  /* drone */
  int drone_h = 0, drone_midi = -1; double drone_on = 0;
  /* tribe pattern engine */
  struct Player { const char* part; int midi; std::vector<int> cyc; int enter, rot, step; };
  std::vector<Player> tribe;
  /* interact: call and answer for a few bars */
  int interact_bars = 0;
  /* settle ramp on the drums before a quieter scene */
  double ramp_t0 = 1e30, ramp_t1 = 0; float ramp_v0 = 1, ramp_v1 = 1;
  int parts_mask = 0, bar_lead = 0;
  std::vector<Mark> marks;

  Impl(Engine& e, uint32_t s) : eng(e), rng(s), seed(s) {
    for (auto& n : notes) n = 50;
    chains = shared_chains();
  }
  /* The chains learn once: Paleotone's own phrases and the phrase bank from real music. Shared by every expedition. */
  static const Chain* shared_chains() {
    static const std::vector<Chain> C = [] {
      std::vector<Chain> c(4);
      auto learn = [&](int k, const Phrase& ph) {
        std::vector<Step> q; q.reserve(ph.size() + 2); q.push_back(kBegin); for (auto& st : ph) q.push_back(st); q.push_back(kEnd);
        for (size_t i = 0; i + 2 < q.size(); i++) c[size_t(k)].T[{q[i], q[i + 1]}].push_back(q[i + 2]);
      };
      for (int k = 0; k < 4; k++) for (auto& ph : kPhrases[k]) learn(k, ph);
      Phrase ph;
      for (size_t i = 0; i + 1 < sizeof kPhraseBank && kPhraseBank[i] != 255;) {
        int k = kPhraseBank[i], n = kPhraseBank[i + 1]; i += 2; ph.clear();
        for (int j = 0; j < n; j++, i += 2) ph.push_back({int(kPhraseBank[i]) - 16, kBankBeats[kPhraseBank[i + 1]]});
        if (k >= 0 && k < 4) learn(k, ph);
      }
      for (auto& ch : c) for (auto& kv : ch.T) if (kv.first.first == kBegin) ch.starts.push_back(kv.first);
      return c;
    }();
    return C.data();
  }
  /* ---- random helpers */
  double U() { return std::uniform_real_distribution<double>(0, 1)(rng); }
  double gauss() { return std::normal_distribution<double>(0, 1)(rng); }
  double U(double a, double b) { return a + (b - a) * U(); }
  int I(int n) { return int(U() * n) % std::max(1, n); }
  template <class T> const T& pick(const std::vector<T>& v) { return v[size_t(I(int(v.size())))]; }
  float N(int k) const { return notes[k] / 100.f; }          /* 0..1, .5 neutral */
  float Nc(int k) const { return (notes[k] - 50) / 50.f; }   /* -1..1 */

  void mark(double t, const std::string& s) { marks.push_back({t, s}); }

  /* ---- time */
  float bpm_now() const { return 100.f * powf(1.25f, Nc(kNPace)); }     /* Pace: 80 .. 125 BPM */
  double B(int b) const { return 60.0 / bar_bpm[size_t(b)]; }
  double BAR(int b) const { return 4 * B(b); }
  double T(int b) {                                                    /* start of bar b (extends the grid) */
    while (int(bar_t.size()) <= b) { size_t k = bar_t.size(); bar_t.push_back(bar_t[k - 1] + 4 * 60.0 / bar_bpm[k - 1]); bar_bpm.push_back(bpm_now()); }
    return bar_t[size_t(b)];
  }
  int bar_at(double t) { T(wbar + 8); return std::max(0, int(std::upper_bound(bar_t.begin(), bar_t.end(), t) - bar_t.begin()) - 1); }

  /* ---- writing notes */
  float drum_scale(double t) const {
    if (t < ramp_t0 || t >= ramp_t1) return 1;
    return ramp_v0 + (ramp_v1 - ramp_v0) * float((t - ramp_t0) / (ramp_t1 - ramp_t0));
  }
  bool n(double t, double d, const char* part, int midi, float vel, float p1 = -1) {
    if (t < cut - 1e-6) return false;
    std::string ps(part); if (is_drum(ps)) vel *= drum_scale(t);
    vel = std::max(0.f, std::min(vel, 1.f)); if (vel < .01f) return false;
    Event e; e.t = t; e.d = d; e.part = ps; e.midi = midi; e.vel = vel; e.p1 = p1; eng.push(e);
    static const char* groups[] = {"taiko", "logdrum", "rattle", "mouthbow", "horn", "fiddle", "stone", "braam"};
    for (int g = 0; g < 8; g++) if (ps == groups[g] || (g == 0 && ps == "bigdrum")) parts_mask |= 1 << g;
    return true;
  }
  /* A creature calls into a gap in the lead, on the 8th grid (Proteus-style agents). */
  void call(const char* kind, double t, float gain, float pan, float dist, int b) {
    double h = B(b) / 2; t = std::round(t / h) * h;
    for (int tries = 0; tries < 3; tries++) {
      bool clash = false; for (double o : onsets) if (std::fabs(o - t) < .22) { clash = true; break; }
      if (!clash) break; t += h;
    }
    if (t < cut - 1e-6) return;
    float w = .7f + .6f * N(kNWildness), sp = Nc(kNSpace) * .25f;
    dist = std::max(0.f, std::min(1.f, dist + sp));
    Event e; e.t = t; e.d = 0; e.part = kind; e.midi = 60; e.vel = std::min(2.f, gain * w); e.p1 = dist; e.p2 = std::max(-1.f, std::min(1.f, pan));
    eng.push(e);
  }

  /* ---- harmony */
  int key_at(int b) const {
    if (cur.type == kStampede && b >= cur.b0 + 12 && cur.b1 - cur.b0 >= 18) return cur.key + 2;   /* the stampede's lift */
    return cur.key;
  }
  int deg(int d, int b, int octave = 0) const { int o = (d >= 0 ? d / 7 : -((-d + 6) / 7)), i = d - 7 * o; return key_at(b) + 12 * (o + octave) + kScale[i]; }
  bool is_build_into(const Inst& a, const Inst& to) const {
    if (to.type == kCoda || to.type == kRest || to.type == kObserveScene || to.type == kShelterScene || to.type == kAftermath) return false;
    return kDefs[to.type].e0 > a.e1 - .05f || to.type == kStampede || to.type == kMajesty || to.type == kTribe || to.type == kFight;
  }
  int chord(int b) {
    if (b >= cur.b1 - 2 && b < cur.b1 && cur.b1 - cur.b0 >= 2) return is_build_into(cur, nxt) ? 6 : 3;   /* cadence: bVII or iv */
    int pair = b - ((b - cur.b0) & 1);
    auto it = chords.find(pair); if (it != chords.end()) return it->second;
    int c;
    float home = .6f * (1 - .6f * N(kNDrift)) + .2f;   /* Drift: how often the walk returns home */
    if (pair == cur.b0) c = 0;
    else if ((cur.type == kStalk || cur.type == kFight) && U() < .4) c = -6;
    else if (walk != 0 && U() < home) c = 0;
    else {
      static const int mv[5][2] = {{5, 3}, {3, 3}, {6, 2}, {2, 2}, {4, 1}};
      double r = U() * 11; c = 4; for (auto& x : mv) { r -= x[1]; if (r <= 0) { c = x[0]; break; } }
    }
    walk = c; chords[pair] = c; return c;
  }
  int root(int b, int octave = -1) { int c = chord(b); return c == -6 ? key_at(b) + 1 + 12 * octave : deg(c, b, octave); }
  bool chord_tone(int b, int m) {
    int c = chord(b), k = key_at(b);
    for (int j = 0; j < 3; j++) { int pc = c == -6 ? (k + 1 + (j == 0 ? 0 : j == 1 ? 3 : 7)) % 12 : (k + kScale[(c + 2 * j) % 7]) % 12; if (((m % 12) + 12) % 12 == pc) return true; }
    return false;
  }

  /* ---- melody: walk a chain; with deja vu (35%) an earlier phrase comes back changed */
  Phrase phrase(LeadKind kind, float max_beats) {
    Phrase p;
    auto it = memory.find(kind);
    if (it != memory.end() && U() < .35) { for (auto& s : it->second) { static const int d[5] = {0, 0, 0, 1, -1}; p.push_back({s.first + d[I(5)], s.second}); } }
    else {
      const Chain& C = chains[kind]; auto ab = pick(C.starts); Step a = ab.first, b = ab.second; p.push_back(b);
      while (p.size() < 12) {
        auto f = C.T.find({a, b}); if (f == C.T.end()) break;
        Step nx = pick(f->second); if (nx == kEnd) break; p.push_back(nx); a = b; b = nx;
      }
    }
    memory[kind] = p;
    Phrase out; float t = 0; for (auto& s : p) { if (t + s.second > max_beats) break; out.push_back(s); t += s.second; }
    return out;
  }
  LeadPlan lead_plan(int b) const {
    float th = threat(b);
    switch (cur.type) {
      case kDawn: return {8, kLeadTravel, {{"fiddle", 0, 1}}, 0, .75f, 2, 6};
      case kTravel: case kHerd:
        if (cur.lead_alt & 1) return {0, kLeadTravel, {{"horn", 0, 1}, {"fiddle", 12, .5f}}, 0, .85f, 2, 6};
        return {0, kLeadTravel, {{"fiddle", 0, 1}, {"horn", -12, .45f}}, 0, .85f, 2, 6};
      case kMajesty: return {4, kLeadMajesty, {{"fiddle", 0, 1}, {"horn", -12, .5f}}, 0, .9f, 3, 6};
      case kStalk: if (th > .7f) return {0, kLeadDanger, {{"horn", 0, .9f}}, -1, .7f + .3f * th, 2, 5}; break;
      case kStampede: return {4, kLeadDanger, {{"horn", 0, 1}, {"horn", -12, .75f}, {"fiddle", 12, .5f}}, 0, 1, 0, 1};
      case kAftermath: return {1, kLeadCoda, {{"fiddle", 0, .9f}, {"horn", -12, .4f}}, 0, .75f, 3, 5};
      case kTribe: return {8, kLeadDanger, {{"horn", 0, .9f}, {"fiddle", -12, .5f}}, 0, .85f, 1, 3};
      case kRest: return {2, kLeadCoda, {{"fiddle", 0, .7f}}, 0, .6f, 4, 8};
      case kObserveScene: return {2, kLeadMajesty, {{"fiddle", 0, .8f}}, 0, .7f, 4, 7};
      case kFight: return {0, kLeadDanger, {{"horn", 0, 1}, {"fiddle", 12, .6f}}, 0, .95f, 0, 1};
      case kCoda: return {0, kLeadCoda, {{"fiddle", 0, .9f}, {"horn", -12, .55f}}, 0, .7f, 2, 4};
      default: break;
    }
    return {1 << 20, kLeadTravel, {}, 0, 0, 0, 0};
  }
  /* Phrases with rests between them; the last note of each phrase lands on a chord tone. */
  void lead_bar(int b, bool build_next) {
    LeadPlan L = lead_plan(b); if (L.voices.empty() || b - cur.b0 < L.from) return;
    double t1 = T(b + 1), stop_t = T(b) + (cur.b1 - b) * BAR(b) - (build_next ? 2 : 1) * B(b);
    if (cur.type == kCoda) stop_t = T(b) + (cur.b1 - 4 - b) * BAR(b);
    if (cur.type == kStalk) stop_t = std::min(stop_t, t1);                     /* stalk: short stabs, bar by bar */
    if (lead_t < T(b)) lead_t = T(b) + (U() < .5 ? 0 : B(b));
    float space = .6f + .8f * N(kNSpace) + 1.2f * std::max(0.f, Nc(kNSilence));
    while (lead_t < t1 && lead_t < stop_t - B(b)) {
      double avail = (stop_t - lead_t) / B(b); Phrase p = phrase(L.kind, float(avail));
      if (p.empty()) break;
      double t = lead_t; int last = int(p.size()) - 1;
      for (int i = 0; i <= last; i++) {
        int bb = bar_at(t), m = deg(p[size_t(i)].first, bb, L.octave);
        if (i == last && !chord_tone(bb, m)) { int best = m; for (int k = 1; k <= 3; k++) { if (chord_tone(bb, m - k)) { best = m - k; break; } if (chord_tone(bb, m + k)) { best = m + k; break; } } m = best; }
        double d = p[size_t(i)].second * B(b) * .97;
        bool any = false; for (auto& v : L.voices) any |= n(t, d, v.part, m + v.oct, L.vel * v.vm);
        if (any) { onsets.push_back(t); lead_count++; bar_lead++; }
        t += p[size_t(i)].second * B(b);
      }
      double rest = U(L.rest0, L.rest1) * space;
      lead_t = B(b) * std::round((t + rest * B(b)) / B(b));
      if (lead_t <= t) lead_t = t + B(b) * .5;
    }
    if (onsets.size() > 64) onsets.erase(onsets.begin(), onsets.begin() + 32);
  }

  /* ---- energy and threat, with the player's notes */
  float energy_at(int b) const {
    float k = float(b - cur.b0) / std::max(1, cur.b1 - cur.b0 - 1);
    float e = cur.e0 + (cur.e1 - cur.e0) * k + .12f * Nc(kNDensity) - .15f * std::max(0.f, Nc(kNSilence));
    return std::max(0.05f, std::min(1.f, e));
  }
  float threat(int b) const {
    float k = float(b - cur.b0) / std::max(1, cur.b1 - cur.b0 - 1);
    float th = cur.th0 + (cur.th1 - cur.th0) * k;
    if (cur.type == kStalk || cur.type == kFight || cur.type == kStampede) th += .25f * Nc(kNDanger);
    else th = th * (1 + Nc(kNDanger)) + .1f * std::max(0.f, Nc(kNDanger));
    return std::max(0.f, std::min(1.f, th));
  }

  /* ---- the drummer: plays from energy and threat */
  void drummer(int b, Style st, float e, float th, float scale = 1, double from = -1) {
    double t0 = T(b), Bt = B(b);
    float dens = .6f + .8f * N(kNDensity);
    auto D = [&](double t, double d, const char* p, int m, float v) {
      /* a human drummer's feel (Groove MIDI Dataset): each 16th of the bar lands a little early or late, and
         louder or softer, as real drummers play it */
      int k = int(std::lround((t - t0) / (Bt / 4))) & 15;
      int cls = (!strcmp(p, "rattle")) ? 2 : (!strcmp(p, "bigdrum") || (!strcmp(p, "taiko") && m <= LO)) ? 0 : 1;
      double off = kFeelOff[cls][k] + .6 * kFeelOffSd[cls][k] * gauss();
      t += std::max(-.3, std::min(.3, off)) * Bt / 4;
      v *= std::max(.6f, std::min(1.3f, 1 + .35f * (kFeelVel[cls][k] - 1) + .15f * kFeelVelSd[cls][k] * float(gauss())));
      if (t >= from) n(std::max(t, cut), d, p, m, v * scale);       /* never pushed back before a rewrite */
    };
    switch (st) {
      case kWalk:
        for (int beat = 0; beat < 4; beat++) {
          if (beat == 0 || beat == 2 || U() < e * .5 * dens) D(t0 + beat * Bt, .5, "taiko", beat % 2 == 0 ? LO : MI, (.55f + .4f * e) * (beat == 0 ? 1 : .8f));
          if (U() < e * .6 * dens) { static const int o[3] = {0, 7, 12}; D(t0 + (beat + .5) * Bt, .3, "logdrum", root(b, 1) + o[I(3)], .3f + .3f * e); }
        }
        if (e > .4f && dens > .7f) for (int k = 0; k < 8; k++) D(t0 + k * Bt / 2, .08, "rattle", 0, (.2f + .3f * e) * (k % 2 == 0 ? 1 : .6f));
        break;
      case kHeartbeat:
        D(t0, .5, "taiko", LO, .35f + .5f * e); D(t0 + .75 * Bt, .5, "taiko", LO, (.35f + .5f * e) * .7f);
        if (e > .5f) D(t0 + 2 * Bt, .5, "bigdrum", 36, .3f + .5f * e);
        break;
      case kStalkDrums:
        for (int k = 0; k < 16; k++) if (U() < (.05 + .35 * th) * dens) { static const int o[3] = {0, 1, 7}; D(t0 + k * Bt / 4, .2, "logdrum", root(b, 1) + o[I(3)], .25f + .4f * th); }
        if (U() < .3 + .5 * th) D(t0 + (U() < .5 ? 0 : 2) * Bt, .6, "taiko", LO, .3f + .5f * th);
        break;
      case kGallop:
        for (int beat = 0; beat < 4; beat++) {
          D(t0 + beat * Bt, .5, "taiko", LO, beat % 2 == 0 ? 1 : .85f);
          D(t0 + (beat + .5) * Bt, .4, "taiko", HI, .55f + .2f * e);
          D(t0 + (beat + .75) * Bt, .4, "taiko", MI, .6f + .2f * e);
          if (e > .9f && U() < .6) D(t0 + (beat + .25) * Bt, .3, "taiko", MI, .45f);
        }
        D(t0, .8, "bigdrum", 33, .7f + .3f * e);
        for (int k = 0; k < 16; k++) D(t0 + k * Bt / 4, .08, "rattle", 0, (.4f + .2f * e) * (k % 2 == 0 ? 1 : .6f));
        for (int k = 0; k < 16; k++) { static const int o[4] = {0, 7, 12, 7}; D(t0 + k * Bt / 4, .1, "logdrum", root(b, 1) + o[k % 4], .4f * (k % 4 == 0 ? 1 : .55f)); }
        break;
      case kTribeDrums:
        for (auto& P : tribe) {
          if (b - cur.b0 < P.enter) continue;
          if ((b - cur.b0) % 4 == 0 && U() < .5) P.rot = (P.rot + 1) % int(P.cyc.size());
          for (int k = 0; k < 16; k++) {
            if (P.cyc[size_t((P.step + P.rot) % int(P.cyc.size()))] && U() < (.55 + .45 * e) * std::min(1.f, dens)) {
              static const int o[3] = {0, 7, 12}; int mm = P.midi >= 0 ? P.midi : root(b, 1) + o[I(3)];
              D(t0 + k * Bt / 4, .2, P.part, mm, (.45f + .45f * e) * (k % 4 == 0 ? 1 : .75f));
            }
            P.step++;
          }
        }
        break;
      case kNone: break;
    }
  }

  void pipe_to(int midi, float v, double t) {        /* the drone: the conductor moves it between scenes */
    if (midi == drone_midi) return;
    if (drone_h) eng.release(drone_h, std::max(t + 3, now + 4.5));
    drone_h = 0; drone_midi = midi;
    if (midi < 0) return;
    Event e; e.t = std::max(t - 2, std::max(now, cut)); e.part = "pipe"; e.midi = midi; e.vel = v; drone_h = eng.hold(e); drone_on = e.t;
  }
  void pads(int b, float v) { int r = root(b, -1); for (int iv : {12, 19, 24}) n(T(b), 2 * BAR(b) * .97, "horn", r + iv, v * (.7f + .6f * N(kNMajesty))); }
  void braam(int b, float v) { int r = root(b, -2) + 12; for (int iv : {12, 19, 24}) n(T(b), 3 * BAR(b), "braam", r + iv, v); }
  void ostinato(int b, float v, const char* inst = "mouthbow") {
    static const int iv[8] = {0, 0, 7, 0, 12, 0, 7, 10}; int r = root(b, 0);
    for (int k = 0; k < 8; k++) n(T(b) + k * B(b) / 2, .22, inst, r + iv[k], v * (k % 4 == 0 ? 1 : .7f));
  }
  void stones(int b, float v) {
    static const int dg[4] = {0, 2, 4, 7}; static const int gap[4] = {2, 3, 4, 6};
    double t = T(b) + I(2) * B(b);
    while (t < T(b + 1)) { n(t, 1.2, "stone", deg(dg[I(4)], b, 2), v * float(U(.6, 1))); t += gap[I(4)] * B(b); }
  }
  void herd_cloud(int b, float x0, float x1, float peak) {     /* a Poisson stream snapped to 8ths, passing across */
    double t = T(b), t1 = T(b + 1);
    while (true) {
      double x = x0 + (x1 - x0) * (t - T(b)) / (t1 - T(b));
      double dens = std::max(.3, peak * std::exp(-std::pow((x - .55) / .32, 2))) * (.6 + .8 * N(kNWildness));
      t += -std::log(1 - U() * .999) / dens; if (t >= t1) break;
      float pos = float(std::sin(2 * M_PI * x * 1.5) * .8 + U(-.2, .2));
      call("herd", t, float(.45 + .25 * std::exp(-std::pow((x - .55) / .3, 2))), pos, 0, b);
    }
  }
  const char* animal_call(int a) const { static const char* k[] = {"herd", "sauropod", "chatter", "ptero", "shout", "rex"}; return k[a % 6]; }

  /* ---- the scene grammar: what comes next. Pacing: a breather after every peak, never two peaks running,
     tension builds while nothing happens. Danger and Majesty weight the draw. */
  int pick_next(int after) {
    float D = .3f + 2 * N(kNDanger), M = .3f + 2 * N(kNMajesty);
    std::vector<std::pair<int, float>> w;
    if (after == kStampede || after == kFight) w = {{kAftermath, 3}, {kRest, .6f}};
    else if (is_peak(after)) w = {{kAftermath, 1.5f}, {kTravel, 2}, {kRest, .6f}};
    else if (after == kStalk) w = {{kStampede, .9f * D}, {kFight, .4f * D}, {kAftermath, 1}, {kTravel, .5f}};
    else {
      float tension = 1 + .8f * quiet;
      w = {{kTravel, after == kTravel ? .5f : 2.5f}, {kHerd, 1.4f}, {kMajesty, 1.4f * M}, {kStalk, 1.0f * D * tension},
           {kStampede, (after == kHerd ? .9f : .3f) * D * tension}, {kTribe, .5f * tension}, {kRest, .6f}};
      if (after == kDawn) w = {{kTravel, 3}, {kHerd, 1}, {kMajesty, .6f * M}};
    }
    float tot = 0; for (auto& x : w) tot += x.second;
    double r = U() * tot; for (auto& x : w) { r -= x.second; if (r <= 0) return x.first; }
    return w.back().first;
  }
  Inst make(int type, int key_from) {
    Inst s; s.type = type; const SceneDef& d = kDefs[type];
    s.e0 = d.e0; s.e1 = d.e1; s.th0 = d.th0; s.th1 = d.th1;
    if (type == kShelterScene) s.th0 = std::max(.0f, have_cur ? threat(std::max(cur.b0, wbar - 1)) : 0.f);
    int len = d.min_bars + 2 * I((d.max_bars - d.min_bars) / 2 + 1); s.b1 = len;   /* b1 holds the length until entry */
    s.key = key_from;
    if ((type == kTravel || type == kAftermath || type == kHerd) && U() < .15 + .4 * N(kNDrift)) {
      static const int mv[4] = {5, -2, -5, 3}; s.key = key_from + mv[I(4)]; while (s.key > 43) s.key -= 12; while (s.key < 34) s.key += 12;
    }
    if (type == kTravel || type == kHerd) s.lead_alt = travels;
    s.animal = type == kMajesty ? 1 : type == kStalk || type == kFight ? 2 : type == kTribe ? 4 : type == kHerd || type == kStampede ? 0 : have_cur ? cur.animal : 0;
    return s;
  }
  void enter(int b) {
    bool built = have_cur && is_build_into(cur, nxt);
    int len = nxt.b1; nxt.b0 = b; nxt.b1 = b + len; nxt.built = built;
    if (nxt.type == kObserveScene || nxt.type == kRest || nxt.type == kShelterScene) nxt.animal = have_cur ? cur.animal : 0;
    cur = nxt; have_cur = true; chords.clear(); walk = 0;
    if (cur.type == kTravel || cur.type == kHerd || cur.type == kRest || cur.type == kObserveScene) quiet++; else quiet = 0;
    if (cur.type == kTravel || cur.type == kHerd) travels++;
    if (cur.type == kTribe) {
      tribe = {{"taiko", LO, {1,0,0,1,0,0,1,0}, 0, 0, 0}, {"logdrum", -1, {1,0,1,1,0}, 2, 0, 0}, {"taiko", HI, {0,0,1,0,1,0,1}, 4, 0, 0},
               {"bigdrum", 36, {1,0,0,0,0,0,1,0,0,0,0,0}, 6, 0, 0}, {"rattle", 0, {1,1,0,1,1,0}, 1, 0, 0}, {"taiko", MI, {0,1,0,0,1,1,0,1,0}, 8, 0, 0}};
    }
    if (cur.type == kCoda) nxt = make(kCoda, cur.key);
    else nxt = make(stopping ? kCoda : pick_next(cur.type), cur.key);
    static const char* kn[12] = {"C", "C#", "D", "Eb", "E", "F", "F#", "G", "G#", "A", "Bb", "B"};
    char buf[96]; snprintf(buf, sizeof buf, "scene: %s (%d bars, key %s minor)", scene_name(cur.type), cur.b1 - cur.b0, kn[cur.key % 12]);
    mark(std::max(T(b), cut), buf);
    if (built) {   /* the arrival */
      n(T(b), 1.2, "bigdrum", 33, 1.0f); n(T(b), .8, "taiko", LO, 1.0f);
    }
  }

  /* ---- one bar */
  void write_bar(int b) {
    T(b + 2);
    if (!have_cur || b >= cur.b1) {
      if (have_cur && cur.type == kCoda) { finished = true; pipe_to(-1, 0, T(b) + 4); mark(T(b), "the end"); return; }
      enter(b);
    }
    parts_mask = 0; bar_lead = 0;
    const SceneDef& d = kDefs[cur.type]; int k = b - cur.b0, L = cur.b1 - cur.b0, left = cur.b1 - b;
    float e = energy_at(b), th = threat(b); double t0 = T(b), Bt = B(b), BR = BAR(b);
    bool breathe = Nc(kNSilence) > 0 && U() < Nc(kNSilence) * .55;          /* Silence: whole bars drop out */
    bool build_next = is_build_into(cur, nxt);

    /* drone */
    int dm = d.drone == -99 ? -1 : key_at(b) + d.drone; if (Nc(kNSilence) > .8f) dm = -1;
    pipe_to(dm, d.drone_v * (.8f + .4f * N(kNMajesty) * (cur.type == kMajesty)), t0);

    /* settle ramp over the last two bars before a quieter scene */
    if (left <= 2 && !build_next && L >= 4) { ramp_t0 = T(cur.b1 - 2); ramp_t1 = T(cur.b1 - 2) + 2 * BR; ramp_v0 = 1; ramp_v1 = .45f; }
    else { ramp_t0 = 1e30; ramp_t1 = 0; }

    /* the scene */
    Style st = d.drums;
    if (cur.type == kDawn && k < 6) st = kNone;
    if (cur.type == kAftermath && k >= 4) st = kNone;
    if (cur.type == kCoda && k >= 8) st = kNone;
    if (cur.type == kRest || cur.type == kObserveScene) e *= .6f;
    if (!breathe) drummer(b, st, e, th);
    if (cur.type == kShelterScene && k % 2 == 0) n(t0, .5, "taiko", LO, .25f);
    /* fill into the next bar's scene: a roll that grows, or one low hit */
    if (left == 1 && st != kNone && !breathe) {
      if (build_next || kDefs[nxt.type].e0 > e) { for (int j = 0; j < 16; j++) n(t0 + j * Bt / 4, .25, "taiko", j < 6 ? HI : j < 11 ? MI : LO, .35f + .04f * j + .2f * e); }
      else n(t0 + 2 * Bt, 1.5, "taiko", LO, .5f * e + .2f);
    }
    float dens = N(kNDensity);
    switch (cur.type) {
      case kDawn:
        if (k < 10) stones(b, .45f);
        if (k == 3) call("rex", t0, 1.2f, -.4f, .8f, b);
        if (k == 6) call("ptero", t0 + 2 * Bt, .5f, .6f, .2f, b);
        if (k == 7) call("ptero", t0, .45f, -.6f, .2f, b);
        if (k == 9 || k == 11 || k == 13) call("herd", t0 + 3 * Bt, .5f, -.7f + .35f * (k - 9), .8f, b);
        break;
      case kTravel:
        if (k >= 2 && !breathe && dens > .2f) ostinato(b, .55f);
        if (U() < .45 * (.5 + N(kNWildness))) call("herd", t0 + I(4) * Bt, .55f, float(U(-.8, .8)), .3f, b);
        if (U() < .12) call("ptero", t0 + 2 * Bt, .45f, float(U(-.7, .7)), .3f, b);
        if (cur.type == kTravel && cur.th1 > 0 && left == 3 && U() < .5) call("chatter", t0, .5f, .6f, .8f, b);
        break;
      case kHerd:
        if (!breathe && dens > .2f) ostinato(b, .6f);
        herd_cloud(b, float(k) / L, float(k + 1) / L, 1.4f);
        if (k % 4 == 2) call("ptero", t0 + 2 * Bt, .5f, k % 8 == 2 ? .7f : -.7f, .2f, b);
        break;
      case kMajesty:
        if (k == 2) call("sauropod", t0, 1.3f, .2f, 0, b);
        if (k == 9) call("sauropod", t0, 1.2f, -.2f, 0, b);
        if (k == 4) { braam(b, .85f); call("boom", t0, 1.0f, 0, 0, b); }
        if (k >= 4 && (k - 4) % 2 == 0 && left >= 2) pads(b, .3f);
        break;
      case kStalk:
        if (th > .4f && k % 2 == 0 && left >= 2) n(t0, 2 * BR * .95, "horn", root(b, -1) + 12, .15f + .25f * th);
        if (th > .55f && !breathe) ostinato(b, .25f + .4f * th);
        if (th > .3f && U() < .5) call("bark", t0 + 3.5 * Bt, .5f + .4f * th, U() < .5 ? -.6f : .6f, .3f * (1 - th), b);
        if (k == 5) call("hiss", t0, .6f, -.2f, .1f, b);
        if (k == 8) call("chatter", t0, .7f, .3f, .2f, b);
        if (left == 1) call("screech", t0, 1.0f, .4f, 0, b);
        break;
      case kStampede: case kFight: {
        if (k % 4 == 0) { braam(b, cur.type == kFight ? .9f : 1.0f); call("boom", t0, .9f, 0, 0, b); }
        if (!breathe) ostinato(b, .85f);
        if (k >= 4 && (k - 4) % 2 == 0 && left >= 2) pads(b, .3f);
        if (cur.type == kStampede) {
          if (k == std::min(16, L - 4)) call("rex", t0, 1.8f, 0, 0, b);
          herd_cloud(b, float(k) / L, float(k + 1) / L, 3.2f);
          if (k % 4 == 2) call("ptero", t0 + 2 * Bt, .5f, (k / 4) % 2 ? .7f : -.7f, .2f, b);
          if (L >= 18 && k == 11) { for (int j = 0; j < 16; j++) n(t0 + j * Bt / 4, .22, "taiko", MI, .5f + .03f * j); eng.push_riser(T(b + 1), 2.5, .35f, key_at(b + 1)); }
        } else {
          if (U() < .5) call(U() < .5 ? "screech" : "bark", t0 + (1 + I(3)) * Bt, .8f, float(U(-.6, .6)), 0, b);
          if (k == 2 && U() < .5 + .5 * N(kNDanger)) call("rex", t0, 1.5f, .1f, .1f, b);
        }
        break;
      }
      case kAftermath:
        if (k >= 2) stones(b, .45f);
        if (k == 5) call("sauropod", t0, .9f, -.5f, .8f, b);
        break;
      case kTribe:
        if (k >= 4 && k % 2 == 0 && U() < .7) { static const double o[3] = {0, 2, 3.5}; call("shout", t0 + o[I(3)] * Bt, .5f + .3f * e, float(U(-.7, .7)), .2f, b); }
        if (k == 8 || k == 12 || k == 16) { braam(b, .8f); call("boom", t0, .6f, 0, 0, b); }
        break;
      case kRest:
        if (k % 2 == 1) stones(b, .35f);
        if (k % 3 == 1 && U() < .6) call(animal_call(cur.animal), t0 + 2 * Bt, .5f, float(U(-.7, .7)), .85f, b);
        break;
      case kObserveScene:   /* everything falls back; the animals come close and take the front */
        if (k % 2 == 0 && left >= 2) pads(b, .22f);
        if (k % 2 == 1 || U() < .3) call(animal_call(cur.animal), t0 + (I(2) + 1) * Bt, .9f, float(U(-.5, .5)), .05f, b);
        break;
      case kShelterScene:   /* under cover: low horns, the world heard from far away */
        if (k % 2 == 0 && left >= 2) n(t0, 2 * BR * .97, "horn", root(b, -1), .2f);
        if (U() < .4) call(animal_call(cur.animal), t0 + I(4) * Bt, .7f, float(U(-.8, .8)), .95f, b);
        break;
      case kCoda:
        if (k == L - 3) {
          double lt = t0, dd = 3 * BR + 4; int kk = cur.key;
          n(lt, dd, "fiddle", kk + 12, .7f); n(lt, dd, "fiddle", kk + 7, .55f); n(lt, dd, "horn", kk + 12, .7f); n(lt, dd, "horn", kk + 7, .55f); n(lt, dd, "horn", kk, .6f);
        }
        if (k == L - 6) call("rex", t0, 1.0f, .5f, .85f, b);
        if (k == L - 2) call("ptero", t0, .4f, -.5f, .3f, b);
        break;
      default: break;
    }
    /* interact: a creature calls close, the lead answers it */
    if (interact_bars > 0) {
      interact_bars--;
      const char* who = animal_call(cur.animal);
      call(who, t0, .9f, -.3f, 0, b);
      int r = deg(0, b, 0); static const int ans[4][4] = {{4, 2, 3, 0}, {7, 5, 4, 2}, {2, 4, 1, 0}, {0, 4, 7, 4}};
      const int* a = ans[(interact_bars + b) & 3];
      for (int j = 0; j < 4; j++) { double tt = t0 + (2 + .5 * j) * Bt; int m = r + kScale[a[j] % 7] + (a[j] >= 7 ? 12 : 0); n(tt, Bt * .48, "fiddle", m, .75f); onsets.push_back(tt); }
    }
    lead_bar(b, build_next);

    /* look-ahead: lead into the next scene */
    if (L >= 2 && cur.type != kCoda) lead_in(b, left, build_next);
    info.resize(size_t(b + 1)); info[size_t(b)] = {cur.type, energy_at(b), th, chord(b), key_at(b), parts_mask, bar_lead, t0};
  }
  void lead_in(int b, int left, bool build) {
    int X = cur.b1; double XT = T(b) + left * BAR(b), Bt = B(b), BR = BAR(b);
    int nkey = nxt.key, nroot = nkey;
    static const Style next_style[kNumScenes] = {kNone, kWalk, kWalk, kNone, kNone, kGallop, kNone, kNone, kNone, kNone, kNone, kGallop, kNone};
    if (build) {
      bool big = nxt.type == kStampede || nxt.type == kMajesty || nxt.type == kTribe || nxt.type == kFight;
      int pre = nxt.type == kStampede ? 4 : 2; Style ns = next_style[nxt.type];
      if (ns != kNone && left <= pre) {   /* the next scene's drums sneak in early and swell */
        float f = .15f + .65f * float(pre - left) / pre;
        drummer(b, ns, kDefs[nxt.type].e0, kDefs[nxt.type].th0, f);
      }
      if (left == 2) {
        for (int j = 0; j < 8; j++) n(T(b) + j * Bt / 2, .25, "taiko", HI, .3f + .04f * j);                         /* roll: 8ths... */
        int r = root(X - 1, -1); for (int j = 0; j < 3; j++) n(T(b) + j * Bt, 2 * BR - j * Bt, "horn", r + (j == 0 ? 12 : j == 1 ? 19 : 24), .18f + .07f * j);
        eng.push_riser(XT, big ? 4.0 : 2.5, big ? .5f : .3f, nroot);
        mark(std::max(T(b), cut), std::string("lead-in: building into ") + scene_name(nxt.type));
      }
      if (left == 1) {
        for (int j = 0; j < 16; j++) n(T(b) + j * Bt / 4, .22, "taiko", j < 8 ? HI : MI, .45f + .03f * j);            /* ...then 16ths */
        for (int j = 0; j < 4; j++) n(T(b) + j * Bt, .6, "bigdrum", 33, .4f + .15f * j);
        std::vector<int> run; if (big) for (int o : {0, 2, 3, 5, 7, 8, 10}) run.push_back(nroot - 12 + o); else for (int o : {3, 5, 7, 10}) run.push_back(nroot - 12 + o);
        int rn = int(run.size()); for (int j = 0; j < rn; j++) n(XT - (rn - j) * Bt / 2, Bt / 2 * .95, "fiddle", run[size_t(j)], .55f + .05f * j);
        n(XT, 2 * Bt, "fiddle", nroot, .9f); lead_t = std::max(lead_t, XT + 2 * Bt);
      }
    } else {
      if (left == 1) {
        int r = nkey - 12; for (int iv : {12, 19, 24}) n(XT - BR, 3 * BR, "horn", r + iv, .22f);                       /* the new chord, early */
        for (int j = 0; j < 3; j++) n(XT - (3 - j) * Bt / 2 - Bt, Bt / 2 * .95, "fiddle", nroot + (j == 0 ? 7 : j == 1 ? 5 : 3), .6f - .05f * j);
        n(XT - Bt / 2, 3 * Bt, "fiddle", nroot, .6f); lead_t = std::max(lead_t, XT + 2.5 * Bt);
        mark(std::max(T(b), cut), std::string("lead-in: settling into ") + scene_name(nxt.type));
      }
    }
  }

  /* ---- live */
  void rewrite(double t_cut, const char* why) {
    eng.cancel_from(t_cut); cut = t_cut;
    int rb = bar_at(t_cut);
    if (cur.b0 > rb) rollback(rb);
    if (rb < wbar) wbar = rb;
    if (drone_h && drone_on >= t_cut) { drone_h = 0; drone_midi = -1; }
    for (auto it = chords.begin(); it != chords.end();) { if (it->first > rb) it = chords.erase(it); else ++it; }
    if (lead_t > t_cut) lead_t = t_cut + B(rb);
    onsets.erase(std::remove_if(onsets.begin(), onsets.end(), [&](double o) { return o >= t_cut; }), onsets.end());
    bar_t.resize(size_t(rb + 1)); bar_bpm.resize(size_t(rb + 1));
    if (cur.b0 > rb) { /* a scene entered in the look-ahead that never sounded: step back into the one playing */ }
    marks.erase(std::remove_if(marks.begin(), marks.end(), [&](const Mark& m) {   /* plans that will no longer sound */
      return m.t >= t_cut - 1e-6 && (m.text.rfind("scene:", 0) == 0 || m.text.rfind("lead-in:", 0) == 0); }), marks.end());
    last_rewrite = now; if (why) mark(now, why);
  }
  double next_beat() { int b = bar_at(now + .05); double Bt = B(b), t = T(b); while (t < now + .05) t += Bt; return t; }
  void request(int scene, int urgency, const char* why) {
    if (!started || finished) return;
    int pb = bar_at(now);
    /* the scene playing now may not be cur if the look-ahead already entered the next one */
    if (cur.b0 > pb) { rollback(pb); }
    Inst want = make(scene, cur.key);
    if (urgency == 0) {
      nxt = want;
      if (cur.b1 - pb <= 3) rewrite(T(std::max(pb + 1, cur.b1 - 2)), why);   /* the lead-in was for something else */
      else if (why) mark(now, why);
      return;
    }
    nxt = want;
    if (urgency == 1) {
      int X = std::max(pb + 2, std::min(cur.b1, pb + 2)); if (T(pb + 1) - now < B(pb)) X = pb + 3;
      cur.b1 = std::max(cur.b0 + 1, X);
      rewrite(next_beat(), why);
    } else {
      double hit = next_beat();
      rewrite(hit, why);
      cur.b1 = pb + 1 + (hit - T(pb) > 3 * B(pb) ? 1 : 0);
      /* the hit, on the next beat */
      n(hit, 1.2, "bigdrum", 33, 1); n(hit, .8, "taiko", LO, 1); n(hit, .8, "taiko", MI, .8f);
      Event e; e.t = hit; e.part = "subdrop"; e.vel = .8f; eng.push(e);
      call("boom", hit, 1.0f, 0, 0, pb);
      int r = cur.key - 12; for (int iv : {12, 19, 24}) n(hit, 2.5 * BAR(pb), "braam", r + iv, .9f);
      if (cur.b1 > pb + 1) { for (int j = 0; j < 16; j++) n(T(pb + 1) + j * B(pb) / 4, .22, "taiko", j < 8 ? HI : MI, .5f + .03f * j); }
    }
  }
  /* The look-ahead already entered a scene that hasn't sounded yet: go back to the one that is playing. */
  Inst prev; bool have_prev = false;
  void rollback(int pb) {
    if (have_prev && cur.b0 > pb) { nxt = cur; nxt.b1 = cur.b1 - cur.b0; cur = prev; }
  }
  void tick(double t) {
    now = t; if (!started || finished) return;
    if (notes_dirty && now - last_rewrite > .5) { notes_dirty = false; rewrite(next_beat(), nullptr); }
    while (!finished && T(wbar) < now + 2 * BAR(std::max(0, wbar - 1)) + .05) {
      int before_type = have_cur ? cur.b0 : -1; Inst before = cur;
      write_bar(wbar);
      if (have_cur && cur.b0 != before_type) { prev = before; have_prev = before_type >= 0; }
      wbar++; cut = -1;
    }
  }
};

Conductor::Conductor(Engine& e, uint32_t seed) : p(new Impl(e, seed)) {}
Conductor::~Conductor() { delete p; }
void Conductor::start(double t) {
  std::lock_guard<std::recursive_mutex> l(p->m);
  p->bar_t = {t + .1}; p->bar_bpm = {p->bpm_now()}; p->wbar = 0; p->started = true; p->now = t;
  p->nxt = p->make(kDawn, 38); p->have_cur = false; p->mark(t, "start");
}
void Conductor::stop() {
  std::lock_guard<std::recursive_mutex> l(p->m);
  if (!p->started || p->stopping) return; p->stopping = true; p->request(kCoda, 1, "stop: into the coda");
}
void Conductor::halt(double t) {
  std::lock_guard<std::recursive_mutex> l(p->m);
  if (!p->started || p->finished) return;
  p->eng.cancel_from(t); if (p->drone_h) p->eng.release(p->drone_h, t + 1.5);
  p->drone_h = 0; p->drone_midi = -1; p->finished = true; p->mark(t, "halt");
}
bool Conductor::running() const { return p->started && !p->finished; }
void Conductor::tick(double now) { std::lock_guard<std::recursive_mutex> l(p->m); p->tick(now); }
void Conductor::note(int k, int v) {
  std::lock_guard<std::recursive_mutex> l(p->m);
  if (k < 0 || k >= kNumNotes) return; v = std::max(0, std::min(100, v)); if (p->notes[k] == v) return;
  p->notes[k] = v; p->notes_dirty = true;
  char buf[64]; snprintf(buf, sizeof buf, "note: %s %d", note_name(k), v); p->mark(p->now, buf);
}
int Conductor::note_value(int k) const { return k >= 0 && k < kNumNotes ? p->notes[k] : 0; }
void Conductor::action(int a) {
  std::lock_guard<std::recursive_mutex> l(p->m);
  std::string why = std::string("action: ") + action_name(a);
  switch (a) {
    case kWait: p->request(kRest, 1, why.c_str()); break;
    case kMoveOn: p->request(kTravel, 1, why.c_str()); break;
    case kFollowHerd: p->request(kHerd, 1, why.c_str()); break;
    case kShelter: p->request(kShelterScene, 1, why.c_str()); break;
    case kInteract: if (p->started && !p->finished) { p->interact_bars = 3; p->rewrite(p->T(p->bar_at(p->now) + 1), why.c_str()); } break;
    case kDefend: p->request(kFight, 2, why.c_str()); break;
    case kObserve: p->request(kObserveScene, 1, why.c_str()); break;
    case kTrack: p->request(kStalk, 1, why.c_str()); break;
    default: break;
  }
  if (a == kTrack && p->nxt.type == kStalk) { p->nxt.th0 = .1f; p->nxt.th1 = .75f; }
}
void Conductor::request_scene(int s, int urgency) {
  std::lock_guard<std::recursive_mutex> l(p->m);
  if (s < 0 || s >= kNumScenes) return; p->request(s, urgency, (std::string("encounter: ") + scene_name(s)).c_str());
}
int Conductor::scene() const {
  std::lock_guard<std::recursive_mutex> l(p->m); int b = p->bar_at(p->now);
  return b < int(p->info.size()) ? p->info[size_t(b)].scene : -1;
}
float Conductor::energy() const {
  std::lock_guard<std::recursive_mutex> l(p->m); int b = p->bar_at(p->now);
  return b < int(p->info.size()) ? p->info[size_t(b)].e : 0;
}
void Conductor::gauges(float out[kNumGauges]) const {
  std::lock_guard<std::recursive_mutex> l(p->m);
  for (int i = 0; i < kNumGauges; i++) out[i] = NAN;
  if (!p->started) return;
  int b = p->bar_at(p->now); if (b >= int(p->info.size())) return;
  const auto& I = p->info[size_t(b)];
  out[kGThreat] = I.th;
  out[kGAwe] = (I.scene == kMajesty || I.scene == kObserveScene) ? .4f + .6f * I.e : .2f * p->N(kNMajesty);
  int parts = 0; for (int g = 0; g < 8; g++) parts += (I.parts >> g) & 1; out[kGParts] = parts / 8.f;
  int ln = 0; for (int k = std::max(0, b - 3); k <= b; k++) ln += p->info[size_t(k)].lead_notes; out[kGPhrases] = std::min(1.f, ln / 24.f);
  out[kGDepth] = .2f + .6f * p->N(kNSpace) + (I.scene == kShelterScene ? .2f : 0);
  out[kGHarmony] = I.chord == 0 ? 0 : I.chord == -6 ? 1 : std::min(1.f, std::abs(I.chord) / 6.f + .2f);
  out[kGMusic] = I.e;
}
std::string Conductor::gauge_text(int g) const {
  std::lock_guard<std::recursive_mutex> l(p->m);
  int b = p->bar_at(p->now); if (!p->started || b >= int(p->info.size())) return "";
  const auto& I = p->info[size_t(b)];
  switch (g) {
    case kGParts: { int n = 0; for (int k = 0; k < 8; k++) n += (I.parts >> k) & 1; return std::to_string(n) + " / 8"; }
    case kGPhrases: { int ln = 0; for (int k = std::max(0, b - 3); k <= b; k++) ln += p->info[size_t(k)].lead_notes; return ln == 0 ? "REST" : ln < 8 ? "SLOW" : ln < 18 ? "WALKING" : "BUSY"; }
    case kGDepth: return p->N(kNSpace) > .6f ? "FAR" : p->N(kNSpace) < .35f ? "NEAR" : "OPEN";
    case kGHarmony: { static const char* r[7] = {"i", "ii", "III", "iv", "v", "VI", "VII"}; return I.chord == -6 ? "bII" : r[((I.chord % 7) + 7) % 7]; }
    case kGMusic: { std::string s = scene_name(I.scene); for (auto& c : s) c = char(toupper(c)); return s; }
    default: return "";
  }
}
const std::vector<Conductor::Mark>& Conductor::marks() const { return p->marks; }
}  // namespace paleo
