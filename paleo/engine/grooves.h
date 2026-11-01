/* SPDX-License-Identifier: GPL-3.0-only */
/* Drums for the Scape conductor. No fixed patterns: each scene gets a groove built from rules (how many hits each
   drum gets in a cycle, spread evenly and rotated, with its own cycle length, accents and ghost notes), sized by
   the scene's energy and threat. The groove keeps changing: a step moves every few bars, a voice is rebuilt now
   and then, and the end of every four-bar phrase gets its own small variation. Fills come from a family of
   generators and the same kind is never used twice running. */
#pragma once
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <random>
#include <vector>

namespace paleo {
namespace groove {

enum Voice { kLo, kMi, kHi, kLog, kRattle, kBig, kNumVoices };
enum Family { kPulse, kHeart, kSparse, kDrive, kCrowd, kSilent };
enum Fill { kRoll, kStopTime, kToms, kCallResponse, kDrop, kNumFills };
struct Hit { float step; int voice; float vel; int pitch_off; };   /* step in 16ths from the bar start; pitch_off for the log drum */
typedef std::vector<Hit> Bar;

struct Track { int len = 16; std::vector<float> vel; float ghost = 0; bool on = false; };

class Drummer {
 public:
  explicit Drummer(uint32_t seed) : rng_(seed * 40503u + 99) {}
  /* A new groove for a scene. */
  void scene(Family f, float energy, float threat) {
    fam_ = f; e_ = energy; th_ = threat; bar_ = 0;
    for (int v = 0; v < kNumVoices; v++) build(v);
  }
  /* The next bar. energy and threat may have moved; density 0..1 thins or thickens. */
  Bar bar(float energy, float threat, float density) {
    e_ = energy; th_ = threat; Bar out;
    if (fam_ == kSilent) { bar_++; return out; }
    if (bar_ > 0 && bar_ % 8 == 0 && U() < .7) build(I(kNumVoices));          /* a voice is rebuilt */
    else if (bar_ > 0 && U() < (fam_ == kHeart ? .8 : .45)) { nudge(); if (fam_ == kHeart && U() < .4) nudge(); }   /* a step moves */
    for (int v = 0; v < kNumVoices; v++) {
      Track& T = tr_[v]; if (!T.on) continue;
      for (int s = 0; s < 16; s++) {
        int c = (pos_[v] + s) % T.len; float vel = T.vel[size_t(c)];
        bool hit = vel > 0 && U() < (.75 + .25 * density) * (fam_ == kSparse ? .55 + .45 * th_ : fam_ == kHeart ? (s == 0 ? 1 : .72) : 1);
        if (!hit && T.ghost > 0 && U() < T.ghost * density) { hit = true; vel = .18f + .1f * float(U()); }
        if (hit) out.push_back({float(s), v, std::min(1.f, vel * (.55f + .45f * e_)), log_pitch()});
      }
      pos_[v] = (pos_[v] + 16) % T.len;
    }
    if (bar_ % 4 == 3 && U() < .5) {                                              /* the end of a phrase turns */
      Bar f = fill(kNumFills, 1 + I(2) * .5f, false); float from = 16 - f_len_ * 4;
      out.erase(std::remove_if(out.begin(), out.end(), [&](const Hit& h) { return h.step >= from; }), out.end());
      for (auto& h : f) out.push_back(h);
    }
    bar_++; return out;
  }
  /* A fill of `beats` beats ending at the bar line. kind = kNumFills: choose (never the last kind used). rising: into
     something bigger. */
  Bar fill(int kind, float beats, bool rising) {
    if (kind == kNumFills) { do kind = I(kNumFills); while (kind == last_fill_ && kNumFills > 1); }
    if (!rising && kind == kDrop && U() < .5) kind = kToms;
    last_fill_ = kind; f_len_ = beats;
    Bar out; float from = 16 - beats * 4; int n = int(beats * 4);
    switch (kind) {
      case kRoll: {     /* density and level both climb; which drums carry it is chosen fresh */
        int a = I(3), b = I(3); float sub = U() < .5 ? 1 : .5f;
        for (float s = from; s < 16 - 1e-3f; s += sub) { float x = (s - from) / (16 - from); int v = x < .5f ? a : b;
          if (U() < .35 + .6 * x) out.push_back({s, v, .3f + .6f * x * (rising ? 1 : .6f), 0}); }
        break; }
      case kStopTime: { /* everything stops, then a few hard hits */
        int hits = 2 + I(3); for (int k = 0; k < hits; k++) { float s = from + float(I(n)); out.push_back({s, U() < .5 ? kLo : kBig, .75f + .25f * float(U()), 0}); }
        break; }
      case kToms: {     /* falling (or climbing) through the drums in a fresh rhythm */
        static const int order[4] = {kHi, kMi, kLog, kLo}; float s = from; int i = rising ? 3 : 0;
        while (s < 16 - 1e-3f) { out.push_back({s, order[i], .5f + .4f * (s - from) / (16 - from), rising ? 12 - 3 * i : -2 * i});
          s += U() < .6 ? 1 : U() < .5 ? 2 : .5f; i = rising ? std::max(0, i - (U() < .5)) : std::min(3, i + (U() < .5)); }
        break; }
      case kCallResponse: {   /* log drum asks, taiko answers */
        float s = from; bool call = true; while (s < 16 - 1e-3f) { int len = 1 + I(3);
          for (int k = 0; k < len && s < 16; k++, s += 1) out.push_back({s, call ? kLog : kMi, call ? .45f : .6f, call ? I(3) * 5 : 0});
          call = !call; s += U() < .5 ? 0 : 1; }
        break; }
      case kDrop: {     /* silence, with the big drum pulsing up to the line */
        int pulses = 1 + I(4); for (int k = 0; k < pulses; k++) out.push_back({16 - float(pulses - k) * (beats * 4 / pulses), kBig, .4f + .5f * float(k + 1) / pulses, 0});
        break; }
    }
    return out;
  }
  int last_fill() const { return last_fill_; }

