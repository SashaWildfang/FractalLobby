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
SLATE = {198: (0x4b, 0x4f, 0x65), 197: (0x4b, 0x4f, 0x65), 196: (0x4b, 0x4f, 0x65), 255: (0x6a, 0x6f, 0x8d), 252: (0x6a, 0x6f, 0x8d),
         139: (0x38, 0x3b, 0x4f), 85: (0x31, 0x35, 0x4b), 55: (0x23, 0x25, 0x36)}
def slate(img):
    px = img.load()
    for x in range(img.width):
        for y in range(img.height):
            r, g, b, a = px[x, y]
            if a and r == g == b and r in SLATE: px[x, y] = SLATE[r] + (a,)
    return img
# every container screen, the creative menu backgrounds and its tabs and scroller
for folder in ('container', 'container/creative_inventory', 'sprites/container/creative_inventory'):
    src, dst = f'{VANILLA}/assets/minecraft/textures/gui/{folder}', f'{OUT}/assets/minecraft/textures/gui/{folder}'
    os.makedirs(dst, exist_ok=True)
    for f in sorted(os.listdir(src)):
        if f.endswith('.png') and f != 'gamemode_switcher.png':
            slate(Image.open(f'{src}/{f}').convert('RGBA')).save(f'{dst}/{f}')
# no crafting in the lobby: the 2x2 grid, arrow and result slot become plain panel, the recipe book button is gone
inv = Image.open(f'{OUT}/assets/minecraft/textures/gui/container/inventory.png')
for x in range(95, 172):
    for y in range(15, 56): inv.putpixel((x, y), SLATE[198] + (255,))
inv.save(f'{OUT}/assets/minecraft/textures/gui/container/inventory.png')
# no armour or shield outlines in the empty equipment slots
os.makedirs(f'{OUT}/assets/minecraft/textures/gui/sprites/container/slot', exist_ok=True)
for f in ('helmet', 'chestplate', 'leggings', 'boots', 'shield'):
    Image.new('RGBA', (16, 16), (0, 0, 0, 0)).save(f'{OUT}/assets/minecraft/textures/gui/sprites/container/slot/{f}.png')
os.makedirs(f'{OUT}/assets/minecraft/textures/gui/sprites/recipe_book', exist_ok=True)
for f in ('button', 'button_highlighted'):
    Image.new('RGBA', (20, 18), (0, 0, 0, 0)).save(f'{OUT}/assets/minecraft/textures/gui/sprites/recipe_book/{f}.png')
gui = f'{OUT}/assets/fractal/textures/gui'
for f in os.listdir(gui): slate(Image.open(f'{gui}/{f}').convert('RGBA')).save(f'{gui}/{f}')
# no "Inventory" label over the player's items in menus
os.makedirs(f'{OUT}/assets/minecraft/lang')
# and no "Crafting" label over the (now empty) crafting corner; creative tab titles in white, readable on slate
GROUPS = {'buildingBlocks': 'Building Blocks', 'coloredBlocks': 'Colored Blocks', 'combat': 'Combat',
          'foodAndDrink': 'Food & Drinks', 'functional': 'Functional Blocks', 'hotbar': 'Saved Hotbars',
          'ingredients': 'Ingredients', 'inventory': 'Survival Inventory', 'natural': 'Natural Blocks',
          'op': 'Operator Utilities', 'redstone': 'Redstone Blocks', 'search': 'Search Items',
          'spawnEggs': 'Spawn Eggs', 'tools': 'Tools & Utilities'}
lang = {"container.inventory": "", "container.crafting": ""}
lang.update({f'itemGroup.{k}': '\u00a7f' + v for k, v in GROUPS.items()})
json.dump(lang, open(f'{OUT}/assets/minecraft/lang/en_us.json', 'w'), ensure_ascii=False, indent=1)

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

# 9. more redraws: enchanted, blossom, electric; audience choices; Radio search; no hover highlight
trail_model('enchanted', Image.open(f'{VANILLA}/assets/minecraft/textures/item/enchanted_book.png').convert('RGBA'))
trail_model('blossom', Image.open(f'{VANILLA}/assets/minecraft/textures/item/pink_petals.png').convert('RGBA'))
bolt = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); dr = ImageDraw.Draw(bolt)
dr.polygon([(9, 1), (3, 8), (7, 8), (5, 14), (12, 6), (8, 6), (11, 1)], fill=(0xd8, 0xee, 0xff, 255))
dr.line([(9, 2), (5, 7)], fill=(255, 255, 255, 255))
trail_model('sparks', outline(bolt, (0x2b, 0x5f, 0xe0)))

# Who you see things from (Everyone / Donors Only / Staff Only / Nobody): not on/off switches
model('audience-everyone', Image.open(f'{ICONS}/guide/worlds.png').convert('RGBA'))
GEM = ["...######...", "..########..", ".##########.", "############", ".##########.", "..########..", "...######...", "....####....", ".....##....."]
model('audience-donors', art(GEM, {'#': lambda x, y, w, h: mix(hexc('#FFE680'), hexc('#E0A030'), y / (h - 1))}))
SHIELD = ["############"] * 5 + [".##########.", ".##########.", "..########..", "..########..", "...######...", "....####....", ".....##....."]
model('audience-staff', art(SHIELD, {'#': lambda x, y, w, h: hexc('#FFC98B') if x < w // 2 else hexc('#E8893A')}, shade=False))
nobody = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); dr = ImageDraw.Draw(nobody)
dr.ellipse((2, 2, 13, 13), outline=(0xff, 0x5a, 0x6e, 255), width=2)
dr.line([(4, 11), (11, 4)], fill=(0xff, 0x5a, 0x6e, 255), width=2)
model('audience-nobody', outline(nobody))

glass = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); dr = ImageDraw.Draw(glass)
dr.ellipse((2, 2, 10, 10), fill=(0x9f, 0xd8, 0xff, 255), outline=LIGHT + (255,), width=2)
dr.point([(5, 4), (4, 5)], fill=(255, 255, 255, 255))
dr.line([(9, 9), (13, 13)], fill=SHADE + (255,), width=3)
radio_model('search', outline(glass))

# no white highlight over the slot under the mouse
for f in ('slot_highlight_back', 'slot_highlight_front'):
    Image.new('RGBA', (24, 24), (0, 0, 0, 0)).save(f'{OUT}/assets/minecraft/textures/gui/sprites/container/{f}.png') \
        if os.makedirs(f'{OUT}/assets/minecraft/textures/gui/sprites/container', exist_ok=True) is None else None

# 10. a smoother music note (Radio hotbar item and Message Sounds)
note2 = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); dr = ImageDraw.Draw(note2)
dr.polygon([(5, 3), (13, 1), (13, 4), (5, 6)], fill=(255, 255, 255, 255))      # beam
dr.rectangle((5, 4, 6, 12), fill=(255, 255, 255, 255))                          # stems
dr.rectangle((12, 2, 13, 10), fill=(255, 255, 255, 255))
dr.ellipse((1, 10, 6, 14), fill=(255, 255, 255, 255))                           # heads
dr.ellipse((8, 8, 13, 12), fill=(255, 255, 255, 255))
px = note2.load()
for x in range(16):
    for y in range(16):
        if px[x, y][3]:
            c = mix(hexc('#B266FF'), hexc('#FF7BE5'), x / 15)
            if y in (2, 3) or (x, y) in ((2, 11), (3, 11), (9, 9), (10, 9)): c = mix(c, (255, 255, 255), 0.35)
            px[x, y] = c + (255,)
