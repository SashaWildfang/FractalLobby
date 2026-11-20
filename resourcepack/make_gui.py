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
by['B'] = drawn(["####.", "#...#", "####.", "#...#", "####."])
by['K'] = drawn(["#...#", "#..#.", "###..", "#..#.", "#...#"])
by['F'] = drawn(["#####", "#....", "####.", "#....", "#...."])
by['X'] = drawn(["#...#", ".#.#.", "..#..", ".#.#.", "#...#"])
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
providers = [{"type": "space", "advances": {"": -8, "": -169, "": -4}}]
for rows in range(1, 7):
    for count in range(0, 10):
        name = f'realms_{rows}_{count}'
        background(rows, centred(count, rows)).save(f'{OUT}/assets/fractal/textures/gui/{name}.png')
        providers.append({"type": "bitmap", "file": f"fractal:gui/{name}.png", "ascent": 29, "height": 256,
                          "chars": [chr(0xF100 + rows * 16 + count)]})
# settings: Lobby on rows 1-2, a groove through the empty middle row, Network on rows 4-5, a groove above the bottom row
for code, name, word, dividers in ((0xF170, 'settings', 'SETTINGS', (62, 106)), (0xF171, 'ignored', 'IGNORED', (70, 106)),
                                   (0xF172, 'friends', 'FRIENDS', (106,))):
    background(6, [], word, dividers=dividers).save(f'{OUT}/assets/fractal/textures/gui/{name}.png')
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
# the Mystery Vault: your stash on top, your boxes (4 rows of 7), and pages / craft / open all below
_grid = [r * 9 + c for r in (1, 2, 3, 4) for c in range(1, 8)]
add(0xF173, 'mystery', background(6, [], "MYSTERY VAULT", cells=[4] + _grid + [45, 47, 51, 53]))
# crafting boxes with Mystery Dust: 1 to 5 stars, and back
add(0xF17C, 'mystery_craft', background(3, [], "CRAFT BOXES", cells=[4, 11, 12, 13, 14, 15, 22]))
add(0xF174, 'collection', background(6, [], "COLLECTION", cells=list(range(45)) + [49]))
# Collectibles: the main menu (the stash, the categories, the extras) and a page per category (season tabs on top for
# hats and suits, the items in the middle, back and take off at the bottom)
add(0xF175, 'collectibles', background(6, [], "COLLECTIBLES", cells=[4, 19, 20, 21, 22, 23, 24, 25, 38, 40, 42]))
for _code, _word, _tabs in ((0xF176, 'HATS', False), (0xF177, 'SUITS', False), (0xF178, 'GADGETS', False),
                            (0xF179, 'CLOAKS', False), (0xF17A, 'EMOTES', False), (0xF17B, 'GESTURES', False)):
    add(_code, 'collect_' + _word.lower(), background(6, [], _word,
        cells=([2, 3, 4, 5, 6] if _tabs else []) + list(range(9, 45)) + [45, 48, 50, 53] + ([] if _word in ('EMOTES', 'GESTURES') else [49])))
# one suit: its four pieces, the whole set, and back
add(0xF17D, 'collect_suit', background(3, [], "SUIT", cells=[10, 11, 12, 13, 15, 18]))
add(0xF1A5, 'parkour', background(5, [], "PARKOUR", cells=[11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 40]))
add(0xF1C3, 'profile', background(3, [4], "PROFILE", cells=[13, 19, 20, 21, 22, 23, 24, 25]))  # before 1.18.1
add(0xF1C4, 'profile_4', background(4, [4], "PROFILE", cells=[13, 29, 30, 31, 32, 33]))
def status_cols(k):  # a part row of k (of 7) is centred; an even count leaves the middle empty
    if k == 7: return list(range(7))
    if k % 2: return list(range((7 - k) // 2, (7 - k) // 2 + k))
    w = k + 1; start = (7 - w) // 2
    return [start + i for i in range(w) if i != w // 2]
def status_slots(n):
    rows = (n + 6) // 7
    return [(1 + r) * 9 + 1 + c for r in range(rows) for c in status_cols(min(7, n - 7 * r))]
for emoji_rows in range(1, 5):  # status menu: 1-4 rows of emojis, the last row holding 1-7 of them
    rows = emoji_rows + 2; bottom = (rows - 1) * 9
    for last in range(1, 8):
        n = (emoji_rows - 1) * 7 + last
        add(0xF1D0 + rows * 8 + last, f'status_{rows}_{last}', background(rows, [], "STATUS", cells=status_slots(n) + [bottom + 3, bottom + 5]))
add(0xF1B6, 'radio', background(6, [], "RADIO", cells=grid(6) + [0, 1, 2, 4, 6, 7, 8] + list(range(45, 54))))
json.dump({"providers": providers}, open(f'{OUT}/assets/fractal/font/lobby.json', 'w'), indent=1)
