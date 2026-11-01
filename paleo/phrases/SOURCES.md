# Music sources: melodies and drum feel

Scape's Markov melody chains learn from Paleotone's own 16 phrases (from compose.py) and from phrases pulled out of
the collections below by `extract.py`, packed into `paleo/engine/phrase_bank.h` by `bank.py`. Only the phrases'
shape (scale degrees and lengths) is used; Scape recombines them, so no complete melody is played back as written.

Licence rule: CC0, public domain, or a free licence that allows commercial use, with every author credited.

| Collection | Licence | Used | Credit |
|---|---|---|---|
| [OpenScore Lieder Corpus](https://github.com/OpenScore/Lieder) | CC0 1.0 | The voice line of 1,461 nineteenth-century songs, with keys from the corpus's harmonic analyses | OpenScore Lieder Corpus, transcribed by MuseScore volunteers (credit requested by the maintainers) |
| [PDMX](https://zenodo.org/records/15571083) | CC BY 4.0 (dataset); each score marked public domain or CC0 on MuseScore | 51,058 songs: the no-licence-conflict, deduplicated subset, screened by `screen_pdmx.py` (classical, folk, religious and world music, and traditional tunes; popular genres and anything naming a film, game, show or pop source left out) | Phillip Long, Zachary Novack, Taylor Berg-Kirkpatrick and Julian McAuley, "PDMX: A Large-Scale Public Domain MusicXML Dataset for Symbolic Music Processing" (2024), and the MuseScore users who made the scores |
| [GregoBase corpus](https://github.com/bacor/gregobasecorpus) | CC0 1.0 (data) | 9,135 Gregorian chants (GABC), from the 2019-10-24 GregoBase dump | GregoBase (gregobase.selapa.net) and its transcribers; corpus by Bas Cornelissen |

## Drum feel

`groove.py` measures how real drummers place and accent each 16th of the bar, and writes
`paleo/engine/groove_feel.h`. The conductor's drummer nudges its hits by those amounts. No pattern is copied.

| Collection | Licence | Used | Credit |
|---|---|---|---|
| [Groove MIDI Dataset](https://magenta.tensorflow.org/datasets/groove) | CC BY 4.0 | 385 straight 4/4 beat takes | Jon Gillick, Adam Roberts, Jesse Engel, Mark Sandler and Peter Kerr, "Learning to Groove with Inverse Sequence Transformations" (ICML 2019); Google LLC |

The PDMX phrase files are too big for the repository (about 70 MB). They are kept with the project files
(`phrases/pdmx-*.txt`), and `extract.py pdmx` with `screen_pdmx.py` makes them again from PDMX's `mid.tar.gz`,
`subset_paths.tar.gz` and `PDMX.csv`.

Checked 2026-11-01. Not used: The Session (its licence forbids processing with AI language models), the Essen
folksong collection (licence unclear), the Galician set (non-commercial).
