/* SPDX-License-Identifier: GPL-3.0-only */
/* The few declarations Faust's generated C++ expects, so the speed test needs no Faust headers. */
#pragma once
#ifndef FAUSTFLOAT
#define FAUSTFLOAT float
#endif
#ifdef _MSC_VER
#define RESTRICT __restrict
#else
#define RESTRICT __restrict__
#endif
struct Soundfile;
struct Meta { virtual ~Meta() {} virtual void declare(const char*, const char*) = 0; };
struct UI {
  virtual ~UI() {}
  virtual void openTabBox(const char*) {} virtual void openHorizontalBox(const char*) {}
  virtual void openVerticalBox(const char*) {} virtual void closeBox() {}
  virtual void addButton(const char*, FAUSTFLOAT*) {} virtual void addCheckButton(const char*, FAUSTFLOAT*) {}
  virtual void addVerticalSlider(const char*, FAUSTFLOAT*, FAUSTFLOAT, FAUSTFLOAT, FAUSTFLOAT, FAUSTFLOAT) {}
  virtual void addHorizontalSlider(const char*, FAUSTFLOAT*, FAUSTFLOAT, FAUSTFLOAT, FAUSTFLOAT, FAUSTFLOAT) {}
  virtual void addNumEntry(const char*, FAUSTFLOAT*, FAUSTFLOAT, FAUSTFLOAT, FAUSTFLOAT, FAUSTFLOAT) {}
  virtual void addHorizontalBargraph(const char*, FAUSTFLOAT*, FAUSTFLOAT, FAUSTFLOAT) {}
  virtual void addVerticalBargraph(const char*, FAUSTFLOAT*, FAUSTFLOAT, FAUSTFLOAT) {}
  virtual void addSoundfile(const char*, const char*, Soundfile**) {}
  virtual void declare(FAUSTFLOAT*, const char*, const char*) {}
};
class dsp {
 public:
  virtual ~dsp() {}
  virtual int getNumInputs() = 0; virtual int getNumOutputs() = 0;
  virtual void buildUserInterface(UI*) = 0; virtual int getSampleRate() = 0;
  virtual void init(int) = 0; virtual void instanceInit(int) = 0; virtual void instanceConstants(int) = 0;
  virtual void instanceResetUserInterface() = 0; virtual void instanceClear() = 0;
  virtual dsp* clone() = 0; virtual void metadata(Meta*) = 0;
  virtual void compute(int, FAUSTFLOAT**, FAUSTFLOAT**) = 0;
};
