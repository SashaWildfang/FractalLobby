"""
The suits: drawn armour textures (fractal:suit_<set>, via the equippable asset) and their 32x32 icons.

Each suit paints two 64x32 armour layers in the game's layout: "humanoid" (helmet = head, chestplate = body and arms,
boots = the bottom of the legs) and "humanoid_leggings" (leggings = legs and the waist). The icons are drawn from
the same textures (little 3D blocks of each piece), so they always match what players wear.
"""
import json, os, random
from PIL import Image
from kit import hexc, mix, light, dark

# face regions (x, y, w, h) in a 64x32 armour texture
HEAD = {'top': (8, 0, 8, 8), 'bottom': (16, 0, 8, 8), 'right': (0, 8, 8, 8), 'front': (8, 8, 8, 8), 'left': (16, 8, 8, 8),
        'back': (24, 8, 8, 8)}
BODY = {'top': (20, 16, 8, 4), 'bottom': (28, 16, 8, 4), 'right': (16, 20, 4, 12), 'front': (20, 20, 8, 12), 'left': (28, 20, 4, 12),
        'back': (32, 20, 8, 12)}
ARM = {'top': (44, 16, 4, 4), 'bottom': (48, 16, 4, 4), 'right': (40, 20, 4, 12), 'front': (44, 20, 4, 12), 'left': (48, 20, 4, 12),
       'back': (52, 20, 4, 12)}
LEG = {'top': (4, 16, 4, 4), 'bottom': (8, 16, 4, 4), 'right': (0, 20, 4, 12), 'front': (4, 20, 4, 12), 'left': (8, 20, 4, 12),
       'back': (12, 20, 4, 12)}
SIDES = ('right', 'front', 'left', 'back')


def c3(c):
    return hexc(c) if isinstance(c, str) else tuple(c)


class Layer:
    def __init__(self, seed):
        self.img = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
        self.r = random.Random(seed)

    def put(self, part, face, x, y, col):
        fx, fy, w, h = part[face]
        if 0 <= x < w and 0 <= y < h:
            self.img.putpixel((fx + x, fy + y), c3(col) + (255,) if col is not None else (0, 0, 0, 0))

    def clear(self, part, face, x0=0, y0=0, x1=99, y1=99):
        fx, fy, w, h = part[face]
        for y in range(max(0, y0), min(h, y1 + 1)):
            for x in range(max(0, x0), min(w, x1 + 1)):
                self.img.putpixel((fx + x, fy + y), (0, 0, 0, 0))

    def cloth(self, part, col, rows=(0, 99), faces=SIDES + ('top', 'bottom'), noise=0.07, fluffy=False):
        """Fills faces with a colour: a little noise, lighter at the top row, darker at the bottom."""
        base = c3(col)
        for face in faces:
            fx, fy, w, h = part[face]
            y0, y1 = (0, h - 1) if face in ('top', 'bottom') else (rows[0], min(rows[1], h - 1))
            for y in range(y0, y1 + 1):
                for x in range(w):
                    n = self.r.uniform(-noise, noise) * (2.2 if fluffy else 1)
                    c = light(base, n) if n > 0 else dark(base, -n)
                    if face not in ('top', 'bottom'):
                        if y == y0 and y0 == 0:
                            c = light(c, 0.08)
                        if y == y1:
                            c = dark(c, 0.12)
                        if face in ('right', 'left'):
                            c = dark(c, 0.06)
                        if face == 'back':
                            c = dark(c, 0.1)
                    if face == 'bottom':
                        c = dark(c, 0.2)
                    self.img.putpixel((fx + x, fy + y), c + (255,))

    def row(self, part, y, col, faces=SIDES):
        for face in faces:
            w = part[face][2]
            for x in range(w):
                self.put(part, face, x, y, col)

    def col(self, part, face, x, col, y0=0, y1=99):
        h = part[face][3]
        for y in range(max(0, y0), min(h, y1 + 1)):
            self.put(part, face, x, y, col)

    def window(self, x0=1, y0=2, x1=6, y1=7):
        """Opens the face of a hood."""
        self.clear(HEAD, 'front', x0, y0, x1, y1)
        self.clear(HEAD, 'bottom')

    def trim(self, col, x0=1, y0=2, x1=6, y1=7):
        for x in range(x0 - 1, x1 + 2):
            self.put(HEAD, 'front', x, y0 - 1, col)
        for y in range(y0, y1 + 1):
            self.put(HEAD, 'front', x0 - 1, y, col)
            self.put(HEAD, 'front', x1 + 1, y, col)


