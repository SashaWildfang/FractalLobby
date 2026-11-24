"""Art for the Levels snake (Hypixel-style: up a column, across, down the next, across).

Links and level badges are 18x18 and their models show them 1.125x in menus, so they fill the whole slot (16 px plus
the 1 px border either side): the bars run edge to edge with no gap, one texture pixel per screen pixel.
- links: level/link_<shape>_<0..4> (shape = how the bar runs: sn up, ns down, we right, se up-then-right,
  ws right-then-down), filled in quarters the way it flows; green stripes that travel along the bar (.mcmeta).
- badges: level/node_<state> (a gem) and level/box_<state> (a gift), state locked / ready / claimed.
- 16x16: level/coin, level/recenter, level/scroll_left|right (+ _off, greyed), level/claim, level/chest.
Run from resourcepack/: python3 art/level_snake.py"""
from PIL import Image
import json, math, os

OUT = 'FractalLobby/assets/fractal'
K = (26, 27, 38)
N = 18                                                           # links and badges
# PERIOD divides 36 (two squares: from one bar to the next along the snake), so every bar's stripes line up
# with the last one's, as one stripe running the whole snake; a pixel a frame.
FRAMES, PERIOD = 12, 12
GREEN = [(150, 248, 140), (104, 222, 104), (64, 178, 82), (42, 132, 62)]   # light, stripe, base, shade
TRACK, TRACK_L = (34, 36, 54), (46, 48, 70)

MID = (N - 1) / 2                                                # 8.5
EDGE = {'n': (MID, -0.5), 's': (MID, N - 0.5), 'w': (-0.5, MID), 'e': (N - 0.5, MID)}
SHAPES = {'sn': ('s', 'n'), 'ns': ('n', 's'), 'we': ('w', 'e'), 'se': ('s', 'e'), 'ws': ('w', 's')}


