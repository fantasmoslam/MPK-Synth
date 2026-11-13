# Kit: not yet sourced

The Kit (`android/native/mpk/kit.c`) is a generic analog-style drum synth: sine drums with a
falling pitch, filtered noise and six square waves for the metal "the way the classic drum
machines did it". It is **not modelled on any one machine**, and its four kits ("Classic",
"Boom Kit", "Tight Kit", "Dirty Kit") and its default pad tunings were written by hand.
Under the rule in [README.md](README.md) they have no source.

To fix this properly the Kit needs a specific machine to model (for example the TR-808,
whose voice circuits are published in its service notes and analysed in detail in the
literature), and then its voices, default tunings and controls rebuilt from that machine's
data. That choice is the project owner's; until it is made, treat the Kit's sounds as
placeholders.
