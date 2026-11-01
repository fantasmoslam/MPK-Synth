#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Screens PDMX songs for the phrase bank, using PDMX.csv. PDMX's licences (public domain mark or CC0) were set by
MuseScore users, and some modern copyrighted songs are wrongly marked. So a song is kept only if:
  - its licence is publicdomain or cc-zero, with no licence conflict (the dataset's own check), and
  - its genre is classical, folk, religious or world music, or it has no genre and is credited to a traditional
    or anonymous source (MuseScore's "Misc tunes" and "Traditional" collections are old folk tunes), and
  - its title, artist and composer name no film, game, show or pop source, and it is not a scale or exercise.
Everything from popular genres (pop, rock, jazz, soundtrack, electronic and the rest) is left out: those are
mostly modern works still under copyright, whatever the label says.
Usage: screen_pdmx.py PDMX.csv POOL.txt OUT.txt   (POOL: MIDI paths, as given to extract.py)"""
import csv, sys, re
csv.field_size_limit(10 ** 9)
KEEP_GENRES = {"classical", "folk", "religiousmusic", "worldmusic"}
TRAD = re.compile(r"\b(misc|trad|traditional|anon|anonymous|unknown|folk|hymn|psalm|carol|chant|plainsong|public domain)\b", re.I)
BAD = re.compile(r"\b(ost|theme|soundtrack|anime|game|movie|film|disney|pixar|marvel|star wars|harry potter|zelda|mario|"
                 r"pok[eé]mon|undertale|deltarune|minecraft|sonic|final fantasy|kingdom hearts|genshin|halo|skyrim|"
                 r"witcher|naruto|ghibli|totoro|frozen|lion king|pirates of the caribbean|game of thrones|lord of the rings|"
                 r"hobbit|interstellar|hans zimmer|john williams|joe hisaishi|koji kondo|toby fox|beatles|queen|coldplay|"
                 r"taylor swift|imagine dragons|ed sheeran|billie eilish|adele|abba|michael jackson|elvis|"
                 r"scales?|exercises?|lessons?|arpeggios?|warm[- ]?ups?|tests?|drills?)\b", re.I)
def main(csv_path, pool_path, out_path):
    pool = set(l.strip() for l in open(pool_path) if l.strip())
    kept = []; why = {}
    for r in csv.DictReader(open(csv_path, encoding="utf-8")):
        p = r["mid"][2:]
        if p not in pool: continue
        text = " ".join(r[k] for k in ("title", "song_name", "subtitle", "artist_name", "composer_name", "tags", "groups") if r[k] != "NA")
        genres = set((r["genres"] or "NA").split("-"))
        if r["license"] not in ("publicdomain", "cc-zero") or r["license_conflict"] == "True": why[p] = "licence"; continue
        if BAD.search(text): why[p] = "title"; continue
        if genres & KEEP_GENRES and not genres - KEEP_GENRES: kept.append(p); continue
        if genres == {"NA"} and TRAD.search(r["artist_name"] + " " + r["composer_name"] + " " + r["title"]): kept.append(p); continue
        why[p] = "genre"
    open(out_path, "w").write("\n".join(sorted(kept)) + "\n")
    import collections
    print("kept %d of %d; left out: %s" % (len(kept), len(pool), dict(collections.Counter(why.values()))))
if __name__ == "__main__":
    main(*sys.argv[1:4])
