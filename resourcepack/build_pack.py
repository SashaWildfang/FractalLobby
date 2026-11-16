"""Builds FractalLobby.zip: rank icons, realm menu backgrounds, slate inventories, the Survival icon."""
from PIL import Image
import json, os, shutil, subprocess, sys

FRACTAL, MENUS, VANILLA, OUT = sys.argv[1:5]   # unzipped Fractal pack, CrazySurvival gui/menus, vanilla assets, output dir
shutil.rmtree(OUT, ignore_errors=True)
os.makedirs(OUT)

# 1. rank icons from the Fractal pack
shutil.copy(f'{FRACTAL}/pack.png', OUT)
shutil.copytree(f'{FRACTAL}/assets/minecraft/textures/gui/ranks', f'{OUT}/assets/minecraft/textures/gui/ranks')
font = json.load(open(f'{FRACTAL}/assets/minecraft/font/default.json'))
os.makedirs(f'{OUT}/assets/minecraft/font')
json.dump({"providers": [p for p in font['providers'] if p.get('file', '').startswith('minecraft:gui/ranks/')]},
          open(f'{OUT}/assets/minecraft/font/default.json', 'w'), ensure_ascii=False, indent=1)

# 2. realm menu backgrounds
subprocess.run([sys.executable, os.path.join(os.path.dirname(__file__), 'make_gui.py'), MENUS, OUT], check=True)

# 3. slate colours for the vanilla greys, so inventories match Survival's menus
SLATE = {198: (0x4b, 0x4f, 0x65), 196: (0x4b, 0x4f, 0x65), 255: (0x6a, 0x6f, 0x8d), 252: (0x6a, 0x6f, 0x8d),
         139: (0x38, 0x3b, 0x4f), 85: (0x31, 0x35, 0x4b), 55: (0x23, 0x25, 0x36)}
def slate(img):
    px = img.load()
    for x in range(img.width):
        for y in range(img.height):
            r, g, b, a = px[x, y]
            if a and r == g == b and r in SLATE: px[x, y] = SLATE[r] + (a,)
    return img
for name in ('inventory', 'generic_54'):
    os.makedirs(f'{OUT}/assets/minecraft/textures/gui/container', exist_ok=True)
    slate(Image.open(f'{VANILLA}/assets/minecraft/textures/gui/container/{name}.png').convert('RGBA')) \
        .save(f'{OUT}/assets/minecraft/textures/gui/container/{name}.png')
gui = f'{OUT}/assets/fractal/textures/gui'
for f in os.listdir(gui): slate(Image.open(f'{gui}/{f}').convert('RGBA')).save(f'{gui}/{f}')
# no "Inventory" label over the player's items in menus
os.makedirs(f'{OUT}/assets/minecraft/lang')
json.dump({"container.inventory": ""}, open(f'{OUT}/assets/minecraft/lang/en_us.json', 'w'))

# 4. the Survival realm icon (item model fractal:survival)
ART = ["................",
       ".......LL.......",
       ".....LLLLl......",
       "....LLLLLLl.....",
       "....LLLLLll.....",
       ".....lLLll......",
       ".......T........",
       ".......T........",
       "..GGGGGGGGGGGG..",
       ".GGGGGGGGGGGGGg.",
       ".gDDDDDDDDDDDDg.",
       "..DDDdDDDDdDDD..",
       "...DdDDSDDDDd...",
       "....DDDDsDDD....",
       "......DdDD......",
       ".......dd......."]
COL = {'L': '#6abe30', 'l': '#3f8a26', 'T': '#6b4a2b', 'G': '#7fcf3a', 'g': '#4e9a2a',
       'D': '#8b5a2b', 'd': '#6b4426', 'S': '#8a8a8a', 's': '#6a6a6a'}
icon = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
for y, row in enumerate(ART):
    for x, c in enumerate(row):
        if c != '.': icon.putpixel((x, y), tuple(int(COL[c][i:i + 2], 16) for i in (1, 3, 5)) + (255,))
