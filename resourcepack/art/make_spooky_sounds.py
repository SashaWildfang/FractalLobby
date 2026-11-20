"""The ghost's and the pumpkin rain's sounds, made from scratch (numpy, scipy, ffmpeg with libvorbis):
  sounds/ghost_woo.ogg        a soft, breathy, wavering "woooOOOooo" that glides up and back down
  sounds/ghost_boo.ogg        a quick airy "BOO!" swell
  sounds/pumpkin_thud_1..3    a hollow, woody pumpkin thump with a little wobble
  sounds/pumpkin_sting.ogg    a spooky little organ-like arpeggio (minor, with a tritone) to start the rain"""
import numpy as np
from make_pinata_sounds import band, low, env, tone, finish, save, sr


def woo():
    n = int(sr * 1.8); t = np.arange(n) / sr
    glide = 330 + 120 * np.sin(np.pi * t / 1.8) ** 2               # up and back down
    vib = 1 + 0.025 * np.sin(2 * np.pi * 5.5 * t)
    f = glide * vib
    voice = tone(f, n) * 0.6 + tone(f * 2, n) * 0.18 + tone(f * 3, n) * 0.06
    breath = band(np.random.default_rng(4).normal(0, 1, n), 400, 1800) * 0.25
    shape = np.sin(np.pi * np.clip(t / 1.8, 0, 1)) ** 1.5
    return finish((voice + breath) * shape, 1.1) * 0.8


def boo():
    n = int(sr * 0.5); t = np.arange(n) / sr
    f = 180 + 60 * np.exp(-t / 0.08)
    voice = tone(f, n) * 0.7 + tone(f * 2.02, n) * 0.25
    breath = band(np.random.default_rng(9).normal(0, 1, n), 300, 2500) * 0.5
    return finish((voice + breath) * env(n, 0.03, 0.18), 1.6)


def thud(seed, body):
    rng = np.random.default_rng(seed)
    n = int(sr * 0.4); t = np.arange(n) / sr
    f = body * (1 + 0.4 * np.exp(-t / 0.015))
    out = tone(f, n) * env(n, 0.001, 0.09) * 1.0
    out += tone(f * 1.5, n) * env(n, 0.001, 0.04) * 0.3
    k = int(sr * 0.03)
    out[:k] += band(rng.normal(0, 1, k), 200, 2500) * env(k, 0.0005, 0.008) * 0.8   # the knock
    out *= 1 + 0.15 * np.sin(2 * np.pi * 9 * t)                    # a little wobble
    return finish(low(out, 3000), 1.5)


def sting():
    n = int(sr * 2.2); out = np.zeros(n)
    notes = [220.0, 261.6, 311.1, 440.0, 415.3]                     # A, C, D#, A, G#: minor with a tritone, spooky
    for i, f in enumerate(notes):
        start = int(sr * 0.16 * i)
        m = n - start; t = np.arange(m) / sr
        organ = tone(f, m) * 0.5 + tone(f * 2, m) * 0.25 + tone(f * 3, m) * 0.12 + tone(f * 0.5, m) * 0.2
        out[start:] += organ * env(m, 0.02, 0.9) * (1 + 0.04 * np.sin(2 * np.pi * 6 * t))
    return finish(out * 0.5, 1.2) * 0.8


def caw(seed, base, length):
    """A crow's harsh "CAAW": a buzzy, slightly falling voice with rough jitter, pushed through two nasal formants."""
    rng = np.random.default_rng(seed)
    n = int(sr * length); t = np.arange(n) / sr
    f = base * (1.08 - 0.18 * t / length) * (1 + 0.03 * np.sin(2 * np.pi * 31 * t)) * (1 + 0.02 * rng.normal(0, 1, n).cumsum() / np.sqrt(n))
    ph = np.cumsum(f) / sr
    saw = 2 * (ph % 1) - 1                                          # buzzy source
    voice = band(saw, 900, 1500) * 1.2 + band(saw, 2000, 3000) * 0.6 + band(rng.normal(0, 1, n), 1000, 3200) * 0.35
    shape = np.minimum(1, t / 0.02) * np.exp(-np.maximum(0, t - length * 0.5) / (length * 0.25))
    return finish(voice * shape, 2.2) * 0.85


if __name__ == '__main__':
    for i, (seed, base, length) in enumerate([(3, 420, 0.32), (8, 380, 0.38), (14, 460, 0.28)], start=1):
        save(f'crow_caw_{i}', caw(seed, base, length))
    save('ghost_woo', woo())
    save('ghost_boo', boo())
    for i, (seed, body) in enumerate([(2, 95), (6, 110), (11, 82)], start=1):
        save(f'pumpkin_thud_{i}', thud(seed, body))
    save('pumpkin_sting', sting())
    print('made the spooky sounds')
