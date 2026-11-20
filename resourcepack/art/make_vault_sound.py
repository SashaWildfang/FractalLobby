"""
Lobby resource pack - built by Sasha Wildfang for Fractal Network.

Makes the Mystery Vault's right-click sound (fractal:mystery.vault_open): the clunk of a heavy latch, a short
creak of the lid, then a bright rising chime (four bell notes) with a shimmer of sparkles fading out.
Needs numpy and soundfile.

    python make_vault_sound.py <FractalLobby folder>
"""
import json
import sys
from pathlib import Path

import numpy as np
import soundfile as sf

SR = 44100


def lowpass(x, a):
    y = np.zeros_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc += a * (v - acc)
        y[i] = acc
    return y


def bell(freq, length, decay):
    t = np.arange(int(SR * length)) / SR
    tone = (np.sin(2 * np.pi * freq * t) + 0.45 * np.sin(2 * np.pi * freq * 2.0 * t)
            + 0.22 * np.sin(2 * np.pi * freq * 3.01 * t) + 0.12 * np.sin(2 * np.pi * freq * 4.2 * t))
    attack = np.minimum(1, t / 0.004)
    return tone * attack * np.exp(-t / decay)


def make():
    rng = np.random.default_rng(11)
    n = int(SR * 1.15)
    out = np.zeros(n)
    # the latch: a low clunk with a click on top
    k = int(SR * 0.12)
    t = np.arange(k) / SR
    out[:k] += np.sin(2 * np.pi * (140 - 60 * t / 0.12) * t) * np.exp(-t / 0.03) * 0.8
    click = rng.standard_normal(int(SR * 0.006)) * np.linspace(1, 0, int(SR * 0.006)) ** 2
    out[:len(click)] += (click - lowpass(click, 0.25)) * 0.6
    # the lid: a short, soft creak (a wobbling filtered buzz)
    start, length = int(SR * 0.05), int(SR * 0.22)
    t = np.arange(length) / SR
    saw = ((t * (95 + 25 * np.sin(2 * np.pi * 9 * t))) % 1.0) * 2 - 1
    creak = lowpass(saw, 0.08) * np.sin(np.linspace(0, np.pi, length)) ** 2
    out[start:start + length] += creak * 0.35
    # the chime: G5, B5, D6, G6, each a little later
    for i, freq in enumerate((783.99, 987.77, 1174.66, 1567.98)):
        at = int(SR * (0.18 + i * 0.075))
        tone = bell(freq, 0.9, 0.32 - i * 0.03) * (0.38 - i * 0.04)
        end = min(n, at + len(tone))
        out[at:end] += tone[:end - at]
    # sparkles: tiny high pings scattered after the chime
    for _ in range(26):
        at = int(SR * (0.3 + rng.exponential(0.18)))
        if at >= n - 2000:
            continue
        ping = bell(rng.uniform(2600, 4200), 0.08, 0.02) * rng.uniform(0.05, 0.12)
        end = min(n, at + len(ping))
        out[at:end] += ping[:end - at]
    out *= np.minimum(1, (n - np.arange(n)) / (SR * 0.08))
    return out / np.max(np.abs(out)) * 0.9


def main():
    pack = Path(sys.argv[1]) / "assets" / "fractal"
    (pack / "sounds" / "mystery").mkdir(parents=True, exist_ok=True)
    sf.write(pack / "sounds" / "mystery" / "vault_open.ogg", make().astype(np.float32), SR, format="OGG", subtype="VORBIS")
    events_file = pack / "sounds.json"
    events = json.loads(events_file.read_text(encoding="utf-8")) if events_file.exists() else {}
    events["mystery.vault_open"] = {"sounds": [{"name": "fractal:mystery/vault_open"}]}
    events_file.write_text(json.dumps(events, indent=1), encoding="utf-8")
    print("Made fractal:mystery.vault_open")


if __name__ == "__main__":
    main()
