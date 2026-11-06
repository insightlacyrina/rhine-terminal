#!/usr/bin/env python3
"""Render the original preview-only mono sound design without third-party audio."""
from math import cos, exp, pi, sin, tau
from pathlib import Path
from random import Random
from struct import pack
import wave

DEST = Path(__file__).resolve().parents[2] / "app/src/main/res/raw"
RATE = 24000


def render(name, length, synth):
    with wave.open(str(DEST / f"archive_{name}.wav"), "wb") as audio:
        audio.setparams((1, 2, RATE, 0, "NONE", "not compressed"))
        audio.writeframes(b"".join(pack("<h", round(32767 * synth(n/RATE)))
                                  for n in range(round(length * RATE))))


def bed(t):
    period = 12
    # Each oscillator completes a whole number of cycles at the 12-second seam.
    layers = sum(sin(tau * cycles * t / period) * gain
                 for cycles, gain in [(663, .035), (997, .015), (1985, .009), (2329, .005)])
    return layers * (.63 + .37 * cos(tau*t/period))


def decay(t, attack, fall):
    return min(1., t / attack) * exp(-t / fall)


rng = Random(16)
render("bed", 12, bed)
render("denied", .38, lambda t: decay(t, .012, .083) *
       (.28*sin(tau*(167*t-88*t*t)) + .12*sin(tau*(306*t-112*t*t)) + .1*rng.uniform(-1,1)))
render("resolved", .72, lambda t: decay(t,.023,.18)*(.12*sin(tau*392*t)+.09*sin(tau*588*t)) +
       (decay(t-.11,.027,.14)*.10*sin(tau*786*(t-.11)) if t >= .11 else 0))
