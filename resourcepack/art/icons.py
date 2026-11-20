"""
The HD (32x32) icons for collectibles: gadgets, cloaks, gestures, season tabs, the Collectibles join item, and the
small textures gadgets use in the world (cookie, paintball, hook). Each function paints one icon with kit.Canvas.
"""
import math
from kit import Canvas, light, dark, mix

GOLD, PURPLE, PINK, BLUE, GREEN, RED, ORANGE, WHITE = '#ffcf5a', '#9b5cff', '#ff6fd8', '#5b9dff', '#4fd36b', '#e8443a', '#ff9a2e', '#f4f4fa'
CONFETTI = [PINK, '#ffe14d', BLUE, GREEN, ORANGE, PURPLE]


# ---------------------------------------------------------------- gadgets

def party_popper():
    c = Canvas()
    # streamers and confetti bursting out of the top
    c.line([(15, 13), (17, 8), (21, 7), (22, 3)], PINK, 1.3)
    c.line([(16, 14), (21, 12), (24, 13), (28, 9)], '#ffe14d', 1.3)
    c.line([(14, 12), (12, 7), (14, 4), (11, 1.5)], BLUE, 1.3)
    for (x, y, col) in [(26, 4, GREEN), (19, 2, ORANGE), (29, 15, PINK), (8, 5, '#ffe14d'), (24, 17, BLUE)]:
        c.shape('rect', (x - 1, y - 1, x + 1, y + 1), col)
    c.star(29, 4, 2.4, WHITE)
    c.star(9, 10, 1.8, '#ffe14d')
    # the cone, striped
    cone = [(4, 29), (11.5, 11), (19.5, 18.5)]
    c.shape('poly', cone, ('lin', light(PURPLE, 0.25), dark(PURPLE, 0.35), 30))
    m = c.mask('poly', cone)
    for k in range(4):
        t0, t1 = 0.2 + k * 0.2, 0.28 + k * 0.2
        band = [(4 + (11.5 - 4) * (1 - t0), 29 - 18 * (1 - t0)), (4 + (19.5 - 4) * (1 - t0), 29 - 10.5 * (1 - t0)),
                (4 + (19.5 - 4) * (1 - t1), 29 - 10.5 * (1 - t1)), (4 + (11.5 - 4) * (1 - t1), 29 - 18 * (1 - t1))]
        bm = c.mask('poly', band)
        from PIL import ImageChops
        c.paint(ImageChops.multiply(bm, m), '#ffd84d')
    # the open rim
    c.shape('ellipse', (10, 10.5, 21, 19.5), ('lin', '#fff4c0', '#d9a43a', 45))
    c.shape('ellipse', (11.6, 12, 19.4, 18), '#3a2060')
    return c.done()


def paintball_gun():
    c = Canvas()
    # splat at the muzzle
    for (x, y, r) in [(28, 9, 2.6), (30.5, 12, 1.2), (25.5, 5.5, 1.1), (29.5, 5.8, 1)]:
        c.shape('ellipse', (x - r, y - r, x + r, y + r), ('rad', '#ffa8ec', PINK, 0.35, 0.3, 1))
    # barrel
    c.solid('rrect', (12, 10, 26, 14.5, 2), '#c9ccd8', angle=90)
    c.shape('rect', (24, 10.3, 26, 14.2), '#7e8296')
    # body
    c.solid('rrect', (5, 12, 21, 19.5, 3), '#6b4bd6', angle=80, strength=0.4)
    c.shape('rect', (6, 15.2, 20, 16.2), '#ffd84d')
    # hopper ball of paint on top
    c.ball(10.5, 8.5, 5, '#3cc8ff')
    c.shape('rect', (8, 11.5, 13, 13.5), '#4a3a9a')
    # grip and trigger
    c.solid('poly', [(7, 18), (12, 18), (10, 29), (5, 29)], '#2c2a40', angle=80)
    c.line([(13, 19), (14.5, 22.5)], '#c9ccd8', 1.1)
    c.shape('rect', (5.5, 26, 10.5, 27), '#4a4766')
    return c.done()


def bubble_wand():
    c = Canvas()
    # bubbles
    for (x, y, r) in [(22, 8, 5.5), (10, 6, 3.4), (27, 19, 2.6), (14.5, 14, 2)]:
        c.shape('ellipse', (x - r, y - r, x + r, y + r), ('rad', (190, 240, 255), (120, 170, 255), 0.4, 0.4, 1.0), opacity=0.55)
        c.shape('arc', ((x - r, y - r, x + r, y + r), 200, 340), (255, 255, 255), width=0.8)
        c.shape('arc', ((x - r, y - r, x + r, y + r), 20, 160), (255, 160, 230), width=0.6)
        c.shape('ellipse', (x - r * 0.55, y - r * 0.6, x - r * 0.15, y - r * 0.2), (255, 255, 255))
    # handle
    c.line([(4, 29), (13, 20)], ('lin', '#ff9ae6', '#c2389f', 45), 2.4)
    c.line([(4.6, 28.2), (7, 25.8)], '#ffe14d', 2.6)
    # ring
    c.shape('arc', ((11, 12.5, 19.5, 21), 0, 360), ('lin', '#fff2a0', '#e2a93a', 45), width=1.8)
    return c.done()


def firework_launcher():
    c = Canvas()
    # burst
    for i in range(10):
        a = math.radians(i * 36 + 10)
        col = CONFETTI[i % len(CONFETTI)]
        c.line([(23 + math.cos(a) * 2.5, 8 + math.sin(a) * 2.5), (23 + math.cos(a) * 6.5, 8 + math.sin(a) * 6.5)], col, 1.1)
    c.star(23, 8, 2.6, WHITE)
    # rocket
    c.solid('poly', [(4, 28), (8, 24), (19, 13), (15, 17), (8, 24)], '#888888', angle=45)
    body = [(6, 21), (16, 11), (21, 16), (11, 26)]
    c.shape('poly', body, ('lin', '#ff6a5c', '#a3201a', 30))
    c.shape('poly', [(9.5, 17.5), (12.5, 14.5), (17.5, 19.5), (14.5, 22.5)], WHITE)
    c.shape('poly', [(16, 11), (21, 16), (22.5, 9.5)], ('lin', '#ffe14d', '#d98f1a', 30))
    # fins and sparks
    c.shape('poly', [(6, 21), (2.5, 21.5), (5, 24)], '#3a5bd6')
    c.shape('poly', [(11, 26), (10.5, 29.5), (8, 27)], '#3a5bd6')
    for (x, y) in [(3, 30), (5.5, 30.5), (2, 27.5)]:
        c.star(x, y, 1.4, '#ffd84d')
    return c.done()


def cookie(size=32):
    c = Canvas(size, size)
    k = size / 32
    c.shape('ellipse', (3 * k, 3 * k, 29 * k, 29 * k), ('rad', '#e8b06a', '#9a5a26', 0.35, 0.3, 0.9))
    # baked edge and crumbs
    c.shape('arc', ((3 * k, 3 * k, 29 * k, 29 * k), 0, 360), '#7a4418', width=1.4 * k)
    for (x, y, r) in [(11, 10, 2.4), (20, 9, 2), (9, 19, 2.2), (17, 16, 2.6), (23, 20, 2.2), (14, 24, 2), (22, 13, 1.4), (7, 14, 1.3)]:
        c.shape('ellipse', ((x - r) * k, (y - r) * k, (x + r) * k, (y + r) * k), ('rad', '#6b3a1c', '#2e1408', 0.3, 0.3, 1))
        c.shape('ellipse', ((x - r * 0.6) * k, (y - r * 0.7) * k, (x - r * 0.1) * k, (y - r * 0.25) * k), '#8a5a3a')
    for (x, y) in [(15, 7), (25, 15), (8, 23), (19, 26)]:
        c.shape('ellipse', ((x - 0.8) * k, (y - 0.8) * k, (x + 0.8) * k, (y + 0.8) * k), '#f2c98a')
    return c.done()


def cookie_fountain():
    c = Canvas()
    # cookies flying out
    for (x, y, r) in [(9, 6, 3.2), (23, 5, 3.4), (16, 2.5, 2.4)]:
        c.shape('ellipse', (x - r, y - r, x + r, y + r), ('rad', '#e8b06a', '#9a5a26', 0.35, 0.3, 0.9))
        c.shape('ellipse', (x - 0.9, y - 0.6, x + 0.3, y + 0.6), '#3a1a0a')
        c.shape('ellipse', (x + 0.5, y + 0.4, x + 1.5, y + 1.4), '#3a1a0a')
    # spout
    c.solid('rect', (14, 10, 18, 19), '#b8bcc8', angle=0)
    c.shape('ellipse', (12, 8.5, 20, 12), '#d8dae4')
    c.shape('ellipse', (13.5, 9.3, 18.5, 11.2), '#9a5a26')
    # basin
    c.solid('poly', [(4, 19), (28, 19), (25, 27), (7, 27)], '#a6abbb', angle=90, strength=0.35)
    c.shape('ellipse', (3, 16.5, 29, 22), '#d6d9e4')
    c.shape('ellipse', (5, 17.6, 27, 21), ('lin', '#c98a4b', '#7a4418', 90))
    for x in (9, 15, 21):
        c.shape('ellipse', (x - 1.4, 18.2, x + 1.6, 20.4), '#e8b06a')
    c.solid('rect', (9, 27, 23, 29.5), '#7e8296', angle=90)
    return c.done()


def bat(c, x, y, s, col='#3a2a52', eyes=True):
    pts = [(x, y + 0.8 * s), (x - 1.2 * s, y - 0.2 * s), (x - 3.4 * s, y - 1.2 * s), (x - 2.8 * s, y + 0.2 * s),
           (x - 3.6 * s, y + 0.9 * s), (x - 1.6 * s, y + 0.9 * s), (x - 0.7 * s, y + 1.8 * s), (x, y + 1.3 * s),
           (x + 0.7 * s, y + 1.8 * s), (x + 1.6 * s, y + 0.9 * s), (x + 3.6 * s, y + 0.9 * s), (x + 2.8 * s, y + 0.2 * s),
           (x + 3.4 * s, y - 1.2 * s), (x + 1.2 * s, y - 0.2 * s)]
    c.shape('poly', pts, ('lin', light(col, 0.2), dark(col, 0.3), 90))
    c.shape('ellipse', (x - 0.9 * s, y - 0.9 * s, x + 0.9 * s, y + 0.9 * s), col)
    c.shape('poly', [(x - 0.8 * s, y - 0.4 * s), (x - 0.5 * s, y - 1.5 * s), (x - 0.1 * s, y - 0.6 * s)], col)
    c.shape('poly', [(x + 0.8 * s, y - 0.4 * s), (x + 0.5 * s, y - 1.5 * s), (x + 0.1 * s, y - 0.6 * s)], col)
    if eyes:
        c.shape('rect', (x - 0.6 * s, y - 0.2 * s, x - 0.2 * s, y + 0.2 * s), '#ff4040')
        c.shape('rect', (x + 0.2 * s, y - 0.2 * s, x + 0.6 * s, y + 0.2 * s), '#ff4040')


def bat_blaster():
    c = Canvas()
    bat(c, 24, 6, 1.7)
    bat(c, 15, 4, 1.1)
    bat(c, 28.5, 14, 0.9)
    # cannon
    c.solid('poly', [(4, 22), (19, 12), (23, 18), (8, 28)], '#4a2f7a', angle=30, strength=0.4)
    c.shape('ellipse', (17.5, 10.5, 24.5, 19.5), ('lin', '#7a5ac8', '#2a1a4a', 45))
    c.shape('ellipse', (19, 12.3, 23, 17.7), '#120a20')
    c.shape('poly', [(8, 17), (13, 14), (14.5, 16.5), (9.5, 19.5)], '#ff4040')
    c.solid('poly', [(7, 25), (11, 23), (13, 29), (9, 30)], '#2c2a40', angle=80)
    for (x, y) in [(10, 21), (15, 18)]:
        c.shape('ellipse', (x - 0.8, y - 0.8, x + 0.8, y + 0.8), '#c9ccd8')
    return c.done()


def fake_tnt():
    c = Canvas()
    # an iso cube
    top = [(16, 6), (28, 11), (16, 16), (4, 11)]
    left = [(4, 11), (16, 16), (16, 30), (4, 25)]
    right = [(16, 16), (28, 11), (28, 25), (16, 30)]
    c.shape('poly', left, ('lin', '#e8483c', '#a8261c', 90))
    c.shape('poly', right, ('lin', '#c8362a', '#801a12', 90))
    c.shape('poly', top, ('lin', '#ff7a6c', '#e04a3c', 90))
    # the white bands with TNT
    c.shape('poly', [(4, 15.5), (16, 20.5), (16, 25.5), (4, 20.5)], '#f2f2f2')
    c.shape('poly', [(16, 20.5), (28, 15.5), (28, 20.5), (16, 25.5)], '#d6d6dc')
    for i, x in enumerate((6.5, 9.5, 12.5)):
        y = 16.5 + (x - 4) * 5 / 12
        if i != 1:
            c.shape('poly', [(x - 1, y), (x + 1, y + 0.8), (x + 1, y + 1.6), (x - 1, y + 0.8)], '#222222')
            c.shape('poly', [(x - 0.3, y + 0.6), (x + 0.3, y + 0.9), (x + 0.3, y + 4.2), (x - 0.3, y + 3.9)], '#222222')
        else:
            c.shape('poly', [(x - 1, y), (x - 0.4, y + 0.3), (x - 0.4, y + 3.8), (x - 1, y + 3.5)], '#222222')
            c.shape('poly', [(x + 0.4, y + 0.6), (x + 1, y + 0.9), (x + 1, y + 4.4), (x + 0.4, y + 4.1)], '#222222')
            c.line([(x - 0.6, y + 0.5), (x + 0.6, y + 3.4)], '#222222', 0.7)
    # fuse and spark
    c.line([(16, 11), (17, 7), (19.5, 5)], '#5a4a3a', 1.2)
    c.star(21, 3.5, 3, '#ffe14d', points=5, inner=0.45)
    c.star(21, 3.5, 1.4, WHITE, points=4)
    return c.done()


def disco_ball():
    c = Canvas()
    c.line([(16, 0.5), (16, 6)], '#c9ccd8', 1)
    c.shape('rect', (14.5, 5, 17.5, 7), '#8a8ea0')
    c.shape('ellipse', (4, 6, 28, 30), ('rad', '#f4f6ff', '#5a6078', 0.35, 0.3, 0.9))
    # mirror tiles
    import numpy as np
    for row in range(-5, 6):
        y = 18 + row * 2.2
        half = math.sqrt(max(0, 12 ** 2 - (y - 18) ** 2))
        n = max(1, int(half / 1.8))
        for k in range(-n, n):
            x = 16 + (k + 0.5) * (half / n)
            shade = ['#e8ecff', '#9aa4c8', '#cfd6f5', '#7c86aa', '#ffffff', '#b6a8ff', '#8ad8ff'][(row * 3 + k * 5) % 7]
            w = half / n * 0.42
            c.shape('rect', (x - w, y - 0.85, x + w, y + 0.85), shade, opacity=0.85)
    c.shape('arc', ((4, 6, 28, 30), 0, 360), '#4a4f68', width=0.9)
    for (x, y, r) in [(10, 12, 3), (23, 22, 1.8), (27, 9, 2.2)]:
        c.star(x, y, r, WHITE)
    return c.done()


