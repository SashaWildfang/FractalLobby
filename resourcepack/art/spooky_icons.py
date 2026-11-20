"""Pumpkin Rain icon options (crisp pixel art, centred), the ghost sheet model, and the spooky sounds."""
import math
from PIL import Image, ImageDraw

OR, OR_D, OR_L, STEM, GLOW, INK = '#f08a1c', '#c0600e', '#ffb04a', '#4f7a2a', '#ffe14d', '#2a1a10'


def centre(im):
    b = im.getbbox()
    out = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    out.alpha_composite(im, (round(16 - (b[0] + b[2]) / 2), round(16 - (b[1] + b[3]) / 2)))
    return out


JACK = [".....S.....",
        "..OOOOOOO..",
        ".OLOODOOOO.",
        "OLOOODOOOOO",
        "OOYYODOYYOO",
        "OOYYODOYYOO",
        "OOOOODOOOOO",
        "OOYYYYYYYOO",
        ".OOYOYOYOO.",
        "..OOOOOOO.."]


def stamp(img, x, y, scale=1, lit=True):
    """A pixel jack-o'-lantern (11 x 10, each pixel `scale` big): stem, ribbed body, a highlight, triangle-ish eyes
    and a toothy grin, glowing yellow (or dark, unlit), top-left at (x, y)."""
    cols = {'S': STEM, 'O': OR, 'L': OR_L, 'D': OR_D, 'Y': GLOW if lit else INK}
    px = img.load()
    for ry, row in enumerate(JACK):
        for rx, ch in enumerate(row):
            if ch == '.':
                continue
            c = cols[ch]
            rgb = tuple(int(c[i:i + 2], 16) for i in (1, 3, 5)) + (255,)
            for dy in range(scale):
                for dx in range(scale):
                    X, Y = x + rx * scale + dx, y + ry * scale + dy
                    if 0 <= X < img.width and 0 <= Y < img.height:
                        px[X, Y] = rgb


def jack(d, x, y, w, h, lit=True):
    stamp(d._image, x, y, 2 if w >= 16 else 1, lit)


def pr1():
    """Sasha's pick: a dark storm cloud raining three jack-o'-lanterns (short stems, dark carved faces, no streaks)."""
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for (cx, cy, r) in ((9, 7, 5), (16, 5, 6), (23, 7, 5)):
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill='#4a3f66')
    d.rectangle((5, 7, 27, 11), fill='#4a3f66')
    d.ellipse((4, 6, 28, 13), fill='#4a3f66')                         # a soft, rounded bottom (no dark bar)
    jack(d, 1, 18, 9, 8, lit=False)
    jack(d, 11, 21, 9, 8, lit=False)
    jack(d, 20, 16, 9, 8, lit=False)
    return centre(img)


def pr2():
    """One big glowing jack-o'-lantern falling, with smaller ones tumbling round it."""
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    jack(d, 0, 0, 7, 6, lit=False)
    jack(d, 21, 1, 7, 6, lit=False)
    jack(d, 5, 11, 18, 17)
    for (x, y) in ((3, 12), (27, 14), (5, 27)):
        d.point((x, y), fill='#ffe14d')
    return centre(img)


def pr3():
    """Pumpkins in a shower under a little umbrella-shaped arc of rain, pumpkins bouncing on the ground with splats."""
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle((1, 28, 30, 30), fill='#3a5a2a')                       # the ground
    jack(d, 4, 19, 10, 9)
    jack(d, 17, 20, 10, 8)
    jack(d, 11, 4, 9, 8, lit=False)
    for (x, y) in ((3, 6), (25, 8), (8, 14), (22, 3), (28, 15)):
        d.line((x, y, x + 1, y + 2), fill='#ffb04a')
    for (x, y) in ((2, 27), (14, 27), (16, 27), (28, 27)):
        d.point((x, y), fill=OR_L)                                     # little splats
    return centre(img)


PUMPKIN = [pr1, pr2, pr3]
