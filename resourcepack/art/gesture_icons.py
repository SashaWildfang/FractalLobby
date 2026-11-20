"""
Lobby resource pack - built by Sasha Wildfang for Fractal Network.

The icons in the Gestures menu (fractal:gesture/<key>), in exactly the style of the Gestures section icon: a little
16x16 figure with a skin-tone head, a purple shirt and legs, a gold belt and a dark outline, doing each gesture.

    python gesture_icons.py <FractalLobby folder> [preview.png]
"""
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from section_icons import PALETTE, outlined  # noqa: E402

PALETTE = dict(PALETTE, **{"Z": (127, 168, 255), "E": (150, 214, 120), "e": (104, 170, 86), "F": (255, 140, 60)})


class Grid:
    def __init__(self):
        self.cells = [["."] * 16 for _ in range(16)]

    def put(self, x, y, key):
        if 0 <= x < 16 and 0 <= y < 16:
            self.cells[y][x] = key

    def line(self, x0, y0, x1, y1, key, width=1, shade=None):
        steps = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(steps + 1):
            x = round(x0 + (x1 - x0) * i / steps)
            y = round(y0 + (y1 - y0) * i / steps)
            self.put(x, y, key)
            if width == 2:
                self.put(x + 1, y, shade or key)

    def rows(self):
        return ["".join(row) for row in self.cells]


def figure(g, ox=0, oy=0, arms=((5, 5, 2, 2), (10, 5, 13, 2)), legs=((6, 10, 4, 12), (9, 10, 10, 12)),
           skin="S", head_dx=0, head_dy=0):
    """The figure: head (with a lighter top), shirt, gold belt, two arms (shoulder -> hand) and two legs (hip -> foot)."""
    hx, hy = ox + head_dx, oy + head_dy
    for x in (7, 8):
        g.put(hx + x, hy + 1, skin)
    for y in (2, 3):
        for x in (6, 7, 8, 9):
            g.put(hx + x, hy + y, skin if x < 9 else ("e" if skin == "E" else "s"))
    for x in (7, 8):
        g.put(hx + x, hy + 4, "e" if skin == "E" else "s")
    for y in range(5, 10):
        for x in (6, 7, 8, 9):
            g.put(ox + x, oy + y, "d" if x == 9 else "P")
    for x in (6, 7, 8):
        g.put(ox + x, oy + 8, "G")
    g.put(ox + 9, oy + 8, "g")
    for (sx, sy, ex, ey) in arms:
        g.line(ox + sx, oy + sy, ox + ex, oy + ey, "P")
        g.put(ox + ex, oy + ey, skin)
    for (sx, sy, ex, ey) in legs:
        g.line(ox + sx, oy + sy, ox + ex, oy + ey, "P", width=2, shade="d")
        g.put(ox + ex, oy + ey, "d")
        g.put(ox + ex + 1, oy + ey, "d")


def sparkle(g, x, y, key="Y"):
    g.put(x, y, key)


def icon(paint):
    g = Grid()
    paint(g)
    return outlined(g.rows())


# ---------------------------------------------------------------- the gestures

def celebrate(g):
    figure(g, arms=((5, 5, 2, 2), (10, 5, 13, 2)), legs=((6, 10, 4, 12), (9, 10, 10, 12)))
    sparkle(g, 2, 0), sparkle(g, 13, 0, "G")


def wave(g):
    figure(g, arms=((5, 6, 4, 9), (10, 5, 12, 1)), legs=((6, 10, 6, 12), (8, 10, 8, 12)))
    for (x, y) in ((14, 1), (14, 3), (11, 0)):
        sparkle(g, x, y)


def clap(g):
    figure(g, arms=((5, 6, 6, 6), (10, 6, 9, 6)), legs=((6, 10, 6, 12), (8, 10, 8, 12)))
    g.put(7, 6, "S"), g.put(8, 6, "S")
    for (x, y) in ((4, 4), (11, 4), (3, 6), (12, 6)):
        sparkle(g, x, y)


def bow(g):
    # seen from the side, bent over at the waist: legs straight, back flat, head down in front, an arm hanging
    for y in range(9, 14):
        g.put(9, y, "P"), g.put(10, y, "d")
    g.put(9, 14, "d"), g.put(10, 14, "d"), g.put(11, 14, "d")
    for x in range(4, 11):
        for y in (6, 7, 8):
            g.put(x, y, "d" if y == 8 else "P")
    for y in (6, 7, 8):
        g.put(10, y, "G" if y < 8 else "g")
    for (x, y) in ((1, 7), (2, 6), (3, 6), (1, 8), (2, 7), (3, 7), (2, 8), (3, 8), (2, 9), (3, 9)):
        g.put(x, y, "S")
    g.line(5, 9, 5, 11, "P"), g.put(5, 12, "S")
    sparkle(g, 12, 3, "G")