def snow_cannon():
    c = Canvas()
    for (x, y, r) in [(24, 6, 3), (29, 13, 2), (18, 3, 1.8)]:
        for i in range(3):
            a = math.radians(i * 60)
            c.line([(x - math.cos(a) * r, y - math.sin(a) * r), (x + math.cos(a) * r, y + math.sin(a) * r)], WHITE, 0.8)
        c.shape('ellipse', (x - 0.7, y - 0.7, x + 0.7, y + 0.7), '#bfe8ff')
    # wheels and barrel
    c.solid('poly', [(5, 20), (17, 10), (22, 16), (10, 26)], '#7fd0ff', angle=30, strength=0.45)
    c.shape('poly', [(7, 18.5), (19, 8.5), (19.8, 9.6), (7.8, 19.6)], WHITE, opacity=0.7)
    c.shape('ellipse', (16, 8.5, 23.5, 17.5), ('lin', '#e6f6ff', '#5aa8e0', 45))
    c.shape('ellipse', (17.6, 10.3, 21.9, 15.7), '#eaf8ff')
    c.ball(11, 25, 4.6, '#5a6b8a', shine=False)
    c.shape('ellipse', (9.3, 23.3, 12.7, 26.7), '#c9d6e8')
    # icicles
    for x in (9, 12.5):
        c.shape('poly', [(x - 1, 21 - (x - 9) * 0.6), (x + 1, 20.4 - (x - 9) * 0.6), (x, 24 - (x - 9) * 0.6)], '#dff4ff')
    return c.done()


def pumpkin(c, x, y, r, face=True):
    c.shape('ellipse', (x - r, y - r * 0.85, x + r, y + r * 0.85), ('rad', '#ffb35a', '#c25a0a', 0.35, 0.3, 1))
    for dx in (-0.45, 0, 0.45):
        c.shape('arc', ((x + dx * r - r * 0.35, y - r * 0.85, x + dx * r + r * 0.35, y + r * 0.85), 270 - 70, 270 + 70), '#b8540c', width=0.6)
    c.shape('rect', (x - r * 0.12, y - r * 1.15, x + r * 0.12, y - r * 0.7), '#4a6b22')
    if face:
        c.shape('poly', [(x - r * 0.55, y - r * 0.2), (x - r * 0.2, y - r * 0.2), (x - r * 0.37, y - r * 0.5)], '#ffe86a')
        c.shape('poly', [(x + r * 0.55, y - r * 0.2), (x + r * 0.2, y - r * 0.2), (x + r * 0.37, y - r * 0.5)], '#ffe86a')
        c.shape('poly', [(x - r * 0.55, y + r * 0.15), (x + r * 0.55, y + r * 0.15), (x + r * 0.3, y + r * 0.45),
                         (x, y + r * 0.3), (x - r * 0.3, y + r * 0.45)], '#ffe86a')


def pumpkin_launcher():
    c = Canvas()
    pumpkin(c, 22, 9, 7)
    # slingshot
    c.line([(9, 30), (11, 19)], ('lin', '#a8743a', '#5a3a1a', 0), 2.6)
    c.line([(11, 19), (6, 10)], '#8b5a2b', 2.2)
    c.line([(11, 19), (15, 11)], '#8b5a2b', 2.2)
    c.line([(6, 10), (14, 13.5)], '#3a2a52', 0.8)
    c.line([(15, 11), (14, 13.5)], '#3a2a52', 0.8)
    c.shape('ellipse', (12.5, 12, 16, 15.5), '#2c2a40')
    for (x, y) in [(10, 23), (10.6, 26)]:
        c.shape('rect', (x - 1.4, y - 0.4, x + 1.4, y + 0.4), '#3a2a52')
    return c.done()


def balloon():
    c = Canvas()
    c.line([(15, 22), (13, 26), (16, 29), (14, 31.5)], '#e8e8f0', 0.7)
    c.shape('ellipse', (7, 2, 23, 21), ('rad', '#ff8a8a', '#a8141c', 0.32, 0.28, 0.9))
    c.shape('poly', [(13.5, 23), (16.5, 23), (15, 20.5)], '#a8141c')
    c.shape('ellipse', (10, 5, 14, 11), (255, 255, 255), opacity=0.7)
    c.shape('ellipse', (11, 12, 12.4, 13.6), (255, 255, 255), opacity=0.5)
    return c.done()


def melody_maker():
    c = Canvas()
    # two beamed notes
    c.solid('poly', [(11, 6), (26, 2.5), (26, 6.5), (11, 10)], PURPLE, angle=90)
    c.shape('rect', (11, 7, 13, 24), ('lin', '#c79bff', '#5a2ab0', 0))
    c.shape('rect', (24, 4, 26, 21), ('lin', '#c79bff', '#5a2ab0', 0))
    c.shape('ellipse', (5.5, 21, 13.5, 27.5), ('rad', '#d6b8ff', '#4a1f9a', 0.3, 0.3, 1))
    c.shape('ellipse', (18.5, 18, 26.5, 24.5), ('rad', '#d6b8ff', '#4a1f9a', 0.3, 0.3, 1))
    c.star(5, 8, 2.4, '#ffe14d')
    c.star(28, 27, 2, WHITE)
    c.star(18, 29, 1.4, PINK)
    return c.done()


def magic_wand():
    c = Canvas()
    c.glow(23, 9, 8, '#ffe680', 0.45)
    c.line([(5, 28), (20, 13)], ('lin', '#3a3a4a', '#121218', 45), 2.6)
    c.line([(5, 28), (8, 25)], WHITE, 2.8)
    c.line([(17.5, 15.5), (20, 13)], WHITE, 2.8)
    c.star(23, 9, 7, ('lin', '#fff6b0', '#f0a828', 60), points=5, inner=0.45)
    c.star(23, 9, 2.5, WHITE, points=5, inner=0.5)
    for (x, y, r, col) in [(12, 6, 2, '#c79bff'), (29, 18, 1.8, PINK), (28, 3, 1.4, WHITE), (16, 20, 1.2, '#7fd7ff')]:
        c.star(x, y, r, col)
    return c.done()


def grappling_hook():
    c = Canvas()
    c.line([(18, 4), (12, 3), (6, 8), (4, 16), (6, 24), (3, 29)], '#a8743a', 1.2)
    hook(c, 18, 3, 1.2)
    return c.done()


def hook(c, x, y, s):
    """An upright grappling hook: ring at (x, y), shaft down, three prongs curling up."""
    metal = ('lin', '#f4f6ff', '#5a6078', 20)
    c.shape('arc', ((x - 2.2 * s, y - 2.2 * s, x + 2.2 * s, y + 2.2 * s), 0, 360), '#8a8ea0', width=1.1 * s)
    c.line([(x, y + 2 * s), (x, y + 20 * s)], metal, 2.2 * s)
    b = y + 20 * s
    c.shape('arc', ((x - 9 * s, b - 9 * s, x + 0.6 * s, b + 1.2 * s), 30, 175), metal, width=2.8 * s)
    c.shape('arc', ((x - 0.6 * s, b - 9 * s, x + 9 * s, b + 1.2 * s), 5, 150), metal, width=2.8 * s)
    c.shape('poly', [(x - 9.2 * s, b - 4.5 * s), (x - 10.2 * s, b - 8.5 * s), (x - 7.2 * s, b - 5.5 * s)], '#e8eaf4')
    c.shape('poly', [(x + 9.2 * s, b - 4.5 * s), (x + 10.2 * s, b - 8.5 * s), (x + 7.2 * s, b - 5.5 * s)], '#e8eaf4')
    c.shape('ellipse', (x - 2 * s, b - 1.6 * s, x + 2 * s, b + 1.8 * s), ('rad', '#ffffff', '#6a7086', 0.3, 0.3, 1))


def grappling_hook_head():
    c = Canvas(16, 16)
    hook(c, 8, 1.6, 0.55)
    return c.done()


def blink_stick():
    c = Canvas()
    c.glow(21, 10, 10, '#b06bff', 0.45)
    c.shape('arc', ((12, 1, 30, 19), 200, 520), '#d9b8ff', width=0.9)
    c.line([(4, 29), (17, 16)], ('lin', '#6a4a8a', '#2a1a3a', 45), 2.2)
    c.shape('ellipse', (14.5, 13.5, 19, 18), '#ffcf5a')
    c.shape('poly', [(21, 2.5), (27, 9), (21, 18), (15, 9)], ('lin', '#f0d8ff', '#6a1fd0', 60))
    c.shape('poly', [(21, 2.5), (21, 18), (15, 9)], ('lin', '#ffffff', '#9b5cff', 60), opacity=0.55)
    c.star(27, 17, 1.8, WHITE)
    c.star(12, 7, 1.4, '#e0c8ff')
    return c.done()


def trampoline():
    c = Canvas()
    # legs
    for x in (6, 16, 26):
        c.solid('rect', (x - 1, 17, x + 1, 28), '#5a6078', angle=0)
    c.solid('poly', [(1.5, 15), (16, 9), (30.5, 15), (16, 21)], '#2f6fe0', angle=90)
    c.shape('ellipse', (1, 9, 31, 21), ('lin', '#ffcf5a', '#d98f1a', 90))
    c.shape('ellipse', (3, 10.5, 29, 19.5), ('rad', '#5aa8ff', '#1a3a9a', 0.5, 0.4, 0.8))
    c.shape('ellipse', (9, 12.5, 23, 17.5), '#7fc0ff', opacity=0.45)
    # bounce lines
    for (x, y) in [(11, 5), (16, 3), (21, 5)]:
        c.line([(x, y + 2.5), (x, y)], WHITE, 1)
    return c.done()


def beach_ball(size=32):
    c = Canvas(size, size)
    k = size / 32
    m = c.mask('ellipse', (3 * k, 3 * k, 29 * k, 29 * k))
    cols = ['#ff4d4d', '#ffffff', '#3f8bff', '#ffe14d', '#ffffff', '#4fd36b']
    from PIL import ImageChops
    for i, col in enumerate(cols):
        a0, a1 = math.radians(-90 + i * 60), math.radians(-90 + (i + 1) * 60)
        pts = [(16 * k, 16 * k)] + [(16 * k + math.cos(a0 + (a1 - a0) * t / 6) * 20 * k, 16 * k + math.sin(a0 + (a1 - a0) * t / 6) * 20 * k) for t in range(7)]
        c.paint(ImageChops.multiply(c.mask('poly', pts), m), col)
    c.shape('ellipse', (3 * k, 3 * k, 29 * k, 29 * k), ('rad', (255, 255, 255), (0, 0, 30), 0.3, 0.25, 1.1), opacity=0.35)
    c.shape('ellipse', (13 * k, 13 * k, 19 * k, 19 * k), '#ffffff')
    c.shape('ellipse', (8 * k, 7 * k, 13 * k, 11 * k), (255, 255, 255), opacity=0.6)
    return c.done()


def paintball():
    c = Canvas(16, 16)
    c.ball(8, 8, 5.5, PINK)
    return c.done()


GADGETS = {
    'party_popper': party_popper, 'paintball_gun': paintball_gun, 'bubble_wand': bubble_wand,
    'firework_launcher': firework_launcher, 'cookie_fountain': cookie_fountain, 'bat_blaster': bat_blaster,
    'fake_tnt': fake_tnt, 'disco_ball': disco_ball, 'snow_cannon': snow_cannon, 'pumpkin_launcher': pumpkin_launcher,
    'balloon': balloon, 'melody_maker': melody_maker, 'magic_wand': magic_wand, 'grappling_hook': grappling_hook,
    'blink_stick': blink_stick, 'trampoline': trampoline, 'beach_ball': beach_ball, 'cookie': cookie,
    'paintball': paintball, 'grappling_hook_head': grappling_hook_head,
}


# ---------------------------------------------------------------- cloak pieces (textures for the models riding the wearer)

def part_halo():
    c = Canvas()
    c.glow(16, 16, 13, '#fff2a8', 0.45)
    c.shape('arc', ((3, 3, 29, 29), 0, 360), ('lin', '#fff8d0', '#e8a420', 60), width=4.2)
    c.shape('arc', ((4.5, 4.5, 27.5, 27.5), 200, 300), (255, 255, 255), width=1.2)
    c.erase('ellipse', (8, 8, 24, 24))
    return c.done(outline=False, keep_soft=True)


def part_wing():
    """A feathered wing growing from the left edge (the spine) out to the right."""
    c = Canvas()
    feathers = [(2, 14, 30, 4, 2.6), (2, 16, 29, 9, 2.6), (2, 18, 27, 14, 2.6), (2, 20, 24, 19, 2.4),
                (2, 22, 20, 24, 2.2), (2, 24, 15, 28, 2.0)]
    c.shape('poly', [(1, 8), (12, 3), (24, 2), (31, 4), (24, 9), (12, 14), (2, 24)], ('lin', '#ffffff', '#c8d6ff', 70))
    for (x0, y0, x1, y1, w) in feathers:
        c.line([(x0, y0), (x1, y1)], ('lin', '#ffffff', '#b8c8f0', 0), w)
        c.line([(x0 + 2, y0 - 0.2), (x1 - 1, y1 - 0.3)], '#e8eeff', 0.5)
    c.line([(1, 9), (12, 4), (24, 3), (30, 4.5)], '#ffffff', 1.3)
    c.shape('ellipse', (0, 7, 6, 13), '#f0f4ff')
    return c.done(outline=True)


def part_sparkle(frame=0):
    c = Canvas(16, 16)
    s = [1.0, 0.7, 0.45, 0.75][frame]
    c.glow(8, 8, 6 * s + 1, '#ffd0f4', 0.5)
    c.star(8, 8, 7 * s, ('rad', '#ffffff', '#ff8fe0', 0.5, 0.5, 0.8), points=4, inner=0.25)
    c.star(8, 8, 3.5 * s, '#ffffff', points=4, inner=0.3, rot=-45)
    return c.done(outline=False, keep_soft=True)


def part_cloud():
    c = Canvas(16, 16)
    c.shape('rect', (0, 0, 16, 16), ('lin', '#ffffff', '#c9d2e4', 90))
    import random
    r = random.Random(4)
    for _ in range(14):
        x, y, rr = r.uniform(0, 16), r.uniform(0, 16), r.uniform(1.5, 3.5)
        c.shape('ellipse', (x - rr, y - rr, x + rr, y + rr), '#ffffff', opacity=0.5)
    for _ in range(8):
        x, y, rr = r.uniform(0, 16), r.uniform(8, 16), r.uniform(1, 2.5)
        c.shape('ellipse', (x - rr, y - rr, x + rr, y + rr), '#aab4cc', opacity=0.35)
    return c.done(outline=False)


def part_orb(col):
    c = Canvas(16, 16)
    c.glow(8, 8, 5.5, col, 0.6)
    c.shape('ellipse', (3, 3, 13, 13), ('rad', '#ffffff', col, 0.38, 0.35, 0.75))
    c.shape('ellipse', (5, 4.5, 8, 7), (255, 255, 255), opacity=0.8)
    return c.done(outline=False, keep_soft=True)


def part_flame(frame=0):
    c = Canvas(16, 16)
    sway = math.sin(frame / 8 * math.pi * 2) * 1.2
    tall = 1 + 0.12 * math.sin(frame / 8 * math.pi * 4)
    c.glow(8, 11, 6, '#ff8a1f', 0.45)
    c.shape('poly', [(2.5, 15), (13.5, 15), (12.5, 9), (8 + sway, 15 - 14 * tall), (6 + sway * 0.5, 8), (3.5, 10)],
            ('lin', '#ffe14d', '#e8380c', 270))
    c.shape('poly', [(5, 15), (11, 15), (10.5, 11), (8 + sway * 0.8, 15 - 9 * tall), (6, 11.5)], ('lin', '#ffffff', '#ffd23f', 270))
    return c.done(outline=False, keep_soft=True)


def part_sun():
    c = Canvas()
    c.glow(16, 16, 12, '#ffe680', 0.5)
    for i in range(12):
        a = math.radians(i * 30)
        l = 15 if i % 2 == 0 else 12
        p = [(16 + math.cos(a - 0.16) * 8, 16 + math.sin(a - 0.16) * 8), (16 + math.cos(a) * l, 16 + math.sin(a) * l),
             (16 + math.cos(a + 0.16) * 8, 16 + math.sin(a + 0.16) * 8)]
        c.shape('poly', p, ('lin', '#fff6b0', '#ffb020', 90))
    c.shape('ellipse', (7, 7, 25, 25), ('rad', '#fffbe0', '#ffb428', 0.4, 0.38, 0.8))
    return c.done(outline=False, keep_soft=True)


