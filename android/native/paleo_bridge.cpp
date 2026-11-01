/* SPDX-License-Identifier: GPL-3.0-only */
/* PALEO and the live Scape conductor inside the tablet app. See paleo_bridge.h. */
#define MINIAUDIO_IMPLEMENTATION
#define MA_NO_DEVICE_IO
#define MA_NO_ENCODING
#define MA_NO_GENERATION
#define MA_NO_THREADING
#include "../../paleo/third_party/miniaudio.h"
#include "../../paleo/engine/paleo.h"
#include "../../paleo/engine/conductor.h"
#include "../../paleo/engine/sampler.h"
#include "paleo_bridge.h"
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <memory>
#include <mutex>
#include <random>
#include <sstream>
#include <string>
#include <thread>
#include <utility>
#include <vector>
#include <sys/resource.h>
#include <unistd.h>
#ifdef __ANDROID__
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <jni.h>
#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "PaleotonePaleo", __VA_ARGS__)
#else
#define LOG(...) (fprintf(stderr, __VA_ARGS__), fputc('\n', stderr))
#endif

namespace {
using paleo::Conductor;
using paleo::Engine;

/* PALEO renders ahead of the audio callback on its own thread (with helpers on other cores), kChunk frames at a
   time, keeping about kAheadSecs in a ring the callback copies from. The callback never waits for PALEO. */
constexpr int kChunk = 256;
constexpr unsigned kRing = 16384;            /* frames; a power of two */
constexpr double kAheadSecs = .085;
constexpr int kThreads = 4;                  /* the render thread and three helpers */

void raise_priority() {
#ifdef __ANDROID__
  setpriority(PRIO_PROCESS, gettid(), -16);  /* Android's audio priority; ignored if refused */
#endif
}
double now_s() { return std::chrono::duration<double>(std::chrono::steady_clock::now().time_since_epoch()).count(); }

struct Bridge {
  /* ---- set from the UI thread */
  std::mutex m;                                   /* guards everything below except the atomics */
  std::string dir; void* assets = nullptr;
  bool want_music = false, want_halt = false; int want_serial = 0, done_serial = 0;
  int notes[paleo::kNumNotes];
  std::vector<std::pair<int, int>> requests;      /* scene, urgency; or -1 - action */
  std::thread worker, renderer; bool quit = false; std::atomic<bool> quit_render{false};

  /* ---- owned by the worker */
  std::vector<std::pair<std::string, std::vector<float>>> library; bool library_loaded = false;
  std::unique_ptr<Conductor> conductor; uint32_t seed = 0;

  /* ---- shared with the render thread and the audio callback */
  std::atomic<unsigned> want_rate{0}, ahead{4096};
  std::atomic<Engine*> live{nullptr};             /* the engine the render thread plays, or null */
  std::atomic<int> in_render{0};
  std::atomic<int> state{0};
  std::atomic<float> load{0};                     /* the last chunk's render time over its length */
  float ring[kRing * 2]; std::atomic<unsigned> rd{0}, wr{0};
  std::atomic<unsigned> mixes{0}, short_mixes{0}, load_max_q{0}, load_sum_q{0}, load_n{0};

  /* ---- read by the UI, written by the worker under m */
  float gauges[paleo::kNumGauges]; std::string texts[paleo::kNumGauges]; int scene = -1;
  std::vector<std::string> recent;

