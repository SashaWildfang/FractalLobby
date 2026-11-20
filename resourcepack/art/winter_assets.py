"""Let It Snow's cloud, the Ice Skates (an equipment asset: white boots with laces and silver blades, worn on the
feet), and their sounds (made by make_winter_sounds())."""
import json, os
from PIL import Image


def skates_layer():
    """The humanoid armour layer (64 x 32) with only the bottom of the legs painted: skates."""
    im = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    px = im.load()
    def c(h):
        return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)
    WHITE, SHADE, CUFF, LACE, BLADE, BLADE_L, SOLE = c('#f4f6ff'), c('#d4dcec'), c('#7fc8ff'), c('#4a5468'), c('#b8c4d6'), c('#eef3fa'), c('#8a96aa')
    for ox in (0,):                                                    # the leg strip (both legs use it)
        for y in range(24, 32):
            for x in range(ox, ox + 16):
                face = (x - ox) // 4                                   # 0 right side, 1 front, 2 left side, 3 back
                col = SHADE if face in (0, 3) else WHITE
                if y == 24:
                    col = CUFF
                if y == 30:
                    col = SOLE
                if y == 31:
                    col = BLADE_L if face == 1 else BLADE
                if face == 1 and y in (25, 27, 29) and (x - ox - 4) in (1, 2):
                    col = LACE
                px[x, y] = col
        for y in range(16, 20):                                        # the bottom of the foot: the blade
            for x in range(8, 12):
                px[x, y] = BLADE_L if x in (9, 10) else SOLE
    return im


def write(out):
    os.makedirs(f'{out}/assets/fractal/equipment', exist_ok=True)
    os.makedirs(f'{out}/assets/fractal/textures/entity/equipment/humanoid', exist_ok=True)
    skates_layer().save(f'{out}/assets/fractal/textures/entity/equipment/humanoid/ice_skates.png')
    json.dump({"layers": {"humanoid": [{"texture": "fractal:ice_skates"}]}}, open(f'{out}/assets/fractal/equipment/ice_skates.json', 'w'))


def snow_cloud(Box, ball):
    W, M, D, DD = '#ffffff', '#eef3fc', '#c3cfe6', '#a9b8d6'
    boxes = []
    for (cx, cy, cz, r, col) in [(8, 9, 8, 5, W), (3.5, 8, 8, 3.8, M), (12.5, 8, 8, 4, M), (6, 11, 7, 3.6, W), (10.5, 11.5, 9, 3.4, W),
                                 (8, 8, 4, 3.5, M), (8, 8, 12, 3.5, M), (4.5, 8, 11.5, 2.8, M), (11.5, 8, 4.5, 2.8, M)]:
        boxes += ball(cx, cy, cz, r, col, 'flat', glow=None)
    boxes += [Box((0.6, 5.2, 3.2), (15.4, 6.0, 12.8), D, 'flat'), Box((2.2, 4.5, 4.6), (13.8, 5.2, 11.4), DD, 'flat')]
    return boxes
