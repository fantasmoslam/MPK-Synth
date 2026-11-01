/* SPDX-License-Identifier: GPL-3.0-only */
/* The Scape conductor, live. It writes two bars ahead of the playhead and rewrites whenever something changes
   (see conductor.h). Its material is generated, not stored: melodies grow from each expedition's own motifs
   (motifs.h), drums are built from rules per scene and keep changing (grooves.h), and harmony, ostinatos, pad
   voicings, creature entrances, fills and lead-ins are all drawn fresh from the seed. Nothing is copied from the
   earlier previews' scores. */
#include "conductor.h"
#include "grooves.h"
#include "motifs.h"
#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdio>
#include <map>
#include <mutex>
#include <random>

namespace paleo {
namespace {
const char* kSceneNames[kNumScenes] = {"dawn", "travel", "herd", "majesty", "stalk", "stampede", "aftermath", "tribe",
                                       "rest", "observe", "shelter", "fight", "coda"};
const char* kActionNames[kNumActions] = {"WAIT", "MOVE ON", "FOLLOW HERD", "SHELTER", "INTERACT", "DEFEND", "OBSERVE", "TRACK"};
const char* kNoteNames[kNumNotes] = {"Danger", "Majesty", "Density", "Pace", "Space", "Wildness", "Drift", "Silence"};

const int kScale[7] = {0, 2, 3, 5, 7, 8, 10};          /* natural minor */
const int LO = 31, MI = 36, HI = 55;                   /* taiko tunings: low, mid, high */

/* What each scene is: its length, its energy and threat curves, whether it is a peak, its drum family, its
   melodic character, and where the drone sits. These are the encounter design's shapes, not musical material. */
struct SceneDef {
  int min_bars, max_bars; float e0, e1, th0, th1; bool peak; groove::Family drums; int character;
  int drone; float drone_v;   /* drone pipe: semitones from the key, or -99 none */
};
using groove::kPulse; using groove::kHeart; using groove::kSparse; using groove::kDrive; using groove::kCrowd; using groove::kSilent;
const SceneDef kDefs[kNumScenes] = {
  /* dawn      */ {14, 18, .15f, .45f, 0, 0, false, kPulse, motif::kTravel, 0, .8f},
  /* travel    */ {8, 14, .5f, .55f, 0, .1f, false, kPulse, motif::kTravel, 0, .75f},
  /* herd      */ {10, 14, .5f, .7f, 0, .05f, false, kPulse, motif::kTravel, 0, .75f},
  /* majesty   */ {12, 16, .35f, .7f, 0, 0, true, kHeart, motif::kMajesty, -5, .85f},
  /* stalk     */ {10, 16, .3f, .7f, .2f, 1.f, false, kSparse, motif::kDanger, 0, .75f},
  /* stampede  */ {16, 24, .85f, 1.f, 1, 1, true, kDrive, motif::kDanger, 0, .85f},
  /* aftermath */ {8, 10, .4f, .2f, .3f, 0, false, kHeart, motif::kSorrow, 0, .7f},
  /* tribe     */ {14, 20, .55f, .9f, 0, .3f, true, kCrowd, motif::kDanger, 0, .6f},
  /* rest      */ {8, 12, .25f, .2f, 0, 0, false, kHeart, motif::kSorrow, 0, .65f},
  /* observe   */ {8, 12, .3f, .35f, 0, 0, false, kHeart, motif::kMajesty, -5, .7f},
  /* shelter   */ {8, 12, .2f, .15f, .3f, 0, false, kSilent, motif::kSorrow, -5, .6f},
  /* fight     */ {8, 12, .9f, .8f, 1, .7f, true, kDrive, motif::kDanger, 0, .85f},
  /* coda      */ {12, 16, .5f, .1f, 0, 0, false, kPulse, motif::kSorrow, 0, .75f},
};
bool is_peak(int s) { return kDefs[s].peak; }
bool is_drum(const std::string& p) { return p == "taiko" || p == "bigdrum" || p == "logdrum" || p == "rattle"; }
bool calm(int s) { return s == kRest || s == kObserveScene || s == kShelterScene || s == kAftermath || s == kCoda; }

struct LeadVoice { const char* part; int oct; float vm; };
struct LeadPlan { int from; std::vector<LeadVoice> voices; int octave; float vel; float rest0, rest1; };
enum BuildKind { kSwell, kStopHit, kClimb, kDropHit, kNumBuilds };
enum SettleKind { kThin, kHush, kRing, kNumSettles };
}  // namespace

const char* scene_name(int s) { return s >= 0 && s < kNumScenes ? kSceneNames[s] : "?"; }
const char* action_name(int a) { return a >= 0 && a < kNumActions ? kActionNames[a] : "?"; }
const char* note_name(int n) { return n >= 0 && n < kNumNotes ? kNoteNames[n] : "?"; }

struct Conductor::Impl {
  Engine& eng; std::mt19937 rng; uint32_t seed;
  std::recursive_mutex m;
  motif::Garden garden; groove::Drummer drum, drum_prev;
  int notes[kNumNotes]; bool notes_dirty = false; double last_rewrite = -1;
  std::vector<std::string>* trace = nullptr;

  /* this expedition's harmony, drawn from the seed: how strongly the chords pull home, which chords follow
     which, and its colour chord for danger (a minor chord a semitone or a tritone above the key) */
  float trans[7][7]; float home_pull; int danger_off;

  /* time: every written bar has a start time and a tempo */
  std::vector<double> bar_t; std::vector<float> bar_bpm;
  bool started = false, stopping = false, finished = false; double now = 0;
  int wbar = 0; double cut = -1;

