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


def gun_paintball():
    c = Canvas()
    g = Gun(c)
    g.poly([(4, -1), (9, -1), (7, -9), (3, -9)], '#2c2a40')            # grip
    g.rect(9.5, -3.5, 11, -1, '#8a8ea0')                                 # trigger guard
    g.tube(3, 21, 3.2, '#7b55e0')                                        # body
    g.rect(20, -1.8, 29, 1.8, '#a8acbc')                                 # barrel
    g.rect(20, 0.4, 29, 1.2, '#e8eaf4')
    g.rect(28, -2.2, 30, 2.2, '#5a5e70')
    g.rect(6, -0.6, 19, 0.6, '#ffcf5a')                                  # stripe
    g.oval(12, 6.5, 4.2, 4.2, '#ff6fd8')                                 # paint hopper
    g.oval(11, 7.6, 1.6, 1.4, '#ffc4ee')
    g.rect(10.5, 2.5, 13.5, 3.6, '#4a3a8a')
    for (u, v) in [(32.5, 3), (33, -2.5)]:
        x, y = g.p(u, v)
        c.shape('ellipse', (x - 1, y - 1, x + 1, y + 1), '#ff6fd8')
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