def part_snowflake():
    c = Canvas(16, 16)
    for i in range(3):
        a = math.radians(i * 60 + 90)
        c.line([(8 - math.cos(a) * 7, 8 - math.sin(a) * 7), (8 + math.cos(a) * 7, 8 + math.sin(a) * 7)], '#ffffff', 1.1)
        for sgn in (-1, 1):
            bx, by = 8 + sgn * math.cos(a) * 4.5, 8 + sgn * math.sin(a) * 4.5
            for d in (-1, 1):
                b = a + sgn * 0 + d * math.radians(50)
                c.line([(bx, by), (bx + sgn * math.cos(b) * 2.2, by + sgn * math.sin(b) * 2.2)], '#dff4ff', 0.8)
    c.shape('ellipse', (6.5, 6.5, 9.5, 9.5), '#bfe8ff')
    return c.done(outline=False, keep_soft=True)


def part_petal():
    c = Canvas(16, 16)
    c.shape('poly', [(8, 15), (2.5, 8), (3.5, 3), (7, 1.5), (8, 4), (9, 1.5), (12.5, 3), (13.5, 8)], ('rad', '#fff0f6', '#ff8cc0', 0.5, 0.8, 0.9))
    c.line([(8, 14), (8, 6)], '#ffb8d8', 0.6)
    return c.done(outline=False)


def part_ghost():
    c = Canvas()
    body = [(6, 14), (8, 6), (16, 2.5), (24, 6), (26, 14), (27, 28), (24, 25), (21, 29), (18, 25), (14, 29), (11, 25), (8, 29), (5, 26)]
    c.glow(16, 16, 11, '#c8f0ff', 0.3)
    c.shape('poly', body, ('rad', '#ffffff', '#b8d8f0', 0.4, 0.3, 1.0))
    c.shape('ellipse', (9.5, 10, 14, 16), '#1a1b26')
    c.shape('ellipse', (18, 10, 22.5, 16), '#1a1b26')
    c.shape('ellipse', (13, 18, 19, 23), '#2a2b3a')
    c.shape('ellipse', (10.5, 11, 12, 12.5), '#ffffff')
    c.shape('ellipse', (19, 11, 20.5, 12.5), '#ffffff')
    return c.done(outline=False, keep_soft=True)


def part_pumpkin_light():
    c = Canvas()
    for i in range(10):
        a = math.radians(i * 36)
        x, y = 16 + math.cos(a) * 12, 16 + math.sin(a) * 12
        col = '#ff8a1f' if i % 2 == 0 else '#9b5cff'
        c.glow(x, y, 3.4, col, 0.8)
        c.shape('ellipse', (x - 1.2, y - 1.2, x + 1.2, y + 1.2), '#ffe0a0' if i % 2 == 0 else '#e0c8ff')
    c.shape('arc', ((4, 4, 28, 28), 0, 360), '#ff8a1f', width=0.6, opacity=0.6)
    return c.done(outline=False, keep_soft=True)


PARTS = {
    'halo': part_halo, 'wing': part_wing, 'cloud': part_cloud, 'orb_blue': lambda: part_orb('#5b9dff'),
    'orb_pink': lambda: part_orb('#ff6fd8'), 'orb_gold': lambda: part_orb('#ffcf5a'), 'sun': part_sun,
    'snowflake': part_snowflake, 'petal': part_petal, 'ghost': part_ghost, 'pumpkin_light': part_pumpkin_light,
}
ANIMATED_PARTS = {'sparkle': (part_sparkle, 4, 3), 'flame': (part_flame, 8, 2)}   # frames, ticks per frame


# ---------------------------------------------------------------- cloak icons (menus)

def head(c, x=16, y=22, r=6.5, hair='#5a3a1e'):
    """A little player head and shoulders to show cloaks on."""
    c.solid('rrect', (x - 9, y + 4, x + 9, y + 11, 3), '#3fa8d0', angle=90)
    c.solid('rrect', (x - r, y - r, x + r, y + r, 1.5), '#e8b088', angle=60)
    c.shape('rect', (x - r, y - r, x + r, y - r * 0.45), hair)
    c.shape('rect', (x - r * 0.55, y - 0.4, x - r * 0.2, y + 1.2), '#2a2b3a')
    c.shape('rect', (x + r * 0.2, y - 0.4, x + r * 0.55, y + 1.2), '#2a2b3a')


def cloak_sparkle_aura():
    c = Canvas()
    head(c)
    for (x, y, r) in [(6, 7, 4), (25, 5, 3.4), (27, 18, 2.6), (4, 19, 2.4), (16, 4, 2)]:
        c.star(x, y, r, ('rad', '#ffffff', '#ff8fe0', 0.5, 0.5, 0.8), inner=0.28)
    return c.done()


def cloak_halo():
    c = Canvas()
    head(c, y=21)
    c.shape('ellipse', (5, 3, 27, 11), ('lin', '#fff8d0', '#e8a420', 90))
    c.erase('ellipse', (9, 5, 23, 9))
    c.shape('arc', ((5, 3, 27, 11), 190, 300), (255, 255, 255), width=0.9)
    return c.done()


def cloak_rain_cloud():
    c = Canvas()
    for (x, y) in [(9, 17), (15, 21), (21, 18), (12, 26), (24, 25), (18, 28)]:
        c.shape('poly', [(x, y - 1.6), (x + 1.3, y + 0.6), (x, y + 1.6), (x - 1.3, y + 0.6)], '#5bb8ff')
    for (x, y, r) in [(10, 10, 5), (17, 8, 6.5), (23.5, 11, 4.6), (14, 12.5, 4.5), (20, 13, 4)]:
        c.shape('ellipse', (x - r, y - r, x + r, y + r), ('rad', '#ffffff', '#aab6d0', 0.4, 0.2, 1.1))
    return c.done()


def cloak_orbit():
    c = Canvas()
    c.shape('arc', ((2, 9, 30, 23), 0, 360), '#8a9ad8', width=0.8)
    c.shape('ellipse', (10, 10, 22, 22), ('rad', '#d6b8ff', '#4a1f9a', 0.35, 0.3, 1))
    for (x, y, col) in [(4, 13, '#5b9dff'), (27, 19, '#ff6fd8'), (19, 23, '#ffcf5a')]:
        c.shape('ellipse', (x - 3, y - 3, x + 3, y + 3), ('rad', '#ffffff', col, 0.35, 0.3, 0.8))
    c.shape('arc', ((2, 9, 30, 23), 200, 340), '#c8d4ff', width=0.8)
    return c.done()


def cloak_flame_ring():
    c = Canvas()
    c.shape('ellipse', (2, 18, 30, 30), '#5a2a0a', opacity=0.6)
    for (x, h) in [(5, 11), (10, 15), (16, 18), (22, 15), (27, 11)]:
        c.shape('poly', [(x - 3, 25), (x + 3, 25), (x + 1.5, 25 - h * 0.5), (x + 0.5, 25 - h), (x - 2, 25 - h * 0.45)], ('lin', '#ffe14d', '#e8380c', 270))
        c.shape('poly', [(x - 1.5, 25), (x + 1.5, 25), (x + 0.3, 25 - h * 0.55)], '#fff6c0')
    return c.done()


def cloak_angel_wings():
    c = Canvas()
    w = part_wing().resize((16, 16))
    c2 = Canvas()
    from PIL import Image
    out = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    out.alpha_composite(w, (16, 6))
    out.alpha_composite(w.transpose(Image.FLIP_LEFT_RIGHT), (0, 6))
    c.img = out.resize((256, 256), Image.NEAREST)
    head(c, y=21, r=5.5)
    return c.done(outline=False)


def cloak_sun_rays():
    c = Canvas()
    from PIL import Image
    c.img.alpha_composite(part_sun().resize((256, 256), Image.LANCZOS))
    head(c, y=22, r=5.5)
    return c.done()


def cloak_snow_globe():
    c = Canvas()
    c.shape('ellipse', (3, 2, 29, 28), ('rad', (220, 240, 255), (120, 170, 220), 0.4, 0.35, 1), opacity=0.6)
    c.solid('rrect', (5, 25, 27, 31, 2), '#8b5a2b', angle=90)
    c.shape('poly', [(8, 22), (12, 15), (16, 22)], '#2e9e4a')
    c.shape('poly', [(14, 23), (19, 13), (24, 23)], '#278a40')
    for (x, y) in [(9, 8), (16, 6), (22, 9), (12, 12), (25, 15), (7, 17), (19, 17)]:
        c.star(x, y, 1.6, '#ffffff')
    c.shape('arc', ((3, 2, 29, 28), 200, 290), (255, 255, 255), width=1)
    return c.done()


def cloak_blossom_aura():
    c = Canvas()
    from PIL import Image
    p = part_petal()
    for (x, y, rot, s) in [(2, 3, 20, 12), (17, 1, -30, 13), (9, 14, 60, 11), (20, 16, -10, 13), (4, 21, 100, 9)]:
        im = p.resize((s, s), Image.LANCZOS).rotate(rot, expand=True)
        c.img.alpha_composite(im.resize((im.width * 8, im.height * 8), Image.NEAREST), (x * 8, y * 8))
    return c.done()


def cloak_spooky_aura():
    c = Canvas()
    from PIL import Image
    c.shape('ellipse', (3, 23, 29, 31), '#9b5cff', opacity=0.5)
    c.img.alpha_composite(part_ghost().resize((176, 176), Image.LANCZOS), (40, 16))
    for (x, y, col) in [(4, 10, '#ff8a1f'), (28, 8, '#9b5cff'), (26, 24, '#ff8a1f')]:
        c.shape('ellipse', (x - 2, y - 2, x + 2, y + 2), ('rad', '#ffffff', col, 0.4, 0.4, 0.8))
    return c.done()


CLOAKS = {
    'sparkle_aura': cloak_sparkle_aura, 'halo': cloak_halo, 'rain_cloud': cloak_rain_cloud, 'orbit': cloak_orbit,
    'flame_ring': cloak_flame_ring, 'angel_wings': cloak_angel_wings, 'sun_rays': cloak_sun_rays,
    'snow_globe': cloak_snow_globe, 'blossom_aura': cloak_blossom_aura, 'spooky_aura': cloak_spooky_aura,
}


# ---------------------------------------------------------------- gestures

SKIN, SHIRT, PANTS = '#e8b088', '#3fa8d0', '#3a4a9a'


def figure(c, x, y, arms='down', lean=0, face=True):
    """A little blocky player: x, y is the top of the head."""
    def p(px, py):  # lean forward (bow)
        return (x + px + lean * (py / 20) * 8, y + py - abs(lean) * (py / 20) * 2)
    c.solid('poly', [p(-3, 9), p(3, 9), p(3, 19), p(-3, 19)], SHIRT, angle=90)
    c.solid('poly', [p(-3, 19), p(0, 19), p(0, 27), p(-3, 27)], PANTS, angle=90)
    c.solid('poly', [p(0, 19), p(3, 19), p(3, 27), p(0, 27)], dark(PANTS, 0.2), angle=90)
    hx, hy = p(0, 0)
    c.solid('rrect', (hx - 4, hy, hx + 4, hy + 8, 1), SKIN, angle=60)
    c.shape('rect', (hx - 4, hy, hx + 4, hy + 2.4), '#5a3a1e')
    if face:
        c.shape('rect', (hx - 2.4, hy + 4, hx - 1.2, hy + 5.2), '#2a2b3a')
        c.shape('rect', (hx + 1.2, hy + 4, hx + 2.4, hy + 5.2), '#2a2b3a')
    if arms == 'down':
        for sx in (-1, 1):
            c.solid('poly', [p(sx * 3, 9), p(sx * 5.4, 9), p(sx * 5.4, 18), p(sx * 3, 18)], SHIRT, angle=90)
    elif arms == 'up':
        for sx in (-1, 1):
            c.line([p(sx * 4.2, 10), p(sx * 8.5, 2)], SHIRT, 2.4)
            c.shape('ellipse', (x + sx * 8.5 - 1.4, y + 0.6, x + sx * 8.5 + 1.4, y + 3.4), SKIN)


def gesture_wave():
    c = Canvas()
    figure(c, 12, 3, arms='down')
    c.line([(16, 13), (22, 5)], SHIRT, 2.4)
    c.shape('ellipse', (20.5, 2, 24.5, 6.5), SKIN)
    for r in (4, 6.5):
        c.shape('arc', ((22 - r, 4 - r, 22 + r, 4 + r), 300, 360), '#ffffff', width=0.9)
    return c.done()


def gesture_clap():
    c = Canvas()
    for sx in (-1, 1):
        x = 16 + sx * 3
        c.shape('poly', [(x - 3.5, 30), (x + 3.5, 30), (16 + sx * 1.2, 11), (16 + sx * 5.5, 13)] if sx > 0 else
                [(x - 3.5, 30), (x + 3.5, 30), (16 + sx * 5.5, 13), (16 + sx * 1.2, 11)], ('lin', '#ffd0a8', '#c88a5a', 0))
        c.shape('rect', (x - 3.5, 26, x + 3.5, 31), SHIRT)
    for i in range(5):
        a = math.radians(-160 + i * 35)
        c.line([(16 + math.cos(a) * 6, 9 + math.sin(a) * 6), (16 + math.cos(a) * 9.5, 9 + math.sin(a) * 9.5)], '#ffe14d', 1.2)
    c.star(16, 6, 2.5, '#ffffff')
    return c.done()


def gesture_bow():
    c = Canvas()
    # legs straight, body bent forward, head low
    c.solid('rect', (7, 18, 10.5, 29), PANTS, angle=90)
    c.solid('rect', (10.5, 18, 14, 29), dark(PANTS, 0.2), angle=90)
    c.solid('poly', [(6.5, 19), (14.5, 19), (26, 13), (22, 7)], SHIRT, angle=60)
    c.line([(19, 12), (21, 22)], SHIRT, 2.6)
    c.shape('ellipse', (19.6, 21, 22.6, 24), SKIN)
    c.solid('poly', [(22, 7), (29, 10), (26.5, 17.5), (19.5, 14.5)], SKIN, angle=60)
    c.shape('poly', [(22, 7), (29, 10), (28.4, 11.8), (21.2, 8.8)], '#5a3a1e')
    c.shape('arc', ((2, 0, 26, 24), 200, 280), '#ffcf5a', width=1)
    c.star(5, 4, 2, '#ffcf5a')
    return c.done()


def gesture_spin():
    c = Canvas()
    figure(c, 16, 3, arms='down')
    c.shape('arc', ((3, 12, 29, 24), 20, 160), '#ffcf5a', width=1.4)
    c.shape('arc', ((3, 12, 29, 24), 200, 340), '#ffcf5a', width=1.4, opacity=0.6)
    c.shape('poly', [(28.5, 15.5), (30.5, 20), (26, 19)], '#ffcf5a')
    c.shape('poly', [(3.5, 20.5), (1.5, 16), (6, 17)], '#ffcf5a')
    return c.done()


def gesture_facepalm():
    c = Canvas()
    c.solid('rrect', (6, 5, 26, 25, 3), SKIN, angle=60)
    c.shape('rect', (6, 5, 26, 10), '#5a3a1e')
    c.shape('rect', (9.5, 21, 22.5, 22.5), '#8a4a3a')
    # the hand over the eyes
    c.solid('rrect', (4, 11, 28, 18, 3), '#f0bc94', angle=90)
    for x in (9, 14, 19, 24):
        c.line([(x, 11.5), (x, 17.5)], '#c88a5a', 0.6)
    c.shape('rect', (2, 13, 6, 26), SHIRT)
    for (x, y) in [(27, 4), (29, 8)]:
        c.line([(x, y), (x + 2, y - 2)], '#7fd7ff', 1)
    return c.done()