def spin(g):
    figure(g, arms=((5, 5, 1, 5), (10, 5, 14, 5)), legs=((6, 10, 6, 12), (8, 10, 8, 12)))
    for (x, y) in ((2, 2), (3, 1), (12, 1), (13, 2), (2, 9), (13, 9)):
        sparkle(g, x, y, "L")


def facepalm(g):
    figure(g, head_dx=1, arms=((5, 6, 4, 9), (10, 6, 9, 3)), legs=((6, 10, 6, 12), (8, 10, 8, 12)))
    g.put(9, 2, "S"), g.put(10, 2, "S")


def headbang(g):
    figure(g, head_dy=1, arms=((5, 5, 3, 1), (10, 5, 12, 1)), legs=((6, 10, 4, 12), (9, 10, 10, 12)))
    for (x, y) in ((6, 0), (9, 0)):
        sparkle(g, x, y, "L")


def dab(g):
    # head tucked into one elbow, the other arm thrown up and out
    figure(g, head_dx=1, head_dy=1, arms=((10, 5, 12, 3), (5, 5, 1, 1)), legs=((6, 10, 5, 12), (9, 10, 10, 12)))
    g.line(12, 3, 10, 2, "P")
    g.put(10, 2, "S")


def floss(g):
    figure(g, arms=((5, 6, 2, 9), (10, 6, 7, 10)), legs=((6, 10, 7, 12), (9, 10, 10, 12)))
    for (x, y) in ((13, 3), (13, 6)):
        sparkle(g, x, y, "L")


def moonwalk(g):
    figure(g, arms=((5, 6, 3, 9), (10, 6, 12, 9)), legs=((6, 10, 3, 12), (9, 10, 10, 12)))
    g.put(13, 1, "G"), g.put(13, 2, "G"), g.put(14, 1, "G"), g.put(12, 3, "G"), g.put(13, 3, "G")   # a note


def zombie_walk(g):
    figure(g, skin="E", head_dx=1, arms=((10, 5, 14, 5), (10, 6, 14, 6)), legs=((6, 10, 5, 12), (9, 10, 10, 12)))


def sleep(g):
    # lying down, with Zs
    for x in range(6, 13):
        for y in (10, 11):
            g.put(x, y, "P" if y == 10 else "d")
    g.put(8, 10, "G"), g.put(8, 11, "g")
    for (x, y) in ((3, 9), (4, 9), (2, 10), (3, 10), (4, 10), (5, 10), (2, 11), (3, 11), (4, 11), (5, 11)):
        g.put(x, y, "S")
    for x in range(13, 15):
        g.put(x, 10, "d"), g.put(x, 11, "d")
    for (x, y) in ((9, 3), (10, 3), (10, 4), (9, 5), (10, 5), (12, 1), (13, 1), (13, 2), (12, 3), (13, 3)):
        g.put(x, y, "Z")


def swim(g):
    # flat in the water, one arm reaching forward, bubbles
    for x in range(5, 12):
        for y in (8, 9):
            g.put(x, y, "P" if y == 8 else "d")
    g.put(9, 8, "G"), g.put(9, 9, "g")
    for (x, y) in ((2, 7), (3, 7), (1, 8), (2, 8), (3, 8), (4, 8), (1, 9), (2, 9), (3, 9), (4, 9)):
        g.put(x, y, "S")
    g.line(5, 7, 2, 4, "P"), g.put(2, 4, "S")
    g.line(12, 8, 14, 7, "d"), g.line(12, 9, 14, 10, "d")
    for (x, y) in ((6, 4), (8, 3), (7, 1)):
        g.put(x, y, "W")


def twirl_jump(g):
    figure(g, oy=-1, arms=((5, 5, 3, 2), (10, 5, 12, 2)), legs=((6, 10, 6, 11), (8, 10, 8, 11)))
    for x in (3, 4, 11, 12):
        g.put(x, 14, "L")
    g.put(5, 13, "L"), g.put(10, 13, "L")


