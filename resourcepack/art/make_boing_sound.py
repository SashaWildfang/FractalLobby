"""Makes the trampoline's BOING (sounds/trampoline_boing_1..3.ogg, picked at random): a soft thump of the mat, then a
springy "boi-oi-oing" (a tone that swoops up and wobbles as the springs settle). Needs numpy and ffmpeg (libvorbis)."""
import os, subprocess, wave
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sr = 44100


def boing(seed, base):
    rng = np.random.default_rng(seed)
    n = int(sr * 0.7)
    t = np.arange(n) / sr
    out = np.zeros(n)
    # the mat: a soft low thump
    k = int(sr * 0.12)
    tt = t[:k]
    out[:k] += np.sin(2 * np.pi * (90 + 60 * np.exp(-tt / 0.02)) * tt) * np.exp(-tt / 0.03) * 0.8
    # the spring: swoops up, then wobbles (the wobble slowing and fading) and slides gently down
    start = int(sr * 0.01)
    tt = t[: n - start]
    f = base * (0.6 + 0.6 * (1 - np.exp(-tt / 0.03))) * (1 - 0.12 * tt) * (1 + 0.09 * np.exp(-tt / 0.25) * np.sin(2 * np.pi * (14 - 6 * tt) * tt))
    tone = np.sin(2 * np.pi * np.cumsum(f) / sr) + 0.35 * np.sin(2 * np.pi * 2 * np.cumsum(f) / sr)
    out[start:] += tone * np.minimum(1, tt / 0.008) * np.exp(-tt / 0.18) * 0.7
    out = np.tanh(out * 1.3) / np.tanh(1.3)
    out *= np.minimum(1, (n - np.arange(n)) / (sr * 0.05))
    return out / np.max(np.abs(out)) * 0.85


os.makedirs(f'{HERE}/sounds', exist_ok=True)
for i, (seed, base) in enumerate([(1, 260), (2, 300), (3, 230)], start=1):
    tmp = f'{HERE}/sounds/trampoline_boing_{i}.wav'
    with wave.open(tmp, 'w') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr); w.writeframes((boing(seed, base) * 32767).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', tmp, '-c:a', 'libvorbis', '-q:a', '5', '-ac', '1',
                    f'{HERE}/sounds/trampoline_boing_{i}.ogg'], check=True)
    os.remove(tmp)
print('made 3 boings')
