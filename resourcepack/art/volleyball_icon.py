"""Volleyball icon options (centred in the slot), and the volleyball's colours for its 3D model."""
import math
from PIL import Image
from kit import Canvas, light, dark

WHITE, YEL, BLUE, SEAM = '#f6f6f2', '#f7c21c', '#1f52d6', '#9aa2b8'


def centre(im):
    b = im.getbbox()
    out = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    out.alpha_composite(im, (round(16 - (b[0] + b[2]) / 2), round(16 - (b[1] + b[3]) / 2)))
    return out


def vb_panel(x, y, z):
    """Which panel a point on the ball is on, like a real volleyball: the ball is split into six panels (as if a cube
    were blown up round it); each panel has three equal strips running one way, and the strips run a different way on
    each pair of panels, which is what makes the curved, woven look. Returns (strip colour, distance to the nearest seam)."""
    ax, ay, az = abs(x), abs(y), abs(z)
    m = max(ax, ay, az)
    # the coordinate the strips are laid out along, on this panel (as a fraction across the panel, -1 to 1)
    if m == ax:
        t, other = y / m, z / m
        order = (YEL, WHITE, BLUE) if x > 0 else (BLUE, WHITE, YEL)
    elif m == ay:
        t, other = z / m, x / m
        order = (BLUE, WHITE, YEL) if y > 0 else (YEL, WHITE, BLUE)
    else:
        t, other = x / m, y / m
        order = (WHITE, YEL, BLUE) if z > 0 else (BLUE, YEL, WHITE)
    band = min(2, int((t + 1) / 2 * 3))
    edge_strip = min(abs(t - (-1 / 3)), abs(t - (1 / 3)))              # between strips
    edge_panel = 1 - max(abs(t), abs(other))                          # the panel's own edge
    return order[band], min(edge_strip, edge_panel)


def vb_colour(x, y, z):
    return vb_panel(x, y, z)[0]


def ball_img(img, cx, cy, r, tilt=(0.62, -0.42)):
    """Paints a crisp-edged, shaded volleyball into img, centred at (cx, cy): the strips, thin dark seams between
    them, soft round shading lit from the top left, a darker rim and a glint."""
    px = img.load()
    lx, ly, lz = -0.45, -0.6, 0.66
    ca, sa, cb, sb = math.cos(tilt[0]), math.sin(tilt[0]), math.cos(tilt[1]), math.sin(tilt[1])
    seam_w = 0.75 / r                                                  # about one pixel wide, whatever the size
    for Y in range(img.height):
        for X in range(img.width):
            dx, dy = (X + 0.5 - cx) / r, (Y + 0.5 - cy) / r
            d2 = dx * dx + dy * dy
            if d2 > 1:
                continue
            dz = math.sqrt(1 - d2)
            x1, z1 = dx * ca + dz * sa, -dx * sa + dz * ca             # turned so three panel groups show
            y1, z2 = dy * cb - z1 * sb, dy * sb + z1 * cb
            col, edge = vb_panel(x1, y1, z2)
            rgb = [int(col[i:i + 2], 16) for i in (1, 3, 5)]
            if edge < seam_w:
                rgb = [int(v * 0.55) for v in rgb]                     # a seam
            light_ = max(0.0, dx * lx + dy * ly + dz * lz)
            k = 0.8 + 0.3 * light_
            if d2 > 0.86:
                k *= 0.88
            px[X, Y] = tuple(max(0, min(255, int(v * k))) for v in rgb) + (255,)
    gx, gy = round(cx - r * 0.42), round(cy - r * 0.5)
    for (X, Y) in ((gx, gy), (gx + 1, gy), (gx, gy + 1)):
        px[X, Y] = (255, 255, 255, 255)


def ball(c, cx, cy, r):
    pass


def vb1():
    """The classic volleyball, big and centred."""
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    ball_img(img, 16, 16, 13.5)
    return centre(img)


def vb2():
    """A volleyball flying over the top of a net."""
    from PIL import ImageDraw
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle((2, 19, 3, 31), fill='#7a5530'); d.rectangle((28, 19, 29, 31), fill='#7a5530')   # posts
    for x in range(4, 28, 3):
        d.line((x, 21, x, 27), fill='#d8e0ee')                         # the net's mesh
    for y in (23, 26):
        d.line((4, y, 27, y), fill='#d8e0ee')
    d.rectangle((4, 19, 27, 20), fill='#ffffff')                       # its top band
    ball_img(img, 16, 9.5, 8.5)
    return centre(img)


def vb3():
    """A volleyball landing on a patch of sand, with motion lines."""
    from PIL import ImageDraw
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((2, 23, 29, 30), fill='#e8cf8a'); d.ellipse((5, 24, 26, 29), fill='#f2dc9e')
    for (x, y) in ((8, 26), (22, 27), (14, 28)):
        d.point((x, y), fill='#c9ac62')
    ball_img(img, 17, 13, 9.5)
    for k, y in enumerate((5, 9)):
        d.line((2 + k, y, 5 + k, y + 2), fill='#ffffff')
    return centre(img)


OPTIONS = [vb1, vb2, vb3]


def crisp_ball():
    """Sasha's pick (#1), with sharper lines: the strips worked out pixel by pixel, a crisp one-pixel dark seam
    wherever two strips meet, gentle shading that doesn't blur the colours, and a clean dark outline."""
    size, cx, cy, r = 32, 16, 16, 14
    ca, sa, cb, sb = math.cos(0.62), math.sin(0.62), math.cos(-0.42), math.sin(-0.42)
    panel = {}
    shade = {}
    for Y in range(size):
        for X in range(size):
            dx, dy = (X + 0.5 - cx) / r, (Y + 0.5 - cy) / r
            d2 = dx * dx + dy * dy
            if d2 > 1:
                continue
            dz = math.sqrt(1 - d2)
            x1, z1 = dx * ca + dz * sa, -dx * sa + dz * ca
            y1, z2 = dy * cb - z1 * sb, dy * sb + z1 * cb
            ax, ay, az = abs(x1), abs(y1), abs(z2)
            m = max(ax, ay, az)
            face = 0 if m == ax else 1 if m == ay else 2
            col = vb_panel(x1, y1, z2)[0]
            panel[(X, Y)] = (face, col)
            shade[(X, Y)] = 0.86 + 0.22 * max(0.0, -0.45 * dx - 0.6 * dy + 0.66 * dz)
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    px = img.load()
    seam = (44, 52, 86, 255)
    for (X, Y), (face, col) in panel.items():
        rgb = [int(col[i:i + 2], 16) for i in (1, 3, 5)]
        k = shade[(X, Y)]
        px[X, Y] = tuple(max(0, min(255, int(v * k))) for v in rgb) + (255,)
    for (X, Y), here in panel.items():                                 # one-pixel seams between different strips
        for nb in ((X + 1, Y), (X, Y + 1)):
            there = panel.get(nb)
            if there and there != here:
                px[X, Y] = seam
                break
    for (X, Y) in panel:                                               # and a clean outline
        if any((X + a, Y + b) not in panel for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            px[X, Y] = seam
    for (X, Y) in ((10, 8), (11, 8), (10, 9)):
        px[X, Y] = (255, 255, 255, 255)                                # a glint
    return centre(img)


def colour_at(mx, mz):
    """For the 3D ball (built of columns, seen mostly from the side): the same strips as the icon, sampled round its
    middle."""
    r = math.hypot(mx, mz) or 1
    return vb_panel(mx / r, 0.3, mz / r)[0]