def gesture_headbang():
    c = Canvas()
    c.solid('rrect', (8, 9, 22, 23, 2), SKIN, angle=60)
    c.shape('rect', (8, 9, 22, 13), '#5a3a1e')
    c.shape('rect', (10.5, 16, 12.5, 18), '#2a2b3a')
    c.shape('rect', (17.5, 16, 19.5, 18), '#2a2b3a')
    for k in range(3):
        c.shape('arc', ((4 - k * 2, 5 - k * 2, 26 + k * 2, 27 + k * 2), 200, 250), '#ffffff', width=0.8, opacity=0.8 - k * 0.2)
    # notes
    c.shape('rect', (26, 3, 27.2, 11), PURPLE)
    c.shape('ellipse', (23, 9.5, 27.2, 12.8), PURPLE)
    c.shape('poly', [(27.2, 3), (30.5, 5), (30.5, 6.5), (27.2, 4.6)], PURPLE)
    c.shape('rect', (4, 22, 5.2, 28), PINK)
    c.shape('ellipse', (1.2, 26.5, 5.2, 29.6), PINK)
    return c.done()


def gesture_celebrate():
    c = Canvas()
    figure(c, 16, 5, arms='up')
    c.shape('poly', [(12, 5.5), (20, 5.5), (16, -2)], ('lin', '#ff6fd8', '#9b5cff', 0))
    for i, (x, y) in enumerate([(4, 6), (27, 4), (6, 18), (26, 16), (2, 12), (29, 11), (9, 2)]):
        c.shape('rect', (x - 1, y - 1, x + 1, y + 1), CONFETTI[i % len(CONFETTI)])
    return c.done()


GESTURES = {'wave': gesture_wave, 'clap': gesture_clap, 'bow': gesture_bow, 'spin': gesture_spin,
            'facepalm': gesture_facepalm, 'headbang': gesture_headbang, 'celebrate': gesture_celebrate}


# ---------------------------------------------------------------- season tabs and the Collectibles item

def season_normal():
    c = Canvas()
    c.star(16, 16, 14, ('rad', '#ffffff', '#9b5cff', 0.45, 0.4, 0.8), points=5, inner=0.45)
    c.star(16, 16, 5, '#ffffff', points=4, inner=0.35)
    return c.done()


def season_summer():
    c = Canvas()
    for i in range(10):
        a = math.radians(i * 36)
        c.shape('poly', [(16 + math.cos(a - 0.25) * 9, 16 + math.sin(a - 0.25) * 9), (16 + math.cos(a) * 15, 16 + math.sin(a) * 15),
                         (16 + math.cos(a + 0.25) * 9, 16 + math.sin(a + 0.25) * 9)], ('lin', '#ffe14d', '#ff9a2e', 90))
    c.ball(16, 16, 9, '#ffc83d')
    return c.done()


def season_holiday():
    c = Canvas()
    c.line([(16, 2), (16, 7)], '#c9a040', 1.2)
    c.solid('rect', (13, 5.5, 19, 9), '#e8c060', angle=0)
    c.ball(16, 19, 11, '#e03a3a')
    c.shape('rect', (5.5, 17, 26.5, 19.5), '#ffcf5a', opacity=0.9)
    for x in (9, 14, 19, 24):
        c.star(x, 23.5, 1.4, '#ffffff')
    return c.done()


def season_easter():
    c = Canvas()
    m = c.mask('ellipse', (6, 2, 26, 30))
    c.paint(m, ('rad', '#ffe8f4', '#e88ac0', 0.38, 0.3, 1.0))
    from PIL import ImageChops
    for (y, col) in [(9, '#7fd7ff'), (16, '#ffe14d'), (23, '#9be37c')]:
        band = c.mask('rect', (0, y - 1.6, 32, y + 1.6))
        c.paint(ImageChops.multiply(band, m), col)
    for x in (11, 16, 21):
        c.paint(ImageChops.multiply(c.mask('ellipse', (x - 1.2, 11.3, x + 1.2, 13.7)), m), '#ffffff')
    c.shape('ellipse', (10, 5, 14, 10), (255, 255, 255), opacity=0.7)
    return c.done()


def season_halloween():
    c = Canvas()
    pumpkin(c, 16, 17, 13)
    return c.done()


def menu_collectibles():
    """The Collectibles join item: a top hat with a magic sparkle."""
    c = Canvas()
    c.glow(16, 18, 13, '#b266ff', 0.3)
    c.solid('ellipse', (2, 21, 30, 29), '#2a2238', angle=90)
    c.solid('rect', (8, 6, 24, 25), '#3a2f52', angle=0, strength=0.4)
    c.shape('ellipse', (8, 3.5, 24, 8.5), ('lin', '#5a4a7a', '#2a2238', 90))
    c.shape('rect', (8, 18, 24, 22), ('lin', '#d9b8ff', '#8a4fe0', 0))
    c.shape('rect', (14, 18, 18, 22), '#ffcf5a')
    c.shape('rect', (9.5, 7, 11, 17), (255, 255, 255), opacity=0.25)
    c.star(26, 6, 4.5, ('rad', '#ffffff', '#ffcf5a', 0.5, 0.5, 0.8), inner=0.3)
    c.star(5, 12, 2.6, '#ff8fe0', inner=0.3)
    c.star(28, 16, 1.6, '#ffffff')
    return c.done()


SEASONS = {'normal': season_normal, 'summer': season_summer, 'holiday': season_holiday, 'easter': season_easter,
           'halloween': season_halloween}


# ---------------------------------------------------------------- 1.38: the guns, drawn along a diagonal (stock bottom
# left, muzzle top right) so they're held like a crossbow, pointing forward

GUN_ANGLE = math.radians(40)


class Gun:
    """Draws in the gun's own frame: u along the barrel (0 = back, 30 = muzzle), v up from the barrel's middle."""

    def __init__(self, c, ox=3.5, oy=26.5, angle=GUN_ANGLE):
        self.c, self.ox, self.oy = c, ox, oy
        self.d = (math.cos(angle), -math.sin(angle))
        self.n = (-math.sin(angle), -math.cos(angle))

    def p(self, u, v):
        return (self.ox + u * self.d[0] + v * self.n[0], self.oy + u * self.d[1] + v * self.n[1])

    def rect(self, u0, v0, u1, v1, fill):
        self.c.shape('poly', [self.p(u0, v0), self.p(u1, v0), self.p(u1, v1), self.p(u0, v1)], fill)

    def poly(self, pts, fill):
        self.c.shape('poly', [self.p(u, v) for u, v in pts], fill)

    def oval(self, u, v, ru, rv, fill):
        self.c.shape('poly', [self.p(u + math.cos(a) * ru, v + math.sin(a) * rv) for a in (i * math.pi / 10 for i in range(20))], fill)

    def tube(self, u0, u1, r, base):
        """A round barrel: dark underside, base, light top stripe."""
        self.rect(u0, -r, u1, r, dark(base, 0.25))
        self.rect(u0, -r * 0.45, u1, r, base)
        self.rect(u0, r * 0.35, u1, r * 0.8, light(base, 0.35))


PAINTS = ['#ff6fd8', '#9b5cff', '#5bc8ff', '#7fe35a', '#ffd23a', '#ff8a1f', '#e040c0', '#2fc8c8']   # the paint colours, in order


def gun_paintball(paint='#ff6fd8'):
    """The paintball gun; the hopper, stripe and drips show the colour it will shoot next."""
    c = Canvas()
    g = Gun(c)
    g.poly([(4, -1), (9, -1), (7, -9), (3, -9)], '#2c2a40')            # grip
    g.rect(9.5, -3.5, 11, -1, '#8a8ea0')                                 # trigger guard
    g.tube(3, 21, 3.2, '#7b55e0')                                        # body
    g.rect(20, -1.8, 29, 1.8, '#a8acbc')                                 # barrel
    g.rect(20, 0.4, 29, 1.2, '#e8eaf4')
    g.rect(28, -2.2, 30, 2.2, '#5a5e70')
    g.rect(6, -0.6, 19, 0.6, paint)                                      # stripe in the paint's colour
    g.oval(12, 6.5, 4.2, 4.2, paint)                                     # paint hopper
    g.oval(11, 7.6, 1.6, 1.4, light(paint, 0.55))
    g.rect(10.5, 2.5, 13.5, 3.6, '#4a3a8a')
    for (u, v) in [(32.5, 3), (33, -2.5)]:
        x, y = g.p(u, v)
        c.shape('ellipse', (x - 1, y - 1, x + 1, y + 1), paint)
    return c.done()


def paintball_of(paint):
    c = Canvas(16, 16)
    c.ball(8, 8, 5.5, paint)
    return c.done()


def gun_bat_blaster():
    c = Canvas()
    g = Gun(c, ox=3, oy=27)
    g.poly([(4, -1), (9, -1), (7, -9), (3, -9)], '#2c2a40')
    g.tube(2, 19, 3.4, '#4a2f7a')
    g.poly([(18, -3.6), (18, 3.6), (27, 6.5), (27, -6.5)], '#5a3a96')      # flared bell
    g.poly([(18, 1), (18, 3.6), (27, 6.5), (27, 3.5)], '#8a64d0')
    g.rect(26, -6.5, 28, 6.5, '#2a1a4a')
    g.rect(27.5, -4.5, 28.2, 4.5, '#120a20')
    g.poly([(7, 3), (12, 3), (10, 8), (8, 6)], '#2a1a4a')                # bat wing fin
    g.poly([(12, 3), (16, 3), (15, 7)], '#2a1a4a')
    g.oval(9, -0.2, 1.1, 1.1, '#ff3a3a')
    g.oval(13, -0.2, 1.1, 1.1, '#ff3a3a')
    return c.done()


def gun_snow_cannon():
    c = Canvas()
    g = Gun(c)
    g.poly([(4, -1), (9, -1), (7, -8), (3, -8)], '#3a5a8a')
    g.tube(2, 26, 4, '#6ec8ff')
    g.rect(25, -4.6, 28, 4.6, '#d8f2ff')
    g.rect(27.4, -3, 28.2, 3, '#2a4a7a')
    for u in (8, 15, 21):                                                 # frosty bands
        g.rect(u, -4, u + 1.4, 4, '#ffffff')
    g.poly([(6, 4), (24, 4), (22, 6), (8, 6)], '#ffffff')                # snow on top
    for (x, y) in [(27, 4), (30, 9)]:
        c.line([(x - 2, y), (x + 2, y)], '#ffffff', 0.9)
        c.line([(x, y - 2), (x, y + 2)], '#ffffff', 0.9)
    return c.done()


def gun_pumpkin_launcher():
    c = Canvas()
    g = Gun(c, ox=3, oy=28)
    g.poly([(4, -1), (9, -1), (7, -8), (3, -8)], '#5a3a1e')
    g.tube(2, 19, 3.6, '#8b5a2b')
    g.rect(6, -3.6, 7.2, 3.6, '#ffcf5a')
    g.rect(14, -3.6, 15.2, 3.6, '#ffcf5a')
    g.rect(18, -4.4, 20, 4.4, '#5a3a1e')
    x, y = g.p(25, 0)
    pumpkin(c, x, y, 6.4)
    return c.done()


def gun_firework_launcher():
    c = Canvas()
    g = Gun(c, ox=3, oy=27)
    g.poly([(7, -1), (12, -1), (10, -8), (6, -8)], '#2c2a40')
    g.tube(0, 22, 3.6, '#e8443a')
    for u in (4, 10, 16):
        g.rect(u, -3.6, u + 2.4, 3.6, '#f4f4fa')
    g.rect(21, -4.2, 23, 4.2, '#8a8ea0')
    g.poly([(23, -2.6), (23, 2.6), (29, 0)], '#ffcf5a')                    # rocket nose poking out
    g.poly([(23, 0.6), (23, 2.6), (29, 0)], '#fff2a0')
    g.rect(0, -4.2, 1.6, 4.2, '#8a8ea0')
    for (u, v, col) in [(31, 4, '#ffe14d'), (31, -4, '#ff6fd8'), (33, 0, '#5b9dff')]:
        x, y = g.p(u, v)
        c.star(x, y, 1.8, col)
    return c.done()


def gun_grappling_hook():
    c = Canvas()
    g = Gun(c)
    g.poly([(4, -1), (9, -1), (7, -8), (3, -8)], '#2c2a40')
    g.tube(2, 18, 3, '#6a7086')
    g.rect(7, 3, 13, 5.5, '#a8743a')                                    # rope drum
    g.rect(7, 3.8, 13, 4.6, '#6b3e1e')
    g.rect(18, -0.9, 28, 0.9, '#c9ccd8')                                 # shaft
    g.rect(18, 0.1, 28, 0.9, '#f4f6ff')
    for k in (-1, 1):                                                     # two prongs curling back
        c.line([g.p(28, 0), g.p(24, k * 5)], '#e8eaf4', 1.5)
        c.line([g.p(24, k * 5), g.p(22.5, k * 3.6)], '#e8eaf4', 1.5)
    g.poly([(28, -1.6), (28, 1.6), (31.5, 0)], '#ffffff')
    return c.done()


def cloak_angel_wings2():
    c = Canvas()
    for side in (-1, 1):
        cx = 16 + side * 2
        pts = [(cx, 10), (cx + side * 9, 4), (cx + side * 14, 3), (cx + side * 13.5, 9), (cx + side * 12, 14),
               (cx + side * 9, 19), (cx + side * 5, 22), (cx, 18)]
        c.shape('poly', pts, '#f4f6ff')
        for k, (a, b) in enumerate([(5, 9), (9, 14), (12, 18)]):
            c.line([(cx + side * 1, a + 2), (cx + side * (13 - k * 3), b - 3)], '#c8d2ee', 0.9)
        c.shape('poly', [(cx, 10), (cx + side * 9, 4), (cx + side * 14, 3), (cx + side * 12, 6), (cx + side * 6, 8)], '#ffffff')
    head(c, x=16, y=19, r=5.2)
    return c.done()


def cloak_sun_rays2():
    c = Canvas()
    for i in range(12):
        a = math.radians(i * 30)
        l = 15.5 if i % 2 == 0 else 13
        c.shape('poly', [(16 + math.cos(a - 0.2) * 9, 15 + math.sin(a - 0.2) * 9), (16 + math.cos(a) * l, 15 + math.sin(a) * l),
                         (16 + math.cos(a + 0.2) * 9, 15 + math.sin(a + 0.2) * 9)], '#ffb428')
    c.shape('ellipse', (6, 5, 26, 25), '#ffd84a')
    c.shape('ellipse', (8, 7, 24, 23), '#ffe88a')
    head(c, x=16, y=17, r=5.6)
    return c.done()


def cloak_blossom2():
    c = Canvas()

    def flower(x, y, r, col, centre):
        for i in range(5):
            a = math.radians(-90 + i * 72)
            px_, py_ = x + math.cos(a) * r * 0.62, y + math.sin(a) * r * 0.62
            c.shape('ellipse', (px_ - r * 0.5, py_ - r * 0.5, px_ + r * 0.5, py_ + r * 0.5), col)
        c.shape('ellipse', (x - r * 0.32, y - r * 0.32, x + r * 0.32, y + r * 0.32), centre)
    flower(10, 10, 8, '#ffb3dc', '#ffe14d')
    flower(23, 8, 6, '#ff8cc8', '#ffffff')
    flower(21, 23, 7, '#ffc8e6', '#ffcf5a')
    flower(7, 25, 4.5, '#ff8cc8', '#ffffff')
    return c.done()


