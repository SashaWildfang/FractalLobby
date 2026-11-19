"""The CATapult icon (Sasha picked this one): mid-fling, the arm thrown over and a cat sailing off, centred."""
import math
from PIL import Image, ImageDraw
OUT = (0x1a, 0x1b, 0x26)
def hexc(h): h = h.lstrip('#'); return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))
W, WL, WD = hexc('#b07a45'), hexc('#d8a46a'), hexc('#7a5230')
ORANGE, ORANGE_L, ORANGE_D, PINK, WHITE = hexc('#ff9a3c'), hexc('#ffc27a'), hexc('#d06a1a'), hexc('#ff9ab0'), hexc('#ffffff')
def finish(im, centre=True):
    px = im.load(); src = im.copy(); sp = src.load()
    for y in range(32):
        for x in range(32):
            if sp[x, y][3]: continue
            if any(0 <= x + a < 32 and 0 <= y + b < 32 and sp[x + a, y + b][3] for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = OUT + (255,)
    if not centre: return im
    b = im.getbbox(); out = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    out.alpha_composite(im, (round(16 - (b[0] + b[2]) / 2), round(16 - (b[1] + b[3]) / 2))); return out

def cat_head(d, im, cx, cy, s=1.0):
    """A round orange cat head with pointy ears, eyes and a pink nose."""
    r = 4.2 * s
    d.ellipse((cx - r, cy - r * 0.9, cx + r, cy + r * 0.9), fill=ORANGE + (255,))
    d.polygon([(cx - r, cy - r * 0.2), (cx - r * 0.9, cy - r * 1.5), (cx - r * 0.2, cy - r * 0.8)], fill=ORANGE + (255,))
    d.polygon([(cx + r, cy - r * 0.2), (cx + r * 0.9, cy - r * 1.5), (cx + r * 0.2, cy - r * 0.8)], fill=ORANGE + (255,))
    d.ellipse((cx - r * 0.75, cy - r * 0.6, cx - r * 0.1, cy - r * 0.05), fill=ORANGE_L + (255,))
    for ex in (-1, 1):
        im.putpixel((int(cx + ex * r * 0.42), int(cy - r * 0.05)), OUT + (255,))
    im.putpixel((int(cx), int(cy + r * 0.3)), PINK + (255,))

def catapult(d, base_y=27, arm=(8, 22, 23, 8)):
    """A small wooden catapult: a base on two wheels, an upright frame, and the throwing arm with its bucket."""
    d.rectangle((4, base_y - 3, 26, base_y - 1), fill=W + (255,)); d.line([(4, base_y - 3), (26, base_y - 3)], fill=WL + (255,))
    for x in (8, 22):
        d.ellipse((x - 3, base_y - 2, x + 3, base_y + 4), fill=WD + (255,)); d.point((x, base_y + 1), fill=OUT + (255,))
    d.polygon([(15, base_y - 3), (18, base_y - 13), (20, base_y - 13), (19, base_y - 3)], fill=WD + (255,))
    x0, y0, x1, y1 = arm
    d.line([(x0, y0), (x1, y1)], fill=W + (255,), width=3)
    return (x1, y1)

def catapult_icon():   # mid-fling: the arm thrown up and over, a cat sailing off with motion lines behind it
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    tip = catapult(d, arm=(10, 22, 11, 9))
    d.chord((tip[0] - 4, tip[1] - 2, tip[0] + 3, tip[1] + 4), 0, 180, fill=WD + (255,))
    cat_head(d, im, 25, 7, 0.9)
    for (x0, y0, x1, y1) in [(16, 5, 19, 6), (16, 9, 20, 9), (17, 12, 20, 11)]:
        d.line([(x0, y0), (x1, y1)], fill=WHITE + (255,))
    return finish(im)