for d in ('textures/item', 'models/item', 'items'): os.makedirs(f'{OUT}/assets/fractal/{d}', exist_ok=True)
icon.save(f'{OUT}/assets/fractal/textures/item/survival.png')
json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "fractal:item/survival"}},
          open(f'{OUT}/assets/fractal/models/item/survival.json', 'w'))
json.dump({"model": {"type": "minecraft:model", "model": "fractal:item/survival"}},
          open(f'{OUT}/assets/fractal/items/survival.json', 'w'))

json.dump({"pack": {"pack_format": 46, "supported_formats": [46, 99], "description": "FractalSMP Lobby"}},
          open(f'{OUT}/pack.mcmeta', 'w'), indent=1)
print('built', OUT)

# 5. HUD: no hearts or hunger, slate hotbar, purple XP bar
HUD = f'{OUT}/assets/minecraft/textures/gui/sprites/hud'
VHUD = f'{VANILLA}/assets/minecraft/textures/gui/sprites/hud'
os.makedirs(f'{HUD}/heart', exist_ok=True)
for f in os.listdir(f'{VHUD}/heart'):
    if f.endswith('.png'):
        w, h = Image.open(f'{VHUD}/heart/{f}').size
        Image.new('RGBA', (w, h), (0, 0, 0, 0)).save(f'{HUD}/heart/{f}')
for f in os.listdir(VHUD):
    if f.startswith('food_') and f.endswith('.png'):
        w, h = Image.open(f'{VHUD}/{f}').size
        Image.new('RGBA', (w, h), (0, 0, 0, 0)).save(f'{HUD}/{f}')

def lum(p): return (p[0] * 3 + p[1] * 6 + p[2]) / 10
def bands(src, palette, alpha=None):
    """Recolours a sprite by brightness into palette [(max brightness, colour)], keeping its shape."""
    img = Image.open(src).convert('RGBA'); px = img.load()
    for x in range(img.width):
        for y in range(img.height):
            p = px[x, y]
            if not p[3]: continue
            for top, c in palette:
                if lum(p) <= top:
                    px[x, y] = c + ((alpha if alpha is not None and p[3] < 255 else p[3]),); break
    return img
SLATE_BANDS = [(12, (0x12, 0x13, 0x1c)), (60, (0x38, 0x3b, 0x4f)), (110, (0x31, 0x35, 0x4b)), (140, (0x4b, 0x4f, 0x65)), (999, (0x6a, 0x6f, 0x8d))]
TWO = [(12, (0x12, 0x13, 0x1c)), (999, (0x31, 0x35, 0x4b))]
for f in ('hotbar_offhand_left', 'hotbar_offhand_right'):
    bands(f'{VHUD}/{f}.png', TWO, alpha=235).save(f'{HUD}/{f}.png')
def cell(img, x0, y0, x1, y1):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            c = (0x23, 0x25, 0x36) if x == x0 or y == y0 else (0x6a, 0x6f, 0x8d) if x == x1 or y == y1 else (0x38, 0x3b, 0x4f)
            img.putpixel((x, y), c + (235,))
hotbar = Image.new('RGBA', (182, 22), (0x31, 0x35, 0x4b, 235))
for x in range(182):
    for y in (0, 21): hotbar.putpixel((x, y), (0x12, 0x13, 0x1c, 255))
for y in range(22):
    for x in (0, 181): hotbar.putpixel((x, y), (0x12, 0x13, 0x1c, 255))