note2 = outline(note2)
model('radio', note2)
model('message-sounds', note2)

# 11. Donors/Staff Only, nothing-here X, social spy eye, Survival's crossed tools
CROWN = ["#....##....#", "##..####..##", "###.####.###", "############", "############", "#R##B##G##R#", "############"]
model('audience-donors', art(CROWN, {'#': lambda x, y, w, h: mix(hexc('#FFE680'), hexc('#E0A030'), y / (h - 1)),
                                     'R': hexc('#FF5A6E'), 'B': hexc('#7FA8FF'), 'G': hexc('#9BFFC4')}))
BADGE = [".##########.", "############", "#####WW#####", "#####WW#####", "##WWWWWWWW##", "###WWWWWW###", "####WWWW####",
         "###WWWWWW###", "###WW##WW###", ".##########.", "..########..", "...######...", "....####....", ".....##....."]
model('audience-staff', art(BADGE, {'#': lambda x, y, w, h: hexc('#FFB36B') if x < w // 2 else hexc('#E8893A'), 'W': (255, 255, 255)}))
model('none', art(["##.....##", "###...###", ".###.###.", "..#####..", "...###...", "..#####..", ".###.###.", "###...###", "##.....##"],
                  {'#': (0xff, 0x75, 0x90)}))
EYE = ["....######....", "..##WWWWWW##..", ".#WWWPPPPWWW#.", "#WWWPPKKPPWWW#", "#WWWPPKKPPWWW#", ".#WWWPPPPWWW#.", "..##WWWWWW##..", "....######...."]
model('social-spy', art(EYE, {'#': (0x6a, 0x6f, 0x8d), 'W': (0xf2, 0xf3, 0xfa), 'P': hexc('#B266FF'), 'K': (0x1a, 0x1b, 0x26)}, shade=False))
# Survival: an iron sword crossed with an iron pickaxe
sword = Image.open(f'{VANILLA}/assets/minecraft/textures/item/iron_sword.png').convert('RGBA')
pick = Image.open(f'{VANILLA}/assets/minecraft/textures/item/iron_pickaxe.png').convert('RGBA').transpose(Image.FLIP_LEFT_RIGHT)
tools = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); tools.alpha_composite(pick); tools.alpha_composite(sword)
tools.save(f'{OUT}/assets/fractal/textures/item/survival.png')

# 12. Radio genre icons (fractal:radio/genre/NAME), a "panel" blank, and the slate search glass is radio/search
def genre(name, img):
    for dd in ('textures/item/radio/genre', 'models/item/radio/genre', 'items/radio/genre'): os.makedirs(f'{OUT}/assets/fractal/{dd}', exist_ok=True)
    img.save(f'{OUT}/assets/fractal/textures/item/radio/genre/{name}.png')
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"fractal:item/radio/genre/{name}"}}, open(f'{OUT}/assets/fractal/models/item/radio/genre/{name}.json', 'w'))
    json.dump({"model": {"type": "minecraft:model", "model": f"fractal:item/radio/genre/{name}"}}, open(f'{OUT}/assets/fractal/items/radio/genre/{name}.json', 'w'))
PINK, GOLD, SLATE_D = hexc('#FFC2F2'), hexc('#FFE680'), (0x3a, 0x3d, 0x52)
genre('anime', art([".....PPP.....", "....PPPPP....", "....PPPPP....", ".PP..PPP..PP.", "PPPP.YYY.PPPP", "PPPPPYYYPPPPP", ".PPPPYYYPPPP.",
                    "...PPPPPPP...", "..PPPP.PPPP..", ".PPPP...PPPP.", ".PPP.....PPP."], {'P': PINK, 'Y': GOLD}))
genre('anthems', art(["#............", "#PPPPPPPP....", "#PPPPPPPPPP..", "#PPPPPPPPPPP.", "#PPPPPPPPPP..", "#PPPPPPPP....", "#............",
                      "#............", "#............", "#............", "##..........."], {'#': (0xc9, 0xcc, 0xdb), 'P': hgrad('#8E2DE2', '#FF7BE5')}))
genre('christmas', art(["......Y......", ".....YYY.....", "......G......", ".....GGG.....", "....GGRGG....", "...GGGGGGG...", "....GGGGG....",
                        "...GGBGGGG...", "..GGGGGGGRG..", ".GGGGGGGGGGG.", "......T......", ".....TTT....."],
                       {'G': (0x3f, 0xa3, 0x4d), 'R': hexc('#FF5A6E'), 'B': hexc('#7FA8FF'), 'Y': GOLD, 'T': (0x7a, 0x52, 0x30)}))
genre('classical-jazz', art([".....##.....", "....####....", "...######...", "...######...", "..########..", "..########..", "..########..",
                             ".##########.", "############", "............", ".....##....."], {'#': (0xf2, 0xc9, 0x4c)}))
eq = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
for i, hgt in enumerate((5, 9, 6, 11, 7)):
    for x in range(2 + i * 3, 4 + i * 3):
        for y in range(14 - hgt, 14): eq.putpixel((x, y), mix(hexc('#7FE7FF'), hexc('#B266FF'), (14 - y) / 11) + (255,))
