// SPDX-License-Identifier: GPL-3.0-only
import("paleo.lib");
// struck stones (lithophone): tuned rocks, inharmonic and dry; a stone clack, not a bell
body = modal(f,(1,2.32,4.25,6.63,9.38),(0.9,0.45,0.25,0.15,0.08),(1,0.5,0.3,0.15,0.08), strike(1.5,6000)*g);
process = body*0.25 : ma.tanh;
