"""Diving board icon options and the touched-up sand castle icon: crisp pixel art (hard edges), centred."""
from PIL import Image, ImageDraw

WATER, WATER_D, WATER_L, FOAM = '#3fa8f0', '#1f6fc8', '#8fd8ff', '#ffffff'
WHITE, GREY, BLUE, STEEL = '#f4f4fa', '#b8bfd2', '#2f6fe0', '#8a92a8'


def centre(im):
    b = im.getbbox()
    out = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    out.alpha_composite(im, (round(16 - (b[0] + b[2]) / 2), round(16 - (b[1] + b[3]) / 2)))
    return out


def pool(d, x0, y0, x1, y1):
    d.rectangle((x0, y0, x1, y1), fill=WATER_D)
    d.rectangle((x0, y0, x1, y0 + 1), fill=WATER)
    for x in range(x0 + 1, x1, 4):                                     # little ripples
        d.line((x, y0 + 3, x + 1, y0 + 3), fill=WATER_L)
    d.rectangle((x0 - 1, y0 - 1, x0, y1), fill=WHITE)                   # pool edge


def splash(d, cx, y):
    for (dx, dy) in ((0, -5), (-2, -4), (2, -4), (-4, -2), (4, -2), (-1, -2), (1, -2)):
        d.point((cx + dx, y + dy), fill=FOAM)
    d.rectangle((cx - 3, y - 1, cx + 3, y), fill=WATER_L)


def ladder(d, x, y_top, y_bot):
    d.line((x, y_top, x, y_bot), fill=STEEL)
    d.line((x + 3, y_top, x + 3, y_bot), fill=STEEL)
    for y in range(y_top + 2, y_bot, 3):
        d.line((x, y, x + 3, y), fill=GREY)


def db1():
    """The whole thing: a ladder up a tower, the board out over the pool, and a splash."""
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    pool(d, 12, 23, 31, 31)
    ladder(d, 1, 7, 31)
    d.rectangle((5, 8, 7, 31), fill=GREY)                              # the tower
    d.rectangle((4, 6, 22, 7), fill=WHITE)                             # the board
    d.rectangle((8, 8, 22, 8), fill=BLUE)
    splash(d, 24, 23)
    return centre(img)


def db2():
    """The springboard bouncing, mid-flex, with dotted arc of a dive down into the pool."""
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    pool(d, 3, 24, 31, 31)
    d.rectangle((0, 9, 3, 31), fill=GREY)                              # the stand
    d.polygon([(0, 7), (14, 7), (21, 9), (21, 10), (14, 9), (0, 9)], fill=WHITE)   # the board, flexing down at its end
    d.line((1, 9, 14, 9), fill=BLUE)
    for (x, y) in ((22, 6), (24, 5), (26, 6), (27, 9), (28, 13), (28, 17), (28, 21)):
        d.point((x, y), fill='#ffd23a')                                # the dive's path
    splash(d, 28, 24)
    return centre(img)


def db3():
    """Close up: the end of a white board with its blue stripe, sticking out over sparkling water."""
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle((0, 17, 31, 31), fill=WATER_D)
    d.rectangle((0, 17, 31, 19), fill=WATER)
    for (x, y) in ((4, 23), (13, 27), (22, 22), (27, 28), (9, 29)):
        d.line((x, y, x + 2, y), fill=WATER_L)
    d.polygon([(0, 8), (26, 6), (27, 7), (27, 10), (0, 13)], fill=WHITE)
    d.line((0, 10, 26, 8), fill=BLUE)
    d.line((0, 13, 27, 10), fill=GREY)
    for (x, y) in ((24, 15), (26, 13), (29, 14)):
        d.point((x, y), fill=FOAM)
    return centre(img)


DIVE = [db1, db2, db3]


