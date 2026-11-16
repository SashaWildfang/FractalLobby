"""Draws the Lobby realm menu backgrounds in the style of Survival's menus (CrazySurvival pack)."""
from PIL import Image
import json, os, sys, zipfile

MENUS = sys.argv[1]  # .../assets/minecraft/textures/gui/menus
OUT = sys.argv[2]
TEXT = {(255, 255, 255), (212, 212, 212)}
SHADOW = (73, 77, 107)

warps = Image.open(f'{MENUS}/warp/warps.png').convert('RGBA')
survival = Image.open(f'{MENUS}/survivalmenu/main.png').convert('RGBA')
W = 176

def rgb(p): return p[:3]

# -- the letters, cut out of "SURVIVAL MENU"
def letters(img, y0, y1, x0, x1):
    cols = [x for x in range(x0, x1) if any(rgb(img.getpixel((x, y))) in TEXT for y in range(y0, y1))]
    groups, cur = [], []
    for x in cols:
        if cur and x != cur[-1] + 1: groups.append(cur); cur = []
        cur.append(x)
    if cur: groups.append(cur)
    out = []
    for g in groups:
        pix = {(x - g[0], y): img.getpixel((x, y)) for x in g for y in range(y0, y1) if rgb(img.getpixel((x, y))) in TEXT}
        out.append((len(g), pix))
    return out

glyphs = letters(survival, 0, 20, 22, 156)
# S U R V I V A L | M E N U
assert len(glyphs) == 12, len(glyphs)
by = dict(zip("SURVIVALMENU", glyphs))

def drawn(rows):  # noqa: uses TOP, set below
    pix = {}
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c == '#': pix[(x, TOP + y)] = (255, 255, 255, 255) if y < 2 else (212, 212, 212, 255)
    return (len(rows[0]), pix)
gap = 1
top_y = min(y for _, p in glyphs for (_, y) in p)
TOP = top_y
by['T'] = drawn(["#####", "..#..", "..#..", "..#..", "..#.."])
by['G'] = drawn([".####", "#....", "#..##", "#...#", ".###."])
by['O'] = drawn([".###.", "#...#", "#...#", "#...#", ".###."])
by['D'] = drawn(["####.", "#...#", "#...#", "#...#", "####."])
by['P'] = drawn(["####.", "#...#", "####.", "#....", "#...."])
by['Y'] = drawn(["#...#", ".#.#.", "..#..", "..#..", "..#.."])
by['C'] = drawn([".####", "#....", "#....", "#....", ".####"])
by['H'] = drawn(["#...#", "#...#", "#####", "#...#", "#...#"])
by['K'] = drawn(["#...#", "#..#.", "###..", "#..#.", "#...#"])
by[' '] = (2, {})

def plaque_title(img, word, centre=88):
    # wipe the old title (copy each row's plain plaque colour across)
    for y in range(3, 16):
        plain = img.getpixel((30, y))
        for x in range(24, 152):
            img.putpixel((x, y), plain)
    width = sum(by[c][0] for c in word) + gap * (len(word) - 1)
    x = centre - width // 2
    for c in word:
        w, pix = by[c]
        for (dx, y), p in pix.items():
            sx, sy = x + dx + 1, y + 1
            if rgb(img.getpixel((sx, sy))) not in TEXT: img.putpixel((sx, sy), SHADOW + (255,))
        for (dx, y), p in pix.items(): img.putpixel((x + dx, y), p)
        x += w + gap

def tile(img, cx, cy):
    """A slate square behind a realm's item (item at cx, cy), like the icon squares on Survival's buttons."""
    x0, y0, x1, y1 = cx - 3, cy - 3, cx + 18, cy + 18
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            edge = x in (x0, x1) or y in (y0, y1)
            light = x == x0 + 1 or y == y0 + 1
            dark = x == x1 - 1 or y == y1 - 1
            c = (0x31, 0x35, 0x4b) if edge else (0x6a, 0x6f, 0x8d) if light and not dark else (0x38, 0x3b, 0x4f) if dark else (0x40, 0x44, 0x5c)
            img.putpixel((x, y), c + (255,))