def cloak_spooky2():
    c = Canvas()
    body = [(9, 12), (10, 6), (16, 3), (22, 6), (23, 12), (24, 25), (21.5, 22.5), (19, 26), (16, 22.5), (13, 26), (10.5, 22.5), (8, 25)]
    c.shape('poly', body, '#f4f6ff')
    c.shape('poly', [(20, 6), (22, 6), (23, 12), (24, 25), (21.5, 22.5), (21, 10)], '#c8d2ee')
    c.shape('ellipse', (11.5, 9, 15, 14), '#1a1b26')
    c.shape('ellipse', (17, 9, 20.5, 14), '#1a1b26')
    c.shape('ellipse', (14, 16, 18, 20), '#2a2b3a')
    for (x, y, col) in [(4, 8, '#ff8a1f'), (28, 6, '#9b5cff'), (27, 26, '#ff8a1f'), (4, 26, '#9b5cff')]:
        c.shape('ellipse', (x - 1.6, y - 1.6, x + 1.6, y + 1.6), col)
    return c.done()


def gesture_facepalm2():
    c = Canvas()
    c.shape('rect', (8, 6, 24, 22), SKIN)
    c.shape('rect', (8, 6, 24, 10), '#5a3a1e')
    c.shape('rect', (11, 18, 21, 19.5), '#8a4a3a')
    # hand across the eyes, arm coming up from below
    c.shape('rect', (5, 11, 27, 16), '#f0bc94')
    for x in (10, 15, 20):
        c.line([(x, 11.5), (x, 15.5)], '#c88a5a', 0.6)
    c.shape('rect', (3, 13, 8, 30), SHIRT)
    c.shape('rect', (11, 23, 21, 30), SHIRT)
    for (x, y) in [(27, 4), (29.5, 7.5)]:
        c.line([(x, y), (x + 2, y - 2)], '#7fd7ff', 1)
    return c.done()


GADGETS.update({'paintball_gun': gun_paintball, 'bat_blaster': gun_bat_blaster, 'snow_cannon': gun_snow_cannon,
                'pumpkin_launcher': gun_pumpkin_launcher, 'firework_launcher': gun_firework_launcher,
                'grappling_hook': gun_grappling_hook, 'cookie_fountain': cookie})
GADGETS.pop('fake_tnt', None)   # shown as the game's own TNT block
CLOAKS.update({'angel_wings': cloak_angel_wings2, 'sun_rays': cloak_sun_rays2, 'blossom_aura': cloak_blossom2,
               'spooky_aura': cloak_spooky2})
GESTURES['facepalm'] = gesture_facepalm2
GUNS = ('paintball_gun', 'bat_blaster', 'snow_cannon', 'pumpkin_launcher', 'firework_launcher', 'grappling_hook')
WANDS = ('magic_wand', 'bubble_wand', 'blink_stick')


def gun_snow_cannon2():
    c = Canvas()
    g = Gun(c)
    g.poly([(5, -2), (10, -2), (8, -10), (4, -10)], '#2f4f86')                 # grip
    g.rect(10.5, -4.6, 12, -2, '#8a8ea0')
    g.tube(2, 24, 4, '#5ab8f0')
    g.rect(6, 1.8, 20, 3.2, '#e8f8ff')                                        # frosty shine
    g.rect(23, -5, 28, 5, '#2a5a9a')                                          # muzzle ring
    g.rect(23.6, -4.2, 27.4, 4.2, '#3f7fc8')
    g.rect(27.2, -3, 28, 3, '#0e1e3a')
    x, y = g.p(13, 0)
    for a in (0, 60, 120):                                                    # snowflake emblem
        r = math.radians(a)
        c.line([(x - math.cos(r) * 3, y - math.sin(r) * 3), (x + math.cos(r) * 3, y + math.sin(r) * 3)], '#ffffff', 0.9)
    for (u, v) in [(31, 3), (32, -3)]:
        x, y = g.p(u, v)
        c.shape('ellipse', (x - 1.4, y - 1.4, x + 1.4, y + 1.4), '#ffffff')
    return c.done()


def gesture_clap2():
    c = Canvas()
    for side in (-1, 1):
        x = 16 + side * 0.4
        palm = [(x, 8), (x + side * 7, 10), (x + side * 8, 20), (x + side * 5, 26), (x, 26)]
        c.shape('poly', palm, '#f0bc94')
        c.shape('poly', [(x, 8), (x + side * 2.5, 8.6), (x + side * 2.5, 26), (x, 26)], '#d89a70')
        for k in range(3):                                                     # fingers, with gaps between
            fx = x + side * (1.2 + k * 2.2)
            c.shape('rect', (min(fx, fx + side * 1.8), 3 + k * 1.2, max(fx, fx + side * 1.8), 12), '#f0bc94')
            gx = fx + side * 1.9
            c.line([(gx, 5 + k * 1.2), (gx, 12)], '#b87850', 0.5)
        c.shape('rect', (min(x + side * 1.5, x + side * 8), 25, max(x + side * 1.5, x + side * 8), 30), SHIRT)
    for (x0, y0, x1, y1) in [(5, 6, 2, 3), (27, 6, 30, 3), (4, 12, 1, 12), (28, 12, 31, 12)]:
        c.line([(x0, y0), (x1, y1)], '#ffe14d', 1.1)
    return c.done()


def gesture_facepalm3():
    c = Canvas()
    c.shape('rect', (7, 5, 25, 23), SKIN)                                    # head
    c.shape('rect', (7, 5, 25, 9), '#5a3a1e')
    c.shape('rect', (12, 19, 20, 20.5), '#8a4a3a')                           # mouth
    c.shape('poly', [(20, 31), (27, 31), (27, 20), (22, 16)], SHIRT)          # arm from below
    c.shape('poly', [(5, 9), (19, 9), (24, 15), (21, 18), (5, 16)], '#f0bc94')  # palm over the eyes
    c.shape('poly', [(19, 9), (24, 15), (21, 18), (17, 14)], '#d89a70')
    for y in (11, 13.5):
        c.line([(6, y), (16, y)], '#c88a5a', 0.6)
    c.line([(27.5, 3), (29.5, 1)], '#7fd7ff', 1)
    c.line([(29, 7), (31, 5)], '#7fd7ff', 1)
    return c.done()


GADGETS['snow_cannon'] = gun_snow_cannon2
GESTURES['clap'] = gesture_clap2
GESTURES['facepalm'] = gesture_facepalm3


# ---------------------------------------------------------------- 1.39: cloak icons show just the cloak

def cloak_sparkle3():
    c = Canvas()
    for (x, y, r, col) in [(11, 12, 8, '#ffffff'), (23, 8, 5, '#ff9ede'), (22, 23, 6.5, '#ffd0f4'), (7, 25, 3.5, '#ff9ede'),
                           (27, 16, 2.5, '#ffffff'), (5, 4, 2.5, '#ffd0f4')]:
        c.star(x, y, r, col, inner=0.26)
        c.star(x, y, r * 0.4, '#ffffff', inner=0.3, rot=-45)
    return c.done()


def cloak_halo3():
    c = Canvas()
    c.shape('ellipse', (2, 9, 30, 23), '#c9963a')
    c.shape('ellipse', (2, 8, 30, 21), '#ffcf5a')
    c.shape('ellipse', (3, 8.5, 29, 15), '#ffe88a')
    c.erase('ellipse', (8, 11.5, 24, 17.5))
    for (x, y) in [(6, 5), (27, 4), (16, 26)]:
        c.star(x, y, 2.4, '#fff6c0', inner=0.3)
    return c.done()


def cloak_wings3():
    c = Canvas()
    for side in (-1, 1):
        def P(x, y):
            return (16 + side * x, y)
        main = [P(1, 13), P(4, 8), P(9, 4), P(14, 2), P(15.5, 4), P(15, 9), P(13.5, 14), P(11, 19), P(8, 23), P(5, 25.5), P(2, 23), P(1, 18)]
        c.shape('poly', main, '#bcc8ea')
        for (x, y, r) in [(13.2, 13, 2.6), (11, 17.5, 2.6), (8.3, 21.5, 2.6), (5, 24, 2.4)]:
            px_, py_ = P(x, y)
            c.shape('ellipse', (px_ - r, py_ - r, px_ + r, py_ + r), '#bcc8ea')
            c.shape('arc', ((px_ - r, py_ - r, px_ + r, py_ + r), 20, 160), '#8e9cc8', width=0.7)
        mid = [P(1, 13), P(4, 8), P(9, 4), P(14, 2), P(15, 5), P(13.5, 10), P(10, 15), P(6, 19), P(2, 19)]
        c.shape('poly', mid, '#dfe6f8')
        top = [P(1, 13), P(4, 8), P(9, 4), P(14, 2), P(14.5, 4.5), P(10, 8), P(5.5, 12), P(2, 15)]
        c.shape('poly', top, '#ffffff')
    return c.done()


def cloak_sun3():
    c = Canvas()
    for i in range(12):
        a = math.radians(i * 30 + 15)
        l = 15.5 if i % 2 == 0 else 12.5
        c.shape('poly', [(16 + math.cos(a - 0.22) * 8, 16 + math.sin(a - 0.22) * 8), (16 + math.cos(a) * l, 16 + math.sin(a) * l),
                         (16 + math.cos(a + 0.22) * 8, 16 + math.sin(a + 0.22) * 8)], ('lin', '#fff2a0', '#ff9a2e', 90))
    c.shape('ellipse', (7, 7, 25, 25), '#ffb428')
    c.shape('ellipse', (8, 8, 24, 23), '#ffd84a')
    c.shape('ellipse', (10, 9.5, 19, 17), '#fff2a0')
    return c.done()


CLOAKS.update({'sparkle_aura': cloak_sparkle3, 'halo': cloak_halo3, 'angel_wings': cloak_wings3, 'sun_rays': cloak_sun3})
GUNS = GUNS + ('party_popper',)


def menu_take_off():
    """Take off: an empty coat hanger with a red cross."""
    c = Canvas()
    c.shape('arc', ((13, 2, 19, 8), 180, 450), '#c9ccd8', width=1.6)        # the hook
    c.line([(16, 8), (16, 10)], '#c9ccd8', 1.6)
    c.line([(16, 10), (3, 20)], '#a8743a', 2.4)                             # the wooden bar
    c.line([(16, 10), (29, 20)], '#a8743a', 2.4)
    c.line([(3.5, 20.5), (28.5, 20.5)], '#8b5a2b', 1.8)
    c.line([(6, 11.5), (14, 6)], '#d9a05a', 0.6, opacity=0.0)
    c.shape('ellipse', (17, 17, 31, 31), '#c8322e')                        # red badge with a white X
    c.shape('ellipse', (18, 17.5, 30, 29), '#e8443a')
    c.line([(20.5, 20.5), (27.5, 27.5)], '#ffffff', 1.8)
    c.line([(27.5, 20.5), (20.5, 27.5)], '#ffffff', 1.8)
    return c.done()


def sun_ring():
    """The sun's rays (no middle): long and short rays alternating, warm yellow to orange."""
    c = Canvas()
    for i in range(12):
        a = math.radians(i * 30)
        l = 15.5 if i % 2 == 0 else 13
        w = 0.26 if i % 2 == 0 else 0.2
        c.shape('poly', [(16 + math.cos(a - w) * 8.5, 16 + math.sin(a - w) * 8.5), (16 + math.cos(a) * l, 16 + math.sin(a) * l),
                         (16 + math.cos(a + w) * 8.5, 16 + math.sin(a + w) * 8.5)], '#ffcf3a' if i % 2 == 0 else '#ffa82a')
    c.shape('arc', ((7, 7, 25, 25), 0, 360), '#ffe88a', width=1.2)
    c.erase('ellipse', (8.5, 8.5, 23.5, 23.5))
    return c.done(outline=False)


def cloak_spooky3():
    """Spooky Aura: bats with orange sparks."""
    c = Canvas()
    bat(c, 11, 11, 2.0, col='#2a1f3a', eyes=False)
    bat(c, 23, 20, 1.5, col='#3a2c52', eyes=False)
    bat(c, 22, 6, 1.0, col='#3a2c52', eyes=False)
    for (x, y, r) in [(6, 24, 1.6), (13, 27, 1.2), (28, 12, 1.2), (4, 5, 1.0), (17, 18, 0.9)]:
        c.shape('ellipse', (x - r, y - r, x + r, y + r), '#ff8a1f')
        c.shape('ellipse', (x - r * 0.4, y - r * 0.4, x + r * 0.4, y + r * 0.4), '#ffd27f')
    for (x, y) in [(10.2, 10.6), (11.8, 10.6)]:
        c.shape('rect', (x - 0.5, y - 0.5, x + 0.5, y + 0.5), '#ff8a1f')
    return c.done()


CLOAKS['spooky_aura'] = cloak_spooky3


def bat2(c, x, y, s, col='#2a1f3a', eye='#ff8a1f'):
    """A clean bat silhouette: wings spread, three scallops under each wing, two ears."""
    for side in (-1, 1):
        def P(px_, py_):
            return (x + side * px_ * s, y + py_ * s)
        wing = [P(0.8, -0.8), P(2.6, -3.0), P(5.0, -4.6), P(7.4, -4.4), P(8.4, -2.0), P(8.4, 2.6), P(7.2, 1.4), P(6.0, 3.0),
                P(4.8, 1.6), P(3.4, 3.0), P(2.2, 1.6), P(0.8, 1.6)]
        c.shape('poly', wing, col)
        c.line([P(1.0, -0.6), P(5.0, -4.2)], light(col, 0.25), 0.45 * s)
        c.line([P(5.0, -4.2), P(6.0, 2.6)], light(col, 0.12), 0.35 * s)
    c.shape('ellipse', (x - 1.4 * s, y - 1.4 * s, x + 1.4 * s, y + 2.2 * s), col)
    for side in (-1, 1):
        c.shape('poly', [(x + side * 0.4 * s, y - 1.0 * s), (x + side * 1.3 * s, y - 2.6 * s), (x + side * 1.4 * s, y - 0.6 * s)], col)
    if eye:
        for side in (-1, 1):
            c.shape('rect', (x + side * 0.7 * s - 0.4 * s, y - 0.3 * s, x + side * 0.7 * s + 0.3 * s, y + 0.4 * s), eye)


def cloak_spooky4():
    c = Canvas()
    bat2(c, 13, 13, 1.45, col='#4a3a66')
    bat2(c, 24, 24, 0.9, col='#5a4878')
    for (x, y, r) in [(5, 25, 1.6), (14, 27, 1.2), (28, 7, 1.4), (24, 14, 1.0), (4, 5, 1.0)]:
        c.shape('ellipse', (x - r, y - r, x + r, y + r), '#ff8a1f')
        c.shape('ellipse', (x - r * 0.45, y - r * 0.45, x + r * 0.45, y + r * 0.45), '#ffd27f')
    return c.done()


CLOAKS['spooky_aura'] = cloak_spooky4


# ---------------------------------------------------------------- 1.41: category icons, Halloween tab, trampoline

def category_suits():
    """Suits: a hero's suit top on a hanger (red, blue trim, gold star)."""
    c = Canvas()
    c.shape('arc', ((13.5, 1, 18.5, 6), 180, 450), '#c9ccd8', width=1.4)
    c.line([(16, 6), (16, 7.5)], '#c9ccd8', 1.4)
    shirt = [(10, 8), (13, 7.5), (16, 9.5), (19, 7.5), (22, 8), (29, 12), (27, 18), (24, 16.5), (24, 29), (8, 29), (8, 16.5), (5, 18), (3, 12)]
    c.shape('poly', shirt, '#d83a34')
    c.shape('poly', [(24, 16.5), (24, 29), (21, 29), (21, 17)], '#a82a28')                     # shade
    c.shape('poly', [(3, 12), (10, 8), (8, 16.5), (5, 18)], '#3c6ae0')                       # sleeves
    c.shape('poly', [(29, 12), (22, 8), (24, 16.5), (27, 18)], '#2c52b8')
    c.shape('rect', (8, 25, 24, 27), '#3c6ae0')                                             # belt
    c.shape('rect', (14.5, 24.6, 17.5, 27.4), '#ffcf5a')
    c.star(16, 16.5, 4.4, '#ffcf5a', points=5, inner=0.45)
    return c.done()