def sand_castle():
    """The sand castle (touched up): a wide wall with battlements, two side towers, a shorter middle tower with its
    own battlements and a dark door, and a proper red pennant flying from a pole on top."""
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    sand, light_, dark_, door = '#ecd08a', '#f8e6b0', '#c9a860', '#8a6a38'
    d.rectangle((1, 28, 30, 30), fill=dark_)                           # the sand it sits on
    d.rectangle((3, 18, 28, 28), fill=sand)                            # the wall
    for x in range(3, 29, 4):
        d.rectangle((x, 16, x + 1, 17), fill=sand)                     # its battlements
    for x0 in (2, 24):                                                 # side towers
        d.rectangle((x0, 13, x0 + 5, 28), fill=sand)
        d.rectangle((x0, 11, x0 + 1, 12), fill=sand); d.rectangle((x0 + 4, 11, x0 + 5, 12), fill=sand)
        d.rectangle((x0, 13, x0, 28), fill=light_)
        d.rectangle((x0 + 2, 17, x0 + 3, 19), fill=dark_)              # a window
    d.rectangle((11, 11, 20, 28), fill=sand)                           # the middle tower (lower than before)
    for x in (11, 14, 17, 20):
        d.rectangle((x, 9, x + (1 if x < 20 else 0), 10), fill=sand)
    d.rectangle((11, 11, 11, 28), fill=light_)
    d.rectangle((14, 22, 17, 28), fill=door); d.rectangle((15, 21, 16, 21), fill=door)   # the door, arched
    d.rectangle((15, 14, 16, 16), fill=dark_)                          # a window
    d.line((15, 2, 15, 8), fill='#6b3e1e')                             # the flag pole
    d.polygon([(16, 2), (22, 3), (24, 4), (22, 5), (16, 6)], fill='#e03a3a')   # the pennant, waving
    d.line((16, 4, 21, 4), fill='#ff6a5a')
    return centre(img)


def write_extras(out):
    """The animated pool water (a flat sheet, rippling), and the railgun's neon-blue energy ring sprite."""
    import json, os, math, random
    tex = f'{out}/assets/fractal/textures/item/gadget'
    os.makedirs(tex, exist_ok=True)
    # water: 32x32 frames, 8 of them, gentle moving ripples in blues with a few bright glints
    F, S = 8, 32
    sheet = Image.new('RGBA', (S, S * F))
    for f in range(F):
        ph = f / F * math.pi * 2
        for y in range(S):
            for x in range(S):
                w = math.sin(x * 0.4 + ph) + math.sin(y * 0.33 - ph) + math.sin((x + y) * 0.21 + ph * 2)
                k = (w + 3) / 6                                       # 0..1
                if k > 0.86: col = (170, 230, 255)
                elif k > 0.62: col = (80, 175, 240)
                elif k > 0.35: col = (52, 140, 225)
                else: col = (36, 108, 200)
                sheet.putpixel((x, f * S + y), col + (215,))
    sheet.save(f'{tex}/pool_water.png')
    json.dump({"animation": {"frametime": 3, "interpolate": True}}, open(f'{tex}/pool_water.png.mcmeta', 'w'))
    model = {"textures": {"t": "fractal:item/gadget/pool_water", "particle": "fractal:item/gadget/pool_water"},
             "elements": [{"from": [0, 8, 0], "to": [16, 8, 16], "shade": False, "light_emission": 8,
                           "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#t"}, "down": {"uv": [0, 0, 16, 16], "texture": "#t"}}}]}
    os.makedirs(f'{out}/assets/fractal/models/item/gadget', exist_ok=True)
    json.dump(model, open(f'{out}/assets/fractal/models/item/gadget/pool_water.json', 'w'))
    os.makedirs(f'{out}/assets/fractal/items/gadget', exist_ok=True)
    json.dump({"model": {"type": "minecraft:model", "model": "fractal:item/gadget/pool_water"}}, open(f'{out}/assets/fractal/items/gadget/pool_water.json', 'w'))
    # the railgun ring: a crisp neon-blue ring with a soft cyan glow inside and out
    ring = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    for y in range(32):
        for x in range(32):
            d = math.hypot(x + 0.5 - 16, y + 0.5 - 16)
            if 12.5 <= d <= 14.5: ring.putpixel((x, y), (60, 220, 255, 255))
            elif 11.5 <= d < 12.5 or 14.5 < d <= 15.5: ring.putpixel((x, y), (150, 240, 255, 150))
            elif 13.2 <= d <= 13.8: ring.putpixel((x, y), (230, 252, 255, 255))
    ring.save(f'{tex}/rail_ring.png')
    model = {"textures": {"t": "fractal:item/gadget/rail_ring", "particle": "fractal:item/gadget/rail_ring"},
             "elements": [{"from": [0, 0, 8], "to": [16, 16, 8], "shade": False, "light_emission": 15,
                           "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#t"}, "south": {"uv": [0, 0, 16, 16], "texture": "#t"}}}]}
    json.dump(model, open(f'{out}/assets/fractal/models/item/gadget/rail_ring.json', 'w'))
    json.dump({"model": {"type": "minecraft:model", "model": "fractal:item/gadget/rail_ring"}}, open(f'{out}/assets/fractal/items/gadget/rail_ring.json', 'w'))