def shirt(l1, col, sleeves=12, faces_body=True, fluffy=False):
    if faces_body:
        l1.cloth(BODY, col, fluffy=fluffy)
    if sleeves:
        l1.cloth(ARM, col, rows=(0, sleeves - 1), faces=SIDES + ('top',), fluffy=fluffy)
        if sleeves < 12:
            l1.clear(ARM, 'bottom')


def trousers(l2, col, rows=(0, 11), waist=True, fluffy=False):
    l2.cloth(LEG, col, rows=rows, faces=SIDES + ('top',), fluffy=fluffy)
    if waist:
        l2.cloth(BODY, col, rows=(7, 11), faces=SIDES + ('bottom',), fluffy=fluffy)


def boots(l1, col, rows=(8, 11), sole=None, fluffy=False):
    l1.cloth(LEG, col, rows=rows, faces=SIDES + ('bottom',), fluffy=fluffy)
    if sole:
        l1.row(LEG, 11, sole)


# ---------------------------------------------------------------- the suits

def plumber(l1, l2):
    RED, BLUE, BROWN = '#e0362e', '#2f5bd8', '#6b3e1e'
    l1.cloth(HEAD, RED, rows=(0, 2), faces=SIDES + ('top',))
    for x in (3, 4):
        for y in (0, 1):
            l1.put(HEAD, 'front', x, y, '#ffffff')
    l1.put(HEAD, 'front', 3, 1, '#d8d8e0')
    for x in range(1, 7):
        l1.put(HEAD, 'front', x, 2, dark(RED, 0.25))
    shirt(l1, RED)
    for x in (1, 6):
        l1.col(BODY, 'front', x, BLUE, 0, 6)
        l1.col(BODY, 'back', x, BLUE, 0, 6)
    for y in range(7, 12):
        l1.row(BODY, y, BLUE)
    for x in (1, 6):
        l1.put(BODY, 'front', x, 7, '#ffd23f')
    trousers(l2, BLUE)
    for x in (0, 3):
        l2.put(LEG, 'front', x, 2, dark(BLUE, 0.2))
    boots(l1, BROWN, sole='#3a2010')


