"""
Writes the HD collectible icons (icons.py), the suit textures (armor.py) and the 3D models the plugin shows in the
world: cloak pieces, the cookie fountain, the trampoline, the balloon and the propeller (hats.py).
"""
import json, os, random
from PIL import Image
import icons, armor
from kit import light, dark, hexc

ALL = ('north', 'south', 'east', 'west', 'up', 'down')


def _dirs(out, folder):
    for d in (f'textures/item/{folder}', f'models/item/{folder}', f'items/{folder}'):
        os.makedirs(f'{out}/assets/fractal/{d}', exist_ok=True)


def flat_item(out, path, img, frametime=None):
    """A texture shown as a normal flat item (menus, hotbar)."""
    _dirs(out, os.path.dirname(path))
    img.save(f'{out}/assets/fractal/textures/item/{path}.png')
    if frametime:
        json.dump({"animation": {"frametime": frametime}}, open(f'{out}/assets/fractal/textures/item/{path}.png.mcmeta', 'w'))
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"fractal:item/{path}"}},
              open(f'{out}/assets/fractal/models/item/{path}.json', 'w'))
    json.dump({"model": {"type": "minecraft:model", "model": f"fractal:item/{path}"}}, open(f'{out}/assets/fractal/items/{path}.json', 'w'))


def model(out, path, texture, elements, tints=False, extra_textures=None):
    _dirs(out, os.path.dirname(path))
    textures = {"t": f"fractal:item/{texture}", "particle": f"fractal:item/{texture}"}
    textures.update(extra_textures or {})
    json.dump({"textures": textures, "elements": elements}, open(f'{out}/assets/fractal/models/item/{path}.json', 'w'))
    item = {"type": "minecraft:model", "model": f"fractal:item/{path}"}
    if tints:
        item["tints"] = [{"type": "minecraft:dye", "default": -1}]
    json.dump({"model": item}, open(f'{out}/assets/fractal/items/{path}.json', 'w'))


def plane(axis='z', uv=(0, 0, 16, 16), flip=False, tex='#t', light_level=None, at=8, size=16):
    lo, hi = (16 - size) / 2, (16 + size) / 2
    u = list(uv)
    back = [u[2], u[1], u[0], u[3]]
    if axis == 'z':
        e = {"from": [lo, lo, at], "to": [hi, hi, at], "faces": {"south": {"uv": back if flip else u, "texture": tex},
                                                                   "north": {"uv": u if flip else back, "texture": tex}}}
    elif axis == 'x':
        e = {"from": [at, lo, lo], "to": [at, hi, hi], "faces": {"east": {"uv": u, "texture": tex}, "west": {"uv": back, "texture": tex}}}
    else:
        e = {"from": [lo, at, lo], "to": [hi, at, hi], "faces": {"up": {"uv": u, "texture": tex}, "down": {"uv": u, "texture": tex}}}
    if light_level:
        e["light_emission"] = light_level
    return e


def box(f, t, uv=(0, 0, 16, 16), tex='#t', tint=False, faces=ALL):
    out = {"from": list(f), "to": list(t), "faces": {}}
    for d in faces:
        out["faces"][d] = {"uv": list(uv), "texture": tex}
        if tint:
            out["faces"][d]["tintindex"] = 0
    return out


def tex_save(out, path, img, frametime=None):
    os.makedirs(os.path.dirname(f'{out}/assets/fractal/textures/item/{path}'), exist_ok=True)
    img.save(f'{out}/assets/fractal/textures/item/{path}.png')
    if frametime:
        json.dump({"animation": {"frametime": frametime, "interpolate": True}},
                  open(f'{out}/assets/fractal/textures/item/{path}.png.mcmeta', 'w'))


def strip(frames):
    w, h = frames[0].size
    out = Image.new('RGBA', (w, h * len(frames)))
    for i, f in enumerate(frames):
        out.paste(f, (0, i * h))
    return out