def model(path, scale=None):
    os.makedirs(os.path.dirname(f'{OUT}/models/item/{path}'), exist_ok=True)
    os.makedirs(os.path.dirname(f'{OUT}/items/{path}'), exist_ok=True)
    m = {"parent": "minecraft:item/generated", "textures": {"layer0": f"fractal:item/{path}"}}
    if scale:
        m["display"] = {"gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [scale, scale, scale]}}
    json.dump(m, open(f'{OUT}/models/item/{path}.json', 'w'))
    item = {"model": {"type": "minecraft:model", "model": f"fractal:item/{path}"}}
    if scale:
        item["oversized_in_gui"] = True          # since 1.21.6 menus clip items to their slot unless this is set
    json.dump(item, open(f'{OUT}/items/{path}.json', 'w'))


def save(path, frames, size, scale=None):
    full = f'{OUT}/textures/item/{path}.png'
    os.makedirs(os.path.dirname(full), exist_ok=True)
    for old in (full + '.mcmeta',):
        if os.path.exists(old):
            os.remove(old)
    if len(frames) == 1:
        frames[0].save(full)
    else:
        strip = Image.new('RGBA', (size, size * len(frames)), (0, 0, 0, 0))
        for i, fr in enumerate(frames):
            strip.alpha_composite(fr, (0, size * i))
        strip.save(full)
        json.dump({"animation": {"frametime": 2, "interpolate": False}}, open(full + '.mcmeta', 'w'))
    model(path, scale)


# ---------------------------------------------------------------- links

def place(px, py, a, b):
    """Nearest point on the bar's path (edge a → middle → edge b): (distance across, signed side, distance along)."""
    c = (MID, MID)
    pts = [EDGE[a], c, EDGE[b]]
    best, run = None, 0
    for i in range(2):
        (x0, y0), (x1, y1) = pts[i], pts[i + 1]
        dx, dy = x1 - x0, y1 - y0
        L = math.hypot(dx, dy)
        t = max(0, min(1, ((px - x0) * dx + (py - y0) * dy) / (L * L)))
        qx, qy = x0 + t * dx, y0 + t * dy
        across = max(abs(px - qx), abs(py - qy))
        side = ((px - qx) * -dy + (py - qy) * dx) / L          # which side of the line (for the stripes' slant)
        if best is None or across < best[0] - 1e-9:
            best = (across, side, run + t * L)
        run += L
    return best[0], best[1], best[2], run


DIRS = {'n': (0, -1), 's': (0, 1), 'w': (-1, 0), 'e': (1, 0)}


def corner_along(px, py, a, b, s, total):
    """For a bend: distance along it that turns smoothly round the corner (a diagonal edge, no notch)."""
    if a in ('n', 's') and b in ('n', 's') or a in ('w', 'e') and b in ('w', 'e'):
        return s
    ix, iy = -DIRS[a][0], -DIRS[a][1]                            # travelling in from edge a: towards the middle
    ox, oy = DIRS[b]                                             # then out towards edge b
    before = -((px - MID) * ix + (py - MID) * iy)               # > 0 still before the middle
    after = (px - MID) * ox + (py - MID) * oy                    # > 0 past it
    if before > 3:
        return MID + 0.5 - before
    if after > 3:
        return MID + 0.5 + after
    return MID + 0.5 + (after - before) / 2


def link(shape, quarters):
    a, b = SHAPES[shape]
    out = []
    for f in range(FRAMES if quarters else 1):
        img = Image.new('RGBA', (N, N), (0, 0, 0, 0))
        for y in range(N):
            for x in range(N):
                across, side, s, total = place(x, y, a, b)
                s = corner_along(x, y, a, b, s, total)
                if across > 3.5:
                    continue
                if across > 2.5:
                    img.putpixel((x, y), K + (255,))
                    continue
                if quarters and s <= total * quarters / 4 + 0.01:
                    # stripes slanting across the bar, travelling forward along it
                    phase = (s + side - f) % PERIOD
                    c = GREEN[1] if phase < PERIOD / 2 else GREEN[2]
                    if side < -1.5:
                        c = GREEN[0] if phase < PERIOD / 2 else GREEN[1]     # the lit edge
                    elif side > 1.5:
                        c = GREEN[2] if phase < PERIOD / 2 else GREEN[3]     # the shaded edge
                else:
                    c = TRACK_L if side < -1.5 else TRACK
                img.putpixel((x, y), c + (255,))
        out.append(img)
    return out


# ---------------------------------------------------------------- badges (18x18)

RIM = {'locked': ((104, 108, 134), (72, 75, 98), (48, 50, 68)),
       'ready': ((255, 228, 130), (236, 174, 52), (168, 108, 26)),
       'claimed': ((206, 170, 255), (152, 100, 232), (98, 60, 164))}
INNER = {'locked': (40, 42, 60), 'ready': (64, 46, 30), 'claimed': (46, 32, 78)}
PURPLE = {'L': (232, 214, 255), 'Y': (178, 102, 255), 'D': (122, 66, 200), 'd': (82, 44, 142)}
GREY = {'L': (176, 180, 200), 'Y': (124, 128, 150), 'D': (88, 92, 112), 'd': (64, 66, 84)}
GOLDP = {'L': (255, 236, 170), 'Y': (255, 196, 64), 'D': (204, 140, 30), 'd': (150, 96, 20)}
GEM = ["..KKKKKK..",
       ".KLLLYYDK.",
       "KLLYYYYDdK",
       "KDDDDDDddK",
       ".KDYYYYdK.",
       "..KDYYdK..",
       "...KDdK...",
       "....KK...."]
GIFT = ["..KK..KK..",
        ".KRRKKRRK.",
        "KKKKKKKKKK",
        "KLLLRRLLLK",
        "KKKKRRKKKK",
        ".KYYRRYDK.",
        ".KYYRRYDK.",
        ".KYYRRYDK.",
        ".KDDrrDDK.",
        ".KKKKKKKK."]


def disc(img, cx, cy, r, colour):
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            if math.hypot(x - cx, y - cy) <= r:
                img.putpixel((x, y), colour + (255,))


def badge(state, kind):
    img = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    light, mid, dark = RIM[state]
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - MID, y - MID)
            if d > 8.9:
                continue
            if d > 8.0:
                c = K
            elif d > 6.0:
                lean = (x - MID) + (y - MID)
                c = light if lean < -3 else dark if lean > 3 else mid
            elif d > 5.2:
                c = tuple(max(0, v - 10) for v in INNER[state])
            else:
                c = INNER[state]
            img.putpixel((x, y), c + (255,))
    if kind == 'node':
        art = GEM
        pal = {'locked': GREY, 'ready': PURPLE, 'claimed': PURPLE}[state]
    else:
        art = GIFT
        base = {'locked': GREY, 'ready': GOLDP, 'claimed': GOLDP}[state]
        rib = {'locked': ((120, 112, 150), (86, 80, 110)), 'ready': ((168, 108, 248), (110, 64, 190)),
               'claimed': ((168, 108, 248), (110, 64, 190))}[state]
        pal = dict(base, R=rib[0], r=rib[1])
    h, w = len(art), len(art[0])
    ox, oy = (N - w) // 2, (N - h) // 2                         # even sizes: exactly centred
    for y, row in enumerate(art):
        for x, ch in enumerate(row):
            if ch != '.':
                img.putpixel((ox + x, oy + y), (K if ch == 'K' else pal[ch]) + (255,))
    if state == 'ready':
        for x, y in ((14, 2), (13, 3), (15, 3), (14, 4)):
            img.putpixel((x, y), (255, 255, 255, 255))
        img.putpixel((3, 14), (255, 240, 180, 255))
    if state == 'claimed':
        # a green tick badge at the bottom right
        disc(img, 13.5, 13.5, 4.4, K)
        disc(img, 13.5, 13.5, 3.5, (76, 200, 96))
        for x, y in ((11, 13), (12, 14), (13, 15), (14, 14), (15, 13), (16, 12)):
            if 0 <= x < N and 0 <= y < N:
                img.putpixel((x, y), (255, 255, 255, 255))
        for x, y in ((12, 13), (13, 14), (14, 13), (15, 12)):
            img.putpixel((x, y), (220, 255, 220, 255))
    return img


