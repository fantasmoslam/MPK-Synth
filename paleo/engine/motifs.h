/* SPDX-License-Identifier: GPL-3.0-only */
/* Melody for the Scape conductor. Its phrases are whole real phrases from the phrase bank, played as written;
   when none fits, every expedition also grows its own motifs from its seed. Most are cut from real
   melodies (the phrase bank, phrase_bank.h: songs, hymns, folk tunes and chant, 199,000 phrases), the rest are
   drawn fresh, and the conductor builds each phrase by developing them (transposing, inverting, reversing, stretching,
   shrinking, fragmenting, sequencing, ornamenting, extending) into a question and an answer. Motifs live: they
   mutate as the expedition goes on, new ones are born and old ones retire. An exact phrase is never played twice
   within the last sixteen. */
#pragma once
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <deque>
#include <functional>
#include <random>
#include <vector>
#include "phrase_bank.h"

namespace paleo {
namespace motif {

enum Character { kTravel, kMajesty, kDanger, kSorrow, kNumCharacters };
struct Note { int deg; float beats; };            /* scale degree from the phrase's start, length in beats */
typedef std::vector<Note> Line;

struct Motif { std::vector<int> steps; std::vector<float> rhythm; int character = kTravel; int born = 0; };

class Garden {
 public:
  explicit Garden(uint32_t seed) : rng_(seed * 2654435761u + 17) {
    for (int c = 0; c < kNumCharacters; c++) for (int k = 0; k < 2; k++) motifs_.push_back(grow(c));
  }
  /* A phrase for a character, at most max_beats long. end_deg: the degree (from the phrase's start) the answer
     should land on, so the conductor can make it end on a chord tone. */
  Line phrase(int character, float max_beats, int scene_serial, const std::function<double(const Line&)>& fit = nullptr) {
    /* Whole real phrases first: a complete phrase from the bank, as written (degrees from the key's tonic). Most
       often the next phrase of the same tune follows, and now and then an earlier one comes back. */
    last_whole_ = true;
    auto& dv = deja_[character];
    if (!dv.empty() && U() < .18) {
      const Line& l = dv[size_t(I(int(dv.size())))]; float tot = 0; for (auto& n : l) tot += n.beats;
      if (tot <= max_beats) return l;
    }
    /* candidates: the next phrase of the tune first, then others; the one that fits the harmony best wins, and the
       tune's own next phrase is kept unless another fits clearly better */
    Line best; double best_fit = -1e9; size_t best_k = 0; bool best_cont = false;
    for (int attempt = 0; attempt < (fit ? 8 : 1) || (best.empty() && attempt < 16); attempt++) {
      bool cont = has_cont_[character] && attempt == 0; size_t k = 0;
      Line q = whole(character, max_beats, attempt == 0, &k);
      if (q.empty()) continue;
      uint64_t h = hash(q);
      if (std::find(recent_.begin(), recent_.end(), h) != recent_.end()) continue;
      double f = fit ? fit(q) : 0; if (cont) f += .15;
      if (f > best_fit) { best = q; best_fit = f; best_k = k; best_cont = cont; }
    }
    (void)best_cont;
    if (!best.empty()) {
      cont_[character] = best_k; has_cont_[character] = true;
      recent_.push_back(hash(best)); if (recent_.size() > 16) recent_.pop_front();
      dv.push_back(best); if (dv.size() > 8) dv.pop_front();
      return best;
    }
    last_whole_ = false;
    for (int attempt = 0; attempt < 8; attempt++) {
      Line q = build(character, max_beats);
      if (q.empty()) return q;
      uint64_t h = hash(q);
      if (std::find(recent_.begin(), recent_.end(), h) != recent_.end()) continue;
      recent_.push_back(h); if (recent_.size() > 16) recent_.pop_front();
      (void)scene_serial; return q;
    }
    Line q = build(character, max_beats); if (!q.empty()) { q.back().deg += 1; } return q;
  }
  /* True when the last phrase was a whole real phrase (its degrees count from the tonic, not from its start). */
  bool last_whole() const { return last_whole_; }
  /* Called when a scene starts: the motifs of its character drift, and now and then one is born. */
  void season(int character, int serial) {
    std::vector<Motif*> mine; for (auto& m : motifs_) if (m.character == character) mine.push_back(&m);
    if (!mine.empty() && U() < .6) mutate(*mine[size_t(I(int(mine.size())))]);
    if (U() < .3) {
      auto oldest = motifs_.end();
      for (auto it = motifs_.begin(); it != motifs_.end(); ++it)
        if (it->character == character && (oldest == motifs_.end() || it->born < oldest->born)) oldest = it;
      Motif n = grow(character); n.born = serial; if (oldest != motifs_.end() && mine.size() >= 3) *oldest = n; else motifs_.push_back(n);
    }
  }
  /* A short climbing or falling figure for lead-ins, made from a motif's own rhythm. */
  Line pickup(int character, bool rising, int notes) {
    const Motif& m = pick_motif(character); Line out; int d = 0;
    for (int i = 0; i < notes; i++) {
      float b = m.rhythm[size_t(i) % m.rhythm.size()]; b = std::min(b, 1.f); if (b < .25f) b = .25f;
      out.push_back({d, b}); int st = std::abs(m.steps[size_t(i) % m.steps.size()]); st = std::max(1, std::min(st, 2));
      d += rising ? st : -st;
    }
    return out;
  }

