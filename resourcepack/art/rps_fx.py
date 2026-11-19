"""Rock Paper Shears pictures (1.41): the three hand signs, and a pair of shears for the icon."""
from PIL import Image, ImageDraw
OUT = (0x1a, 0x1b, 0x26)
def hexc(h): h = h.lstrip('#'); return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))
SKIN, SKIN_L, SKIN_D = hexc('#f2c39a'), hexc('#ffdcbc'), hexc('#cf946a')
def art(rows, pal):
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0)); px = im.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal: px[x, y] = pal[ch] + (255,)
    src = im.copy(); sp = src.load()
    for y in range(32):
        for x in range(32):
            if sp[x, y][3]: continue
            if any(0 <= x + a < 32 and 0 <= y + b < 32 and sp[x + a, y + b][3] for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = OUT + (255,)
    b = im.getbbox(); out = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    out.alpha_composite(im, (round(16 - (b[0] + b[2]) / 2), round(16 - (b[1] + b[3]) / 2))); return out

def shears():   # just a pair of shears, open
    rows = [""] * 32
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    blade, blade_l, handle = hexc('#d8dce8'), hexc('#ffffff'), hexc('#e03a3a')
    d.polygon([(15, 16), (28, 3), (30, 5), (17, 18)], fill=blade + (255,))     # upper blade
    d.line([(16, 16), (28, 4)], fill=blade_l + (255,))
    d.polygon([(15, 16), (28, 29), (26, 31), (13, 18)], fill=blade + (255,))   # lower blade
    d.ellipse((2, 4, 13, 14), fill=handle + (255,)); d.ellipse((5, 7, 10, 11), fill=(0, 0, 0, 0))
    d.ellipse((2, 18, 13, 28), fill=handle + (255,)); d.ellipse((5, 21, 10, 25), fill=(0, 0, 0, 0))
    d.line([(10, 12), (16, 16)], fill=handle + (255,), width=3); d.line([(10, 20), (16, 16)], fill=handle + (255,), width=3)
    im.putpixel((15, 16), hexc('#5a5e70') + (255,))
    return finish(im)

def finish(im):
    px = im.load(); src = im.copy(); sp = src.load()
    for y in range(32):
        for x in range(32):
            if sp[x, y][3]: continue
            if any(0 <= x + a < 32 and 0 <= y + b < 32 and sp[x + a, y + b][3] for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = OUT + (255,)
    b = im.getbbox(); out = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    out.alpha_composite(im, (round(16 - (b[0] + b[2]) / 2), round(16 - (b[1] + b[3]) / 2))); return out

def fist():     # a rock: a simple fist
    rows = [
        "................................",
        "................................",
        "................................",
        "................................",
        "................................",
        "........LLLL.LLLL.LLLL.LLL......",
        ".......LSSSSLSSSSLSSSSLSSSD.....",
        ".......LSSSSDSSSSDSSSSDSSSD.....",
        ".......LSSSSDSSSSDSSSSDSSSD.....",
        ".......LSSSSDSSSSDSSSSDSSSD.....",
        ".......LSSSSDSSSSDSSSSDSSSD.....",
        "......LSSSSSSSSSSSSSSSSSSSD.....",
        ".....LSSLLLLLLLSSSSSSSSSSSD.....",
        ".....LSSSSSSSSSDSSSSSSSSSSD.....",
        ".....LSSSSSSSSSDSSSSSSSSSSD.....",
        "......LSSSSSSSDSSSSSSSSSSSD.....",
        ".......LSSSSSSSSSSSSSSSSSSD.....",
        ".......LSSSSSSSSSSSSSSSSSD......",
        "........LSSSSSSSSSSSSSSSD.......",
        ".........SSSSSSSSSSSSSSD........",
        ".........BBBBBBBBBBBBBBB........",
        ".........BBBBBBBBBBBBBBB........",
        ".........bbbbbbbbbbbbbbb........",
    ]
    return art(rows, {'S': SKIN, 'L': SKIN_L, 'D': SKIN_D, 'B': hexc('#3c8de0'), 'b': hexc('#2a6ac0')})

def peace():    # shears as a hand: two fingers up
    rows = [
        "................................",
        "..........LL......LL............",
        ".........LSSD....LSSD...........",
        ".........LSSD....LSSD...........",
        ".........LSSD....LSSD...........",
        ".........LSSD...LSSD............",
        "..........LSSD..LSSD............",
        "..........LSSD..LSSD............",
        "..........LSSD.LSSD.............",
        "...........LSSDLSSD.............",
        "...........LSSDLSSD.............",
        "........LLLLSSSSSSSLLLL.........",
        ".......LSSSSSSSSSSSSSSSD........",
        ".......LSSDDDDSSSDDDDSSD........",
        ".......LSSSSSSSSSSSSSSSD........",
        "......LLSSSSSSSSSSSSSSSD........",
        ".....LSSSSSSSSSSSSSSSSSD........",
        ".....LSSSSSSSSSSSSSSSSD.........",
        "......LSSSSSSSSSSSSSSSD.........",
        ".......LSSSSSSSSSSSSSD..........",
        "........SSSSSSSSSSSSS...........",
        "........BBBBBBBBBBBBB...........",
        "........BBBBBBBBBBBBB...........",
        "........bbbbbbbbbbbbb...........",
    ]
    return art(rows, {'S': SKIN, 'L': SKIN_L, 'D': SKIN_D, 'B': hexc('#3c8de0'), 'b': hexc('#2a6ac0')})


def paper():    # paper: an open hand, fingers together
    rows = [
        "................................",
        "...........LL.LL.LL.............",
        "..........LSSLSSLSSD............",
        "..........LSSLSSLSSD.LL.........",
        "..........LSSDSSDSSDLSSD........",
        "..........LSSDSSDSSDLSSD........",
        "..........LSSDSSDSSDLSSD........",
        "..........LSSDSSDSSDLSSD........",
        "..........LSSDSSDSSDLSSD........",
        "..........LSSDSSDSSDLSSD........",
        "..........LSSSSSSSSSSSSD........",
        "....LL....LSSSSSSSSSSSSD........",
        "...LSSD...LSSSSSSSSSSSSD........",
        "....LSSD..LSSSSSSSSSSSSD........",
        ".....LSSD.LSSSSSSSSSSSSD........",
        "......LSSDLSSSSSSSSSSSSD........",
        ".......LSSSSSSSSSSSSSSSD........",
        "........LSSSSSSSSSSSSSD.........",
        ".........LSSSSSSSSSSSD..........",
        "..........SSSSSSSSSSS...........",
        "..........BBBBBBBBBBB...........",
        "..........BBBBBBBBBBB...........",
        "..........bbbbbbbbbbb...........",
    ]
    return art(rows, {'S': SKIN, 'L': SKIN_L, 'D': SKIN_D, 'B': hexc('#3c8de0'), 'b': hexc('#2a6ac0')})


def build(out, flat_item):
    flat_item(out, 'gadget/rps_rock', fist())
    flat_item(out, 'gadget/rps_paper', paper())
    flat_item(out, 'gadget/rps_shears', peace())


def shears_icon():
    """A pair of shears, only a little open: two long silver blades meeting at a pivot, red finger rings."""
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    blade, blade_l, blade_d, handle = hexc('#d8dce8'), hexc('#ffffff'), hexc('#9aa0b4'), hexc('#e03a3a')
    d.polygon([(14, 15), (30, 10), (30, 12), (15, 17)], fill=blade + (255,))      # upper blade
    d.line([(15, 15), (29, 10)], fill=blade_l + (255,))
    d.polygon([(14, 17), (30, 22), (30, 20), (15, 15)], fill=blade + (255,))      # lower blade
    d.line([(15, 17), (29, 21)], fill=blade_d + (255,))
    d.ellipse((2, 6, 11, 14), fill=handle + (255,)); d.ellipse((5, 9, 8, 11), fill=(0, 0, 0, 0))
    d.ellipse((2, 18, 11, 26), fill=handle + (255,)); d.ellipse((5, 21, 8, 23), fill=(0, 0, 0, 0))
    d.line([(10, 12), (15, 16)], fill=handle + (255,), width=3); d.line([(10, 20), (15, 16)], fill=handle + (255,), width=3)
    im.putpixel((15, 16), hexc('#5a5e70') + (255,))
    return finish(im)


def shears_held():
    """The shears as held: drawn turned so the blades point up and out (like a tool), the handles toward the hand."""
    import math as _m
    a = _m.radians(-45)

    def r(x, y):
        dx, dy = x - 16, y - 16
        return (16 + dx * _m.cos(a) - dy * _m.sin(a), 16 + dx * _m.sin(a) + dy * _m.cos(a))

    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    blade, blade_l, blade_d, handle = hexc('#d8dce8'), hexc('#ffffff'), hexc('#9aa0b4'), hexc('#e03a3a')
    d.polygon([r(14, 15), r(30, 10), r(30, 12), r(15, 17)], fill=blade + (255,))
    d.polygon([r(14, 17), r(30, 22), r(30, 20), r(15, 15)], fill=blade + (255,))
    d.line([r(15, 15), r(29, 10)], fill=blade_l + (255,)); d.line([r(15, 17), r(29, 21)], fill=blade_d + (255,))
    for (cx, cy) in [(6.5, 10), (6.5, 22)]:
        x, y = r(cx, cy)
        d.ellipse((x - 4.5, y - 4, x + 4.5, y + 4), fill=handle + (255,)); d.ellipse((x - 1.5, y - 1, x + 1.5, y + 1), fill=(0, 0, 0, 0))
    d.line([r(10, 12), r(15, 16)], fill=handle + (255,), width=3); d.line([r(10, 20), r(15, 16)], fill=handle + (255,), width=3)
    return finish(im)


def build_held(out, flat_item):
    import json
    flat_item(out, 'gadget/rock_paper_shears_held', shears_held())
    # menus keep the flat picture; in the hand it's the turned one, held like a tool
    model = json.load(open(f'{out}/assets/fractal/models/item/gadget/rock_paper_shears_held.json'))
    model["parent"] = "minecraft:item/handheld"
    json.dump(model, open(f'{out}/assets/fractal/models/item/gadget/rock_paper_shears_held.json', 'w'))
    json.dump({"model": {"type": "minecraft:select", "property": "minecraft:display_context",
                         "cases": [{"when": ["gui", "ground", "fixed"], "model": {"type": "minecraft:model", "model": "fractal:item/gadget/rock_paper_shears"}}],
                         "fallback": {"type": "minecraft:model", "model": "fractal:item/gadget/rock_paper_shears_held"}}},
              open(f'{out}/assets/fractal/items/gadget/rock_paper_shears.json', 'w'))
