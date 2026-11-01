/* SPDX-License-Identifier: GPL-3.0-only */
/* PALEO speed test (Step 0): times every instrument and a full STAMPEDE-sized moment on this device.
   Run: adb push paleo-bench /data/local/tmp/ && adb shell /data/local/tmp/paleo-bench (or paleo-bench.exe on Windows) */
#include "faust_min.h"
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>
#include <thread>
#ifdef _WIN32
#include <windows.h>
#else
#include <sched.h>
#endif
#ifdef __ANDROID__
#include <sys/system_properties.h>
#endif
#if defined(__x86_64__) || defined(__i386__) || defined(_M_X64)
#include <xmmintrin.h>
#endif
#define DECL(n) dsp* make_##n();
DECL(taiko) DECL(bigdrum) DECL(logdrum) DECL(rattle) DECL(stone) DECL(mouthbow) DECL(horn) DECL(pipe)
DECL(fiddle) DECL(choir) DECL(hall) DECL(echo)

static const int SR = 48000, BLOCK = 192;   /* 4 ms, a typical tablet burst */
struct Params : UI {
  std::vector<std::pair<std::string, float*>> z;
  void addButton(const char* l, float* p) override { z.push_back({l, p}); }
  void addNumEntry(const char* l, float* p, float, float, float, float) override { z.push_back({l, p}); }
  void addHorizontalSlider(const char* l, float* p, float, float, float, float) override { z.push_back({l, p}); }
  void addVerticalSlider(const char* l, float* p, float, float, float, float) override { z.push_back({l, p}); }
  void set(const char* l, float v) { for (auto& e : z) if (e.first == l) *e.second = v; }
};
struct Voice { dsp* d; Params p; int held; int age; };
struct Inst { const char* name; dsp* (*make)(); int held; float freq; };
static const Inst INSTS[] = {
  {"taiko", make_taiko, 0, 46}, {"bigdrum", make_bigdrum, 0, 55}, {"logdrum", make_logdrum, 0, 147},
  {"rattle", make_rattle, 0, 200}, {"stone", make_stone, 0, 587}, {"mouthbow", make_mouthbow, 0, 147},
  {"horn", make_horn, 1, 147}, {"pipe", make_pipe, 1, 73}, {"fiddle", make_fiddle, 1, 147},
  {"choir", make_choir, 1, 147}};
static Voice* voice(const Inst& in, int k) {
  Voice* v = new Voice; v->d = in.make(); v->d->init(SR); v->d->buildUserInterface(&v->p);
  v->p.set("freq", in.freq * powf(2.f, (k % 12) / 12.f)); v->p.set("gain", 0.8f); v->p.set("p1", 2.0f);
  v->p.set("p2", 4.5f + 0.3f * (k % 5)); v->held = in.held; v->age = (k * 37) % 250; return v;
}
/* Struck voices are hit every second; held voices hold 2 s and rest 0.25 s, like the leads in the song. */
static void tick(Voice* v) {
  int period = v->held ? 562 : 250, on = v->held ? 500 : 2;   /* in blocks of 4 ms */
  v->p.set("gate", (v->age++ % period) < on ? 1.f : 0.f);
}
static double now() { return std::chrono::duration<double>(std::chrono::steady_clock::now().time_since_epoch()).count(); }
static float sink;
/* Runs the voices (and effects) for secs of audio; returns the share of one core used, in %. */
static double run(std::vector<Voice*>& vs, std::vector<dsp*>& fx, double secs) {
  static float mono[BLOCK], bus[2][BLOCK], out[2][BLOCK];
  float* m[1] = {mono}; float* b[2] = {bus[0], bus[1]}; float* o[2] = {out[0], out[1]};
  int blocks = int(secs * SR / BLOCK); double t0 = now();
  for (int i = 0; i < blocks; i++) {
    memset(bus, 0, sizeof bus);
    for (auto* v : vs) { tick(v); v->d->compute(BLOCK, nullptr, m); for (int k = 0; k < BLOCK; k++) { bus[0][k] += mono[k]; bus[1][k] += mono[k]; } }
    for (auto* f : fx) { f->compute(BLOCK, b, o); for (int k = 0; k < BLOCK; k++) { bus[0][k] += out[0][k] * .3f; bus[1][k] += out[1][k] * .3f; } }
    sink += bus[0][BLOCK - 1];
  }
  return 100.0 * (now() - t0) / secs;
}
/* A plain sample player: what the drums and creatures cost once rendered ahead. */
static double sampler(int voices, double secs) {
  static std::vector<float> data(SR * 4); for (size_t i = 0; i < data.size(); i++) data[i] = sinf(i * .01f);
  std::vector<double> pos(voices), step(voices); for (int v = 0; v < voices; v++) step[v] = 0.7 + 0.01 * v;
  float out[2][BLOCK]; int blocks = int(secs * SR / BLOCK); double t0 = now();
  for (int i = 0; i < blocks; i++) {
    memset(out, 0, sizeof out);
    for (int v = 0; v < voices; v++) for (int k = 0; k < BLOCK; k++) {
      double p = pos[v]; size_t a = size_t(p); float f = float(p - a), s = data[a] + (data[a + 1] - data[a]) * f;
      out[0][k] += s * .5f; out[1][k] += s * .4f; p += step[v]; if (p >= data.size() - 2) p = 0; pos[v] = p; }
    sink += out[0][BLOCK - 1];
  }
  return 100.0 * (now() - t0) / secs;
}
#ifdef _WIN32
static void pin(int cpu) { SetThreadAffinityMask(GetCurrentThread(), DWORD_PTR(1) << cpu); }
#else
static void pin(int cpu) { cpu_set_t s; CPU_ZERO(&s); CPU_SET(cpu, &s); sched_setaffinity(0, sizeof s, &s); }
#endif
static void freeall(std::vector<Voice*>& vs) { for (auto* v : vs) { delete v->d; delete v; } vs.clear(); }

