#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Builds PALEO's recorded-instrument library (paleo/samples/lib) from the Versilian Community Sample Library
(VCSL, CC0, Versilian Studios): folk and world instruments only. Orchestral libraries are not used.

For each instrument it takes one recording per note (the middle dynamic when there are several), finds the note's
real pitch, makes it mono at 24 kHz, trims the silence before it, and:
  held instruments (bowed, blown): keeps about 3.5 s and bakes a crossfaded loop into the sustain, so a note can
      be held as long as the music wants;
  plucked and struck instruments: keeps the ring until it dies away (at most MAX_RING seconds).
Each instrument is levelled so all its notes sound equally loud. Writes lib/<name>-<n>.flac and lib/manifest.txt:
  inst NAME ATTACK RELEASE          the envelope the engine gives every note (seconds)
  zone NAME ROOT LOOP_START LOOP_END FILE   ROOT is a MIDI note with cents (e.g. 64.12); loop points in frames, -1 none
Usage: build.py VCSL_DIR"""
import sys, os, glob, re
import numpy as np, soundfile as sf
from scipy.signal import resample_poly

SR = 24000
MAX_RING = 4.0
HOLD_KEEP = 3.6          # seconds kept of a held note
LOOP_FROM = 1.0          # loop starts this far in (after the attack has settled)
XF = .15                 # loop crossfade, seconds
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "lib")

# name, library, folder (glob under it), held?, attack, release, low..high MIDI kept
INSTRUMENTS = [
    ("ocarina",  "vcsl", "Aerophones/Edge-blown Aerophones/Ocarina, Typical/Sustains/SusVib/*.wav", True, .05, .25, 60, 96),
    ("recorder", "vcsl", "Aerophones/Edge-blown Aerophones/Baroque Alto Recorder/SusVib/*.wav", True, .04, .25, 60, 96),
    ("trecorder", "vcsl", "Aerophones/Edge-blown Aerophones/Baroque Tenor Recorder/SusVib/*.wav", True, .05, .25, 48, 84),
    ("psaltery", "vcsl", "Chordophones/Zithers/Psaltery, Bowed and Plucked/LongBow/*.wav", True, .15, .5, 55, 100),
    ("glass",    "vcsl", "Idiophones/Friction Idiophones/Wine Glasses/Sustains/Slow/*.wav", True, .3, .8, 60, 100),
    ("folkharp", "vcsl", "Chordophones/Composite Chordophones/Folk Harp/*.wav", False, .003, .4, 24, 100),
    ("dantranh", "vcsl", "Chordophones/Zithers/Dan Tranh/Normal/*.wav",    False, .003, .4, 40, 100),
    ("kalimba",  "vcsl", "Idiophones/Plucked Idiophones/Kalimba, Kenya/**/*.wav", False, .002, .3, 50, 100),
    ("mbira",    "vcsl", "Idiophones/Plucked Idiophones/Mbira dzaVadzimu Nyamaropa, Zimbabwe, Low B/*.wav", False, .002, .3, 40, 100),
    ("balafon",  "vcsl", "Idiophones/Struck Idiophones/Balafon/Traditional Mallet/*.wav", False, .002, .25, 40, 100),
    ("marimba",  "vcsl", "Idiophones/Struck Idiophones/Marimba/*.wav",     False, .002, .25, 36, 100),
]
RING = {"chimes": 6.0, "didgephrase": 12.0, "gong": 8.0, "bassdrum": 5.0}   # longer rings than MAX_RING

# Drums and other unpitched things: each sound gets its own note (played as recorded at that note).
# name, attack, release, [(note, file glob)]
KITS = []

def mono24(path):
    x, sr = sf.read(path, dtype="float64", always_2d=True)
    x = x.mean(axis=1)
    if sr != SR:
        g = np.gcd(SR, sr); x = resample_poly(x, SR // g, sr // g)
    return x

def yin(x, fmin=27.5, fmax=2100):
    """Median YIN pitch (Hz) over the steady part of a note."""
    w = 2048; tau_max = int(SR / fmin); tau_min = int(SR / fmax); est = []
    start = int(.15 * SR); stop = min(len(x) - w - tau_max, int(1.2 * SR))
    for s in range(start, max(start + 1, stop), 1024):
        seg = x[s:s + w + tau_max]
        if len(seg) < w + tau_max or np.sqrt(np.mean(seg ** 2)) < 1e-4: continue
        d = np.array([np.sum((seg[:w] - seg[t:t + w]) ** 2) for t in range(tau_max + 1)])
        cmnd = d[1:] * np.arange(1, tau_max + 1) / np.maximum(np.cumsum(d[1:]), 1e-12)
        cmnd = np.concatenate([[1], cmnd])
        cand = np.where(cmnd[tau_min:] < .15)[0]
        if not len(cand): continue
        t = cand[0] + tau_min
        while t + 1 < len(cmnd) and cmnd[t + 1] < cmnd[t]: t += 1
        if 1 <= t < len(cmnd) - 1:          # parabolic refinement
            a, b, c = cmnd[t - 1], cmnd[t], cmnd[t + 1]; t = t + .5 * (a - c) / max(a - 2 * b + c, 1e-12)
        est.append(SR / t)
    return float(np.median(est)) if est else 0.0

def name_midi(path):
    """The note in the file name (A#3, C4 ...), used to check the pitch found."""
    m = re.search(r"(?<![A-Za-z])([A-G])(#|b)?(-?\d)(?!\d)", os.path.basename(path))
    if not m: return None
    pc = "C D EF G A B".index(m.group(1)) + (1 if m.group(2) == "#" else -1 if m.group(2) == "b" else 0)
    return 12 * (int(m.group(3)) + 1) + pc

def trim(x):
    pk = np.max(np.abs(x)); on = np.where(np.abs(x) > pk * .02)[0]
    s = max(0, on[0] - int(.004 * SR)) if len(on) else 0
    return x[s:]

def bake_loop(x):
    """A crossfaded loop over the sustain: returns (audio, loop_start, loop_end)."""
    ls = int(LOOP_FROM * SR); le = min(len(x) - int(.05 * SR), int(HOLD_KEEP * SR)); X = int(XF * SR)
    if le - ls < 3 * X: return x[:le], -1, -1
    # nudge the end to where the waveform best matches the loop start
    win = int(.01 * SR); ref = x[ls:ls + win]; best, bi = -2, le
    for e in range(le - int(.03 * SR), le):
        c = np.dot(ref, x[e:e + win]) / (np.linalg.norm(ref) * np.linalg.norm(x[e:e + win]) + 1e-12)
        if c > best: best, bi = c, e
    le = bi; y = x[:le].copy(); w = np.linspace(0, 1, X)
    y[le - X:le] = x[le - X:le] * np.sqrt(1 - w) + x[ls - X:ls] * np.sqrt(w)
    return y, ls, le

def ring(x, max_ring=MAX_RING):
    env = np.abs(x); pk = env.max(); n = len(x)
    # cut where a 50 ms window stays below -55 dB of the peak
    hop = int(.05 * SR); lvl = [np.sqrt(np.mean(x[i:i + hop] ** 2)) for i in range(0, n, hop)]
    end = n
    for i, l in enumerate(lvl):
        if i > 4 and l < pk * 10 ** (-55 / 20): end = i * hop; break
    end = min(end, int(max_ring * SR)); y = x[:end].copy(); f = min(int(.08 * SR), end // 4)
    y[end - f:] *= np.linspace(1, 0, f)
    return y

def main():
    vcsl = sys.argv[1]
    os.makedirs(OUT, exist_ok=True)
    for f in glob.glob(os.path.join(OUT, "*.flac")): os.remove(f)
    man = ["# built by paleo/samples/build.py from VCSL (CC0, Versilian Studios); see CREDITS.md"]
    total = 0
    for name, lib, pat, held, att, rel, lo, hi in INSTRUMENTS:
        files = sorted(glob.glob(os.path.join(vcsl, pat), recursive=True))
        notes = {}
        for f in files:
            if re.search(r"Release|_rel", f, re.I): continue
            x = trim(mono24(f)); hz = yin(x)
            nm = name_midi(f)
            if hz <= 0:
                if nm is None: continue
                midi = float(nm)
            else:
                midi = 69 + 12 * np.log2(hz / 440)   # the libraries name octaves differently, so the pitch found wins
            r = int(round(midi))
            if r < lo or r > hi: continue
            seg = x[int(.2 * SR):int(1.2 * SR)] if held else x[:int(.3 * SR)]
            notes.setdefault(r, []).append((float(np.sqrt(np.mean(seg ** 2))), midi, x, f))
        zones = []
        for r in sorted(notes):
            cand = sorted(notes[r], key=lambda c: c[0]); c = cand[len(cand) // 2]   # the middle dynamic
            zones.append(c)
        if not zones: print("%s: nothing found" % name); continue
        # level: every note at the same loudness (held: sustain RMS; struck: the hit's RMS)
        out = []
        for lvl, midi, x, f in zones:
            y = x / max(lvl, 1e-6) * .12
            if held: y, ls, le = bake_loop(y)
            else: y, ls, le = ring(y, RING.get(name, MAX_RING)), -1, -1
            pk = np.max(np.abs(y))
            out.append([midi, y, ls, le, pk])
        g = min(1.0, .89 / max(o[4] for o in out))          # no file may clip
        man.append("inst %s %.3f %.3f" % (name, att, rel))
        for i, (midi, y, ls, le, pk) in enumerate(out):
            fn = "%s-%02d.flac" % (name, i)
            sf.write(os.path.join(OUT, fn), (y * g).astype(np.float32), SR, subtype="PCM_16")
            man.append("zone %s %.2f %d %d %s" % (name, midi, ls, le, fn)); total += len(y)
        print("%-10s %2d notes  %s" % (name, len(out), " ".join("%.1f" % o[0] for o in out)))
    for name, att, rel, sounds in KITS:
        out = []
        for root, pat in sounds:
            fs = sorted(glob.glob(os.path.join(vcsl, pat)))
            if not fs: print("%s: missing %s" % (name, pat)); continue
            x = trim(mono24(fs[0])); lvl = float(np.sqrt(np.mean(x[:int(.3 * SR)] ** 2)))
            y = ring(x / max(lvl, 1e-6) * .12, RING.get(name, MAX_RING)); out.append((root, y))
        g = min(1.0, .89 / max(np.max(np.abs(y)) for _, y in out))
        man.append("inst %s %.3f %.3f" % (name, att, rel))
        for i, (root, y) in enumerate(out):
            fn = "%s-%02d.flac" % (name, i)
            sf.write(os.path.join(OUT, fn), (y * g).astype(np.float32), SR, subtype="PCM_16")
            man.append("zone %s %.2f -1 -1 %s" % (name, root, fn)); total += len(y)
        print("%-10s %2d sounds" % (name, len(out)))
    open(os.path.join(OUT, "manifest.txt"), "w").write("\n".join(man) + "\n")
    print("total %.1f s of audio, %.1f MB as 16-bit in memory" % (total / SR, total * 2 / 1e6))

if __name__ == "__main__":
    main()