def category_trails():
    """Trails: a sneaker running, a rainbow streak of sparkles behind it."""
    c = Canvas()
    for k, col in enumerate(('#ff6a50', '#ffcf3a', '#5be37c', '#5b9dff', '#b266ff')):
        y = 15 + k * 2
        c.shape('rect', (1 + k * 0.6, y, 14, y + 2), col)
    for (x, y, r, col) in [(4, 9, 2.2, '#ffffff'), (9, 6, 1.6, '#ffe88a'), (3, 27, 1.6, '#ffffff')]:
        c.star(x, y, r, col, inner=0.3)
    # the shoe
    c.shape('poly', [(13, 12), (20, 12), (21, 16), (27, 18), (30, 21), (30, 25), (13, 25)], '#f4f6ff')
    c.shape('poly', [(13, 12), (20, 12), (21, 16), (27, 18), (29, 20), (13, 20)], '#e8443a')
    c.shape('rect', (13, 25, 30.5, 27.5), '#5a6078')                                        # sole
    for x in (17, 20, 23):
        c.line([(x, 17.5), (x + 1.6, 16)], '#ffffff', 0.7)                                   # laces
    c.shape('rect', (13, 21, 30, 22.4), '#c9ccd8')
    return c.done()


def season_halloween2():
    """Halloween: a jack-o'-lantern with a classic carved face, lit from inside."""
    c = Canvas()
    c.shape('rect', (14.5, 2, 17.5, 7), '#5a4a22')
    c.shape('poly', [(17.5, 4), (23, 2.5), (21.5, 6)], '#4caa3c')
    body = (2.5, 6, 29.5, 29)
    c.shape('ellipse', body, '#ec7a1c')
    for (x0, x1) in [(6, 14), (12, 20), (18, 26)]:
        c.shape('ellipse', (x0, 6.4, x1, 28.6), '#f59a2e')
    for x in (10, 16, 22):
        c.line([(x, 8), (x, 27)], '#c8601a', 0.9)
    lit = '#ffe14d'
    c.shape('poly', [(7.5, 16), (13.5, 16), (10.5, 11)], lit)                               # eyes
    c.shape('poly', [(18.5, 16), (24.5, 16), (21.5, 11)], lit)
    c.shape('poly', [(14.5, 19.5), (17.5, 19.5), (16, 17)], lit)                            # nose
    c.shape('poly', [(7, 21), (25, 21), (23, 25.5), (20, 23.5), (16, 26), (12, 23.5), (9, 25.5)], lit)   # grin
    return c.done()


def trampoline2():
    """Trampoline: a round blue mat on a yellow frame with black legs, seen slightly from above."""
    c = Canvas()
    for x in (6, 16, 26):
        c.shape('rect', (x - 1.2, 17, x + 1.2, 28), '#3a3d50')
        c.shape('rect', (x - 2.2, 27, x + 2.2, 29), '#2a2c3a')
    c.shape('ellipse', (1, 9, 31, 22), '#e0a020')
    c.shape('ellipse', (1, 8, 31, 20.5), '#ffcf3a')
    c.shape('ellipse', (3.5, 9.5, 28.5, 19), '#2f6fe0')
    c.shape('ellipse', (8, 11, 24, 17), '#4a8cff')
    im = c.done()                                    # (no spring or bounce lines: just the trampoline, centred)
    from PIL import Image as _Image
    b = im.getbbox()
    out = _Image.new('RGBA', im.size, (0, 0, 0, 0))
    out.alpha_composite(im, (round(16 - (b[0] + b[2]) / 2), round(16 - (b[1] + b[3]) / 2)))
    return out


SEASONS['halloween'] = season_halloween2
GADGETS['trampoline'] = trampoline2


# ---------------------------------------------------------------- 1.41: more gestures (a little poseable player)

def person(c, x, y, s=1.0, arms=((-5, 6), (5, 6)), legs=((-2, 15), (2, 15)), shirt=SHIRT, pants=PANTS, skin=SKIN,
           horizontal=False, face=True):
    """A blocky player. x, y: top of the head; limb ends are offsets from the shoulders / hips (in pixels at s=1)."""
    if horizontal:   # lying down: head on the left
        c.shape('rect', (x, y, x + 6 * s, y + 6 * s), skin)
        c.shape('rect', (x, y, x + 6 * s, y + 1.6 * s), '#5a3a1e')
        c.shape('rect', (x + 6 * s, y + 0.5 * s, x + 15 * s, y + 5.5 * s), shirt)
        c.shape('rect', (x + 15 * s, y + 0.8 * s, x + 23 * s, y + 5.2 * s), pants)
        return
    hx = x
    sh = (x, y + 7.5 * s)          # shoulders
    hp = (x, y + 15 * s)           # hips
    for (ex, ey) in legs:
        c.line([(hp[0] + (1.2 if ex > 0 else -1.2) * s, hp[1]), (hp[0] + ex * s, hp[1] + (ey - 15 + 7) * s)], pants, 2.6 * s)
    c.shape('rect', (x - 3.2 * s, y + 6.5 * s, x + 3.2 * s, y + 15.5 * s), shirt)
    for (ex, ey) in arms:
        c.line([(sh[0] + (2.6 if ex > 0 else -2.6) * s, sh[1]), (sh[0] + ex * s, sh[1] + (ey - 6) * s)], shirt, 2.2 * s)
        c.shape('ellipse', (sh[0] + ex * s - 1.2 * s, sh[1] + (ey - 6) * s - 1.2 * s, sh[0] + ex * s + 1.2 * s, sh[1] + (ey - 6) * s + 1.2 * s), skin)
    c.shape('rect', (hx - 3.2 * s, y, hx + 3.2 * s, y + 6.4 * s), skin)
    c.shape('rect', (hx - 3.2 * s, y, hx + 3.2 * s, y + 1.8 * s), '#5a3a1e')
    if face:
        c.shape('rect', (hx - 2 * s, y + 3 * s, hx - 0.9 * s, y + 4.1 * s), '#2a2b3a')
        c.shape('rect', (hx + 0.9 * s, y + 3 * s, hx + 2 * s, y + 4.1 * s), '#2a2b3a')


def g_dab():
    c = Canvas()
    person(c, 15, 5, arms=((-9, -1), (-4, 3)))
    for (x, y) in [(26, 6), (28, 11), (25, 16)]:
        c.star(x, y, 1.8, '#ffe14d', inner=0.35)
    return c.done()


def g_floss():
    c = Canvas()
    person(c, 16, 4, arms=((-7, 13), (-3, 14)))
    for (x0, y0, x1, y1) in [(5, 18, 2, 18), (5, 21, 2, 21), (27, 18, 30, 18), (27, 21, 30, 21)]:
        c.line([(x0, y0), (x1, y1)], '#ffffff', 0.8)
    return c.done()


def g_moonwalk():
    c = Canvas()
    person(c, 18, 4, arms=((-4, 12), (4, 12)), legs=((-4, 15), (3, 14)))
    for (x, y) in [(7, 12), (5, 18)]:
        c.shape('poly', [(x, y), (x + 3, y - 2.2), (x + 3, y + 2.2)], '#ffcf5a')
        c.line([(x + 2, y), (x + 6, y)], '#ffcf5a', 1)
    c.shape('rect', (25, 3, 26.2, 9), PURPLE)
    c.shape('ellipse', (22.4, 7.6, 26.2, 10.4), PURPLE)
    c.shape('rect', (8, 29, 28, 30), '#5a6078')
    return c.done()


def g_twirl_jump():
    c = Canvas()
    person(c, 16, 2, arms=((-6, 0), (6, 0)), legs=((-3, 13), (3, 13)))
    c.shape('arc', ((4, 9, 28, 17), 20, 160), '#ffcf5a', width=1.2)
    c.shape('arc', ((4, 9, 28, 17), 200, 340), '#ffcf5a', width=1.2, opacity=0.6)
    for (x, r) in [(9, 2.2), (16, 2.8), (23, 2.2)]:
        c.shape('ellipse', (x - r, 27, x + r, 30.5), '#c9ccd8')
    return c.done()


def g_levitate():
    c = Canvas()
    c.shape('ellipse', (5, 26, 27, 30.5), '#9b5cff')
    c.shape('ellipse', (9, 27, 23, 29.5), '#c79bff')
    person(c, 16, 2, arms=((-6, 9), (6, 9)), legs=((-2, 13), (2, 13)))
    for (x, y) in [(5, 8), (27, 6), (4, 17), (28, 18)]:
        c.star(x, y, 1.8, '#e0c8ff', inner=0.3)
    return c.done()


def g_zombie():
    c = Canvas()
    person(c, 13, 4, arms=((9, 7), (11, 9)), skin='#6cb85a', shirt='#2f8fa8', pants='#3a3a8a')
    for (x, y) in [(23, 3), (27, 7)]:
        c.shape('rect', (x, y, x + 2, y + 2), '#6cb85a')
    return c.done()


def g_explode():
    c = Canvas()
    for i in range(10):
        a = math.radians(i * 36 + 10)
        c.shape('poly', [(16 + math.cos(a - 0.3) * 6, 16 + math.sin(a - 0.3) * 6), (16 + math.cos(a) * 15, 16 + math.sin(a) * 15),
                         (16 + math.cos(a + 0.3) * 6, 16 + math.sin(a + 0.3) * 6)], '#ff8a1f' if i % 2 else '#ffcf3a')
    c.shape('ellipse', (8, 8, 24, 24), '#ffe14d')
    c.shape('ellipse', (11, 11, 21, 21), '#ffffff')
    person(c, 16, 9, s=0.62, arms=((-6, 2), (6, 2)), legs=((-4, 15), (4, 15)))
    return c.done()


def g_sleep():
    c = Canvas()
    c.shape('rect', (3, 22, 29, 25), '#c8322e')
    c.shape('rect', (3, 25, 29, 27), '#8b5a2b')
    c.shape('rect', (3, 19, 9, 22), '#f4f4fa')
    person(c, 4, 15.5, s=1.0, horizontal=True)
    for (x, y, k) in [(15, 12, 0.8), (21, 7.5, 1.0), (27.5, 3, 1.25)]:
        w = 2.0 * k
        c.shape('rect', (x - w, y - w, x + w, y - w + 1.1), '#c8e8ff')
        c.shape('rect', (x - w, y + w - 1.1, x + w, y + w), '#c8e8ff')
        c.line([(x + w - 0.5, y - w + 0.8), (x - w + 0.5, y + w - 0.8)], '#c8e8ff', 1.1)
    return c.done()


def g_swim():
    c = Canvas()
    for (x0, x1, y) in [(2, 30, 26), (5, 27, 29)]:
        c.line([(x0, y), (x1, y)], '#5bb8ff', 1.4)
    person(c, 3, 12, horizontal=True)
    c.line([(12, 13), (8, 8)], SHIRT, 2.2)
    for (x, y, r) in [(27, 9, 1.8), (24, 5, 1.2), (29, 3, 1.0)]:
        c.shape('arc', ((x - r, y - r, x + r, y + r), 0, 360), '#c8e8ff', width=0.8)
    return c.done()


def g_giant():
    c = Canvas()
    person(c, 19, 2, s=1.12, arms=((-6, 13), (6, 13)), legs=((-3, 15), (3, 15)))
    person(c, 6, 18, s=0.42, arms=((-5, 12), (5, 12)), face=False)
    for x in (3, 9):
        c.shape('poly', [(x, 6), (x + 2.5, 2), (x + 5, 6)], '#ffcf5a')
    return c.done()


def g_tiny():
    c = Canvas()
    person(c, 16, 16, s=0.55, arms=((-5, 12), (5, 12)))
    for (x, y) in [(6, 6), (26, 6)]:
        c.shape('poly', [(x - 2.5, y), (x + 2.5, y), (x, y + 4)], '#7fd7ff')
    c.shape('arc', ((6, 6, 26, 26), 200, 340), '#c9ccd8', width=0.8)
    return c.done()


def g_magic_trick():
    c = Canvas()
    for (x, y, r) in [(10, 22, 6), (19, 20, 7), (25, 24, 4.5)]:
        c.shape('ellipse', (x - r, y - r, x + r, y + r), '#e2e6f2')
    c.shape('ellipse', (8, 13, 24, 17), '#1c1c24')                     # hat brim
    c.shape('rect', (11, 3, 21, 15), '#2a2a36')
    c.shape('rect', (11, 11, 21, 13), '#9b5cff')
    for (x, y, r) in [(5, 6, 2.4), (27, 4, 2.0), (28, 14, 1.6)]:
        c.star(x, y, r, '#ffe14d', inner=0.3)
    return c.done()


GESTURES.update({'dab': g_dab, 'floss': g_floss, 'moonwalk': g_moonwalk, 'twirl_jump': g_twirl_jump, 'levitate': g_levitate,
                 'zombie_walk': g_zombie, 'explode': g_explode, 'sleep': g_sleep, 'swim': g_swim, 'giant': g_giant,
                 'tiny': g_tiny, 'magic_trick': g_magic_trick})


# ---------------------------------------------------------------- 1.41: the new gadgets (32x32)

def gi_hot_potato():
    c = Canvas()
    for i, (x0, y0, x1, y1) in enumerate([(6, 8, 3, 3), (10, 6, 9, 1), (21, 6, 23, 1), (25, 9, 29, 4)]):
        c.line([(x0, y0), (x1, y1)], '#ff8a1f' if i % 2 else '#ffcf3a', 1.2)
    c.shape('ellipse', (4, 9, 28, 28), '#c48a44')
    c.shape('ellipse', (5, 9.5, 24, 24), '#e0a85a')
    c.shape('ellipse', (8, 12, 15, 17), '#f2c88a')
    for (x, y) in [(18, 14), (12, 21), (22, 22), (9, 16.5)]:
        c.shape('ellipse', (x - 0.9, y - 0.7, x + 0.9, y + 0.7), '#8a5a2a')
    c.shape('ellipse', (19, 4, 31, 13), '#ff6a3a')                   # a little flame on top
    c.shape('ellipse', (21.5, 6, 28.5, 12), '#ffcf3a')
    return c.done()


def gi_magic_8_ball():
    c = Canvas()
    c.ball(16, 16, 13, '#2a2a36')
    c.shape('ellipse', (9, 8, 23, 22), '#ffffff')
    c.shape('ellipse', (11, 10, 21, 20), '#f2f2f8')
    c.shape('ellipse', (13, 10.5, 19, 15), '#1a1b26')                 # the 8: two rings
    c.shape('ellipse', (14.3, 11.6, 17.7, 13.9), '#f2f2f8')
    c.shape('ellipse', (12.5, 14.5, 19.5, 19.5), '#1a1b26')
    c.shape('ellipse', (14, 15.8, 18, 18.2), '#f2f2f8')
    return c.done()


def gi_fortune_cookie():
    c = Canvas()
    c.shape('poly', [(3, 22), (9, 9), (16, 6), (23, 9), (29, 22), (23, 18), (16, 24), (9, 18)], '#e8a84a')
    c.shape('poly', [(3, 22), (9, 9), (16, 6), (16, 24), (9, 18)], '#f4c070')
    c.shape('poly', [(9, 9), (16, 6), (14, 12)], '#fbe0a8')
    c.shape('poly', [(16, 24), (23, 18), (29, 22), (20, 26)], '#c98a2e')
    c.shape('poly', [(14, 15), (28, 7), (30, 10), (16, 18)], '#ffffff')    # the paper slip
    for x in (19, 22, 25):
        c.line([(x, 12.5 - (x - 19) * 0.45), (x + 1.4, 11.8 - (x - 19) * 0.45)], '#e03a3a', 0.6)
    return c.done()


