"""Makes the paintball's SPLAT (sounds/paint_splat_1..3.ogg, the game picks one at random): a big cartoon splat, made
from scratch. A heavy wet slap, a meaty low "thwop" dropping in pitch, a gooey squelch that wobbles and slides down,
a couple of slime "bloops" and a few drips at the end, all pushed a little so it hits hard.
Needs numpy, scipy and ffmpeg (with libvorbis). Run it again to remake the files."""
import os, subprocess, wave
import numpy as np
from scipy.signal import butter, sosfilt

HERE = os.path.dirname(os.path.abspath(__file__))
sr = 44100


def band(x, lo, hi, order=2):
    return sosfilt(butter(order, [lo, hi], btype='band', fs=sr, output='sos'), x)


def low(x, hz, order=2):
    return sosfilt(butter(order, hz, btype='low', fs=sr, output='sos'), x)


def sweep(f, seconds):
    """A sine whose pitch follows f (an array, Hz)."""
    return np.sin(2 * np.pi * np.cumsum(f) / sr)


def env(n, attack, decay):
    t = np.arange(n) / sr
    return np.minimum(1, t / max(attack, 1e-4)) * np.exp(-t / decay)


def splat(seed, low_hz, squelch_hz):
    rng = np.random.default_rng(seed)
    n = int(sr * 0.95)
    out = np.zeros(n)

    def add(at, x):
        i = int(at * sr)
        out[i:i + len(x)] += x[:max(0, n - i)]

    # 1. the slap: a thick, wet burst of noise (the "SPL")
    k = int(sr * 0.09)
    slap = band(rng.standard_normal(k), 350, 3200) * env(k, 0.0008, 0.022) * 1.7
    slap += band(rng.standard_normal(k), 120, 700) * env(k, 0.001, 0.04) * 1.4
    add(0, slap)

    # 2. the thwop: a meaty low hit falling in pitch (the weight of a big blob landing)
    k = int(sr * 0.22); t = np.arange(k) / sr
    f = low_hz + (low_hz * 2.4) * np.exp(-t / 0.035)
    add(0.002, sweep(f, 0.22) * env(k, 0.002, 0.06) * 0.65)

    # 3. the squelch (the "AT"): wobbly, gooey noise whose tone slides down as the goo spreads
    k = int(sr * 0.42); t = np.arange(k) / sr
    goo = rng.standard_normal(k)
    wobble = 0.55 + 0.45 * np.sin(2 * np.pi * (28 + 10 * rng.random()) * t + 6 * np.sin(2 * np.pi * 7 * t))
    pieces = []
    for j in range(6):                                   # the filter slides down over the squelch
        a, b = j * k // 6, (j + 1) * k // 6
        centre = squelch_hz * (1.0 - 0.11 * j)
        pieces.append(band(goo[a:b] if j == 0 else goo[a - 400:b], centre * 0.55, centre * 1.6)[0 if j == 0 else 400:])
    squelch = np.concatenate(pieces)[:k] * wobble * env(k, 0.01, 0.12) * 1.6
    add(0.015, squelch)

    # 4. slime bloops: little bubbles of goo popping, rising in pitch
    for at in sorted(rng.uniform(0.12, 0.42, 3)):
        k = int(sr * 0.05); t = np.arange(k) / sr
        f = rng.uniform(170, 300) * (1 + 1.4 * t / 0.05)
        add(at, sweep(f, 0.05) * env(k, 0.003, 0.016) * 0.25)

    # 5. drips: a few small plips as the last of it drips off
    for at in sorted(rng.uniform(0.45, 0.85, 4)):
        k = int(sr * 0.03); t = np.arange(k) / sr
        f = rng.uniform(900, 1500) * (1 + 0.8 * np.exp(-t / 0.004))
        add(at, sweep(f, 0.03) * env(k, 0.0005, 0.006) * 0.16)

    out = low(out, 9000)
    out = np.tanh(out * 1.8) / np.tanh(1.8)               # pushed a little: punchy, cartoon-loud
    out *= np.minimum(1, (n - np.arange(n)) / (sr * 0.05))
    return out / np.max(np.abs(out)) * 0.92


if __name__ == '__main__':
    os.makedirs(f'{HERE}/sounds', exist_ok=True)
    for i, (seed, lo, sq) in enumerate([(11, 62, 900), (23, 72, 1050), (37, 55, 800)], start=1):
        tmp = f'{HERE}/sounds/paint_splat_{i}.wav'
        with wave.open(tmp, 'w') as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr)
            w.writeframes((splat(seed, lo, sq) * 32767).astype(np.int16).tobytes())
        subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', tmp, '-c:a', 'libvorbis', '-q:a', '6', '-ac', '1',
                        f'{HERE}/sounds/paint_splat_{i}.ogg'], check=True)
        os.remove(tmp)
    print('made 3 splats')
