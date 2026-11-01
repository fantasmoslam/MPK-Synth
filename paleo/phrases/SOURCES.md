# Melody sources

Scape's melody generator (paleo/engine/motifs.h) cuts most of its motifs from phrases pulled out of
the collections below by `extract.py`, packed into `paleo/engine/phrase_bank.h` by `bank.py`, then develops them. Only the phrases'
shape (scale degrees and lengths) is used, three to seven notes at a time, so no melody is played back as written.

Licence rule: CC0, public domain, or a free licence that allows commercial use, with every author credited.

| Collection | Licence | Used | Credit |
|---|---|---|---|
| [OpenScore Lieder Corpus](https://github.com/OpenScore/Lieder) | CC0 1.0 | The voice line of 1,461 nineteenth-century songs, with keys from the corpus's harmonic analyses | OpenScore Lieder Corpus, transcribed by MuseScore volunteers (credit requested by the maintainers) |
| [PDMX](https://zenodo.org/records/15571083) | CC BY 4.0 (dataset); each score marked public domain or CC0 on MuseScore | 51,058 songs: the no-licence-conflict, deduplicated subset, screened by `screen_pdmx.py` (classical, folk, religious and world music, and traditional tunes; popular genres and anything naming a film, game, show or pop source left out) | Phillip Long, Zachary Novack, Taylor Berg-Kirkpatrick and Julian McAuley, "PDMX: A Large-Scale Public Domain MusicXML Dataset for Symbolic Music Processing" (2024), and the MuseScore users who made the scores |
| [GregoBase corpus](https://github.com/bacor/gregobasecorpus) | CC0 1.0 (data) | 9,135 Gregorian chants (GABC), from the 2019-10-24 GregoBase dump | GregoBase (gregobase.selapa.net) and its transcribers; corpus by Bas Cornelissen |

The PDMX phrase files are too big for the repository (about 70 MB). They are kept with the project files
(`phrases/pdmx-*.txt`), and `extract.py pdmx` with `screen_pdmx.py` makes them again from PDMX's `mid.tar.gz`,
`subset_paths.tar.gz` and `PDMX.csv`.

Checked 2026-11-01. Not used: The Session (its licence forbids processing with AI language models), the Essen
folksong collection (licence unclear), the Galician set (non-commercial).