def gi_tetherball():
    c = Canvas()
    c.shape('rect', (14.5, 4, 17.5, 29), '#c9ccd8')
    c.shape('rect', (14.5, 4, 15.5, 29), '#ffffff')
    c.shape('rect', (10, 28, 22, 31), '#5a6078')
    c.line([(16, 6), (25, 14)], '#e8e8f0', 0.8)
    c.ball(25, 17, 5, '#ff6a3a')
    c.shape('arc', ((20, 12, 30, 22), 200, 330), '#ffffff', width=0.7)
    return c.done()


def gi_rps():
    c = Canvas()
    # a fist (rock), a flat hand (paper) and two fingers (shears), in a little fan
    c.shape('rrect', (2, 15, 12, 25, 2), '#f0bc94')
    for y in (17, 19.5, 22):
        c.line([(3, y), (8, y)], '#c88a5a', 0.6)
    c.shape('rect', (12, 6, 21, 18), '#f0bc94')
    for x in (14, 16.5, 19):
        c.line([(x, 6), (x, 12)], '#c88a5a', 0.6)
    c.shape('rect', (11, 17, 22, 21), SHIRT)
    c.shape('rect', (23, 16, 30, 25), '#f0bc94')
    c.line([(25, 16), (23, 6)], '#f0bc94', 2.0)
    c.line([(28, 16), (30, 6)], '#f0bc94', 2.0)
    for (x, y) in [(5, 6), (27, 30)]:
        c.star(x, y, 2, '#ffcf5a', inner=0.35)
    return c.done()


def gi_catapult():
    c = Canvas()
    c.shape('rect', (3, 25, 29, 28), '#8b5a2b')
    c.shape('ellipse', (4, 25, 10, 31), '#5a3a1e')
    c.shape('ellipse', (22, 25, 28, 31), '#5a3a1e')
    c.line([(16, 25), (11, 13)], '#a8743a', 2.4)                     # the arm
    c.shape('rect', (13, 17, 19, 25), '#7a4a24')
    c.shape('ellipse', (6, 9, 14, 15), '#6b3e1e')                      # the cup
    # a cat flying out of it
    c.shape('ellipse', (17, 3, 27, 10), '#ff9a2e')
    c.shape('poly', [(18, 4), (19, 1), (21, 3.5)], '#ff9a2e')
    c.shape('poly', [(24, 3.5), (26, 1), (26.5, 4.5)], '#ff9a2e')
    c.shape('rect', (19.5, 5.6, 20.7, 6.8), '#1a1b26')
    c.shape('rect', (23.5, 5.6, 24.7, 6.8), '#1a1b26')
    c.line([(27, 7), (30.5, 10)], '#ff9a2e', 1.2)
    return c.done()


def gi_pigs_fly():
    c = Canvas()
    for side in (-1, 1):                                              # little white wings
        c.shape('poly', [(16 + side * 3, 13), (16 + side * 13, 6), (16 + side * 12, 12), (16 + side * 6, 17)], '#ffffff')
        c.line([(16 + side * 5, 13), (16 + side * 11, 9)], '#c8d2ee', 0.6)
    c.shape('rrect', (7, 12, 25, 25, 3), '#f2a0b0')
    c.shape('rrect', (7, 12, 25, 16, 2), '#f8c0cc')
    c.shape('rrect', (12, 18, 20, 23.5, 1.5), '#e07890')                 # snout
    c.shape('rect', (13.6, 19.8, 14.8, 21.4), '#8a3a4a')
    c.shape('rect', (17.2, 19.8, 18.4, 21.4), '#8a3a4a')
    c.shape('rect', (9.5, 14.5, 11.5, 16.5), '#1a1b26')
    c.shape('rect', (20.5, 14.5, 22.5, 16.5), '#1a1b26')
    for (x, y) in [(4, 28), (28, 28)]:
        c.line([(x - 2, y), (x + 2, y)], '#ffffff', 0.9)
    return c.done()


def gi_creeper_astronaut():
    c = Canvas()
    for (x, y) in [(13, 28), (16, 30), (19, 28)]:
        c.shape('ellipse', (x - 2, y - 2, x + 2, y + 2), '#ff8a1f')
    c.shape('rect', (11, 15, 21, 27), '#5bb84a')                        # body
    c.shape('ellipse', (6, 2, 26, 21), '#bfe8ff', opacity=0.9)          # glass helmet
    c.shape('rect', (9, 5, 23, 18), '#6cc85a')                          # creeper head
    c.shape('rect', (11, 8, 14, 11), '#1a1b26')
    c.shape('rect', (18, 8, 21, 11), '#1a1b26')
    c.shape('rect', (14, 11, 18, 15), '#1a1b26')
    c.shape('rect', (12.5, 13, 14, 16), '#1a1b26')
    c.shape('rect', (18, 13, 19.5, 16), '#1a1b26')
    c.shape('arc', ((6, 2, 26, 21), 200, 260), '#ffffff', width=1)
    return c.done()


def gi_exploding_sheep():
    c = Canvas()
    cols = ['#ff6a5c', '#ffcf3a', '#5be37c', '#5b9dff', '#b266ff']
    for i, col in enumerate(cols):
        c.shape('rect', (5 + i * 4.4, 8, 9.6 + i * 4.4, 23), col)
    c.shape('rrect', (5, 7, 27, 23, 4), (0, 0, 0, 0))
    c.shape('rect', (1, 11, 8, 19), '#3a3a44')                          # face
    c.shape('rect', (2, 13, 3.5, 14.5), '#ffffff')
    for x in (8, 13, 19, 24):
        c.shape('rect', (x, 23, x + 2.4, 29), '#3a3a44')
    return c.done()


def gi_duckling():
    c = Canvas()
    c.shape('ellipse', (6, 14, 26, 27), '#ffe14d')
    c.shape('ellipse', (14, 6, 26, 17), '#ffe14d')
    c.shape('ellipse', (7, 15, 18, 23), '#ffef8a')
    c.shape('poly', [(25, 11), (31, 12.5), (25, 14.5)], '#ff8a1f')
    c.shape('rect', (20.5, 9.5, 22, 11), '#1a1b26')
    c.shape('poly', [(8, 17), (14, 18), (11, 22)], '#f2c42a')              # wing
    c.shape('rect', (12, 27, 14, 30), '#ff8a1f')
    c.shape('rect', (18, 27, 20, 30), '#ff8a1f')
    return c.done()


