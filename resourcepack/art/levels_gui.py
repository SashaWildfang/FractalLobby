"""The Levels menu background (U+F1C6): the LEVELS plaque, wells for the top-row buttons and the page arrows, and a
snake track through the 29 trail slots (one continuous channel; the ten level slots set in purple-rimmed sockets).
Run from resourcepack/: python3 art/levels_gui.py <CrazySurvival menus folder>"""
import json, sys
src = open('make_gui.py').read()
src = src[:src.index("os.makedirs(f'{OUT}/assets/fractal/textures/gui'")]
sys.argv = ['make_gui.py', sys.argv[1], '/tmp/unused']
g = {}
exec(compile(src, 'make_gui.py', 'exec'), g)

# across row 1, down at the right (row 2), back along row 3, down at the left (row 4), across row 5
PATH = list(range(9, 18)) + [26] + list(range(35, 26, -1)) + [36] + list(range(45, 54))
NODES = PATH[0:28:3]
img = g['background'](6, [], "LEVELS", cells=[0, 2, 4, 6, 8, 18, 44])

def box(slot):
    r, c = divmod(slot, 9)
    x, y = 8 + c * 18, 16 + 18 + r * 18                    # item corner, as make_gui's cell()
    return x - 1, y - 1, x + 16, y + 16

INNER, DARK, LIGHT = (0x2c, 0x2e, 0x42), (0x1d, 0x1f, 0x2d), (0x6a, 0x6f, 0x8d)
linked = {s: set() for s in PATH}
for a, b in zip(PATH, PATH[1:]):
    linked[a].add(b)
    linked[b].add(a)
# the channel: each slot's box, opened towards the slots it joins
for s in PATH:
    x0, y0, x1, y1 = box(s)
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            img.putpixel((x, y), INNER + (255,))
    for n in linked[s]:
        if n == s + 1:   # joins to the right: fill the 1px seam
            pass
    left = s - 1 in linked[s]
    right = s + 1 in linked[s]
    up = s - 9 in linked[s]
    down = s + 9 in linked[s]
    for x in range(x0, x1 + 1):
        if not up: img.putpixel((x, y0), DARK + (255,))
        if not down: img.putpixel((x, y1), LIGHT + (255,))
    for y in range(y0, y1 + 1):
        if not left: img.putpixel((x0, y), DARK + (255,))
        if not right: img.putpixel((x1, y), LIGHT + (255,))
# a faint line down the middle of the channel, so it reads as a track
MID = (0x3a, 0x30, 0x5a)
for a, b in zip(PATH, PATH[1:]):
    ax0, ay0, ax1, ay1 = box(a)
    bx0, by0, bx1, by1 = box(b)
    acx, acy = (ax0 + ax1) // 2, (ay0 + ay1) // 2
    bcx, bcy = (bx0 + bx1) // 2, (by0 + by1) // 2
    for x in range(min(acx, bcx), max(acx, bcx) + 1):
        for y in range(min(acy, bcy), max(acy, bcy) + 1):
            for d in (0, 1):
                if acy == bcy: img.putpixel((x, y + d), MID + (255,))
                else: img.putpixel((x + d, y), MID + (255,))
# sockets for the levels: a purple rim with rounded corners
RIM, RIM_D = (0x96, 0x62, 0xe6), (0x5a, 0x3a, 0x9a)
for s in NODES:
    x0, y0, x1, y1 = box(s)
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            edge = x in (x0, x1) or y in (y0, y1)
            corner = x in (x0, x1) and y in (y0, y1)
            if corner:
                continue
            if edge:
                img.putpixel((x, y), (RIM if (y == y0 or x == x0) else RIM_D) + (255,))
            else:
                img.putpixel((x, y), (0x34, 0x30, 0x4c, 255))
img.save('FractalLobby/assets/fractal/textures/gui/levels.png')
from PIL import Image
img.crop((0, 0, 176, 160)).resize((528, 480), Image.NEAREST).save('/tmp/levels_prev.png')
