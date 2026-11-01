/* SPDX-License-Identifier: GPL-3.0-only */
/* Recorded instruments for PALEO (paleo/samples: VCSL, CC0). A recorded instrument is a set of
   zones, one recording per note; a voice plays the zone nearest the note, tuned to it, with the instrument's attack
   and release. Held instruments have a crossfaded loop baked into each recording, so a note lasts as long as its
   gate. The voice looks like a Faust dsp (freq, gain, gate), so it goes through the parts and the mix like any
   other instrument. The library is shared by every engine and is loaded once. */
#pragma once
#include "../faust/faust_min.h"
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <map>
#include <memory>
#include <string>
#include <vector>

namespace paleo {
struct SampleZone { float root = 60; int loop_start = -1, loop_end = -1; std::vector<int16_t> pcm; };
struct SampledInstrument { float attack = .01f, release = .3f; int rate = 24000; std::vector<SampleZone> zones; };
inline std::map<std::string, SampledInstrument>& sample_library() { static std::map<std::string, SampledInstrument> lib; return lib; }
/* For the loaders: the instrument's envelope, then its zones (pcm as floats at the file's rate). */
inline void add_sample_instrument(const std::string& name, float attack, float release) {
  auto& I = sample_library()[name]; I.attack = attack; I.release = release;
}
inline void add_sample_zone(const std::string& name, float root, int loop_start, int loop_end, const std::vector<float>& pcm, int rate) {
  auto& I = sample_library()[name]; I.rate = rate; SampleZone z; z.root = root; z.loop_start = loop_start; z.loop_end = loop_end;
  z.pcm.resize(pcm.size() + 1);
  for (size_t i = 0; i < pcm.size(); i++) z.pcm[i] = int16_t(std::lround(std::max(-1.f, std::min(1.f, pcm[i])) * 32767));
  /* one guard sample for the interpolation: the loop's start when the loop runs to the end, else the last sample */
  bool loops = loop_start >= 0 && loop_end == int(pcm.size());
  z.pcm.back() = loops ? z.pcm[size_t(loop_start)] : z.pcm.size() > 1 ? z.pcm[z.pcm.size() - 2] : 0;
  if (loop_end > int(pcm.size())) z.loop_start = z.loop_end = -1;
  I.zones.push_back(std::move(z));
}

class SampleDsp : public dsp {
 public:
  explicit SampleDsp(const char* name) : name_(name) {}
  int getNumInputs() override { return 0; }
  int getNumOutputs() override { return 1; }
  void buildUserInterface(UI* ui) override {
    ui->addHorizontalSlider("freq", &freq_, 440, 20, 5000, .01f); ui->addHorizontalSlider("gain", &gain_, .5f, 0, 1, .01f);
    ui->addButton("gate", &gate_);
  }
  int getSampleRate() override { return sr_; }
  void init(int sr) override { instanceInit(sr); }
  void instanceInit(int sr) override { instanceConstants(sr); instanceResetUserInterface(); instanceClear(); }
  void instanceConstants(int sr) override { sr_ = sr; }
  void instanceResetUserInterface() override { freq_ = 440; gain_ = .5f; gate_ = 0; }
  void instanceClear() override { z_ = nullptr; started_ = false; pos_ = 0; env_ = 0; level_ = 0; done_ = false; }
  dsp* clone() override { return new SampleDsp(name_); }
  void metadata(Meta*) override {}
  void compute(int n, FAUSTFLOAT**, FAUSTFLOAT** out) override {
    float* y = out[0];
    if (!started_ && gate_ > 0) start();
    if (!z_ || done_) { memset(y, 0, sizeof(float) * size_t(n)); return; }
    const int16_t* s = z_->pcm.data();
    const bool loop = z_->loop_end > z_->loop_start && z_->loop_start >= 0;
    const double last = loop ? double(z_->loop_end) + 1 : double(z_->pcm.size() - 2);
    const double span = loop ? double(z_->loop_end - z_->loop_start) : 0;
    if (gate_ > 0) level_ = gain_;                               /* the part's control zeroes gain at note off: keep it */
    for (int i = 0; i < n; i++) {
      if (gate_ > 0) { env_ += att_step_; if (env_ > 1) env_ = 1; } else env_ *= rel_k_;
      if (loop && pos_ >= z_->loop_end) pos_ -= span;
      if (pos_ >= last) { done_ = true; for (; i < n; i++) y[i] = 0; return; }
      size_t k = size_t(pos_); float f = float(pos_ - double(k));
      y[i] = (s[k] + (s[k + 1] - s[k]) * f) * (1.f / 32768.f) * env_ * level_;
      pos_ += rate_;
    }
    if (gate_ <= 0 && env_ < 1e-5f) done_ = true;
  }

 private:
  void start() {
    started_ = true;
    auto it = sample_library().find(name_); if (it == sample_library().end() || it->second.zones.empty()) return;
    const SampledInstrument& I = it->second;
    float midi = 69 + 12 * std::log2(std::max(freq_, 1.f) / 440.f);
    const SampleZone* best = &I.zones[0];
    for (auto& z : I.zones) if (std::fabs(z.root - midi) < std::fabs(best->root - midi)) best = &z;
    z_ = best; pos_ = 0;
    rate_ = std::pow(2.0, double(midi - best->root) / 12.0) * double(I.rate) / double(sr_);
    att_step_ = 1.f / std::max(1.f, I.attack * float(sr_));
    rel_k_ = std::exp(-1.f / std::max(1.f, I.release / 4.6f * float(sr_)));   /* -40 dB after the release time */
    env_ = I.attack < .005f ? 1.f : 0.f; level_ = gain_;
  }
  const char* name_; int sr_ = 48000;
  float freq_ = 440, gain_ = .5f, gate_ = 0;
  const SampleZone* z_ = nullptr; bool started_ = false, done_ = false;
  double pos_ = 0, rate_ = 1; float env_ = 0, level_ = 0, att_step_ = 1, rel_k_ = .999f;
};
}  // namespace paleo