def gi_pinata():
    c = Canvas()
    c.line([(16, 1), (16, 6)], '#e8e8f0', 0.8)
    stripes = ['#ff6fd8', '#ffcf3a', '#5be37c', '#5b9dff', '#ff8a1f']
    # a donkey: body, neck, head, legs
    for i, col in enumerate(stripes):
        c.shape('rect', (6, 12 + i * 2, 22, 14 + i * 2), col)
    c.shape('rect', (18, 4, 24, 13), '#ff6fd8')
    c.shape('rect', (20, 6, 29, 11), '#ffcf3a')
    c.shape('poly', [(19, 4), (19.5, 0.5), (21, 4)], '#5be37c')
    c.shape('rect', (25, 7, 26.2, 8.2), '#1a1b26')
    for x in (7, 11, 16, 20):
        c.shape('rect', (x, 22, x + 2, 28), stripes[(x // 4) % 5])
    for k in range(3):
        c.line([(6, 13 + k * 3), (3, 14 + k * 3)], stripes[k + 1], 0.9)
    return c.done()


def gi_jetpack():
    c = Canvas()
    for x in (8, 18):
        c.shape('rrect', (x, 5, x + 7, 22, 3), '#c8322e')
        c.shape('rect', (x, 9, x + 7, 11), '#e8e8f0')
        c.shape('rect', (x + 1, 22, x + 6, 24), '#5a6078')
        c.shape('poly', [(x + 1, 24), (x + 6, 24), (x + 3.5, 31)], '#ff8a1f')
        c.shape('poly', [(x + 2.2, 24), (x + 4.8, 24), (x + 3.5, 28.5)], '#ffe14d')
        c.shape('rect', (x + 1, 6, x + 2.4, 18), '#e8504a')
    c.shape('rect', (14, 9, 19, 16), '#5a6078')
    return c.done()


def gi_mob_gun():
    c = Canvas()
    g = Gun(c)
    g.poly([(4, -1), (9, -1), (7, -9), (3, -9)], '#2c2a40')
    g.tube(2, 20, 3.4, '#4caa3c')
    g.rect(19, -4.4, 26, 4.4, '#2f7a28')
    g.rect(25.4, -3.2, 26.4, 3.2, '#12200f')
    g.rect(7, 3.2, 14, 6.5, '#6b3e1e')                                   # ammo: a little pig face on top
    x, y = g.p(26, 9)
    c.shape('rrect', (x - 4, y - 4, x + 4, y + 3, 1.5), '#f2a0b0')
    c.shape('rect', (x - 2, y - 0.5, x + 2, y + 2), '#e07890')
    c.shape('rect', (x - 3, y - 3, x - 1.8, y - 1.8), '#1a1b26')
    c.shape('rect', (x + 1.8, y - 3, x + 3, y - 1.8), '#1a1b26')
    return c.done()


def gi_railgun():
    c = Canvas()
    g = Gun(c)
    g.poly([(4, -1), (9, -1), (7, -9), (3, -9)], '#2c2a40')
    g.tube(2, 16, 3.6, '#5a6078')
    g.rect(15, -2.6, 29, -1.2, '#c9ccd8')                                 # twin rails
    g.rect(15, 1.2, 29, 2.6, '#c9ccd8')
    for u in (18, 22, 26):
        g.rect(u, -2.6, u + 1, 2.6, '#7fd7ff')
    g.rect(15, -0.6, 30, 0.6, '#bfeaff')
    x, y = g.p(31, 0)
    c.star(x, y, 3, '#bfeaff', inner=0.3)
    return c.done()


def gi_explosive_bow():
    c = Canvas()
    c.shape('arc', ((-4, 4, 24, 32), 270, 360), '#8b5a2b', width=2.4)    # the bow, top right to bottom left
    c.line([(10, 4.5), (23.5, 18)], '#e8e8f0', 0.6)
    c.line([(4, 27), (27, 5)], '#c9ccd8', 1.0)                           # the arrow
    c.shape('poly', [(27, 5), (22, 6.5), (25.5, 10)], '#5a6078')
    c.ball(26, 6, 3.4, '#e03a3a', shine=False)                          # a little bomb on its tip
    c.star(29, 2.5, 2, '#ffcf3a', inner=0.35)
    c.shape('poly', [(4, 27), (3, 23), (7, 25)], '#ff6fd8')
    c.shape('poly', [(4, 27), (8, 28), (6, 24)], '#ff6fd8')
    return c.done()


def gi_pong():
    c = Canvas()
    c.shape('rect', (2, 6, 6, 18), '#ffffff')
    c.shape('rect', (26, 14, 30, 26), '#ffffff')
    c.line([(16, 2), (16, 30)], '#8a8ea0', 0.8)
    c.shape('rect', (14.5, 2, 17.5, 4), '#1a1b26')
    c.shape('rect', (14.5, 8, 17.5, 10), '#1a1b26')
    c.shape('rect', (14.5, 14, 17.5, 16), '#1a1b26')
    c.shape('rect', (14.5, 20, 17.5, 22), '#1a1b26')
    c.ball(20, 11, 3.4, '#ffe14d')
    c.line([(9, 17), (15, 13)], '#ffe14d', 0.6, opacity=0.6)
    return c.done()


def gi_rush_pearl():
    c = Canvas()
    for k, (x, y) in enumerate([(6, 26), (10, 22), (14, 18)]):
        c.shape('ellipse', (x - 1.4 + k * 0.3, y - 1.4 + k * 0.3, x + 1.4 - k * 0.3, y + 1.4 - k * 0.3), '#7ad8c8')
    c.ball(21, 11, 8, '#1f7a6a')
    c.shape('ellipse', (16, 7, 26, 15), '#3fbfa8')
    c.shape('ellipse', (19, 9, 23, 13), '#0e3a33')
    return c.done()


def gi_diamond_shower():
    c = Canvas()
    for (x, y, s) in [(9, 9, 1.0), (21, 7, 1.2), (14, 19, 1.3), (25, 21, 0.9), (6, 22, 0.8)]:
        pts = [(x, y - 5 * s), (x + 4.5 * s, y - 1 * s), (x, y + 5 * s), (x - 4.5 * s, y - 1 * s)]
        c.shape('poly', pts, '#5ae6e0')
        c.shape('poly', [(x, y - 5 * s), (x + 4.5 * s, y - 1 * s), (x, y - 0.5 * s)], '#b8fff8')
        c.shape('poly', [(x, y + 5 * s), (x - 4.5 * s, y - 1 * s), (x, y - 0.5 * s)], '#2ab0b0')
    for (x, y) in [(28, 4), (4, 4), (17, 29)]:
        c.star(x, y, 2, '#ffffff', inner=0.3)
    return c.done()


def gi_ghost():
    c = Canvas()
    body = [(8, 13), (9, 6), (16, 3), (23, 6), (24, 13), (25, 28), (22, 25), (19, 28.5), (16, 25), (13, 28.5), (10, 25), (7, 28)]
    c.shape('poly', body, '#f4f6ff')
    c.shape('poly', [(21, 6), (23, 6), (24, 13), (25, 28), (22, 25), (22, 10)], '#c8d2ee')
    c.shape('ellipse', (11, 9, 15, 15), '#1a1b26')
    c.shape('ellipse', (17, 9, 21, 15), '#1a1b26')
    c.shape('ellipse', (13.5, 17, 18.5, 22), '#2a2b3a')
    c.shape('rect', (12, 10, 13.2, 11.2), '#ffffff')
    c.shape('rect', (18, 10, 19.2, 11.2), '#ffffff')
    return c.done()


def gi_rainbow():
    c = Canvas()
    for i, col in enumerate(['#ff5a5a', '#ff9a2e', '#ffe14d', '#5be37c', '#5b9dff', '#9b5cff']):
        r = 14 - i * 1.8
        c.shape('arc', ((16 - r, 24 - r, 16 + r, 24 + r), 180, 360), col, width=1.9)
    for (x, y, r) in [(4, 24, 3.4), (7.5, 25, 3), (24.5, 25, 3), (28, 24, 3.4)]:
        c.shape('ellipse', (x - r, y - r * 0.8, x + r, y + r * 0.8), '#ffffff')
    return c.done()


def gi_diving_board():
    c = Canvas()
    for i, col in enumerate(['#3f8fe0', '#5bb8ff']):
        c.shape('rect', (0, 24 + i * 3, 32, 27 + i * 3), col)
    c.shape('rect', (3, 12, 7, 24), '#c9ccd8')                          # the stand
    c.shape('poly', [(2, 10), (30, 6), (30, 8.5), (2, 13)], '#f4f4fa')    # the board
    c.shape('poly', [(2, 12), (30, 8), (30, 8.5), (2, 13)], '#c9ccd8')
    for (x, y) in [(26, 20), (22, 22), (29, 22)]:
        c.shape('ellipse', (x - 1.4, y - 1.4, x + 1.4, y + 1.4), '#ffffff')
    return c.done()


def gi_swing():
    c = Canvas()
    c.line([(3, 29), (9, 3)], '#a8743a', 2.4)
    c.line([(29, 29), (23, 3)], '#a8743a', 2.4)
    c.line([(11.5, 4), (11.5, 22.5)], '#e8e8f0', 1.0)     # the ropes, tied on under the bar and down into the seat
    c.line([(20.5, 4), (20.5, 22.5)], '#e8e8f0', 1.0)
    c.shape('rect', (6, 2, 26, 5), '#8b5a2b')
    c.shape('rect', (9.5, 21.5, 22.5, 24), '#e03a3a')
    return c.done()


def gi_pumpkin_rain():
    c = Canvas()
    for (x, y, r) in [(4, 4, 3.4), (11, 4.5, 4), (19, 3.5, 4.5), (27, 4.5, 3.6)]:
        c.shape('ellipse', (x - r, y - r * 0.8, x + r, y + r * 0.8), '#6a6e86')
    for (x, y, r) in [(8, 15, 3.2), (20, 13, 3.8), (13, 25, 4.2), (26, 24, 3.2)]:
        pumpkin(c, x, y, r)
    return c.done()


def gi_scarecrow():
    c = Canvas()
    c.shape('rect', (15, 12, 17, 31), '#8b5a2b')
    c.shape('rect', (4, 15, 28, 17), '#8b5a2b')
    c.shape('rect', (9, 14, 23, 25), '#5b8fd0')                         # shirt
    c.shape('rect', (9, 18, 23, 19), '#3a6aa8')
    for (x0, y0) in [(4, 17), (25, 17)]:
        c.line([(x0, y0), (x0 - 1 if x0 < 10 else x0 + 3, y0 + 3)], '#e8c86a', 0.8)
    pumpkin(c, 16, 9, 5, face=True)
    c.shape('ellipse', (7, 3, 25, 6), '#c9a24a')                        # straw hat
    c.shape('rect', (11, 0, 21, 4), '#c9a24a')
    c.shape('rect', (11, 2.6, 21, 3.6), '#c8322e')
    return c.done()


def gi_cauldron():
    c = Canvas()
    for (x, y, r) in [(11, 7, 2.4), (18, 4, 2), (22, 9, 1.6), (14, 2, 1.2)]:
        c.shape('ellipse', (x - r, y - r, x + r, y + r), '#7fe35a')
        c.shape('ellipse', (x - r * 0.4, y - r * 0.6, x, y - r * 0.2), '#d8ffc0')
    c.shape('ellipse', (4, 10, 28, 16), '#2a2a36')
    c.shape('ellipse', (6, 11, 26, 15), '#5bd84a')
    c.shape('poly', [(4, 13), (28, 13), (27, 24), (22, 28), (10, 28), (5, 24)], '#3a3a48')
    c.shape('poly', [(4, 13), (10, 13), (10, 28), (5, 24)], '#4a4a5a')
    c.shape('rect', (7, 27, 10, 31), '#2a2a36')
    c.shape('rect', (22, 27, 25, 31), '#2a2a36')
    return c.done()


def gi_let_it_snow():
    c = Canvas()
    for (x, y, r) in [(9, 9, 6), (17, 7, 7.5), (24, 10, 5.5), (13, 12, 5), (21, 12.5, 5)]:
        c.shape('ellipse', (x - r, y - r, x + r, y + r), '#e8eef8')
    c.shape('rect', (4, 13, 29, 16), '#c8d4e8')
    for (x, y) in [(8, 21), (16, 23), (24, 21), (12, 28), (21, 29), (28, 27), (4, 27)]:
        c.line([(x - 1.6, y), (x + 1.6, y)], '#ffffff', 0.8)
        c.line([(x, y - 1.6), (x, y + 1.6)], '#ffffff', 0.8)
    return c.done()


def gi_ice_skate():
    c = Canvas()
    c.shape('poly', [(6, 5), (16, 5), (17, 14), (26, 16), (27, 22), (6, 22)], '#f4f4fa')
    c.shape('poly', [(6, 5), (16, 5), (16, 9), (6, 9)], '#5b9dff')
    for y in (11, 14, 17):
        c.line([(9, y), (14, y - 1)], '#c9ccd8', 0.8)
    c.shape('rect', (8, 22, 11, 26), '#8a8ea0')
    c.shape('rect', (22, 22, 25, 26), '#8a8ea0')
    c.shape('poly', [(4, 26), (28, 26), (30, 24), (31, 27), (28, 28), (4, 28)], '#c9ccd8')
    c.shape('rect', (4, 26, 28, 26.8), '#ffffff')
    return c.done()


def gi_frisbee():
    c = Canvas()
    c.shape('ellipse', (3, 9, 29, 23), '#c8322e')
    c.shape('ellipse', (3, 8, 29, 21), '#ff5a4a')
    c.shape('ellipse', (8, 10.5, 24, 18.5), '#ff7a6a')
    c.shape('arc', ((8, 10.5, 24, 18.5), 180, 360), '#ffffff', width=0.8)
    for (x0, y0, x1) in [(1, 26, 8), (3, 29, 12)]:
        c.line([(x0, y0), (x1, y0)], '#ffffff', 0.8)
    return c.done()


def gi_sand_castle():
    c = Canvas()
    sand, dark_ = '#ecd08a', '#c9a860'
    c.shape('rect', (2, 26, 30, 30), dark_)
    c.shape('rect', (5, 14, 27, 27), sand)
    for x in (5, 9, 13, 17, 21, 25):
        c.shape('rect', (x, 12, x + 2, 14), sand)
    c.shape('rect', (11, 6, 21, 27), sand)
    for x in (11, 14.5, 18.5):
        c.shape('rect', (x, 4, x + 2.5, 6), sand)
    c.shape('rect', (14, 19, 18, 27), dark_)
    c.shape('ellipse', (14, 17, 18, 21), dark_)
    c.line([(16, 4), (16, -1)], '#8b5a2b', 0.8)
    c.shape('poly', [(16, -0.5), (22, 1.2), (16, 3)], '#e03a3a')
    c.shape('rect', (11, 6, 12.4, 27), '#f6e0a8')
    return c.done()


def gi_tic_tac_toe():
    c = Canvas()
    c.shape('rrect', (2, 2, 30, 30, 2), '#8b5a2b')
    c.shape('rrect', (3, 3, 29, 29, 2), '#c49a5a')
    for k in (11.3, 20.6):
        c.shape('rect', (k, 4, k + 1.4, 28), '#5a3a1e')
        c.shape('rect', (4, k, 28, k + 1.4), '#5a3a1e')
    for (x, y) in [(7, 7), (16, 16), (25, 25)]:
        c.line([(x - 2.5, y - 2.5), (x + 2.5, y + 2.5)], '#e03a3a', 1.4)
        c.line([(x + 2.5, y - 2.5), (x - 2.5, y + 2.5)], '#e03a3a', 1.4)
    for (x, y) in [(25, 7), (7, 25)]:
        c.shape('arc', ((x - 3, y - 3, x + 3, y + 3), 0, 360), '#3c6ae0', width=1.4)
    return c.done()


def gi_dj_booth():
    c = Canvas()
    for x in (2, 24):                                                    # speakers
        c.shape('rect', (x, 6, x + 6, 26), '#2a2a36')
        c.shape('ellipse', (x + 0.8, 8, x + 5.2, 12.4), '#5a6078')
        c.shape('ellipse', (x + 0.4, 15, x + 5.6, 22), '#5a6078')
        c.shape('ellipse', (x + 2, 17, x + 4, 20), '#1a1b26')
    c.shape('rect', (8, 17, 24, 27), '#3a2a52')                         # the desk
    c.shape('rect', (8, 17, 24, 18.5), '#9b5cff')
    c.shape('ellipse', (9, 13.5, 15.5, 17.5), '#1a1b26')                 # turntables
    c.shape('ellipse', (16.5, 13.5, 23, 17.5), '#1a1b26')
    c.shape('ellipse', (11.4, 14.8, 13.2, 16.2), '#e03a3a')
    c.shape('ellipse', (18.9, 14.8, 20.7, 16.2), '#5b9dff')
    for (x, y, col) in [(13, 4, '#ff6fd8'), (19, 2, '#5b9dff')]:
        c.shape('rect', (x, y, x + 1.2, y + 6), col)
        c.shape('ellipse', (x - 2.8, y + 4.6, x + 1.2, y + 7.6), col)
    return c.done()


GADGETS.update({'hot_potato': gi_hot_potato, 'magic_8_ball': gi_magic_8_ball, 'fortune_cookie': gi_fortune_cookie,
                'tetherball': gi_tetherball, 'rock_paper_shears': gi_rps, 'catapult': gi_catapult, 'pigs_fly': gi_pigs_fly,
                'creeper_astronaut': gi_creeper_astronaut, 'exploding_sheep': gi_exploding_sheep, 'duckling': gi_duckling,
                'pinata': gi_pinata, 'jetpack': gi_jetpack, 'mob_gun': gi_mob_gun, 'railgun': gi_railgun,
                'explosive_bow': gi_explosive_bow, 'pong': gi_pong, 'rush_pearl': gi_rush_pearl, 'diamond_shower': gi_diamond_shower,
                'ghost': gi_ghost, 'rainbow': gi_rainbow, 'diving_board': gi_diving_board, 'swing': gi_swing,
                'pumpkin_rain': gi_pumpkin_rain, 'scarecrow': gi_scarecrow, 'witch_cauldron': gi_cauldron,
                'let_it_snow': gi_let_it_snow, 'ice_skate': gi_ice_skate, 'frisbee': gi_frisbee, 'sand_castle': gi_sand_castle,
                'tic_tac_toe': gi_tic_tac_toe, 'dj_booth': gi_dj_booth})
GUNS = GUNS + ('mob_gun', 'railgun')


from bubble_wand_icon import bubble_wand as _bubble_wand_pixels
GADGETS['bubble_wand'] = _bubble_wand_pixels

from party_popper_icon import popper_closed as _popper_closed, popper_open as _popper_open
GADGETS['party_popper'] = _popper_closed
GADGETS['party_popper_open'] = _popper_open
GUNS = GUNS + ('party_popper_open',)


# ---------------------------------------------------------------- 1.41: the firework launcher, loaded but not firing
def gun_firework_launcher():
    """The firework launcher (Sasha picked this one): a red and white bazooka with a rocket loaded, only its gold nose
    showing at the muzzle (no fireworks until it fires). Placed so it sits in the middle of the slot."""
    c = Canvas(); g = Gun(c, ox=6.5, oy=24)
    g.poly([(7, -1), (12, -1), (10, -8), (6, -8)], '#2c2a40')               # grip
    g.poly([(14, -1), (17, -1), (16, -6), (13, -6)], '#2c2a40')             # front grip
    g.tube(0, 23, 3.8, '#e8443a')
    for u in (4, 10, 16):
        g.rect(u, -3.8, u + 2.4, 3.8, '#f4f4fa')
    g.rect(21.5, -4.4, 24, 4.4, '#8a8ea0')                                   # muzzle ring
    g.rect(0, -4.4, 1.8, 4.4, '#8a8ea0')
    g.poly([(24, -2.4), (24, 2.4), (28.5, 0)], '#ffcf5a')                    # the rocket's nose, waiting
    g.poly([(24, 0.5), (24, 2.4), (28.5, 0)], '#fff2a0')
    g.rect(8, 3.8, 14, 5.6, '#5a5e70')                                       # sight
    g.rect(12, 5.6, 13.2, 7.2, '#5a5e70')
    return c.done()


GADGETS['firework_launcher'] = gun_firework_launcher

from disco_ball_icon import disco_ball_icon as _disco_ball_icon
GADGETS['disco_ball'] = _disco_ball_icon

from blink_stick_icon import blink_stick_icon as _blink_stick_icon
GADGETS['blink_stick'] = _blink_stick_icon


def gi_magic_8_ball_big():
    """The magic 8 ball: a black ball with a big white window and a bold 8 (pixel by pixel)."""
    from PIL import Image as _I
    im = _I.new('RGBA', (32, 32), (0, 0, 0, 0)); px = im.load()
    for y in range(32):
        for x in range(32):
            dx, dy = x + 0.5 - 16, y + 0.5 - 16
            d = math.hypot(dx, dy)
            if d > 14: continue
            light = -dx - dy
            col = (74, 76, 96) if light > 14 else (44, 46, 60) if light > 2 else (28, 29, 40)
            if math.hypot(dx + 0.5, dy + 0.5) <= 6.4: col = (244, 244, 250)
            px[x, y] = col + (255,)
    eight = [".###.", "#...#", ".###.", "#...#", ".###."]
    for r, row in enumerate(eight):
        for c, ch in enumerate(row):
            if ch == '#': px[13 + c, 13 + r] = (17, 18, 26, 255)
    for (x, y) in [(8, 7), (9, 7), (8, 8)]:
        px[x, y] = (140, 144, 170, 255)
    src = im.copy(); sp = src.load()
    for y in range(32):
        for x in range(32):
            if sp[x, y][3]: continue
            if any(0 <= x + a < 32 and 0 <= y + b < 32 and sp[x + a, y + b][3] for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = (0x1a, 0x1b, 0x26, 255)
    return im


GADGETS['magic_8_ball'] = gi_magic_8_ball_big

from fortune_fx import icon as _fortune_icon
GADGETS['fortune_cookie'] = _fortune_icon

from rps_fx import shears_icon as _rps_icon
GADGETS['rock_paper_shears'] = _rps_icon

from catapult_icon import catapult_icon as _catapult_icon
GADGETS['catapult'] = _catapult_icon

from mob_gun_icon import opt1 as _mob_gun_icon
GADGETS['mob_gun'] = _mob_gun_icon        # the Pig Blaster (Sasha picked #1)

from pong_icon import diamond_shower as _diamond_shower_icon
GADGETS['diamond_shower'] = _diamond_shower_icon   # a cloud raining diamonds
from pong_icon import pong1 as _pong_icon
GADGETS['pong'] = _pong_icon                       # the arcade screen (Sasha picked #1)

from tetherball_icon import tb2 as _tetherball_icon
GADGETS['tetherball'] = _tetherball_icon           # wound round the pole (Sasha picked #2, no sparkle)

from pinata_icon import opt3 as _pinata_icon
GADGETS['pinata'] = _pinata_icon                   # the donkey getting whacked by the bat (Sasha picked #3)

from railgun_icon import opt3 as _railgun_icon
GADGETS['railgun'] = _railgun_icon                 # mid-shot with the cyan beam (Sasha picked #3)

from rush_pearl_icon import opt1 as _rush_pearl_icon
GADGETS['rush_pearl'] = _rush_pearl_icon           # a deep teal pearl with a swirl (Sasha picked #1, polished)

from boomerang_icon import crisp as _boomerang_icon
GADGETS['explosive_bow'] = _boomerang_icon         # the Boomerang (it replaced the Explosive Bow but kept its key)

from pigs_fly_icon import crisp as _pigs_fly_icon
GADGETS['pigs_fly'] = _pigs_fly_icon               # side-on pig with a feathered wing (Sasha picked #1)

from astro_jet_icons import astro1 as _astro_icon, jet1 as _jet_icon
GADGETS['creeper_astronaut'] = _astro_icon         # creeper in a bubble helmet (Sasha picked #1)
GADGETS['jetpack'] = _jet_icon                     # silver twin tanks with flames (Sasha picked #1)

from astro_jet_icons import sharp_astro1 as _astro_sharp
GADGETS['creeper_astronaut'] = _astro_sharp        # sharpened, with the right creeper face (Sasha picked #1)

from volleyball_icon import crisp_ball as _volleyball_icon
GADGETS['beach_ball'] = _volleyball_icon           # the Volleyball (it replaced the Beach Ball but kept its key)

from pool_icons import sand_castle as _sand_castle_icon, db1 as _diving_icon
GADGETS['sand_castle'] = _sand_castle_icon         # shorter middle tower, a proper pennant
GADGETS['diving_board'] = _diving_icon             # ladder tower, board over the pool (Sasha picked #1)

from spooky_icons import pr1 as _pumpkin_rain_icon
GADGETS['pumpkin_rain'] = _pumpkin_rain_icon       # storm cloud raining jack-o'-lanterns (Sasha picked #1)
