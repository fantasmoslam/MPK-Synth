/* SPDX-License-Identifier: GPL-3.0-only */
/* Small, allocation-free building blocks for PALEO's channel strips and buses. */
#pragma once
#include <cmath>
#include <vector>
#include <algorithm>

namespace paleo {
static const float kPi = 3.14159265358979f;

/* RBJ biquad, transposed direct form II. */
struct Biquad {
  float b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0, z1 = 0, z2 = 0;
  inline float tick(float x) { float y = b0 * x + z1; z1 = b1 * x - a1 * y + z2; z2 = b2 * x - a2 * y; return y; }
  void norm(float a0) { b0 /= a0; b1 /= a0; b2 /= a0; a1 /= a0; a2 /= a0; }
  void lowpass(float f, float sr, float q = 0.70710678f) {
    float w = 2 * kPi * f / sr, c = cosf(w), al = sinf(w) / (2 * q);
    b0 = (1 - c) / 2; b1 = 1 - c; b2 = (1 - c) / 2; a1 = -2 * c; a2 = 1 - al; norm(1 + al);
  }
  void highpass(float f, float sr, float q = 0.70710678f) {
    float w = 2 * kPi * f / sr, c = cosf(w), al = sinf(w) / (2 * q);
    b0 = (1 + c) / 2; b1 = -(1 + c); b2 = (1 + c) / 2; a1 = -2 * c; a2 = 1 - al; norm(1 + al);
  }
  void peak(float f, float sr, float q, float gain) {
    float A = sqrtf(gain), w = 2 * kPi * f / sr, c = cosf(w), al = sinf(w) / (2 * q);
    b0 = 1 + al * A; b1 = -2 * c; b2 = 1 - al * A; a1 = -2 * c; a2 = 1 - al / A; norm(1 + al / A);
  }
};

/* The song's EQ: low cut and high cut as two cascaded 2nd-order Butterworths each (|H| = 1/(1+(f0/f)^4)),
   plus an optional mud dip and presence boost. */
struct Eq {
  Biquad st[6]; int n = 0;
  void setup(float sr, float lo, float hi, float dipf, float dipd, float bf, float bb) {
    n = 0; for (auto& b : st) b = Biquad();
    if (lo > 0) { st[n++].highpass(lo, sr); st[n++].highpass(lo, sr); }
    if (hi > 0) { st[n++].lowpass(hi, sr); st[n++].lowpass(hi, sr); }
    if (dipf > 0) st[n++].peak(dipf, sr, 1.5f, 1 - dipd);
    if (bf > 0) st[n++].peak(bf, sr, 0.9f, 1 + bb);
  }
  inline float tick(float x) { for (int i = 0; i < n; i++) x = st[i].tick(x); return x; }
};

/* A plain delay line with a fixed maximum. */
struct Delay {
  std::vector<float> b; size_t w = 0;
  void init(size_t n) { b.assign(n + 1, 0.f); w = 0; }
  inline void push(float x) { b[w] = x; if (++w == b.size()) w = 0; }
  inline float at(size_t d) const { size_t i = w + b.size() - 1 - d; if (i >= b.size()) i -= b.size(); return b[i]; }
};

/* The song's ping-pong echo: six taps, each fb quieter, alternating sides; the first tap darkened once,
   the later ones twice. */
struct PingPong {
  Delay l1, r1, l2, r2; Biquad lpL[4], lpR[4]; size_t d = 0; float fb = 0, mix = 0;
  void setup(float sr, float secs, float feedback, float m) {
    d = size_t(secs * sr); fb = feedback; mix = m;
    l1.init(d); r1.init(d); l2.init(6 * d); r2.init(6 * d);
    for (auto* f : {lpL, lpR}) for (int i = 0; i < 4; i++) f[i].lowpass(3500, sr);
  }
  inline void tick(float& L, float& R) {
    float a = lpL[1].tick(lpL[0].tick(L)), b = lpR[1].tick(lpR[0].tick(R));
    float a2 = lpL[3].tick(lpL[2].tick(a)), b2 = lpR[3].tick(lpR[2].tick(b));
    l1.push(a); r1.push(b); l2.push(a2); r2.push(b2);
    float oL = r1.at(d) * mix, oR = l1.at(d) * mix, g = mix * fb;   /* tap 1: swapped */
    for (int k = 2; k <= 6; k++, g *= fb) {
      bool sw = k & 1; float tl = (sw ? r2 : l2).at(k * d), tr = (sw ? l2 : r2).at(k * d);
      oL += tl * g; oR += tr * g;
    }
    L += oL; R += oR;
  }
};

/* The song's peak follower (fast attack, slow release), evaluated on blocks of `blk` samples. */
struct Follower {
  float a = 0, r = 0, v = 0, m = 0; int blk = 48, k = 0;
  void setup(float sr, float att, float rel, int block) { blk = block; a = expf(-blk / sr / att); r = expf(-blk / sr / rel); }
  inline float tick(float x) {
    m = std::max(m, fabsf(x));
    if (++k == blk) { v = m > v ? a * v + (1 - a) * m : r * v + (1 - r) * m; m = 0; k = 0; }
    return v;
  }
};

/* Equal-power pan with the song's sqrt(2) makeup, and the Haas widening (right side delayed). */
struct Haas {
  float gl = 1, gr = 1; Delay dl; size_t d = 0;
  void setup(float sr, float ms, float p) {
    gl = cosf((p + 1) * kPi / 4) * 1.41421356f; gr = sinf((p + 1) * kPi / 4) * 1.41421356f;
    d = size_t(ms * sr / 1000); dl.init(d + 1);
  }
  inline void tick(float x, float& L, float& R) { L = x * gl; dl.push(x * gr); R = d ? dl.at(d) : x * gr; }
};
}  // namespace paleo