int main() {
#if defined(__x86_64__) || defined(__i386__) || defined(_M_X64)
  _mm_setcsr(_mm_getcsr() | 0x8040);
#elif defined(__aarch64__)
  { uint64_t f; asm volatile("mrs %0, fpcr" : "=r"(f)); asm volatile("msr fpcr, %0" :: "r"(f | (1 << 24))); }
#endif
  setvbuf(stdout, nullptr, _IONBF, 0);
  char model[96] = "unknown", soc[96] = "unknown";
#ifdef _WIN32
  strcpy(model, "Windows PC");
#endif
#ifdef __ANDROID__
  __system_property_get("ro.product.model", model); __system_property_get("ro.soc.model", soc);
#endif
  int ncpu = int(std::thread::hardware_concurrency()); if (ncpu < 1) ncpu = 1; if (ncpu > 64) ncpu = 64;
  printf("PALEO speed test 1\ndevice %s, chip %s, %d cores, %s, block %d frames at %d Hz\n\n", model, soc, ncpu,
         sizeof(void*) == 8 ? "64-bit" : "32-bit", BLOCK, SR);
  /* 1. Find the fastest core with the same small job on each. */
  int best = 0; double bestc = 1e9;
  printf("Core speed (lower is faster):\n");
  for (int c = 0; c < ncpu; c++) {
    pin(c); std::vector<Voice*> vs; std::vector<dsp*> fx;
    for (int k = 0; k < 4; k++) vs.push_back(voice(INSTS[8], k));
    run(vs, fx, 0.5); double t = run(vs, fx, 3.0); freeall(vs);
    printf("  core %d: %.1f%%\n", c, t); if (t < bestc) { bestc = t; best = c; }
  }
  pin(best); printf("Using core %d from here on.\n\n", best);
  /* 2. Each instrument: four voices, 8 s, cost per voice. */
  printf("One voice of each instrument (%% of one core):\n");
  for (auto& in : INSTS) {
    std::vector<Voice*> vs; std::vector<dsp*> fx; for (int k = 0; k < 4; k++) vs.push_back(voice(in, k));
    run(vs, fx, 0.5); double t = run(vs, fx, 8.0) / 4; freeall(vs);
    printf("  %-9s %6.2f%%\n", in.name, t);
  }
  { std::vector<Voice*> vs; std::vector<dsp*> fx = {make_hall()}; fx[0]->init(SR);
    printf("  %-9s %6.2f%%  (stereo hall reverb, one bus)\n", "hall", run(vs, fx, 8.0)); delete fx[0]; }
  { std::vector<Voice*> vs; std::vector<dsp*> fx = {make_echo()}; fx[0]->init(SR);
    printf("  %-9s %6.2f%%  (stereo ping-pong echo)\n", "echo", run(vs, fx, 8.0)); delete fx[0]; }
  printf("  %-9s %6.2f%%  (64 sample voices: drums and creatures rendered ahead)\n\n", "samples", sampler(64, 8.0));
  /* 3. The busiest moment of STAMPEDE, two ways. */
  struct Mix { const char* name; int n[10]; };
  const Mix mixes[] = {
    {"A. everything live, as in the song", {33, 0, 17, 17, 6, 9, 18, 2, 45, 60}},
    {"B. the plan: drums rendered ahead, leads live (one voice per note)", {0, 0, 0, 0, 0, 9, 18, 2, 15, 15}}};
  printf("The busiest moment of STAMPEDE (%% of one core, 100%% = too slow):\n");
  for (auto& mx : mixes) {
    std::vector<Voice*> vs; std::vector<dsp*> fx = {make_hall(), make_echo(), make_echo(), make_echo()};
    for (auto* f : fx) f->init(SR);
    for (int i = 0; i < 10; i++) for (int k = 0; k < mx.n[i]; k++) vs.push_back(voice(INSTS[i], k));
    run(vs, fx, 0.5); double t = run(vs, fx, 6.0); if (mx.n[0] == 0) t += sampler(64, 6.0);
    printf("  %s: %.0f%% with %zu live voices\n", mx.name, t, vs.size()); freeall(vs); for (auto* f : fx) delete f;
  }
  printf("\nDone. Please send everything above.\n");
  return sink == 12345.f;
}