 private:
  std::mt19937 rng_; std::vector<Motif> motifs_; std::deque<uint64_t> recent_;
  bool last_whole_ = false; size_t cont_[kNumCharacters] = {}; bool has_cont_[kNumCharacters] = {};
  std::deque<Line> deja_[kNumCharacters];
  /* A whole phrase of this character that fits in max_beats: the next one of the tune before, or a new tune. */
  /* A whole phrase of this character that fits in max_beats: the next one of the tune before (when follow and
     there is one), or one from anywhere in the bank. Its bank position goes to *at_k. */
  Line whole(int c, float max_beats, bool follow, size_t* at_k) {
    const std::vector<uint32_t>& ix = bank_index()[c];
    if (ix.empty()) return {};
    for (int attempt = 0; attempt < 12; attempt++) {
      size_t k = follow && has_cont_[c] && attempt == 0 ? (cont_[c] + 1) % ix.size() : size_t(I(int(ix.size())));
      size_t at = ix[k]; int n = kPhraseBank[at + 1]; Line l; float tot = 0;
      for (int j = 0; j < n; j++) { float b = kBankBeats[kPhraseBank[at + 3 + 2 * size_t(j)]]; l.push_back({int(kPhraseBank[at + 2 + 2 * size_t(j)]) - 16, b}); tot += b; }
      if (tot > max_beats) continue;
      *at_k = k; return l;
    }
    return {};
  }
  double U() { return std::uniform_real_distribution<double>(0, 1)(rng_); }
  int I(int n) { return int(U() * n) % std::max(1, n); }

