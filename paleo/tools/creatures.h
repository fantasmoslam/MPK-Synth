/* SPDX-License-Identifier: GPL-3.0-only */
/* Loads PALEO's creature library (paleo/creatures/lib: manifest.txt plus FLAC files) into an engine.
   Needs miniaudio's decoder, so include it after miniaudio.h with its implementation. */
#pragma once
#include "../engine/paleo.h"
#include "../engine/sampler.h"
#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

/* Loads the recorded instruments (paleo/samples/lib: manifest.txt plus FLAC files) once per process. Looks in
   PALEO_SAMPLES, then samples/lib and paleo/samples/lib, then beside the creature folder. Returns the zones loaded. */
static int paleo_load_samples(const std::string& creature_dir) {
  static int loaded = -1; if (loaded >= 0) return loaded;
  std::string dir; const char* env = getenv("PALEO_SAMPLES");
  for (std::string c : {std::string(env ? env : ""), std::string("samples/lib"), std::string("paleo/samples/lib"), creature_dir + "/../../samples/lib"})
    if (!c.empty() && std::ifstream(c + "/manifest.txt")) { dir = c; break; }
  loaded = 0; if (dir.empty()) return 0;
  std::ifstream m(dir + "/manifest.txt"); std::string line;
  while (std::getline(m, line)) {
    if (line.empty() || line[0] == '#') continue;
    std::istringstream ss(line); std::string kind, name; ss >> kind >> name;
    if (kind == "inst") { float a = .01f, r = .3f; ss >> a >> r; paleo::add_sample_instrument(name, a, r); continue; }
    if (kind != "zone") continue;
    float root; int ls, le; std::string file; if (!(ss >> root >> ls >> le >> file)) continue;
    ma_decoder_config cfg = ma_decoder_config_init(ma_format_f32, 1, 0); ma_decoder d;
    if (ma_decoder_init_file((dir + "/" + file).c_str(), &cfg, &d) != MA_SUCCESS) { fprintf(stderr, "paleo: can't read %s\n", file.c_str()); continue; }
    std::vector<float> pcm; float buf[4096]; ma_uint64 got = 0;
    while (ma_decoder_read_pcm_frames(&d, buf, 4096, &got) == MA_SUCCESS && got > 0) pcm.insert(pcm.end(), buf, buf + got);
    int rate = int(d.outputSampleRate); ma_decoder_uninit(&d);
    paleo::add_sample_zone(name, root, ls, le, pcm, rate); loaded++;
  }
  return loaded;
}

/* Looks in dir, or if dir is empty: "creatures" next to the score, then creatures/lib beside the score folder, ./creatures, ./creatures/lib, paleo/creatures/lib. Returns the files loaded. */
static int paleo_load_creatures(paleo::Engine& e, std::string dir, const char* score_path) {
  struct SamplesOnce { SamplesOnce(const std::string& d) { int z = paleo_load_samples(d); if (z) fprintf(stderr, "recorded instruments: %d notes\n", z); } };
  if (dir.empty()) {
    std::string s = score_path; size_t k = s.find_last_of("/\\");
    std::string here = k == std::string::npos ? std::string(".") : s.substr(0, k);
    for (std::string c : {here + "/creatures", here + "/../creatures/lib", std::string("creatures"), std::string("creatures/lib"), std::string("paleo/creatures/lib")})
      if (std::ifstream(c + "/manifest.txt")) { dir = c; break; }
    if (dir.empty()) return 0;
  }
  static SamplesOnce once(dir);
  std::ifstream m(dir + "/manifest.txt"); std::string line; int n = 0;
  while (std::getline(m, line)) {
    if (line.empty() || line[0] == '#') continue;
    std::istringstream ss(line); std::string kind, file; if (!(ss >> kind >> file)) continue;
    ma_decoder_config cfg = ma_decoder_config_init(ma_format_f32, 1, 48000); ma_decoder d;
    if (ma_decoder_init_file((dir + "/" + file).c_str(), &cfg, &d) != MA_SUCCESS) { fprintf(stderr, "paleo: can't read %s\n", file.c_str()); continue; }
    std::vector<float> pcm; float buf[4096]; ma_uint64 got = 0;
    while (ma_decoder_read_pcm_frames(&d, buf, 4096, &got) == MA_SUCCESS && got > 0) pcm.insert(pcm.end(), buf, buf + got);
    ma_decoder_uninit(&d);
    e.add_creature(kind, std::move(pcm)); n++;
  }
  return n;
}
