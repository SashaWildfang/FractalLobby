"""
The cookie fountain (1.41 revamp): a big round three-tier fountain of cream ceramic dipped in chocolate, with cookies
leaning on the basin, and chocolate milk that really flows: it spills over each bowl in a falling curtain into the one
below, ripples in the pools and springs up out of the spout at the top.

Two models, shown on top of each other by the plugin:
  gadget/cookie_fountain_3d    the fountain itself (vox boxes, round from every side)
  gadget/cookie_fountain_flow  the chocolate milk: animated textures (falling streaks, rippling pools)
"""
import json, math, os, random
from PIL import Image
from vox import Box, write

CREAM, CREAM2, CHOC, DOUGH, CHIP = '#f2e9da', '#e6d8c2', '#5a2e14', '#c98a4b', '#3a1a0a'


def ring(cx, cz, r_out, r_in, y0, y1, colour, pattern='solid', step=1.0):
    """A round ring (or a disc when r_in is 0) as rows of boxes: round from every side, few boxes."""
    boxes = []
    z = cz - r_out
    while z < cz + r_out - 1e-6:
        mz = z + step / 2 - cz
        if abs(mz) < r_out:
            half = math.sqrt(r_out * r_out - mz * mz)
            half = round(half / (step / 2)) * (step / 2)
            if half > 0:
                if r_in > 0 and abs(mz) < r_in:
                    inner = round(math.sqrt(r_in * r_in - mz * mz) / (step / 2)) * (step / 2)
                    if half - inner > 1e-6:
                        boxes.append(Box((cx - half, y0, z), (cx - inner, y1, z + step), colour, pattern))
                        boxes.append(Box((cx + inner, y0, z), (cx + half, y1, z + step), colour, pattern))
                else:
                    boxes.append(Box((cx - half, y0, z), (cx + half, y1, z + step), colour, pattern))
        z += step
    return boxes


def drips(cx, cz, r, y, count, seed):
    """Chocolate drips running down the outside of a rim."""
    rnd = random.Random(seed)
    out = []
    for i in range(count):
        a = 2 * math.pi * (i + rnd.random() * 0.5) / count
        x, z = cx + math.cos(a) * (r + 0.05), cz + math.sin(a) * (r + 0.05)
        length = rnd.uniform(0.6, 1.8)
        out.append(Box((x - 0.35, y - length, z - 0.35), (x + 0.35, y, z + 0.35), CHOC, 'solid'))
    return out


def cookie(cx, cy, cz, r, facing_z, seed):
    """A big chocolate chip cookie standing on its edge (its flat side facing z, or x)."""
    rnd = random.Random(seed)
    out = []
    t = 0.7
    y = cy - r
    while y < cy + r - 1e-6:
        my = y + 0.5 - cy
        half = round(math.sqrt(max(0, r * r - my * my)) * 2) / 2
        if half > 0:
            if facing_z:
                out.append(Box((cx - half, y, cz - t / 2), (cx + half, y + 1, cz + t / 2), DOUGH, 'solid'))
            else:
                out.append(Box((cx - t / 2, y, cz - half), (cx + t / 2, y + 1, cz + half), DOUGH, 'solid'))
        y += 1
    for _ in range(6):                                    # chips on both faces
        a, d = rnd.uniform(0, 6.28), rnd.uniform(0, r - 0.8)
        u, v = math.cos(a) * d, math.sin(a) * d
        for side in (-1, 1):
            if facing_z:
                z = cz + side * (t / 2 + 0.05)
                out.append(Box((cx + u - 0.35, cy + v - 0.35, min(z, cz + side * t / 2)), (cx + u + 0.35, cy + v + 0.35, max(z, cz + side * t / 2)), CHIP, 'solid'))
            else:
                x = cx + side * (t / 2 + 0.05)
                out.append(Box((min(x, cx + side * t / 2), cy + v - 0.35, cz + u - 0.35), (max(x, cx + side * t / 2), cy + v + 0.35, cz + u + 0.35), CHIP, 'solid'))
    return out


