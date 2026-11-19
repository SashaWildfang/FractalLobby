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

W, WL, WD, IRON = '#b5803f', '#d9a85f', '#7d5530', '#6a7086'


def build(out):
    # smooth, flat-coloured wood (one texel a unit, no speckles) so it reads clean at any distance
    def wood(f, t, c=W):
        return Box(f, t, c, 'flat')
    base = [wood((3.5, 2.5, 1), (5.5, 4.5, 15)), wood((10.5, 2.5, 1), (12.5, 4.5, 15)),                 # the rails
            wood((5.5, 3, 2), (10.5, 4, 3.5), WD), wood((5.5, 3, 12.5), (10.5, 4, 14), WD),               # cross pieces
            wood((4, 4.5, 6.5), (5.5, 11, 8.5)), wood((10.5, 4.5, 6.5), (12, 11, 8.5)),                   # the uprights
            wood((3.5, 11, 6.2), (12.5, 12.4, 8.8), WL),                                                  # the crossbar
            wood((6.5, 12.4, 6.6), (9.5, 13, 8.4), WD),                                                   # its pad
            Box((5.5, 4.5, 10.4), (10.5, 5.5, 11.6), IRON, 'flat')]                                        # the winch
    for (x0, x1) in [(1.8, 3.5), (12.5, 14.2)]:                                                          # four round wheels
        for z in (3.5, 12.5):
            base += [wood((x0, 0.8, z - 2), (x1, 3.2, z + 2), WD), wood((x0, 0, z - 1.2), (x1, 4, z + 1.2), WD),
                     wood((x0, 0.3, z - 1.7), (x1, 3.7, z + 1.7), WD),
                     Box((x0 - 0.15, 1.4, z - 0.6), (x1 + 0.15, 2.6, z + 0.6), IRON, 'flat')]
    write(out, 'gadget/catapult_base', base, scale=1)
    arm = [wood((7.2, 7.2, 8), (8.8, 8.8, 20)),                                                          # the beam
           Box((7.0, 7.0, 12), (9.0, 9.0, 13), IRON, 'flat'),                                             # an iron band
           wood((5, 6.6, 19), (11, 7.6, 25), WD),                                                         # the bucket
           wood((5, 7.6, 19), (6, 10, 25)), wood((10, 7.6, 19), (11, 10, 25)),
           wood((6, 7.6, 19), (10, 10, 20)), wood((6, 7.6, 24), (10, 10, 25))]
    write(out, 'gadget/catapult_arm', arm, scale=1)

    # the rainbow's clouds: a soft round cloud sheet, finely drawn (no blocks), shown facing the player
    cloud = Image.new('RGBA', (64, 40), (0, 0, 0, 0))   # drawn 64 x 40, then set at the bottom of a square sprite
    puffs = [(14, 27, 11), (27, 20, 14), (41, 22, 12), (52, 28, 9), (33, 29, 12), (20, 31, 8), (46, 31, 8)]
    tones = [(255, 255, 255), (241, 245, 255), (222, 230, 248), (198, 208, 236)]
    for y in range(40):
        for x in range(64):
            px, py = x + 0.5, y + 0.5
            inside = [(cx, cy, r) for (cx, cy, r) in puffs if (px - cx) ** 2 + (py - cy) ** 2 <= r * r]
            if not inside or py > 37:
                continue
            # lit from the top left: light near each puff's upper left, deeper towards the bottom of the cloud
            best = min(((px - (cx - r * 0.35)) ** 2 + (py - (cy - r * 0.45)) ** 2) ** 0.5 / r for (cx, cy, r) in inside)
            shade = 0 if best < 0.55 else 1 if best < 0.95 else 2
            if py > 33:
                shade = max(shade, 3 if py > 35 else 2)
            cloud.putpixel((x, y), tones[shade] + (255,))
    square = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    square.alpha_composite(cloud, (0, 26))
    os.makedirs(f'{out}/assets/fractal/textures/item/gadget', exist_ok=True)
    square.save(f'{out}/assets/fractal/textures/item/gadget/rainbow_cloud.png')
    # a plain flat item sprite: lit like the game's own items (a bare sheet came out grey and flickered)
    os.makedirs(f'{out}/assets/fractal/models/item/gadget', exist_ok=True)
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "fractal:item/gadget/rainbow_cloud"}},
              open(f'{out}/assets/fractal/models/item/gadget/rainbow_cloud.json', 'w'))
    os.makedirs(f'{out}/assets/fractal/items/gadget', exist_ok=True)
    json.dump({"model": {"type": "minecraft:model", "model": "fractal:item/gadget/rainbow_cloud"}},
              open(f'{out}/assets/fractal/items/gadget/rainbow_cloud.json', 'w'))

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