# ---------------------------------------------------------------- 16x16 buttons

def paint16(rows, pal):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == 'K':
                img.putpixel((x, y), K + (255,))
            elif ch in pal:
                img.putpixel((x, y), pal[ch] + (255,))
    return img


def coin():
    return paint16([
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
        "................"], GOLDP)


def chest():
    """A gold-trimmed purple chest with light spilling out: Claim All."""
    pal = {'L': (232, 214, 255), 'Y': (178, 102, 255), 'D': (122, 66, 200), 'd': (82, 44, 142),
           'G': (255, 196, 64), 'g': (204, 140, 30), 'W': (255, 244, 190)}
    return paint16([
        "....W..W..W.....",
        ".....W.W.W......",
        "..KKKKKKKKKKKK..",
        ".KLLLLLGGLLLLLK.",
        ".KYYYYYGGYYYYDK.",
        ".KGGGGGGGGGGGgK.",
        ".KKKKKKWWKKKKKK.",
        ".KYYYYGKKGYYYDK.",
        ".KYYYYGWWGYYYDK.",
        ".KYYYYYGGYYYYDK.",
        ".KYYYYYGGYYYYDK.",
        ".KDDDDDggDDDDdK.",
        ".KGGGGGGGGGGGgK.",
        ".KKKKKKKKKKKKKK.",
        "................",
        "................"], pal)