  Bridge() { for (int& n : notes) n = 50; for (float& g : gauges) g = -1; }
  ~Bridge() { { std::lock_guard<std::mutex> l(m); quit = true; } if (worker.joinable()) worker.join();
              quit_render = true; if (renderer.joinable()) renderer.join(); }
};
Bridge& B() { static Bridge b; return b; }

/* ---- the creature library ---------------------------------------------------------------- */
bool decode(const void* data, size_t size, std::vector<float>& pcm) {
  ma_decoder_config cfg = ma_decoder_config_init(ma_format_f32, 1, 48000); ma_decoder d;
  if (ma_decoder_init_memory(data, size, &cfg, &d) != MA_SUCCESS) return false;
  float buf[4096]; ma_uint64 got = 0;
  while (ma_decoder_read_pcm_frames(&d, buf, 4096, &got) == MA_SUCCESS && got > 0) pcm.insert(pcm.end(), buf, buf + got);
  ma_decoder_uninit(&d); return !pcm.empty();
}
bool read_file(const std::string& path, std::vector<char>& out) {
#ifdef __ANDROID__
  Bridge& b = B();
  if (b.assets) {
    AAsset* a = AAssetManager_open((AAssetManager*)b.assets, path.c_str(), AASSET_MODE_BUFFER); if (!a) return false;
    const void* p = AAsset_getBuffer(a); off_t n = AAsset_getLength(a);
    if (p) out.assign((const char*)p, (const char*)p + n);
    AAsset_close(a); return p != nullptr;
  }
#endif
  FILE* f = fopen(path.c_str(), "rb"); if (!f) return false;
  fseek(f, 0, SEEK_END); long n = ftell(f); fseek(f, 0, SEEK_SET);
  out.resize(size_t(n > 0 ? n : 0)); bool ok = n > 0 && fread(out.data(), 1, size_t(n), f) == size_t(n); fclose(f); return ok;
}
void load_library(Bridge& b, const std::string& dir) {
  std::vector<char> text; std::string base = dir.empty() ? std::string("creatures") : dir;
  if (!read_file(base + "/manifest.txt", text)) { LOG("no creature library at %s", base.c_str()); return; }
  std::istringstream m(std::string(text.begin(), text.end())); std::string line; size_t frames = 0;
  while (std::getline(m, line)) {
    if (line.empty() || line[0] == '#') continue;
    std::istringstream ss(line); std::string kind, file; if (!(ss >> kind >> file)) continue;
    std::vector<char> raw; std::vector<float> pcm;
    if (!read_file(base + "/" + file, raw) || !decode(raw.data(), raw.size(), pcm)) { LOG("can't read %s", file.c_str()); continue; }
    frames += pcm.size(); b.library.emplace_back(kind, std::move(pcm));
  }
  LOG("creature library: %d calls, %.1f MB", int(b.library.size()), frames * 4 / 1e6);
}

/* The recorded instruments (assets/samples: manifest.txt plus FLAC, from paleo/samples). They live in a library
   shared by every engine, so they are read once per process and stay as 16-bit (about 19 MB). */
void load_samples(const std::string& creature_dir) {
  static bool done = false; if (done) return; done = true;
  /* in the APK beside the creatures; on a desktop paleo/samples/lib beside paleo/creatures/lib */
  std::string base = creature_dir.empty() || creature_dir == "creatures" ? std::string("samples") : creature_dir + "/../../samples/lib";
  std::vector<char> text;
  if (!read_file(base + "/manifest.txt", text)) { LOG("no recorded instruments"); return; }
  std::istringstream m(std::string(text.begin(), text.end())); std::string line; int zones = 0;
  while (std::getline(m, line)) {
    if (line.empty() || line[0] == '#') continue;
    std::istringstream ss(line); std::string kind, name; ss >> kind >> name;
    if (kind == "inst") { float a = .01f, r = .3f; ss >> a >> r; paleo::add_sample_instrument(name, a, r); continue; }
    if (kind != "zone") continue;
    float root; int ls, le; std::string file; if (!(ss >> root >> ls >> le >> file)) continue;
    std::vector<char> raw; if (!read_file(base + "/" + file, raw)) { LOG("can't read %s", file.c_str()); continue; }
    ma_decoder_config cfg = ma_decoder_config_init(ma_format_f32, 1, 0); ma_decoder d;
    if (ma_decoder_init_memory(raw.data(), raw.size(), &cfg, &d) != MA_SUCCESS) { LOG("can't decode %s", file.c_str()); continue; }
    std::vector<float> pcm; float buf[4096]; ma_uint64 got = 0;
    while (ma_decoder_read_pcm_frames(&d, buf, 4096, &got) == MA_SUCCESS && got > 0) pcm.insert(pcm.end(), buf, buf + got);
    int rate = int(d.outputSampleRate); ma_decoder_uninit(&d);
    paleo::add_sample_zone(name, root, ls, le, pcm, rate); zones++;
  }
  LOG("recorded instruments: %d notes", zones);
}

/* ---- the worker: builds the engine, runs the conductor ---------------------------------- */
void retire_engine(Bridge& b) {
  Engine* e = b.live.exchange(nullptr);
  while (b.in_render.load()) std::this_thread::sleep_for(std::chrono::milliseconds(1));
  b.conductor.reset(); delete e;
}
Engine* build_engine(Bridge& b, unsigned rate) {
  paleo::Options o; o.sample_rate = int(rate); o.lead = paleo::Lead::all; o.ostinato = paleo::Ostinato::mouthbow;
  o.threads = kThreads; o.on_worker = raise_priority;
  b.ahead.store(std::min(kRing - 2 * kChunk, unsigned(kAheadSecs * rate / kChunk + 1) * kChunk));
  Engine* e = new Engine(o);
  /* The calls move into the engine (about 43 MB as floats); a later rebuild reads the library again. */
  std::string dir; { std::lock_guard<std::mutex> l(b.m); dir = b.dir; }
  if (b.library.empty()) load_library(b, dir);
  load_samples(dir);
  for (auto& c : b.library) e->add_creature(c.first, std::move(c.second));
  b.library.clear();
  return e;
}
void publish(Bridge& b) {   /* under b.m */
  if (!b.conductor) { for (float& g : b.gauges) g = -1; for (auto& t : b.texts) t.clear(); b.scene = -1; return; }
  float g[paleo::kNumGauges]; b.conductor->gauges(g);
  for (int i = 0; i < paleo::kNumGauges; i++) { b.gauges[i] = std::isnan(g[i]) ? -1 : g[i]; b.texts[i] = b.conductor->gauge_text(i); }
  b.scene = b.conductor->scene();
  const auto& mk = b.conductor->marks(); b.recent.clear();
  for (size_t i = mk.size() > 8 ? mk.size() - 8 : 0; i < mk.size(); i++) b.recent.push_back(mk[i].text);
}
/* The render thread: keeps the ring about b.ahead frames ahead of the callback. */
void render_loop(Bridge& b) {
  raise_priority(); float L[kChunk], R[kChunk];
  while (!b.quit_render.load()) {
    b.in_render.store(1);
    Engine* e = b.live.load();
    unsigned w = b.wr.load(std::memory_order_relaxed), r = b.rd.load(std::memory_order_acquire);
    if (!e || w - r + kChunk > b.ahead.load()) {
      b.in_render.store(0); std::this_thread::sleep_for(std::chrono::microseconds(e ? 1000 : 5000)); continue;
    }
    double t0 = now_s(); e->render(L, R, kChunk); double used = (now_s() - t0) * b.want_rate.load() / kChunk;
    b.in_render.store(0);
    for (int i = 0; i < kChunk; i++) { unsigned k = (w + unsigned(i)) & (kRing - 1); b.ring[2 * k] = L[i]; b.ring[2 * k + 1] = R[i]; }
    b.wr.store(w + kChunk, std::memory_order_release);
    b.load.store(float(used)); unsigned q = unsigned(used * 1000);
    b.load_sum_q.fetch_add(q); b.load_n.fetch_add(1); if (q > b.load_max_q.load()) b.load_max_q.store(q);
  }
}
/* Every 10 s while the stream runs: adb logcat -s PaleotonePaleo */
void report(Bridge& b, double& last, unsigned& last_mixes) {
  double now = now_s(); if (now - last < 10) return; last = now;
  unsigned m = b.mixes.load(); bool streaming = m != last_mixes; last_mixes = m;
  unsigned n = b.load_n.exchange(0), sum = b.load_sum_q.exchange(0), mx = b.load_max_q.exchange(0), sh = b.short_mixes.exchange(0);
  if (!streaming) return;
  LOG("state %d | render %.0f%% avg, %.0f%% max of real time (%d threads) | callbacks short of PALEO audio: %u | ahead %u frames",
      b.state.load(), n ? sum / 10.0 / n : 0.0, mx / 10.0, kThreads, sh, b.ahead.load());
}
void run(Bridge& b) {
  { std::string dir; { std::lock_guard<std::mutex> l(b.m); dir = b.dir; } load_library(b, dir); b.library_loaded = true; }
  /* decoded before the stream exists, so pressing Play needs no wait */
  unsigned rate = 0; std::mt19937 seeds(uint32_t(std::chrono::steady_clock::now().time_since_epoch().count()));
  b.renderer = std::thread([&b] { render_loop(b); });
  double last_report = now_s(); unsigned last_mixes = 0;
  for (;;) {
    report(b, last_report, last_mixes);
    unsigned want = b.want_rate.load();
    if (want && want != rate) { retire_engine(b); b.live.store(build_engine(b, want)); rate = want; b.state = 0; LOG("engine ready at %u Hz", want); }
    Engine* e = b.live.load();
    {
      std::lock_guard<std::mutex> l(b.m);
      if (b.quit) break;
      double now = e ? e->seconds() : 0;
      if (e && b.want_halt) { if (b.conductor) b.conductor->halt(now); b.want_halt = false; }
      if (e && b.want_music && b.want_serial != b.done_serial) {          /* a new expedition */
        if (b.conductor) b.conductor->halt(now);
        b.seed = seeds() % 1000000u + 1; b.conductor.reset(new Conductor(*e, b.seed));
        for (int k = 0; k < paleo::kNumNotes; k++) b.conductor->note(k, b.notes[k]);
        b.conductor->start(now); b.done_serial = b.want_serial; LOG("expedition %u begins", b.seed);
      }
      if (e && !b.want_music && b.conductor && b.conductor->running()) b.conductor->stop();
      if (b.conductor && b.conductor->running())
        for (auto& r : b.requests) { if (r.first < 0) b.conductor->action(-1 - r.first); else b.conductor->request_scene(r.first, r.second); }
      b.requests.clear();
      if (b.conductor) b.conductor->tick(now);
      bool on = b.conductor && b.conductor->running();
      b.state = !b.library_loaded || (b.want_music && !e) ? 1 : !on ? 0 : b.want_music ? 2 : 3;
      if (!on) b.want_music = false;
      publish(b);
    }
    std::this_thread::sleep_for(std::chrono::milliseconds(10));
  }
  retire_engine(b);
}
void ensure_worker() {   /* under b.m */
  Bridge& b = B(); if (!b.worker.joinable()) b.worker = std::thread([&b] { run(b); });
}
}  // namespace

