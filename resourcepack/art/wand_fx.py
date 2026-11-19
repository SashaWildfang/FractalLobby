"""
The magic wand's own particles (1.41): little pixel-art pictures the plugin shows facing the player, in a starry
gold, lavender and white palette.

  gadget/fx_orb       the spell itself: a glowing star in a soft halo
  gadget/fx_star      a five-point star (bursts out where the spell lands)
  gadget/fx_twinkle   a four-point twinkle (the trail)
  gadget/fx_sparkle   a tiny sparkle (the trail)
  gadget/fx_circle    a star circle laid on the ground where the spell lands: rings, a star outline and little stars
"""
import math
from PIL import Image

GOLD, PALE, WHITE = (255, 222, 110), (255, 246, 210), (255, 255, 255)
LAV, DEEP = (199, 166, 255), (122, 76, 255)


def _star_points(cx, cy, r_out, r_in, points=5, turn=-90):
    pts = []
    for i in range(points * 2):
        a = math.radians(turn + i * 180 / points)
        r = r_out if i % 2 == 0 else r_in
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    return pts


def _inside(pts, x, y):
    c = False
    for i in range(len(pts)):
        (x1, y1), (x2, y2) = pts[i], pts[(i + 1) % len(pts)]
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
            c = not c
    return c


def star(size=16):
    """A plump five-point star: white middle, gold body, a lavender edge."""
    im = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    c = (size - 1) / 2
    outer = _star_points(c, c + 0.6, size * 0.48, size * 0.22)
    body = _star_points(c, c + 0.6, size * 0.40, size * 0.17)
    core = _star_points(c, c + 0.6, size * 0.22, size * 0.10)
    for y in range(size):
        for x in range(size):
            p = (x + 0.5, y + 0.5)
            if _inside(core, *p): im.putpixel((x, y), WHITE + (255,))
            elif _inside(body, *p): im.putpixel((x, y), GOLD + (255,))
            elif _inside(outer, *p): im.putpixel((x, y), LAV + (255,))
    return im


def twinkle(size=16):
    """A four-point twinkle: long thin points up/down/left/right, short diagonals, a bright middle."""
    im = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    c = size // 2
    for d in range(1, c):
        a = 255 if d < c * 0.5 else 200
        col = WHITE if d < 3 else PALE if d < c * 0.6 else LAV
        for (x, y) in [(c + d, c), (c - d, c), (c, c + d), (c, c - d)]:
            if 0 <= x < size and 0 <= y < size: im.putpixel((x, y), col + (a,))
        if d < 3:
            for (x, y) in [(c + d, c + d), (c - d, c - d), (c + d, c - d), (c - d, c + d)]:
                im.putpixel((x, y), PALE + (200,))
    for (x, y) in [(c, c), (c - 1, c), (c + 1, c), (c, c - 1), (c, c + 1)]:
        im.putpixel((x, y), WHITE + (255,))
    return im


def sparkle():
    """A tiny plus-shaped sparkle."""
    im = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    for (x, y, col) in [(4, 4, WHITE), (3, 4, GOLD), (5, 4, GOLD), (4, 3, GOLD), (4, 5, GOLD), (2, 4, LAV), (6, 4, LAV), (4, 2, LAV), (4, 6, LAV)]:
        im.putpixel((x, y), col + (255,))
    return im


def orb():
    """The spell: a star glowing in a soft round halo."""
    size = 16
    im = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    c = 7.5
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - c, y - c)
            if d <= 7.4:
                k = d / 7.4
                col = tuple(int(LAV[i] * (1 - k) + DEEP[i] * k) for i in range(3))
                im.putpixel((x, y), col + (int(200 * (1 - k * k) + 30),))
    s = star(12)
    im.alpha_composite(s, (2, 2))
    return im


def circle():
    """A star circle for the ground: a double ring of pale gold, a star outline inside it, small stars round it."""
    size = 32
    im = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    c = 15.5
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - c, y - c)
            if 14.2 <= d <= 15.4: im.putpixel((x, y), GOLD + (255,))
            elif 11.6 <= d <= 12.4: im.putpixel((x, y), LAV + (230,))
    # a star inside: its outline (not crossed lines), with a soft glowing middle
    outer = _star_points(c, c + 0.8, 10.6, 4.6)
    inner = _star_points(c, c + 0.8, 9.2, 3.6)
    for y in range(size):
        for x in range(size):
            p = (x + 0.5, y + 0.5)
            if _inside(outer, *p) and not _inside(inner, *p): im.putpixel((x, y), PALE + (255,))
            elif _inside(inner, *p): im.putpixel((x, y), LAV + (70,))
    # little stars on the ring between the points
    for i in range(5):
        a = math.radians(-90 + 36 + i * 72)
        x, y = int(c + math.cos(a) * 13.3), int(c + math.sin(a) * 13.3)
        for (dx, dy) in [(0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)]:
            im.putpixel((x + dx, y + dy), WHITE + (255,))
    return im


def build(out, flat_item):
    flat_item(out, 'gadget/fx_orb', orb())
    flat_item(out, 'gadget/fx_star', star())
    flat_item(out, 'gadget/fx_twinkle', twinkle())
    flat_item(out, 'gadget/fx_sparkle', sparkle())
    flat_item(out, 'gadget/fx_circle', circle())