  struct Inst {
    int type = kTravel, b0 = 0, b1 = 16, key = 38; float e0 = .5f, e1 = .5f, th0 = 0, th1 = 0; int lead_alt = 0; int animal = 0;
    bool built = false; int serial = 0;
    /* drawn fresh for each scene */
    int hr = 2; int lead_from = 0; int drums_from = 0; std::vector<int> voicing; int giant_bar = -1, giant2_bar = -1, peak_bar = -1;
    int braam_every = 4; int lift = 0, lift_bar = -1; float herd_peak = .55f, herd_width = .32f;
    std::vector<int> ost_iv; std::vector<float> ost_v; bool ost_on = true;
  };
  Inst cur, nxt, prev; bool have_cur = false, have_prev = false; int quiet = 0, travels = 0, serial = 0;
  struct BarInfo { int scene; float e, th; int chord, key; int parts; int lead_notes; double t; };
  std::vector<BarInfo> info;

  std::map<int, int> chords; int walk = 0;
  double lead_t = 0; std::vector<double> onsets;
  int drone_h = 0, drone_midi = -1; double drone_on = 0;
  int interact_bars = 0;
  double ramp_t0 = 1e30, ramp_t1 = 0; float ramp_v0 = 1, ramp_v1 = 1;
  double hush_from = 1e30;
  int parts_mask = 0, bar_lead = 0, last_build = -1, last_settle = -1, build_kind = -1, settle_kind = -1;
  std::vector<Mark> marks;

  Impl(Engine& e, uint32_t s) : eng(e), rng(s), seed(s), garden(s), drum(s), drum_prev(s) {
    for (auto& n : notes) n = 50;
    home_pull = float(.35 + .35 * U());
    for (int a = 0; a < 7; a++) { int z1 = 1 + I(6), z2 = 1 + I(6); for (int b = 0; b < 7; b++) trans[a][b] = (b == a || b == z1 || b == z2) ? 0.f : float(.15 + U()); }
    danger_off = U() < .6 ? 1 : 6;
  }
  double U() { return std::uniform_real_distribution<double>(0, 1)(rng); }
  double U(double a, double b) { return a + (b - a) * U(); }
  int I(int n) { return int(U() * n) % std::max(1, n); }
  float N(int k) const { return notes[k] / 100.f; }
  float Nc(int k) const { return (notes[k] - 50) / 50.f; }
  void mark(double t, const std::string& s) { marks.push_back({t, s}); }
  void tr(const std::string& s) { if (trace) trace->push_back(s); }

  /* ---- time */
  float bpm_now() const { return 100.f * powf(1.25f, Nc(kNPace)); }
  double B(int b) const { return 60.0 / bar_bpm[size_t(b)]; }
  double BAR(int b) const { return 4 * B(b); }
  double T(int b) {
    while (int(bar_t.size()) <= b) { size_t k = bar_t.size(); bar_t.push_back(bar_t[k - 1] + 4 * 60.0 / bar_bpm[k - 1]); bar_bpm.push_back(bpm_now()); }
    return bar_t[size_t(b)];
  }
  int bar_at(double t) { T(wbar + 8); return std::max(0, int(std::upper_bound(bar_t.begin(), bar_t.end(), t) - bar_t.begin()) - 1); }

