"""Pumpkin Head options. Head pixels: x, z from -4 to 4 (-z is the face), y from the top of the head (0) up."""

O, O2, O3, LIT, CARVE, STEM, LEAF = '#f08a1c', '#d26a14', '#ffa53a', '#ffd84a', '#5a2408', '#5a4a22', '#4caa3c'


def _round_pumpkin(cx, cy0, cz, rx, h, rz):
    """A round, ribbed pumpkin from stacked rings (narrow at top and bottom, fat in the middle), with darker rib
    stripes on every side. (cx, cz) centre, cy0 bottom, rx/rz half widths, h height."""
    b = []
    rings = [(0.00, 0.08, 0.70), (0.08, 0.20, 0.90), (0.20, 0.78, 1.00), (0.78, 0.90, 0.90), (0.90, 1.00, 0.70)]
    for (a0, a1, w) in rings:
        b.append((cx - rx * w, cy0 + h * a0, cz - rz * w, cx + rx * w, cy0 + h * a1, cz + rz * w, O))
    for f in (-0.5, 0.0, 0.5):                                         # ribs, standing slightly proud
        x = cx + rx * f
        z = cz + rz * f
        y0, y1 = cy0 + h * 0.1, cy0 + h * 0.88
        b.append((x - 0.3, y0, cz + rz, x + 0.3, y1, cz + rz + 0.12, O2))            # back
        b.append((cx - rx - 0.12, y0, z - 0.3, cx - rx, y1, z + 0.3, O2))            # sides
        b.append((cx + rx, y0, z - 0.3, cx + rx + 0.12, y1, z + 0.3, O2))
    b.append((cx - rx * 0.45, cy0 + h, cz - rz * 0.45, cx + rx * 0.45, cy0 + h + 0.25, cz + rz * 0.45, O2))   # the dip on top
    return b


def _carve(pieces, z, glow=LIT):
    """Carved openings on the front at depth z: a dark rim (the cut) with the glow just inside it."""
    b = []
    for (x0, y0, x1, y1) in pieces:
        b.append((x0 - 0.25, y0 - 0.25, z - 0.06, x1 + 0.25, y1 + 0.25, z + 0.02, CARVE))
        b.append((x0, y0, z - 0.12, x1, y1, z - 0.06, glow))
    return b


def _stem(x, y, z, tall=1.8, lean=0.5):
    return [(x - 0.6, y, z - 0.6, x + 0.6, y + tall * 0.6, z + 0.6, STEM),
            (x - 0.5 + lean, y + tall * 0.6, z - 0.5, x + 0.5 + lean, y + tall, z + 0.5, STEM),
            (x + lean + 0.5, y + tall * 0.85, z - 0.2, x + lean + 1.4, y + tall * 0.85 + 0.3, z + 0.2, STEM)]


def _leaf(x, y, z):
    return [(x, y, z - 0.9, x + 2.4, y + 0.3, z + 0.9, LEAF), (x + 0.6, y + 0.3, z - 0.15, x + 2.0, y + 0.4, z + 0.15, '#3a8a2c')]


def ph1():
    """The jack-o'-lantern head, done properly: a round ribbed pumpkin over the whole head, a curly stem and a leaf,
    and a carved face (triangle eyes, a nose, a toothy grin) with dark cut edges and a warm glow inside."""
    b = _round_pumpkin(0, -8.8, 0, 5.2, 10.4, 5.2)
    b += _stem(0, 1.85, 0) + _leaf(0.6, 1.7, 0.6)
    z = -5.2
    eyes = [(-3.9, -3.4, -1.3, -2.6), (-3.5, -2.6, -1.7, -1.8), (-3.0, -1.8, -2.2, -1.2),
            (1.3, -3.4, 3.9, -2.6), (1.7, -2.6, 3.5, -1.8), (2.2, -1.8, 3.0, -1.2)]
    nose = [(-0.6, -4.4, 0.6, -3.8)]
    grin = [(-3.9, -6.5, 3.9, -5.3), (-3.9, -5.3, -3.1, -4.8), (3.1, -5.3, 3.9, -4.8)]
    b += _carve(eyes + nose + grin, z)
    for x in (-1.9, 0.9):                                              # two teeth hanging into the grin
        b.append((x, -5.9, z - 0.18, x + 1.0, -5.3, z - 0.12, O))
    b.append((-0.5, -6.5, z - 0.18, 0.5, -6.0, z - 0.12, O))          # and one coming up
    return b


def ph2():
    """A little pumpkin hat: a small round jack-o'-lantern sitting on top of your head, tipped jauntily to one side,
    with its own tiny glowing face, a curly stem and a leaf (your face stays free)."""
    b = _round_pumpkin(0.6, -0.2, -0.4, 3.4, 5.0, 3.4)
    b += _stem(0.6, 4.95, -0.4, 1.6, 0.4) + _leaf(1.0, 4.8, 0.2)
    z = -3.8
    eyes = [(-1.6, 2.6, -0.4, 3.4), (-1.3, 3.4, -0.7, 3.8), (1.6, 2.6, 2.8, 3.4), (1.9, 3.4, 2.5, 3.8)]
    grin = [(-1.6, 1.0, 2.8, 1.6), (-1.6, 1.6, -1.0, 2.0), (2.2, 1.6, 2.8, 2.0), (0.3, 1.6, 0.9, 1.9)]
    b += _carve(eyes + grin, z)
    return b


def ph3():
    """Spooky: the full pumpkin head with sharp slanted eyes and a jagged fanged grin glowing eerie green, and a small
    crooked witch's hat perched on top."""
    b = _round_pumpkin(0, -8.8, 0, 5.2, 10.4, 5.2)
    z = -5.2
    GREEN = '#9dff6a'
    eyes = [(-3.9, -3.2, -1.3, -2.6), (-3.5, -2.6, -1.3, -2.0), (-2.7, -2.0, -1.3, -1.5),
            (1.3, -3.2, 3.9, -2.6), (1.3, -2.6, 3.5, -2.0), (1.3, -2.0, 2.7, -1.5)]
    grin = [(-4.0, -6.2, 4.0, -5.5)]
    for k, x in enumerate((-3.6, -2.0, -0.4, 1.2, 2.8)):                # fangs up and down
        grin.append((x, -5.5, x + 0.8, -4.9) if k % 2 == 0 else (x, -6.8, x + 0.8, -6.2))
    b += _carve(eyes + grin, z, GREEN)
    H, H2, BAND = '#3a3048', '#2a2236', '#9dff6a'
    b += [(-5.4, 1.6, -5.4, 5.4, 2.2, 5.4, H2), (-3.4, 2.2, -3.4, 3.4, 4.2, 3.4, H), (-2.4, 4.2, -2.4, 2.4, 5.8, 2.4, H),
          (-1.4, 5.8, -1.2, 1.6, 7.0, 1.4, H), (-0.4, 7.0, -0.2, 1.8, 7.8, 1.0, H), (1.2, 7.4, 0.0, 2.6, 8.2, 0.8, H),
          (-3.45, 2.2, -3.45, 3.45, 2.9, 3.45, BAND)]
    return b


OPTIONS = {'pumpkin_head_1': ph1, 'pumpkin_head_2': ph2, 'pumpkin_head_3': ph3}
LIT_COLOURS = {LIT, '#9dff6a'}
