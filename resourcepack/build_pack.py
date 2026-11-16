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
    'join-messages': 'guide/dungeons', 'leave-messages': 'guide/next', 'chat': 'guide/chat',
    'player-visibility': 'guide/profile', 'mentions': 'guide/emoji', 'double-jump': 'guide/souls',
    'scoreboard': 'guide/dailyquest', 'private-messages': 'profile/friend', 'ignored': 'profile/game',
    'social-spy': 'guide/quests', 'command-spy': 'guide/auction', 'vanish': 'guide/warps',
    'unavailable': 'guide/afk', 'reset': 'guide/trading', 'back': 'guide/back', 'more': 'settings/scrolldown',
    'on': 'settings/enabled', 'off': 'settings/disabled', 'middle': 'settings/middle',
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
print('menu icons', len(MENU_ICONS) + 1)