genre('electronic', outline(eq))
genre('games', art(["..##########..", ".#DDDDDDDDDD#.", "#DDWDDDDDDRDD#", "#DWWWDDDDGDBD#", "#DDWDDDDDDYDD#", "#DDDDD##DDDDD#", ".#DDD#..#DDD#.", "..###....###.."],
                   {'#': SLATE_D, 'D': (0x8a, 0x8f, 0xae), 'W': (255, 255, 255), 'R': hexc('#FF5A6E'), 'G': hexc('#9BFFC4'), 'B': hexc('#7FA8FF'), 'Y': GOLD}))
genre('hip-hop', art(["....####....", "...#WWWW#...", "...WWWWWW...", "...WGWGWW...", "...WWWWWW...", "...#WWWW#...", "....####....", ".....##.....",
                      ".....##.....", "....####...."], {'#': SLATE_D, 'W': (0xe1, 0xe3, 0xee), 'G': (0x8a, 0x8f, 0xae)}))
genre('memes', art(["...######...", "..########..", ".##########.", "##BB####BB##", "############", "############", "#BBBBBBBBBB#", ".#BWWWWWWB#.",
                    "..#BBBBBB#..", "...######..."], {'#': (0xff, 0xd8, 0x4a), 'B': (0x4a, 0x2c, 0x10), 'W': (255, 255, 255)}))
genre('pop', art([".##...##.", "####.####", "#########", "#########", ".#######.", "..#####..", "...###...", "....#...."], {'#': hexc('#FF7BE5')}))
genre('rock', art(["..........NN", ".........NN.", "........NN..", ".......NN...", "......NN....", "..##.NN.....", ".#######....", "########....",
                   ".######.....", "..######....", "...####....."], {'#': hexc('#FF5A6E'), 'N': (0x9a, 0x6a, 0x3c)}))
genre('tv-film', art(["#W#W#W#W#W#", "W#W#W#W#W#W", "...........", "DDDDDDDDDDD", "DDDDDDDDDDD", "DDWWWWWWWDD", "DDDDDDDDDDD", "DDWWWWWDDDD", "DDDDDDDDDDD"],
                     {'#': (0x22, 0x23, 0x30), 'W': (255, 255, 255), 'D': SLATE_D}, shade=False))
genre('default', note2)
# a panel-coloured square slightly bigger than a slot, to cover an unused well
blank = Image.new('RGBA', (16, 16), (0x4b, 0x4f, 0x65, 255))
for dd in ('textures/item/radio', 'models/item/radio', 'items/radio'): os.makedirs(f'{OUT}/assets/fractal/{dd}', exist_ok=True)
blank.save(f'{OUT}/assets/fractal/textures/item/radio/blank.png')
json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "fractal:item/radio/blank"},
           "display": {"gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.125, 1.125, 1.125]}}},
          open(f'{OUT}/assets/fractal/models/item/radio/blank.json', 'w'))
json.dump({"model": {"type": "minecraft:model", "model": "fractal:item/radio/blank"}}, open(f'{OUT}/assets/fractal/items/radio/blank.json', 'w'))
genre('hip-hop', art([".....####.....", "....#WWWW#....", "...#WGWGWW#...", "...#WWWWWW#...", "...#WGWGWW#...", "....#WWWW#....", ".....####.....",
                      "......##......", "..##..##..##..", "...##.##.##...", "....######....", "......##......", ".....####....."],
                     {'#': SLATE_D, 'W': (0xe1, 0xe3, 0xee), 'G': (0x8a, 0x8f, 0xae)}))

# 13. the Radio's purple boss bar: a dark track like a music staff, filled with the brand gradient
os.makedirs(f'{OUT}/assets/minecraft/textures/gui/sprites/boss_bar', exist_ok=True)
track = Image.new('RGBA', (182, 5), (0x23, 0x25, 0x36, 255))
for x in range(182):
    for y in (0, 4): track.putpixel((x, y), (0x12, 0x13, 0x1c, 255))
    if x % 3 == 0 and 2 <= x <= 179: track.putpixel((x, 2), (0x3d, 0x40, 0x5a, 255))   # a dotted staff line
    if x % 26 == 13: track.putpixel((x, 1), (0x4b, 0x4f, 0x65, 255)); track.putpixel((x, 3), (0x4b, 0x4f, 0x65, 255))  # bar lines
for y in range(5): track.putpixel((0, y), (0x12, 0x13, 0x1c, 255)); track.putpixel((181, y), (0x12, 0x13, 0x1c, 255))
track.save(f'{OUT}/assets/minecraft/textures/gui/sprites/boss_bar/purple_background.png')
fill = Image.new('RGBA', (182, 5), (0, 0, 0, 0))
for x in range(182):
    c = mix(hexc('#8E2DE2'), hexc('#FF7BE5'), x / 181)
    fill.putpixel((x, 0), (0x2a, 0x17, 0x40, 255)); fill.putpixel((x, 4), (0x2a, 0x17, 0x40, 255))
    fill.putpixel((x, 1), mix(c, (255, 255, 255), 0.4) + (255,))
    fill.putpixel((x, 2), c + (255,)); fill.putpixel((x, 3), mix(c, (0, 0, 0), 0.15) + (255,))
for y in range(5): fill.putpixel((0, y), (0x2a, 0x17, 0x40, 255))
fill.save(f'{OUT}/assets/minecraft/textures/gui/sprites/boss_bar/purple_progress.png')
# Staff Only: an orange shield with a white tick
STAFF = [".RRRRRRRRRRR.", "RLLLLLLDDDDDR", "RLLLLLLDDDDDR", "RLLLLLLDDDWDR", "RLLLLLLDDWWDR", "RLLWLLLDWWDDR", "RLLWWLLWWDDDR",
         "RLLLWWWWDDDDR", ".RLLLWWDDDDR.", ".RLLLLLDDDDR.", "..RLLLLDDDR..", "...RLLLDDR...", "....RLLDR....", ".....RRR....."]