def recenter():
    """A gold compass with a purple needle: back to your level."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    gold_l, gold, gold_d = (255, 236, 170), (255, 196, 64), (190, 124, 28)
    face, face_d = (238, 232, 250), (196, 188, 220)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d > 7.8:
                continue
            if d > 6.9:
                c = K
            elif d > 5.4:
                lean = (x - 7.5) + (y - 7.5)
                c = gold_l if lean < -3 else gold_d if lean > 3 else gold
            elif d > 4.7:
                c = K
            else:
                c = face if (x - 7.5) + (y - 7.5) < 2 else face_d
            img.putpixel((x, y), c + (255,))
    # tick marks at N E S W
    for x, y in ((7, 3), (8, 3), (12, 7), (12, 8), (7, 12), (8, 12), (3, 7), (3, 8)):
        img.putpixel((x, y), (150, 140, 180, 255))
    # the needle: a diamond from the middle, purple pointing up-right, grey down-left, a gold pin
    P, PL, PD, G, GD = (178, 102, 255), (226, 206, 255), (110, 60, 190), (128, 130, 152), (90, 92, 112)
    for y in range(16):
        for x in range(16):
            u = ((x - 7.5) + (7.5 - y)) / 1.414                  # along the needle (up-right +)
            v = ((x - 7.5) - (7.5 - y)) / 1.414                  # across it
            if abs(u) <= 4.8 and abs(v) <= 2.4 * (1 - abs(u) / 5.4) + 0.5:
                if u > 0:
                    c = PL if v < -0.3 else P if v < 0.6 else PD
                else:
                    c = G if v < 0 else GD
                img.putpixel((x, y), c + (255,))
    for x, y in ((7, 7), (8, 8), (7, 8), (8, 7)):
        img.putpixel((x, y), gold + (255,))
    img.putpixel((7, 7), gold_l + (255,))
    return img


def claim_button(on):
    """Claim All. With rewards waiting: an open treasure chest spilling gold coins and a purple gem, light rising
    out of it. With none: a plain grey circle (nothing to do)."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    if not on:
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if d > 6.9:
                    continue
                c = K if d > 6.0 else (96, 100, 122) if d > 4.6 and (x + y) < 15 else (70, 73, 92) if d > 4.6 else (52, 54, 72)
                img.putpixel((x, y), c + (255,))
        return img
    pal = {'W': (255, 248, 210), 'w': (255, 230, 150), 'G': (255, 196, 64), 'g': (204, 140, 30), 'L': (255, 236, 170),
           'B': (150, 92, 52), 'b': (104, 62, 34), 'P': (178, 102, 255), 'p': (226, 206, 255)}
    rows = [
        "...w...W...w....",
        "....w..W..w.....",
        ".KKKKKKKKKKKKKK.",
        ".KBBBBBBBBBBBbK.",
        ".KGGGGGGGGGGGgK.",
        ".KKKKKKKKKKKKKK.",
        ".KLGpPLGLGKLGgK.",
        "KKGGPPGGGGLGGgKK",
        "KBKKKKKKKKKKKKbK",
        "KBBBBBGGGGBBBBbK",
        "KBBBBBGKKGBBBBbK",
        "KBBBBBGGGGBBBBbK",
        "KbbbbbbggbbbbbbK",
        "KGGGGGGGGGGGGGgK",
        "KKKKKKKKKKKKKKKK",
        "................"]
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == 'K':
                img.putpixel((x, y), K + (255,))
            elif ch in pal:
                img.putpixel((x, y), pal[ch] + (255,))
    return img


def chevrons(right, off=False):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    pts = set()
    for start in (2, 8):
        for i in range(-4, 5):
            for w in range(3):
                x = start + (4 - abs(i)) + w
                pts.add((x if right else 15 - x, 7 + i))
    for x, y in pts:
        for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + a, y + b) not in pts and 0 <= x + a < 16 and 0 <= y + b < 16:
                img.putpixel((x + a, y + b), K + (255,))
    shades = [(120, 124, 148), (92, 96, 118), (70, 72, 92)] if off else [(226, 206, 255), (178, 102, 255), (120, 66, 196)]
    for x, y in pts:
        img.putpixel((x, y), (shades[0] if y < 6 else shades[1] if y < 10 else shades[2]) + (255,))
    return img


SCALE = 1.125
sheet = []
for shape in SHAPES:
    for q in range(5):
        frames = link(shape, q)
        save(f'level/link_{shape}_{q}', frames, N, SCALE)
        sheet.append(frames[0])
for state in ('locked', 'ready', 'claimed'):
    for kind in ('node', 'box'):
        img = badge(state, kind)
        save(f'level/{kind}_{state}', [img], N, SCALE)
        sheet.append(img)
for path, img in (('level/coin', coin()), ('level/recenter', recenter()), ('level/chest', chest()),
                  ('level/claim_on', claim_button(True)), ('level/claim_off', claim_button(False)),
                  ('level/scroll_left', chevrons(False)), ('level/scroll_right', chevrons(True)),
                  ('level/scroll_left_off', chevrons(False, True)), ('level/scroll_right_off', chevrons(True, True))):
    save(path, [img], 16)
    sheet.append(img)

big = Image.new('RGBA', (12 * 80, 4 * 80), (60, 62, 80, 255))
for i, img in enumerate(sheet):
    big.alpha_composite(img.resize((img.size[0] * 4, img.size[1] * 4), Image.NEAREST), ((i % 12) * 80 + 4, (i // 12) * 80 + 4))
big.save('/tmp/snake_sheet.png')
# a few frames of one bar, to check the stripes move along it
anim = Image.new('RGBA', (12 * 80, 80), (60, 62, 80, 255))
for i, fr in enumerate(link('se', 4)):
    anim.alpha_composite(fr.resize((72, 72), Image.NEAREST), (i * 80 + 4, 4))
anim.save('/tmp/snake_anim.png')
