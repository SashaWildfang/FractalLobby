"""
The fortune cookie gadget's pictures (1.41), drawn pixel by pixel after a real fortune cookie (a round body folded at
the top into two lobes, the fold creasing down the middle). The plugin shows them floating in front of the player,
always facing them, and cracks the cookie open: it shakes, splits into its two halves, and the fortune slip rises out.

  gadget/fc_whole   the cookie, closed
  gadget/fc_left    its left half
  gadget/fc_right   its right half
  gadget/fc_slip    the fortune slip (red lucky marks on white paper)
"""
import math
from PIL import Image, ImageDraw
OUT = (0x1a, 0x1b, 0x26)
def hexc(h): h = h.lstrip('#'); return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))
HI, PALE, MID, TOAST, CREASE = hexc('#fff0cc'), hexc('#f3d9a4'), hexc('#e8bf78'), hexc('#c98a40'), hexc('#9a5a24')
PAPER, INK = (255, 255, 255), hexc('#e03a3a')

def fortune(dx=0, dy=0, s=1.0, keep=None):
    """A fortune cookie like the real thing: a plump round body folded at the top into two rounded lobes (the left a
    little bigger), with the fold creasing down into the middle. Pale golden, toasted at the edges."""
    def inside(x, y):
        x, y = (x - dx - 16) / s + 16, (y - dy - 16) / s + 16
        body = ((x - 16) / 13) ** 2 + ((y - 19) / 10) ** 2 <= 1
        lobe_l = math.hypot(x - 11.5, y - 12.5) <= 8.5
        lobe_r = math.hypot(x - 21, y - 14) <= 7.5
        notch = math.hypot(x - 17.2, y - 4.8) <= 4.2           # the dip between the lobes at the top
        return (body or lobe_l or lobe_r) and not notch
    def crease(x, y):
        x, y = (x - dx - 16) / s + 16, (y - dy - 16) / s + 16
        # the fold: a curve from the notch down and to the left into the middle of the cookie
        t = (y - 8) / 12
        if not (0 <= t <= 1): return 0
        cx = 17.0 - 3.2 * t * t                                    # the fold curls down and to the left
        d = x - cx
        width = 1.6 * (1 - t) + 0.6
        if -width - 1.2 <= d < -width: return 3                     # the soft shadow inside the fold
        if -width <= d <= 0.4: return 2                             # the deep crease
        if 0.4 < d <= 2.0 and t < 0.85: return 1                    # the lit edge of the lobe folding over it
        return 0
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0)); px = im.load()
    mask = [[inside(x + 0.5, y + 0.5) for x in range(32)] for y in range(32)]
    for y in range(32):
        for x in range(32):
            if not mask[y][x]: continue
            if keep and not keep(x, y): continue
            edge = any(not (0 <= x + a < 32 and 0 <= y + b < 32 and mask[y + b][x + a]) for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1), (2, 0), (0, 2)))
            c = crease(x + 0.5, y + 0.5)
            light = -(x - 16) - (y - 16)
            if c == 3: col = MID
            elif c == 2: col = CREASE
            elif c == 1: col = HI
            elif edge: col = TOAST
            elif light > 12: col = HI
            elif light > -4: col = PALE
            else: col = MID
            px[x, y] = col + (255,)
    return im

def outline(im):
    src = im.copy(); sp = src.load(); px = im.load()
    for y in range(32):
        for x in range(32):
            if sp[x, y][3]: continue
            if any(0 <= x + a < 32 and 0 <= y + b < 32 and sp[x + a, y + b][3] for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = OUT + (255,)
    return im

def centred(im):
    b = im.getbbox(); dx, dy = round(16 - (b[0] + b[2]) / 2), round(16 - (b[1] + b[3]) / 2)
    out = Image.new('RGBA', (32, 32), (0, 0, 0, 0)); out.alpha_composite(im, (dx, dy)); return out



def whole():
    return outline(fortune())          # (not re-centred: the halves must sit exactly where the whole cookie was)


def half(side):
    """One half of the cookie, broken along the fold (left: side -1, right: side 1)."""
    if side < 0:
        return outline(fortune(keep=lambda x, y: x < 16 + (y - 16) * -0.2))
    return outline(fortune(keep=lambda x, y: x >= 16 + (y - 16) * -0.2))


def slip_sprite():
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0)); px = im.load()
    for y in range(13, 19):
        for x in range(4, 28):
            px[x, y] = (255, 255, 255, 255)
    for x in range(7, 26, 3):
        px[x, 15] = INK + (255,); px[x, 16] = INK + (255,)
    return outline(im)


def build(out, flat_item):
    flat_item(out, 'gadget/fc_whole', whole())
    flat_item(out, 'gadget/fc_left', half(-1))
    flat_item(out, 'gadget/fc_right', half(1))
    flat_item(out, 'gadget/fc_slip', slip_sprite())


def icon():
    """The menu icon (Sasha picked this one): the plain fortune cookie, centred."""
    return centred(outline(fortune()))
