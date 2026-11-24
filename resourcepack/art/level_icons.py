"""Network level art: the Levels menu's snake (beads that fill, level gems), the Network Level icon, the coin-pile
multiplier icon, the claim-all gift, and 9x9 chat glyphs (coin, level gem) in the HUD icons' style.
Run from resourcepack/: python3 art/level_icons.py"""
from PIL import Image
import json, math, os

K = (26, 27, 38)                       # outline, as the HUD icons
GOLD = {'L': (255, 236, 170), 'Y': (255, 196, 64), 'D': (204, 140, 30), 'd': (150, 96, 20)}
PURPLE = {'L': (226, 206, 255), 'Y': (178, 102, 255), 'D': (120, 66, 196), 'd': (80, 44, 140)}
GRAY = {'L': (176, 180, 204), 'Y': (118, 122, 148), 'D': (78, 82, 104), 'd': (54, 56, 74)}
OUT = 'FractalLobby/assets/fractal'


def paint(rows, pal, size):
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == 'K':
                img.putpixel((x, y), K + (255,))
            elif ch in pal:
                img.putpixel((x, y), pal[ch] + (255,))
    return img


def outline(img):
    """Dark outline round everything drawn (4-neighbours)."""
    w, h = img.size
    filled = {(x, y) for y in range(h) for x in range(w) if img.getpixel((x, y))[3]}
    for y in range(h):
        for x in range(w):
            if (x, y) not in filled and any((x + a, y + b) in filled for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                img.putpixel((x, y), K + (255,))
    return img


def gem(pal, check=False, sparkle=None):
    """A cut gem (16x16): light top-left facets, dark bottom-right."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    cx, cy = 7.5, 8.0
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            top = 2 <= y <= 5 and abs(dx) <= 2 + (y - 2) * 1.6 + 0.01
            body = 5 <= y <= 13 and abs(dx) <= 7 - (y - 5) * 0.86
            if not (top or body):
                continue
            if y <= 5:
                c = pal['L'] if (y == 2 or dx < -1) else pal['Y']
            elif dx < -2.5 + (y - 6) * 0.35:
                c = pal['Y']
            elif dx > 1.5:
                c = pal['d']
            else:
                c = pal['D']
            if y == 5:
                c = pal['L'] if dx < 0 else pal['Y']
            img.putpixel((x, y), c + (255,))
    outline(img)
    if sparkle:
        for x, y in ((13, 1), (12, 2), (14, 2), (13, 3), (2, 12)):
            img.putpixel((x, y), sparkle + (255,))
    if check:
        g, gd = (96, 230, 120), (40, 140, 70)
        # a bold green tick over the bottom right, outlined
        tick = [(8, 11), (9, 12), (10, 13), (11, 12), (12, 11), (13, 10), (14, 9), (15, 8)]
        cells = set()
        for x, y in tick:
            cells |= {(x, y), (x, y + 1)}
        for x, y in cells:
            for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
                if (x + a, y + b) not in cells and 0 <= x + a < 16 and 0 <= y + b < 16:
                    img.putpixel((x + a, y + b), K + (255,))
        for x, y in cells:
            img.putpixel((x, y), (g if (x, y) in tick else gd) + (255,))
    return img


def bead(fill, glow=False):
    """A round socket on the snake (16x16): empty, or filled bottom-up with purple in quarters; glow = the shine."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    r = 5.2
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d > r + 1:
                continue
            if d > r:
                img.putpixel((x, y), K + (255,))
                continue
            level = (12.7 - y) / 10.4                                   # 0 at the bottom, 1 at the top
            if glow:
                c = (255, 255, 255) if (x - 6) ** 2 + (y - 5) ** 2 < 3 else (232, 214, 255) if d < 3.5 else (200, 160, 255)
            elif level < fill:
                c = PURPLE['L'] if (x - 6) ** 2 + (y - 5) ** 2 < 2.5 else PURPLE['Y'] if x < 9 else PURPLE['D']
            else:
                c = (46, 48, 70) if d < r - 1 else (64, 66, 92)
            img.putpixel((x, y), c + (255,))
    return img


def coins():
    """Two gold coins, one in front of the other (16x16), for the coin multiplier."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    def disc(cx, cy, r):
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - cx, y - cy)
                if d <= r:
                    rim = d > r - 1.3
                    c = GOLD['D'] if rim and (x - cx) + (y - cy) > 0 else GOLD['L'] if rim else GOLD['Y']
                    if not rim and (x - cx) + (y - cy) > 3:
                        c = GOLD['D']
                    img.putpixel((x, y), c + (255,))
    disc(9.5, 5.5, 4.6)
    outline(img)
    disc(6.0, 9.5, 5.4)
    # the front coin's mark: a small star
    for x, y in ((6, 7), (5, 9), (6, 9), (7, 9), (6, 10), (4, 9), (8, 9), (6, 8), (6, 11)):
        img.putpixel((x, y), GOLD['d'] + (255,))
    outline(img)
    return img


def gift():
    """A purple gift with a gold ribbon (16x16), for Claim All."""
    rows = [
        "................",
        "....KK....KK....",
        "...KYYK..KYYK...",
        "....KYYKKYYK....",
        ".KKKKKKKKKKKKKK.",
        ".KLLLLLYYLLLLLK.",
        ".KDDDDDYYDDDDDK.",
        ".KKKKKKKKKKKKKK.",
        "..KPPPPYYPPPPK..",
        "..KPPPPYYPPPpK..",
        "..KPPPPYYPPPpK..",
        "..KPPPPYYPPPpK..",
        "..KPPPPYYPPPpK..",
        "..KppppDDppppK..",
        "..KKKKKKKKKKKK..",
        "................",
    ]
    pal = dict(GOLD)
    pal.update({'L': PURPLE['L'], 'D': PURPLE['D'], 'P': PURPLE['Y'], 'p': PURPLE['d']})
    pal['Y'] = GOLD['Y']
    return paint(rows, pal, 16)


def glyph_coin():
    rows = [
        "..KKKKK..",
        ".KLLYYYK.",
        "KLYYYYYDK",
        "KLYDDDYDK",
        "KYYDYYYDK",
        "KYYDDDYDK",
        "KYYYYYDDK",
        ".KDDDDDK.",
        "..KKKKK..",
    ]
    return paint(rows, GOLD, 9)


def glyph_gem():
    rows = [
        "....K....",
        "...KLK...",
        "..KLYYK..",
        ".KLYYYDK.",
        "KLYYYYDdK",
        ".KYYYDdK.",
        "..KYDdK..",
        "...KdK...",
        "....K....",
    ]
    return paint(rows, PURPLE, 9)


def model(path):
    """An item model + item definition for fractal:<path> (a flat icon)."""
    os.makedirs(os.path.dirname(f'{OUT}/models/item/{path}'), exist_ok=True)
    os.makedirs(os.path.dirname(f'{OUT}/items/{path}'), exist_ok=True)
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"fractal:item/{path}"}}, open(f'{OUT}/models/item/{path}.json', 'w'))
    json.dump({"model": {"type": "minecraft:model", "model": f"fractal:item/{path}"}}, open(f'{OUT}/items/{path}.json', 'w'))


items = {
    'level/node_locked': gem(GRAY),
    'level/node_ready': gem(GOLD, sparkle=(255, 255, 255)),
    'level/node_claimed': gem(PURPLE, check=True),
    'level/bead_0': bead(0), 'level/bead_1': bead(0.25), 'level/bead_2': bead(0.5), 'level/bead_3': bead(0.75), 'level/bead_4': bead(1.01),
    'level/bead_glow': bead(1, glow=True),
    'level/coins': coins(),
    'level/claim': gift(),
    'menu/level': gem(PURPLE, sparkle=(255, 255, 255)),
}
for path, img in items.items():
    os.makedirs(os.path.dirname(f'{OUT}/textures/item/{path}'), exist_ok=True)
    img.save(f'{OUT}/textures/item/{path}.png')
    model(path)

glyph_coin().save(f'{OUT}/textures/gui/hud_coins.png')
glyph_gem().save(f'{OUT}/textures/gui/hud_level.png')
font = f'{OUT}/font/lobby.json'
d = json.load(open(font))
have = {p.get('file') for p in d['providers']}
for name, ch in (('hud_coins', ''), ('hud_level', '')):
    if f'fractal:gui/{name}.png' not in have:
        d['providers'].append({"type": "bitmap", "file": f"fractal:gui/{name}.png", "ascent": 8, "height": 9, "chars": [ch]})
json.dump(d, open(font, 'w'), indent=1)

# a sheet to look at
sheet = Image.new('RGBA', (len(items) * 72 + 200, 80), (60, 62, 80, 255))
for i, img in enumerate(items.values()):
    sheet.alpha_composite(img.resize((64, 64), Image.NEAREST), (i * 72 + 4, 8))
sheet.alpha_composite(glyph_coin().resize((54, 54), Image.NEAREST), (len(items) * 72 + 10, 12))
sheet.alpha_composite(glyph_gem().resize((54, 54), Image.NEAREST), (len(items) * 72 + 80, 12))
sheet.save('/tmp/level_icons.png')
