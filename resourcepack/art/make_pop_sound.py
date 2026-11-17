"""Makes the party popper's POP (sounds/party_popper.ogg): a sharp crack, a cork-like "pok", a soft thump, the whoosh
of paper leaving and a rustle of confetti fading out. Needs numpy and ffmpeg (with libvorbis). Run it again to remake."""
import os, subprocess, wave
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sr = 44100
n = int(sr * 0.75)
rng = np.random.default_rng(7)
out = np.zeros(n)


def lowpass(x, a):
    y = np.zeros_like(x); acc = 0.0
    for i, v in enumerate(x):
        acc += a * (v - acc); y[i] = acc
    return y


k = int(sr * 0.004)                                      # the crack
crack = rng.standard_normal(k) * np.linspace(1, 0, k) ** 2
out[:k] += (crack - lowpass(crack, 0.3)) * 0.9
pk = int(sr * 0.06); tt = np.arange(pk) / sr             # the pok: 1500 -> 300 Hz
f = 300 + 1200 * np.exp(-tt / 0.012)
out[:pk] += np.sin(2 * np.pi * np.cumsum(f) / sr) * np.exp(-tt / 0.018) * 0.75
th = int(sr * 0.09); tt = np.arange(th) / sr             # the thump
out[:th] += np.sin(2 * np.pi * 110 * tt) * np.exp(-tt / 0.025) * 0.45
wk = int(sr * 0.18); noise = rng.standard_normal(wk)      # the whoosh
band = lowpass(noise, 0.25) - lowpass(noise, 0.03)
env = np.sin(np.linspace(0, np.pi, wk)) ** 1.5 * np.exp(-np.linspace(0, 2, wk))
out[int(sr * 0.01):int(sr * 0.01) + wk] += band * env * 0.9
for _ in range(90):                                       # the confetti rustle
    at = int(sr * (0.05 + rng.exponential(0.16)))
    if at >= n - 200: continue
    L = int(rng.integers(40, 140))
    tick = rng.standard_normal(L) * np.exp(-np.arange(L) / (L / 4))
    out[at:at + L] += (tick - lowpass(tick, 0.2)) * 0.35 * np.exp(-(at / sr) / 0.28)
out *= np.minimum(1, (n - np.arange(n)) / (sr * 0.05))
out = out / np.max(np.abs(out)) * 0.89

os.makedirs(f'{HERE}/sounds', exist_ok=True)
tmp = f'{HERE}/sounds/party_popper.wav'
with wave.open(tmp, 'w') as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr); w.writeframes((out * 32767).astype(np.int16).tobytes())
subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', tmp, '-c:a', 'libvorbis', '-q:a', '5', '-ac', '1',
                f'{HERE}/sounds/party_popper.ogg'], check=True)
os.remove(tmp)
print('rms pop', np.sqrt(np.mean(out[:int(sr * .06)] ** 2)), 'rms tail', np.sqrt(np.mean(out[int(sr * .1):] ** 2)))