def body():
    b = []
    b += ring(8, 8, 9.6, 0, 0, 1, CREAM2)                     # basin floor
    b += ring(8, 8, 9.6, 8.6, 1, 3.2, CREAM)                  # basin wall
    b += ring(8, 8, 10.0, 8.4, 3.2, 3.8, CHOC)                # chocolate-dipped lip
    b += drips(8, 8, 10.0, 3.3, 14, 1)
    b += ring(8, 8, 1.8, 0, 1, 7.6, CREAM)                    # pedestal
    b += ring(8, 8, 2.1, 0, 4, 4.6, CHOC)
    b += ring(8, 8, 4.0, 0, 7.4, 8.2, CREAM2)                 # middle bowl
    b += ring(8, 8, 6.2, 5.2, 8.2, 9.8, CREAM)
    b += ring(8, 8, 5.2, 4.0, 8.0, 8.4, CREAM2)
    b += ring(8, 8, 6.5, 5.0, 9.8, 10.3, CHOC)
    b += drips(8, 8, 6.5, 9.9, 10, 2)
    b += ring(8, 8, 1.1, 0, 9.6, 12.2, CREAM)                 # upper column
    b += ring(8, 8, 2.6, 0, 12.0, 12.6, CREAM2)               # top bowl
    b += ring(8, 8, 3.8, 3.0, 12.6, 13.8, CREAM)
    b += ring(8, 8, 4.0, 2.9, 13.8, 14.2, CHOC)
    b += drips(8, 8, 4.0, 13.9, 7, 3)
    b += ring(8, 8, 0.75, 0, 13.0, 15.4, CREAM)               # the spout
    b += ring(8, 8, 1.0, 0, 15.4, 15.8, CHOC)
    # four big cookies leaning on the basin
    b += cookie(8, 2.6, 8 + 10.6, 2.6, True, 11) + cookie(8, 2.6, 8 - 10.6, 2.6, True, 12)
    b += cookie(8 + 10.6, 2.6, 8, 2.6, False, 13) + cookie(8 - 10.6, 2.6, 8, 2.6, False, 14)
    return b


# ---------------------------------------------------------------- the flowing chocolate milk

MILK, MILK_LIGHT, MILK_DARK = (138, 85, 48), (196, 146, 104), (104, 58, 28)


def flow_texture(frames=8):
    """A sheet of chocolate milk pouring over a rim, like a chocolate fountain: see-through enough to show the bowl
    behind, with lighter streaks and blobs sliding down it (one frame a step) and a frilly, dripping bottom edge."""
    rnd = random.Random(5)
    phase = [rnd.uniform(0, 6.28) for _ in range(16)]
    img = Image.new('RGBA', (16, 16 * frames), (0, 0, 0, 0))
    for f in range(frames):
        for y in range(16):
            for x in range(16):
                w = math.sin(2 * math.pi * (y - f * 2) / 16 + phase[x])
                col = MILK_LIGHT if w > 0.75 else MILK_DARK if w < -0.6 else MILK
                alpha = 205 if w > 0.75 else 175
                img.putpixel((x, f * 16 + y), col + (alpha,))
    return img


def pool_texture(frames=6):
    """A pool of chocolate milk with rings rippling out from the middle and a swirl of milk."""
    img = Image.new('RGBA', (16, 16 * frames))
    for f in range(frames):
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                a = math.atan2(y - 7.5, x - 7.5)
                w = math.sin(d * 1.1 - f * math.pi / 3)
                swirl = math.sin(a * 2 + d * 0.6 - f * 0.5)
                col = MILK_LIGHT if w > 0.7 else MILK_DARK if w < -0.75 else MILK
                if swirl > 0.93 and 2 < d < 7:
                    col = (226, 196, 164)
                img.putpixel((x, f * 16 + y), col + (255,))
    return img


