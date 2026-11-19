"""Tetherball icon options (centred in the slot)."""
import math
from PIL import Image
from kit import Canvas, light, dark

YEL, POLE, BASE, ROPE = '#ffd23a', '#c9ccd8', '#5a6078', '#f0f0f6'


def centre(im):
    b = im.getbbox()
    out = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    out.alpha_composite(im, (round(16 - (b[0] + b[2]) / 2), round(16 - (b[1] + b[3]) / 2)))
    return out


def seam_ball(c, x, y, r):
    c.ball(x, y, r, YEL)
    c.shape('arc', ((x - r * 0.35, y - r, x + r * 1.7, y + r), 110, 250), '#ffffff', width=0.9)


def tb1():
    """Mid-swing: the pole, the rope pulled out tight, the ball flying round with speed lines."""
    c = Canvas()
    c.shape('rect', (10, 3, 13, 28), POLE)
    c.shape('rect', (10, 3, 11, 28), '#ffffff')
    c.shape('rrect', (6, 27, 17, 30, 1), BASE)
    c.shape('rect', (10, 2, 13, 3.5), '#ffcf5a')
    c.line([(12.5, 4.5), (22.5, 12.5)], ROPE, 0.9)
    seam_ball(c, 24, 15, 5)
    return centre(c.done())


def tb2():
    """Wound up: rope coiled round the top of the pole, the ball tucked in close (Sasha picked this one)."""
    c = Canvas()
    c.shape('rect', (14.5, 3, 17.5, 28), POLE)
    c.shape('rect', (14.5, 3, 15.5, 28), '#ffffff')
    c.shape('rrect', (10, 27, 22, 30, 1), BASE)
    for y in (6, 8.5, 11):                                          # the coils
        c.line([(13.2, y + 1), (18.8, y - 0.6)], ROPE, 1.0)
    c.line([(18.5, 11.5), (20, 14)], ROPE, 0.9)
    seam_ball(c, 22, 17.5, 4.6)
    return centre(c.done())


def tb3():
    """Close up: a big yellow tetherball on its rope, swinging, with a curved motion trail."""
    c = Canvas()
    c.line([(13, 1), (16.5, 11.5)], ROPE, 1.1)
    seam_ball(c, 18, 19, 8)
    c.ball(6, 15, 4, YEL)                                            # a fading echo of where it just was
    c.erase('ellipse', (1, 10, 6.5, 20))
    return centre(c.done())


OPTIONS = [tb1, tb2, tb3]