model('audience-staff', art(STAFF, {'R': hexc('#B85A1E'), 'L': hexc('#FFC48A'), 'D': hexc('#F0923F'), 'W': (255, 255, 255)}, shade=False))
# Staff Only: a magic staff (a wooden staff with a big glowing orange orb)
staff = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); dr = ImageDraw.Draw(staff)
dr.line([(1, 14), (8, 7)], fill=(0x8a, 0x5a, 0x32, 255), width=3)
dr.line([(2, 14), (8, 8)], fill=(0x6b, 0x43, 0x24, 255), width=1)
dr.rectangle((6, 7, 8, 9), fill=(0xf2, 0xc9, 0x4c, 255))                       # gold collar
dr.ellipse((7, 1, 14, 8), fill=(0xff, 0x8a, 0x3a, 255))                         # orb
dr.ellipse((8, 2, 12, 6), fill=(0xff, 0xb3, 0x6b, 255))
dr.ellipse((9, 2, 10, 3), fill=(0xff, 0xf0, 0xd8, 255))
staff = outline(staff)
model('audience-staff', staff)
# Donors Only: a bigger crown
CROWN2 = ["#.....##.....#", "##....##....##", "###..####..###", "####.####.####", "##############", "##############",
          "##RR##BB##GG##", "##############", "##############"]
model('audience-donors', art(CROWN2, {'#': lambda x, y, w, h: mix(hexc('#FFE680'), hexc('#E0A030'), y / (h - 1)),
                                      'R': hexc('#FF5A6E'), 'B': hexc('#7FA8FF'), 'G': hexc('#9BFFC4')}))
# Staff Only: an orange key
model('audience-staff', art(["..####........", ".######.......", "##....##......", "##....########", "##....########", "##....##..##.#", ".######...##.#", "..####........"],
                            {'#': lambda x, y, w, h: mix(hexc('#FFB36B'), hexc('#E8893A'), y / (h - 1))}))

# 14. redrawn genre icons and a greyed-out "last page" arrow
genre('hip-hop', art(["...HHHHHHHH...", "...H......H...", "BBBBBBBBBBBBBB", "BSSSSBPPBSSSSB", "BSCCSBPPBSCCSB", "BSCCSBBBBSCCSB", "BSSSSBYYBSSSSB", "BBBBBBBBBBBBBB"],
                     {'H': (0xc9, 0xcc, 0xdb), 'B': (0x6a, 0x6f, 0x8d), 'S': (0xe1, 0xe3, 0xee), 'C': (0x2c, 0x2e, 0x3e), 'P': hexc('#B266FF'), 'Y': hexc('#FFE680')}, shade=False))
genre('memes', art(["...######...", ".##########.", ".##########.", "############", "#KKKKKKKKKK#", "#KKKK##KKKK#", "##KK####KK##", "############",
                    "##K######K##", ".##KKKKKK##.", ".##########.", "...######..."], {'#': (0xff, 0xd8, 0x4a), 'K': (0x22, 0x23, 0x30)}))
genre('tv-film', art(["...A......A...", "....A....A....", ".....A..A.....", "FFFFFFFFFFFFFF", "FSSSSSSSSSFFKF", "FSSSSSSSSSFFFF", "FSSSSSSSSSFFKF",
                      "FSSSSSSSSSFFFF", "FSSSSSSSSSFFFF", "FFFFFFFFFFFFFF", ".FF........FF."],
                     {'A': (0xc9, 0xcc, 0xdb), 'F': (0x5a, 0x5f, 0x7a), 'K': hexc('#FFE680'),
                      'S': lambda x, y, w, h: mix(hexc('#7FE7FF'), hexc('#B266FF'), (x + y) / 14)}, shade=False))
genre('games', art([".DDDD....DDDD.", "DDDDDDDDDDDDDD", "DDWDDDDDDDDRDD", "DWWWDDDDDDGDBD", "DDWDDDDDDDDYDD", "DDDDDDDDDDDDDD", "DDDD.DDDD.DDDD", "DDD........DDD", ".D..........D."],
                   {'D': hexc('#9A7BFF'), 'W': (255, 255, 255), 'R': hexc('#FF5A6E'), 'G': hexc('#9BFFC4'), 'B': hexc('#7FE7FF'), 'Y': GOLD}))
gtr = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); dr = ImageDraw.Draw(gtr)
dr.line([(13, 2), (7, 8)], fill=(0x9a, 0x6a, 0x3c, 255), width=2)
dr.rectangle((12, 1, 14, 2), fill=(0x4a, 0x2c, 0x10, 255))
dr.ellipse((1, 8, 8, 14), fill=hexc('#FF5A6E') + (255,)); dr.ellipse((4, 6, 9, 11), fill=hexc('#FF5A6E') + (255,))
dr.ellipse((2, 9, 4, 11), fill=(0xff, 0x9a, 0xa8, 255)); dr.rectangle((5, 9, 6, 10), fill=(0x22, 0x23, 0x30, 255))
genre('rock', outline(gtr))
pno = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); dr = ImageDraw.Draw(pno)
dr.rectangle((1, 2, 14, 3), fill=hexc('#B266FF') + (255,))
dr.rectangle((1, 4, 14, 13), fill=(0xf4, 0xf5, 0xfb, 255))
for x in (4, 8, 12): dr.line([(x, 4), (x, 13)], fill=(0xa9, 0xae, 0xc8, 255))
for x in (3, 7, 11): dr.rectangle((x, 4, x + 2, 9), fill=(0x22, 0x23, 0x30, 255))
genre('classical-jazz', outline(pno))
off = Image.open(f'{OUT}/assets/fractal/textures/item/menu/next.png').convert('RGBA'); px = off.load()
for x in range(16):
    for y in range(16):
        r, g, b, a = px[x, y]
        if a and (r, g, b) != (0x1a, 0x1b, 0x26): px[x, y] = (0x62, 0x66, 0x80, a)
        elif a: px[x, y] = (0x38, 0x3b, 0x4f, a)
radio_model('next-off', off)