for i in range(9): cell(hotbar, 2 + 20 * i, 2, 19 + 20 * i, 19)
hotbar.save(f'{HUD}/hotbar.png')
bands(f'{VHUD}/hotbar_selection.png', [(12, (0x2a, 0x17, 0x40)), (120, (0x8e, 0x2d, 0xe2)), (190, (0xb2, 0x66, 0xff)), (999, (0xe5, 0x8b, 0xff))]).save(f'{HUD}/hotbar_selection.png')
def bar(name, colour):
    src = Image.open(f'{VHUD}/{name}.png').convert('RGBA'); img = Image.new('RGBA', src.size); w, h = src.size
    for x in range(w):
        for y in range(h):
            if not src.getpixel((x, y))[3]: continue
            edge = y in (0, h - 1) or x in (0, w - 1)
            img.putpixel((x, y), colour(x / (w - 1), y, edge) + (255,))
    img.save(f'{HUD}/{name}.png')
def mix(a, b, t): return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))
bar('experience_bar_background', lambda t, y, edge: (0x12, 0x13, 0x1c) if edge else (0x31, 0x35, 0x4b))
bar('experience_bar_progress', lambda t, y, edge: (0x2a, 0x17, 0x40) if edge else
    mix(mix((0x8e, 0x2d, 0xe2), (0xe5, 0x8b, 0xff), t), (255, 255, 255), 0.35 if y == 1 else 0))

# 6. icons for the settings menu (most from Survival's pack, item models fractal:menu/NAME)
ICONS = sys.argv[5]  # CrazySurvival assets/minecraft/textures/item
MENU_ICONS = {
    'header': 'guide/settings', 'lobby': 'guide/home', 'network': 'guide/worlds',
     'chat': 'guide/chat',
    'player-visibility': 'guide/profile', 'mentions': 'guide/emoji', 'double-jump': 'guide/souls',
    'scoreboard': 'guide/dailyquest', 'private-messages': 'profile/friend', 'ignored': 'profile/game',
    'social-spy': 'guide/quests', 'command-spy': 'guide/auction', 'vanish': 'guide/warps',
    'unavailable': 'guide/afk', 'reset': 'guide/trading', 'back': 'guide/back', 'more': 'settings/scrolldown',
    'on': 'settings/enabled', 'next': 'guide/next', 'search': 'guide/quests', 'off': 'settings/disabled', 'middle': 'settings/middle',
}
NOTE = ["................",
        ".....#########..",
        ".....#OOOOOOO#..",
        ".....#O#####O#..",
        ".....#O#...#O#..",
        ".....#O#...#O#..",
        ".....#O#...#O#..",
        ".....#O#...#O#..",
        "..####O#.####O#.",
        ".#OOOOO#.#OOOO#.",
        ".#OOOOO#.#OOOO#.",
        ".#OOOO#..#OOO#..",
        "..####...####...",
        "................",
        "................",
        "................"]
def model(name, img):
    for d in ('textures/item/menu', 'models/item/menu', 'items/menu'): os.makedirs(f'{OUT}/assets/fractal/{d}', exist_ok=True)
    img.save(f'{OUT}/assets/fractal/textures/item/menu/{name}.png')
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"fractal:item/menu/{name}"}},
              open(f'{OUT}/assets/fractal/models/item/menu/{name}.json', 'w'))
    json.dump({"model": {"type": "minecraft:model", "model": f"fractal:item/menu/{name}"}},
              open(f'{OUT}/assets/fractal/items/menu/{name}.json', 'w'))
for name, src in MENU_ICONS.items():
    model(name, Image.open(f'{ICONS}/{src}.png').convert('RGBA'))
note = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
for y, row in enumerate(NOTE):
    for x, c in enumerate(row):
        if c == '#': note.putpixel((x, y), (0x2a, 0x17, 0x40, 255))
        elif c == 'O': note.putpixel((x, y), (0xb2, 0x66, 0xff, 255) if y < 9 else (0xe5, 0x8b, 0xff, 255))
model('message-sounds', note)

