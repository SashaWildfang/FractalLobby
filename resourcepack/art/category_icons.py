"""
Lobby resource pack - built by Sasha Wildfang for Fractal Network.

The seven section icons on the Collectibles main menu, in the server's look: purple and gold, lit from the top
left, a crisp near-black outline (like the Mystery Vault icons), and each one centred in its 32x32 square.

  fractal:category/hats      a purple top hat with a gold band
  fractal:category/suits     a purple jacket with gold trim
  fractal:category/gadgets   a little rocket blasting off
  fractal:category/cloaks    a flowing cape with a gold clasp
  fractal:category/emotes    a speech bubble with a heart
  fractal:category/gestures  a waving hand
  fractal:category/trails    a sparkling swoosh

    python category_icons.py <FractalLobby folder> [preview.png]
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

from kit import Canvas, dark, light, sheet
from mystery_icons import inked

PURPLE, LILAC, DEEP, GOLD, PINK, WHITE, SKY = '#9B5CFF', '#C9A6FF', '#5B2BB0', '#FFCF5A', '#FF6FB5', '#F4F4FA', '#7FD4FF'


def centred(img):
    """Moves the drawing so its visible part sits in the middle of the square."""
    arr = np.array(img)
    ys, xs = np.nonzero(arr[..., 3])
    if len(xs) == 0:
        return img
    w, h = img.size
    dx = round((w - 1 - xs.max() - xs.min()) / 2)
    dy = round((h - 1 - ys.max() - ys.min()) / 2)
    out = Image.new('RGBA', img.size, (0, 0, 0, 0))
    out.paste(img, (dx, dy))
    return out


def hats():
    c = Canvas()
    c.shape('ellipse', (4, 21, 28, 28), ('lin', light(DEEP, 0.15), dark(DEEP, 0.2), 90))          # brim
    c.solid('poly', [(9, 8), (23, 8), (22, 24), (10, 24)], PURPLE, angle=0, strength=0.32)    # crown
    c.shape('ellipse', (9, 5.5, 23, 10.5), ('lin', light(PURPLE, 0.45), PURPLE, 90))         # top
    c.shape('poly', [(9.7, 17.5), (22.3, 17.5), (22.1, 21), (9.9, 21)], ('lin', light(GOLD, 0.25), dark(GOLD, 0.2), 0))
    c.shape('rect', (12, 17.5, 14, 21), light(GOLD, 0.55))                                     # shine on the band
    c.star(25.5, 6.5, 3, WHITE)
    return c


def suits():
    c = Canvas()
    body = [(8, 8), (12.5, 5), (16, 9), (19.5, 5), (24, 8), (28, 17), (24, 18.5), (22.5, 14), (22.5, 28), (9.5, 28),
            (9.5, 14), (8, 18.5), (4, 17)]
    c.solid('poly', body, PURPLE, angle=20, strength=0.3)
    c.shape('poly', [(12.5, 5), (16, 9), (19.5, 5), (17.5, 4.4), (16, 6), (14.5, 4.4)], dark(DEEP, 0.2))   # collar
    c.shape('rect', (15.2, 9, 16.8, 28), ('lin', light(GOLD, 0.3), dark(GOLD, 0.15), 90))         # trim
    c.shape('rect', (9.5, 20.5, 22.5, 23), ('lin', light(GOLD, 0.2), dark(GOLD, 0.25), 0))        # belt
    c.shape('rect', (14.6, 20, 17.4, 23.5), dark(GOLD, 0.45))                                     # buckle
    for y in (12, 15.5):
        c.shape('ellipse', (13, y, 14.6, y + 1.6), GOLD)                                          # buttons
    return c


def gadgets():
    c = Canvas()
    c.shape('poly', [(12.5, 23), (16, 31), (19.5, 23)], ('lin', '#FFE680', '#FF8A2E', 90))        # flame
    c.shape('poly', [(12, 15), (6.5, 24), (12, 23)], ('lin', light(GOLD, 0.2), dark(GOLD, 0.3), 60))
    c.shape('poly', [(20, 15), (25.5, 24), (20, 23)], ('lin', GOLD, dark(GOLD, 0.35), 60))
    c.solid('poly', [(11.5, 10), (20.5, 10), (21, 24), (11, 24)], PURPLE, angle=0, strength=0.35)  # body
    c.shape('poly', [(11.5, 10.5), (16, 2), (20.5, 10.5)], ('lin', light(GOLD, 0.35), dark(GOLD, 0.2), 30))   # nose
    c.shape('ellipse', (12.8, 12, 19.2, 18.4), dark(DEEP, 0.3))                                    # window
    c.shape('ellipse', (13.6, 12.8, 18.4, 17.6), ('lin', light(SKY, 0.4), SKY, 45))
    c.star(5.5, 8, 2.6, WHITE)
    c.star(26.5, 9, 2, GOLD)
    return c


def cloaks():
    c = Canvas()
    cape = [(10, 6), (22, 6), (26, 18), (28, 28), (22, 25.5), (16, 28), (10, 25.5), (4, 28), (6, 18)]
    c.shape('poly', cape, ('lin', light(PURPLE, 0.25), dark(PURPLE, 0.35), 80))
    c.shape('poly', [(12, 7), (20, 7), (22.5, 18), (16, 21), (9.5, 18)], ('lin', dark(DEEP, 0.15), dark(DEEP, 0.4), 90))  # lining
    for x in (10.5, 16, 21.5):                                                                     # folds
        c.line([(x, 20), (x + (x - 16) * 0.25, 27)], light(PURPLE, 0.35), 0.9, opacity=0.6)
    c.shape('rect', (9, 4.5, 23, 7.5), ('lin', light(PURPLE, 0.4), PURPLE, 90))                   # collar
    c.ball(16, 7, 2.6, GOLD)                                                                       # clasp
    return c


def emotes():
    c = Canvas()
    c.shape('poly', [(9, 20), (7, 28), (15, 21.5)], ('lin', light(LILAC, 0.3), LILAC, 90))        # tail
    c.shape('rrect', (3, 4, 29, 23, 6), ('lin', light(LILAC, 0.55), LILAC, 70))                  # bubble
    c.shape('ellipse', (9.5, 8, 16.5, 15), ('lin', light(PINK, 0.35), PINK, 60))                  # heart
    c.shape('ellipse', (15.5, 8, 22.5, 15), ('lin', PINK, dark(PINK, 0.15), 60))
    c.shape('poly', [(10, 12.6), (22, 12.6), (16, 19.6)], ('lin', PINK, dark(PINK, 0.25), 90))
    c.shape('ellipse', (11, 9.4, 13.4, 11.6), WHITE, opacity=0.85)                                 # shine
    return c


def gestures():
    c = Canvas()
    skin, shade = '#F5D9C0', '#D9A982'
    for x0, top in ((9.5, 6), (12.8, 3.5), (16.1, 3.8), (19.4, 6.2)):                            # fingers
        c.shape('rrect', (x0, top, x0 + 3, 17, 1.5), ('lin', light(skin, 0.2), shade, 0))
    c.shape('rrect', (9.5, 12, 22.4, 25, 3.5), ('lin', light(skin, 0.25), shade, 30))             # palm
    c.shape('poly', [(10, 15), (5.5, 11.5), (4.2, 13.4), (8.5, 20)], ('lin', light(skin, 0.15), shade, 0))   # thumb
    c.shape('rect', (10, 24, 22, 28.5), ('lin', light(PURPLE, 0.3), dark(PURPLE, 0.2), 90))       # sleeve
    c.shape('rect', (10, 24, 22, 25.4), GOLD)                                                      # cuff
    for side in (-1, 1):                                                                            # waving lines
        cx = 16 + side * 13.5
        c.line([(cx, 6), (cx + side * 1.2, 9.5), (cx, 13)], LILAC, 1.2)
    return c


def trails():
    c = Canvas()
    # a stream of sparkles growing and warming from purple to gold, behind a big star
    path = [(4.5, 27.5), (9, 24.5), (13.5, 21), (18, 16.5)]
    colours = [PURPLE, LILAC, PINK, GOLD]
    for i, ((x, y), col) in enumerate(zip(path, colours)):
        c.ball(x, y, 1.0 + i * 0.35, col, shine=i > 1)
    c.star(8.5, 18.5, 1.8, LILAC)
    c.star(16.5, 26.5, 2.0, PINK)
    c.star(4, 21, 1.2, PURPLE)
    c.star(24.5, 9, 7, GOLD, inner=0.42)
    c.star(24.5, 9, 2.6, light(GOLD, 0.7))
    return c


# Hats, suits, gadgets, cloaks and gestures are now 16x16 pixel art in section_icons.py; these two stay as they are.
ICONS = {"emotes": emotes, "trails": trails}


def save(pack: Path, name: str, image):
    texture = pack / "assets" / "fractal" / "textures" / "item" / "category" / f"{name}.png"
    model = pack / "assets" / "fractal" / "models" / "item" / "category" / f"{name}.json"
    item = pack / "assets" / "fractal" / "items" / "category" / f"{name}.json"
    for path in (texture, model, item):
        path.parent.mkdir(parents=True, exist_ok=True)
    image.save(texture)
    model.write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"fractal:item/category/{name}"}}))
    item.write_text(json.dumps({"model": {"type": "minecraft:model", "model": f"fractal:item/category/{name}"}}))


def main():
    pack = Path(sys.argv[1])
    images = {name: centred(inked(paint())) for name, paint in ICONS.items()}
    for name, image in images.items():
        save(pack, name, image)
    if len(sys.argv) > 2:
        sheet(list(images.values()), scale=6, cols=7).save(sys.argv[2])
    print("Drew " + ", ".join(f"fractal:category/{n}" for n in images))


if __name__ == "__main__":
    main()
