/* SPDX-License-Identifier: GPL-3.0-only */
/* The Scape conductor: Paleotone's generative composer, live.
   It writes the music only a little ahead of the playhead (two bars, about five seconds at 100 BPM), straight into
   a PALEO engine. Whenever something happens (an action, a note from the player, a new scene) it throws away what
   it has written but not yet played and writes it again, so the music turns within a beat. The look-ahead is only
   there so it can see a scene change coming and lead into it (drums swell, cadence chord, the lead climbs).

   Its parts follow the approved line-up (Generative Engines for Scape, expedition v2's compose.py):
     conductor   scene grammar with pacing rules, a weighted chord walk, energy and threat curves, lead-ins
     drummer     an Ape Out-style agent playing from energy and threat, with fills into scene changes
     melody      Markov chains trained only on Paleotone's own phrases, with deja vu (phrases come back changed)
     herd        a stochastic cloud of calls whose density follows the herd
     creatures   agents that call into the gaps of the lead, on the 8th-note grid
     tribe       a pattern engine: cycles of different lengths, rotated and thinned
   Everything comes from one seed, so a run can be heard again exactly. */
#pragma once
#include "paleo.h"
#include <cstdint>
#include <string>
#include <vector>

namespace paleo {

/* The player's notes to the conductor, 0..100 (50 = neutral), in the interface's order. */
enum Note { kNDanger, kNMajesty, kNDensity, kNPace, kNSpace, kNWildness, kNDrift, kNSilence, kNumNotes };
/* Expedition actions, in the interface's order. */
enum Action { kWait, kMoveOn, kFollowHerd, kShelter, kInteract, kDefend, kObserve, kTrack, kNumActions };
/* Scenes. The encounter engine will request these; until then the conductor picks its own. */
enum Scene { kDawn, kTravel, kHerd, kMajesty, kStalk, kStampede, kAftermath, kTribe, kRest, kObserveScene,
             kShelterScene, kFight, kCoda, kNumScenes };
/* The conductor's answers, in the interface's order (NaN = not answering yet). */
enum Gauge { kGThreat, kGAwe, kGParts, kGPhrases, kGDepth, kGHabitat, kGHarmony, kGMusic, kNumGauges };

const char* scene_name(int scene);
const char* action_name(int action);
const char* note_name(int note);

class Conductor {
 public:
  Conductor(Engine& engine, uint32_t seed);
  ~Conductor();
  void start(double t);            /* begin the expedition (dawn) at engine time t */
  void stop();                     /* lead into the coda, then fall silent */
  void halt(double t);             /* end at once: drop what is written from t, let the drone die away */
  bool running() const;
  void tick(double now);           /* call often (every few ms to 50 ms) with the engine clock: writes ahead */
  void note(int note, int value);  /* 0..100 */
  int note_value(int note) const;
  void action(int action);
  /* Ask for a scene. urgency 0: after the current scene; 1: soon, with a two-bar lead-in; 2: now, on the next beat. */
  void request_scene(int scene, int urgency);
  void gauges(float out[kNumGauges]) const;
  std::string gauge_text(int gauge) const;
  int scene() const;               /* the scene playing now */
  float energy() const;
  /* What the conductor did and when, for previews and the field journal. */
  struct Mark { double t; std::string text; };
  const std::vector<Mark>& marks() const;
  /* For measuring: every drum bar ("D scene pattern") and every phrase ("M degree/beats ...") it writes. */
  void set_trace(std::vector<std::string>* out);
  struct Impl; Impl* p;
};
}  // namespace paleo