# 15. mouse and Shift glyphs for item hints (font fractal:lobby: F010 left, F011 right, F012 any click, F013 Shift +)
KEYL, KEYB, KEYP = (0xe1, 0xe3, 0xee), (0xb4, 0xb8, 0xcf), hexc('#FF7BE5')
MOUSE = [".LLL.RRR.", "LLLL.RRRR", "LLLL.RRRR", "LLLL.RRRR", ".........", "BBBBBBBBB", "BBBBBBBBB", "BBBBBBBBB", "BBBBBBBBB", ".BBBBBBB."]
def mouse(left, right):
    img = Image.new('RGBA', (9, 10), (0, 0, 0, 0))
    for y, row in enumerate(MOUSE):
        for x, ch in enumerate(row):
            if ch == 'L': img.putpixel((x, y), (KEYP if left else KEYL) + (255,))
            elif ch == 'R': img.putpixel((x, y), (KEYP if right else KEYL) + (255,))
            elif ch == 'B': img.putpixel((x, y), KEYB + (255,))
    return img
F35 = {'S': ["###", "#..", "###", "..#", "###"], 'H': ["#.#", "#.#", "###", "#.#", "#.#"], 'I': ["###", ".#.", ".#.", ".#.", "###"],
       'F': ["###", "#..", "##.", "#..", "#.."], 'T': ["###", ".#.", ".#.", ".#.", ".#."]}
shift = Image.new('RGBA', (28, 10), (0, 0, 0, 0)); dr = ImageDraw.Draw(shift)
dr.rounded_rectangle((0, 0, 22, 9), radius=2, fill=(0x4b, 0x4f, 0x65, 255), outline=KEYL + (255,))
dr.line([(1, 9), (21, 9)], fill=KEYB + (255,))
for i, ch in enumerate("SHIFT"):
    for y, row in enumerate(F35[ch]):
        for x, c in enumerate(row):
            if c == '#': shift.putpixel((2 + i * 4 + x, 2 + y), (255, 255, 255, 255))
dr.line([(24, 5), (26, 5)], fill=KEYL + (255,)); dr.line([(25, 4), (25, 6)], fill=KEYL + (255,))
lobby_font = json.load(open(f'{OUT}/assets/fractal/font/lobby.json'))
for code, name, img in ((0xF010, 'click_left', mouse(True, False)), (0xF011, 'click_right', mouse(False, True)),
                        (0xF012, 'click_any', mouse(True, True)), (0xF013, 'key_shift', shift)):
    img.save(f'{OUT}/assets/fractal/textures/gui/{name}.png')
    lobby_font['providers'].append({"type": "bitmap", "file": f"fractal:gui/{name}.png", "ascent": 8, "height": 10, "chars": [chr(code)]})
json.dump(lobby_font, open(f'{OUT}/assets/fractal/font/lobby.json', 'w'), indent=1)

# 16. bigger pop heart, centred equalizer, shorter click glyphs (8px, so stacked lines don't touch)
genre('pop', art(["..###...###..", ".#####.#####.", "#############", "#############", "#############", ".###########.", "..#########..", "...#######...",
                  "....#####....", ".....###.....", "......#......"], {'#': hexc('#FF7BE5')}))
eq = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
for i, hgt in enumerate((5, 9, 6, 11, 7)):
    for x in range(1 + i * 3, 3 + i * 3):
        for y in range(14 - hgt, 14): eq.putpixel((x, y), mix(hexc('#7FE7FF'), hexc('#B266FF'), (14 - y) / 11) + (255,))
genre('electronic', outline(eq))
MOUSE = [".LLL.RRR.", "LLLL.RRRR", "LLLL.RRRR", ".........", "BBBBBBBBB", "BBBBBBBBB", "BBBBBBBBB", ".BBBBBBB."]
def mouse8(left, right):
    img = Image.new('RGBA', (9, 8), (0, 0, 0, 0))
    for y, row in enumerate(MOUSE):
        for x, ch in enumerate(row):
            if ch == 'L': img.putpixel((x, y), (KEYP if left else KEYL) + (255,))
            elif ch == 'R': img.putpixel((x, y), (KEYP if right else KEYL) + (255,))
            elif ch == 'B': img.putpixel((x, y), KEYB + (255,))
    return img
shift = Image.new('RGBA', (28, 8), (0, 0, 0, 0)); dr = ImageDraw.Draw(shift)
dr.rounded_rectangle((0, 0, 22, 6), radius=1, fill=KEYL + (255,))
dr.line([(1, 7), (21, 7)], fill=(0x8a, 0x8f, 0xae, 255))
for i, ch in enumerate("SHIFT"):
    for y, row in enumerate(F35[ch]):
        for x, c in enumerate(row):
            if c == '#': shift.putpixel((2 + i * 4 + x, 1 + y), (0x2c, 0x2e, 0x3e, 255))
dr.line([(24, 4), (26, 4)], fill=KEYL + (255,)); dr.line([(25, 3), (25, 5)], fill=KEYL + (255,))
lobby_font = json.load(open(f'{OUT}/assets/fractal/font/lobby.json'))
lobby_font['providers'] = [p for p in lobby_font['providers'] if not any(k in p.get('file', '') for k in ('click_', 'key_shift'))]
for code, name, img in ((0xF010, 'click_left', mouse8(True, False)), (0xF011, 'click_right', mouse8(False, True)),
                        (0xF012, 'click_any', mouse8(True, True)), (0xF013, 'key_shift', shift)):
    img.save(f'{OUT}/assets/fractal/textures/gui/{name}.png')
    lobby_font['providers'].append({"type": "bitmap", "file": f"fractal:gui/{name}.png", "ascent": 7, "height": 8, "chars": [chr(code)]})
json.dump(lobby_font, open(f'{OUT}/assets/fractal/font/lobby.json', 'w'), indent=1)

# 17. Double Jump: a winged boot
BOOT = ["WW....BBBBB...", "WWW...BBBBB...", "WWWW..BCCCB...", ".WWWW.BBBBB...", "..WWWWBBBBB...", "...WWWBBBBB...", "......BBBBBBB.",
        "......BBBBBBBB", "......BBBBBBBB", "......SSSSSSSS", "......SSS.SSSS"]
model('double-jump', art(BOOT, {'B': hexc('#E0483C'), 'C': hexc('#FFE680'), 'S': (0x7a, 0x4a, 0x2a), 'W': (0xf4, 0xf5, 0xfb)}))
BOOT = ["......TTTTT.....", "......BBBBB.....", ".W....BBBBB.....", "WWW...BBBBB.....", "WWWWW.BBBBB.....", ".WWWWWBBBBB.....", "..WWWWBBBBBBB...",
        "....WWBBBBBBBBB.", "......BBBBBBBBBB", "......BBBBBBBBBB", "......SSSSSSSSSS"]