extern "C" {
void paleo_library_dir(const char* dir) { Bridge& b = B(); std::lock_guard<std::mutex> l(b.m); if (!b.worker.joinable()) { b.dir = dir ? dir : ""; ensure_worker(); } }
void paleo_library_assets(void* am) { Bridge& b = B(); std::lock_guard<std::mutex> l(b.m); if (!b.worker.joinable()) { b.assets = am; b.dir = "creatures"; ensure_worker(); } }
void paleo_prepare(unsigned rate) { B().want_rate.store(rate); }

void paleo_mix(float* out, unsigned frames, float gain) {
  Bridge& b = B(); b.mixes.fetch_add(1, std::memory_order_relaxed);
  unsigned r = b.rd.load(std::memory_order_relaxed), w = b.wr.load(std::memory_order_acquire);
  unsigned n = std::min(frames, w - r);
  for (unsigned i = 0; i < n; i++) { unsigned k = (r + i) & (kRing - 1); out[2 * i] += b.ring[2 * k] * gain; out[2 * i + 1] += b.ring[2 * k + 1] * gain; }
  b.rd.store(r + n, std::memory_order_release);
  if (n < frames && b.live.load(std::memory_order_relaxed)) b.short_mixes.fetch_add(1, std::memory_order_relaxed);
}

void paleo_music(int on) {
  Bridge& b = B(); std::lock_guard<std::mutex> l(b.m);
  if (on && (!b.want_music || !b.conductor || !b.conductor->running())) { b.want_music = true; b.want_serial++; b.state = 1; }
  if (!on) b.want_music = false;
}
void paleo_halt(void) { Bridge& b = B(); std::lock_guard<std::mutex> l(b.m); b.want_music = false; b.want_halt = true; b.requests.clear(); }
int paleo_state(void) { return B().state.load(); }
void paleo_note(int k, int v) {
  Bridge& b = B(); if (k < 0 || k >= paleo::kNumNotes) return;
  std::lock_guard<std::mutex> l(b.m); b.notes[k] = v < 0 ? 0 : v > 100 ? 100 : v; if (b.conductor) b.conductor->note(k, b.notes[k]);
}
int paleo_note_value(int k) { Bridge& b = B(); if (k < 0 || k >= paleo::kNumNotes) return 0; std::lock_guard<std::mutex> l(b.m); return b.notes[k]; }
void paleo_action(int a) { Bridge& b = B(); if (a < 0 || a >= paleo::kNumActions) return; std::lock_guard<std::mutex> l(b.m); b.requests.emplace_back(-1 - a, 0); }
void paleo_scene(int s, int u) { Bridge& b = B(); if (s < 0 || s >= paleo::kNumScenes) return; std::lock_guard<std::mutex> l(b.m); b.requests.emplace_back(s, u < 0 ? 0 : u > 2 ? 2 : u); }
int paleo_scene_now(void) { Bridge& b = B(); std::lock_guard<std::mutex> l(b.m); return b.scene; }
void paleo_gauges(float out[8]) { Bridge& b = B(); std::lock_guard<std::mutex> l(b.m); for (int i = 0; i < 8; i++) out[i] = b.gauges[i]; }
int paleo_gauge_text(int g, char* buf, int cap) {
  Bridge& b = B(); if (cap <= 0) return 0; buf[0] = 0; if (g < 0 || g >= paleo::kNumGauges) return 0;
  std::lock_guard<std::mutex> l(b.m); snprintf(buf, size_t(cap), "%s", b.texts[g].c_str()); return int(strlen(buf));
}
int paleo_marks(char* buf, int cap) {
  Bridge& b = B(); if (cap <= 0) return 0; std::string s; { std::lock_guard<std::mutex> l(b.m); for (auto& r : b.recent) s += r + "\n"; }
  snprintf(buf, size_t(cap), "%s", s.c_str()); return int(strlen(buf));
}
float paleo_load(void) { return B().load.load(); }
}

