"""Art for the Levels snake (Hypixel-style: up a column, across, down the next, across).
- links (16x16, animated stripes): the bar between two levels, one per shape the snake uses (sn = from the bottom
  up, ns = down, we = left to right, se = in from the bottom and out to the right, ws = in from the left and out the
  bottom), filled 0..4 quarters along the way it flows: level/link_<shape>_<q>
- badges (16x16): level/node_locked|ready|claimed (a gem) and level/box_locked|ready|claimed (a gift)
- level/coin (the C coin, 16x16), level/recenter
Run from resourcepack/: python3 art/level_snake.py"""
from PIL import Image
import json, math, os

OUT = 'FractalLobby/assets/fractal'
K = (26, 27, 38)
TRACK, TRACK_L = (36, 38, 56), (48, 50, 72)
G1, G2, G3 = (120, 236, 120), (66, 186, 86), (40, 140, 64)      # stripe light, stripe dark, shade
FRAMES, PERIOD = 8, 8

SIDES = {'n': (7.5, -0.5), 's': (7.5, 15.5), 'w': (-0.5, 7.5), 'e': (15.5, 7.5)}
SHAPES = {'sn': ('s', 'n'), 'ns': ('n', 's'), 'we': ('w', 'e'), 'se': ('s', 'e'), 'ws': ('w', 's')}


def along(px, py, a, b):
    """For a pixel: its distance to the path a → centre → b, and how far along the path (0..1) its nearest point is."""
    c = (7.5, 7.5)
    best = None
    pts = [SIDES[a], c, SIDES[b]]
    total = sum(math.dist(pts[i], pts[i + 1]) for i in range(2))
    run = 0
    for i in range(2):
        (x0, y0), (x1, y1) = pts[i], pts[i + 1]
        dx, dy = x1 - x0, y1 - y0
        L = math.hypot(dx, dy)
        t = max(0, min(1, ((px - x0) * dx + (py - y0) * dy) / (L * L)))
        qx, qy = x0 + t * dx, y0 + t * dy
        d = max(abs(px - qx), abs(py - qy))                     # square ends, crisp edges
        if best is None or d < best[0]:
            best = (d, (run + t * L) / total)
        run += L
    return best


def link(shape, quarters):
    a, b = SHAPES[shape]
    frames = []
    for f in range(FRAMES):
        img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        for y in range(16):
            for x in range(16):
                d, t = along(x, y, a, b)
                if d > 3.5:
                    continue
                if d > 2.5:
                    img.putpixel((x, y), K + (255,))
                    continue
                if t <= quarters / 4 + 1e-6 and quarters > 0:
                    # diagonal stripes, moving with the frames along the flow
                    stripe = (x + y - f) % PERIOD < PERIOD / 2
                    c = G1 if stripe else G2
                    if d > 1.5 and (y > 8 if shape in ('we',) else False):
                        c = G3 if not stripe else G2
                else:
                    c = TRACK_L if d <= 0.5 else TRACK
                img.putpixel((x, y), c + (255,))
        frames.append(img)
    return frames


def save_anim(path, frames):
    full = f'{OUT}/textures/item/{path}.png'
    os.makedirs(os.path.dirname(full), exist_ok=True)
    if len(frames) == 1:
        frames[0].save(full)
        return
    strip = Image.new('RGBA', (16, 16 * len(frames)), (0, 0, 0, 0))
    for i, fr in enumerate(frames):
        strip.alpha_composite(fr, (0, 16 * i))
    strip.save(full)
    json.dump({"animation": {"frametime": 2, "interpolate": False}}, open(full + '.mcmeta', 'w'))


def model(path):
    os.makedirs(os.path.dirname(f'{OUT}/models/item/{path}'), exist_ok=True)
    os.makedirs(os.path.dirname(f'{OUT}/items/{path}'), exist_ok=True)
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"fractal:item/{path}"}}, open(f'{OUT}/models/item/{path}.json', 'w'))
    json.dump({"model": {"type": "minecraft:model", "model": f"fractal:item/{path}"}}, open(f'{OUT}/items/{path}.json', 'w'))


