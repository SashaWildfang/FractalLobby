"""
Lobby resource pack - built by Sasha Wildfang for Fractal Network.

The flat (2D) Mystery Box icons for the menus, painted with kit.Canvas like the collectible icons (32x32, lit from
the top left, dark outline). The 3D boxes (fractal:mystery/box_1..5) stay for the opening animation.

  fractal:mystery/gift_1..5  a gift box in its star colour (gold ribbon; the gold 5-star box gets a purple one)
  fractal:mystery/empty      "No boxes yet": an open, empty box with its lid set aside
  fractal:mystery/craft      "Craft Boxes": Mystery Dust swirling into a new box
  fractal:mystery/open_all   "Open Everything": a stack of boxes bursting open

    python mystery_icons.py <FractalLobby folder>
"""
import json
import sys
from pathlib import Path

from kit import Canvas, dark, light, sheet

BOX_COLOURS = {1: '#9C8DB8', 2: '#5BE37C', 3: '#7FA8FF', 4: '#C77DFF', 5: '#FFB347'}
GOLD, PURPLE, DUST, WHITE = '#FFD27F', '#B266FF', '#FFE6A8', '#F4F4FA'


def ribbon_for(stars):
    return PURPLE if stars == 5 else GOLD


def gift(c, colour, ribbon, ox=0.0, oy=0.0, k=1.0, bow=True):
    """A gift box seen from the front corner (top, left and right faces), with a ribbon and a bow.
    Drawn in a 32x32 box, moved by (ox, oy) and scaled by k."""
    def p(x, y):
        return (ox + x * k, oy + y * k)
    top = [p(16, 7), p(28, 12), p(16, 17), p(4, 12)]
    left = [p(4, 12), p(16, 17), p(16, 30), p(4, 25)]
    right = [p(16, 17), p(28, 12), p(28, 25), p(16, 30)]
    c.shape('poly', left, ('lin', light(colour, 0.12), dark(colour, 0.12), 90))
    c.shape('poly', right, ('lin', dark(colour, 0.2), dark(colour, 0.38), 90))
    c.shape('poly', top, ('lin', light(colour, 0.38), light(colour, 0.12), 60))
    # the ribbon down each side and across the top
    c.shape('poly', [p(9, 14), p(11.5, 15), p(11.5, 28), p(9, 27)], ('lin', light(ribbon, 0.1), dark(ribbon, 0.1), 90))
    c.shape('poly', [p(20.5, 15.4), p(23, 14.4), p(23, 27.4), p(20.5, 28.4)], ('lin', dark(ribbon, 0.18), dark(ribbon, 0.32), 90))
    c.shape('poly', [p(9, 14), p(11.5, 15), p(23, 10), p(20.5, 9)], light(ribbon, 0.25))
    c.shape('poly', [p(20.5, 15.4), p(23, 14.4), p(11.5, 9.4), p(9, 10.4)], light(ribbon, 0.18))
    # a soft shine on the front edge
    c.line([p(16, 18), p(16, 29)], light(colour, 0.5), 0.8 * k, opacity=0.35)
    if bow:
        # two tails falling onto the lid, then the loops of the bow and the knot
        c.shape('poly', [p(15.2, 10), p(11.2, 14.2), p(13.2, 15), p(16.4, 11)], dark(ribbon, 0.15))
        c.shape('poly', [p(16.8, 10), p(20.6, 14.4), p(18.6, 15.1), p(15.8, 11)], dark(ribbon, 0.3))
        left_loop = [p(16, 9.6), p(11.5, 3.2), p(7.6, 4.4), p(7.4, 8.4), p(11, 10.6)]
        right_loop = [p(16, 9.6), p(20.5, 3.2), p(24.4, 4.4), p(24.6, 8.4), p(21, 10.6)]
        c.shape('poly', left_loop, ('lin', light(ribbon, 0.4), ribbon, 70))
        c.shape('poly', right_loop, ('lin', ribbon, dark(ribbon, 0.3), 70))
        c.shape('poly', [p(15, 9.2), p(11.2, 5.4), p(9.6, 6.4), p(11.4, 8.8)], dark(ribbon, 0.35))
        c.shape('poly', [p(17, 9.2), p(20.8, 5.4), p(22.4, 6.4), p(20.6, 8.8)], dark(ribbon, 0.45))
        c.shape('ellipse', (*p(14, 7.6), *p(18, 11.4)), ('lin', light(ribbon, 0.3), dark(ribbon, 0.25), 60))