  /* Where each character's phrases start in the bank (bank kinds: travel, majesty, danger, coda = sorrow). */
  static const std::vector<uint32_t>* bank_index() {
    static const std::vector<std::vector<uint32_t>> idx = [] {
      std::vector<std::vector<uint32_t>> v(kNumCharacters);
      for (size_t i = 0; i + 1 < sizeof kPhraseBank && kPhraseBank[i] != 255; i += 2 + 2 * size_t(kPhraseBank[i + 1]))
        if (kPhraseBank[i] < kNumCharacters) v[kPhraseBank[i]].push_back(uint32_t(i));
      return v;
    }();
    return idx.data();
  }
  /* A motif cut from a real melody of this character: three to seven notes of one bank phrase. */
  bool from_bank(int c, Motif& m) {
    const std::vector<uint32_t>& ix = bank_index()[c];
    if (ix.empty()) return false;
    static const float kMax[kNumCharacters] = {6, 10, 4, 8};
    for (int attempt = 0; attempt < 6; attempt++) {
      size_t at = ix[size_t(I(int(ix.size())))]; int n = kPhraseBank[at + 1];
      if (n < 3) continue;
      int w = 3 + I(std::min(n, 7) - 2), s0 = I(n - w + 1);
      std::vector<int> deg; std::vector<float> rh; float tot = 0;
      for (int j = s0; j < s0 + w; j++) {
        float b = kBankBeats[kPhraseBank[at + 2 + 2 * size_t(j) + 1]];
        if (tot + b > kMax[c] && deg.size() >= 3) break;
        deg.push_back(int(kPhraseBank[at + 2 + 2 * size_t(j)]) - 16); rh.push_back(b); tot += b;
      }
      if (deg.size() < 3) continue;
      m = Motif(); m.character = c; m.rhythm = rh;
      for (size_t j = 1; j < deg.size(); j++) m.steps.push_back(deg[j] - deg[j - 1]);
      return true;
    }
    return false;
  }
  /* A new motif with the character's tendencies: most often cut from a real melody, otherwise drawn fresh. */
  Motif grow(int c) {
    { Motif b; if (U() < .8 && from_bank(c, b)) return b; }
    Motif m; m.character = c;
    /* rhythm: a random partition of the motif's length into note values the character likes */
    static const float kLen[kNumCharacters][2] = {{2, 4}, {4, 8}, {1.5f, 3}, {3, 6}};
    std::vector<std::pair<float, float>> vals;   /* value, weight */
    switch (c) {
      case kTravel: vals = {{.5f, 3}, {1, 4}, {1.5f, 1.5f}, {.25f, .6f}, {2, 1}}; break;
      case kMajesty: vals = {{1, 2}, {2, 4}, {3, 2}, {1.5f, 1}, {4, 1}}; break;
      case kDanger: vals = {{.25f, 3}, {.5f, 4}, {.75f, 1}, {1, 1.5f}}; break;
      default: vals = {{1, 3}, {2, 3}, {1.5f, 1.5f}, {3, 1}, {.5f, 1}}; break;
    }
    float len = float(kLen[c][0] + (kLen[c][1] - kLen[c][0]) * U()); len = std::round(len * 2) / 2; float t = 0;
    while (t < len - 1e-3) {
      float tot = 0; for (auto& v : vals) if (v.first <= len - t + 1e-3) tot += v.second;
      if (tot <= 0) { m.rhythm.push_back(len - t); break; }
      double r = U() * tot; for (auto& v : vals) { if (v.first > len - t + 1e-3) continue; r -= v.second; if (r <= 0) { m.rhythm.push_back(v.first); t += v.first; break; } }
      if (m.rhythm.size() > 9) break;
    }
    if (m.rhythm.size() < 3) { float h = m.rhythm.back() / 2; if (h >= .25f) { m.rhythm.back() = h; m.rhythm.push_back(h); } }
    /* contour: a shape (arch, rise, fall, wave, hover) and a step-size spread the character likes */
    int shape = I(5); int n = int(m.rhythm.size());
    for (int i = 1; i < n; i++) {
      double r = U(); int size;
      if (c == kMajesty) size = r < .25 ? 1 : r < .55 ? 2 : r < .8 ? 3 : r < .93 ? 4 : 7;
      else if (c == kDanger) size = r < .25 ? 0 : r < .75 ? 1 : r < .93 ? 2 : 4;
      else size = r < .1 ? 0 : r < .6 ? 1 : r < .85 ? 2 : r < .95 ? 3 : 5;
      double x = double(i) / (n - 1); int dir;
      switch (shape) {
        case 0: dir = x < .55 ? 1 : -1; break;
        case 1: dir = U() < .8 ? 1 : -1; break;
        case 2: dir = U() < .8 ? -1 : 1; break;
        case 3: dir = (i % 2) ? 1 : -1; break;
        default: dir = U() < .5 ? 1 : -1; break;
      }
      if (c == kSorrow && U() < .6) dir = -1;
      m.steps.push_back(size * dir);
    }
    return m;
  }
  void mutate(Motif& m) {
    double r = U();
    if (r < .4 && !m.steps.empty()) { int i = I(int(m.steps.size())); m.steps[size_t(i)] += U() < .5 ? 1 : -1; }
    else if (r < .7 && m.rhythm.size() > 1) { int i = I(int(m.rhythm.size()) - 1); std::swap(m.rhythm[size_t(i)], m.rhythm[size_t(i) + 1]); }
    else if (r < .85 && m.rhythm.size() < 9) { int i = I(int(m.rhythm.size())); float h = m.rhythm[size_t(i)] / 2; if (h >= .25f) { m.rhythm[size_t(i)] = h; m.rhythm.insert(m.rhythm.begin() + i, h); m.steps.insert(m.steps.begin() + std::min(i, int(m.steps.size())), U() < .5 ? 1 : -1); } }
    else if (m.steps.size() > 2) { int i = I(int(m.steps.size())); m.steps[size_t(i)] = -m.steps[size_t(i)]; }
  }
  const Motif& pick_motif(int c) {
    std::vector<const Motif*> mine; for (auto& m : motifs_) if (m.character == c) mine.push_back(&m);
    if (mine.empty() || U() < .12) return motifs_[size_t(I(int(motifs_.size())))];   /* a stranger now and then */
    return *mine[size_t(I(int(mine.size())))];
  }
  static Line realise(const Motif& m) {
    Line l; int d = 0;
    for (size_t i = 0; i < m.rhythm.size(); i++) { l.push_back({d, m.rhythm[i]}); if (i < m.steps.size()) d += m.steps[i]; }
    return l;
  }
  /* development: each takes a line and returns a changed one */
  Line develop(Line l, int c) {
    int ops = 1 + I(c == kMajesty ? 2 : 3);
    for (int k = 0; k < ops; k++) {
      double r = U(); Line o;
      if (r < .14) { for (auto& n : l) o.push_back({-n.deg, n.beats}); }                                      /* invert */
      else if (r < .24) { int last = l.back().deg; for (auto it = l.rbegin(); it != l.rend(); ++it) o.push_back({it->deg - last, it->beats}); }   /* reverse */
      else if (r < .36 && c != kDanger) { for (auto& n : l) o.push_back({n.deg, n.beats * 2}); }            /* stretch */
      else if (r < .46 && c != kMajesty) { for (auto& n : l) o.push_back({n.deg, std::max(.25f, n.beats / 2)}); }   /* shrink */
      else if (r < .6 && l.size() > 3) { size_t k2 = 2 + size_t(I(int(l.size()) - 2)); bool head = U() < .6;   /* fragment */
        if (head) o.assign(l.begin(), l.begin() + long(k2)); else { int base = l[l.size() - k2].deg; for (size_t i = l.size() - k2; i < l.size(); i++) o.push_back({l[i].deg - base, l[i].beats}); } }
      else if (r < .72 && l.size() <= 5) { int st = U() < .5 ? 1 : -1; if (c == kMajesty) st = U() < .7 ? 1 : 2; int reps = 2 + (U() < .3);   /* sequence */
        for (int j = 0; j < reps; j++) for (auto& n : l) o.push_back({n.deg + st * j, n.beats}); }
      else if (r < .84) { for (size_t i = 0; i < l.size(); i++) { o.push_back(l[i]);                           /* ornament leaps */
          if (i + 1 < l.size() && std::abs(l[i + 1].deg - l[i].deg) >= 3 && l[i].beats >= .5f) { o.back().beats /= 2; o.push_back({(l[i].deg + l[i + 1].deg) / 2, l[i].beats / 2}); } } }
      else { o = l; int d = l.back().deg; int steps = 1 + I(3); for (int j = 0; j < steps; j++) { d += U() < .5 ? 1 : -1; o.push_back({d, l.back().beats >= 1 ? 1.f : .5f}); } }   /* extend */
      if (!o.empty() && o.size() <= 16) l = o;
    }
    return l;
  }
  Line build(int c, float max_beats) {
    const Motif& a = pick_motif(c);
    Line q = develop(realise(a), c);
    Line ans = U() < .55 ? develop(realise(a), c) : develop(realise(pick_motif(c)), c);   /* the answer: same motif or another */
    int off = q.back().deg + (U() < .5 ? 1 : -1) * (1 + I(2)) - ans.front().deg;
    Line out; float t = 0;
    for (auto& n : q) { if (t + n.beats > max_beats) return out; out.push_back(n); t += n.beats; }
    if (U() < .25 || t > max_beats * .6f) return out;                                    /* sometimes only the question */
    if (!out.empty()) { float rest = U() < .5 ? .5f : 0; if (rest > 0) out.back().beats += rest, t += rest; }
    for (auto& n : ans) { if (t + n.beats > max_beats) break; out.push_back({n.deg + off, n.beats}); t += n.beats; }
    /* keep it in a singable range: no more than an octave and a fourth from the start */
    for (auto& n : out) { while (n.deg > 10) n.deg -= 7; while (n.deg < -6) n.deg += 7; }
    return out;
  }
  static uint64_t hash(const Line& l) {
    uint64_t h = 1469598103934665603ull;
    for (auto& n : l) { h = (h ^ uint64_t(n.deg + 64)) * 1099511628211ull; h = (h ^ uint64_t(n.beats * 8)) * 1099511628211ull; }
    return h;
  }
};
}  // namespace motif
}  // namespace paleo