# ---------------------------------------------------------------- badges

RIMS = {'locked': ((92, 96, 120), (60, 62, 82), (40, 42, 58)),
        'ready': ((255, 224, 120), (230, 168, 50), (160, 104, 24)),
        'claimed': ((200, 160, 255), (150, 98, 230), (96, 58, 160))}
INNER = {'locked': (44, 46, 64), 'ready': (70, 52, 30), 'claimed': (52, 36, 84)}
GEM = {'locked': {'L': (150, 154, 176), 'Y': (110, 114, 138), 'D': (80, 84, 104), 'd': (60, 62, 80)},
       'ready': {'L': (226, 206, 255), 'Y': (178, 102, 255), 'D': (120, 66, 196), 'd': (80, 44, 140)},
       'claimed': {'L': (226, 206, 255), 'Y': (178, 102, 255), 'D': (120, 66, 196), 'd': (80, 44, 140)}}
GEM9 = ["....K....", "...KLK...", "..KLYYK..", ".KLYYYDK.", "KLYYYYDdK", ".KYYYDdK.", "..KYDdK..", "...KdK...", "....K...."]
GIFT9 = ["..KK.KK..", ".KRRKRRK.", "KKKKKKKKK", "KLLLRLLDK", "KYYYRYYDK", "KYYYRYYDK", "KYYYRYYDK", "KDDDdDDDK", "KKKKKKKKK"]
GIFT = {'locked': {'L': (150, 154, 176), 'Y': (110, 114, 138), 'D': (80, 84, 104), 'R': (130, 120, 160), 'd': (90, 84, 120)},
        'ready': {'L': (255, 236, 170), 'Y': (255, 196, 64), 'D': (204, 140, 30), 'R': (150, 98, 230), 'd': (104, 64, 176)},
        'claimed': {'L': (255, 236, 170), 'Y': (255, 196, 64), 'D': (204, 140, 30), 'R': (150, 98, 230), 'd': (104, 64, 176)}}


