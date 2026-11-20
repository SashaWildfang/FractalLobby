"""Makes the piñata's sounds, from scratch (needs numpy, scipy and ffmpeg with libvorbis):
  sounds/pinata_whack_1..3.ogg  a bat hitting the piñata: a hollow wooden THOCK on a papier-mache drum, a papery
                                crunch and a rattle of candy inside
  sounds/pinata_burst.ogg       it bursting open: a big cardboard crack and rip, then candy pattering down
  sounds/bat_swing.ogg          a swish of the bat through the air"""
import os, subprocess, wave
import numpy as np
from scipy.signal import butter, sosfilt

HERE = os.path.dirname(os.path.abspath(__file__))
sr = 44100


def band(x, lo, hi, order=2):
    return sosfilt(butter(order, [lo, hi], btype='band', fs=sr, output='sos'), x)


def low(x, hz):
    return sosfilt(butter(2, hz, btype='low', fs=sr, output='sos'), x)


def env(n, attack, decay):
    t = np.arange(n) / sr
    return np.minimum(1, t / max(attack, 1e-4)) * np.exp(-t / decay)


def tone(f, n):
    return np.sin(2 * np.pi * np.cumsum(np.full(n, f) if np.isscalar(f) else f) / sr)


def finish(out, drive=1.6):
    out = np.tanh(out * drive) / np.tanh(drive)
    out *= np.minimum(1, (len(out) - np.arange(len(out))) / (sr * 0.03))
    return out / np.max(np.abs(out)) * 0.92


def candy(out, rng, start, end, count, loud):
    for at in sorted(rng.uniform(start, end, count)):
        k = int(sr * 0.025); i = int(at * sr)
        if i + k > len(out): continue
        click = band(rng.normal(0, 1, k), rng.uniform(2500, 4000), rng.uniform(5000, 8000)) * env(k, 0.0003, 0.004)
        out[i:i + k] += click * loud * rng.uniform(0.5, 1)


def whack(seed, body):
    rng = np.random.default_rng(seed)
    n = int(sr * 0.45); out = np.zeros(n); t = np.arange(n) / sr
    # the wood: a sharp knock with two short wooden partials
    k = int(sr * 0.08)
    knock = (tone(body * 4.1, k) * 0.6 + tone(body * 6.7, k) * 0.35) * env(k, 0.0004, 0.012)
    out[:k] += knock * 0.9
    # the hollow drum of the papier-mache body, dropping slightly in pitch
    f = body * (1 + 0.25 * np.exp(-t / 0.02))
    out += tone(f, n) * env(n, 0.001, 0.07) * 0.9
    out += band(rng.normal(0, 1, n), body * 1.5, body * 4) * env(n, 0.001, 0.05) * 0.6
    # paper crunch: crackly noise bursts
    for at in rng.uniform(0.005, 0.09, 14):
        kk = int(sr * 0.012); i = int(at * sr)
        out[i:i + kk] += band(rng.normal(0, 1, kk), 1500, 6000) * env(kk, 0.0005, 0.004) * rng.uniform(0.3, 0.7)
    candy(out, rng, 0.05, 0.3, 10, 0.35)                    # the candy rattling inside
    return finish(out)


def burst():
    rng = np.random.default_rng(99)
    n = int(sr * 1.6); out = np.zeros(n); t = np.arange(n) / sr
    k = int(sr * 0.25)                                       # the big crack
    out[:k] += band(rng.normal(0, 1, k), 300, 5000) * env(k, 0.0005, 0.05) * 1.4
    out += tone(90 * (1 + 0.5 * np.exp(-t / 0.03)), n) * env(n, 0.001, 0.1) * 1.0
    for at in rng.uniform(0.02, 0.35, 40):                   # cardboard ripping
        kk = int(sr * 0.02); i = int(at * sr)
        out[i:i + kk] += band(rng.normal(0, 1, kk), 800, 4500) * env(kk, 0.001, 0.008) * rng.uniform(0.2, 0.6)
    candy(out, rng, 0.15, 1.45, 70, 0.45)                    # candy pattering down all round
    return finish(out, 1.4)


def swing():
    rng = np.random.default_rng(5)
    n = int(sr * 0.3); t = np.arange(n) / sr
    shape = np.sin(np.pi * np.clip(t / 0.25, 0, 1)) ** 2
    out = np.zeros(n)
    for lo, hi in ((400, 900), (700, 1500), (1100, 2300)):
        out += band(rng.normal(0, 1, n), lo, hi) * shape
    return finish(out * 0.6, 1.2) * 0.6


def save(name, data):
    tmp = f'{HERE}/sounds/{name}.wav'
    with wave.open(tmp, 'w') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr)
        w.writeframes((data * 32767).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', tmp, '-c:a', 'libvorbis', '-q:a', '6', '-ac', '1',
                    f'{HERE}/sounds/{name}.ogg'], check=True)
    os.remove(tmp)


if __name__ == '__main__':
    os.makedirs(f'{HERE}/sounds', exist_ok=True)
    for i, (seed, body) in enumerate([(3, 150), (8, 135), (14, 165)], start=1):
        save(f'pinata_whack_{i}', whack(seed, body))
    save('pinata_burst', burst())
    save('bat_swing', swing())
    print('made the piñata sounds')
