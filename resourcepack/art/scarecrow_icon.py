"""Scarecrow icon options: crisp 32 x 32 pixel art, drawn pixel by pixel, centred, with a dark outline."""
from PIL import Image

HAT, HAT_D, BAND = '#e2b04e', '#b07f2e', '#b8322e'
SACK, SACK_D, INK = '#dcc092', '#b8966a', '#2a1a10'
SHIRT, PLAID = '#c8463a', '#8e2a24'
STRAW, STRAW_D = '#f2d35e', '#c9a23a'
WOOD, WOOD_D = '#8b5a2b', '#6a4220'
CROW, CROW_L, BEAK = '#24242e', '#454558', '#e8b23a'
DENIM, DENIM_D = '#5577b4', '#3d5890'
COAT, COAT_D = '#5a4a6e', '#3f3352'
GLOW = '#ffd84a'


def rgba(c):
    return tuple(int(c[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


class P:
    def __init__(self):
        self.im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
        self.px = self.im.load()

    def dot(self, x, y, c):
        if 0 <= x < 32 and 0 <= y < 32:
            self.px[x, y] = rgba(c)

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.dot(x, y, c)

    def clear(self, x, y):
        if 0 <= x < 32 and 0 <= y < 32:
            self.px[x, y] = (0, 0, 0, 0)

    def done(self):
        """A one-pixel outline, a deep shade of whatever it borders, then centred."""
        src = self.im.copy()
        s = src.load()
        out = self.im.load()
        for y in range(32):
            for x in range(32):
                if s[x, y][3]:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    X, Y = x + dx, y + dy
                    if 0 <= X < 32 and 0 <= Y < 32 and s[X, Y][3]:
                        r, g, b, _ = s[X, Y]
                        out[x, y] = (int(r * 0.28 + 12), int(g * 0.26 + 8), int(b * 0.3 + 14), 255)
                        break
        b = self.im.getbbox()
        res = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
        res.alpha_composite(self.im, (round(16 - (b[0] + b[2]) / 2), round(16 - (b[1] + b[3]) / 2)))
        return res


def crow(p, x, y, facing=-1):
    """A little perched crow, 5 wide, its beak pointing `facing` (-1 left, 1 right); (x, y) its back-top corner."""
    f = facing
    p.rect(x, y + 1, x + 3, y + 3, CROW)                             # body
    p.rect(x + (0 if f > 0 else 1), y, x + (2 if f > 0 else 3), y, CROW)
    hx = x + (3 if f > 0 else 0)
    p.rect(hx, y - 1, hx, y, CROW)                                   # head
    p.dot(hx + f, y - 1, BEAK)
    p.dot(hx, y - 1, '#ffffff')                                      # a bright eye
    p.dot(x + (0 if f > 0 else 3) - f, y + 2, CROW)                  # tail
    p.rect(x + 1, y + 1, x + 2, y + 1, CROW_L)                       # wing shine
    p.dot(x + 1, y + 4, BEAK)
    p.dot(x + 2, y + 4, BEAK)                                        # feet


def sc1():
    """Classic: a burlap-sack head with button eyes and a stitched smile, a straw hat with a red band, arms out on
    the crossbar in a red plaid shirt, straw sprouting from the cuffs and hem, and a crow perched on one arm."""
    p = P()
    p.rect(15, 21, 16, 31, WOOD)
    p.rect(16, 21, 16, 31, WOOD_D)                                    # the post
    # arms (the sleeves on the crossbar) and straw hands
    p.rect(5, 13, 26, 15, SHIRT)
    p.rect(5, 15, 26, 15, PLAID)
    for x in (8, 23):
        p.rect(x, 13, x, 15, PLAID)
    for (x, ys) in ((2, (12, 13, 14, 15, 16)), (3, (13, 14, 15)), (4, (12, 14, 16)),
                    (29, (12, 13, 14, 15, 16)), (28, (13, 14, 15)), (27, (12, 14, 16))):
        for y in ys:
            p.dot(x, y, STRAW if (x + y) % 2 else STRAW_D)
    # the body: plaid shirt, straw poking out of the hem
    p.rect(11, 12, 20, 21, SHIRT)
    for x in (13, 18):
        p.rect(x, 12, x, 21, PLAID)
    p.rect(11, 17, 20, 17, PLAID)
    p.rect(15, 12, 16, 21, '#a9382e')                                 # buttoned front
    p.dot(15, 14, INK)
    p.dot(15, 19, INK)
    for x in (11, 13, 16, 18, 20):
        p.dot(x, 22, STRAW)
    for x in (12, 17):
        p.dot(x, 23, STRAW_D)
    # the sack head, tied off with twine
    p.rect(11, 4, 20, 10, SACK)
    p.rect(12, 3, 19, 3, SACK)
    p.rect(11, 10, 20, 10, SACK_D)
    p.rect(19, 5, 20, 9, SACK_D)
    p.rect(13, 11, 18, 11, STRAW_D)
    p.rect(13, 6, 14, 7, INK)                                          # button eyes
    p.rect(17, 6, 18, 7, INK)
    p.dot(13, 6, '#5a4a3a')
    p.dot(17, 6, '#5a4a3a')
    for x in (13, 15, 17):
        p.dot(x, 9, INK)                                               # stitched smile
    p.dot(12, 8, INK)
    p.dot(19, 8, INK)
    for x in (14, 16, 18):
        p.dot(x, 9, '#7a5a3a')
    # straw hat
    p.rect(8, 3, 23, 4, HAT)
    p.rect(8, 4, 23, 4, HAT_D)
    p.rect(11, 0, 20, 2, HAT)
    p.rect(11, 2, 20, 2, BAND)
    p.rect(19, 0, 20, 1, HAT_D)
    crow(p, 23, 9, facing=-1)                                          # on the right arm
    return p.done()


def sc2():
    """Spooky: a dark tattered coat, a droopy pointed hat, a jagged stitched grin and glowing eyes, one crow on the hat
    and one on an arm."""
    p = P()
    p.rect(15, 22, 16, 31, WOOD)
    p.rect(16, 22, 16, 31, WOOD_D)
    p.rect(4, 14, 27, 16, COAT)
    p.rect(4, 16, 27, 16, COAT_D)
    for x in (4, 6, 25, 27):                                           # ragged cuffs
        p.dot(x, 17, COAT_D)
    for (x, y) in ((2, 13), (2, 15), (3, 14), (3, 16), (29, 13), (29, 15), (28, 14), (28, 16), (2, 17), (29, 17)):
        p.dot(x, y, STRAW if (x + y) % 2 else STRAW_D)
    p.rect(11, 13, 20, 22, COAT)
    p.rect(15, 13, 16, 22, COAT_D)
    for x in (11, 14, 17, 20):                                          # tattered hem
        p.dot(x, 23, COAT)
    for x in (12, 18):
        p.dot(x, 24, COAT_D)
    p.dot(13, 18, '#7a6a52')
    p.rect(12, 18, 13, 19, '#7a6a52')                                   # a patch
    p.rect(11, 5, 20, 11, SACK)
    p.rect(12, 4, 19, 4, SACK)
    p.rect(19, 6, 20, 11, SACK_D)
    p.rect(13, 12, 18, 12, STRAW_D)
    p.dot(13, 7, GLOW)                                                 # glowing triangle eyes
    p.rect(13, 8, 14, 8, GLOW)
    p.dot(18, 7, GLOW)
    p.rect(17, 8, 18, 8, GLOW)
    for i, x in enumerate(range(12, 20)):                              # a jagged stitched grin
        p.dot(x, 10 if i % 2 else 11, INK)
    p.rect(10, 4, 21, 5, '#3a3048')                                    # the droopy pointed hat
    p.rect(12, 2, 19, 3, '#3a3048')
    p.rect(14, 0, 18, 1, '#3a3048')
    p.dot(19, 0, '#3a3048')
    p.dot(20, 1, '#3a3048')
    p.rect(12, 3, 19, 3, BAND)
    crow(p, 5, 9, facing=1)
    crow(p, 23, 10, facing=-1)
    return p.done()


def sc3():
    """Close up: a big sack-head face with a patch and a stitched grin under a wide straw hat, straw sticking out of the
    sides, and a crow sitting on the brim."""
    p = P()
    p.rect(8, 11, 23, 25, SACK)
    p.rect(9, 26, 22, 26, SACK)
    p.rect(21, 12, 23, 25, SACK_D)
    p.rect(9, 26, 22, 26, SACK_D)
    p.rect(10, 27, 21, 28, STRAW_D)                                    # tied at the neck
    for (x, y) in ((8, 29), (11, 29), (14, 29), (17, 29), (20, 29), (23, 29), (9, 30), (15, 30), (21, 30)):
        p.dot(x, y, STRAW)
    for (x, y) in ((5, 12), (6, 13), (5, 14), (6, 15), (26, 12), (25, 13), (26, 14), (25, 15)):
        p.dot(x, y, STRAW if y % 2 else STRAW_D)
    p.rect(7, 12, 7, 15, STRAW_D)
    p.rect(24, 12, 24, 15, STRAW_D)
    p.rect(10, 14, 13, 17, '#8a6a46')                                  # a patch over one eye, cross-stitched on
    for (x, y) in ((10, 14), (13, 14), (10, 17), (13, 17)):
        p.dot(x, y, INK)
    p.rect(17, 15, 19, 17, INK)                                         # a big button eye
    p.dot(17, 15, '#5a4a3a')
    p.rect(11, 21, 20, 21, INK)                                         # stitched grin
    p.dot(10, 20, INK)
    p.dot(21, 20, INK)
    for x in (12, 15, 18):
        p.rect(x, 20, x, 22, INK)
    p.rect(3, 9, 28, 10, HAT)                                           # the wide brim
    p.rect(3, 10, 28, 10, HAT_D)
    p.rect(9, 3, 22, 8, HAT)
    p.rect(9, 7, 22, 8, BAND)
    p.rect(20, 3, 22, 6, HAT_D)
    for x in range(10, 21, 3):
        p.dot(x, 5, HAT_D)                                              # weave
    crow(p, 23, 4, facing=-1)
    return p.done()


SCARECROW = [sc1, sc2, sc3]
