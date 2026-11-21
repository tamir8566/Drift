#!/usr/bin/env python3
"""Generates Drift's three feedback sounds (standard library only, no samples).

Run from the repository root:

    python3 tools/make_sounds.py

It writes mono 16-bit PCM WAV files to app/src/main/res/raw/:

    saved.wav     880 Hz + 1760 Hz (the octave at -12 dB), 70 ms, exponential decay
                  - a shift, an expense or a deposit was saved
    goal.wav      523 -> 659 -> 784 Hz, 90 ms each, soft attack and decay
                  - the goal was reached (once per event)
    captured.wav  440 Hz, 50 ms - an expense was captured while the app is open

saved and captured are written at 22.05 kHz. goal is 270 ms long, which at 22.05 kHz would be
about 12 KB; it is written at 11.025 kHz (its highest tone is 784 Hz) so that every file stays
well under 10 KB. The generated files are committed; re-run the script only to change a sound.
"""

import math
import os
import struct
import wave

RATE = 22050
GOAL_RATE = 11025
PEAK = 0.55  # of full scale; the app plays the sounds at volume 0.3 on top of this
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "raw")


def db(value):
    """Decibels to a linear gain."""
    return 10.0 ** (value / 20.0)


def fade(i, n, attack, release):
    """Raised-cosine fade in over `attack` samples and out over `release` samples."""
    gain = 1.0
    if attack > 0 and i < attack:
        gain *= 0.5 - 0.5 * math.cos(math.pi * i / attack)
    if release > 0 and i >= n - release:
        gain *= 0.5 - 0.5 * math.cos(math.pi * (n - 1 - i) / release)
    return gain


def saved(rate):
    n = int(rate * 0.070)
    attack = int(rate * 0.002)
    release = int(rate * 0.006)
    octave = db(-12.0)
    samples = []
    for i in range(n):
        t = i / rate
        tone = math.sin(2 * math.pi * 880.0 * t) + octave * math.sin(2 * math.pi * 1760.0 * t)
        decay = math.exp(-t / 0.022)  # exponential decay: about -27 dB by the end
        samples.append(tone / (1.0 + octave) * decay * fade(i, n, attack, release))
    return samples


def goal(rate):
    samples = []
    for frequency in (523.25, 659.25, 783.99):  # C5, E5, G5
        n = int(rate * 0.090)
        attack = int(rate * 0.012)
        release = int(rate * 0.030)
        for i in range(n):
            t = i / rate
            samples.append(math.sin(2 * math.pi * frequency * t) * fade(i, n, attack, release))
    return samples


def captured(rate):
    n = int(rate * 0.050)
    attack = int(rate * 0.004)
    release = int(rate * 0.020)
    return [math.sin(2 * math.pi * 440.0 * i / rate) * fade(i, n, attack, release) for i in range(n)]


def write(name, samples, rate):
    path = os.path.join(OUT_DIR, name)
    frames = b"".join(struct.pack("<h", int(round(max(-1.0, min(1.0, s * PEAK)) * 32767))) for s in samples)
    with wave.open(path, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(rate)
        out.writeframes(frames)
    print("%-13s %5d Hz  %4d ms  %5d bytes" % (name, rate, round(1000 * len(samples) / rate), os.path.getsize(path)))


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    write("saved.wav", saved(RATE), RATE)
    write("goal.wav", goal(GOAL_RATE), GOAL_RATE)
    write("captured.wav", captured(RATE), RATE)


if __name__ == "__main__":
    main()
