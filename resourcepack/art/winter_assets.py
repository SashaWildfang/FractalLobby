"""Let It Snow's cloud, the Ice Skates (an equipment asset: white boots with laces and silver blades, worn on the
feet), and their sounds (made by make_winter_sounds())."""
import json, os
from PIL import Image


def skates_layer():
    """The humanoid armour layer (64 x 32), only the bottom of the legs painted: a tall white figure-skate boot with a
    fuzzy blue cuff and criss-cross laces, a dark sole, the blade holder (posts, the leg showing between them) and a
    bright silver blade along the bottom, with a toe pick at the front."""
    im = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    px = im.load()
    def c(h):
        return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)
    WHITE, SHADE, CUFF, CUFF_L, LACE = c('#f6f8ff'), c('#d8e0ee'), c('#8fd0ff'), c('#c8ecff'), c('#3a4458')
    SOLE, POST, BLADE, BLADE_L = c('#3a3f4e'), c('#9aa6ba'), c('#c4ccda'), c('#ffffff')
    for x in range(0, 16):
        face, i = x // 4, x % 4                                        # 0 right side, 1 front, 2 left side, 3 back
        side = face in (0, 2)
        for y in range(21, 32):
            col = None
            if y == 21:
                col = CUFF_L if (x % 2) else CUFF
            elif y <= 27:
                col = SHADE if face == 3 else WHITE
                if face == 1 and y >= 22 and ((y % 2 == 0 and i in (1, 2)) or (y % 2 == 1 and i in (0, 3))):
                    col = LACE
            elif y == 28:
                col = SOLE
            elif y in (29, 30):
                if side and i in (0, 3):
                    col = POST
                elif not side and i in (1, 2) and y == 29:
                    col = POST
            else:                                                      # the blade
                if side:
                    col = BLADE_L if i in (1, 2) else BLADE
                elif face == 1 and i in (1, 2):
                    col = BLADE                                        # the toe pick
            if col:
                px[x, y] = col
    for y in range(16, 20):                                            # under the foot: the blade's edge
        for x in range(8, 12):
            px[x, y] = BLADE_L if x in (9, 10) else SOLE
    return im


def write(out):
    os.makedirs(f'{out}/assets/fractal/equipment', exist_ok=True)
    os.makedirs(f'{out}/assets/fractal/textures/entity/equipment/humanoid', exist_ok=True)
    skates_layer().save(f'{out}/assets/fractal/textures/entity/equipment/humanoid/ice_skates.png')
    json.dump({"layers": {"humanoid": [{"texture": "fractal:ice_skates"}]}}, open(f'{out}/assets/fractal/equipment/ice_skates.json', 'w'))


def snow_cloud(Box, ball):
    """A soft, rounded cloud built like a heightmap: one column per unit, its height the top of a few overlapping
    domes (so the top is smooth and puffy, not boxes), a flat bottom with a cool grey-blue underside, gentle shading
    from the bright top down. 14 wide, 10 deep."""
    import math
    W, M, D = '#ffffff', '#eef3fc', '#c6d2e8'
    domes = [(8, 8, 4.6, 4.0), (4.3, 8.3, 3.2, 2.8), (11.8, 7.8, 3.4, 3.0), (6.4, 6.0, 2.8, 2.4), (9.8, 10.0, 2.8, 2.6)]
    base = 5.0
    boxes = []
    for x in range(1, 15):
        for z in range(3, 13):
            cx, cz = x + 0.5, z + 0.5
            top = 0
            for (dx, dz, r, h) in domes:
                d2 = ((cx - dx) / r) ** 2 + ((cz - dz) / (r * 0.85)) ** 2
                if d2 < 1:
                    top = max(top, base + 1.2 + h * math.sqrt(1 - d2))
            if top <= 0:
                continue
            top = round(top * 2) / 2
            edge = top < base + 2.2
            boxes.append(Box((x, base + 0.6, z), (x + 1, top, z + 1), M if edge else W, 'flat'))
            boxes.append(Box((x, base, z), (x + 1, base + 0.6, z + 1), D, 'flat'))
    return boxes