def flow_elements():
    els = []

    def pool(r, y):
        for b in ring(8, 8, r, 0, y - 0.05, y, '#000000'):
            (x0, _, z0), (x1, _, z1) = b.f, b.t
            u0, u1 = (x0 - (8 - r)) / (2 * r) * 16, (x1 - (8 - r)) / (2 * r) * 16
            v0, v1 = (z0 - (8 - r)) / (2 * r) * 16, (z1 - (8 - r)) / (2 * r) * 16
            els.append({"from": [x0, y - 0.05, z0], "to": [x1, y, z1], "shade": False, "light_emission": 4,
                        "faces": {"up": {"uv": [u0, v0, u1, v1], "texture": "#pool"}}})

    def curtain(a, top, bottom):
        """Sixteen thin falling sheets round a rim (apothem a), each showing both sides."""
        s = 2 * a * math.tan(math.radians(11.25)) * 1.08
        for (axis, sign) in [('z', 1), ('z', -1), ('x', 1), ('x', -1)]:
            for angle in ([0, 22.5, -22.5, 45, -45] if axis == 'z' else [0, 22.5, -22.5]):
                if axis == 'z':
                    f, t = [8 - s / 2, bottom, 8 + sign * a], [8 + s / 2, top, 8 + sign * a]
                    faces = {"north": {"uv": [0, 0, 16, 16], "texture": "#flow"}, "south": {"uv": [0, 0, 16, 16], "texture": "#flow"}}
                else:
                    f, t = [8 + sign * a, bottom, 8 - s / 2], [8 + sign * a, top, 8 + s / 2]
                    faces = {"east": {"uv": [0, 0, 16, 16], "texture": "#flow"}, "west": {"uv": [0, 0, 16, 16], "texture": "#flow"}}
                el = {"from": f, "to": t, "shade": False, "light_emission": 4, "faces": faces}
                if angle:
                    el["rotation"] = {"angle": angle, "axis": "y", "origin": [8, top, 8]}
                els.append(el)

    pool(8.6, 2.7)                     # basin
    pool(5.2, 9.6)                     # middle bowl
    pool(3.0, 13.6)                    # top bowl
    curtain(6.65, 9.9, 2.7)            # over the middle bowl into the basin
    curtain(4.15, 13.9, 9.6)           # over the top bowl into the middle one
    # the spring: crossed sheets of milk shooting up out of the spout (the streak texture turned upside down)
    for (f, t, faces) in [([7.3, 15.8, 8], [8.7, 19.5, 8], ("north", "south")), ([8, 15.8, 7.3], [8, 19.5, 8.7], ("east", "west"))]:
        els.append({"from": f, "to": t, "shade": False, "light_emission": 6,
                    "faces": {k: {"uv": [0, 16, 16, 0], "texture": "#flow"} for k in faces}})
    return els


def build(out):
    write(out, 'gadget/cookie_fountain_3d', body(), scale=0.5)
    tex = f'{out}/assets/fractal/textures/item/gadget'
    os.makedirs(tex, exist_ok=True)
    flow_texture().save(f'{tex}/choc_flow.png')
    json.dump({"animation": {"frametime": 2}}, open(f'{tex}/choc_flow.png.mcmeta', 'w'))
    pool_texture().save(f'{tex}/choc_pool.png')
    json.dump({"animation": {"frametime": 3, "interpolate": True}}, open(f'{tex}/choc_pool.png.mcmeta', 'w'))
    model = {"textures": {"flow": "fractal:item/gadget/choc_flow", "pool": "fractal:item/gadget/choc_pool",
                          "particle": "fractal:item/gadget/choc_pool"}, "elements": flow_elements()}
    os.makedirs(f'{out}/assets/fractal/models/item/gadget', exist_ok=True)
    os.makedirs(f'{out}/assets/fractal/items/gadget', exist_ok=True)
    json.dump(model, open(f'{out}/assets/fractal/models/item/gadget/cookie_fountain_flow.json', 'w'))
    json.dump({"model": {"type": "minecraft:model", "model": "fractal:item/gadget/cookie_fountain_flow"}},
              open(f'{out}/assets/fractal/items/gadget/cookie_fountain_flow.json', 'w'))
