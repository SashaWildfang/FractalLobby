"""Makes the paintball's SPLAT (sounds/paint_splat_1..3.ogg, the game picks one at random): a wet slap, a squelchy
low "blorp" and little droplets pattering down after. Needs numpy and ffmpeg (with libvorbis)."""
import os, subprocess, wave
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sr = 44100


def lowpass(x, a):
    y = np.zeros_like(x); acc = 0.0
    for i, v in enumerate(x):
        acc += a * (v - acc); y[i] = acc
    return y


def splat(seed, body_hz):
    rng = np.random.default_rng(seed)
    n = int(sr * 0.55)
    out = np.zeros(n)
    k = int(sr * 0.05)                                   # the slap: a burst of noise, darkening fast
    noise = rng.standard_normal(k)
    slap = lowpass(noise, 0.5) * np.exp(-np.arange(k) / (sr * 0.008))
    slap2 = lowpass(noise, 0.12) * np.exp(-np.arange(k) / (sr * 0.02))
    out[:k] += slap * 0.8 + slap2 * 1.4
    b = int(sr * 0.14); tt = np.arange(b) / sr           # the squelch: a wobbling low tone sliding down
    f = body_hz * (1 + 0.6 * np.exp(-tt / 0.03)) * (1 + 0.08 * np.sin(2 * np.pi * 38 * tt))
    out[int(sr * 0.004):int(sr * 0.004) + b] += np.sin(2 * np.pi * np.cumsum(f) / sr) * np.exp(-tt / 0.04) * 0.7
    for _ in range(14):                                   # droplets: tiny bright plips
        at = int(sr * (0.06 + rng.exponential(0.09)))
        if at >= n - 600: continue
        L = int(sr * 0.012); tt = np.arange(L) / sr
        fd = rng.uniform(1400, 2600) * (1 + 0.5 * np.exp(-tt / 0.003))
        out[at:at + L] += np.sin(2 * np.pi * np.cumsum(fd) / sr) * np.exp(-tt / 0.003) * 0.18 * np.exp(-(at / sr) / 0.2)
    out *= np.minimum(1, (n - np.arange(n)) / (sr * 0.04))
    return out / np.max(np.abs(out)) * 0.89


os.makedirs(f'{HERE}/sounds', exist_ok=True)
for i, (seed, hz) in enumerate([(11, 150), (23, 175), (37, 130)], start=1):
    tmp = f'{HERE}/sounds/paint_splat_{i}.wav'
    with wave.open(tmp, 'w') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr); w.writeframes((splat(seed, hz) * 32767).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', tmp, '-c:a', 'libvorbis', '-q:a', '5', '-ac', '1',
                    f'{HERE}/sounds/paint_splat_{i}.ogg'], check=True)
    os.remove(tmp)
print('ok')
