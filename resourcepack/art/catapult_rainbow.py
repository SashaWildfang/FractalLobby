"""
1.41 models for two gadgets:

  gadget/catapult_base   a small wooden catapult: two rails on four wheels, an upright frame with a crossbar
  gadget/catapult_arm    its throwing arm with the bucket (built along +z from the pivot at the model's middle, so the
                         plugin can swing it round the pivot)
  gadget/rainbow_arc     a big flat rainbow arc (six soft bands), lit, seen from both sides
"""
import json, math, os
from PIL import Image
from vox import Box, write

W, WL, WD, IRON = '#b07a45', '#d8a46a', '#7a5230', '#6a7086'


def build(out):
    base = [Box((4, 2, 1), (5.5, 3.5, 15), W, 'wood'), Box((10.5, 2, 1), (12, 3.5, 15), W, 'wood'),      # the rails
            Box((5.5, 2.2, 2), (10.5, 3.2, 3.5), WD, 'wood'), Box((5.5, 2.2, 12.5), (10.5, 3.2, 14), WD, 'wood'),
            Box((4.5, 3.5, 7), (5.5, 10, 8.5), WD, 'wood'), Box((10.5, 3.5, 7), (11.5, 10, 8.5), WD, 'wood'),   # the frame
            Box((4.5, 9.2, 6.8), (11.5, 10.4, 8.7), W, 'wood'), Box((5.5, 3.6, 10.6), (10.5, 4.4, 11.6), IRON, 'metal')]
    for (x0, x1) in [(2.6, 4), (12, 13.4)]:                                                              # four wheels
        for z in (3.5, 12.5):
            base += [Box((x0, 0.5, z - 1.5), (x1, 3.5, z + 1.5), WD, 'wood'), Box((x0, 0, z - 1), (x1, 4, z + 1), WD, 'wood'),
                     Box((x0 - 0.1, 1.6, z - 0.4), (x1 + 0.1, 2.4, z + 0.4), IRON, 'metal')]
    write(out, 'gadget/catapult_base', base, scale=0.5)
    arm = [Box((7.3, 7.3, 8), (8.7, 8.7, 19), W, 'wood'),                                                  # the beam
           Box((5.5, 7, 18.5), (10.5, 8, 23.5), WD, 'wood'),                                              # the bucket
           Box((5.5, 8, 18.5), (6.5, 10, 23.5), WD, 'wood'), Box((9.5, 8, 18.5), (10.5, 10, 23.5), WD, 'wood'),
           Box((6.5, 8, 18.5), (9.5, 10, 19.5), WD, 'wood'), Box((6.5, 8, 22.5), (9.5, 10, 23.5), WD, 'wood')]
    write(out, 'gadget/catapult_arm', arm, scale=0.5)

    # the rainbow: a 96 x 48 picture of a half circle of six bands, soft at its edges, on a flat sheet 48 x 24 units
    bands = [(255, 77, 77), (255, 154, 46), (255, 225, 77), (91, 227, 124), (91, 157, 255), (155, 92, 255)]
    tex = Image.new('RGBA', (96, 48), (0, 0, 0, 0))
    outer, width = 46.0, 5.0
    for y in range(48):
        for x in range(96):
            d = math.hypot(x + 0.5 - 48, y + 0.5 - 48)
            k = (outer - d) / width
            if 0 <= k < len(bands):
                band = int(k)
                edge = min(k, len(bands) - k)
                alpha = 210 if edge > 0.6 else 140
                tex.putpixel((x, y), bands[band] + (alpha,))
    os.makedirs(f'{out}/assets/fractal/textures/item/gadget', exist_ok=True)
    tex.save(f'{out}/assets/fractal/textures/item/gadget/rainbow_arc.png')
    faces = {f: {"uv": [0, 0, 16, 16], "texture": "#t"} for f in ("north", "south")}
    model = {"textures": {"t": "fractal:item/gadget/rainbow_arc", "particle": "fractal:item/gadget/rainbow_arc"},
             "elements": [{"from": [-16, 0, 8], "to": [32, 24, 8], "shade": False, "light_emission": 15, "faces": faces}]}
    os.makedirs(f'{out}/assets/fractal/models/item/gadget', exist_ok=True)
    json.dump(model, open(f'{out}/assets/fractal/models/item/gadget/rainbow_arc.json', 'w'))
    os.makedirs(f'{out}/assets/fractal/items/gadget', exist_ok=True)
    json.dump({"model": {"type": "minecraft:model", "model": "fractal:item/gadget/rainbow_arc"}},
              open(f'{out}/assets/fractal/items/gadget/rainbow_arc.json', 'w'))
