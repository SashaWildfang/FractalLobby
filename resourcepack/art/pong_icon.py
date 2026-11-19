"""Pong icon options and the new Diamond Shower icon (all centred in the slot)."""
from PIL import Image
from kit import Canvas, light, dark

INK = '#1a1b26'


def centre(im):
    b = im.getbbox()
    out = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    out.alpha_composite(im, (round(16 - (b[0] + b[2]) / 2), round(16 - (b[1] + b[3]) / 2)))
    return out


def pong1():
    """Arcade screen: a little monitor with two white paddles, a square ball and the dashed middle line."""
    c = Canvas()
    c.shape('rrect', (2, 5, 30, 27, 3), '#4a4e6a')                       # bezel
    c.shape('rrect', (4, 7, 28, 25, 1.5), '#10121c')                      # screen
    for y in range(8, 25, 4):
        c.shape('rect', (15.5, y, 16.5, y + 2), '#5a6078')
    c.shape('rect', (6, 10, 8, 17), '#ffffff')
    c.shape('rect', (24, 15, 26, 22), '#ffffff')
    c.shape('rect', (19.5, 10.5, 22, 13), '#ffffff')
    return centre(c.done())


def pong2():
    """Neon Pong: a cyan paddle and a pink paddle, a glowing square ball zipping between them."""
    c = Canvas()
    c.glow(16, 16, 8, '#ffffff', opacity=0.25)
    c.shape('rrect', (4, 6, 8, 19, 1.5), '#3fd8ff')
    c.shape('rect', (5, 7, 6, 17), light('#3fd8ff', 0.6))
    c.shape('rrect', (24, 13, 28, 26, 1.5), '#ff5ab4')
    c.shape('rect', (25, 14, 26, 24), light('#ff5ab4', 0.6))
    c.shape('rect', (10, 18, 11.5, 19.5), '#ffffff', opacity=0.35)        # its trail, in steps
    c.shape('rect', (12.5, 16.5, 14, 18), '#ffffff', opacity=0.6)
    c.shape('rect', (16, 12, 20, 16), '#ffffff')
    return centre(c.done())


def pong3():
    """Table tennis: a red paddle with a wooden handle, and a white ball."""
    c = Canvas()
    c.shape('rrect', (17, 21, 21.5, 30, 1.5), '#b07a45')                  # handle
    c.shape('rect', (17, 21, 21.5, 22.5), dark('#b07a45', 0.3))
    c.shape('ellipse', (8, 3, 30, 23), '#e0443a')                         # blade
    c.shape('ellipse', (10, 5, 26, 18), light('#e0443a', 0.18))
    c.ball(7, 24, 4.2, '#f8f8fc')
    return centre(c.done())


PONG = [pong1, pong2, pong3]


def diamond_shower():
    """A fluffy cloud raining three game-style diamonds, with a couple of glints."""
    c = Canvas()
    for (x, y, r) in [(9, 9, 5), (15, 6.5, 6), (22, 8.5, 5.5), (16, 10, 5)]:
        c.shape('ellipse', (x - r, y - r, x + r, y + r), '#f4f6ff')
    c.shape('rect', (5, 11, 27.5, 14.5), '#dfe5f6')
    c.shape('rrect', (4, 9, 28, 14.5, 3), '#f4f6ff')
    c.shape('rect', (6, 13, 26.5, 14.5), '#c9d2ea')

    def gem(x, y, s):
        top = [(x - 3 * s, y), (x - 1.5 * s, y - 2 * s), (x + 1.5 * s, y - 2 * s), (x + 3 * s, y)]
        c.shape('poly', top + [(x, y + 4 * s)], '#4fe0da')
        c.shape('poly', [(x - 1.5 * s, y - 2 * s), (x + 1.5 * s, y - 2 * s), (x + 1 * s, y), (x - 1 * s, y)], '#c8fffa')
        c.shape('poly', [(x + 3 * s, y), (x + 1 * s, y), (x, y + 4 * s)], '#24a6b0')
    gem(9, 20, 1.0)
    gem(18, 25, 1.15)
    gem(25, 19.5, 0.9)
    c.star(13, 28, 1.8, '#ffffff', inner=0.3)
    c.star(28, 26, 1.5, '#ffffff', inner=0.3)
    return centre(c.done())
