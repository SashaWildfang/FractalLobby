"""Let It Snow and Pumpkin Launcher icon options: crisp 32 x 32 pixel art, centred, outlined (scarecrow painter)."""
import math
from scarecrow_icon import P

WHITE, CLOUD, CLOUD_M, CLOUD_D = '#ffffff', '#f2f6ff', '#d6e0f2', '#a9b8d6'
ICE, ICE_L, ICE_D = '#9fdcff', '#e0f6ff', '#5aa8e0'
OR, OR_L, OR_D, STEM, GLOW, INK = '#f08a1c', '#ffb04a', '#c0600e', '#4f7a2a', '#ffe14d', '#2a1a10'
IRON, IRON_L, IRON_D, WOOD, WOOD_D = '#4a4a5a', '#7a7a8e', '#2c2c38', '#8b5a2b', '#6a4220'

FLAKE_S = [".#.", "#.#", ".#."]
FLAKE_M = ["#...#", ".#.#.", "..#..", ".#.#.", "#...#"]
FLAKE_P = ["..#..", "#.#.#", ".###.", "#.#.#", "..#.."]
FLAKE_L = ["...#...", ".#.#.#.", "..###..", "#######", "..###..", ".#.#.#.", "...#..."]


def stamp(p, rows, x, y, c, centre=None):
    for ry, row in enumerate(rows):
        for rx, ch in enumerate(row):
            if ch == '#':
                p.dot(x + rx, y + ry, c)
    if centre:
        p.dot(x + len(rows[0]) // 2, y + len(rows) // 2, centre)


def disc(p, cx, cy, r, c):
    for y in range(int(cy - r - 1), int(cy + r + 2)):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                p.dot(x, y, c)


def thick(p, x0, y0, x1, y1, w, c):
    """A crisp thick line (pixels within w/2 of the segment)."""
    dx, dy = x1 - x0, y1 - y0
    L2 = dx * dx + dy * dy
    for y in range(32):
        for x in range(32):
            u = max(0, min(1, ((x - x0) * dx + (y - y0) * dy) / L2))
            if math.hypot(x - (x0 + u * dx), y - (y0 + u * dy)) <= w / 2:
                p.dot(x, y, c)


def cloud(p, top=3, dark=True):
    """A fluffy, round-topped cloud: three puffs over a flat soft base, lit from above, a cool blue-grey underside."""
    for (cx, cy, r) in ((9.5, top + 8, 5), (16, top + 5.5, 6.5), (22.5, top + 8, 5)):
        disc(p, cx, cy, r, CLOUD)
    p.rect(5, top + 8, 27, top + 12, CLOUD)
    disc(p, 5.5, top + 10.5, 2.5, CLOUD)
    disc(p, 26.5, top + 10.5, 2.5, CLOUD)
    p.rect(5, top + 11, 27, top + 13, CLOUD_M if dark else CLOUD)
    if dark:
        p.rect(7, top + 13, 25, top + 13, CLOUD_D)
    for (cx, cy, r) in ((14, top + 3.5, 2.2), (8, top + 6.5, 1.5)):        # highlights
        disc(p, cx, cy, r, WHITE)


def sn1():
    """A soft, fluffy snow cloud with crisp falling snowflakes of different sizes."""
    p = P()
    cloud(p, 1)
    stamp(p, FLAKE_P, 6, 18, WHITE, ICE_L)
    stamp(p, FLAKE_S, 15, 17, ICE_L)
    stamp(p, FLAKE_L, 13, 22, WHITE, ICE)
    stamp(p, FLAKE_S, 24, 18, WHITE)
    stamp(p, FLAKE_P, 22, 25, ICE_L, WHITE)
    stamp(p, FLAKE_S, 6, 26, ICE_L)
    return p.done()


def sn2():
    """The cloud snowing onto a little drift, with a tiny snowman standing in it."""
    p = P()
    cloud(p, 0)
    for (x, y) in ((7, 16), (12, 19), (22, 16), (26, 20), (17, 15)):
        stamp(p, FLAKE_S, x, y, WHITE)
    for x in range(2, 30):                                              # the drift
        h = 3 + round(1.5 * math.sin(x * 0.45))
        p.rect(x, 31 - h, x, 31, CLOUD)
    p.rect(2, 30, 29, 31, CLOUD_M)
    disc(p, 16, 24, 3.2, WHITE)                                        # snowman
    disc(p, 16, 19.2, 2.3, WHITE)
    p.dot(15, 18, INK)
    p.dot(17, 18, INK)
    p.rect(16, 19, 17, 19, OR)
    p.rect(14, 21, 18, 21, '#d03a3a')                                  # scarf
    p.dot(18, 22, '#d03a3a')
    p.dot(16, 23, INK)
    p.dot(16, 25, INK)
    return p.done()


def sn3():
    """One big crystal snowflake, icy blue with white tips, a little cloud tucked behind its top."""
    p = P()
    cx, cy = 16, 17
    for k in range(6):
        a = math.pi / 2 + k * math.pi / 3
        ex, ey = cx + math.cos(a) * 13, cy - math.sin(a) * 13
        thick(p, cx, cy, ex, ey, 2.2, ICE)
        for f in (0.5, 0.75):                                            # the little branches
            bx, by = cx + math.cos(a) * 13 * f, cy - math.sin(a) * 13 * f
            for s in (-1, 1):
                b = a + s * math.pi / 4
                thick(p, bx, by, bx + math.cos(b) * 3.6, by - math.sin(b) * 3.6, 1.4, ICE_L)
        disc(p, ex, ey, 1.2, WHITE)
    disc(p, cx, cy, 3, ICE_D)
    disc(p, cx, cy, 1.6, ICE_L)
    return p.done()


SNOW = [sn1, sn2, sn3]


# ---------------------------------------------------------------- the pumpkin launcher (held like a gun: barrel up-right)

def jack(p, cx, cy, r, lit=True):
    disc(p, cx, cy, r, OR)
    for dx in (-r * 0.45, r * 0.45):
        thick(p, cx + dx, cy - r * 0.8, cx + dx, cy + r * 0.8, 0.9, OR_D)
    disc(p, cx - r * 0.35, cy - r * 0.45, r * 0.25, OR_L)
    p.rect(int(cx) - 1, int(cy - r) - 2, int(cx), int(cy - r), STEM)
    face = GLOW if lit else INK
    p.rect(int(cx - r * 0.55), int(cy - r * 0.3), int(cx - r * 0.55) + 1, int(cy - r * 0.3) + 1, face)
    p.rect(int(cx + r * 0.25), int(cy - r * 0.3), int(cx + r * 0.25) + 1, int(cy - r * 0.3) + 1, face)
    p.rect(int(cx - r * 0.55), int(cy + r * 0.35), int(cx + r * 0.6), int(cy + r * 0.35), face)


def pl1():
    """An iron mortar tube with orange bands on a wooden stock, a lit jack-o'-lantern loaded at the muzzle."""
    p = P()
    thick(p, 4, 28, 10, 22, 5, WOOD)                                    # stock and grip
    thick(p, 5, 29, 9, 25, 2, WOOD_D)
    thick(p, 9, 23, 20, 12, 7, IRON)                                     # the tube
    thick(p, 8, 21, 19, 10, 1.6, IRON_L)
    thick(p, 11, 25, 22, 14, 1.4, IRON_D)
    for f in (0.25, 0.65):                                               # orange bands
        x, y = 9 + 11 * f, 23 - 11 * f
        thick(p, x - 3, y - 3, x + 3, y + 3, 2, OR_D)
    thick(p, 18, 10, 23, 15, 3, IRON_D)                                  # the muzzle rim
    jack(p, 23.5, 8.5, 5.5)
    return p.done()


BRASS, BRASS_L, BRASS_D = '#d4a03a', '#f2cc6a', '#9a6e1e'


def pl2():
    """A pumpkin blunderbuss: a wooden stock, a brass barrel flaring into a bell, a jack-o'-lantern bursting out of it
    in a puff of smoke."""
    p = P()
    thick(p, 3, 28, 10, 21, 5.5, WOOD)
    thick(p, 4, 29, 8, 25, 2, WOOD_D)
    thick(p, 9, 22, 19, 12, 3.6, BRASS)                                   # barrel
    thick(p, 8.5, 20.5, 18, 11, 1, BRASS_L)
    disc(p, 20.5, 10.5, 4.3, BRASS_D)                                     # the flared bell
    disc(p, 20.5, 10.5, 3.3, BRASS)
    disc(p, 20.5, 10.5, 2, INK)
    for (x, y, r) in ((24, 13, 2), (17, 6, 1.8), (26, 9, 1.4)):            # smoke puffs
        disc(p, x, y, r, '#d8dce6')
    jack(p, 26, 4.5, 3.8)
    return p.done()


def pl3():
    """A pumpkin crossbow: a dark wooden stock, curved orange limbs with a taut string, a mini jack-o'-lantern loaded."""
    p = P()
    thick(p, 4, 28, 20, 12, 3.6, WOOD)                                    # the stock
    thick(p, 4, 28, 9, 23, 4.6, WOOD_D)
    thick(p, 9, 21, 11, 23, 2, IRON_D)                                    # trigger guard
    mx, my = 18.5, 13.5                                                   # curved limbs, across the stock, bent back
    vx, vy, bx, by = 0.7071, 0.7071, -0.7071, 0.7071
    for sgn in (-1, 1):
        pts = [(mx + vx * k * sgn + bx * 0.07 * k * k, my + vy * k * sgn + by * 0.07 * k * k) for k in range(0, 10)]
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            thick(p, x0, y0, x1, y1, 2.2, OR)
        ex, ey = pts[-1]
        thick(p, ex, ey, 13, 19, 0.8, '#e8e8f0')                          # the string, drawn back
    thick(p, 18, 14, 21, 11, 2.6, IRON)                                   # the bow's mount
    jack(p, 24, 7.5, 4)
    return p.done()


def tube(p, tip=(21, 11)):
    """The mortar from #1: a wooden stock and grip, an iron tube with orange bands, a dark rimmed muzzle at `tip`."""
    tx, ty = tip
    thick(p, 4, 28, 10, 22, 5, WOOD)
    thick(p, 5, 29, 9, 25, 2, WOOD_D)
    thick(p, 9, 23, tx - 1, ty + 1, 7, IRON)
    thick(p, 8, 21, tx - 2, ty - 1, 1.6, IRON_L)
    thick(p, 11, 25, tx + 1, ty + 3, 1.4, IRON_D)
    for f in (0.22, 0.6):
        x, y = 9 + (tx - 10) * f, 23 - (23 - ty) * f
        thick(p, x - 3, y - 3, x + 3, y + 3, 2, OR_D)
    thick(p, tx - 3, ty - 2, tx + 2, ty + 3, 3.4, IRON_D)            # the muzzle rim


def pa():
    """A carved launcher: the iron tube itself has a glowing jack-o'-lantern face cut into its side, and the muzzle
    glows orange from inside."""
    p = P()
    tube(p, (23, 9))
    # the carved face along the tube (eyes and a grin, glowing through the iron)
    for (x, y) in ((12, 17), (16, 13)):
        p.rect(x, y, x + 1, y + 1, GLOW)
    for k in range(4):
        p.dot(15 + k, 20 - k, GLOW)
    p.dot(15, 19, GLOW)
    p.dot(19, 15, GLOW)
    disc(p, 23, 9, 1.6, OR)                                             # glowing inside the muzzle
    p.dot(23, 9, GLOW)
    return p.done()


def pb():
    """Mid-shot: a bright orange-and-yellow muzzle flash bursting out of the tube, with pumpkin seeds flying."""
    p = P()
    tube(p, (19, 13))
    cx, cy = 24, 8
    for k in range(8):                                                   # the flash: a spiky star
        a = k * math.pi / 4 + math.pi / 8
        r = 6 if k % 2 == 0 else 4
        thick(p, cx, cy, cx + math.cos(a) * r, cy + math.sin(a) * r, 1.6, OR)
    disc(p, cx, cy, 2.6, GLOW)
    disc(p, cx, cy, 1.2, '#ffffff')
    for (x, y) in ((29, 3), (30, 9), (26, 1), (22, 2)):                   # seeds
        p.dot(x, y, '#f4e8c0')
    return p.done()


def pc():
    """Fired: a short smoking muzzle, and a little jack-o'-lantern well clear of it, flying off along a sparkly arc."""
    p = P()
    tube(p, (17, 15))
    for i, (x, y) in enumerate(((20, 12), (21.5, 9.5), (23, 7.5), (24.5, 6))):   # the spark trail
        p.dot(round(x), round(y), GLOW if i % 2 else OR_L)
    jack(p, 27, 4, 3.4)
    return p.done()


LAUNCHER = [pa, pb, pc]