def ninja(l1, l2):
    BLACK, GREY, RED = '#1c1c26', '#3a3a4a', '#c8282e'
    l1.cloth(HEAD, BLACK)
    l1.row(HEAD, 1, RED)
    l1.row(HEAD, 2, dark(RED, 0.2))
    for y in (1, 2, 3):
        l1.put(HEAD, 'back', 3 + (y - 1), 2 + y, RED)
    l1.clear(HEAD, 'front', 1, 3, 6, 4)
    l1.clear(HEAD, 'bottom')
    shirt(l1, BLACK)
    for i in range(6):
        l1.put(BODY, 'front', 1 + i // 2 + (1 if i % 2 else 0), i, GREY)
        l1.put(BODY, 'front', 6 - i // 2 - (1 if i % 2 else 0), i, GREY)
    l1.row(BODY, 9, RED)
    l1.row(BODY, 10, dark(RED, 0.25))
    trousers(l2, BLACK)
    for y in (6, 8):
        l2.row(LEG, y, GREY)
    boots(l1, GREY, rows=(9, 11), sole='#14141c')
    l1.put(LEG, 'front', 1, 10, '#14141c')
    l1.put(LEG, 'front', 1, 11, '#14141c')


def astronaut(l1, l2):
    WHITE, GREY, DARK = '#f0f0f4', '#a8acb8', '#5a5e6e'
    l1.cloth(HEAD, WHITE)
    for y in range(2, 7):
        for x in range(1, 7):
            t = (y - 2) / 4
            c = mix('#ffd77a', '#2f6fe0', t) if x > 1 else mix('#fff2c0', '#5a9aff', t)
            l1.put(HEAD, 'front', x, y, c)
    l1.put(HEAD, 'front', 2, 3, '#ffffff')
    l1.put(HEAD, 'front', 3, 2, '#ffffff')
    for face in ('right', 'left'):
        l1.put(HEAD, face, 3, 4, GREY)
        l1.put(HEAD, face, 4, 4, GREY)
        l1.put(HEAD, face, 3, 5, DARK)
        l1.put(HEAD, face, 4, 5, DARK)
    l1.put(HEAD, 'top', 6, 1, '#e03a3a')
    shirt(l1, WHITE)
    for y in range(3, 8):
        for x in range(2, 6):
            l1.put(BODY, 'front', x, y, GREY if y in (3, 7) or x in (2, 5) else DARK)
    for (x, y, c) in [(3, 4, '#e03a3a'), (4, 4, '#4fd36b'), (3, 5, '#5b9dff'), (4, 5, '#ffd23f'), (3, 6, '#ffffff')]:
        l1.put(BODY, 'front', x, y, c)
    l1.row(BODY, 10, GREY)
    l1.row(ARM, 0, GREY)
    l1.row(ARM, 10, GREY)
    l1.row(ARM, 11, DARK)
    for (x, y, c) in [(0, 2, '#2f5bd8'), (1, 2, '#ffffff'), (2, 2, '#e03a3a'), (3, 2, '#e03a3a'), (0, 3, '#2f5bd8'), (1, 3, '#2f5bd8'),
                      (2, 3, '#ffffff'), (3, 3, '#ffffff')]:
        l1.put(ARM, 'right', x, y, c)
    for y in range(9, 12):
        l2.row(BODY, y, WHITE)
    trousers(l2, WHITE)
    l2.row(LEG, 5, GREY)
    l2.row(LEG, 6, GREY)
    boots(l1, '#c8ccd6', rows=(7, 11), sole=DARK)
    l1.row(LEG, 7, GREY)


def frog(l1, l2):
    GREEN, LIGHT, DARKG = '#4fbf3c', '#c8f0a0', '#2e8a24'
    l1.cloth(HEAD, GREEN)
    l1.window(1, 2, 6, 6)
    l1.trim(DARKG, 1, 2, 6, 6)
    for x in range(0, 8):
        l1.put(HEAD, 'front', x, 7, GREEN)
    for x in (1, 2, 5, 6):
        l1.put(HEAD, 'top', x, 6, '#ffffff')
        l1.put(HEAD, 'top', x, 7, '#ffffff')
        l1.put(HEAD, 'front', x, 0, '#ffffff')
    for x in (2, 5):
        l1.put(HEAD, 'top', x, 7, '#1c1c24')
        l1.put(HEAD, 'front', x, 0, '#1c1c24')
    shirt(l1, GREEN)
    for y in range(1, 12):
        for x in range(2, 6):
            l1.put(BODY, 'front', x, y, LIGHT if (x, y) not in ((2, 1), (5, 1)) else GREEN)
    for (x, y) in [(1, 3), (2, 7), (0, 9)]:
        l1.put(BODY, 'back', x + 2, y, DARKG)
        l1.put(ARM, 'right', x, y, DARKG)
    trousers(l2, GREEN)
    for y in range(7, 12):
        for x in range(2, 6):
            l2.put(BODY, 'front', x, y, LIGHT)
    l2.put(LEG, 'right', 1, 4, DARKG)
    l2.put(LEG, 'left', 2, 2, DARKG)
    boots(l1, DARKG, rows=(9, 11))
    for x in (0, 2):
        l1.put(LEG, 'front', x, 11, LIGHT)
        l1.put(LEG, 'front', x + 1, 10, LIGHT)


def lifeguard(l1, l2):
    RED, YELLOW = '#e0362e', '#ffd23f'
    l1.cloth(HEAD, YELLOW, rows=(1, 2), faces=SIDES)
    for x in range(1, 7):
        l1.put(HEAD, 'front', x, 2, dark(YELLOW, 0.2))
    for x in range(8):
        l1.put(HEAD, 'front', x, 1, '#ffffff' if x in (3, 4) else YELLOW)
    l1.cloth(BODY, RED)
    for (x, y) in [(3, 2), (4, 2), (2, 3), (3, 3), (4, 3), (5, 3), (3, 4), (4, 4)]:
        l1.put(BODY, 'front', x, y, '#ffffff')
    l1.put(BODY, 'front', 6, 1, '#c9ccd8')
    l1.cloth(ARM, RED, rows=(0, 1), faces=SIDES + ('top',))
    trousers(l2, YELLOW, rows=(0, 5))
    l2.col(LEG, 'right', 1, RED, 0, 5)
    l2.col(LEG, 'left', 2, RED, 0, 5)
    for x in range(4):
        l1.put(LEG, 'front', x, 11, '#3fa8d0')
        l1.put(LEG, 'right', x, 11, '#3fa8d0')
        l1.put(LEG, 'left', x, 11, '#3fa8d0')
        l1.put(LEG, 'back', x, 11, '#3fa8d0')
    l1.put(LEG, 'front', 1, 10, '#3fa8d0')
    l1.put(LEG, 'front', 2, 10, '#3fa8d0')
    l1.cloth(LEG, '#3fa8d0', faces=('bottom',))


def tourist(l1, l2):
    TEAL, BEIGE, ORANGE = '#2fc4d0', '#e8d6a8', '#ff8a2e'
    l1.cloth(HEAD, BEIGE, rows=(0, 2), faces=SIDES + ('top',))
    l1.row(HEAD, 2, ORANGE)
    shirt(l1, TEAL, sleeves=5)
    rr = random.Random(7)
    for part, faces in ((BODY, SIDES), (ARM, SIDES)):
        for face in faces:
            fx, fy, w, h = part[face]
            for _ in range(4 if part is BODY else 2):
                x, y = rr.randrange(w), rr.randrange(0, h if part is BODY else 4)
                col = rr.choice([ORANGE, '#ff6fd8', '#ffffff', '#ffe14d'])
                l1.put(part, face, x, y, col)
                if x + 1 < w:
                    l1.put(part, face, x + 1, y, dark(col, 0.15))
    for (x, y) in [(2, 0), (5, 0), (3, 1), (4, 1)]:
        l1.put(BODY, 'front', x, y, '#ffffff')
    l1.col(BODY, 'front', 4, dark(TEAL, 0.25), 2, 11)
    trousers(l2, BEIGE, rows=(0, 6))
    for face in ('right', 'left'):
        for y in (3, 4):
            for x in (1, 2):
                l2.put(LEG, face, x, y, dark(BEIGE, 0.2))
    l2.row(BODY, 7, '#8b5a2b')
    BROWN = '#8b5a2b'
    l1.row(LEG, 11, dark(BROWN, 0.3) if False else '#5a3a1e')
    l1.row(LEG, 10, BROWN)
    l1.cloth(LEG, '#5a3a1e', faces=('bottom',))
    for face in SIDES:
        l1.put(LEG, face, 0, 9, BROWN)
        l1.put(LEG, face, 3, 9, BROWN)
        l1.put(LEG, face, 1, 8, BROWN)
        l1.put(LEG, face, 2, 8, BROWN)


def santa(l1, l2):
    RED, FUR, BLACK = '#d62828', '#f6f6f6', '#1c1c24'
    l1.cloth(HEAD, RED)
    l1.window(1, 2, 6, 7)
    l1.trim(FUR, 1, 2, 6, 7)
    for x in range(8):
        l1.put(HEAD, 'front', x, 7, FUR)
    for (x, y) in [(5, 5), (6, 5), (5, 6), (6, 6)]:
        l1.put(HEAD, 'back', x, y, FUR)
    shirt(l1, RED)
    l1.col(BODY, 'front', 3, FUR)
    l1.col(BODY, 'front', 4, FUR)
    l1.row(BODY, 8, BLACK)
    for x in (3, 4):
        l1.put(BODY, 'front', x, 8, '#ffcf5a')
    l1.row(ARM, 10, FUR)
    l1.row(ARM, 11, FUR)
    for y in range(9, 12):
        l1.row(BODY, y, RED)
        l1.put(BODY, 'front', 3, y, FUR)
        l1.put(BODY, 'front', 4, y, FUR)
    l1.row(BODY, 11, FUR)
    trousers(l2, RED)
    boots(l1, BLACK, rows=(6, 11), sole='#0c0c12')
    l1.row(LEG, 6, FUR)
    l1.put(LEG, 'front', 1, 8, '#3a3a4a')


def elf(l1, l2):
    GREEN, RED, GOLD = '#2e9e4a', '#d62828', '#ffcf5a'
    l1.cloth(HEAD, GREEN)
    l1.window(1, 2, 6, 7)
    for x in range(0, 8):
        l1.put(HEAD, 'front', x, 1 if x % 2 else 0, RED)
    for y in range(2, 8):
        l1.put(HEAD, 'front', 0, y, RED if y % 2 else GREEN)
        l1.put(HEAD, 'front', 7, y, GREEN if y % 2 else RED)
    shirt(l1, GREEN)
    for x in range(8):
        l1.put(BODY, 'front', x, 0, RED)
        l1.put(BODY, 'front', x, 1, RED if x % 2 == 0 else GREEN)
    l1.row(BODY, 9, '#5a3a1e')
    l1.put(BODY, 'front', 3, 9, GOLD)
    l1.put(BODY, 'front', 4, 9, GOLD)
    for y in range(10, 12):
        for face in SIDES:
            w = BODY[face][2]
            for x in range(w):
                l1.put(BODY, face, x, y, RED if (x + y) % 2 == 0 else GREEN)
    l1.row(ARM, 10, RED)
    l1.row(ARM, 11, RED)
    trousers(l2, '#ffffff')
    for y in range(0, 12, 2):
        l2.row(LEG, y, RED)
    boots(l1, RED, rows=(9, 11), sole='#7a1414')
    l1.put(LEG, 'front', 1, 9, GOLD)
    l1.put(LEG, 'front', 2, 9, GOLD)


def bunny(l1, l2):
    WHITE, PINK = '#f6f2f2', '#f6b8d2'
    l1.cloth(HEAD, WHITE, fluffy=True)
    l1.window(1, 2, 6, 7)
    for (x, y) in [(1, 1), (2, 1), (1, 2), (2, 2), (1, 3), (2, 3), (5, 1), (6, 1), (5, 2), (6, 2), (5, 3), (6, 3)]:
        l1.put(HEAD, 'top', x, y, PINK)
    for x in (1, 2, 5, 6):
        l1.put(HEAD, 'front', x, 0, PINK)
    shirt(l1, WHITE, fluffy=True)
    for y in range(3, 11):
        for x in range(2, 6):
            if not ((y in (3, 10)) and x in (2, 5)):
                l1.put(BODY, 'front', x, y, PINK)
    l1.put(BODY, 'back', 3, 9, '#ffffff')
    l1.put(BODY, 'back', 4, 9, '#ffffff')
    l1.put(BODY, 'back', 3, 10, '#ffffff')
    l1.put(BODY, 'back', 4, 10, '#ffffff')
    trousers(l2, WHITE, fluffy=True)
    boots(l1, WHITE, rows=(9, 11), fluffy=True)
    for x in (0, 3):
        l1.put(LEG, 'front', x, 11, PINK)
    l1.put(LEG, 'front', 1, 10, PINK)
    l1.put(LEG, 'front', 2, 10, PINK)
    l1.cloth(LEG, PINK, faces=('bottom',))


def chick(l1, l2):
    YELLOW, DEEP, ORANGE = '#ffe14d', '#f2b82a', '#ff8a1f'
    l1.cloth(HEAD, YELLOW, fluffy=True)
    l1.window(1, 2, 6, 6)
    for x in range(8):
        l1.put(HEAD, 'front', x, 7, YELLOW)
    l1.put(HEAD, 'front', 3, 7, ORANGE)
    l1.put(HEAD, 'front', 4, 7, ORANGE)
    l1.put(HEAD, 'front', 3, 6, ORANGE)
    l1.put(HEAD, 'front', 4, 6, ORANGE)
    for (x, y) in [(3, 2), (4, 1), (4, 3), (5, 2)]:
        l1.put(HEAD, 'top', x, y, DEEP)
    shirt(l1, YELLOW, fluffy=True)
    for face, part in (('front', BODY), ('back', BODY), ('right', ARM), ('front', ARM), ('left', ARM), ('back', ARM)):
        w, h = part[face][2], part[face][3]
        for y in range(2, h, 3):
            for x in range(w):
                if (x + y // 3) % 2 == 0:
                    l1.put(part, face, x, y, DEEP)
    trousers(l2, YELLOW, fluffy=True)
    boots(l1, ORANGE, rows=(10, 11))
    for x in (0, 2):
        l1.put(LEG, 'front', x, 11, dark(ORANGE, 0.3))


def skeleton(l1, l2):
    BONE, BLACK, SHADOW = '#ecebe2', '#1e1e24', '#a8a89a'
    l1.cloth(HEAD, BONE)
    for (x, y) in [(1, 2), (2, 2), (1, 3), (2, 3), (5, 2), (6, 2), (5, 3), (6, 3), (3, 5), (4, 5)]:
        l1.put(HEAD, 'front', x, y, BLACK)
    for x in range(1, 7):
        l1.put(HEAD, 'front', x, 6, BLACK if x % 2 else BONE)
    l1.put(HEAD, 'front', 2, 1, SHADOW)
    l1.put(HEAD, 'top', 5, 3, SHADOW)
    l1.put(HEAD, 'top', 6, 4, SHADOW)
    l1.clear(HEAD, 'bottom')
    shirt(l1, BLACK)
    for y in range(1, 9, 2):
        for x in range(1, 7):
            l1.put(BODY, 'front', x, y, BONE)
            l1.put(BODY, 'back', x, y, SHADOW)
    l1.col(BODY, 'front', 3, BONE, 0, 10)
    l1.col(BODY, 'back', 3, BONE, 0, 11)
    l1.col(BODY, 'back', 4, BONE, 0, 11)
    for face in ('front', 'back', 'right', 'left'):
        l1.col(ARM, face, 1, BONE, 0, 11)
    l1.put(ARM, 'front', 2, 5, BONE)
    l1.put(ARM, 'front', 0, 5, BONE)
    trousers(l2, BLACK)
    for face in ('front', 'back', 'right', 'left'):
        l2.col(LEG, face, 1, BONE, 0, 11)
    for (x, y) in [(0, 5), (2, 5), (0, 6), (1, 6), (2, 6)]:
        l2.put(LEG, 'front', x, y, BONE)
    for (x, y) in [(2, 9), (5, 9), (3, 10), (4, 10)]:
        l2.put(BODY, 'front', x, y, BONE)
    boots(l1, BLACK, rows=(9, 11))
    for x in range(4):
        if x != 1:
            l1.put(LEG, 'front', x, 11, BONE)


def vampire(l1, l2):
    BLACK, RED, WHITE = '#1a1a22', '#8b0a14', '#f2f2f2'
    l1.cloth(HEAD, BLACK, rows=(0, 1), faces=('front',))
    l1.cloth(HEAD, BLACK, rows=(0, 4), faces=('right', 'left'))
    l1.cloth(HEAD, BLACK, rows=(0, 6), faces=('back', 'top'))
    l1.put(HEAD, 'front', 3, 2, BLACK)
    l1.put(HEAD, 'front', 4, 2, BLACK)
    for face in ('right', 'left', 'back', 'front', 'top'):
        fx, fy, w, h = HEAD[face]
        for x in range(w):
            if face != 'front' or x in (1, 6):
                for y in range(h):
                    p = l1.img.getpixel((fx + x, fy + y))
                    if p[3] and (x + y) % 5 == 0:
                        l1.img.putpixel((fx + x, fy + y), light(BLACK, 0.18) + (255,))
    shirt(l1, BLACK)
    for y in range(12):
        for x in range(2, 6):
            l1.put(BODY, 'front', x, y, WHITE if y < 10 else BLACK)
        l1.put(BODY, 'front', 1, y, RED)
        l1.put(BODY, 'front', 6, y, RED)
    l1.put(BODY, 'front', 3, 2, RED)
    l1.put(BODY, 'front', 4, 2, '#ff2a3a')
    l1.put(BODY, 'front', 3, 1, '#1a1a22')
    l1.put(BODY, 'front', 4, 1, '#1a1a22')
    l1.row(ARM, 11, WHITE)
    l1.row(BODY, 0, RED, faces=('right', 'left', 'back'))
    trousers(l2, BLACK)
    l2.col(LEG, 'right', 1, '#2a2a36')
    l2.col(LEG, 'left', 2, '#2a2a36')
    boots(l1, '#0e0e14', rows=(9, 11))
    l1.put(LEG, 'front', 1, 9, '#5a5a6e')


SUITS = {'plumber': plumber, 'ninja': ninja, 'astronaut': astronaut, 'frog': frog, 'lifeguard': lifeguard,
         'tourist': tourist, 'santa': santa, 'elf': elf, 'bunny': bunny, 'chick': chick, 'skeleton': skeleton,
         'vampire': vampire}


def layers(name):
    import zlib
    l1, l2 = Layer(zlib.crc32(name.encode())), Layer(zlib.crc32(name.encode()) >> 3)
    SUITS[name](l1, l2)
    return l1.img, l2.img


# ---------------------------------------------------------------- icons drawn from the textures

def face(tex, part, which, crop=None):
    x, y, w, h = part[which]
    im = tex.crop((x, y, x + w, y + h))
    if crop:
        im = im.crop((0, crop[0], w, crop[1] + 1))
    return im


def shade(im, f):
    out = im.copy()
    px = out.load()
    for x in range(out.width):
        for y in range(out.height):
            p = px[x, y]
            if p[3]:
                px[x, y] = dark(p[:3], f) + (p[3],) if f > 0 else light(p[:3], -f) + (p[3],)
    return out


def block(dst, tex, part, ox, oy, k, rows=None, mirror=False):
    """Draws one cube of a piece at ox, oy (top left of its front), k pixels per texel, with its right side and
    top shown in a 3/4 view."""
    front = face(tex, part, 'front', rows)
    side = face(tex, part, 'left' if not mirror else 'right', rows)
    top = face(tex, part, 'top')
    w, h = front.size
    d = side.size[0]
    fw, fh = w * k, h * k
    sw = max(1, round(d * k * 0.5))
    # front
    dst.alpha_composite(front.resize((fw, fh), Image.NEAREST), (ox, oy))
    # side, squashed and sheared up
    s = shade(side, 0.28).resize((sw, fh), Image.NEAREST)
    lift = round(d * k * 0.35)
    for x in range(sw):
        col = s.crop((x, 0, x + 1, fh))
        dy = -round(lift * (x + 1) / sw)
        dst.alpha_composite(col, (ox + fw + x, oy + dy))
    if rows is None or rows[0] == 0:
        t = shade(top, -0.12).resize((fw, max(1, lift)), Image.NEAREST)
        for y in range(t.height):
            rowim = t.crop((0, y, fw, y + 1))
            shift = round(sw * (t.height - y) / t.height)
            dst.alpha_composite(rowim, (ox + shift, oy - t.height + y))


def outline(im):
    import numpy as np
    arr = np.array(im).astype(np.int32)
    solid = arr[..., 3] > 0
    out = arr.copy()
    H, W = solid.shape
    for y in range(H):
        for x in range(W):
            if solid[y, x]:
                continue
            near = [(y + dy, x + dx) for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0))
                    if 0 <= y + dy < H and 0 <= x + dx < W and solid[y + dy, x + dx]]
            if near:
                c = np.mean([arr[p][:3] for p in near], axis=0)
                out[y, x] = list(dark(tuple(int(v) for v in c), 0.62)) + [255]
    return Image.fromarray(out.clip(0, 255).astype('uint8'), 'RGBA')


def icon(name, piece):
    l1, l2 = layers(name)
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    if piece == 'helmet':
        # the head inside, so open hoods aren't empty
        skin = Layer(1)
        skin.cloth(HEAD, '#e8b088')
        for (x, y) in [(2, 4), (5, 4)]:
            skin.put(HEAD, 'front', x, y, '#3a2a52')
        under = skin.img
        block(im, under, HEAD, 6, 10, 2)
        block(im, l1, HEAD, 5, 9, 2, mirror=False)
        im = im.resize((32, 32))
    elif piece == 'chestplate':
        block(im, l1, ARM, 2, 8, 1)
        block(im, l1, BODY, 6, 8, 2)
        block(im, l1, ARM, 22, 8, 1)
        im = im.crop((0, 0, 32, 32))
        im = im.transform((32, 32), Image.AFFINE, (1, 0, 0, 0, 1, 0))
        big = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
        bb = im.getbbox()
        if bb:
            part = im.crop(bb)
            big.alpha_composite(part, ((32 - part.width) // 2, (32 - part.height) // 2))
        im = big
    elif piece == 'leggings':
        tmp = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
        block(tmp, l2, BODY, 8, 4, 2, rows=(7, 11))
        block(tmp, l2, LEG, 8, 14, 2)
        block(tmp, l2, LEG, 17, 14, 2)
        bb = tmp.getbbox()
        part = tmp.crop(bb)
        sc = min(30 / part.height, 30 / part.width, 1.0)
        part = part.resize((max(1, round(part.width * sc)), max(1, round(part.height * sc))), Image.NEAREST)
        im.alpha_composite(part, ((32 - part.width) // 2, (32 - part.height) // 2))
    else:  # boots
        tmp = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
        block(tmp, l1, LEG, 4, 20, 3, rows=(5, 11))
        block(tmp, l1, LEG, 18, 26, 3, rows=(5, 11))
        bb = tmp.getbbox()
        part = tmp.crop(bb)
        sc = min(30 / part.height, 30 / part.width, 1.0)
        part = part.resize((max(1, round(part.width * sc)), max(1, round(part.height * sc))), Image.NEAREST)
        im.alpha_composite(part, ((32 - part.width) // 2, (32 - part.height) // 2))
    return outline(im)


def doll(name):
    """The whole suit on a little front-facing figure (the set's icon)."""
    l1, l2 = layers(name)
    skin = Layer(2)
    skin.cloth(HEAD, '#e8b088')
    skin.cloth(BODY, '#e8b088')
    skin.cloth(ARM, '#e8b088')
    skin.cloth(LEG, '#e8b088')
    for (x, y) in [(2, 4), (5, 4)]:
        skin.put(HEAD, 'front', x, y, '#3a2a52')
    fig = Image.new('RGBA', (16, 32), (0, 0, 0, 0))
    for tex in (skin.img, l2, l1):
        fig.alpha_composite(face(tex, HEAD, 'front'), (4, 0))
        fig.alpha_composite(face(tex, BODY, 'front'), (4, 8))
        fig.alpha_composite(face(tex, ARM, 'front'), (0, 8))
        fig.alpha_composite(face(tex, ARM, 'front').transpose(Image.FLIP_LEFT_RIGHT), (12, 8))
        fig.alpha_composite(face(tex, LEG, 'front'), (4, 20))
        fig.alpha_composite(face(tex, LEG, 'front').transpose(Image.FLIP_LEFT_RIGHT), (8, 20))
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    small = fig.resize((15, 30), Image.NEAREST)
    im.alpha_composite(small, (8, 1))
    # a little gold star: the full set
    for (x, y) in [(27, 2), (26, 3), (27, 3), (28, 3), (27, 4), (25, 3), (29, 3), (27, 1), (27, 5)]:
        im.putpixel((x, y), (255, 214, 90, 255))
    return outline(im)


def build(out_root):
    """Equipment assets, armour textures and icons for every suit."""
    for d in ('equipment', 'textures/entity/equipment/humanoid', 'textures/entity/equipment/humanoid_leggings'):
        os.makedirs(f'{out_root}/assets/fractal/{d}', exist_ok=True)
    count = 0
    for name in SUITS:
        l1, l2 = layers(name)
        l1.save(f'{out_root}/assets/fractal/textures/entity/equipment/humanoid/suit_{name}.png')
        l2.save(f'{out_root}/assets/fractal/textures/entity/equipment/humanoid_leggings/suit_{name}.png')
        json.dump({"layers": {"humanoid": [{"texture": f"fractal:suit_{name}"}],
                              "humanoid_leggings": [{"texture": f"fractal:suit_{name}"}]}},
                  open(f'{out_root}/assets/fractal/equipment/suit_{name}.json', 'w'))
        for piece in ('helmet', 'chestplate', 'leggings', 'boots', 'set'):
            path = f'suit/{name}/{piece}'
            for d in (f'textures/item/suit/{name}', f'models/item/suit/{name}', f'items/suit/{name}'):
                os.makedirs(f'{out_root}/assets/fractal/{d}', exist_ok=True)
            (doll(name) if piece == 'set' else icon(name, piece)).save(f'{out_root}/assets/fractal/textures/item/{path}.png')
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"fractal:item/{path}"}},
                      open(f'{out_root}/assets/fractal/models/item/{path}.json', 'w'))
            json.dump({"model": {"type": "minecraft:model", "model": f"fractal:item/{path}"}},
                      open(f'{out_root}/assets/fractal/items/{path}.json', 'w'))
            count += 1
    return count
