"""The bubble wand icon, drawn pixel by pixel (chosen by Sasha from four options)."""
import math
from PIL import Image

def hexc(h): h = h.lstrip('#'); return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))
OUT = (0x1a, 0x1b, 0x26)

class P:
    def __init__(s): s.im = Image.new('RGBA', (32, 32), (0, 0, 0, 0)); s.px = s.im.load()
    def put(s, x, y, c, a=255):
        if 0 <= x < 32 and 0 <= y < 32: s.px[x, y] = (c if isinstance(c, tuple) else hexc(c)) + (a,)
    def handle(s, x0, y0, x1, y1, cols, stripes=None):
        # a diagonal stick 3 pixels thick (down-left to up-right), shaded light / mid / dark across it
        n = max(abs(x1 - x0), abs(y1 - y0))
        for i in range(n + 1):
            x = round(x0 + (x1 - x0) * i / n); y = round(y0 + (y1 - y0) * i / n)
            band = cols
            if stripes and (i // 2) % 2: band = stripes
            s.put(x - 1, y - 1, band[0]); s.put(x - 1, y, band[0]); s.put(x, y - 1, band[1]); s.put(x, y, band[1])
            s.put(x + 1, y, band[2]); s.put(x, y + 1, band[2]); s.put(x + 1, y + 1, band[2])
    def ring(s, cx, cy, ro, ri, light, mid, darkc, film=True, shape=None):
        inside = shape or (lambda x, y, r: math.hypot(x - cx, y - cy) <= r)
        for y in range(32):
            for x in range(32):
                px, py = x + 0.5, y + 0.5
                if inside(px, py, ro) and not inside(px, py, ri):
                    # light from the top left
                    t = ((px - cx) + (py - cy)) / (2 * ro)
                    s.put(x, y, light if t < -0.25 else darkc if t > 0.25 else mid)
                elif film and inside(px, py, ri):
                    # a soap film: mostly clear, with a rainbow sheen swirling round its lower right
                    d = ((px - cx) + (py - cy)) / (ri * 1.414)
                    if d > 0.55: col, al = (255, 170, 230), 170
                    elif d > 0.35: col, al = (255, 235, 150), 160
                    elif d > 0.15: col, al = (160, 235, 255), 150
                    else: col, al = (220, 245, 255), 60
                    s.put(x, y, col, al)
    def bubble(s, cx, cy, r):
        for y in range(32):
            for x in range(32):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if r - 1.0 < d <= r: s.put(x, y, '#c8f4ff' if (x + 0.5 - cx) + (y + 0.5 - cy) < 0 else '#ffb8f0')
                elif d <= r - 1.0:
                    k = ((x + 0.5 - cx) + (y + 0.5 - cy)) / (r * 1.414)
                    s.put(x, y, (255, 180, 235) if k > 0.45 else (160, 235, 255) if k > 0.2 else (220, 245, 255), 150 if k > 0.2 else 50)
        s.put(int(cx - r * 0.45), int(cy - r * 0.45), '#ffffff')
    def outline(s):
        src = s.im.copy(); sp = src.load()
        for y in range(32):
            for x in range(32):
                if sp[x, y][3]: continue
                if any(0 <= x + dx < 32 and 0 <= y + dy < 32 and sp[x + dx, y + dy][3] > 200 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    s.px[x, y] = OUT + (255,)
        return s.im

GOLD = ('#fff2a0', '#ffcf5a', '#d9962a')
PINK = ('#ffb3ee', '#ff6fd8', '#c2389f')

def bubble_wand():
    """The bubble wand: a gold ring holding a rainbow soap film, on a pink handle with a gold end (hand-placed pixels)."""
    p = P()
    p.handle(5, 27, 14, 18, PINK)
    p.handle(4, 28, 6, 26, ('#fff2a0', '#ffd23a', '#d9962a'))
    p.ring(19.5, 12.5, 7.7, 5.9, *GOLD)
    p.put(16, 9, '#ffffff'); p.put(17, 9, '#ffffff'); p.put(16, 10, '#ffffff')
    return p.outline()

