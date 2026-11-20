"""Boomerang icon options (centred in the slot), and the boomerang sprite that flies."""
import math
from PIL import Image
from kit import Canvas, light, dark


def centre(im):
    b = im.getbbox()
    out = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    out.alpha_composite(im, (round(16 - (b[0] + b[2]) / 2), round(16 - (b[1] + b[3]) / 2)))
    return out


def arm(c, a, b, w, col):
    c.line([a, b], col, w)
    c.shape('ellipse', (b[0] - w / 2, b[1] - w / 2, b[0] + w / 2, b[1] + w / 2), col)


def shape(c, cx, cy, s, col, stripe=None, edge=None):
    """A boomerang: two arms meeting at a rounded elbow at (cx, cy), opening down-right."""
    elbow = (cx, cy)
    a, b = (cx + 13 * s, cy + 3 * s), (cx + 3 * s, cy + 13 * s)
    for tip in (a, b):
        arm(c, elbow, tip, 5.2 * s, edge or dark(col, 0.35))
    for tip in (a, b):
        arm(c, elbow, tip, 3.8 * s, col)
    c.shape('ellipse', (cx - 2.6 * s, cy - 2.6 * s, cx + 2.6 * s, cy + 2.6 * s), col)
    if stripe:
        for tip in (a, b):
            mid = ((elbow[0] + tip[0]) / 2, (elbow[1] + tip[1]) / 2)
            c.line([elbow, mid], stripe, 1.1 * s)


def opt1():
    """A classic wooden boomerang with two painted bands on each arm."""
    c = Canvas()
    shape(c, 8, 8, 1.15, '#c98a4a', edge='#6b3e1e')
    for t in (0.45, 0.7):
        for tip in ((8 + 13 * 1.15, 8 + 3 * 1.15), (8 + 3 * 1.15, 8 + 13 * 1.15)):
            x, y = 8 + (tip[0] - 8) * t, 8 + (tip[1] - 8) * t
            c.shape('ellipse', (x - 1.6, y - 1.6, x + 1.6, y + 1.6), '#e03a3a' if t < 0.6 else '#ffd23a')
    c.shape('ellipse', (6, 6, 10, 10), '#e2ad6a')
    return centre(c.done())


def opt2():
    """A bright toy boomerang, red with a yellow stripe, whooshing round with motion arcs."""
    c = Canvas()
    shape(c, 11, 10, 1.0, '#e8423a', stripe='#ffd23a')
    c.shape('arc', ((1, 1, 30, 30), 190, 260), '#ffffff', width=1.0, opacity=0.8)
    c.shape('arc', ((4, 4, 27, 27), 200, 250), '#ffffff', width=0.8, opacity=0.5)
    return centre(c.done())


def opt3():
    """A purple and gold Fractal boomerang mid-spin, with a curved trail behind it."""
    c = Canvas()
    shape(c, 11, 10, 1.0, '#8a3fe0', stripe='#ffd27f', edge='#3a1a6a')
    for k, op in enumerate((0.85, 0.5, 0.25)):                          # a spinning trail curling round behind it
        c.shape('arc', ((1 + k * 2.5, 1 + k * 2.5, 31 - k * 2.5, 31 - k * 2.5), 150 + k * 8, 250 - k * 8), '#d99bff', width=1.2, opacity=op)
    return centre(c.done())


OPTIONS = [opt1, opt2, opt3]


def crisp():
    """Sasha's pick (#1), sharper: hard pixel edges (no blur), a 1-pixel dark outline, arms that taper to rounded
    points, a lighter top edge, and crisp red and yellow painted bands on each arm."""
    from PIL import ImageDraw
    big = 8                                                          # drawn 8x, then cut to pixels with no smoothing
    W = 32 * big
    mask = Image.new('L', (W, W), 0)
    d = ImageDraw.Draw(mask)
    elbow, tips = (8, 8), [(25, 12.5), (12.5, 25)]
    def poly_arm(tip, w0, w1):
        dx, dy = tip[0] - elbow[0], tip[1] - elbow[1]
        L = math.hypot(dx, dy); nx, ny = -dy / L, dx / L
        pts = [(elbow[0] + nx * w0, elbow[1] + ny * w0), (tip[0] + nx * w1, tip[1] + ny * w1),
               (tip[0] - nx * w1, tip[1] - ny * w1), (elbow[0] - nx * w0, elbow[1] - ny * w0)]
        d.polygon([(x * big, y * big) for x, y in pts], fill=255)
        d.ellipse(((tip[0] - w1) * big, (tip[1] - w1) * big, (tip[0] + w1) * big, (tip[1] + w1) * big), fill=255)
    for tip in tips:
        poly_arm(tip, 3.9, 2.3)
    d.ellipse(((elbow[0] - 4.2) * big, (elbow[1] - 4.2) * big, (elbow[0] + 4.2) * big, (elbow[1] + 4.2) * big), fill=255)
    small = mask.resize((32, 32), Image.BOX).point(lambda v: 255 if v >= 128 else 0)
    px = small.load()
    out = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    o = out.load()
    WOOD, LIGHT, DARK, EDGE = (201, 138, 74), (226, 173, 106), (156, 100, 52), (74, 42, 20)
    inside = lambda x, y: 0 <= x < 32 and 0 <= y < 32 and px[x, y]
    for y in range(32):
        for x in range(32):
            if not px[x, y]:
                continue
            if not all(inside(x + a, y + b) for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                o[x, y] = EDGE + (255,)                              # the outline
            elif not inside(x, y - 2) or not inside(x - 2, y):
                o[x, y] = LIGHT + (255,)                             # lit along the top and left
            elif not inside(x, y + 2) or not inside(x + 2, y):
                o[x, y] = DARK + (255,)                              # shaded along the bottom and right
            else:
                o[x, y] = WOOD + (255,)
    for tip in tips:                                                 # the painted bands, square and crisp
        for t, col in ((0.42, (224, 58, 58)), (0.66, (255, 210, 58))):
            cx, cy = elbow[0] + (tip[0] - elbow[0]) * t, elbow[1] + (tip[1] - elbow[1]) * t
            for y in range(int(cy - 1.5), int(cy + 1.5) + 1):
                for x in range(int(cx - 1.5), int(cx + 1.5) + 1):
                    if inside(x, y) and o[x, y][:3] != EDGE and (x - cx) ** 2 + (y - cy) ** 2 <= 3.3:
                        o[x, y] = col + (255,)
    return centre(out)
