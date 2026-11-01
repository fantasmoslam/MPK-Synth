#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Checks Paleotone phrase files (the training material for Scape's Markov melody chains).

Format, one phrase per line:   deg:beats deg:beats ...   # optional source id
  deg    scale degree of the scene's mode from its tonic: 0 tonic, 7 the octave above, -1 the leading step below.
         Range -7..11. The conductor maps degrees onto its mode (natural minor by default), so write in degrees.
  beats  one of 0.25 0.5 0.75 1 1.5 2 3 4 6
  3..14 notes, at most 16 beats in all. Blank lines and lines starting with # are ignored.
Usage: check.py FILE... ; prints counts, duplicate and near-duplicate rates, and every bad line."""
import sys, collections
BEATS = {0.25, .5, .75, 1, 1.5, 2, 3, 4, 6}
def parse(line):
    body = line.split("#", 1)[0].split()
    if not body: return None
    out = []
    for tok in body:
        d, b = tok.split(":"); d = int(d); b = float(b)
        if not -7 <= d <= 11: raise ValueError("degree %d out of range" % d)
        if b not in BEATS: raise ValueError("beats %g not allowed" % b)
        out.append((d, b))
    if not 3 <= len(out) <= 14: raise ValueError("%d notes" % len(out))
    if sum(b for _, b in out) > 16: raise ValueError("%g beats" % sum(b for _, b in out))
    return tuple(out)
def shape(p):   # transposition-free shape: intervals and rhythm
    return tuple((p[i + 1][0] - p[i][0], p[i][1]) for i in range(len(p) - 1)) + (p[-1][1],)
def main(files):
    bad = 0; allp = []
    for f in files:
        ps = []
        for n, line in enumerate(open(f, encoding="utf-8"), 1):
            if line.lstrip().startswith("#"): continue
            try:
                p = parse(line)
                if p: ps.append(p)
            except Exception as e:
                bad += 1; print("%s:%d: %s: %s" % (f, n, e, line.strip()))
        c = collections.Counter(ps); s = collections.Counter(shape(p) for p in ps)
        print("%s: %d phrases, %d exact duplicates, %d same shape as another" % (f, len(ps), len(ps) - len(c), len(ps) - len(s)))
        allp += ps
    print("total %d phrases, %d bad lines" % (len(allp), bad))
    return 1 if bad else 0
if __name__ == "__main__": sys.exit(main(sys.argv[1:]))
