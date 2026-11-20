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


def _resonate(x, f, bw):
    """A two-pole resonator (a formant) at f Hz with bandwidth bw."""
    from scipy.signal import lfilter
    r = np.exp(-np.pi * bw / sr)
    a1, a2 = -2 * r * np.cos(2 * np.pi * f / sr), r * r
    return lfilter([1 - r], [1, a1, a2], x)


def caw(seed, base, length):
    """A crow's "CAAW": a rough voice made of irregular glottal pulses (jittery spacing, alternating loud and soft
    pulses for the rasp), with a quick rise then a falling pitch, through the crow's throat formants, plus a breathy
    hiss, a hard start and a short tail."""
    rng = np.random.default_rng(seed)
    n = int(sr * length)
    src = np.zeros(n)
    t, k = 0.0, 0
    while True:
        u = t / length
        f0 = base * (1 + 0.12 * np.sin(np.pi * min(u / 0.25, 1) / 2) - 0.28 * max(0, u - 0.25))
        period = sr / f0 * (1 + rng.normal(0, 0.06))                 # jitter
        i = int(t * sr)
        if i >= n - 40:
            break
        amp = (1.0 if k % 2 == 0 else 0.55) * (1 + rng.normal(0, 0.12))   # period doubling + shimmer: the rasp
        w = np.arange(24)
        src[i:i + 24] += amp * np.exp(-w / 5.0) * (1 - w / 24)
        t += period / sr
        k += 1
    breath = rng.normal(0, 1, n) * 0.18
    x = src + breath
    voice = (_resonate(x, 1150, 220) * 1.0 + _resonate(x, 1750, 260) * 0.8 + _resonate(x, 2700, 420) * 0.45
             + _resonate(x, 600, 180) * 0.25)
    tt = np.arange(n) / sr
    shape = np.minimum(1, tt / 0.008) * np.minimum(1, (length - tt) / 0.07)
    shape *= 1 - 0.25 * np.exp(-((tt / length - 0.18) / 0.05) ** 2)  # the little "ca-AW" dip
    return finish(voice * shape / (np.abs(voice).max() + 1e-9), 2.6) * 0.9


def jumpscare():
    """A loud horror stab: a dissonant cluster of rough low and high voices hitting at once, a noise blast, then a
    shuddering tail."""
    rng = np.random.default_rng(21)
    n = int(sr * 1.3); t = np.arange(n) / sr
    out = np.zeros(n)
    for f in (92.5, 98, 138.6, 523, 554, 740, 784):                  # semitone clashes, low and high
        ph = np.cumsum(f * (1 + 0.004 * rng.normal(0, 1, n).cumsum() / np.sqrt(n))) / sr
        saw = 2 * (ph % 1) - 1
        out += saw * (0.9 if f < 200 else 0.45)
    out = low(out, 5000)
    out += band(rng.normal(0, 1, n), 300, 6000) * np.exp(-t / 0.08) * 1.6   # the blast
    out *= np.minimum(1, t / 0.004) * (0.35 + 0.65 * np.exp(-t / 0.35)) * (1 + 0.25 * np.sin(2 * np.pi * 11 * t))
    out *= np.minimum(1, (1.3 - t) / 0.25)
    return finish(out / np.abs(out).max(), 2.2) * 0.95


def scrape(seed):
    """A skate blade carving ice: a bright, airy "shhhk" swelling and cut off, with a faint metallic ring."""
    rng = np.random.default_rng(seed)
    n = int(sr * 0.42); t = np.arange(n) / sr
    hiss = band(rng.normal(0, 1, n), 2500, 9000) * 0.8 + band(rng.normal(0, 1, n), 900, 2500) * 0.3
    shape = np.sin(np.pi * np.clip(t / 0.42, 0, 1)) ** 0.7 * np.minimum(1, (0.42 - t) / 0.03)
    ring = tone(np.full(n, 3150.0), n) * np.exp(-t / 0.12) * 0.06
    return finish(hiss * shape + ring, 1.3) * 0.6


def launch():
    """The pumpkin launcher's THOOMP: a deep punch, a burst of air and a rising whoosh."""
    rng = np.random.default_rng(31)
    n = int(sr * 0.7); t = np.arange(n) / sr
    punch = tone(55 + 70 * np.exp(-t / 0.03), n) * np.exp(-t / 0.12) * 1.2
    burst = band(rng.normal(0, 1, n), 150, 1800) * np.exp(-t / 0.05) * 0.9
    sweep = 400 + 2400 * np.clip(t / 0.5, 0, 1)
    whoosh = np.zeros(n)
    for i in range(0, n, 512):
        seg = rng.normal(0, 1, min(512, n - i))
        whoosh[i:i + len(seg)] = band(seg, sweep[i] * 0.7, sweep[i] * 1.3)
    whoosh *= np.sin(np.pi * np.clip(t / 0.6, 0, 1)) * 0.35
    return finish(punch + burst + whoosh, 1.8) * 0.9


if __name__ == '__main__':
    for i in (1, 2, 3):
        save(f'skate_scrape_{i}', scrape(40 + i))
    save('pumpkin_launch', launch())
    for i, (seed, base, length) in enumerate([(3, 560, 0.34), (8, 520, 0.4), (14, 610, 0.3)], start=1):
        save(f'crow_caw_{i}', caw(seed, base, length))
    save('scarecrow_scare', jumpscare())
    save('ghost_woo', woo())
    save('ghost_boo', boo())
    for i, (seed, body) in enumerate([(2, 95), (6, 110), (11, 82)], start=1):
        save(f'pumpkin_thud_{i}', thud(seed, body))
    save('pumpkin_sting', sting())
    print('made the spooky sounds')