# join and leave messages: the door with a green arrow going in, or a red one coming out
door = Image.open(f'{ICONS}/guide/dungeons.png').convert('RGBA')
def door_arrow(colour, light, pointing_in):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); img.alpha_composite(door, (-2, 0))
    shape = ["..#....", ".##....", "#######", ".##....", "..#...."] if pointing_in else \
            ["....#..", "....##.", "#######", "....##.", "....#.."]
    ox, oy = 9, 9
    for y in range(-1, 6):           # dark outline first
        for x in range(-1, 8):
            near = any(0 <= y + dy < 5 and 0 <= x + dx < 7 and shape[y + dy][x + dx] == '#' for dx in (-1, 0, 1) for dy in (-1, 0, 1))
            if near and 0 <= ox + x < 16 and 0 <= oy + y < 16: img.putpixel((ox + x, oy + y), (0x1a, 0x1b, 0x26, 255))
    for y, row in enumerate(shape):
        for x, ch in enumerate(row):
            if ch == '#': img.putpixel((ox + x, oy + y), (light if y < 2 else colour) + (255,))
    return img
model('join-messages', door_arrow((0x3c, 0xa0, 0x3c), (0x7f, 0xdb, 0x6a), True))
model('leave-messages', door_arrow((0xb0, 0x30, 0x30), (0xe3, 0x5a, 0x5a), False))
print('menu icons', len(MENU_ICONS) + 1)

