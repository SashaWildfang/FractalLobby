"""Creeper Astronaut and Jetpack icon options (centred in the slot)."""
import math
from PIL import Image
from kit import Canvas, light, dark

INK = '#1a1b26'
CG, CG_D, CG_L = '#5cc04a', '#3a8a30', '#8ee070'


def centre(im):
    b = im.getbbox()
    out = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    out.alpha_composite(im, (round(16 - (b[0] + b[2]) / 2), round(16 - (b[1] + b[3]) / 2)))
    return out


def creeper_face(c, x, y, s):
    """A creeper's face: a green square with the classic dark face, top-left at (x, y), s pixels a face-pixel."""
    c.shape('rect', (x, y, x + 8 * s, y + 8 * s), CG)
    for (px, py, col) in ((1, 1, CG_L), (5, 2, CG_D), (2, 6, CG_L), (6, 6, CG_D), (0, 4, CG_D), (7, 0, CG_L)):
        c.shape('rect', (x + px * s, y + py * s, x + (px + 1) * s, y + (py + 1) * s), col)
    for (x0, y0, x1, y1) in ((1, 2, 3, 4), (5, 2, 7, 4), (3, 4, 5, 6), (2, 5, 3, 8), (5, 5, 6, 8)):
        c.shape('rect', (x + x0 * s, y + y0 * s, x + x1 * s, y + y1 * s), INK)


def bubble(c, cx, cy, r):
    """A round glass space helmet: a pale see-through bubble, a bright rim, a shine and a collar."""
    c.shape('ellipse', (cx - r, cy - r, cx + r, cy + r), '#bfe6ff', opacity=0.35)
    c.shape('arc', ((cx - r, cy - r, cx + r, cy + r), 0, 360), '#e8f6ff', width=1.2)
    c.shape('arc', ((cx - r + 2.2, cy - r + 2.2, cx + r - 2.2, cy + r - 2.2), 200, 250), '#ffffff', width=1.4)


def astro1():
    """Head and shoulders: the creeper's face inside a round glass bubble helmet with a white collar."""
    c = Canvas()
    c.shape('rrect', (6, 24, 26, 31, 2), '#e8ecf6')                        # the suit's shoulders
    c.shape('rect', (14, 25, 18, 28), '#ff8a1f')                           # a mission patch
    creeper_face(c, 9, 8, 1.75)
    bubble(c, 16, 15, 11.5)
    c.shape('rrect', (8, 24, 24, 26.5, 1), '#9aa2b8')                     # the helmet's collar ring
    return centre(c.done())


def astro2():
    """Blast off: a little creeper in a bubble helmet riding up on a column of flame, with a couple of stars."""
    c = Canvas()
    c.shape('poly', [(12, 22), (20, 22), (18, 30), (16, 32), (14, 30)], '#ffb03a')   # the flame
    c.shape('poly', [(13.5, 22), (18.5, 22), (17, 28), (16, 29.5), (15, 28)], '#fff2a0')
    c.shape('rect', (11, 15, 21, 23), CG)                                  # body
    c.shape('rect', (11, 15, 13, 23), CG_D)
    creeper_face(c, 11, 4, 1.25)
    bubble(c, 16, 9, 7.6)
    c.star(4, 6, 2.2, '#fff2a0', inner=0.3)
    c.star(28, 15, 1.8, '#ffffff', inner=0.3)
    return centre(c.done())


def astro3():
    """A space badge: a round night-sky disc with stars, the creeper's helmeted head in the middle."""
    c = Canvas()
    c.shape('ellipse', (1, 1, 31, 31), '#1d2350')
    c.shape('arc', ((1, 1, 31, 31), 0, 360), '#5a6bd8', width=1.4)
    for (x, y) in ((7, 8), (24, 7), (6, 22), (26, 23), (16, 4)):
        c.shape('rect', (x, y, x + 1, y + 1), '#ffffff')
    creeper_face(c, 10, 10, 1.5)
    bubble(c, 16, 16, 9.5)
    return centre(c.done())


ASTRO = [astro1, astro2, astro3]


def tanks(c, x, y, col, w=5, h=14):
    """Two side-by-side rocket tanks with rounded tops and nozzles, top-left at (x, y)."""
    for k in range(2):
        tx = x + k * (w + 1)
        c.shape('rrect', (tx, y, tx + w, y + h, w / 2), col)
        c.shape('rect', (tx + 1, y + 2, tx + 2, y + h - 2), light(col, 0.4))
        c.shape('rect', (tx + w - 1.2, y + 2, tx + w, y + h - 1), dark(col, 0.3))
        c.shape('poly', [(tx + 0.8, y + h), (tx + w - 0.8, y + h), (tx + w - 0.2, y + h + 2), (tx + 0.2, y + h + 2)], '#5a6078')


