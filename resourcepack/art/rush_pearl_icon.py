"""Rush Pearl icon options: a literal pearl (centred in the slot)."""
import math
from PIL import Image
from kit import Canvas, light, dark


def centre(im):
    b = im.getbbox()
    out = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    out.alpha_composite(im, (round(16 - (b[0] + b[2]) / 2), round(16 - (b[1] + b[3]) / 2)))
    return out


def opt1():
    """A deep teal pearl (Sasha's pick, polished): a soft teal glow round it, a darker rim, a two-armed swirl of green
    light curling into a bright core, a big glossy shine top left and a rim of light along the bottom right."""
    c = Canvas()
    c.glow(16, 16, 15, '#3fd8b8', opacity=0.28)
    c.ball(16, 16, 11.5, '#17685c')
    c.shape('ellipse', (6.5, 6.5, 25.5, 25.5), '#1d7a6c')                 # a little lighter in the middle
    for (start, end, box, col, w) in (                                     # the swirl: two arms curling in
            (190, 330, (8.5, 8.5, 23.5, 23.5), '#2fc7a8', 1.6),
            (10, 150, (8.5, 8.5, 23.5, 23.5), '#2fc7a8', 1.6),
            (230, 390, (11.5, 11.5, 20.5, 20.5), '#6ff0d4', 1.4),
            (50, 210, (11.5, 11.5, 20.5, 20.5), '#6ff0d4', 1.4)):
        c.shape('arc', (box, start, end), col, width=w)
    c.glow(16, 16, 4, '#c8fff2', opacity=0.8)                              # the bright core
    c.shape('ellipse', (14.6, 14.6, 17.4, 17.4), '#eafff9')
    c.shape('arc', ((5.5, 5.5, 26.5, 26.5), 20, 80), '#5fe0c8', width=1.0)  # rim light, bottom right
    c.shape('ellipse', (9, 7.5, 15, 11.5), '#ffffff', opacity=0.75)       # the shine
    c.shape('ellipse', (16.5, 7.5, 18, 9), '#ffffff', opacity=0.5)
    return centre(c.done())


def opt2():
    """A glossy purple Fractal pearl in a soft glow, with two little sparkles."""
    c = Canvas()
    c.glow(16, 17, 14, '#b266ff', opacity=0.35)
    c.ball(16, 17, 10.5, '#8a3fe0')
    c.shape('ellipse', (9, 10, 21, 20), light('#8a3fe0', 0.25))
    c.star(25.5, 6.5, 3.4, '#fff2a0', inner=0.3)
    c.star(6, 27, 2.2, '#ffffff', inner=0.3)
    return centre(c.done())


def opt3():
    """A real pearl: creamy white with a pastel rainbow sheen and a glint."""
    c = Canvas()
    c.ball(16, 17, 11, '#e6dcec')
    c.shape('arc', ((7, 8, 25, 26), 20, 110), '#f6c6e2', width=1.6)       # a pastel sheen round its lower edge
    c.shape('arc', ((7, 8, 25, 26), 110, 170), '#c6daff', width=1.6)
    c.shape('ellipse', (10, 10, 17, 16), '#ffffff', opacity=0.9)
    c.star(26, 6, 3.2, '#fff2a0', inner=0.28)
    return centre(c.done())


OPTIONS = [opt1, opt2, opt3]
