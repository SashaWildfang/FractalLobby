"""
Writes the HD collectible icons (icons.py), the suit textures (armor.py) and the 3D models the plugin shows in the
world: cloak pieces, the cookie fountain, the trampoline, the balloon and the propeller (hats.py).
"""
import json, math, os, random
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


GUN_DISPLAY = {
    # Worked out from the game's own sword transforms: the muzzle (top right of the sprite, 40 degrees up) ends up
    # pointing straight ahead in first person, and out in front of the hand in third person.
    # (first person: the hand's +Z points back at the camera, so the muzzle is turned to -Z)
    "firstperson_righthand": {"rotation": [0, 100, -35], "translation": [1.13, 3.2, 1.13], "scale": [0.6, 0.6, 0.6]},
    "firstperson_lefthand": {"rotation": [0, -100, 35], "translation": [1.13, 3.2, 1.13], "scale": [0.6, 0.6, 0.6]},
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
}
_OLD_GUN_DISPLAY = {
    "thirdperson_righthand": {"rotation": [-90, 0, -60], "translation": [2, 0.1, -3], "scale": [0.9, 0.9, 0.9]},
    "thirdperson_lefthand": {"rotation": [-90, 0, 30], "translation": [2, 0.1, -3], "scale": [0.9, 0.9, 0.9]},
    "firstperson_righthand": {"rotation": [-90, 0, -55], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [-90, 0, 35], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
}


def held_item(out, path, img, parent="minecraft:item/generated", display=None):
    flat_item(out, path, img)
    m = {"parent": parent, "textures": {"layer0": f"fractal:item/{path}"}}
    if display:
        m["display"] = display
    json.dump(m, open(f'{out}/assets/fractal/models/item/{path}.json', 'w'))


def build(out):
    import kit
    kit.S, kit.BANDS, kit.PIXEL = 4, 3, True          # crisp pixel art, like the Survival pack's icons
    from vox import Box, write
    n = 0
    # ------------------------------------------------ icons (32x32)
    for key, fn in icons.GADGETS.items():
        if key in icons.GUNS:
            held_item(out, f'gadget/{key}', fn(), display=GUN_DISPLAY)
        elif key in icons.WANDS:
            held_item(out, f'gadget/{key}', fn(), parent="minecraft:item/handheld")
        else:
            flat_item(out, f'gadget/{key}', fn())
        n += 1
    # the fake TNT is the game's own TNT block (readable label, the real look)
    _dirs(out, 'gadget')
    json.dump({"model": {"type": "minecraft:model", "model": "minecraft:block/tnt"}}, open(f'{out}/assets/fractal/items/gadget/fake_tnt.json', 'w'))
    for key, fn in icons.CLOAKS.items():
        flat_item(out, f'cloak/{key}', fn()); n += 1
    for key, fn in icons.GESTURES.items():
        flat_item(out, f'gesture/{key}', fn()); n += 1
    for key, fn in icons.SEASONS.items():
        flat_item(out, f'season/{key}', fn()); n += 1
    flat_item(out, 'menu/collectibles', icons.menu_collectibles()); n += 1
    flat_item(out, 'menu/take_off', icons.menu_take_off()); n += 1
    flat_item(out, 'category/suits', icons.category_suits()); n += 1
    flat_item(out, 'category/trails', icons.category_trails()); n += 1
    n += armor.build(out)

    # ------------------------------------------------ cloak pieces: blocky models, 32 pixels a block
    P = 'cloak/part/'
    H = 0.5
    def ball(cx, cy, cz, r, col, pattern='glow', glow=15, light=None):
        """A round-ish ball of boxes (three crossing slabs and a core), like a Blockbench sphere."""
        k = r * 0.62
        return [Box((cx - k, cy - k, cz - k), (cx + k, cy + k, cz + k), col, pattern, glow=glow),
                Box((cx - r, cy - k * 0.8, cz - k * 0.8), (cx + r, cy + k * 0.8, cz + k * 0.8), col, pattern, glow=glow),
                Box((cx - k * 0.8, cy - r, cz - k * 0.8), (cx + k * 0.8, cy + r, cz + k * 0.8), col, pattern, glow=glow),
                Box((cx - k * 0.8, cy - k * 0.8, cz - r), (cx + k * 0.8, cy + k * 0.8, cz + r), col, pattern, glow=glow)]
    GOLD, WHITE = '#ffcf5a', '#f4f6ff'
    # halo: an octagon of gold bars
    ring = [Box((4, 7.25, 2.5), (12, 8.75, 4), GOLD, 'metal', glow=15), Box((4, 7.25, 12), (12, 8.75, 13.5), GOLD, 'metal', glow=15),
            Box((2.5, 7.25, 4), (4, 8.75, 12), GOLD, 'metal', glow=15), Box((12, 7.25, 4), (13.5, 8.75, 12), GOLD, 'metal', glow=15)]
    for (cx, cz) in ((4, 4), (12, 4), (4, 12), (12, 12)):
        ring.append(Box((cx - 1.2, 7.25, cz - 0.75), (cx + 1.2, 8.75, cz + 0.75), GOLD, 'metal', glow=15,
                        rotation={"angle": 45 if (cx < 8) == (cz < 8) else -45, "axis": "y", "origin": [cx, 8, cz]}))
    write(out, P + 'halo', ring, scale=H)
    # wings: rows of feathers growing out of the spine (x = 8) to the side; the right wing is the mirror
    def wing(mirror):
        """A bird's wing seen from behind: twelve slim feathers hanging from a bone that arcs up from the spine
        (x = 8) to the tip, longer and paler towards the tip, each ending in a point, with two rows of coverts."""
        boxes = []
        def mx(x0, x1):
            return (16 - x1, 16 - x0) if mirror else (x0, x1)
        n = 12
        for i in range(n):
            t = i / (n - 1)
            x0 = 8.4 + t * 15.6
            top = 13 + 10 * math.sin(t * math.pi * 0.8)
            length = 3.5 + 13 * t ** 1.2 - (2 if i == n - 1 else 0)
            shade = int(255 - 40 * t)
            col = '#%02x%02x%02x' % (shade - 12, shade - 6, 255)
            z = 8.2 + (i % 2) * 0.35
            a0, b0 = mx(x0, x0 + 1.5)
            rot = None
            if False:   # (no rotated feathers: they read better straight)
                rot = {"angle": (22.5 if not mirror else -22.5), "axis": "z", "origin": [(mx(x0, x0 + 1.5)[0] + mx(x0, x0 + 1.5)[1]) / 2, top, 8]}
            boxes.append(Box((a0, top - length + 1.2, z), (b0, top, z + 0.5), col, 'feather', glow=11, rotation=rot))
            a1, b1 = mx(x0 + 0.4, x0 + 1.1)
            boxes.append(Box((a1, top - length, z), (b1, top - length + 1.2, z + 0.5), col, 'flat', glow=11, rotation=rot))   # pointed tip
        for row, (drop, z) in enumerate(((3.2, 7.6), (1.6, 7.2))):
            for i in range(0, n, 2):
                t = i / (n - 1)
                x0 = 8.2 + t * 15.6
                top = 13 + 10 * math.sin(t * math.pi * 0.8)
                a0, b0 = mx(x0, x0 + 3.2)
                boxes.append(Box((a0, top - drop - (1.4 if row == 0 else 0.6), z), (b0, top + 0.6, z + 0.6),
                                 '#ffffff' if row else '#f2f5fe', 'flat', glow=11))
        return boxes
    write(out, P + 'wing_left', wing(False), scale=H)
    write(out, P + 'wing_right', wing(True), scale=H)
    W2, G2 = '#ffffff', '#dfe5f0'
    cloud = []
    for (cx, cy, cz, r) in [(8, 8, 8, 4.5), (4, 7.5, 8, 3.4), (12, 7.5, 8, 3.6), (6, 10, 7, 3.2), (10.5, 10.5, 8.5, 3.0), (8, 7, 4.8, 3.0), (8, 7, 11.2, 3.0)]:
        cloud += ball(cx, cy, cz, r, W2, 'flat', glow=None)
    cloud.append(Box((1.5, 4.6, 5), (14.5, 5.4, 11), G2, 'flat'))
    write(out, P + 'cloud', cloud, scale=H)
    for name, col in (('orb_blue', '#5b9dff'), ('orb_pink', '#ff6fd8'), ('orb_gold', '#ffcf5a')):
        write(out, P + name, [Box((5, 5, 5), (11, 11, 11), col, 'glow', glow=15),
                              Box((6, 4, 6), (10, 12, 10), col, 'glow', glow=15), Box((4, 6, 6), (12, 10, 10), col, 'glow', glow=15),
                              Box((6, 6, 4), (10, 10, 12), col, 'glow', glow=15)], scale=H)
    # the ghost: a little blocky ghost, eyes on the front (north)
    gw, gb = '#f4f6ff', '#1a1b26'
    write(out, P + 'ghost', [Box((4, 4, 5), (12, 13, 11), gw, 'flat', glow=12), Box((5, 13, 6), (11, 14, 10), gw, 'flat', glow=12),
                             Box((4, 2, 5), (6, 4, 11), gw, 'flat', glow=12), Box((7, 2.5, 5), (9, 4, 11), gw, 'flat', glow=12),
                             Box((10, 2, 5), (12, 4, 11), gw, 'flat', glow=12),
                             Box((5, 9, 4.6), (7, 11.5, 5), gb, 'flat'), Box((9, 9, 4.6), (11, 11.5, 5), gb, 'flat'),
                             Box((7, 6, 4.6), (9, 8, 5), '#2a2b3a', 'flat')], scale=H)
    sun = ball(8, 8, 8, 5, '#ffd84a') + ball(8, 8, 8, 3.6, '#fff2a0')[1:]
    write(out, P + 'sun3d', sun, scale=H)
    # its rays: a ring of light that always faces whoever looks at it (the plugin turns it)
    tex_save(out, P + 'sun_ring', icons.sun_ring())
    model(out, P + 'sun_ring', P + 'sun_ring', [plane('z', light_level=15)])

    BAT, WING_C = '#2a1f3a', '#3c2c52'
    write(out, P + 'bat', [Box((6.5, 6.5, 6.5), (9.5, 9.8, 9.5), BAT, 'flat'),
                           Box((6.8, 9.8, 7.0), (7.6, 11, 7.8), BAT, 'flat'), Box((8.4, 9.8, 7.0), (9.2, 11, 7.8), BAT, 'flat'),
                           Box((7.0, 8.3, 6.3), (7.7, 8.9, 6.5), '#ff8a1f', 'flat', glow=15), Box((8.3, 8.3, 6.3), (9.0, 8.9, 6.5), '#ff8a1f', 'flat', glow=15),
                           Box((1, 7.5, 7.6), (6.5, 9.5, 8.2), WING_C, 'flat'), Box((9.5, 7.5, 7.6), (15, 9.5, 8.2), WING_C, 'flat'),
                           Box((1, 6.2, 7.6), (3, 7.5, 8.2), WING_C, 'flat'), Box((13, 6.2, 7.6), (15, 7.5, 8.2), WING_C, 'flat'),
                           Box((3.8, 6.8, 7.6), (5.4, 7.5, 8.2), WING_C, 'flat'), Box((10.6, 6.8, 7.6), (12.2, 7.5, 8.2), WING_C, 'flat')], scale=H)

    # flat pieces (pixel sprites)
    tex_save(out, P + 'sparkle', strip([icons.part_sparkle(i).resize((32, 32), Image.NEAREST) for i in range(4)]), 3)
    tex_save(out, P + 'flame', strip([icons.part_flame(i).resize((32, 32), Image.NEAREST) for i in range(8)]), 2)
    for key in ('snowflake', 'petal', 'sun', 'pumpkin_light'):
        im = icons.PARTS[key]()
        tex_save(out, P + key, im.resize((32, 32), Image.NEAREST) if im.size != (32, 32) else im)

    def cross(name, three=True):
        els = [plane('z', light_level=15), plane('x', light_level=15)]
        if three:
            els.append(plane('y', light_level=15))
        model(out, P + name, P + name, els)
    cross('sparkle')
    cross('snowflake')
    cross('flame', three=False)
    model(out, P + 'sun', P + 'sun', [plane('z', light_level=15)])
    model(out, P + 'petal', P + 'petal', [plane('z', light_level=12)])
    model(out, P + 'pumpkin_light', P + 'pumpkin_light', [plane('y', light_level=15)])

    # ------------------------------------------------ gadgets in the world
    stone, dough, choc = '#a8acba', '#c98a4b', '#4a2410'
    write(out, 'gadget/cookie_fountain_3d', [
        Box((1, 0, 1), (15, 3.5, 2.5), stone, 'brick'), Box((1, 0, 13.5), (15, 3.5, 15), stone, 'brick'),
        Box((1, 0, 2.5), (2.5, 3.5, 13.5), stone, 'brick'), Box((13.5, 0, 2.5), (15, 3.5, 13.5), stone, 'brick'),
        Box((2.5, 0, 2.5), (13.5, 2.5, 13.5), dough, 'solid'),
        Box((4, 2.5, 5), (6, 3, 7), choc, 'flat'), Box((9, 2.5, 4), (11, 3, 6), choc, 'flat'), Box((10, 2.5, 10), (12, 3, 12), choc, 'flat'),
        Box((6.5, 2.5, 6.5), (9.5, 10, 9.5), stone, 'brick'), Box((4.5, 10, 4.5), (11.5, 11.5, 11.5), stone, 'brick'),
        Box((5.5, 11.5, 5.5), (10.5, 12.5, 10.5), dough, 'solid'), Box((6.5, 12.5, 6.5), (9.5, 13.5, 9.5), dough, 'solid'),
        Box((7, 13.5, 7), (8.5, 14, 8.5), choc, 'flat')], scale=H)
    yellow, blue, leg = '#ffcf5a', '#3c6ae0', '#5a6078'
    write(out, 'gadget/trampoline_3d', [
        Box((1, 0, 1), (2.2, 10.5, 2.2), leg, 'metal'), Box((13.8, 0, 1), (15, 10.5, 2.2), leg, 'metal'),
        Box((1, 0, 13.8), (2.2, 10.5, 15), leg, 'metal'), Box((13.8, 0, 13.8), (15, 10.5, 15), leg, 'metal'),
        Box((0, 10.5, 0), (16, 11.6, 1.4), yellow, 'solid'), Box((0, 10.5, 14.6), (16, 11.6, 16), yellow, 'solid'),
        Box((0, 10.5, 1.4), (1.4, 11.6, 14.6), yellow, 'solid'), Box((14.6, 10.5, 1.4), (16, 11.6, 14.6), yellow, 'solid'),
        Box((1.4, 10.9, 1.4), (14.6, 11.1, 14.6), blue, 'mat')], scale=H)
    write(out, 'gadget/balloon_3d', [
        Box((4, 6, 4), (12, 16, 12), '#eeeeee', 'solid', tint=True), Box((3, 8, 5), (13, 14.5, 11), '#eeeeee', 'solid', tint=True),
        Box((5, 8, 3), (11, 14.5, 13), '#eeeeee', 'solid', tint=True), Box((5, 16, 5), (11, 17, 11), '#eeeeee', 'solid', tint=True),
        Box((5.5, 4.5, 5.5), (10.5, 6, 10.5), '#d8d8d8', 'solid', tint=True), Box((7.3, 3.2, 7.3), (8.7, 4.5, 8.7), '#c8c8c8', 'solid', tint=True),
        Box((4.5, 13, 3.9), (6.5, 15, 4.0), '#ffffff', 'flat')], scale=H, tints=True)
    # ------------------------------------------------ Mystery Boxes: bigger presents with a real bow (1-5 stars)
    BOX_COLOURS = {1: '#9C8DB8', 2: '#5BE37C', 3: '#7FA8FF', 4: '#C77DFF', 5: '#FFB347'}
    QMARK = [".###.", "#...#", "...#.", "..#..", ".....", "..#.."]
    BLOCK_DISPLAY = {"gui": {"rotation": [30, 225, 0], "translation": [0, -0.5, 0], "scale": [0.6, 0.6, 0.6]},
                     "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
                     "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
                     "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
                     "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
                     "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}}
    for stars, col in BOX_COLOURS.items():
        rib = '#B266FF' if stars == 5 else '#FFD27F'
        b = [Box((1.5, 0, 1.5), (14.5, 11.5, 14.5), col, 'solid'), Box((1, 11, 1), (15, 14, 15), col, 'solid')]
        # ribbon round the box and over the lid
        b += [Box((7, 0, 1.3), (9, 11.5, 14.7), rib, 'metal'), Box((1.3, 0, 7), (14.7, 11.5, 9), rib, 'metal'),
              Box((7, 11, 0.8), (9, 14.2, 15.2), rib, 'metal'), Box((0.8, 11, 7), (15.2, 14.2, 9), rib, 'metal')]
        # the bow: a knot, two loops standing up and two tails
        b += [Box((6.6, 14.2, 6.6), (9.4, 16, 9.4), rib, 'metal')]
        for side in (-1, 1):
            def X(a, c):
                return (8 + side * a, 8 + side * c) if side > 0 else (8 - c, 8 - a)
            for (a, c, y0, y1) in [(1.4, 6, 14.2, 15.2), (5.2, 6.4, 15.2, 18.6), (2.0, 5.4, 18.4, 19.4), (1.2, 2.2, 15.6, 18.6)]:
                x0, x1 = X(a, c)
                b.append(Box((x0, y0, 7.1), (x1, y1, 8.9), rib, 'metal'))
        b += [Box((6.2, 9.5, 0.6), (7.3, 14, 1.0), rib, 'metal'), Box((8.7, 9, 0.6), (9.8, 14, 1.0), rib, 'metal')]
        # a white "?" on each side panel
        for (face, fixed) in (('north', 1.3), ('south', 14.7), ('west', 1.3), ('east', 14.7)):
            for qy, row in enumerate(QMARK):
                for qx, ch in enumerate(row):
                    if ch != '#':
                        continue
                    u, v = 2.4 + qx * 0.75, 9.5 - qy * 0.75
                    if face in ('north', 'south'):
                        z0 = fixed - 0.1 if face == 'north' else fixed
                        b.append(Box((u, v - 0.75, z0), (u + 0.75, v, z0 + 0.1), '#ffffff', 'flat', faces=(face,)))
                    else:
                        x0 = fixed - 0.1 if face == 'west' else fixed
                        b.append(Box((x0, v - 0.75, u), (x0 + 0.1, v, u + 0.75), '#ffffff', 'flat', faces=(face,)))
        write(out, f'mystery/box_{stars}', b, scale=H, display=BLOCK_DISPLAY)

    # ------------------------------------------------ Mystery Dust: a golden sand pile with twinkles (8 frames)
    import make_dust32
    _dirs(out, 'mystery')
    make_dust32.build(f'{out}/assets/fractal/textures/item/mystery/dust.png')
    json.dump({"animation": {"frametime": 3}}, open(f'{out}/assets/fractal/textures/item/mystery/dust.png.mcmeta', 'w'))

    # ------------------------------------------------ the Mystery Vault: a treasure chest on a stone plinth.
    # Purple wood, gold corners and bands, a gold lock on the front (+Z, the side players stand on); the lid
    # is its own model and swings open on a hinge at the back (the plugin turns it).
    WOOD, WOOD2, GOLD_, STONE, STONE2, RUNE = '#4a2f72', '#3a2460', '#ffcf5a', '#3a3d56', '#4a4e6a', '#b266ff'
    body = [Box((0, 0, 0), (16, 2, 16), STONE, 'brick'), Box((1, 2, 1), (15, 3, 15), STONE2, 'solid'),
            Box((0.5, 1.4, 0.5), (15.5, 1.9, 15.5), RUNE, 'flat', glow=12),
            Box((2, 3, 3), (14, 11, 13), WOOD, 'solid', faces=('north', 'south', 'east', 'west', 'down')),   # open at the top
            Box((2.1, 10.2, 3.1), (13.9, 10.3, 12.9), '#f0e0ff', 'glow', glow=15, faces=('up',)),  # the glowing inside, below the rim
            Box((2.4, 10.3, 3.0), (13.6, 11, 3.4), WOOD2, 'solid', faces=('south',)), Box((2.4, 10.3, 12.6), (13.6, 11, 13.0), WOOD2, 'solid', faces=('north',)),
            Box((2.0, 10.3, 3.0), (2.4, 11, 13.0), WOOD2, 'solid', faces=('east',)), Box((13.6, 10.3, 3.0), (14.0, 11, 13.0), WOOD2, 'solid', faces=('west',)),
            Box((1.8, 3, 2.8), (3.2, 11, 4.2), GOLD_, 'metal'), Box((12.8, 3, 2.8), (14.2, 11, 4.2), GOLD_, 'metal'),
            Box((1.8, 3, 11.8), (3.2, 11, 13.2), GOLD_, 'metal'), Box((12.8, 3, 11.8), (14.2, 11, 13.2), GOLD_, 'metal'),
            Box((1.9, 6.2, 2.9), (14.1, 7.2, 13.1), GOLD_, 'metal'),
            Box((6.6, 4.8, 13.1), (9.4, 8.8, 13.5), GOLD_, 'metal'), Box((7.6, 5.6, 13.5), (8.4, 7.2, 13.6), '#1a1b26', 'flat'),
            Box((6.6, 8.8, 13.1), (9.4, 9.4, 13.4), '#d9a63a', 'metal')]
    write(out, 'mystery/vault_body', body, scale=H)
    lid = [Box((2, 11, 3), (14, 13, 13), WOOD2, 'solid'), Box((2.6, 13, 3.6), (13.4, 14.2, 12.4), WOOD2, 'solid'),
           Box((3.4, 14.2, 4.4), (12.6, 14.8, 11.6), WOOD, 'solid'),
           Box((1.8, 11, 2.8), (3.2, 13.2, 4.2), GOLD_, 'metal'), Box((12.8, 11, 2.8), (14.2, 13.2, 4.2), GOLD_, 'metal'),
           Box((1.8, 11, 11.8), (3.2, 13.2, 13.2), GOLD_, 'metal'), Box((12.8, 11, 11.8), (14.2, 13.2, 13.2), GOLD_, 'metal'),
           Box((7.1, 11, 2.9), (8.9, 14.9, 13.1), GOLD_, 'metal'),
           Box((7, 9.6, 13.1), (9, 11.6, 13.7), GOLD_, 'metal')]                          # the latch over the lock
    write(out, 'mystery/vault_lid', lid, scale=H)

    # ------------------------------------------------ 1.41 gadget models (placed in the world or flying)
    G = 'gadget/'
    write(out, G + 'tetherball_pole', [Box((7.2, 0, 7.2), (8.8, 32, 8.8), '#c9ccd8', 'metal'),
                                         Box((5, 0, 5), (11, 1, 11), '#5a6078', 'solid'), Box((7, 31.5, 7), (9, 32, 9), '#ffcf5a', 'flat')], scale=H)
    write(out, G + 'ball_orange', ball(8, 8, 8, 5, '#ff6a3a', 'solid', glow=None) + [Box((7.6, 2.8, 2.9), (8.4, 13.2, 3.1), '#ffffff', 'flat')], scale=H)
    write(out, G + 'pong_ball', ball(8, 8, 8, 4, '#ffe14d', 'solid', glow=10), scale=H)
    def sphere_columns(cx, cy, cz, r, colour_at, step=1.0):
        """A round ball built from upright columns (one per step x step cell inside the circle), each as tall as the
        sphere is at that spot: round from every side, and every column can have its own colour."""
        boxes = []
        n = int(round(2 * r / step))
        for i in range(n):
            for k in range(n):
                x0, z0 = cx - r + i * step, cz - r + k * step
                mx, mz = x0 + step / 2 - cx, z0 + step / 2 - cz
                d2 = mx * mx + mz * mz
                if d2 > r * r * 0.97:
                    continue
                h = math.sqrt(max(0.0, r * r - d2))
                h = round(h / (step / 2)) * (step / 2)      # whole half-steps: clean pixel edges
                if h <= 0:
                    continue
                boxes.append(Box((x0, cy - h, z0), (x0 + step, cy + h, z0 + step), colour_at(mx, mz), 'flat'))
        return boxes
    GORES = ('#ff4d4d', '#ffffff', '#3f8bff', '#ffffff', '#ffd23a', '#ffffff')
    def beach_colour(mx, mz):
        if mx * mx + mz * mz < 2.3:                       # the white cap at the top and bottom
            return '#ffffff'
        a = (math.degrees(math.atan2(mz, mx)) + 360) % 360
        return GORES[int(a // 60) % 6]
    bb = sphere_columns(8, 8, 8, 6.5, beach_colour)
    write(out, G + 'beach_ball_3d', bb, scale=H)
    write(out, G + 'frisbee_3d', [Box((2, 7.4, 2), (14, 8.6, 14), '#ff5a4a', 'solid'), Box((3.5, 8.6, 3.5), (12.5, 9.0, 12.5), '#ff7a6a', 'flat'),
                                    Box((1.4, 7.2, 4), (14.6, 8.2, 12), '#c8322e', 'flat'), Box((4, 7.2, 1.4), (12, 8.2, 14.6), '#c8322e', 'flat')], scale=H)
    P_ = ['#ff6fd8', '#ffcf3a', '#5be37c', '#5b9dff', '#ff8a1f']
    pinata = []
    for i, col in enumerate(P_):                                   # a striped donkey
        pinata.append(Box((3, 7 + i * 1.4, 5.5), (12, 8.4 + i * 1.4, 10.5), col, 'solid'))
    pinata += [Box((10, 11, 6.5), (13, 17, 9.5), '#ff6fd8', 'solid'), Box((10.5, 15, 6.6), (16, 18, 9.4), '#ffcf3a', 'solid'),
               Box((11, 18, 7.2), (12, 20, 8), '#5be37c', 'solid'), Box((11, 18, 8.4), (12, 20, 9.2), '#5be37c', 'solid'),
               Box((14.8, 16.5, 6.5), (15.4, 17.1, 6.7), '#1a1b26', 'flat'), Box((14.8, 16.5, 9.3), (15.4, 17.1, 9.5), '#1a1b26', 'flat')]
    for (x, z) in [(3.5, 6), (3.5, 9), (10, 6), (10, 9)]:
        pinata.append(Box((x, 3, z), (x + 1.4, 7, z + 1.2), P_[int(x) % 5], 'solid'))
    for k in range(3):
        pinata.append(Box((1, 9 + k * 1.6, 7.5), (3, 9.6 + k * 1.6, 8.5), P_[k + 1], 'flat'))
    pinata.append(Box((7.6, 14, 7.6), (8.4, 32, 8.4), '#e8e8f0', 'flat'))
    write(out, G + 'pinata_3d', pinata, scale=H)
    board, stand = '#f4f4fa', '#c9ccd8'
    write(out, G + 'diving_board_3d', [Box((0, 0, 5), (3, 6, 11), stand, 'metal'), Box((0, 6, 6), (16, 7, 10), board, 'solid'),
                                         Box((16, 6.2, 6), (30, 7, 10), board, 'solid'), Box((0, 6.9, 6.4), (30, 7.1, 9.6), '#5b9dff', 'flat')], scale=H)
    write(out, G + 'swing_frame', [Box((0, 0, 7), (1.5, 30, 9), '#a8743a', 'wood'), Box((14.5, 0, 7), (16, 30, 9), '#a8743a', 'wood'),
                                     Box((0, 30, 6.5), (16, 32, 9.5), '#8b5a2b', 'wood')], scale=H)
    write(out, G + 'swing_seat', [Box((4, 0, 6.5), (12, 1, 9.5), '#e03a3a', 'solid'), Box((4.5, 1, 7.7), (5.1, 24, 8.3), '#e8e8f0', 'flat'),
                                    Box((10.9, 1, 7.7), (11.5, 24, 8.3), '#e8e8f0', 'flat')], scale=H)
    scare = [Box((7.3, 0, 7.3), (8.7, 26, 8.7), '#8b5a2b', 'wood'), Box((0, 17, 7.3), (16, 18.4, 8.7), '#8b5a2b', 'wood'),
             Box((4, 12, 6), (12, 20, 10), '#5b8fd0', 'solid'), Box((1, 16.6, 6.6), (4, 19, 9.4), '#5b8fd0', 'solid'),
             Box((12, 16.6, 6.6), (15, 19, 9.4), '#5b8fd0', 'solid'), Box((4, 9, 6.4), (12, 12, 9.6), '#7a5a3a', 'solid')]
    scare += ball(8, 24, 8, 3.6, '#ec7a1c', 'solid', glow=None)
    scare += [Box((6.4, 24.4, 4.3), (7.4, 25.6, 4.5), '#ffd84a', 'flat', glow=12), Box((8.6, 24.4, 4.3), (9.6, 25.6, 4.5), '#ffd84a', 'flat', glow=12),
              Box((6.4, 22.4, 4.3), (9.6, 23.2, 4.5), '#ffd84a', 'flat', glow=12),
              Box((2.5, 27.2, 2.5), (13.5, 27.8, 13.5), '#c9a24a', 'wood'), Box((5, 27.8, 5), (11, 30.5, 11), '#c9a24a', 'wood'),
              Box((4.9, 27.8, 4.9), (11.1, 28.6, 11.1), '#c8322e', 'flat')]
    write(out, G + 'scarecrow_3d', scare, scale=H)
    sand, sand2 = '#ecd08a', '#d6b46a'
    castle = [Box((0, 0, 0), (16, 1, 16), sand2, 'solid'), Box((2, 1, 2), (14, 7, 14), sand, 'solid'),
              Box((5, 7, 5), (11, 13, 11), sand, 'solid'), Box((7.6, 13, 7.6), (8.4, 17, 8.4), '#8b5a2b', 'flat'),
              Box((8.4, 15, 7.8), (11.5, 16.8, 8.2), '#e03a3a', 'flat'), Box((6.5, 1, 1.8), (9.5, 4.5, 2.1), sand2, 'flat')]
    for (x, z) in [(1, 1), (12, 1), (1, 12), (12, 12)]:
        castle.append(Box((x, 1, z), (x + 3, 10, z + 3), sand, 'solid'))
        castle.append(Box((x - 0.2, 10, z - 0.2), (x + 3.2, 11, z + 3.2), sand2, 'solid'))
    for x in (2.5, 5.5, 8.5, 11.5):
        castle.append(Box((x, 7, 1.8), (x + 1.5, 8, 2.8), sand, 'solid'))
    write(out, G + 'sand_castle_3d', castle, scale=H)
    write(out, G + 'ttt_board', [Box((0, 0, 0), (16, 0.6, 16), '#c49a5a', 'wood'), Box((5, 0.6, 0.5), (5.6, 0.8, 15.5), '#5a3a1e', 'flat'),
                                  Box((10.4, 0.6, 0.5), (11, 0.8, 15.5), '#5a3a1e', 'flat'), Box((0.5, 0.6, 5), (15.5, 0.8, 5.6), '#5a3a1e', 'flat'),
                                  Box((0.5, 0.6, 10.4), (15.5, 0.8, 11), '#5a3a1e', 'flat')], scale=H)
    write(out, G + 'ttt_x', [Box((1, 0, 7), (15, 1, 9), '#e03a3a', 'flat', rotation={"angle": 45, "axis": "y", "origin": [8, 0, 8]}),
                             Box((7, 0, 1), (9, 1, 15), '#e03a3a', 'flat', rotation={"angle": 45, "axis": "y", "origin": [8, 0, 8]})], scale=H)
    o = []
    for (x0, z0, x1, z1) in [(3, 2, 13, 4), (3, 12, 13, 14), (2, 3, 4, 13), (12, 3, 14, 13)]:
        o.append(Box((x0, 0, z0), (x1, 1, z1), '#3c6ae0', 'flat'))
    write(out, G + 'ttt_o', o, scale=H)
    dj = [Box((0, 0, 4), (4, 14, 12), '#2a2a36', 'solid'), Box((12, 0, 4), (16, 14, 12), '#2a2a36', 'solid'),
          Box((0.6, 9, 3.6), (3.4, 12, 4), '#5a6078', 'flat'), Box((12.6, 9, 3.6), (15.4, 12, 4), '#5a6078', 'flat'),
          Box((0.4, 2, 3.6), (3.6, 7, 4), '#5a6078', 'flat'), Box((12.4, 2, 3.6), (15.6, 7, 4), '#5a6078', 'flat'),
          Box((4, 0, 5), (12, 8, 11), '#3a2a52', 'solid'), Box((4, 7.4, 4.8), (12, 8.2, 5.2), '#b266ff', 'flat', glow=15),
          Box((4.6, 8, 6), (7.6, 8.6, 10), '#1a1b26', 'flat'), Box((8.4, 8, 6), (11.4, 8.6, 10), '#1a1b26', 'flat'),
          Box((5.6, 8.6, 7.5), (6.6, 8.8, 8.5), '#e03a3a', 'flat', glow=12), Box((9.4, 8.6, 7.5), (10.4, 8.8, 8.5), '#5b9dff', 'flat', glow=12)]
    write(out, G + 'dj_booth_3d', dj, scale=H)
    write(out, G + 'jetpack_3d', [Box((3, 3, 6), (7, 13, 10), '#c8322e', 'solid'), Box((9, 3, 6), (13, 13, 10), '#c8322e', 'solid'),
                                    Box((7, 6, 7), (9, 11, 9), '#5a6078', 'metal'), Box((3.5, 1.5, 6.5), (6.5, 3, 9.5), '#5a6078', 'metal'),
                                    Box((9.5, 1.5, 6.5), (12.5, 3, 9.5), '#5a6078', 'metal'), Box((3, 9, 5.8), (7, 10, 6.1), '#e8e8f0', 'flat'),
                                    Box((9, 9, 5.8), (13, 10, 6.1), '#e8e8f0', 'flat')], scale=H)

    # the disco ball (texture used by sprites.py's model): mirror tiles
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
