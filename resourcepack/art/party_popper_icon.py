"""The party popper icons, drawn pixel by pixel (Sasha picked option 1): closed in the hotbar, open (streamers and
confetti flying out) for a moment after it pops. Also the confetti and streamer pieces it shoots out."""
import math
from PIL import Image

OUT = (0x1a, 0x1b, 0x26)
def hexc(h): h = h.lstrip('#'); return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))

class P:
    def __init__(s): s.im = Image.new('RGBA', (32, 32), (0, 0, 0, 0)); s.px = s.im.load()
    def put(s, x, y, c, a=255):
        x, y = int(x), int(y)
        if 0 <= x < 32 and 0 <= y < 32: s.px[x, y] = (c if isinstance(c, tuple) else hexc(c)) + (a,)
    def cone(s, tip, base_a, base_b, colour_at):
        """Fills the triangle tip-base_a-base_b; colour_at(t along tip->base 0..1, u across -1..1) gives each pixel's colour."""
        (tx, ty), (ax, ay), (bx, by) = tip, base_a, base_b
        mx, my = (ax + bx) / 2, (ay + by) / 2
        L = math.hypot(mx - tx, my - ty); dx, dy = (mx - tx) / L, (my - ty) / L
        half = math.hypot(bx - ax, by - ay) / 2
        for y in range(32):
            for x in range(32):
                px, py = x + 0.5, y + 0.5
                t = ((px - tx) * dx + (py - ty) * dy) / L
                if t < 0 or t > 1: continue
                u = ((px - tx) * -dy + (py - ty) * dx) / (half * t + 1e-6)
                if abs(u) > 1: continue
                s.put(x, y, colour_at(t, u))
    def curve(s, pts, colour, steps=60):
        # a 1-pixel streamer along a quadratic/cubic Bezier
        def bez(t):
            p = pts
            while len(p) > 1: p = [((1 - t) * a[0] + t * b[0], (1 - t) * a[1] + t * b[1]) for a, b in zip(p, p[1:])]
            return p[0]
        for i in range(steps + 1):
            x, y = bez(i / steps); s.put(x, y, colour)
    def sq(s, x, y, c, n=2):
        for i in range(n):
            for j in range(n): s.put(x + i, y + j, c)
    def spark(s, x, y, c):
        s.put(x, y, c); s.put(x - 1, y, c); s.put(x + 1, y, c); s.put(x, y - 1, c); s.put(x, y + 1, c)
    def outline(s):
        src = s.im.copy(); sp = src.load()
        for y in range(32):
            for x in range(32):
                if sp[x, y][3]: continue
                if any(0 <= x + dx < 32 and 0 <= y + dy < 32 and sp[x + dx, y + dy][3] > 200 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    s.px[x, y] = OUT + (255,)
        return s.im

def popper_open():
    p = P()
    for pts, c in [([(17, 14), (16, 8), (21, 6), (20, 2)], '#ff6fd8'), ([(18, 15), (23, 12), (26, 15), (30, 10)], '#ffe14d'),
                   ([(16, 13), (11, 9), (14, 5), (10, 2)], '#5bc8ff')]:
        p.curve(pts, c); p.curve([(a + 0.6, b + 0.6) for a, b in pts], c)
    for (x, y, c) in [(26, 4, '#7fe39a'), (23, 18, '#5bc8ff'), (7, 6, '#ffe14d'), (28, 20, '#ff6fd8')]: p.sq(x, y, c)
    p.spark(29, 3, '#ffffff'); p.put(13, 12, '#ffe14d')
    stripes = lambda t, u: ('#ffb3ee' if u < -0.45 else '#c2389f' if u > 0.45 else '#ff6fd8') if int(t * 5) % 2 else ('#c79bff' if u < -0.45 else '#5a2ab0' if u > 0.45 else '#9b5cff')
    p.cone((4, 29), (12, 13), (20, 21), stripes)
    for x in range(12, 21): p.put(x, 13 + (x - 12), '#ffe14d')   # gold rim
    return p.outline()


def popper_closed():
    """The same cone, still sealed: a gold foil cap over the mouth and a little pull string at the tip."""
    p = P()
    stripes = lambda t, u: ('#ffb3ee' if u < -0.45 else '#c2389f' if u > 0.45 else '#ff6fd8') if int(t * 5) % 2 else ('#c79bff' if u < -0.45 else '#5a2ab0' if u > 0.45 else '#9b5cff')
    p.cone((4, 29), (12, 13), (20, 21), stripes)
    # the foil cap: a dome over the mouth, crinkled
    cx, cy = 16, 17
    for y in range(32):
        for x in range(32):
            px, py = x + 0.5, y + 0.5
            along = ((px - cx) - (py - cy)) / 1.414           # out of the mouth (up and right) is +
            across = ((px - cx) + (py - cy)) / 1.414
            if -0.3 <= along <= 3.4 * math.sqrt(max(0, 1 - (across / 5.9) ** 2)) and abs(across) <= 5.9:
                col = '#fff2a0' if across < -2 else '#e2a93a' if across > 2.2 else '#ffcf5a'
                if (x + y) % 5 == 0 and abs(across) < 4: col = '#fff8d0'
                p.put(x, y, col)
    p.put(15, 14, '#ffffff'); p.put(16, 14, '#ffffff')          # a glint on the foil
    # the pull string from the tip
    p.curve([(4, 29), (2.5, 30.5), (3.5, 31.5)], '#ffffff', 20)
    return p.outline()


CONFETTI = ['#ff4d6d', '#ffcf3a', '#5be37c', '#5bc8ff', '#b98bff', '#ff8ad8']


def confetti_piece(i):
    """A small paper rectangle (8x8 canvas, 6x4 piece) with a lighter top edge, one per colour."""
    im = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    base = hexc(CONFETTI[i])
    light = tuple(min(255, int(v + (255 - v) * 0.45)) for v in base)
    darkc = tuple(int(v * 0.72) for v in base)
    for y in range(2, 6):
        for x in range(1, 7):
            im.putpixel((x, y), (light if y == 2 else darkc if y == 5 else base) + (255,))
    return im


def streamer_piece(i):
    """A curly paper streamer: a thin ribbon waving down a 4x16 strip."""
    im = Image.new('RGBA', (8, 16), (0, 0, 0, 0))
    base = hexc(CONFETTI[i])
    light = tuple(min(255, int(v + (255 - v) * 0.45)) for v in base)
    for y in range(16):
        x = 3 + round(1.6 * math.sin(y * 0.8))
        im.putpixel((x, y), light + (255,))
        im.putpixel((x + 1, y), base + (255,))
    return im
