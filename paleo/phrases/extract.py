#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Pulls melody phrases out of real music for Scape's Markov melody chains, and sorts them by character.

Sources (all CC0, checked 2026-11-01):
  lieder  OpenScore Lieder Corpus, https://github.com/OpenScore/Lieder (CC0; credit OpenScore Lieder).
          The voice line of each song's MusicXML; keys from the corpus's own harmonic analyses (.rntxt).
  pdmx    PDMX, https://zenodo.org/records/15571083 (CC BY 4.0 dataset of MuseScore scores marked public domain
          or CC0). The highest part of each song's MIDI file; the key found from all its notes.
  chant   GregoBase corpus, https://github.com/bacor/gregobasecorpus (CC0). Gregorian chant in GABC.

Each phrase becomes scale degrees from the key's tonic (in the key's own mode) and beats, the format of
check.py. Notes outside the key are moved to the nearest note of the key, so the conductor never plays
out of key. Phrases end at rests, breath marks and long notes.

Character (the conductor's four melody kinds), from the phrase alone:
  travel   moving notes, mostly steps
  majesty  long notes, a wide span, leaps
  danger   short or repeated notes in a narrow span, or minor and agitated
  coda     falls to the tonic and settles on a long note

Usage: extract.py lieder DIR OUT_PREFIX     (writes OUT_PREFIX-travel.txt and so on)
       extract.py chant SQL_DUMP OUT_PREFIX
       extract.py pdmx LIST OUT_PREFIX            (LIST: MIDI paths, e.g. PDMX's no-licence-conflict, deduplicated subset)"""
import sys, os, re, glob, collections
from multiprocessing import Pool

BEATS = [0.25, .5, .75, 1, 1.5, 2, 3, 4, 6]
MAJOR = [0, 2, 4, 5, 7, 9, 11]; MINOR = [0, 2, 3, 5, 7, 8, 10]
KINDS = ["travel", "majesty", "danger", "coda"]
PC = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}

def qbeats(x):
    return min(BEATS, key=lambda b: abs(b - x))

def degree(midi, tonic, minor):
    """Absolute diatonic degree (7 per octave) of a MIDI note in the key; chromatic notes go to the nearer key note."""
    sc = MINOR if minor else MAJOR
    rel = midi - tonic; o, r = divmod(rel, 12)
    best = min(range(8), key=lambda i: (abs((sc + [12])[i] - r), i))   # ties go down
    return 7 * o + (best if best < 7 else 0) + (7 if best == 7 else 0)

def normalise(ph):
    """Shift by octaves so the phrase sits around the tonic (degrees 0..7 on average)."""
    mean = sum(d for d, _ in ph) / len(ph)
    k = -int((mean + 1) // 7)
    return [(d + 7 * k, b) for d, b in ph]

def valid(ph):
    return (3 <= len(ph) <= 14 and sum(b for _, b in ph) <= 16 and all(-7 <= d <= 11 for d, _ in ph))

def split_long(ph):
    """Phrases longer than the limits are cut after a long note or at the middle."""
    if len(ph) <= 14 and sum(b for _, b in ph) <= 16: return [ph]
    best = None
    for i in range(3, len(ph) - 3):
        if ph[i - 1][1] >= 1.5 and (best is None or abs(i - len(ph) / 2) < abs(best - len(ph) / 2)): best = i
    if best is None: best = len(ph) // 2
    return split_long(ph[:best]) + split_long(ph[best:])

def character(ph, minor):
    ds = [d for d, _ in ph]; bs = [b for _, b in ph]
    avg = sum(bs) / len(bs); span = max(ds) - min(ds)
    steps = [abs(ds[i + 1] - ds[i]) for i in range(len(ds) - 1)]
    stepwise = sum(1 for s in steps if s <= 1) / len(steps)
    leaps = sum(1 for s in steps if s >= 3)
    repeats = sum(1 for s in steps if s == 0) / len(steps)
    falls_home = ds[-1] % 7 == 0 and ds[-1] < ds[0] and bs[-1] >= 2
    score = {
        "coda": (2 if falls_home else 0) + (1 if avg >= 1 else 0) + (1 if ds[-1] <= min(ds) else 0),
        "majesty": (2 if avg >= 1.25 else 0) + (1 if span >= 5 and avg >= .75 else 0) + (1 if leaps >= 1 and avg >= .75 else 0) + (.5 if not minor else 0),
        "danger": (1 if avg <= .75 else 0) + (1 if span <= 3 else 0) + (1 if repeats >= .3 else 0) + (1 if minor else 0),
        "travel": (1 if .5 <= avg <= 1.25 else 0) + (1 if stepwise >= .6 else 0) + (1 if 3 <= span <= 7 else 0) + .5,
    }
    return max(KINDS, key=lambda k: score[k])

# ---------------------------------------------------------------- OpenScore Lieder
def rn_keys(path):
    """measure -> (tonic pc, minor) from a Roman-text analysis: every 'X:' token starts a key."""
    keys = {}; cur = None
    if not os.path.exists(path): return keys
    for line in open(path, encoding="utf-8", errors="replace"):
        m = re.match(r"m(\d+)\s+(.*)", line.strip())
        if not m: continue
        bar = int(m.group(1))
        for tok in m.group(2).split():
            k = re.match(r"^([A-Ga-g])([#b-]*):$", tok)
            if k:
                pc = PC[k.group(1).upper()] + k.group(2).count("#") - k.group(2).count("b") - k.group(2).count("-")
                cur = (pc % 12, k.group(1).islower())
                if bar not in keys: keys[bar] = cur
        if bar not in keys and cur: keys[bar] = cur
    return keys

def lieder_one(mxl):
    try:
        return lieder_score(mxl)
    except Exception as e:
        print('skipped %s: %s' % (os.path.basename(mxl), e), file=sys.stderr); return []

def lieder_score(mxl):
    import music21 as m21
    try:
        s = m21.converter.parse(mxl)
    except Exception:
        return []
    parts = list(s.parts)
    if not parts: return []
    voice = next((p for p in parts if re.search(r"voice|singst|sopran|tenor|alto|bass|gesang|canto|stimme|chant", (p.partName or ""), re.I)), parts[0])
    keys = rn_keys(os.path.join(os.path.dirname(mxl), "analysis_automatic.rntxt"))
    if not keys:
        k = s.analyze("key"); keys = {1: (k.tonic.pitchClass, k.mode == "minor")}
    def key_at(bar):
        best = None
        for b in sorted(keys):
            if b <= bar: best = keys[b]
            else: break
        return best or keys[min(keys)]
    flat = voice.stripTies().flatten()
    phrases = []; cur = []; cur_minor = False
    def close():
        nonlocal cur
        if cur:
            for piece in split_long(cur): phrases.append((piece, cur_minor))
        cur = []
    for el in flat.notesAndRests:
        if el.isRest:
            close(); continue
        if el.isChord: el = el.notes[-1]          # the top note of a chord in the voice part
        bar = el.measureNumber or 1
        tonic, minor = key_at(bar)
        b = float(el.quarterLength)
        if b <= 0: continue                         # grace notes
        if not cur: cur_minor = minor
        cur.append((degree(el.pitch.midi, tonic + 60, minor), qbeats(b)))
        if any(isinstance(x, m21.expressions.Fermata) for x in el.expressions) or any(isinstance(a, m21.articulations.BreathMark) for a in el.articulations):
            close()
    close()
    src = os.path.basename(mxl).split(".")[0]
    out = []
    for ph, minor in phrases:
        ph = normalise(ph)
        if valid(ph): out.append((character(ph, minor), ph, "lieder:" + src))
    return out

# ---------------------------------------------------------------- GregoBase chant (GABC)
GABC_PITCH = "abcdefghijklm"
def gabc_phrases(gabc, mode, ident):
    """GABC notes in brackets after each syllable; ',' ';' ':' '::' are bars (phrase ends).
    Rhythm (Solesmes): every note is one pulse (half a beat); a dot (mora) or episema doubles it."""
    clef = re.search(r"\((c[1-4]|f[1-4]|cb[1-4])\)", gabc)
    if not clef: return []
    c = clef.group(1); line = int(c[-1])
    # staff positions: 'a' is the space under the staff; lines 1..4 are d, f, h, j, so a clef on line n is at 2n+1
    clef_idx = 2 * line + 1
    clef_step = 0 if c.startswith("c") else 3                        # C = 0, F = 3 on the white-note scale
    final = {1: 1, 2: 1, 3: 2, 4: 2, 5: 3, 6: 3, 7: 4, 8: 4}.get(mode, 1)   # D E F G as white-note steps from C
    minorish = mode in (1, 2, 3, 4)
    out = []; cur = []
    for grp in re.findall(r"\(([^)]*)\)", gabc):
        if re.fullmatch(r"\s*(c[1-4]|f[1-4]|cb[1-4])\s*", grp): continue
        if re.search(r"[,;:]", grp) or grp.strip() in ("z", "Z"):
            notes_before = re.split(r"[,;:]", grp)[0]
            grp = notes_before; end = True
        else:
            end = False
        grp = re.sub(r"\[[^\]]*\]", "", grp)                         # [attributes]
        for m in re.finditer(r"([a-mA-M])([^a-mA-M]*)", grp):
            ch, marks = m.group(1).lower(), m.group(2)
            if marks[:1] in ("x", "y", "#"): continue                  # a flat, natural or sharp sign, not a note
            idx = GABC_PITCH.index(ch)
            step = clef_step + (idx - clef_idx)                     # white-note steps above C (of the clef's octave)
            deg = step - final
            beats = .5 * (2 if ("." in marks or "_" in marks) else 1)
            cur.append((deg, beats))
        if end and cur:
            out.append(cur); cur = []
    if cur: out.append(cur)
    res = []
    for ph in out:
        merged = []
        for d, b in ph:                                              # repeated notes in a neume (strophae) merge into one
            if merged and merged[-1][0] == d and b == .5 and merged[-1][1] < 2: merged[-1] = (d, qbeats(merged[-1][1] + .5))
            else: merged.append((d, b))
        for piece in split_long(merged):
            if piece: piece[-1] = (piece[-1][0], qbeats(max(piece[-1][1], 1.5)))   # the phrase-end lengthening of chant
            piece = normalise(piece)
            if valid(piece): res.append((character(piece, minorish), piece, "chant:" + ident))
    return res

def sql_rows(line):
    """The fields of one '(v, v, ...)' row of a MySQL dump line: numbers, NULL or quoted strings."""
    out = []; i = line.index("(") + 1; n = len(line)
    while i < n:
        while i < n and line[i] in " ,": i += 1
        if i >= n or line[i] == ")": break
        if line[i] == "'":
            i += 1; buf = []
            while i < n and line[i] != "'":
                if line[i] == "\\" and i + 1 < n: buf.append(line[i + 1]); i += 2
                else: buf.append(line[i]); i += 1
            out.append("".join(buf)); i += 1
        else:
            j = i
            while j < n and line[j] not in ",)": j += 1
            tok = line[i:j].strip(); out.append(None if tok == "NULL" else tok); i = j
    return out

def chants(sql):
    """(id, mode, gabc) for each chant in the GregoBase SQL dump (the corpus's CC0 release)."""
    table = False
    for line in open(sql, encoding="utf-8", errors="replace"):
        if line.startswith("INSERT INTO `gregobase_chants`"): table = True; continue
        if table and not line.startswith("("): table = False
        if not table: continue
        f = sql_rows(line)
        if len(f) < 11 or not f[10]: continue
        try: mode = int(f[6])
        except (TypeError, ValueError): mode = 0
        yield f[0], mode, f[10]

# ---------------------------------------------------------------- PDMX (MIDI)
# Krumhansl-Kessler key profiles, for finding the key from the notes (MIDI key signatures are the written ones).
KK_MAJ = [6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88]
KK_MIN = [6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17]
def find_key(hist):
    def corr(a, b):
        ma, mb = sum(a) / 12, sum(b) / 12
        num = sum((x - ma) * (y - mb) for x, y in zip(a, b))
        den = (sum((x - ma) ** 2 for x in a) * sum((y - mb) ** 2 for y in b)) ** .5
        return num / den if den else 0
    best = None
    for t in range(12):
        rot = hist[t:] + hist[:t]
        for minor, prof in ((False, KK_MAJ), (True, KK_MIN)):
            c = corr(rot, prof)
            if best is None or c > best[0]: best = (c, t, minor)
    return best[1], best[2]

def pdmx_one(path):
    try:
        return pdmx_song(path)
    except Exception as e:
        return []

def pdmx_song(path):
    import mido
    m = mido.MidiFile(path)
    tpb = m.ticks_per_beat or 480
    tracks = []; hist = [0.0] * 12
    for tr in m.tracks:
        t = 0; on = {}; notes = []
        for msg in tr:
            t += msg.time
            if msg.type == "note_on" and msg.velocity > 0 and msg.channel != 9:
                on[(msg.channel, msg.note)] = t
            elif (msg.type == "note_off" or (msg.type == "note_on" and msg.velocity == 0)) and msg.channel != 9:
                s0 = on.pop((msg.channel, msg.note), None)
                if s0 is not None and t > s0: notes.append((s0 / tpb, (t - s0) / tpb, msg.note))
        if len(notes) >= 16:
            for s0, d, n in notes: hist[n % 12] += d
            tracks.append(sorted(notes))
    if not tracks: return []
    tonic, minor = find_key(hist)
    def mean_pitch(ns): return sum(n for _, _, n in ns) / len(ns)
    mel = max(tracks, key=mean_pitch)
    # skyline: the top note at each onset
    top = {}
    for s0, d, n in mel:
        k = round(s0 * 48) / 48
        if k not in top or n > top[k][1]: top[k] = (d, n)
    seq = sorted((k, d, n) for k, (d, n) in top.items())
    phrases = []; cur = []
    for i, (s0, d, n) in enumerate(seq):
        nxt = seq[i + 1][0] if i + 1 < len(seq) else s0 + d
        gap = nxt - (s0 + d)
        b = nxt - s0 if gap < .5 else d                  # legato: up to the next note; otherwise the note itself
        if b < .2: continue                              # ornaments
        cur.append((degree(n, tonic + 60, minor), qbeats(b)))
        if gap >= .5 or b >= 4:
            if cur: phrases += split_long(cur)
            cur = []
    if cur: phrases += split_long(cur)
    src = "pdmx:" + os.path.basename(path).split(".")[0]
    out = []
    for ph in phrases:
        if len(set(d for d, _ in ph)) < 2: continue      # one repeated note is not a melody
        ph = normalise(ph)
        if valid(ph): out.append((character(ph, minor), ph, src))
    return out

def main():
    what, src, prefix = sys.argv[1], sys.argv[2], sys.argv[3]
    got = []
    if what == "lieder":
        files = sorted(glob.glob(os.path.join(src, "**", "*.mxl"), recursive=True))
        with Pool() as pool:
            for r in pool.imap_unordered(lieder_one, files, chunksize=4): got += r
    elif what == "pdmx":                             # src: a file listing the MIDI paths to use
        files = [l.strip() for l in open(src) if l.strip()]
        with Pool() as pool:
            for r in pool.imap_unordered(pdmx_one, files, chunksize=64): got += r
    elif what == "chant":
        for ident, mode, gabc in chants(src): got += gabc_phrases(gabc, mode, ident)
    by = collections.defaultdict(list); seen = set()
    for kind, ph, s in got:
        key = tuple(ph)
        if key in seen: continue
        seen.add(key); by[kind].append((ph, s))
    for k in KINDS:
        with open("%s-%s.txt" % (prefix, k), "w", encoding="utf-8") as f:
            f.write("# %s phrases from %s, made by paleo/phrases/extract.py\n" % (k, what))
            for ph, s in sorted(by[k], key=lambda x: x[1]):
                f.write(" ".join("%d:%g" % (d, b) for d, b in ph) + "  # " + s + "\n")
        print("%s %s: %d" % (what, k, len(by[k])))

if __name__ == "__main__":
    main()