  /* ---- writing */
  float drum_scale(double t) const {
    if (t >= hush_from) return 0;
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
  /* A creature calls into a gap in the lead, on the 8th grid. */
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
  int key_at(int b) const { return cur.key + (cur.lift_bar >= 0 && b >= cur.lift_bar ? cur.lift : 0); }
  int deg(int d, int b, int octave = 0) const { int o = (d >= 0 ? d / 7 : -((-d + 6) / 7)), i = d - 7 * o; return key_at(b) + 12 * (o + octave) + kScale[i]; }
  bool is_build_into(const Inst& a, const Inst& to) const {
    if (calm(to.type)) return false;
    return kDefs[to.type].e0 > a.e1 - .05f || to.type == kStampede || to.type == kMajesty || to.type == kTribe || to.type == kFight;
  }
  int cadence_chord(bool build) {   /* drawn once per lead-in, kept in the chord map so it stays put */
    static const int bc[3] = {6, 5, 4}, sc[3] = {3, 5, 2}; return build ? bc[I(3)] : sc[I(3)];
  }
  int chord(int b) {
    if (b >= cur.b1 - 2 && b < cur.b1 && cur.b1 - cur.b0 >= 2) {
      int key = -1000 - (cur.b1 - 2); auto it = chords.find(key); if (it != chords.end()) return it->second;
      int c = cadence_chord(is_build_into(cur, nxt)); chords[key] = c; return c;
    }
    int pair = b - ((b - cur.b0) % std::max(1, cur.hr));
    auto it = chords.find(pair); if (it != chords.end()) return it->second;
    int c;
    float home = home_pull * (1.3f - .9f * N(kNDrift));
    bool danger = cur.type == kStalk || cur.type == kFight || cur.type == kStampede;
    if (pair == cur.b0) c = 0;
    else if (danger && U() < .25 + .3 * N(kNDanger)) c = -6;
    else if (walk != 0 && U() < home) c = 0;
    else {
      int from = walk < 0 ? 0 : walk; float tot = 0; for (int k = 1; k < 7; k++) tot += trans[from][k];
      double r = U() * tot; c = 1; for (int k = 1; k < 7; k++) { r -= trans[from][k]; if (r <= 0) { c = k; break; } }
    }
    walk = c; chords[pair] = c; return c;
  }
  int root(int b, int octave = -1) { int c = chord(b); return c == -6 ? key_at(b) + danger_off + 12 * octave : deg(c, b, octave); }
  bool chord_tone(int b, int m) {
    int c = chord(b), k = key_at(b);
    for (int j = 0; j < 3; j++) {
      int pc = c == -6 ? (k + danger_off + (j == 0 ? 0 : j == 1 ? 3 : 7)) % 12 : (k + kScale[(c + 2 * j) % 7]) % 12;
      if (((m % 12) + 12) % 12 == pc) return true;
    }
    return false;
  }

  /* ---- melody */
  LeadPlan lead_plan(int b) const {
    float th = threat(b); int f = cur.lead_from;
    switch (cur.type) {
      case kDawn: return {f, {{"fiddle", 0, 1}}, 0, .75f, 2, 6};
      case kTravel: case kHerd:
        if (cur.lead_alt & 1) return {f, {{"horn", 0, 1}, {"fiddle", 12, .5f}}, 0, .85f, 2, 6};
        return {f, {{"fiddle", 0, 1}, {"horn", -12, .45f}}, 0, .85f, 2, 6};
      case kMajesty: return {f, {{"fiddle", 0, 1}, {"horn", -12, .5f}}, 0, .9f, 3, 6};
      case kStalk: if (th > .65f) return {0, {{"horn", 0, .9f}}, -1, .7f + .3f * th, 2, 5}; break;
      case kStampede: return {f, {{"horn", 0, 1}, {"horn", -12, .75f}, {"fiddle", 12, .5f}}, 0, 1, 0, 1.5f};
      case kAftermath: return {f, {{"fiddle", 0, .9f}, {"horn", -12, .4f}}, 0, .75f, 3, 5};
      case kTribe: return {f, {{"horn", 0, .9f}, {"fiddle", -12, .5f}}, 0, .85f, 1, 3};
      case kRest: return {f, {{"fiddle", 0, .7f}}, 0, .6f, 4, 8};
      case kObserveScene: return {f, {{"fiddle", 0, .8f}}, 0, .7f, 4, 7};
      case kFight: return {0, {{"horn", 0, 1}, {"fiddle", 12, .6f}}, 0, .95f, 0, 1.5f};
      case kCoda: return {f, {{"fiddle", 0, .9f}, {"horn", -12, .55f}}, 0, .7f, 2, 4};
      default: break;
    }
    return {1 << 20, {}, 0, 0, 0, 0};
  }
  std::string line_text(const motif::Line& l) { std::string s; char b[16]; for (auto& n : l) { snprintf(b, sizeof b, "%d/%g ", n.deg, n.beats); s += b; } return s; }
  void lead_bar(int b, bool build_next) {
    LeadPlan L = lead_plan(b); if (L.voices.empty() || b - cur.b0 < L.from) return;
    double t1 = T(b + 1), stop_t = T(b) + (cur.b1 - b) * BAR(b) - (build_next ? 2 : 1) * B(b);
    if (cur.type == kCoda) stop_t = T(b) + (cur.b1 - 4 - b) * BAR(b);
    if (cur.type == kStalk) stop_t = std::min(stop_t, t1);
    if (lead_t < T(b)) lead_t = T(b) + I(3) * .5 * B(b);
    float space = .6f + .8f * N(kNSpace) + 1.2f * std::max(0.f, Nc(kNSilence));
    int ch = kDefs[cur.type].character;
    while (lead_t < t1 && lead_t < stop_t - B(b)) {
      double avail = (stop_t - lead_t) / B(b);
      motif::Line p = garden.phrase(ch, float(std::min(avail, 16.0)), cur.serial);
      if (p.empty()) break;
      tr("M " + line_text(p));
      int sb = bar_at(lead_t), c = chord(sb), start = (c == -6 ? 0 : c) + 2 * I(3); while (start > 7) start -= 7;
      double t = lead_t; int last = int(p.size()) - 1;
      for (int i = 0; i <= last; i++) {
        int bb = bar_at(t), m = deg(start + p[size_t(i)].deg, bb, L.octave);
        if (i == last && !chord_tone(bb, m)) { int best = m; for (int k = 1; k <= 3; k++) { if (chord_tone(bb, m - k)) { best = m - k; break; } if (chord_tone(bb, m + k)) { best = m + k; break; } } m = best; }
        double d = p[size_t(i)].beats * B(b) * .97;
        bool any = false; for (auto& v : L.voices) any |= n(t, d, v.part, m + v.oct, L.vel * v.vm * float(U(.88, 1.0)));
        if (any) { onsets.push_back(t); bar_lead++; }
        t += p[size_t(i)].beats * B(b);
      }
      double rest = U(L.rest0, L.rest1) * space;
      lead_t = B(b) * std::round((t + rest * B(b)) / (B(b) * .5)) * .5;
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

  /* ---- drums: the groove engine's hits, on PALEO's drums */
  void play_hits(int b, const groove::Bar& hits, float scale, double from = -1) {
    double t0 = T(b), s16 = B(b) / 4; std::string cells[16];
    for (auto& h : hits) {
      double t = t0 + h.step * s16; if (t < from) continue; float v = h.vel * scale;
      switch (h.voice) {
        case groove::kLo: n(t, .5, "taiko", LO, v); break;
        case groove::kMi: n(t, .4, "taiko", MI, v); break;
        case groove::kHi: n(t, .3, "taiko", HI, v * .85f); break;
        case groove::kLog: n(t, .25, "logdrum", root(b, 1) + h.pitch_off, v * .65f); break;
        case groove::kRattle: n(t, .08, "rattle", 0, v * .55f); break;
        case groove::kBig: n(t, .8, "bigdrum", 33, v * .9f); break;
      }
      int si = std::max(0, std::min(15, int(h.step))); char c = "LMHgrB"[h.voice]; if (cells[si].find(c) == std::string::npos) cells[si] += c;
    }
    if (trace) { std::string pat; for (auto& c : cells) { std::string x = c; std::sort(x.begin(), x.end()); pat += (x.empty() ? "." : x) + "|"; }
      tr("D " + std::string(scene_name(cur.type)) + " " + pat); }
  }

  void pipe_to(int midi, float v, double t) {        /* the drone: the conductor moves it between scenes */
    if (midi == drone_midi) return;
    if (drone_h) eng.release(drone_h, std::max(t + 3, now + 4.5));
    drone_h = 0; drone_midi = midi;
    if (midi < 0) return;
    Event e; e.t = std::max(t - 2, std::max(now, cut)); e.part = "pipe"; e.midi = midi; e.vel = v; drone_h = eng.hold(e); drone_on = e.t;
  }
  void pads(int b, float v, int bars = 2) { int r = root(b, -1); for (int iv : cur.voicing) n(T(b), bars * BAR(b) * .97, "horn", r + iv, v * (.7f + .6f * N(kNMajesty))); }
  void braam(int b, float v) { int r = root(b, -2) + 12; for (int iv : cur.voicing) n(T(b), 3 * BAR(b), "braam", r + iv, v); }
  void ostinato(int b, float v) {
    if (!cur.ost_on || cur.ost_iv.empty()) return;
    int r = root(b, 0), L = int(cur.ost_iv.size()); double step = B(b) / 2;
    if ((b - cur.b0) % 4 == 3 && U() < .5) { int i = I(L); static const int pool[7] = {0, 7, 12, 10, 3, 5, -2}; cur.ost_iv[size_t(i)] = U() < .25 ? -99 : pool[I(7)]; }
    for (int k = 0; k < 8; k++) { int iv = cur.ost_iv[size_t(k % L)]; if (iv == -99) continue; n(T(b) + k * step, .22, "mouthbow", r + iv, v * cur.ost_v[size_t(k % L)]); }
  }
  void stones(int b, float v) {
    double t = T(b) + I(4) * .5 * B(b);
    while (t < T(b + 1)) { n(t, 1.2, "stone", deg(2 * I(4) + (U() < .2), b, 2), v * float(U(.55, 1))); t += (1.5 + I(5)) * B(b); }
  }
  void herd_cloud(int b, float x0, float x1, float peak) {
    double t = T(b), t1 = T(b + 1);
    float c = cur.herd_peak, w = cur.herd_width;
    while (true) {
      double x = x0 + (x1 - x0) * (t - T(b)) / (t1 - T(b));
      double dens = std::max(.25, peak * std::exp(-std::pow((x - c) / w, 2))) * (.6 + .8 * N(kNWildness));
      t += -std::log(1 - U() * .999) / dens; if (t >= t1) break;
      float pos = float(std::sin(2 * M_PI * x * (1 + cur.serial % 3 * .5)) * .8 + U(-.2, .2));
      call("herd", t, float(.42 + .28 * std::exp(-std::pow((x - c) / w, 2))), pos, float(U(0, .35)), b);
    }
  }
  const char* animal_call(int a) const { static const char* k[] = {"herd", "sauropod", "chatter", "ptero", "shout", "rex"}; return k[a % 6]; }

  /* ---- the scene grammar: a breather after every peak, never two peaks running, tension builds while nothing
     happens; Danger and Majesty weight the draw */
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
    if (type == kShelterScene) s.th0 = have_cur ? threat(std::max(cur.b0, wbar - 1)) : 0.f;
    int len = d.min_bars + 2 * I((d.max_bars - d.min_bars) / 2 + 1); s.b1 = len;   /* b1 holds the length until entry */
    s.key = key_from;
    if ((type == kTravel || type == kAftermath || type == kHerd || type == kRest) && U() < .15 + .4 * N(kNDrift)) {
      static const int mv[5] = {5, -2, -5, 3, 2}; s.key = key_from + mv[I(5)]; while (s.key > 43) s.key -= 12; while (s.key < 34) s.key += 12;
    }
    if (type == kTravel || type == kHerd) s.lead_alt = travels;
    s.animal = type == kMajesty ? (U() < .7 ? 1 : 5) : type == kStalk || type == kFight ? 2 : type == kTribe ? 4 : type == kHerd || type == kStampede ? 0 : have_cur ? cur.animal : I(4);
    /* fresh for this scene */
    int ch = d.character;
    s.hr = ch == motif::kMajesty ? (U() < .5 ? 2 : 4) : ch == motif::kDanger ? (U() < .5 ? 1 : 2) : ch == motif::kSorrow ? (U() < .6 ? 4 : 2) : 1 + I(2) + (U() < .2) * 2;
    if (s.hr == 3) s.hr = 4;
    s.lead_from = type == kDawn ? 4 + I(6) : type == kMajesty ? 2 + I(4) : type == kStampede ? 2 + I(4) : type == kTribe ? 4 + I(6) : I(3);
    s.drums_from = type == kDawn ? 3 + I(5) : 0;
    static const int pool[8] = {7, 12, 15, 19, 22, 24, 26, 27};
    s.voicing.clear(); while (s.voicing.size() < 3) { int v = pool[I(8)]; if (std::find(s.voicing.begin(), s.voicing.end(), v) == s.voicing.end()) s.voicing.push_back(v); }
    if (std::find(s.voicing.begin(), s.voicing.end(), 12) == s.voicing.end() && std::find(s.voicing.begin(), s.voicing.end(), 19) == s.voicing.end()) s.voicing[0] = 12;
    std::sort(s.voicing.begin(), s.voicing.end());
    s.giant_bar = 1 + I(std::max(1, len / 3)); s.giant2_bar = s.giant_bar + 4 + I(std::max(1, len - s.giant_bar - 6));
    s.peak_bar = len / 2 + I(std::max(1, len / 2 - 3));
    s.braam_every = U() < .4 ? 4 : U() < .5 ? 8 : 6;
    if (type == kStampede && len >= 16 && U() < .7) { static const int lf[3] = {2, 3, 5}; s.lift = lf[I(3)]; s.lift_bar = 8 + I(len - 12); }
    s.herd_peak = float(U(.3, .75)); s.herd_width = float(U(.2, .45));
    int ol = U() < .6 ? 8 : U() < .5 ? 6 : 4; s.ost_iv.clear(); s.ost_v.clear();
    static const int op[8] = {0, 0, 7, 12, 10, 3, 5, -99};
    for (int k = 0; k < ol; k++) { int iv = k == 0 ? 0 : op[I(8)]; s.ost_iv.push_back(iv); s.ost_v.push_back(k == 0 ? 1.f : float(U(.55, .85))); }
    s.ost_on = U() < .8;
    return s;
  }
  void enter(int b) {
    bool built = have_cur && is_build_into(cur, nxt);
    int len = nxt.b1; nxt.b0 = b; nxt.b1 = b + len; nxt.built = built; nxt.serial = ++serial;
    if (nxt.type == kObserveScene || nxt.type == kRest || nxt.type == kShelterScene) nxt.animal = have_cur ? cur.animal : nxt.animal;
    drum_prev = drum;
    cur = nxt; have_cur = true; chords.clear(); walk = 0; hush_from = 1e30;
    if (cur.type == kTravel || cur.type == kHerd || cur.type == kRest || cur.type == kObserveScene) quiet++; else quiet = 0;
    if (cur.type == kTravel || cur.type == kHerd) travels++;
    drum.scene(kDefs[cur.type].drums, cur.e0, cur.th0);
    garden.season(kDefs[cur.type].character, cur.serial);
    nxt = make(cur.type == kCoda || stopping ? kCoda : pick_next(cur.type), cur.key);
    static const char* kn[12] = {"C", "C#", "D", "Eb", "E", "F", "F#", "G", "G#", "A", "Bb", "B"};
    char buf[96]; snprintf(buf, sizeof buf, "scene: %s (%d bars, key %s minor)", scene_name(cur.type), cur.b1 - cur.b0, kn[cur.key % 12]);
    mark(std::max(T(b), cut), buf);
    if (built) {   /* the arrival, drawn fresh */
      double t = std::max(T(b), cut); n(t, 1.2, "bigdrum", 33, 1.0f);
      if (U() < .7) n(t, .8, "taiko", LO, 1.0f);
      if (U() < .35) call("boom", t, .8f, 0, 0, b);
      if (U() < .25) braam(b, .7f);
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
    bool breathe = Nc(kNSilence) > 0 && U() < Nc(kNSilence) * .55;
    bool build_next = is_build_into(cur, nxt);
    float dens = N(kNDensity);

    int dm = d.drone == -99 ? -1 : key_at(b) + d.drone; if (Nc(kNSilence) > .8f) dm = -1;
    pipe_to(dm, d.drone_v * (cur.type == kMajesty ? .8f + .4f * N(kNMajesty) : 1.f), t0);

    /* the bar's drums */
    if (left <= 2 && !build_next && L >= 2 && settle_kind == kThin) { ramp_t0 = T(cur.b1 - 2); ramp_t1 = T(cur.b1 - 2) + 2 * BR; ramp_v0 = 1; ramp_v1 = .4f; }
    else { ramp_t0 = 1e30; ramp_t1 = 0; }
    groove::Bar hits = drum.bar(cur.type == kRest || cur.type == kObserveScene ? e * .6f : e, th, .6f + .8f * dens);
    if (k < cur.drums_from) hits.clear();
    if (cur.type == kAftermath && k >= 4 + I(3)) hits.clear();
    if (cur.type == kCoda && k >= L - 6) hits.clear();
    if (left == 1 && !hits.empty() && !breathe) {   /* the fill into what comes next */
      groove::Bar f = drum.fill(groove::kNumFills, build_next ? 2 + I(3) : 1 + I(2), build_next);
      float from = 16 - 4.f * (build_next ? 2 : 1); hits.erase(std::remove_if(hits.begin(), hits.end(), [&](const groove::Hit& h) { return h.step >= from; }), hits.end());
      for (auto& h : f) hits.push_back(h);
    }
    if (!breathe) play_hits(b, hits, 1);
    if (cur.type == kShelterScene && U() < .5) n(t0 + I(2) * 2 * Bt, .5, "taiko", LO, .22f);

    switch (cur.type) {
      case kDawn:
        if (k < L - 4 && U() < .7) stones(b, .45f);
        if (k == cur.giant_bar) call(U() < .5 ? "rex" : "sauropod", t0 + I(4) * Bt, 1.1f, float(U(-.6, .6)), .8f, b);
        if (k >= 4 && U() < .25) call("ptero", t0 + I(4) * Bt, .45f, float(U(-.7, .7)), float(U(0, .4)), b);
        if (k > L / 2 && U() < .3) call("herd", t0 + I(4) * Bt, .5f, float(U(-.8, .8)), .75f, b);
        if (k >= cur.drums_from && k >= 6 && !breathe && dens > .3f && U() < .5) ostinato(b, .45f);
        break;
      case kTravel:
        if (k >= 1 && !breathe && dens > .2f) ostinato(b, .55f);
        if (U() < .4 * (.5 + N(kNWildness))) call("herd", t0 + I(4) * Bt, .55f, float(U(-.8, .8)), float(U(.1, .5)), b);
        if (U() < .12) call("ptero", t0 + I(4) * Bt, .45f, float(U(-.7, .7)), .3f, b);
        if (cur.th1 > 0 && left <= 4 && U() < .2 + .3 * N(kNDanger)) call("chatter", t0 + I(4) * Bt, .5f, float(U(-.7, .7)), .8f, b);
        break;
      case kHerd:
        if (!breathe && dens > .2f) ostinato(b, .6f);
        herd_cloud(b, float(k) / L, float(k + 1) / L, 1.4f);
        if (U() < .2) call("ptero", t0 + I(4) * Bt, .5f, float(U(-.7, .7)), .2f, b);
        break;
      case kMajesty:
        if (k == cur.giant_bar) { call(animal_call(cur.animal), t0, 1.3f, float(U(-.3, .3)), 0, b); braam(b, .85f); if (U() < .6) call("boom", t0, 1.0f, 0, 0, b); }
        if (k == cur.giant2_bar) call(animal_call(cur.animal), t0 + I(3) * Bt, 1.15f, float(U(-.4, .4)), float(U(0, .3)), b);
        if (k >= cur.giant_bar && (k - cur.giant_bar) % 2 == 0 && left >= 2) pads(b, .3f);
        break;
      case kStalk:
        if (th > .4f && k % cur.hr == 0 && left >= cur.hr) n(t0, cur.hr * BR * .95, "horn", root(b, -1) + 12, .15f + .25f * th);
        if (th > .55f && !breathe) ostinato(b, .25f + .4f * th);
        if (th > .3f && U() < .3 + .4 * th) call(U() < .7 ? "bark" : "chatter", t0 + U(0, 3.5) * Bt, .5f + .4f * th, float(U(-.7, .7)), .35f * (1 - th), b);
        if (k == cur.giant_bar + 2) call("hiss", t0 + I(4) * Bt, .6f, float(U(-.4, .4)), .1f, b);
        if (left == 1 && U() < .7) call("screech", t0 + I(4) * Bt, 1.0f, float(U(-.5, .5)), 0, b);
        break;
      case kStampede: case kFight: {
        if (k % cur.braam_every == 0) { braam(b, cur.type == kFight ? .9f : 1.0f); if (U() < .8) call("boom", t0, .9f, 0, 0, b); }
        if (!breathe) ostinato(b, .85f);
        if (k >= 2 && k % 2 == 0 && left >= 2 && U() < .7) pads(b, .3f);
        if (cur.type == kStampede) {
          if (k == cur.peak_bar) call("rex", t0 + I(2) * 2 * Bt, 1.8f, float(U(-.3, .3)), 0, b);
          herd_cloud(b, float(k) / L, float(k + 1) / L, 3.0f);
          if (U() < .25) call("ptero", t0 + I(4) * Bt, .5f, float(U(-.7, .7)), .2f, b);
          if (cur.lift_bar >= 0 && b == cur.b0 + cur.lift_bar - 1) { play_hits(b, drum.fill(groove::kNumFills, 2, true), 1); eng.push_riser(T(b + 1), 2.5, .35f, cur.key + cur.lift); }
        } else {
          if (U() < .5) call(U() < .5 ? "screech" : "bark", t0 + (1 + I(3)) * Bt, .8f, float(U(-.6, .6)), 0, b);
          if (k == cur.giant_bar && U() < .4 + .5 * N(kNDanger)) call("rex", t0, 1.5f, float(U(-.3, .3)), .1f, b);
        }
        break;
      }
      case kAftermath:
        if (k >= 1 && U() < .6) stones(b, .4f);
        if (k == cur.giant_bar + 1) call(U() < .5 ? "sauropod" : "ptero", t0 + I(4) * Bt, .9f, float(U(-.6, .6)), .8f, b);
        break;
      case kTribe:
        if (k >= 2 && U() < .5) call("shout", t0 + U(0, 3.5) * Bt, .5f + .3f * e, float(U(-.7, .7)), float(U(0, .4)), b);
        if (k >= 4 && k % cur.braam_every == 0) { braam(b, .8f); call("boom", t0, .6f, 0, 0, b); }
        break;
      case kRest:
        if (U() < .5) stones(b, .35f);
        if (U() < .3) call(animal_call(cur.animal), t0 + I(4) * Bt, .5f, float(U(-.7, .7)), .85f, b);
        break;
      case kObserveScene:
        if (k % cur.hr == 0 && left >= cur.hr) pads(b, .22f, cur.hr);
        if (U() < .6) call(animal_call(cur.animal), t0 + (I(3) + 1) * Bt, .9f, float(U(-.5, .5)), float(U(0, .15)), b);
        break;
      case kShelterScene:
        if (k % cur.hr == 0 && left >= cur.hr) n(t0, cur.hr * BR * .97, "horn", root(b, -1), .2f);
        if (U() < .4) call(animal_call(cur.animal), t0 + I(4) * Bt, .7f, float(U(-.8, .8)), .95f, b);
        break;
      case kCoda:
        if (k == L - 3) { double dd = 3 * BR + 4; int r = key_at(b); for (int iv : cur.voicing) n(t0, dd, "horn", r - 12 + iv, .6f); n(t0, dd, "fiddle", r + 7 * (U() < .5), .65f); }
        if (k == L - 6 && U() < .7) call(U() < .5 ? "rex" : "sauropod", t0, 1.0f, float(U(-.6, .6)), .85f, b);
        if (k == L - 2) call("ptero", t0 + I(4) * Bt, .4f, float(U(-.6, .6)), .3f, b);
        break;
      default: break;
    }
    if (interact_bars > 0) {   /* a creature calls close; the lead answers it with a phrase of its own */
      interact_bars--;
      call(animal_call(cur.animal), t0, .9f, float(U(-.5, .5)), 0, b);
      motif::Line p = garden.phrase(kDefs[cur.type].character, 2, cur.serial); double tt = t0 + 2 * Bt; int st = 2 * I(3);
      for (auto& q : p) { n(tt, q.beats * Bt * .95, "fiddle", deg(st + q.deg, b, 0), .75f); onsets.push_back(tt); tt += q.beats * Bt; }
    }
    lead_bar(b, build_next);
    if (L >= 2 && cur.type != kCoda) lead_in(b, left, build_next);
    info.resize(size_t(b + 1)); info[size_t(b)] = {cur.type, energy_at(b), th, chord(b), key_at(b), parts_mask, bar_lead, t0};
  }

  static int dmidi(int root, int d) { int o = d >= 0 ? d / 7 : -((-d + 6) / 7); return root + 12 * o + kScale[d - 7 * o]; }
  /* ---- lead-ins: a family of builds and settles, never the same kind twice running */
  void lead_in(int b, int left, bool build) {
    int X = cur.b1; double XT = T(b) + left * BAR(b), Bt = B(b), BR = BAR(b);
    int nroot = nxt.key; int nch = kDefs[nxt.type].character;
    bool big = nxt.type == kStampede || nxt.type == kMajesty || nxt.type == kTribe || nxt.type == kFight;
    if (left == 2 || (left == 1 && (build ? build_kind : settle_kind) < 0)) {
      if (build) { do build_kind = I(kNumBuilds); while (build_kind == last_build); last_build = build_kind; settle_kind = -1; }
      else { do settle_kind = I(kNumSettles); while (settle_kind == last_settle); last_settle = settle_kind; build_kind = -1; }
      static const char* bn[kNumBuilds] = {"swell", "stop and hit", "climb", "drop and hit"}, *sn[kNumSettles] = {"thin out", "hush", "one low ring"};
      mark(std::max(T(b), cut), std::string("lead-in: ") + (build ? bn[build_kind] : sn[settle_kind]) + (build ? " into " : " into ") + scene_name(nxt.type));
    }
    if (build) {
      if (left == 2) {
        if (build_kind == kSwell) { groove::Drummer pre = drum; pre.scene(kDefs[nxt.type].drums, kDefs[nxt.type].e0, kDefs[nxt.type].th0);
          play_hits(b, pre.bar(kDefs[nxt.type].e0, kDefs[nxt.type].th0, .8f), .3f); }
        int r = root(X - 1, -1), style = I(3);
        if (style == 0) { for (size_t j = 0; j < cur.voicing.size(); j++) n(T(b) + j * Bt, 2 * BR - j * Bt, "horn", r + cur.voicing[j], .18f + .07f * j); }
        else if (style == 1) { for (int j = 0; j < 4; j++) n(T(b) + j * 2 * Bt, 2 * Bt * .95, "horn", r + 12 + 3 * j + (j > 1) * 1, .2f + .06f * j); }
        eng.push_riser(XT, big ? 3.0 + U() : 2.0 + U(), big ? .45f : .28f, nroot);
      }
      if (left == 1) {
        groove::Bar f;
        switch (build_kind) {
          case kSwell: { groove::Drummer pre = drum; pre.scene(kDefs[nxt.type].drums, kDefs[nxt.type].e0, kDefs[nxt.type].th0);
            play_hits(b, pre.bar(kDefs[nxt.type].e0, kDefs[nxt.type].th0, .9f), .6f); f = drum.fill(groove::kRoll, 2, true); break; }
          case kStopHit: f = drum.fill(groove::kStopTime, 3, true); hush_from = T(b) + Bt; break;
          case kClimb: f = drum.fill(groove::kToms, 4, true); break;
          default: f = drum.fill(groove::kDrop, 4, true); hush_from = T(b); break;
        }
        double keep = hush_from; hush_from = 1e30; play_hits(b, f, 1); hush_from = keep;
        if (build_kind != kStopHit || U() < .5) {   /* the lead climbs into the new root, in a figure grown from the next scene's motifs */
          motif::Line up = garden.pickup(nch, true, 3 + I(5)); double len = 0; for (auto& q : up) len += q.beats;
          int top = up.back().deg, base = -top; double t = XT - len * Bt;
          for (auto& q : up) { n(t, q.beats * Bt * .95, "fiddle", dmidi(nroot, base + q.deg), .5f + .3f * float((t - (XT - len * Bt)) / (len * Bt))); t += q.beats * Bt; }
          n(XT, 2 * Bt, "fiddle", nroot, .85f); lead_t = std::max(lead_t, XT + 2 * Bt);
        }
      }
    } else if (left == 1) {
      if (settle_kind == kHush) hush_from = T(b) + (1 + I(2)) * Bt;
      if (settle_kind == kRing) { hush_from = T(b) + 2 * Bt; n(T(b) + 2 * Bt, 2, "taiko", LO, .5f); }
      if (U() < .6) { int r = nxt.key - 12; for (int iv : nxt.voicing) n(XT - BR * (U() < .5 ? 1 : .5), 3 * BR, "horn", r + iv, .2f); }
      if (U() < .7) {
        motif::Line dn = garden.pickup(nch, false, 2 + I(3)); double len = 0; for (auto& q : dn) len += q.beats;
        double t = XT - len * Bt; int top = -dn.back().deg;
        for (auto& q : dn) { n(t, q.beats * Bt * .95, "fiddle", dmidi(nroot, top + q.deg), .55f); t += q.beats * Bt; }
        n(XT, 3 * Bt, "fiddle", nroot, .6f); lead_t = std::max(lead_t, XT + 2.5 * Bt);
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
    for (auto it = chords.begin(); it != chords.end();) { if (it->first > rb || it->first <= -1000) it = chords.erase(it); else ++it; }
    if (lead_t > t_cut) lead_t = t_cut + B(rb);
    onsets.erase(std::remove_if(onsets.begin(), onsets.end(), [&](double o) { return o >= t_cut; }), onsets.end());
    bar_t.resize(size_t(rb + 1)); bar_bpm.resize(size_t(rb + 1));
    hush_from = 1e30; build_kind = settle_kind = -1;
    marks.erase(std::remove_if(marks.begin(), marks.end(), [&](const Mark& m) {
      return m.t >= t_cut - 1e-6 && (m.text.rfind("scene:", 0) == 0 || m.text.rfind("lead-in:", 0) == 0); }), marks.end());
    last_rewrite = now; if (why) mark(now, why);
  }
  double next_beat() { int b = bar_at(now + .05); double Bt = B(b), t = T(b); while (t < now + .05) t += Bt; return t; }
  void request(int scene, int urgency, const char* why) {
    if (!started || finished) return;
    int pb = bar_at(now);
    if (cur.b0 > pb) rollback(pb);
    Inst want = make(scene, cur.key);
    nxt = want;
    if (urgency == 0) {
      if (cur.b1 - pb <= 3) rewrite(T(std::max(pb + 1, cur.b1 - 2)), why); else if (why) mark(now, why);
      return;
    }
    if (urgency == 1) {
      int X = pb + 2; if (T(pb + 1) - now < B(pb)) X = pb + 3;
      cur.b1 = std::max(cur.b0 + 1, X);
      rewrite(next_beat(), why);
    } else {
      double hit = next_beat();
      rewrite(hit, why);
      cur.b1 = pb + 1 + (hit - T(pb) > 3 * B(pb) ? 1 : 0);
      n(hit, 1.2, "bigdrum", 33, 1); n(hit, .8, "taiko", LO, 1); if (U() < .6) n(hit, .8, "taiko", MI, .8f);
      Event e; e.t = hit; e.part = "subdrop"; e.vel = float(U(.6, .9)); eng.push(e);
      call("boom", hit, 1.0f, 0, 0, pb);
      int r = cur.key - 12; for (int iv : cur.voicing) n(hit, (1.5 + U()) * BAR(pb), "braam", r + iv, .9f);
    }
  }
  void rollback(int pb) {
    if (have_prev && cur.b0 > pb) { nxt = cur; nxt.b1 = cur.b1 - cur.b0; cur = prev; drum = drum_prev; }
  }
  void tick(double t) {
    now = t; if (!started || finished) return;
    if (notes_dirty && now - last_rewrite > .5) { notes_dirty = false; rewrite(next_beat(), nullptr); }
    while (!finished && T(wbar) < now + 2 * BAR(std::max(0, wbar - 1)) + .05) {
      int before = have_cur ? cur.b0 : -1; Inst saved = cur;
      write_bar(wbar);
      if (have_cur && cur.b0 != before) { prev = saved; have_prev = before >= 0; }
      wbar++; cut = -1;
    }
  }
};

Conductor::Conductor(Engine& e, uint32_t seed) : p(new Impl(e, seed)) {}
Conductor::~Conductor() { delete p; }
void Conductor::start(double t) {
  std::lock_guard<std::recursive_mutex> l(p->m);
  p->bar_t = {t + .1}; p->bar_bpm = {p->bpm_now()}; p->wbar = 0; p->started = true; p->now = t;
  static const int keys[6] = {38, 36, 40, 41, 35, 43};
  p->nxt = p->make(kDawn, keys[p->I(6)]); p->have_cur = false; p->mark(t, "start");
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
    case kInteract: if (p->started && !p->finished) { p->interact_bars = 2 + p->I(3); p->rewrite(p->T(p->bar_at(p->now) + 1), why.c_str()); } break;
    case kDefend: p->request(kFight, 2, why.c_str()); break;
    case kObserve: p->request(kObserveScene, 1, why.c_str()); break;
    case kTrack: p->request(kStalk, 1, why.c_str()); p->nxt.th0 = .1f; p->nxt.th1 = .75f; break;
    default: break;
  }
}
void Conductor::request_scene(int s, int urgency) {
  std::lock_guard<std::recursive_mutex> l(p->m);
  if (s < 0 || s >= kNumScenes) return; p->request(s, urgency, (std::string("encounter: ") + scene_name(s)).c_str());
}
void Conductor::set_trace(std::vector<std::string>* out) { std::lock_guard<std::recursive_mutex> l(p->m); p->trace = out; }
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
    case kGHarmony: { static const char* r[7] = {"i", "ii", "III", "iv", "v", "VI", "VII"}; return I.chord == -6 ? (p->danger_off == 1 ? "bII" : "bV") : r[((I.chord % 7) + 7) % 7]; }
    case kGMusic: { std::string s = scene_name(I.scene); for (auto& c : s) c = char(toupper(c)); return s; }
    default: return "";
  }
}
const std::vector<Conductor::Mark>& Conductor::marks() const { return p->marks; }
}  // namespace paleo