model('double-jump', art(BOOT, {'B': hexc('#E0483C'), 'T': hexc('#FFE680'), 'S': (0x6b, 0x40, 0x24), 'W': (0xf4, 0xf5, 0xfb)}, shade=False))
# Double Jump: an original green boot on a spring
BOOT = ["...TTTTTT.......", "...GGGGGG.......", "...GGGGGG.......", "...GLGGGG.......", "...GLGGGG.......", "...GGGGGGGGG....", "...GGGGGGGGGGG..",
        "...GGGGGGGGGGGG.", "...DDDDDDDDDDDD.", "....SSSSSSSS....", ".....SSSSSS.....", "....SSSSSSSS....", ".....SSSSSS....."]
model('double-jump', art(BOOT, {'G': (0x4c, 0xb8, 0x3a), 'L': (0x9a, 0xe6, 0x7a), 'D': (0x2f, 0x7d, 0x28), 'T': (0xf4, 0xf5, 0xfb),
                                'S': lambda x, y, w, h: (0xc9, 0xcc, 0xdb) if y % 2 else (0x8a, 0x8f, 0xae)}, shade=False))

# 18. logos: "LOBBY" in the style of Survival's title (purple), and the FractalSMP F for the tab list
import random
LETTERS = {'L': ["#....", "#....", "#....", "#....", "#####"], 'O': ["#####", "#...#", "#...#", "#...#", "#####"],
           'B': ["####.", "#...#", "####.", "#...#", "#####"], 'Y': ["#...#", "#...#", "#####", "..#..", "..#.."]}