def badge(state, art, pal):
    """A round badge: a bevelled rim in the state's colour, a dark inner disc, the 9x9 art in the middle."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    light, mid, dark = RIMS[state]
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d > 7.6:
                continue
            if d > 6.7:
                c = K
            elif d > 5.2:
                c = light if (x + y) < 13 else dark if (x + y) > 17 else mid
            else:
                c = INNER[state]
                if d > 4.4 and (x + y) > 15:
                    c = tuple(max(0, v - 14) for v in c)
            img.putpixel((x, y), c + (255,))
    for y, row in enumerate(art):
        for x, ch in enumerate(row):
            if ch == '.':
                continue
            img.putpixel((x + 3, y + 3), (K if ch == 'K' else pal[ch]) + (255,))
    if state == 'ready':
        for x, y in ((13, 1), (12, 2), (14, 2), (13, 3), (2, 13)):
            img.putpixel((x, y), (255, 255, 255, 255))
    if state == 'claimed':
        tick = [(9, 12), (10, 13), (11, 12), (12, 11), (13, 10), (14, 9)]
        cells = set()
        for x, y in tick:
            cells |= {(x, y), (x, y + 1)}
        for x, y in list(cells):
            for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
                if (x + a, y + b) not in cells and 0 <= x + a < 16 and 0 <= y + b < 16:
                    img.putpixel((x + a, y + b), K + (255,))
        for x, y in cells:
            img.putpixel((x, y), ((110, 240, 120) if (x, y) in tick else (50, 160, 80)) + (255,))
    return img


def coin():
    """The C coin, 16x16 (the chat glyph's design, bigger)."""
    gold = {'L': (255, 236, 170), 'Y': (255, 196, 64), 'D': (204, 140, 30), 'd': (150, 96, 20)}
    rows = [
        "................",
        ".....KKKKKK.....",
        "...KKLLLYYYKK...",
        "..KLLYYYYYYYDK..",
        "..KLYYDDDDYYDK..",
        ".KLYYDDDDDDYYDK.",
        ".KLYDDYYYYYYYDK.",
        ".KYYDDYYYYYYYDK.",
        ".KYYDDYYYYYYYDK.",
        ".KYYDDYYYYYYYDK.",
        ".KYYYDDDDDDYYDK.",
        "..KYYYDDDDYYDK..",
        "..KDYYYYYYYDDK..",
        "...KKDDDDDDKK...",
        ".....KKKKKK.....",
        "................",
    ]
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == 'K':
                img.putpixel((x, y), K + (255,))
            elif ch in gold:
                img.putpixel((x, y), gold[ch] + (255,))
    return img


def recenter():
    """A crosshair on a purple disc: back to your level."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if 4.6 < d <= 5.8:
                img.putpixel((x, y), (178, 102, 255, 255))
            elif 5.8 < d <= 6.8 or 3.6 < d <= 4.6:
                img.putpixel((x, y), K + (255,))
            elif d <= 1.6:
                img.putpixel((x, y), (255, 255, 255, 255))
    for i in range(0, 4):
        for x, y in ((7 + 0, i), (8, i), (7, 15 - i), (8, 15 - i), (i, 7), (i, 8), (15 - i, 7), (15 - i, 8)):
            img.putpixel((x, y), (226, 206, 255, 255) if i in (1, 2) else K + (255,))
    return img


made = {}
for shape in SHAPES:
    for q in range(5):
        path = f'level/link_{shape}_{q}'
        frames = link(shape, q)
        save_anim(path, frames if q > 0 else frames[:1])
        model(path)
        made[path] = frames[0]
for state in ('locked', 'ready', 'claimed'):
    for kind, art, pal in (('node', GEM9, GEM[state]), ('box', GIFT9, GIFT[state])):
        path = f'level/{kind}_{state}'
        img = badge(state, art, pal)
        save_anim(path, [img])
        model(path)
        made[path] = img
for path, img in (('level/coin', coin()), ('level/recenter', recenter())):
    save_anim(path, [img])
    model(path)
    made[path] = img

# a sheet to look at: every piece, and a little snake
sheet = Image.new('RGBA', (16 * 12 * 4, 16 * 5 * 4), (60, 62, 80, 255))
for i, (path, img) in enumerate(made.items()):
    sheet.alpha_composite(img.resize((48, 48), Image.NEAREST), ((i % 12) * 64 + 8, (i // 12) * 64 + 8))
sheet.save('/tmp/snake_sheet.png')


def chevrons(right):
    """« or »: scroll the snake (purple, outlined)."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    pts = set()
    for start in (3, 8):
        for k in range(5):
            for w in range(2):
                x = start + k + w if right else 15 - (start + k + w)
                pts |= {(x, 7 - (4 - k) + 4 - 4 + 0), }
    pts = set()
    for start in (2, 8):
        for i in range(-4, 5):
            for w in range(3):
                x = start + (4 - abs(i)) + w
                if right:
                    pts.add((x, 7 + i))
                else:
                    pts.add((15 - x, 7 + i))
    for x, y in pts:
        for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + a, y + b) not in pts and 0 <= x + a < 16 and 0 <= y + b < 16:
                img.putpixel((x + a, y + b), K + (255,))
    for x, y in pts:
        if 0 <= x < 16 and 0 <= y < 16:
            light = (x + y) % 2 == 0 and y < 7
            img.putpixel((x, y), ((226, 206, 255) if y < 6 else (178, 102, 255) if y < 10 else (120, 66, 196)) + (255,))
    return img


for name, img in (('level/scroll_left', chevrons(False)), ('level/scroll_right', chevrons(True))):
    save_anim(name, [img])
    model(name)
    img.resize((96, 96), Image.NEAREST).save(f'/tmp/{name.split("/")[1]}.png')