def noise_tex(base, size=16, amount=0.08, seed=1, bricks=False):
    r = random.Random(seed)
    img = Image.new('RGBA', (size, size))
    for y in range(size):
        for x in range(size):
            n = r.uniform(-amount, amount)
            c = light(base, n) if n > 0 else dark(base, -n)
            if bricks and (y % 4 == 3 or (x + (4 if (y // 4) % 2 else 0)) % 8 == 7):
                c = dark(c, 0.25)
            img.putpixel((x, y), c + (255,))
    return img


def build(out):
    n = 0
    # ------------------------------------------------ icons
    for key, fn in icons.GADGETS.items():
        flat_item(out, f'gadget/{key}', fn()); n += 1
    for key, fn in icons.CLOAKS.items():
        flat_item(out, f'cloak/{key}', fn()); n += 1
    for key, fn in icons.GESTURES.items():
        flat_item(out, f'gesture/{key}', fn()); n += 1
    for key, fn in icons.SEASONS.items():
        flat_item(out, f'season/{key}', fn()); n += 1
    flat_item(out, 'menu/collectibles', icons.menu_collectibles()); n += 1
    n += armor.build(out)

    # ------------------------------------------------ cloak pieces
    G = 15  # they glow
    for key, fn in icons.PARTS.items():
        tex_save(out, f'cloak/part/{key}', fn())
    for key, (fn, frames, ticks) in icons.ANIMATED_PARTS.items():
        tex_save(out, f'cloak/part/{key}', strip([fn(i) for i in range(frames)]), ticks)
    P = 'cloak/part/'

    def cross(name, three=True, size=16):
        els = [plane('z', light_level=G, size=size), plane('x', light_level=G, size=size)]
        if three:
            els.append(plane('y', light_level=G, size=size))
        model(out, P + name, P + name, els)

    model(out, P + 'halo', P + 'halo', [plane('y', light_level=G), plane('y', light_level=G, at=8.3)])
    model(out, P + 'wing_left', P + 'wing', [plane('z', light_level=10)])
    model(out, P + 'wing_right', P + 'wing', [plane('z', flip=True, light_level=10)])
    model(out, P + 'sun', P + 'sun', [plane('z', light_level=G)])
    model(out, P + 'ghost', P + 'ghost', [plane('z', light_level=G)])
    model(out, P + 'petal', P + 'petal', [plane('z', light_level=12)])
    model(out, P + 'pumpkin_light', P + 'pumpkin_light', [plane('y', light_level=G)])
    for k in ('orb_blue', 'orb_pink', 'orb_gold', 'sparkle', 'snowflake'):
        cross(k)
    cross('flame', three=False)
    model(out, P + 'cloud', P + 'cloud', [box((2, 5, 3), (14, 10, 13)), box((0, 6, 5), (5, 9.5, 11)), box((11, 6, 4), (16, 10.5, 11)),
                                          box((4, 9, 4), (11, 13, 11)), box((8, 9.5, 6), (13, 12, 11))])

    # ------------------------------------------------ the cookie fountain (stone basin, cookie dough, a spout)
    tex_save(out, 'gadget/fountain_stone', noise_tex((168, 172, 186), seed=3, bricks=True))
    tex_save(out, 'gadget/fountain_dough', icons.cookie(16))
    T = {"stone": "fractal:item/gadget/fountain_stone", "dough": "fractal:item/gadget/fountain_dough"}
    els = [box((1, 0, 1), (15, 3.5, 2.5), tex='#stone'), box((1, 0, 13.5), (15, 3.5, 15), tex='#stone'),
           box((1, 0, 2.5), (2.5, 3.5, 13.5), tex='#stone'), box((13.5, 0, 2.5), (15, 3.5, 13.5), tex='#stone'),
           box((2.5, 0, 2.5), (13.5, 2.6, 13.5), tex='#dough'),
           box((6.5, 2.6, 6.5), (9.5, 10, 9.5), tex='#stone'), box((4.5, 10, 4.5), (11.5, 11.5, 11.5), tex='#stone'),
           box((5.3, 11.5, 5.3), (10.7, 12.3, 10.7), tex='#dough'), box((6.5, 12.3, 6.5), (9.5, 13.3, 9.5), tex='#dough')]
    model(out, 'gadget/cookie_fountain_3d', 'gadget/fountain_stone', els, extra_textures=T)

    # ------------------------------------------------ the trampoline (2x2 blocks when shown; sits on the ground)
    mat = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            mat.putpixel((x, y), ((40, 110, 230) if (x + y) % 2 else (52, 124, 244)) + (255,))
    tex_save(out, 'gadget/trampoline_mat', mat)
    tex_save(out, 'gadget/trampoline_frame', noise_tex((255, 207, 90), seed=5, amount=0.05))
    tex_save(out, 'gadget/trampoline_leg', noise_tex((90, 96, 120), seed=6, amount=0.05))
    T = {"mat": "fractal:item/gadget/trampoline_mat", "frame": "fractal:item/gadget/trampoline_frame",
         "leg": "fractal:item/gadget/trampoline_leg"}
    els = [box((1, 4, 1), (2.2, 10.5, 2.2), tex='#leg'), box((13.8, 4, 1), (15, 10.5, 2.2), tex='#leg'),
           box((1, 4, 13.8), (2.2, 10.5, 15), tex='#leg'), box((13.8, 4, 13.8), (15, 10.5, 15), tex='#leg'),
           box((0, 10.5, 0), (16, 11.6, 1.4), tex='#frame'), box((0, 10.5, 14.6), (16, 11.6, 16), tex='#frame'),
           box((0, 10.5, 1.4), (1.4, 11.6, 14.6), tex='#frame'), box((14.6, 10.5, 1.4), (16, 11.6, 14.6), tex='#frame'),
           box((1.4, 10.9, 1.4), (14.6, 11.1, 14.6), tex='#mat')]
    model(out, 'gadget/trampoline_3d', 'gadget/trampoline_mat', els, extra_textures=T)

    # ------------------------------------------------ the balloon (tinted by the item's dye colour)
    b = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            k = 1.0 - y * 0.018 + (0.12 if (x < 6 and y < 6) else 0)
            v = max(0, min(255, round(232 * k)))
            b.putpixel((x, y), (v, v, v, 255))
    for (x, y) in [(3, 3), (4, 3), (3, 4)]:
        b.putpixel((x, y), (255, 255, 255, 255))
    tex_save(out, 'gadget/balloon_skin', b)
    els = [box((4, 6, 4), (12, 16, 12), tint=True), box((3, 8, 5), (13, 14.5, 11), tint=True), box((5, 8, 3), (11, 14.5, 13), tint=True),
           box((5, 16, 5), (11, 17, 11), tint=True), box((5.5, 4.5, 5.5), (10.5, 6, 10.5), tint=True),
           box((7.3, 3.2, 7.3), (8.7, 4.5, 8.7), tint=True)]
    model(out, 'gadget/balloon_3d', 'gadget/balloon_skin', els, tints=True)

    # ------------------------------------------------ the mirrored disco ball, sharper
    t = Image.new('RGBA', (16, 16))
    shades = [(235, 238, 250), (160, 168, 196), (120, 200, 255), (255, 255, 255), (200, 160, 255), (130, 136, 160), (255, 190, 240)]
    for y in range(16):
        for x in range(16):
            c = shades[((x // 2) * 5 + (y // 2) * 3) % len(shades)]
            if x % 2 == 1 or y % 2 == 1:
                c = dark(c, 0.25)
            t.putpixel((x, y), c + (255,))
    tex_save(out, 'gadget/disco_ball_3d', t)
    return n
