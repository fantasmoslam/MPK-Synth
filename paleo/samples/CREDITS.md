# Recorded instrument credits

The notes in `lib/` were cut from the Versilian Community Sample Library (VCSL) by Versilian Studios (Sam Gossner
and contributors), released under CC0 1.0 (public domain dedication): https://github.com/sgossner/VCSL
No credit is required; we give it anyway. Only folk and world instruments are used: ocarina, alto and tenor
recorders, bowed psaltery, wine glasses, folk harp, dan tranh, kalimba, mbira, balafon and marimba.
Orchestral sample libraries are not used.

`build.py` takes one recording per note, makes it mono at 24 kHz, trims it, levels it, and bakes a crossfaded
loop into held notes. To rebuild: `python3 paleo/samples/build.py PATH/TO/VCSL`.
