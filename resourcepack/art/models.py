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
    "firstperson_righthand": {"rotation": [0, -78, -35], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 78, 35], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
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
    n += armor.build(out)

    # ------------------------------------------------ cloak pieces: blocky models, 32 pixels a block
    P = 'cloak/part/'
    H = 0.5
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
    write(out, P + 'cloud', [Box((2, 5, 3), (14, 10, 13), WHITE, 'fluffy'), Box((0, 6, 5), (5, 9.5, 11), WHITE, 'fluffy'),
                             Box((11, 6, 4), (16, 10.5, 11), WHITE, 'fluffy'), Box((4, 9, 4), (11, 13, 11), WHITE, 'fluffy'),
                             Box((8, 9.5, 6), (13, 12, 11), WHITE, 'fluffy'), Box((2.5, 4.5, 3.5), (13.5, 5, 12.5), '#aab4cc', 'solid')], scale=H)
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
    sun = [Box((5, 5, 5), (11, 11, 11), '#ffd84a', 'glow', glow=15), Box((4, 6, 6), (12, 10, 10), '#ffcf3a', 'glow', glow=15),
           Box((6, 4, 6), (10, 12, 10), '#ffcf3a', 'glow', glow=15), Box((6, 6, 4), (10, 10, 12), '#ffcf3a', 'glow', glow=15)]
    for k, ang in enumerate((0, 45, -45, 22.5, -22.5)):
        for axis in (('y', 'x') if k == 0 else ('y',)):
            rlen = 15 if k < 3 else 11
            sun.append(Box((8 - rlen, 7.6, 7.6), (8 + rlen, 8.4, 8.4), '#fff2a0' if k < 3 else '#ffb428', 'glow', glow=15,
                           rotation={"angle": ang, "axis": axis, "origin": [8, 8, 8]} if ang else None))
    sun.append(Box((7.6, 7.6, -7), (8.4, 8.4, 23), '#fff2a0', 'glow', glow=15))
    for ang in (45, -45):
        sun.append(Box((7.6, -3, 7.6), (8.4, 19, 8.4), '#ffb428', 'glow', glow=15, rotation={"angle": ang, "axis": "x", "origin": [8, 8, 8]}))
        sun.append(Box((7.6, -3, 7.6), (8.4, 19, 8.4), '#ffb428', 'glow', glow=15, rotation={"angle": ang, "axis": "z", "origin": [8, 8, 8]}))
    write(out, P + 'sun3d', sun, scale=H)

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
        Box((1, 4, 1), (2.2, 10.5, 2.2), leg, 'metal'), Box((13.8, 4, 1), (15, 10.5, 2.2), leg, 'metal'),
        Box((1, 4, 13.8), (2.2, 10.5, 15), leg, 'metal'), Box((13.8, 4, 13.8), (15, 10.5, 15), leg, 'metal'),
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
