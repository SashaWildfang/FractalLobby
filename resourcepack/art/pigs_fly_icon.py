"""When Pigs Fly icon options (centred in the slot)."""
from PIL import Image
from kit import Canvas, light, dark

PINK, PINK_D, PINK_L, SNOUT, INK = '#f4a3b8', '#d9708e', '#ffc4d4', '#e77f9c', '#1a1b26'


def centre(im):
    b = im.getbbox()
    out = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    out.alpha_composite(im, (round(16 - (b[0] + b[2]) / 2), round(16 - (b[1] + b[3]) / 2)))
    return out


def wing(c, x, y, flip=1, s=1.0, up=True):
    """A white feathered wing rooted at (x, y), sweeping up and out (flip = 1 right, -1 left), with a scalloped
    trailing edge of three feather tips and a soft blue line between the feather rows."""
    P = lambda dx, dy: (x + flip * dx * s, y + dy * s)
    c.shape('poly', [P(0, 0), P(1, -5), P(4, -8.5), P(9, -9.5), P(8, -7), P(9.2, -6.2), P(7, -4.6), P(7.8, -3.4),
                     P(5.2, -2.4), P(5.6, -1), P(2.6, 0.6)], '#ffffff')
    c.line([P(2, -3), P(7.5, -7.6)], '#c8d6f0', 0.7)
    c.line([P(2.2, -1.2), P(6.2, -4.2)], '#c8d6f0', 0.7)


def side_pig(c, ox, oy):
    """A chubby pig side-on, facing right, top-left at (ox, oy): 18 wide, about 12 tall."""
    c.shape('rrect', (ox, oy + 2, ox + 15, oy + 10, 3), PINK)                # body
    c.shape('rect', (ox + 2, oy + 2.5, ox + 13, oy + 4), PINK_L)
    c.shape('rrect', (ox + 11, oy, ox + 18, oy + 7, 2), PINK)               # head
    c.shape('rrect', (ox + 16.5, oy + 3, ox + 19.5, oy + 6, 1), SNOUT)     # snout
    c.shape('rect', (ox + 17.4, oy + 4, ox + 18.2, oy + 4.8), dark(SNOUT, 0.4))
    c.shape('rect', (ox + 14.5, oy + 1.8, ox + 15.7, oy + 3), INK)          # eye
    c.shape('poly', [(ox + 12, oy), (ox + 13, oy - 2), (ox + 14.5, oy)], PINK_D)   # ear
    for x in (1.5, 5, 9.5, 12.5):                                          # legs, tucked up as it flies
        c.shape('rect', (ox + x, oy + 9.5, ox + x + 2, oy + 12), PINK_D)
    c.shape('arc', ((ox - 3, oy + 2, ox + 1, oy + 6), 120, 330), PINK_D, width=0.9)   # curly tail


def opt1():
    """Side-on flying pig with a big white wing up on its back."""
    c = Canvas()
    side_pig(c, 5, 12)
    wing(c, 13, 14, flip=-1, s=1.15)
    c.shape('rect', (19, 13, 21, 15), INK)                              # a clear 2x2 eye (Sasha picked #1)
    return centre(c.done())


def opt2():
    """Front-on pig face with a white wing on each side, flapping (no body)."""
    c = Canvas()
    wing(c, 9, 17, flip=-1, s=1.0)
    wing(c, 23, 17, flip=1, s=1.0)
    c.shape('rrect', (8, 9, 24, 25, 5), PINK)
    c.shape('rect', (10, 10, 22, 12), PINK_L)
    c.shape('poly', [(8, 10), (9, 6), (12.5, 9)], PINK_D)                  # ears
    c.shape('poly', [(24, 10), (23, 6), (19.5, 9)], PINK_D)
    c.shape('rect', (11, 13.5, 13, 15.5), INK)                             # eyes
    c.shape('rect', (19, 13.5, 21, 15.5), INK)
    c.shape('rrect', (12.5, 17, 19.5, 22.5, 2), SNOUT)                    # snout
    c.shape('rect', (14, 18.8, 15.2, 20.6), dark(SNOUT, 0.45))
    c.shape('rect', (16.8, 18.8, 18, 20.6), dark(SNOUT, 0.45))
    return centre(c.done())


def opt3():
    """The pig flying over a little cloud, both wings up, with a couple of motion lines behind it."""
    c = Canvas()
    for (x, y, r) in [(9, 26, 3.5), (15, 24.5, 4.5), (21, 26, 3.5)]:
        c.shape('ellipse', (x - r, y - r * 0.8, x + r, y + r * 0.8), '#eef2ff')
    c.shape('rect', (5.5, 26, 24.5, 28.5), '#eef2ff')
    side_pig(c, 6, 7)
    wing(c, 14, 9, flip=-1, s=0.95)
    wing(c, 17, 9, flip=1, s=0.85)
    for y in (11, 14):
        c.line([(0.5, y), (3.5, y)], '#ffffff', 0.9, opacity=0.7)
    return centre(c.done())


OPTIONS = [opt1, opt2, opt3]
