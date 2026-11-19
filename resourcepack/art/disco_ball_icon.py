"""The disco ball icon (Sasha picked the pastel one): a round mirror ball, tiles catching soft colours, a thin rim."""
import math
from PIL import Image
OUT = (0x1a, 0x1b, 0x26)
CX = CY = 16.0
R = 14.2

def tile_of(x, y, rows, cols):
    dx, dy = (x + 0.5 - CX) / R, (y + 0.5 - CY) / R
    if dx * dx + dy * dy > 1: return None
    nz = math.sqrt(1 - dx * dx - dy * dy)
    lat = math.asin(-dy)                                  # -pi/2 .. pi/2
    lon = math.atan2(dx, nz)                              # -pi/2 .. pi/2 on the front
    return (math.floor((lat / math.pi + 0.5) * rows), math.floor((lon / math.pi + 0.5) * cols))

def ball(colour_of, rows=7, cols=7, grout=(70, 72, 96)):
    """A round mirror ball: tiles laid in rows that curve with the sphere (1-pixel grout drawn where the tile changes),
    lit softly from the top left, with a bright shine."""
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0)); px = im.load()
    for y in range(32):
        for x in range(32):
            t = tile_of(x, y, rows, cols)
            if t is None: continue
            dx, dy = (x + 0.5 - CX) / R, (y + 0.5 - CY) / R
            nz = math.sqrt(max(0, 1 - dx * dx - dy * dy))
            light = max(0.0, -0.45 * dx - 0.5 * dy + 0.74 * nz)
            right, below = tile_of(x + 1, y, rows, cols), tile_of(x, y + 1, rows, cols)
            edge = dx * dx + dy * dy > 0.87                  # a smooth shaded rim keeps the outline round and clean
            if edge:
                c = tuple(int(v * (0.4 + 0.4 * light)) for v in (170, 174, 196))
            elif (right is not None and right != t) or (below is not None and below != t):
                c = tuple(int(v * (0.55 + 0.45 * light)) for v in grout)
            else:
                base = colour_of(t)
                k = 0.42 + 0.68 * light ** 1.3
                c = tuple(min(255, int(v * k)) for v in base)
            px[x, y] = c + (255,)
    # a soft shine on the upper left
    for (x, y) in [(10, 9), (11, 9), (10, 10)]:
        px[x, y] = (255, 255, 255, 255)
    # outline
    src = im.copy(); sp = src.load()
    for y in range(32):
        for x in range(32):
            if sp[x, y][3]: continue
            if any(0 <= x + a < 32 and 0 <= y + b < 32 and sp[x + a, y + b][3] for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = OUT + (255,)
    return im

SILVER = (214, 218, 232)
def disco_ball_icon():
    pal = [(240, 180, 228), (172, 212, 255), (206, 184, 255), (255, 228, 168), SILVER, SILVER]
    return ball(lambda t: pal[(t[0] * 2 + t[1] * 3) % len(pal)])

