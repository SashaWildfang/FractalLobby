"""Mob gun icon options (drawn pointing up-right in the gun frame; the menus show it flipped, like the paintball gun)."""
import math
from PIL import Image
from kit import Canvas, light, dark
from icons import Gun

PINK, PINK_D, SNOUT = '#f4a3b8', '#d9708e', '#e77f9c'
INK = '#1a1b26'


def centre(im):
    b = im.getbbox()
    out = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    out.alpha_composite(im, (round(16 - (b[0] + b[2]) / 2), round(16 - (b[1] + b[3]) / 2)))
    return out


def opt1():
    """The Pig Blaster: a chunky pink blaster whose muzzle is a big pig snout, a little chick riding in the hopper."""
    c = Canvas(); g = Gun(c, ox=4, oy=26)
    g.poly([(5, -1), (10, -1), (8, -9), (4, -9)], '#3a2f4a')                 # grip
    g.tube(1, 19, 4.2, PINK)
    g.rect(1, -4.2, 3, 4.2, PINK_D)                                         # back cap
    g.oval(21, 0, 3.0, 6.0, '#c45a7a')                                      # the snout
    g.oval(21.8, 0, 2.2, 5.0, '#f58fab')
    g.oval(22.2, 2.0, 1.1, 1.4, '#5a1a30')                                  # nostrils
    g.oval(22.2, -2.0, 1.1, 1.4, '#5a1a30')
    g.rect(7, 4.2, 14, 6.2, '#8a5a3a')                                      # hopper
    x, y = g.p(10.5, 8.6)                                                   # a chick peeking out
    c.ball(x, y, 2.9, '#ffd84a')
    c.shape('rect', (x - 1.4, y - 1.2, x - 0.4, y - 0.2), INK)
    c.shape('poly', [(x + 0.6, y - 0.6), (x + 3.0, y), (x + 0.6, y + 0.6)], '#ff9a2a')
    return centre(c.done())


def opt2():
    """The Spawn Egg Launcher: a green farm tube gun with a spotted pig spawn egg loaded at the muzzle."""
    c = Canvas(); g = Gun(c, ox=3, oy=27)
    g.poly([(5, -1), (10, -1), (8, -9), (4, -9)], '#3a2f4a')
    g.poly([(13, -1), (16, -1), (15, -6), (12, -6)], '#3a2f4a')
    g.tube(0, 20, 3.8, '#5cb848')
    g.rect(17.5, -4.6, 20.5, 4.6, '#c9a24a')                                 # brass muzzle ring
    g.rect(0, -4.4, 1.6, 4.4, '#c9a24a')
    g.rect(6, 3.8, 11, 5.4, '#3d7a2e')                                      # sight
    g.oval(24, 0, 4.6, 3.6, '#f0a0b4')                                      # the egg
    g.oval(24.6, 1.0, 2.6, 1.6, light('#f0a0b4', 0.4))
    for (u, v) in ((22.5, -1.4), (25.8, -0.6), (24.4, 1.9), (27, 1.4)):    # its spots
        g.oval(u, v, 0.8, 0.8, '#d9608a')
    return centre(c.done())


def opt3():
    """The Barnyard Blunderbuss: a red barn-wood blunderbuss with white trim and a flared bell, a baby sheep popping out."""
    c = Canvas(); g = Gun(c, ox=3.5, oy=26.5)
    g.poly([(4, -1), (9, -1), (7, -9), (3, -9)], '#6b3e1e')                  # wooden grip
    g.rect(0, -3.2, 15, 3.2, '#c8322e')                                     # barn-red barrel
    g.rect(0, 1.2, 15, 2.6, light('#c8322e', 0.3))
    g.rect(0, -3.2, 15, -1.6, dark('#c8322e', 0.25))
    for u in (3, 9):                                                        # white barn trim
        g.rect(u, -3.2, u + 1.4, 3.2, '#f4f4fa')
    g.poly([(15, -3.2), (21, -5.8), (21, 5.8), (15, 3.2)], '#c8322e')       # the bell
    g.poly([(15, 1.4), (21, 3.4), (21, 5.8), (15, 3.2)], light('#c8322e', 0.3))
    g.rect(20.6, -5.8, 22, 5.8, '#f4f4fa')
    x, y = g.p(25.5, 0)                                                     # a fluffy baby sheep
    c.ball(x, y, 4.2, '#f4f4f8', shine=False)
    for a in range(0, 360, 60):
        c.ball(x + math.cos(math.radians(a)) * 3.3, y + math.sin(math.radians(a)) * 3.3, 1.8, '#f4f4f8', shine=False)
    c.shape('rrect', (x - 2.4, y - 1.4, x + 2.8, y + 3.2, 1.6), '#e9b9a0')
    c.shape('rect', (x - 1.4, y, x - 0.2, y + 1.2), INK)
    c.shape('rect', (x + 0.8, y, x + 2.0, y + 1.2), INK)
    c.shape('rect', (x - 0.2, y + 2.0, x + 0.8, y + 2.6), '#c08070')
    return centre(c.done())


OPTIONS = [opt1, opt2, opt3]