def block_logo(word, cw=9, ch=8, gap=5, depth=14, pad=4):
    rnd = random.Random(7)
    width = len(word) * 5 * cw + (len(word) - 1) * gap
    W2, H2 = width + pad * 2, 5 * ch + depth + pad * 2
    face = Image.new('L', (W2, H2), 0); fd = ImageDraw.Draw(face)
    for i, c in enumerate(word):
        ox = pad + i * (5 * cw + gap)
        for r, row in enumerate(LETTERS[c]):
            for col, v in enumerate(row):
                if v == '#': fd.rectangle((ox + col * cw, pad + r * ch, ox + (col + 1) * cw - 1, pad + (r + 1) * ch - 1), fill=255)
    img = Image.new('RGBA', (W2, H2), (0, 0, 0, 0)); fp = face.load()
    solid = Image.new('L', (W2, H2), 0); sp = solid.load()          # face plus its extrusion downwards
    for x in range(W2):
        for y in range(H2):
            if fp[x, y]:
                for d in range(depth + 1):
                    if y + d < H2: sp[x, y + d] = 255
    drip = [0] * W2; level = 5 * ch * 0.45
    for x in range(W2):                                              # jagged line where the light top meets the purple
        if x % 3 == 0: level = min(5 * ch * 0.62, max(5 * ch * 0.3, level + rnd.choice((-3, -2, 0, 2, 3))))
        drip[x] = pad + int(level)
    for x in range(W2):
        for y in range(H2):
            if not sp[x, y]: continue
            if fp[x, y]:
                if y < drip[x]: c = mix(hexc('#FBE8FF'), hexc('#EBC4FF'), (y - pad) / (5 * ch * 0.6))
                elif y < drip[x] + 3: c = hexc('#F6DCFF') if (x // 3) % 2 else hexc('#D9A0FF')
                else: c = mix(hexc('#C77DFF'), hexc('#8E2DE2'), (y - drip[x]) / (5 * ch * 0.6))
                if rnd.random() < 0.05: c = mix(c, (255, 255, 255), 0.25)
            else:
                dd = 0
                while y - dd >= 0 and not fp[x, y - dd]: dd += 1     # how far below the face
                c = mix(hexc('#5E1FA8'), hexc('#35105E'), dd / depth)
            img.putpixel((x, y), c + (255,))
    out = img.copy()                                                 # dark outline, 2px
    for x in range(W2):
        for y in range(H2):
            if sp[x, y]: continue
            if any(0 <= x + dx < W2 and 0 <= y + dy < H2 and sp[x + dx, y + dy] for dx in range(-2, 3) for dy in range(-2, 3)):
                out.putpixel((x, y), (0x22, 0x0b, 0x40, 255))
    return out
lobby_logo = block_logo("LOBBY")
lobby_logo.save(f'{OUT}/assets/fractal/textures/gui/logo_lobby.png')
ficon = Image.open(sys.argv[6]).convert('RGBA'); ficon = ficon.crop(ficon.getbbox())
ficon = ficon.resize((round(ficon.width * 64 / ficon.height), 64), Image.LANCZOS)
ficon.save(f'{OUT}/assets/fractal/textures/gui/logo_fractal.png')
lobby_font = json.load(open(f'{OUT}/assets/fractal/font/lobby.json'))
lobby_font['providers'].append({"type": "bitmap", "file": "fractal:gui/logo_lobby.png", "ascent": 19, "height": 24, "chars": [""]})
lobby_font['providers'].append({"type": "bitmap", "file": "fractal:gui/logo_fractal.png", "ascent": 29, "height": 32, "chars": [""]})
json.dump(lobby_font, open(f'{OUT}/assets/fractal/font/lobby.json', 'w'), indent=1)
print('logo sizes', lobby_logo.size, ficon.size)

# 19. a new, chunkier LOBBY logo (1:1 pixels, thick strokes) and a slightly smaller F for the tab list
def chunky(word):
    H, W, S = 13, 12, 4                     # letter height, width, stroke
    def letter(c):
        g = [[False] * W for _ in range(H)]
        def box(x0, y0, x1, y1, v=True):
            for y in range(y0, y1 + 1):
                for x in range(x0, x1 + 1): g[y][x] = v
        if c == 'L':
            box(0, 0, S - 1, H - 1); box(0, H - S, W - 1, H - 1)
        elif c == 'O':
            box(0, 0, W - 1, H - 1); box(S, S, W - S - 1, H - S - 1, False)
            for x, y in ((0, 0), (W - 1, 0), (0, H - 1), (W - 1, H - 1)): g[y][x] = False
        elif c == 'B':
            box(0, 0, W - 1, H - 1); box(S, 3, W - S - 1, 4, False); box(S, 8, W - S - 1, 9, False)
            for x, y in ((W - 1, 0), (W - 1, H - 1), (W - 1, 6)): g[y][x] = False
        elif c == 'Y':
            box(0, 0, S - 1, 5); box(W - S, 0, W - 1, 5); box(0, 4, W - 1, 7); box(S, 6, W - S - 1, H - 1)
            g[7][0] = g[7][W - 1] = False
        return g
    gap, depth, pad = 2, 3, 2
    width = len(word) * W + (len(word) - 1) * gap
    img = Image.new('RGBA', (width + pad * 2, H + depth + pad * 2), (0, 0, 0, 0))
    face = set()
    for i, c in enumerate(word):
        for y, row in enumerate(letter(c)):
            for x, v in enumerate(row):
                if v: face.add((pad + i * (W + gap) + x, pad + y))
    # the extrusion only shows below the letters, never inside their holes
    solid = face | {(x, y + d) for (x, y) in face for d in range(1, depth + 1)
                    if (x, y + d) not in face and not any((x, y + d + k) in face for k in range(1, H))}
    for (x, y) in solid:
        if (x, y) in face:
            t = (y - pad) / (H - 1)
            c = mix(hexc('#F3D9FF'), hexc('#C77DFF'), t / 0.5) if t < 0.5 else mix(hexc('#B266FF'), hexc('#8E2DE2'), (t - 0.5) / 0.5)
            if (x, y - 1) not in face: c = mix(c, (255, 255, 255), 0.6)          # light top edge
        else:
            c = (0x5e, 0x1f, 0xa8) if (x, y - 1) in face else (0x3d, 0x12, 0x70)  # extrusion
        img.putpixel((x, y), c + (255,))
    out = img.copy()
    for x in range(img.width):
        for y in range(img.height):
            if (x, y) not in solid and any((x + dx, y + dy) in solid for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                out.putpixel((x, y), (0x1c, 0x08, 0x36, 255))
    return out
lobby_logo = chunky("LOBBY")
lobby_logo.save(f'{OUT}/assets/fractal/textures/gui/logo_lobby.png')
lobby_font = json.load(open(f'{OUT}/assets/fractal/font/lobby.json'))
for p in lobby_font['providers']:
    if p.get('file') == 'fractal:gui/logo_lobby.png': p.update(height=lobby_logo.height, ascent=11)
    if p.get('file') == 'fractal:gui/logo_fractal.png': p.update(height=30, ascent=30)
json.dump(lobby_font, open(f'{OUT}/assets/fractal/font/lobby.json', 'w'), indent=1)
print('lobby logo', lobby_logo.size)

# 20. little icons for the tab list: a shopping bag (store) and a blurple chat bubble (Discord)
BAG = ["..HHHH..", ".H....H.", ".H....H.", "BBBBBBBB", "BBBBBBBB", "BBWBBBBB", "BBBBBBBB", "BBBBBBBB", ".BBBBBB."]
bag = Image.new('RGBA', (8, 9), (0, 0, 0, 0))
for y, row in enumerate(BAG):
    for x, ch in enumerate(row):
        if ch == 'H': bag.putpixel((x, y), (0xff, 0xe6, 0x80, 255))
        elif ch == 'B': bag.putpixel((x, y), mix(hexc('#FF7BE5'), hexc('#B266FF'), y / 8) + (255,))
        elif ch == 'W': bag.putpixel((x, y), (255, 255, 255, 255))
BUB = [".BBBBBBB.", "BBBBBBBBB", "BBWBBBWBB", "BBWBBBWBB", "BBBBBBBBB", "BWBBBBBWB", "BBWWWWWBB", ".BBBBBBB.", "..BB.....", ".B......."]
bub = Image.new('RGBA', (9, 10), (0, 0, 0, 0))
for y, row in enumerate(BUB):
    for x, ch in enumerate(row):
        if ch == 'B': bub.putpixel((x, y), (0x58, 0x65, 0xf2, 255))
        elif ch == 'W': bub.putpixel((x, y), (255, 255, 255, 255))
bag.save(f'{OUT}/assets/fractal/textures/gui/icon_store.png'); bub.save(f'{OUT}/assets/fractal/textures/gui/icon_discord.png')
lobby_font = json.load(open(f'{OUT}/assets/fractal/font/lobby.json'))
lobby_font['providers'].append({"type": "bitmap", "file": "fractal:gui/icon_store.png", "ascent": 8, "height": 9, "chars": [""]})
lobby_font['providers'].append({"type": "bitmap", "file": "fractal:gui/icon_discord.png", "ascent": 8, "height": 10, "chars": [""]})
json.dump(lobby_font, open(f'{OUT}/assets/fractal/font/lobby.json', 'w'), indent=1)
# Discord icon: the logo cut out of Survival's Discord banner (CrazySurvival pack)
banner = Image.open(f'{ICONS}/../gui/ui/announcements/discord.png').convert('RGBA')
dc = Image.new('RGBA', (23, 17), (0, 0, 0, 0))
for x in range(23):
    for y in range(17):
        r, g, b, a = banner.getpixel((28 + x, 19 + y))
        if min(r, g, b) > 0xd8: dc.putpixel((x, y), (r, g, b, 255))
dc = dc.crop(dc.getbbox())
padded = Image.new('RGBA', (dc.width + 2, dc.height + 2), (0, 0, 0, 0)); padded.alpha_composite(dc, (1, 1)); dc = padded.copy()
for x in range(padded.width):
    for y in range(padded.height):
        if padded.getpixel((x, y))[3]: continue
        if any(0 <= x + dx < padded.width and 0 <= y + dy < padded.height and padded.getpixel((x + dx, y + dy))[3]
               for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            dc.putpixel((x, y), (0x31, 0x49, 0x8c, 255))
lobby_font = json.load(open(f'{OUT}/assets/fractal/font/lobby.json'))
dc.save(f'{OUT}/assets/fractal/textures/gui/icon_discord.png')
for p in lobby_font['providers']:
    if p.get('file') == 'fractal:gui/icon_discord.png': p.update(height=9, ascent=8)
json.dump(lobby_font, open(f'{OUT}/assets/fractal/font/lobby.json', 'w'), indent=1)
print('discord icon', dc.size)

# 21. status emojis (Survival's chat emojis): item models fractal:status/NAME for the status menu, and font
# glyphs U+F300 on (fractal:lobby, in the order below) for "Status: <emoji> Happy" under players' names
EMOJIS = ['angel', 'angry', 'begging', 'clown', 'cool', 'crying', 'dead', 'evil', 'eyes', 'happy', 'heart', 'joy',
          'kiss', 'love', 'melt', 'mindblown', 'money', 'poop', 'sad', 'scream', 'skull', 'sweat', 'thinking',
          'thumbsup', 'wink']  # never reorder: the plugin maps names to glyphs by this order (MenuStyle.EMOJIS)
emoji_dir = f'{ICONS}/../gui/emojis'
for d in ('textures/item/status', 'models/item/status', 'items/status', 'textures/gui/status'):
    os.makedirs(f'{OUT}/assets/fractal/{d}', exist_ok=True)
lobby_font = json.load(open(f'{OUT}/assets/fractal/font/lobby.json'))
for i, name in enumerate(EMOJIS):
    img = Image.open(f'{emoji_dir}/{name}.png').convert('RGBA')
    img.save(f'{OUT}/assets/fractal/textures/item/status/{name}.png')
    img.save(f'{OUT}/assets/fractal/textures/gui/status/{name}.png')
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"fractal:item/status/{name}"}},
              open(f'{OUT}/assets/fractal/models/item/status/{name}.json', 'w'))
    json.dump({"model": {"type": "minecraft:model", "model": f"fractal:item/status/{name}"}},
              open(f'{OUT}/assets/fractal/items/status/{name}.json', 'w'))
    lobby_font['providers'].append({"type": "bitmap", "file": f"fractal:gui/status/{name}.png", "ascent": 8, "height": 9,
                                    "chars": [chr(0xF300 + i)]})