# 7. more icons: arrows, chat colours, trails, profile and hotbar items
def outline(img, colour=(0x1a, 0x1b, 0x26)):
    out = img.copy(); px = img.load()
    for x in range(16):
        for y in range(16):
            if px[x, y][3]: continue
            if any(0 <= x + dx < 16 and 0 <= y + dy < 16 and px[x + dx, y + dy][3] > 100 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.putpixel((x, y), colour + (255,))
    return out
def from_art(art, colours):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(art):
        for x, ch in enumerate(row):
            if ch in colours: img.putpixel((x, y), colours[ch] + (255,))
    return img
ARROW = ["................",
         "................",
         "......#.........",
         ".....##.........",
         "....###.........",
         "...############.",
         "..#############.",
         ".##############.",
         "..#############.",
         "...############.",
         "....###.........",
         ".....##.........",
         "......#.........",
         "................",
         "................",
         "................"]
def arrow(left):
    art = [r[::-1] if not left else r for r in ARROW]
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(art):
        for x, ch in enumerate(row):
            if ch == '#': img.putpixel((x, y), (0xe9, 0xeb, 0xf5, 255) if y < 7 else (0xa9, 0xae, 0xc8, 255))
    return outline(img)
model('back', arrow(True))
model('next', arrow(False))

DROP = ["................",
        "........o.......",
        ".......ohf......",
        ".......ohf......",
        "......ohhfo.....",
        "......ohffo.....",
        ".....ohhffdo....",
        ".....ohfffdo....",
        "....ohhffffdo...",
        "....ohfffffdo...",
        "....ohffffddo...",
        "....offfffddo...",
        ".....offffdo....",
        "......oddddo....",
        ".......oooo.....",
        "................"]
def hexc(h): h = h.lstrip('#'); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
def drop(colour_at):
    """A paint drop; colour_at(t) gives the colour t of the way down (for gradients)."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(DROP):
        base = colour_at(y / 15)
        for x, ch in enumerate(row):
            if ch == 'o': c = mix(base, (0x10, 0x10, 0x18), 0.75)
            elif ch == 'h': c = mix(base, (255, 255, 255), 0.55)
            elif ch == 'd': c = mix(base, (0, 0, 0), 0.3)
            elif ch == 'f': c = base
            else: continue
            img.putpixel((x, y), c + (255,))
    return img
def gradient(*stops):
    cols = [hexc(s) for s in stops]
    def at(t):
        t = min(max((t - 0.1) / 0.8, 0), 1) * (len(cols) - 1); i = min(int(t), len(cols) - 2)
        return mix(cols[i], cols[i + 1], t - i)
    return at
import colorsys
CHAT_COLOURS = {'white': '#FFFFFF', 'gray': '#AAAAAA', 'red': '#FF7590', 'orange': '#FFB36B', 'yellow': '#FFE680',
                'green': '#9BFFC4', 'aqua': '#7FE7FF', 'blue': '#7FA8FF', 'purple': '#B266FF', 'lavender': '#E3C4FF',
                'pink': '#FF7BE5'}
def color_model(name, img):
    for d in ('textures/item/color', 'models/item/color', 'items/color'): os.makedirs(f'{OUT}/assets/fractal/{d}', exist_ok=True)
    img.save(f'{OUT}/assets/fractal/textures/item/color/{name}.png')
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"fractal:item/color/{name}"}}, open(f'{OUT}/assets/fractal/models/item/color/{name}.json', 'w'))
    json.dump({"model": {"type": "minecraft:model", "model": f"fractal:item/color/{name}"}}, open(f'{OUT}/assets/fractal/items/color/{name}.json', 'w'))
for name, h in CHAT_COLOURS.items(): color_model(name, drop(lambda t, h=h: hexc(h)))
color_model('fractal', drop(gradient('#8E2DE2', '#E58BFF')))
color_model('sunset', drop(gradient('#FF7590', '#FFD27F')))
color_model('ocean', drop(gradient('#4FC3F7', '#9BFFC4')))
color_model('rainbow', drop(lambda t: tuple(round(v * 255) for v in colorsys.hsv_to_rgb(t * 0.85, 0.55, 1))))

PARTICLES = f'{VANILLA}/assets/minecraft/textures/particle'
SNOWFLAKE = ["...#....", ".#.#.#..", "..###...", "#######.", "..###...", ".#.#.#..", "...#....", "........"]
def particle(src, tint=None):
    if isinstance(src, list):
        small = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
        for y, row in enumerate(src):
            for x, ch in enumerate(row):
                if ch == '#': small.putpixel((x, y), (255, 255, 255, 255))
    else:
        small = Image.open(f'{PARTICLES}/{src}.png').convert('RGBA').resize((8, 8), Image.NEAREST)
    if tint:
        t = hexc(tint); px = small.load()
        for x in range(8):
            for y in range(8):
                r, g, b, a = px[x, y]
                if a: px[x, y] = (r * t[0] // 255, g * t[1] // 255, b * t[2] // 255, a)
    big = small.resize((16, 16), Image.NEAREST)
    # shrink to 14x14 inside the icon so the outline fits
    big = big.crop(big.getbbox()) if big.getbbox() else big
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    if big.width > 14 or big.height > 14: big = big.resize((min(14, big.width), min(14, big.height)), Image.NEAREST)
    img.alpha_composite(big, ((16 - big.width) // 2, (16 - big.height) // 2))
    return outline(img)
TRAILS = {'notes': ('note', '#9BFFC4'), 'clouds': ('generic_7', None), 'sparkles': ('glint', '#9BFFC4'),
          'hearts': ('heart', None), 'snowflakes': (SNOWFLAKE, '#E8F6FF'), 'flames': ('flame', None),
          'soul-flames': ('soul_fire_flame', None), 'magic': ('spell_7', '#B266FF'), 'stars': ('glitter_7', '#FFE680'),
          'enchanted': ('sga_a', '#C9A8FF'), 'blossom': ('cherry_0', None), 'sparks': ('spark_7', '#7FA8FF'),
          'totem': ('glitter_5', '#C8FF80')}
def trail_model(name, img):
    for d in ('textures/item/trail', 'models/item/trail', 'items/trail'): os.makedirs(f'{OUT}/assets/fractal/{d}', exist_ok=True)
    img.save(f'{OUT}/assets/fractal/textures/item/trail/{name}.png')
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"fractal:item/trail/{name}"}}, open(f'{OUT}/assets/fractal/models/item/trail/{name}.json', 'w'))
    json.dump({"model": {"type": "minecraft:model", "model": f"fractal:item/trail/{name}"}}, open(f'{OUT}/assets/fractal/items/trail/{name}.json', 'w'))
for name, (src, tint) in TRAILS.items(): trail_model(name, particle(src, tint))

# profile and hotbar
model('chat-color', drop(gradient('#8E2DE2', '#E58BFF')))
model('trail', particle('glitter_7', '#FFE680'))
model('glow', particle('glow', '#FFE680'))
model('parkour', Image.open(f'{ICONS}/guide/rank.png').convert('RGBA'))
model('realms', Image.open(f'{ICONS}/guide/worlds.png').convert('RGBA'))
model('radio', note)
model('players-shown', Image.open(f'{ICONS}/guide/profile.png').convert('RGBA'))
hidden = Image.open(f'{ICONS}/guide/profile.png').convert('RGBA'); px = hidden.load()
for x in range(16):
    for y in range(16):
        r, g, b, a = px[x, y]
        if a: v = (r * 3 + g * 6 + b) // 10; px[x, y] = (v * 3 // 5, v * 3 // 5, v * 2 // 3, a)
model('players-hidden', hidden)

# a quieter hotbar selection: a thin light frame
sel = Image.new('RGBA', (24, 23), (0, 0, 0, 0))
for x in range(24):
    for y in range(23):
        edge = x in (0, 23) or y in (0, 22)
        inner = x in (1, 22) or y in (1, 21)
        if edge: sel.putpixel((x, y), (0x12, 0x13, 0x1c, 255))
        elif inner: sel.putpixel((x, y), (0xd6, 0xd9, 0xea, 255))
sel.save(f'{HUD}/hotbar_selection.png')

# 8. hand-drawn icons: better trails, profile cosmetics, and the Radio buttons
from PIL import ImageDraw
def art(rows, colours, shade=True):
    """Pixel art from rows of characters, centred in 16x16 and outlined. colours: char -> (r,g,b) or
    char -> function(x, y, w, h) -> (r,g,b). With shade, the top half is a little lighter."""
    h, w = len(rows), max(len(r) for r in rows)
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    ox, oy = (16 - w) // 2, (16 - h) // 2
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch not in colours: continue
            c = colours[ch]
            c = c(x, y, w, h) if callable(c) else c
            if shade and y < h * 0.4: c = mix(c, (255, 255, 255), 0.18)
            img.putpixel((ox + x, oy + y), c + (255,))
    return outline(img)
LIGHT, SHADE = (0xe9, 0xeb, 0xf5), (0xa9, 0xae, 0xc8)
def glyph(x, y, w, h): return LIGHT if y < h / 2 else SHADE
def hgrad(a, b): return lambda x, y, w, h: mix(hexc(a), hexc(b), x / max(1, w - 1))

SNOW = ["#.....#.....#", ".#...###...#.", "..#...#...#..", "...#..#..#...", "....#.#.#....", ".#...###...#.",
        "#############", ".#...###...#.", "....#.#.#....", "...#..#..#...", "..#...#...#..", ".#...###...#.", "#.....#.....#"]
STAR = ["......#......", "......#......", ".....###.....", ".....###.....", "#############", ".###########.",
        "..#########..", "...#######...", "...#######...", "..####.####..", "..###...###..", ".###.....###.", ".#.........#."]
BOLT = [".......####", "......####.", ".....####..", "....####...", "...#######.", "..#######..", ".....###...",
        "....###....", "...###.....", "..##.......", ".#........."]
SPARKLE = ["...........#..", "..........###.", "....#......#..", "....#.........", "...###........", "..#####.......",
           "#########.....", "..#####.......", "...###...#....", "....#...###...", "....#....#....", ".............."]
TOTEM = ["....YYYY....", "...YYYYYY...", "...YGYYGY...", "...YYYYYY...", "....YDDY....", "YY.YYYYYY.YY", "YYYYYYYYYYYY",
         "...YYYYYY...", "...YDYYDY...", "....YYYY....", "....YDDY....", "...YY..YY..."]
trail_model('snowflakes', art(SNOW, {'#': lambda x, y, w, h: (0xf4, 0xfb, 0xff) if (x + y) % 2 else (0xa8, 0xd8, 0xff)}, shade=False))
trail_model('stars', art(STAR, {'#': (0xff, 0xd8, 0x4a)}))
trail_model('sparks', art(BOLT, {'#': (0x7f, 0xb8, 0xff)}))
trail_model('magic', art(SPARKLE, {'#': hgrad('#8E2DE2', '#E58BFF')}))
trail_model('totem', art(TOTEM, {'Y': (0xf2, 0xc9, 0x4c), 'D': (0xb8, 0x86, 0x2b), 'G': (0x5e, 0xc4, 0x5e)}))

BUBBLE = ["..##########..", ".############.", "##############", "##############", "##WW##WW##WW##", "##WW##WW##WW##",
          "##############", ".############.", "..##########..", "...###........", "...##.........", "...#.........."]
model('chat-color', art(BUBBLE, {'#': hgrad('#8E2DE2', '#FF7BE5'), 'W': (255, 255, 255)}))
TRAIL3 = ["..........#...", ".........###..", "..........#...", "..............", "......#.......", ".....###......",
          "......#.......", "..............", "..#...........", ".###..........", "#####.........", ".###..........", "..#..........."]
model('trail', art(TRAIL3, {'#': lambda x, y, w, h: mix(hexc('#FFE680'), hexc('#FF7BE5'), y / (h - 1))}))
orb = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(orb)
d.ellipse((1, 1, 14, 14), fill=(0xff, 0xe6, 0x80, 70)); d.ellipse((3, 3, 12, 12), fill=(0xff, 0xe6, 0x80, 140))
d.ellipse((4, 4, 11, 11), fill=(0xff, 0xd2, 0x4a, 255)); d.ellipse((5, 5, 8, 8), fill=(0xff, 0xf6, 0xc8, 255))
model('glow', orb)

# Radio buttons (item models fractal:radio/NAME)
def radio_model(name, img):
    for dd in ('textures/item/radio', 'models/item/radio', 'items/radio'): os.makedirs(f'{OUT}/assets/fractal/{dd}', exist_ok=True)
    img.save(f'{OUT}/assets/fractal/textures/item/radio/{name}.png')
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"fractal:item/radio/{name}"}}, open(f'{OUT}/assets/fractal/models/item/radio/{name}.json', 'w'))
    json.dump({"model": {"type": "minecraft:model", "model": f"fractal:item/radio/{name}"}}, open(f'{OUT}/assets/fractal/items/radio/{name}.json', 'w'))
G = {'#': glyph}
P = {'#': hgrad('#8E2DE2', '#E58BFF')}
radio_model('play', art(["#......", "###....", "#####..", "#######", "#####..", "###....", "#......"][:7] and
                        ["#.......", "##......", "####....", "######..", "########", "######..", "####....", "##......", "#......."], {'#': (0x9b, 0xff, 0xc4)}))
radio_model('pause', art(["###..###"] * 9, P))
radio_model('skip', art(["#.....##", "##....##", "####..##", "########", "####..##", "##....##", "#.....##"], G))
radio_model('previous', art(["##.....#", "##....##", "##..####", "########", "##..####", "##....##", "##.....#"], G))
SHUF = ["........#..", "#.....#####", "##...#..#..", "..#.#......", "...#.......", "..#.#......", "##...#..#..", "#.....#####", "........#.."]
radio_model('shuffle', art(SHUF, P)); radio_model('shuffle-off', art(SHUF, G))
REPEAT = ["...#........", "..#########.", "...#.......#", "#..........#", "#..........#", "#.......#...", ".#########..", "........#..."]
radio_model('repeat', art(REPEAT, G))
radio_model('volume', art(["....#......", "...##..#...", "..###...#..", "####..#..#.", "####...#.#.", "####...#.#.", "####..#..#.",
                           "..###...#..", "...##..#...", "....#......"], G))
radio_model('queue', art(["##.########", "##.########", "...........", "##.########", "##.########", "...........", "##.######..", "##.######.."], G))
radio_model('sort', art(["##########", "##########", "..........", "#######...", "#######...", "..........", "####......", "####......"], G))
radio_model('categories', art(["AAAA.BBBB", "AAAA.BBBB", "AAAA.BBBB", "AAAA.BBBB", ".........", "CCCC.DDDD", "CCCC.DDDD", "CCCC.DDDD", "CCCC.DDDD"],
                              {'A': hexc('#B266FF'), 'B': hexc('#FF7BE5'), 'C': hexc('#7FE7FF'), 'D': hexc('#FFE680')}))
radio_model('favorites', art(STAR, {'#': (0xff, 0xd8, 0x4a)}))
radio_model('station', art(["#.......#", ".#.....#.", "#.#.#.#.#", ".#.###.#.", "#...#...#", "....#....", "...###...", "...#.#...", "..#...#..", "..#####..", ".#.....#."], G))
radio_model('bar-boss', art(["############", "#PPPPPPPP..#", "############"], {'#': SHADE, 'P': hexc('#B266FF')}))
radio_model('bar-action', art(["#.#.###.#.#", "...........", "###########"], G))
radio_model('bar-off', art(["#.........#", ".#.......#.", "..#.....#..", "...#...#...", "....#.#....", ".....#.....", "....#.#....", "...#...#...",
                            "..#.....#..", ".#.......#.", "#.........#"], {'#': (0x8a, 0x8e, 0xa8)}))
radio_model('empty', art(["...#####...", "..#.....#..", ".#.......#.", "#.........#", "#.........#", "#.........#", ".#.......#.", "..#.....#..", "...#####..."],
                         {'#': (0x8a, 0x8e, 0xa8)}))
DISC = ["....######....", "..##########..", ".############.", ".####LLLL####.", "####LLLLLL####", "###LLL..LLL###", "###LL....LL###",
        "###LL....LL###", "###LLL..LLL###", "####LLLLLL####", ".####LLLL####.", ".############.", "..##########..", "....######...."]
for i, label in enumerate(('#B266FF', '#FF7BE5', '#7FE7FF', '#9BFFC4', '#FFE680')):
    radio_model(f'disc_{i}', art(DISC, {'#': lambda x, y, w, h: (0x2c, 0x2e, 0x3e) if (x + y) % 5 else (0x3d, 0x40, 0x55),
                                        'L': hexc(label)}, shade=False))
SKIP = ["#......##", "##.....##", "###....##", "####...##", "#####..##", "######.##", "#####..##", "####...##", "###....##", "##.....##", "#......##"]
radio_model('skip', art(SKIP, G))
radio_model('previous', art([r[::-1] for r in SKIP], G))
radio_model('station', art(["........#.", ".......#..", "......#...", "##########", "#SSS#....#", "#SSS#.##.#", "#SSS#....#", "#SSS#.##.#", "##########"],
                           {'#': glyph, 'S': (0x6a, 0x6f, 0x8d)}))
# the totem trail: Minecraft's own Totem of Undying
trail_model('totem', Image.open(f'{VANILLA}/assets/minecraft/textures/item/totem_of_undying.png').convert('RGBA'))
# Radio close button: a red X
radio_model('close', art(["##.....##", "###...###", ".###.###.", "..#####..", "...###...", "..#####..", ".###.###.", "###...###", "##.....##"],
                         {'#': (0xff, 0x75, 0x90)}))