def levitate(g):
    figure(g, oy=-1, arms=((5, 6, 2, 8), (10, 6, 13, 8)), legs=((6, 10, 6, 11), (8, 10, 8, 11)))
    for (x, y) in ((4, 14), (7, 14), (10, 14), (6, 13), (9, 13)):
        g.put(x, y, "L")


def tiny(g):
    # a small figure, sparkling
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
        g.put(x, y, "S")
    for (x, y) in ((7, 9), (8, 9), (7, 10), (8, 10)):
        g.put(x, y, "P")
    g.put(6, 9, "P"), g.put(9, 9, "P"), g.put(7, 11, "d"), g.put(8, 11, "d")
    for (x, y) in ((4, 5), (11, 5), (3, 10), (12, 10)):
        sparkle(g, x, y)


def magic_trick(g):
    figure(g, oy=1, arms=((5, 6, 4, 9), (10, 5, 12, 3)), legs=((6, 10, 6, 11), (8, 10, 8, 11)))
    for (x, y) in ((6, 0), (7, 0), (8, 0), (9, 0), (5, 1), (6, 1), (7, 1), (8, 1), (9, 1), (10, 1)):
        g.put(x, y, "d")                                            # a top hat
    g.line(12, 3, 14, 1, "W")                                       # the wand, with a spark
    g.put(14, 0, "Y"), g.put(13, 1, "G"), g.put(15, 1, "Y")


def giant(g):
    # big and stomping: the figure fills the square
    for y in range(0, 4):
        for x in range(5, 11):
            g.put(x, y, "S" if x < 10 else "s")
    for y in range(4, 11):
        for x in range(4, 12):
            g.put(x, y, "d" if x == 11 else "P")
    for x in range(4, 11):
        g.put(x, 9, "G")
    g.put(11, 9, "g")
    g.line(4, 5, 1, 9, "P"), g.put(1, 9, "S")
    g.line(11, 5, 14, 9, "P"), g.put(14, 9, "S")
    for x in (4, 5, 6, 9, 10, 11):
        for y in (11, 12, 13):
            g.put(x, y, "P" if x in (4, 5, 9, 10) else "d")
    for x in (3, 4, 5, 6, 9, 10, 11, 12):
        g.put(x, 14, "d")


def explode(g):
    for (x, y) in ((2, 1), (13, 1), (1, 6), (14, 6), (2, 12), (13, 12), (7, 0), (8, 14)):
        g.put(x, y, "F")
    for (x, y) in ((3, 2), (12, 2), (2, 6), (13, 6), (3, 11), (12, 11)):
        g.put(x, y, "Y")
    figure(g, arms=((5, 5, 3, 3), (10, 5, 12, 3)), legs=((6, 10, 4, 12), (9, 10, 10, 12)))


GESTURES = {
    "wave": wave, "clap": clap, "bow": bow, "spin": spin, "facepalm": facepalm, "headbang": headbang,
    "celebrate": celebrate, "dab": dab, "floss": floss, "moonwalk": moonwalk, "zombie_walk": zombie_walk,
    "sleep": sleep, "swim": swim, "twirl_jump": twirl_jump, "levitate": levitate, "tiny": tiny,
    "magic_trick": magic_trick, "giant": giant, "explode": explode,
}


def draw(rows):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, key in enumerate(row):
            if key != ".":
                img.putpixel((x, y), PALETTE[key] + (255,))
    box = img.getbbox()
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    out.paste(img, ((16 - (box[2] - box[0])) // 2 - box[0], (16 - (box[3] - box[1])) // 2 - box[1]))
    return out


if __name__ == "__main__":
    pack = Path(sys.argv[1])
    folder = pack / "assets" / "fractal" / "textures" / "item" / "gesture"
    folder.mkdir(parents=True, exist_ok=True)
    images = {}
    for key, paint in GESTURES.items():
        images[key] = draw(icon(paint))
        images[key].save(folder / f"{key}.png")
    if len(sys.argv) > 2:
        scale, gap, cols = 8, 10, 7
        rows = (len(images) + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * (16 * scale + gap) + gap, rows * (16 * scale + gap) + gap), (54, 48, 66, 255))
        for i, img in enumerate(images.values()):
            sheet.alpha_composite(img.resize((16 * scale, 16 * scale), Image.NEAREST),
                                  (gap + (i % cols) * (16 * scale + gap), gap + (i // cols) * (16 * scale + gap)))
        sheet.save(sys.argv[2])
    print("Drew " + ", ".join(images))