def gift_icon(stars):
    c = Canvas()
    gift(c, BOX_COLOURS[stars], ribbon_for(stars))
    return c.done()


def empty_icon():
    """An open box with its flaps up and nothing inside."""
    c = Canvas()
    colour = BOX_COLOURS[1]
    # the back flaps, standing open
    c.shape('poly', [(4, 13), (16, 8), (14.5, 2.5), (2.5, 7.5)], ('lin', light(colour, 0.3), colour, 80))
    c.shape('poly', [(16, 8), (28, 13), (29.5, 7.5), (17.5, 2.5)], ('lin', colour, dark(colour, 0.25), 80))
    # the empty inside, in shadow
    c.shape('poly', [(4, 13), (16, 8), (28, 13), (16, 18)], dark(colour, 0.72))
    c.shape('poly', [(7, 13.2), (16, 9.6), (25, 13.2), (16, 16.8)], dark(colour, 0.6))
    # the front walls, with a light rim
    c.shape('poly', [(4, 13), (16, 18), (16, 30), (4, 25)], ('lin', light(colour, 0.12), dark(colour, 0.12), 90))
    c.shape('poly', [(16, 18), (28, 13), (28, 25), (16, 30)], ('lin', dark(colour, 0.2), dark(colour, 0.38), 90))
    c.line([(4, 13), (16, 18), (28, 13)], light(colour, 0.5), 0.9)
    # a speck of dust left behind
    c.star(14, 13.2, 1.6, DUST, opacity=0.85)
    return c.done()


def craft_icon():
    """Mystery Dust swirling into a new box: a small box with sparkles and a gold +."""
    c = Canvas()
    gift(c, BOX_COLOURS[3], GOLD, ox=5, oy=7, k=0.78)
    # the dust: a swirl of gold specks coming in from the top left
    for (x, y, r) in ((4, 5, 1.6), (7.5, 2.5, 1.1), (3, 10, 1.0), (10.5, 5.5, 0.9)):
        c.ball(x, y, r, '#FFD27F', shine=False)
    c.star(13, 3, 2.4, WHITE)
    # the plus
    c.shape('rect', (25, 2, 28, 11), ('lin', light(GOLD, 0.3), dark(GOLD, 0.2), 90))
    c.shape('rect', (22, 5, 31, 8), ('lin', light(GOLD, 0.3), dark(GOLD, 0.2), 0))
    return c.done()


def open_all_icon():
    """A pile of boxes, the top one bursting open with light."""
    c = Canvas()
    gift(c, BOX_COLOURS[2], GOLD, ox=-1, oy=13, k=0.6, bow=False)
    gift(c, BOX_COLOURS[4], GOLD, ox=14, oy=13, k=0.6, bow=False)
    # a burst of light from the top box
    for (x, y, r) in ((16, 4, 3.4), (8, 7, 2.0), (25, 6, 2.2)):
        c.star(x, y, r, WHITE)
    gift(c, BOX_COLOURS[5], PURPLE, ox=6.5, oy=6, k=0.6, bow=True)
    return c.done()


def save(pack: Path, name: str, image):
    texture = pack / "assets" / "fractal" / "textures" / "item" / "mystery" / f"{name}.png"
    model = pack / "assets" / "fractal" / "models" / "item" / "mystery" / f"{name}.json"
    item = pack / "assets" / "fractal" / "items" / "mystery" / f"{name}.json"
    for path in (texture, model, item):
        path.parent.mkdir(parents=True, exist_ok=True)
    image.save(texture)
    model.write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"fractal:item/mystery/{name}"}}))
    item.write_text(json.dumps({"model": {"type": "minecraft:model", "model": f"fractal:item/mystery/{name}"}}))


def main():
    pack = Path(sys.argv[1])
    icons = {f"gift_{stars}": gift_icon(stars) for stars in BOX_COLOURS}
    icons.update({"empty": empty_icon(), "craft": craft_icon(), "open_all": open_all_icon()})
    for name, image in icons.items():
        save(pack, name, image)
    if len(sys.argv) > 2:
        sheet(list(icons.values()), scale=6, cols=8).save(sys.argv[2])
    print("Drew " + ", ".join(f"fractal:mystery/{n}" for n in icons))


if __name__ == "__main__":
    main()