def cell(img, cx, cy):
    """A slot-sized well behind an item at (cx, cy), like the inventory's slots."""
    x0, y0, x1, y1 = cx - 1, cy - 1, cx + 16, cy + 16
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            c = (0x23, 0x25, 0x36) if x == x0 or y == y0 else (0x6a, 0x6f, 0x8d) if x == x1 or y == y1 else (0x38, 0x3b, 0x4f)
            img.putpixel((x, y), c + (255,))

def background(rows, slots, word="REALMS", dividers=(), cells=()):
    top = warps.crop((0, 0, W, 29))
    middle = warps.crop((0, 25, W, 26))
    bottom = warps.crop((0, 116, W, 127))
    total = 45 + 18 * rows
    img = Image.new('RGBA', (W, 256), (0, 0, 0, 0))
    img.paste(top, (0, 0))
    for y in range(29, total - 11): img.paste(middle, (0, y))
    img.paste(bottom, (0, total - 11))
    plaque_title(img, word)
    for gy in dividers:  # a groove across the panel at this menu y (between slot rows)
        for x in range(8, 168):
            img.putpixel((x, gy + 16), (0x31, 0x35, 0x4b, 255))
            img.putpixel((x, gy + 17), (0x6a, 0x6f, 0x8d, 255))
    for slot in slots:
        r, c = divmod(slot, 9)
        tile(img, 8 + c * 18, 16 + 18 + r * 18)  # 16 = how far the picture starts above the menu
    for slot in cells:
        r, c = divmod(slot, 9)
        cell(img, 8 + c * 18, 16 + 18 + r * 18)
    return img

def centred(count, rows):
    if count == 0: return []
    if count > 9: return None
    gap = 2 if count <= 5 else 1
    first = 4 - (count - 1) * gap // 2
    row = (rows - 1) // 2
    return [row * 9 + first + i * gap for i in range(count)]

os.makedirs(f'{OUT}/assets/fractal/textures/gui', exist_ok=True)
os.makedirs(f'{OUT}/assets/fractal/font', exist_ok=True)
providers = [{"type": "space", "advances": {"": -8, "": -169}}]
for rows in range(1, 7):
    for count in range(0, 10):
        name = f'realms_{rows}_{count}'
        background(rows, centred(count, rows)).save(f'{OUT}/assets/fractal/textures/gui/{name}.png')
        providers.append({"type": "bitmap", "file": f"fractal:gui/{name}.png", "ascent": 29, "height": 256,
                          "chars": [chr(0xF100 + rows * 16 + count)]})
for code, name, word in ((0xF170, 'settings', 'SETTINGS'), (0xF171, 'ignored', 'IGNORED')):
    background(6, [], word, dividers=(70, 106)).save(f'{OUT}/assets/fractal/textures/gui/{name}.png')
    providers.append({"type": "bitmap", "file": f"fractal:gui/{name}.png", "ascent": 29, "height": 256, "chars": [chr(code)]})
def add(code, name, img):
    img.save(f'{OUT}/assets/fractal/textures/gui/{name}.png')
    providers.append({"type": "bitmap", "file": f"fractal:gui/{name}.png", "ascent": 29, "height": 256, "chars": [chr(code)]})
def grid(rows):  # 7 across, one row in from each side, above the bottom row
    return [(1 + r) * 9 + 1 + c for r in range(rows - 2) for c in range(7)]
for rows in range(3, 7):
    bottom = (rows - 1) * 9
    add(0xF180 + rows, f'chatcolor_{rows}', background(rows, [], "CHAT COLOR", cells=grid(rows) + [bottom + 3, bottom + 5]))
    add(0xF190 + rows, f'trails_{rows}', background(rows, [], "TRAILS", cells=grid(rows) + [bottom + 3, bottom + 5]))
add(0xF1A5, 'parkour', background(5, [], "PARKOUR", cells=[11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 40]))
add(0xF1B6, 'radio', background(6, [], "RADIO", cells=grid(6) + [0, 1, 2, 4, 6, 7, 8] + list(range(45, 54))))
json.dump({"providers": providers}, open(f'{OUT}/assets/fractal/font/lobby.json', 'w'), indent=1)
