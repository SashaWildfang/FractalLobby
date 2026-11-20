"""Railgun icon options (drawn pointing up-right in the gun frame; the menus show it flipped like the other guns),
plus the beam ring sprite for its new effect."""
import math
from PIL import Image
from kit import Canvas, light, dark
from icons import Gun

INK = '#1a1b26'


def centre(im):
    b = im.getbbox()
    out = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    out.alpha_composite(im, (round(16 - (b[0] + b[2]) / 2), round(16 - (b[1] + b[3]) / 2)))
    return out


def opt1():
    """Sci-fi white: a sleek white rifle body, two long rails with glowing cyan coils between them, a dark grip."""
    c = Canvas(); g = Gun(c, ox=3, oy=27)
    g.poly([(4, -1), (9, -1), (7, -9), (3, -9)], '#2c2a40')               # grip
    g.poly([(0, -3.8), (14, -3.8), (16, 0), (14, 4.2), (0, 4.2)], '#e8ecf6')   # body
    g.rect(0, 2.2, 14, 4.2, '#ffffff')
    g.rect(0, -3.8, 13, -2.4, '#b8bfd2')
    g.rect(14, -3.2, 29, -1.8, '#9aa2b8')                                 # the rails
    g.rect(14, 1.8, 29, 3.2, '#9aa2b8')
    for u in (16.5, 20, 23.5, 27):                                        # glowing coils
        g.rect(u, -1.8, u + 1.6, 1.8, '#3fe0ff')
    g.rect(5, -1, 11, 1, '#3fe0ff')                                       # a glowing charge window
    return centre(c.done())


def opt2():
    """Fractal purple: a chunky purple and gold railgun with a glowing violet energy core and twin prongs."""
    c = Canvas(); g = Gun(c, ox=3, oy=27)
    g.poly([(4, -1), (9, -1), (7, -9), (3, -9)], '#2c2a40')
    g.rect(0, -4.4, 15, 4.4, '#6b3fb0')
    g.rect(0, 2.4, 15, 4.4, light('#6b3fb0', 0.3))
    g.rect(0, -4.4, 15, -3, dark('#6b3fb0', 0.3))
    g.rect(13, -4.8, 15, 4.8, '#ffd27f')                                  # gold collar
    g.oval(7, 0, 2.6, 2.6, '#e8b8ff')                                     # the energy core
    g.oval(7, 0, 1.4, 1.4, '#ffffff')
    g.poly([(15, -4), (29, -2.6), (29, -1.2), (15, -1.6)], '#c9ccd8')    # twin prongs
    g.poly([(15, 4), (29, 2.6), (29, 1.2), (15, 1.6)], '#c9ccd8')
    g.rect(16, -0.5, 30, 0.5, '#d99bff')                                  # a crackle of charge between them
    return centre(c.done())


def opt3():
    """Mid-shot: a dark rail rifle firing, a bright cyan beam leaving the muzzle with a ring round it."""
    c = Canvas(); g = Gun(c, ox=2, oy=29, )
    g.poly([(4, -1), (9, -1), (7, -9), (3, -9)], '#2c2a40')
    g.rect(0, -3.4, 13, 3.4, '#3a3d56')
    g.rect(0, 1.8, 13, 3.4, '#5a6078')
    g.rect(13, -2.6, 19, -1.4, '#9aa2b8')
    g.rect(13, 1.4, 19, 2.6, '#9aa2b8')
    g.rect(4, -0.8, 10, 0.8, '#3fe0ff')
    g.rect(18, -1.4, 33, 1.4, '#3fe0ff')                                  # the beam
    g.rect(18, -0.5, 33, 0.5, '#ffffff')
    g.oval(21, 0, 1.0, 3.4, '#bff6ff')                                    # a ring round it
    g.oval(21, 0, 0.5, 2.4, '#3fe0ff')
    return centre(c.done())


OPTIONS = [opt1, opt2, opt3]