json.dump(lobby_font, open(f'{OUT}/assets/fractal/font/lobby.json', 'w'), indent=1)
print('status emojis', len(EMOJIS))

# 22. the AFK clock (U+F310, fractal:lobby) shown before "AFK" under idle players' names
CLOCK = ["...RRRRR...",
         "..RWWWWWR..",
         ".RWWWDWWWR.",
         "RWWWWDWWWWR",
         "RWWWWDWWWWR",
         "RWWWWDDDWWR",
         "RWWWWWWWWWR",
         "RWWWWWWWWWR",
         ".RWWWWWWWR.",
         "..RWWWWWR..",
         "...RRRRR..."]
clock = Image.new('RGBA', (11, 11), (0, 0, 0, 0))
for y, row in enumerate(CLOCK):
    for x, ch in enumerate(row):
        c = {'R': (0xFF, 0x5A, 0x5A), 'W': (0xFF, 0xF4, 0xF4), 'D': (0x3a, 0x1f, 0x2a)}.get(ch)
        if c: clock.putpixel((x, y), c + (255,))
clock.save(f'{OUT}/assets/fractal/textures/gui/afk_clock.png')
lobby_font = json.load(open(f'{OUT}/assets/fractal/font/lobby.json'))
lobby_font['providers'].append({"type": "bitmap", "file": "fractal:gui/afk_clock.png", "ascent": 8, "height": 9, "chars": [chr(0xF310)]})
json.dump(lobby_font, open(f'{OUT}/assets/fractal/font/lobby.json', 'w'), indent=1)

# 23. the AFK badge (U+E2A0, minecraft:default font, like the rank badges): the OWNER badge's style, light red
W = 25
AFK_ART = [list('.' + 'a' * (W - 2) + '.'), list('aa' + 'b' * (W - 4) + 'aa')] \
    + [list('a' + 'b' * (W - 2) + 'a') for _ in range(3)] + [list('b' + 'd' * (W - 2) + 'b') for _ in range(2)] \
    + [list('bb' + 'd' * (W - 4) + 'bb'), list('.' + 'b' * (W - 2) + '.')]
LETTERS = {'A': [".###.", "#...#", "#####", "#...#", "#...#"],
           'F': ["#####", "#....", "####.", "#....", "#...."],
           'K': ["#...#", "#..#.", "###..", "#..#.", "#...#"]}
for n, letter in enumerate('AFK'):  # top three rows white on the light band, bottom two pink on the dark band
    for y, row in enumerate(LETTERS[letter]):
        for x, ch in enumerate(row):
            if ch == '#': AFK_ART[2 + y][4 + n * 6 + x] = 'c' if y < 3 else 'e'
AFK_COL = {'a': '#ffa3a3', 'b': '#ff6f6f', 'c': '#ffffff', 'd': '#e04848', 'e': '#ffd9d6'}
afk_badge = Image.new('RGBA', (25, 9), (0, 0, 0, 0))
for y, row in enumerate(AFK_ART):
    for x, ch in enumerate(row):
        if ch in AFK_COL: afk_badge.putpixel((x, y), hexc(AFK_COL[ch]) + (255,))
afk_badge.save(f'{OUT}/assets/minecraft/textures/gui/ranks/afk.png')
default_font = json.load(open(f'{OUT}/assets/minecraft/font/default.json'))
default_font['providers'].append({"type": "bitmap", "file": "minecraft:gui/ranks/afk.png", "ascent": 8, "height": 9, "chars": [""]})
json.dump(default_font, open(f'{OUT}/assets/minecraft/font/default.json', 'w'), ensure_ascii=False, indent=1)

# 24. the NPC skins and cape (fractal:entity/npc/NAME), drawn by skins/make_skins.py
npc_dir = f'{OUT}/assets/fractal/textures/entity/npc'
os.makedirs(npc_dir, exist_ok=True)
subprocess.run([sys.executable, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'skins', 'make_skins.py'), npc_dir], check=True,
               cwd=os.path.join(os.path.dirname(os.path.abspath(__file__)), 'skins'))
for f in os.listdir(npc_dir):
    if f.startswith('preview_'): os.remove(f'{npc_dir}/{f}')
# The skins the NPCs actually wear (chosen on NameMC, in skins/src) replace the drawn ones; the cape stays available.
src_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'skins', 'src')
for f in os.listdir(src_dir):
    if f.endswith('.png'): shutil.copy(os.path.join(src_dir, f), f'{npc_dir}/{f}')
print('npc skins', sorted(os.listdir(npc_dir)))
