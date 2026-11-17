"""Mystery Dust: a golden sand pile, peaked like real sand, with sparkles twinkling over it (8 frames, 32x32)."""
import math, random
from PIL import Image
import kit
from kit import Canvas

kit.S, kit.BANDS, kit.PIXEL = 4, 3, True


def pile():
    c = Canvas()
    # the pile: a soft peak, sides curving out to a wide base
    pts = [(16, 8)]
    for i in range(1, 13):
        t = i / 12
        pts.append((16 + 13.5 * t, 8 + 19 * (t ** 0.9)))
    pts += [(29.5, 28.5), (2.5, 28.5)]
    for i in range(12, 0, -1):
        t = i / 12
        pts.append((16 - 13.5 * t, 8 + 19 * (t ** 0.9)))
    c.shape('poly', pts, '#e9b04a')
    # light from the left: the left slope bright, the right in shade, a highlight near the top
    left = [(16, 8)] + [(16 - 13.5 * (i / 12), 8 + 19 * ((i / 12) ** 0.75)) for i in range(1, 13)] + [(2.5, 28.5), (13, 28.5), (15.5, 9)]
    c.shape('poly', left, '#ffd27f')
    right = [(16.5, 9)] + [(16 + 13.5 * (i / 12), 8 + 19 * ((i / 12) ** 0.75)) for i in range(1, 13)] + [(29.5, 28.5), (21, 28.5)]
    c.shape('poly', right, '#c98a2e')
    c.shape('poly', [(16, 8), (12, 15), (14.5, 15.5)], '#fff1c9')
    c.shape('ellipse', (2, 26.5, 30, 30.5), '#a8701e')
    c.shape('rect', (2, 26.5, 30, 28.5), '#c98a2e')
    c.shape('rect', (2, 26.5, 16, 28.5), '#e0a845')
    img = c.done().copy()
    # a few grains catching the light
    r = random.Random(3)
    px = img.load()
    for _ in range(26):
        x, y = r.randrange(5, 27), r.randrange(12, 27)
        if px[x, y][3] and px[x, y][:3] != (0, 0, 0):
            px[x, y] = (255, 241, 201, 255) if x < 16 else (240, 196, 110, 255)
    return img


SPARKS = [(7, 5, '#ffe680', 0), (24, 4, '#ff7be5', 3), (27, 13, '#ffffff', 5), (4, 14, '#c9b8ee', 2), (16, 2, '#ffffff', 6),
          (21, 9, '#ffd27f', 1)]


def frame(f):
    img = pile().copy()
    for (x, y, col, phase) in SPARKS:
        size = [0, 1, 2, 3, 2, 1, 0, 0][(f + phase) % 8]
        if not size:
            continue
        c = tuple(int(col[i:i + 2], 16) for i in (1, 3, 5))
        def put(px_, py_, cc):
            if 0 <= px_ < 32 and 0 <= py_ < 32:
                img.putpixel((px_, py_), cc + (255,))
        put(x, y, (255, 255, 255) if size > 1 else c)
        for k in range(1, size):
            for dx, dy in ((k, 0), (-k, 0), (0, k), (0, -k)):
                put(x + dx, y + dy, c)
    return img


def build(path):
    out = Image.new('RGBA', (32, 32 * 8))
    for f in range(8):
        out.paste(frame(f), (0, f * 32))
    out.save(path)


if __name__ == '__main__':
    build('/tmp/dust32.png')
    kit.sheet([frame(0), frame(3)], scale=8, cols=2).save('/tmp/dust_prev.png')
