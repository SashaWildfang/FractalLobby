"""Piñata icon options (centred in the slot), plus the baseball bat sprite you whack it with."""
import math
from PIL import Image
from kit import Canvas, light, dark

INK = '#1a1b26'
STRIPES = ['#ff5ab4', '#ffcf3a', '#5be37c', '#3fb8ff', '#ff8a1f']


def centre(im):
    b = im.getbbox()
    out = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    out.alpha_composite(im, (round(16 - (b[0] + b[2]) / 2), round(16 - (b[1] + b[3]) / 2)))
    return out


def donkey(c, ox, oy, s=1.0):
    """A chunky striped piñata donkey facing right, its top-left at (ox, oy)."""
    P = lambda x, y: (ox + x * s, oy + y * s)
    for i, col in enumerate(STRIPES[:4]):                               # the body, in frilly stripes
        c.shape('rect', (*P(0, 8 + i * 2.5), *P(15, 10.5 + i * 2.5)), col)
        for x in range(0, 15, 2):                                       # the frill along each stripe
            c.shape('rect', (*P(x, 10 + i * 2.5), *P(x + 1, 10.5 + i * 2.5)), dark(col, 0.25))
    c.shape('rect', (*P(11, 1), *P(16, 9)), STRIPES[0])                 # neck
    c.shape('rect', (*P(13, 2.5), *P(21, 7.5)), STRIPES[1])             # head
    c.shape('rect', (*P(18, 4), *P(19.2, 5.2)), INK)                    # eye
    c.shape('poly', [P(12, 1), P(12.8, -2.5), P(14.2, 1)], STRIPES[2])  # ears
    c.shape('poly', [P(14.4, 1), P(15.4, -2), P(16.4, 1)], STRIPES[3])
    for i, x in enumerate((1, 4.5, 9, 12.5)):                           # legs
        c.shape('rect', (*P(x, 18), *P(x + 2, 22.5)), STRIPES[(i + 3) % 5])
    for k in range(3):                                                  # a streamer tail
        c.line([P(0, 9 + k * 2.5), P(-3, 10.5 + k * 2.5)], STRIPES[(k + 2) % 5], 0.9 * s)


def opt1():
    """The classic: a striped donkey piñata hanging on its rope."""
    c = Canvas()
    c.line([(13, 0), (13, 16)], '#e8e0cc', 1.0)                         # the rope, tied round its middle
    donkey(c, 6, 8)
    return centre(c.done())


def opt2():
    """The star piñata: a bright ball with seven cone points and tassels, hanging on a rope."""
    c = Canvas()
    cx, cy = 16, 17
    c.line([(16, 0), (16, 8)], '#e8e0cc', 1.0)
    for k in range(7):
        a = math.radians(-90 + k * 360 / 7)
        tip = (cx + math.cos(a) * 14, cy + math.sin(a) * 14)
        side = math.radians(90)
        l = (cx + math.cos(a - 0.42) * 6, cy + math.sin(a - 0.42) * 6)
        r = (cx + math.cos(a + 0.42) * 6, cy + math.sin(a + 0.42) * 6)
        col = STRIPES[k % 5]
        c.shape('poly', [l, tip, r], col)
        c.shape('poly', [l, tip, ((l[0] + r[0]) / 2, (l[1] + r[1]) / 2)], light(col, 0.3))
        c.shape('rect', (tip[0] - 0.8, tip[1] - 0.8, tip[0] + 0.8, tip[1] + 0.8), '#ffffff')   # a tassel on each point
    c.ball(cx, cy, 6.8, '#ff5ab4')
    for i, col in enumerate(STRIPES[1:4]):
        c.shape('rect', (cx - 6 + i * 0.5, cy - 3 + i * 2.6, cx + 6 - i * 0.5, cy - 1.8 + i * 2.6), col)
    return centre(c.done())


def opt3():
    """Whack time: the donkey piñata with a baseball bat swinging into it and a little burst of candy (Sasha picked this)."""
    c = Canvas()
    donkey(c, 9, 7, 0.92)
    c.shape('rect', (25, 10, 27, 12), INK)                              # its eye, nice and clear at this size
    # the bat, coming in from the bottom left
    c.line([(1.5, 30), (9.5, 17)], '#7a4a26', 2.0)
    c.line([(8, 19.5), (12.5, 11.5)], '#c98a4a', 3.6)
    c.line([(8.4, 18.4), (12, 12.2)], '#e2ad6a', 1.2)
    for (x, y, col) in ((5, 6, '#ff5ab4'), (8, 3, '#ffcf3a'), (3, 10, '#3fb8ff')):
        c.shape('rect', (x, y, x + 1.6, y + 1.6), col)
    return centre(c.done())


OPTIONS = [opt1, opt2, opt3]


def bat():
    """The baseball bat you get to whack the piñata with (drawn handle bottom-left, barrel top-right)."""
    c = Canvas()
    c.line([(4, 28), (13, 19)], '#6b3e1e', 2.4)                        # handle, with tape
    for k in range(3):
        c.line([(5 + k * 2, 27 - k * 2), (5.8 + k * 2, 26.2 - k * 2)], '#f0f0f6', 2.6)
    c.shape('ellipse', (2, 27, 6, 31), '#6b3e1e')                       # the knob
    c.line([(12, 20), (26, 6)], '#c98a4a', 5.2)                         # the barrel, getting fatter
    c.line([(18, 14), (26.5, 5.5)], '#c98a4a', 6.4)
    c.line([(13, 18.5), (25.5, 6)], '#e2ad6a', 1.6)
    c.shape('ellipse', (23.5, 2.5, 29.5, 8.5), '#d69a58')               # its rounded end
    return centre(c.done())
