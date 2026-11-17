"""
Blocky 3D models in the game's own style: every face gets its own pixels at the same density as the model (one
texture pixel per model unit, like vanilla blocks and Blockbench models), with a few shades of each colour and a
little noise, never smooth gradients.

    write(out, 'cloak/part/halo', [Box((3, 7, 3), (13, 8.5, 5), '#ffcf5a', 'metal'), ...])
"""
import json, math, os, random, zlib
from PIL import Image

FACES = ('north', 'south', 'east', 'west', 'up', 'down')


def hexc(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def ramp(base, k):
    """A shade of base, k from -3 (darkest) to +2 (lightest), hue-shifted like pixel art (cool shadows, warm lights)."""
    r, g, b = hexc(base) if isinstance(base, str) else base
    if k >= 0:
        t = k * 0.16
        return tuple(round(v + (255 - v) * t * w) for v, w in zip((r, g, b), (1.0, 0.95, 0.8)))
    t = -k * 0.2
    return (round(r * (1 - t) + 20 * t), round(g * (1 - t) + 14 * t), round(b * (1 - t) + 40 * t))


class Box:
    def __init__(self, f, t, colour, pattern='solid', glow=None, tint=False, rotation=None, faces=FACES, shade=True):
        self.f, self.t, self.colour, self.pattern = list(f), list(t), colour, pattern
        self.glow, self.tint, self.rotation, self.faces, self.shade = glow, tint, rotation, faces, shade


def paint(colour, pattern, w, h, face, seed):
    r = random.Random(seed)
    img = Image.new('RGBA', (w, h))
    for y in range(h):
        for x in range(w):
            k = 0
            n = r.random()
            if pattern == 'solid':
                k = 1 if n > 0.93 else (-1 if n < 0.05 else 0)
            elif pattern == 'fluffy':
                k = 1 if n > 0.72 else (-1 if n < 0.06 else 0)
            elif pattern == 'metal':
                k = 2 if (x + y) % 5 == 0 else (1 if y == 0 else (-1 if y == h - 1 and h > 2 else 0))
            elif pattern == 'glow':
                cx, cy = (w - 1) / 2, (h - 1) / 2
                d = math.hypot((x - cx) / max(1, w / 2), (y - cy) / max(1, h / 2))
                k = 2 if d < 0.35 else (1 if d < 0.75 else 0)
            elif pattern == 'feather':
                k = -1 if x % 3 == 2 else (1 if y == 0 else 0)
                if y == h - 1:
                    k = -1
            elif pattern == 'knit':
                k = 1 if x % 2 == 0 else -1
                if y % 3 == 2:
                    k -= 1
            elif pattern == 'stripes':
                k = 1 if (y // 2) % 2 == 0 else -1
            elif pattern == 'wood':
                k = -1 if (x * 7 + y * 3) % 9 == 0 else (1 if n > 0.85 else 0)
            elif pattern == 'brick':
                k = -2 if y % 4 == 3 or (x + (4 if (y // 4) % 2 else 0)) % 8 == 7 else (1 if n > 0.85 else 0)
            elif pattern == 'mat':
                k = 1 if (x + y) % 2 else -1
            elif pattern == 'flat':
                k = 0
            if face == 'up':
                k += 1
            elif face == 'down':
                k -= 1
            k = max(-3, min(2, k))
            img.putpixel((x, y), ramp(colour, k) + (255,))
    return img


def write(out, path, boxes, scale=1.0, display=None, tints=False, particle=None):
    """Writes models/item/<path>.json, textures/item/<path>.png and items/<path>.json. scale: model units per texel."""
    regions = []   # (box index, face, w, h)
    for i, b in enumerate(boxes):
        dx, dy, dz = (abs(b.t[k] - b.f[k]) for k in range(3))
        size = {'north': (dx, dy), 'south': (dx, dy), 'east': (dz, dy), 'west': (dz, dy), 'up': (dx, dz), 'down': (dx, dz)}
        for face in b.faces:
            w, h = size[face]
            regions.append((i, face, max(1, round(w / scale)), max(1, round(h / scale))))
    # shelf packing
    side = 16
    while True:
        placed, x, y, row = {}, 0, 0, 0
        ok = True
        for (i, face, w, h) in sorted(regions, key=lambda r: -r[3]):
            if x + w > side:
                x, y, row = 0, y + row, 0
            if y + h > side or w > side:
                ok = False
                break
            placed[(i, face)] = (x, y, w, h)
            x += w
            row = max(row, h)
        if ok:
            break
        side *= 2
    tex = Image.new('RGBA', (side, side), (0, 0, 0, 0))
    elements = []
    for i, b in enumerate(boxes):
        el = {"from": [round(v, 4) for v in b.f], "to": [round(v, 4) for v in b.t], "faces": {}}
        if b.rotation:
            el["rotation"] = b.rotation
        if b.glow:
            el["light_emission"] = b.glow
        if not b.shade:
            el["shade"] = False
        for face in b.faces:
            x, y, w, h = placed[(i, face)]
            tex.paste(paint(b.colour, b.pattern, w, h, face, zlib.crc32(f'{path}/{i}/{face}'.encode())), (x, y))
            k = 16 / side
            el["faces"][face] = {"uv": [x * k, y * k, (x + w) * k, (y + h) * k], "texture": "#t"}
            if b.tint:
                el["faces"][face]["tintindex"] = 0
        elements.append(el)
    for d in ('textures/item', 'models/item', 'items'):
        os.makedirs(os.path.dirname(f'{out}/assets/fractal/{d}/{path}'), exist_ok=True)
    tex.save(f'{out}/assets/fractal/textures/item/{path}.png')
    model = {"textures": {"t": f"fractal:item/{path}", "particle": particle or f"fractal:item/{path}"}, "elements": elements}
    if display:
        model["display"] = display
    json.dump(model, open(f'{out}/assets/fractal/models/item/{path}.json', 'w'))
    item = {"type": "minecraft:model", "model": f"fractal:item/{path}"}
    if tints:
        item["tints"] = [{"type": "minecraft:dye", "default": -1}]
    json.dump({"model": item}, open(f'{out}/assets/fractal/items/{path}.json', 'w'))
    return tex