 private:
  std::mt19937 rng_; Family fam_ = kPulse; float e_ = .5f, th_ = 0; int bar_ = 0; int last_fill_ = -1; float f_len_ = 1;
  Track tr_[kNumVoices]; int pos_[kNumVoices] = {};
  double U() { return std::uniform_real_distribution<double>(0, 1)(rng_); }
  int I(int n) { return int(U() * n) % std::max(1, n); }
  int log_pitch() { static const int o[5] = {0, 7, 12, 5, 10}; return o[I(fam_ == kSparse ? 3 : 5)]; }
  /* hits evenly spread over a cycle (the Euclidean rule), rotated */
  static std::vector<int> spread(int k, int n, int rot) {
    std::vector<int> p(size_t(n), 0); if (k <= 0) return p; k = std::min(k, n);
    for (int i = 0; i < k; i++) p[size_t((i * n / k + rot) % n)] = 1;
    return p;
  }
  void build(int v) {
    Track& T = tr_[v]; T = Track(); pos_[v] = 0;
    float e = e_, th = th_;
    /* per family: does the voice play, and how many hits per cycle (a range that grows with energy) */
    int lo = 0, hi = 0; float on = 0, ghost = 0;
    switch (fam_) {
      case kPulse: { static const int r[6][2] = {{2, 4}, {1, 4}, {0, 3}, {3, 7}, {4, 8}, {0, 1}}; lo = r[v][0]; hi = r[v][1]; on = v == kLo ? 1 : v == kBig ? .3f : .5f + .5f * e; ghost = v == kMi || v == kLog ? .08f : 0; break; }
      case kHeart: { static const int r[6][2] = {{2, 3}, {0, 2}, {0, 1}, {0, 2}, {0, 2}, {1, 2}}; lo = r[v][0]; hi = r[v][1]; on = v == kLo || v == kBig ? 1 : .3f; break; }
      case kSparse: { static const int r[6][2] = {{1, 2}, {0, 2}, {0, 2}, {2, 6}, {0, 3}, {0, 1}}; lo = r[v][0]; hi = r[v][1]; on = v == kLog ? 1 : .35f + .5f * th; ghost = v == kLog ? .1f + .2f * th : 0; break; }
      case kDrive: { static const int r[6][2] = {{4, 6}, {4, 8}, {5, 10}, {6, 12}, {8, 16}, {1, 3}}; lo = r[v][0]; hi = r[v][1]; on = v == kBig ? .9f : 1; ghost = v == kHi ? .12f : 0; break; }
      case kCrowd: { static const int r[6][2] = {{3, 6}, {3, 7}, {3, 8}, {3, 7}, {4, 10}, {1, 3}}; lo = r[v][0]; hi = r[v][1]; on = .85f; ghost = .05f; break; }
      case kSilent: return;
    }
    if (U() > on) return;
    T.on = true;
    /* cycle length: 16, except the crowd, where every drum keeps its own count and they drift against each other */
    if (fam_ == kCrowd) { static const int L[6] = {10, 12, 14, 16, 18, 20}; T.len = L[I(6)]; }
    int k = lo + int(std::round((hi - lo) * std::min(1.0, e * .7 + .3 * U()))); if (v == kLo && fam_ != kSparse) k = std::max(k, 1);
    std::vector<int> p = spread(k, T.len, I(T.len));
    if (v == kLo && fam_ != kSparse && fam_ != kCrowd) { std::rotate(p.begin(), std::find(p.begin(), p.end(), 1), p.end()); }   /* the low drum owns the downbeat */
    T.vel.assign(size_t(T.len), 0.f);
    for (int s = 0; s < T.len; s++) if (p[size_t(s)]) {
      bool strong = s % 8 == 0, mid = s % 4 == 0; float base = strong ? .95f : mid ? .8f : .62f;
      T.vel[size_t(s)] = std::min(1.f, base * float(.85 + .3 * U()));
    }
    T.ghost = ghost;
  }
  void nudge() {
    int on[kNumVoices], non = 0; for (int i = 0; i < kNumVoices; i++) if (tr_[i].on) on[non++] = i;
    if (!non) return; int v = on[I(non)]; Track& T = tr_[v];
    std::vector<int> hits; for (int s = 0; s < T.len; s++) if (T.vel[size_t(s)] > 0) hits.push_back(s);
    if (hits.empty()) return; int s = hits[size_t(I(int(hits.size())))];
    if (v == kLo && s == 0) return;
    double r = U();
    if (r < .5) { int to = (s + (U() < .5 ? 1 : T.len - 1)) % T.len; if (T.vel[size_t(to)] == 0) { std::swap(T.vel[size_t(s)], T.vel[size_t(to)]); } }
    else if (r < .75) { int to = I(T.len); if (T.vel[size_t(to)] == 0) T.vel[size_t(to)] = .5f + .3f * float(U()); }
    else if (hits.size() > 1) T.vel[size_t(s)] = 0;
  }
};
}  // namespace groove
}  // namespace paleo