def flames(c, x, y, w, k=2, colours=('#ff6a1f', '#ffd23a', '#fff6c0'), length=7):
    """A flame out of each nozzle, starting at (x, y)."""
    for i in range(k):
        fx = x + i * (w + 1) + w / 2
        c.shape('poly', [(fx - 2.2, y), (fx + 2.2, y), (fx, y + length)], colours[0])
        c.shape('poly', [(fx - 1.4, y), (fx + 1.4, y), (fx, y + length * 0.7)], colours[1])
        c.shape('poly', [(fx - 0.6, y), (fx + 0.6, y), (fx, y + length * 0.4)], colours[2])


def jet1():
    """Silver twin tanks with straps and bright orange flames."""
    c = Canvas()
    tanks(c, 9.5, 3, '#c9ccd8', 6, 15)
    c.shape('rect', (8.5, 7, 23.5, 9), '#3a3d56')                          # the straps
    c.shape('rect', (8.5, 13, 23.5, 15), '#3a3d56')
    flames(c, 9.5, 20, 6)
    return centre(c.done())


def jet2():
    """A retro red rocket pack: two red tanks with white fins and blue jets."""
    c = Canvas()
    tanks(c, 10, 4, '#e0443a', 5.5, 14)
    for x, flip in ((10, -1), (21.5, 1)):
        c.shape('poly', [(x, 13), (x + flip * 3.5, 17.5), (x + flip * 3.5, 20), (x, 18)], '#f4f4fa')
    c.shape('rect', (10, 9, 21.5, 10.5), '#ffd23a')
    flames(c, 10, 20, 5.5, colours=('#3fb8ff', '#9fe8ff', '#ffffff'))
    return centre(c.done())


def jet3():
    """A purple Fractal jetpack: a sleek single body with gold trim, twin nozzles and orange flames, and a glow."""
    c = Canvas()
    c.shape('rrect', (9, 3, 23, 19, 4), '#6b3fb0')
    c.shape('rect', (11, 5, 13, 17), light('#6b3fb0', 0.35))
    c.shape('rrect', (12, 7, 20, 13, 2), '#ffd27f')                       # a gold panel
    c.shape('ellipse', (14.5, 8.5, 17.5, 11.5), '#e8b8ff')
    for x in (10.5, 17.5):
        c.shape('poly', [(x, 19), (x + 4, 19), (x + 4.6, 21.5), (x - 0.6, 21.5)], '#5a6078')
    flames(c, 10.5, 21.5, 4, length=7.5)
    return centre(c.done())


JET = [jet1, jet2, jet3]


def write_flame(out):
    """The jetpack's thruster flame: two crossed planes (so it looks solid from any side) with an animated texture,
    4 frames of a flame pointing down (wide and white-hot at the nozzle, orange and ragged at the tip), flickering."""
    import json, os, random
    rnd = random.Random(7)
    W, H, F = 16, 32, 4
    sheet = Image.new('RGBA', (W, H * F), (0, 0, 0, 0))
    for f in range(F):
        length = 26 + rnd.randint(-3, 3)
        sway = rnd.uniform(-1.2, 1.2)
        for y in range(H):
            if y > length:
                break
            k = y / length                                            # 0 at the nozzle, 1 at the tip
            half = 6.2 * (1 - k) ** 0.8 + 0.6
            cx = 7.5 + sway * k
            for x in range(W):
                d = abs(x - cx) / half
                if d > 1:
                    continue
                heat = (1 - d) * (1 - k * 0.85)
                if heat > 0.62: col = (255, 250, 220)
                elif heat > 0.42: col = (255, 214, 70)
                elif heat > 0.22: col = (255, 140, 40)
                else: col = (230, 70, 30)
                alpha = 255 if d < 0.8 else 170
                if k > 0.8 and rnd.random() < 0.35:
                    continue                                          # a ragged tip
                sheet.putpixel((x, f * H + y), col + (alpha,))
    tex = f'{out}/assets/fractal/textures/item/gadget'
    os.makedirs(tex, exist_ok=True)
    sheet.save(f'{tex}/jet_flame.png')
    json.dump({"animation": {"frametime": 2, "interpolate": False, "width": 16, "height": 32}}, open(f"{tex}/jet_flame.png.mcmeta", "w"))
    faces_x = {f: {"uv": [0, 0, 16, 16], "texture": "#t"} for f in ("north", "south")}
    faces_z = {f: {"uv": [0, 0, 16, 16], "texture": "#t"} for f in ("east", "west")}
    model = {"textures": {"t": "fractal:item/gadget/jet_flame", "particle": "fractal:item/gadget/jet_flame"},
             "elements": [{"from": [4, 0, 8], "to": [12, 16, 8], "shade": False, "light_emission": 15, "faces": faces_x},
                          {"from": [8, 0, 4], "to": [8, 16, 12], "shade": False, "light_emission": 15, "faces": faces_z}]}
    os.makedirs(f'{out}/assets/fractal/models/item/gadget', exist_ok=True)
    json.dump(model, open(f'{out}/assets/fractal/models/item/gadget/jet_flame.json', 'w'))
    os.makedirs(f'{out}/assets/fractal/items/gadget', exist_ok=True)
    json.dump({"model": {"type": "minecraft:model", "model": "fractal:item/gadget/jet_flame"}},
              open(f'{out}/assets/fractal/items/gadget/jet_flame.json', 'w'))


