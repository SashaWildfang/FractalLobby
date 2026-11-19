"""The blink stick icon (Sasha picked the portal wand): a dark wand ending in a swirling purple portal, centred."""
import math
from PIL import Image
OUT = (0x1a, 0x1b, 0x26)
def hexc(h): h = h.lstrip('#'); return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))

class P:
    def __init__(s): s.im = Image.new('RGBA', (32, 32), (0, 0, 0, 0)); s.px = s.im.load()
    def put(s, x, y, c, a=255):
        x, y = int(x), int(y)
        if 0 <= x < 32 and 0 <= y < 32: s.px[x, y] = (c if isinstance(c, tuple) else hexc(c)) + (a,)
    def stick(s, x0, y0, x1, y1, light, mid, darkc):
        n = max(abs(x1 - x0), abs(y1 - y0))
        for i in range(n + 1):
            x = round(x0 + (x1 - x0) * i / n); y = round(y0 + (y1 - y0) * i / n)
            s.put(x - 1, y - 1, light); s.put(x - 1, y, light); s.put(x, y - 1, mid); s.put(x, y, mid)
            s.put(x + 1, y, darkc); s.put(x, y + 1, darkc); s.put(x + 1, y + 1, darkc)
    def disc(s, cx, cy, r, colour_at):
        for y in range(32):
            for x in range(32):
                dx, dy = x + 0.5 - cx, y + 0.5 - cy
                if dx * dx + dy * dy <= r * r: s.put(x, y, colour_at(dx / r, dy / r))
    def outline(s):
        src = s.im.copy(); sp = src.load()
        for y in range(32):
            for x in range(32):
                if sp[x, y][3]: continue
                if any(0 <= x + dx < 32 and 0 <= y + dy < 32 and sp[x + dx, y + dy][3] > 200 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    s.px[x, y] = OUT + (255,)
        return s.im

def opt3():   # portal wand: a dark wand ending in a swirling purple portal ring
    p = P()
    p.stick(5, 28, 15, 18, '#5a4a7a', '#3a2a5a', '#22183a')
    p.stick(4, 29, 6, 27, '#d8b8ff', '#9b5cff', '#5a24b0')
    cx, cy = 21, 11
    for y in range(32):
        for x in range(32):
            dx, dy = (x + 0.5 - cx) / 7.2, (y + 0.5 - cy) / 7.2
            d = math.hypot(dx, dy)
            if d > 1: continue
            a = math.atan2(dy, dx)
            if d > 0.72:                                       # the ring
                p.put(x, y, '#e8d0ff' if dx + dy < -0.6 else '#b07bff' if dx + dy < 0.5 else '#7a3ae0')
            else:                                              # the swirl inside
                band = math.sin(a * 2 + d * 7)
                p.put(x, y, '#c08cff' if band > 0.5 else '#6a2ad0' if band > -0.3 else '#2a1060')
    p.put(21, 11, '#ffffff')
    return p.outline()

def centred(im):
    b = im.getbbox(); dx, dy = round(16 - (b[0] + b[2]) / 2), round(16 - (b[1] + b[3]) / 2)
    out = Image.new('RGBA', (32, 32), (0, 0, 0, 0)); out.alpha_composite(im, (dx, dy)); return out


def blink_stick_icon():
    return centred(opt3())