#ifdef __ANDROID__
/* ---- Java: org.paleotone.synth.Native ---------------------------------------------------- */
#define PJNI(name) JNIEXPORT JNICALL Java_org_paleotone_synth_Native_##name
extern "C" {
static jobject asset_ref;
void PJNI(paleoLibrary)(JNIEnv* e, jclass, jobject am) {
  if (asset_ref || !am) return; asset_ref = e->NewGlobalRef(am); paleo_library_assets(AAssetManager_fromJava(e, asset_ref));
}
void PJNI(paleoMusic)(JNIEnv*, jclass, jboolean on) { paleo_music(on ? 1 : 0); }
void PJNI(paleoHalt)(JNIEnv*, jclass) { paleo_halt(); }
jint PJNI(paleoState)(JNIEnv*, jclass) { return paleo_state(); }
void PJNI(paleoNote)(JNIEnv*, jclass, jint k, jint v) { paleo_note(k, v); }
jint PJNI(paleoNoteValue)(JNIEnv*, jclass, jint k) { return paleo_note_value(k); }
void PJNI(paleoAction)(JNIEnv*, jclass, jint a) { paleo_action(a); }
void PJNI(paleoScene)(JNIEnv*, jclass, jint s, jint u) { paleo_scene(s, u); }
jint PJNI(paleoSceneNow)(JNIEnv*, jclass) { return paleo_scene_now(); }
jfloatArray PJNI(paleoGauges)(JNIEnv* e, jclass) {
  float g[8]; paleo_gauges(g); jfloatArray a = e->NewFloatArray(8); if (a) e->SetFloatArrayRegion(a, 0, 8, g); return a;
}
jstring PJNI(paleoGaugeText)(JNIEnv* e, jclass, jint g) { char b[64]; paleo_gauge_text(g, b, sizeof b); return e->NewStringUTF(b); }
jstring PJNI(paleoMarks)(JNIEnv* e, jclass) { char b[1024]; paleo_marks(b, sizeof b); return e->NewStringUTF(b); }
jfloat PJNI(paleoLoad)(JNIEnv*, jclass) { return paleo_load(); }
}
#endif