# ---- sharper Creeper Astronaut options: hard pixel edges, the real creeper face on whole pixels
FACE = ["........",
        "........",
        ".EE..EE.",
        ".EE..EE.",
        "...MM...",
        "..MMMM..",
        "..MMMM..",
        "..M..M.."]


def pixel_face(img, x0, y0, p, greens=('#5cc04a', '#4fae40', '#6fd25a', '#3f9a34')):
    """The creeper's face as true pixels: an 8x8 grid, p screen pixels per face pixel, mottled greens, dark face."""
    import random
    rnd = random.Random(3)
    px = img.load()
    for fy, row in enumerate(FACE):
        for fx, ch in enumerate(row):
            col = '#1a1b26' if ch != '.' else greens[rnd.randrange(len(greens))]
            rgb = tuple(int(col[i:i + 2], 16) for i in (1, 3, 5)) + (255,)
            for yy in range(p):
                for xx in range(p):
                    px[x0 + fx * p + xx, y0 + fy * p + yy] = rgb


def crisp_ring(img, cx, cy, r, col, width=1.0, fill=None, fill_alpha=0):
    """A hard-edged circle outline (and optional see-through fill) on whole pixels."""
    px = img.load()
    rgb = tuple(int(col[i:i + 2], 16) for i in (1, 3, 5))
    for y in range(32):
        for x in range(32):
            d = ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5
            if r - width <= d <= r:
                px[x, y] = rgb + (255,)
            elif fill and d < r - width and px[x, y][3] == 0:
                f = tuple(int(fill[i:i + 2], 16) for i in (1, 3, 5))
                px[x, y] = f + (fill_alpha,)


def sharp_astro1():
    """Your current one, sharpened: the creeper's face (on whole pixels, the right face) in a crisp glass bubble with
    a shine, on white suit shoulders with an orange patch."""
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = __import__('PIL.ImageDraw', fromlist=['ImageDraw']).Draw(img)
    d.rectangle((6, 25, 25, 31), fill='#e8ecf6'); d.rectangle((6, 25, 7, 31), fill='#c9cfe0'); d.rectangle((24, 25, 25, 31), fill='#c9cfe0')
    d.rectangle((14, 27, 17, 29), fill='#ff8a1f')
    pixel_face(img, 8, 6, 2)
    crisp_ring(img, 16, 14.5, 12, '#e8f6ff', 1.2, fill='#bfe6ff', fill_alpha=90)
    d.rectangle((8, 24, 23, 25), fill='#9aa2b8')                           # the collar ring
    for (x, y) in ((9, 7), (8, 8), (8, 9), (10, 6)):                       # the shine, top left
        img.putpixel((x, y), (255, 255, 255, 255))
    return centre(img)


def sharp_astro2():
    """Space-suited: the face in a gold-rimmed helmet with a little antenna and a blinking red tip."""
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = __import__('PIL.ImageDraw', fromlist=['ImageDraw']).Draw(img)
    d.rectangle((15, 0, 16, 4), fill='#9aa2b8'); d.rectangle((14, 0, 17, 1), fill='#ff4a4a')
    d.rectangle((5, 26, 26, 31), fill='#e8ecf6'); d.rectangle((5, 26, 6, 31), fill='#c9cfe0'); d.rectangle((25, 26, 26, 31), fill='#c9cfe0')
    pixel_face(img, 8, 8, 2)
    crisp_ring(img, 16, 16, 12, '#ffd27f', 1.6, fill='#bfe6ff', fill_alpha=80)
    d.rectangle((9, 25, 22, 26), fill='#c99a3a')
    for (x, y) in ((9, 9), (8, 10), (8, 11)):
        img.putpixel((x, y), (255, 255, 255, 255))
    return centre(img)


def sharp_astro3():
    """The whole little creeper in its suit: bubble-helmeted head, white suit body with an orange patch, and its four
    stubby legs with dark boots."""
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = __import__('PIL.ImageDraw', fromlist=['ImageDraw']).Draw(img)
    d.rectangle((11, 16, 20, 25), fill='#e8ecf6'); d.rectangle((11, 16, 12, 25), fill='#c9cfe0'); d.rectangle((19, 16, 20, 25), fill='#d8dceb')
    d.rectangle((14, 18, 17, 20), fill='#ff8a1f')
    for x0 in (9, 13, 17, 21):                                             # legs and boots
        d.rectangle((x0 - 1, 25, x0, 29), fill='#d8dceb'); d.rectangle((x0 - 1, 28, x0, 29), fill='#5a6078')
    pixel_face(img, 12, 4, 1)
    crisp_ring(img, 16, 8, 7, '#e8f6ff', 1.1, fill='#bfe6ff', fill_alpha=90)
    d.rectangle((11, 14, 20, 15), fill='#9aa2b8')                          # collar
    img.putpixel((12, 4), (255, 255, 255, 255)); img.putpixel((11, 5), (255, 255, 255, 255))
    return centre(img)


SHARP_ASTRO = [sharp_astro1, sharp_astro2, sharp_astro3]
