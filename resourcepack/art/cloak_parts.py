"""The redone cloaks' pieces (fractal:cloak/part/...): crisp sprites, round voxel shapes, glowing parts.

  sparkle:  star_white, star_pink, star_gold, star_lilac   (4-point twinkle stars, shown facing you)
  halo:     halo_ring                                      (a smooth round gold ring, glowing)
  rain:     rain_cloud2, raindrop, bolt                    (a soft grey cloud, rain streaks, a little lightning bolt)
  orbit:    planet_ringed, planet_blue, planet_pink        (round little planets, one with a ring)
  flame:    flame_tongue, fire_ring                        (animated flames, a glowing ring of fire on the ground)
  sun:      sun_ball, sun_rays2, sun_glow                  (a round glowing sun, its turning rays, a soft glow)
  spooky:   spook_ghost                                    (a little round sheet ghost)
"""
import json, math, os, random
from PIL import Image

P = 'cloak/part/'


def rgba(h, a=255):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (a,)


# ---------------------------------------------------------------- sprites

def star(core, tint):
    """A crisp 4-point twinkle star on 16x16: long thin arms, a bright core, a soft tinted halo round the core."""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    c = 7.5
    for y in range(16):
        for x in range(16):
            dx, dy = abs(x - c), abs(y - c)
            arm = (dx < 0.9 and dy < 7.6 - dx * 6) or (dy < 0.9 and dx < 7.6 - dy * 6)
            diag = dx + dy < 3.2
            if dx < 1.2 and dy < 1.2:
                px[x, y] = rgba('#ffffff')
            elif arm:
                px[x, y] = rgba(core) if max(dx, dy) < 4.5 else rgba(tint)
            elif diag:
                px[x, y] = rgba(tint, 150)
    return im


def raindrop():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(2, 14):
        a = int(110 + 140 * (y - 2) / 11)
        im.putpixel((8, y), rgba('#bfe6ff', a))
        if y > 9:
            im.putpixel((7, y), rgba('#8fcaff', a))
    im.putpixel((8, 14), rgba('#ffffff'))
    return im


def bolt():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    pts = [(9, 0), (8, 1), (8, 2), (7, 3), (7, 4), (6, 5), (6, 6), (7, 6), (8, 6), (9, 6), (9, 7), (8, 8), (8, 9), (7, 10), (7, 11),
           (6, 12), (6, 13), (5, 14), (5, 15)]
    for (x, y) in pts:
        im.putpixel((x, y), rgba('#fff6a8'))
        if x + 1 < 16:
            im.putpixel((x + 1, y), rgba('#ffe14d'))
    return im


def sun_rays():
    """32x32: sixteen crisp rays round an empty middle (the sun sits there), long and short in turn, gold to amber."""
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    px = im.load()
    c = 15.5
    for y in range(32):
        for x in range(32):
            dx, dy = x - c, y - c
            r = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            k = (a / (2 * math.pi) * 16) % 1
            ray = int(round(a / (2 * math.pi) * 16)) % 2
            reach = 15.5 if ray == 0 else 12.5
            width = 0.5 - abs(k - 0.5)                                   # 0 at a ray's centre line... flip it
            on = abs(((a / (2 * math.pi) * 16) - round(a / (2 * math.pi) * 16))) < (0.16 if ray == 0 else 0.12) * (1.2 - r / 18)
            if 8.5 < r < reach and on:
                px[x, y] = rgba('#fff2a0') if r < 11 else rgba('#ffc83d') if r < 13.5 else rgba('#ff9a1f')
    return im


def sun_glow():
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    px = im.load()
    for y in range(32):
        for x in range(32):
            r = math.hypot(x - 15.5, y - 15.5) / 15.5
            if r < 1:
                a = int(120 * (1 - r) ** 1.6)
                px[x, y] = (255, 220, 110, a)
    return im


def fire_ring_tex():
    """32x32, 4 frames: a glowing ring of fire seen from above, flickering."""
    rnd = random.Random(5)
    F = 4
    sheet = Image.new('RGBA', (32, 32 * F), (0, 0, 0, 0))
    for f in range(F):
        for y in range(32):
            for x in range(32):
                r = math.hypot(x - 15.5, y - 15.5)
                a = math.atan2(y - 15.5, x - 15.5)
                wob = 0.9 * math.sin(a * 7 + f * 1.6) + rnd.uniform(-0.4, 0.4)
                d = abs(r - 12.2 - wob * 0.4)
                if d < 2.3:
                    heat = 1 - d / 2.3
                    col = '#fff2c0' if heat > 0.75 else '#ffd84a' if heat > 0.5 else '#ff8a1f' if heat > 0.25 else '#d0401a'
                    sheet.putpixel((x, f * 32 + y), rgba(col, 255 if heat > 0.25 else 180))
    return sheet


def flame_tex():
    """16x32, 6 frames: an upright flame, white-hot at the base core, yellow, orange, ragged red tips, licking about."""
    rnd = random.Random(11)
    W, H, F = 16, 32, 6
    sheet = Image.new('RGBA', (W, H * F), (0, 0, 0, 0))
    for f in range(F):
        length = 24 + rnd.randint(-3, 4)
        sway = math.sin(f / F * 2 * math.pi) * 1.6
        for yy in range(H):
            y = H - 1 - yy                                                # from the base up
            k = yy / length
            if k > 1:
                break
            half = 6.0 * math.sin(math.pi * min(1, (k + 0.12) / 1.12)) ** 0.9 * (1 - k * 0.55) + 0.4
            cx = 7.5 + sway * k * k
            for x in range(W):
                d = abs(x - cx) / half
                if d > 1:
                    continue
                heat = (1 - d) * (1 - k * 0.9)
                if k > 0.75 and rnd.random() < 0.3:
                    continue
                col = '#fff8e0' if heat > 0.62 else '#ffd84a' if heat > 0.42 else '#ff8a1f' if heat > 0.2 else '#d0401a'
                sheet.putpixel((x, f * H + y), rgba(col, 255 if d < 0.85 else 170))
    return sheet


# ---------------------------------------------------------------- shapes

def sphere(Box, cx, cy, cz, r, colour_at, step=1.0, glow=None):
    """A round ball as columns (one box per step x step column, as tall as the sphere there): smooth from every side.
    colour_at(x, z, h) gives each column's colour."""
    boxes = []
    n = int(math.ceil(r / step))
    for i in range(-n, n):
        for j in range(-n, n):
            x, z = (i + 0.5) * step, (j + 0.5) * step
            d2 = x * x + z * z
            if d2 >= r * r:
                continue
            h = math.sqrt(r * r - d2)
            h = round(h * 2) / 2
            if h <= 0:
                continue
            boxes.append(Box((cx + i * step, cy - h, cz + j * step), (cx + (i + 1) * step, cy + h, cz + (j + 1) * step),
                             colour_at(x, z, h), 'flat', glow=glow))
    return boxes


def ring_segments(Box, cx, cy, cz, R, n, width, height, colour, glow=None):
    """A round ring of n straight segments (n a multiple of 8, so every piece turns by 22.5 degree steps)."""
    boxes = []
    seg = 2 * math.pi * R / n * 1.12
    for i in range(n):
        th = math.radians(i * 360 / n)
        px, pz = cx + R * math.cos(th), cz + R * math.sin(th)
        target = (math.degrees(th) + 90) % 180
        phi = (-target + 90) % 180 - 90                                 # long along x, turned phi
        along_x = abs(phi) <= 45.01
        if not along_x:
            phi = (-(target - 90) + 90) % 180 - 90
        if along_x:
            f, t = (px - seg / 2, cy - height / 2, pz - width / 2), (px + seg / 2, cy + height / 2, pz + width / 2)
        else:
            f, t = (px - width / 2, cy - height / 2, pz - seg / 2), (px + width / 2, cy + height / 2, pz + seg / 2)
        rot = {"angle": round(phi, 1), "axis": "y", "origin": [px, cy, pz]} if abs(phi) > 0.01 else None
        boxes.append(Box(f, t, colour, 'flat', glow=glow, rotation=rot))
    return boxes


def heightmap_cloud(Box, top_col, mid_col, under_col):
    domes = [(8, 8, 4.6, 3.6), (4.3, 8.3, 3.2, 2.6), (11.8, 7.8, 3.4, 2.8), (6.4, 6.0, 2.8, 2.2), (9.8, 10.0, 2.8, 2.4)]
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
            boxes.append(Box((x, base + 0.6, z), (x + 1, top, z + 1), mid_col if top < base + 2.2 else top_col, 'flat'))
            boxes.append(Box((x, base, z), (x + 1, base + 0.6, z + 1), under_col, 'flat'))
    return boxes


def ghost(Box):
    """A little sheet ghost: a round dome head flowing into a skirt with a wavy hem, big dark eyes and an "o" mouth
    on the front (north), and two tiny arms."""
    W, W2, W3, INK = '#f6f8ff', '#e4e9f8', '#cfd8f0', '#1a1b26'
    b = sphere(Box, 8, 9, 8, 4.2, lambda x, z, h: W)
    b = [bx for bx in b if bx.f[1] >= 0]                                # (all of it, the bottom half becomes the skirt)
    for i in range(-5, 5):                                             # a round skirt flaring out a touch, wavy at the hem
        for j in range(-5, 5):
            x, z = i + 0.5, j + 0.5
            d = math.hypot(x, z)
            if d >= 4.4:
                continue
            ang = math.atan2(z, x)
            hem = 0.8 + 0.7 * (1 + math.sin(ang * 4)) / 2 if d > 3.0 else 1.6
            b.append(Box((8 + i, hem, 8 + j), (8 + i + 1, 9, 8 + j + 1), W3 if d > 3.6 else W, 'flat'))
    b += [Box((2.9, 5.4, 6.8), (3.9, 7.0, 9.2), W2, 'flat'), Box((12.1, 5.4, 6.8), (13.1, 7.0, 9.2), W2, 'flat')]
    face = {'north'}
    b += [Box((5.6, 8.6, 3.75), (7.2, 10.8, 3.85), INK, 'flat', faces=face), Box((8.8, 8.6, 3.75), (10.4, 10.8, 3.85), INK, 'flat', faces=face),
          Box((7.4, 6.4, 3.8), (8.6, 7.8, 3.9), INK, 'flat', faces=face),
          Box((6.0, 10.0, 3.7), (6.6, 10.5, 3.75), '#ffffff', 'flat', faces=face), Box((9.2, 10.0, 3.7), (9.8, 10.5, 3.75), '#ffffff', 'flat', faces=face)]
    return b


# ---------------------------------------------------------------- writing it all

def _sprite_model(out, name, tex_name, w, h, light=15, crossed=False, flat_y=False, frametime=None, tex_img=None, interpolate=False):
    tex = f'{out}/assets/fractal/textures/item/cloak/part'
    os.makedirs(tex, exist_ok=True)
    tex_img.save(f'{tex}/{tex_name}.png')
    if frametime:
        json.dump({"animation": {"frametime": frametime, "interpolate": interpolate, "width": tex_img.width, "height": tex_img.width * h // w if False else
                                 (tex_img.height // (tex_img.height // (tex_img.width * h // w)))}},
                  open(f'{tex}/{tex_name}.png.mcmeta', 'w'))
    full = [0, 0, 16, 16]
    if flat_y:
        els = [{"from": [0, 8, 0], "to": [16, 8, 16], "shade": False, "light_emission": light,
                "faces": {"up": {"uv": full, "texture": "#t"}, "down": {"uv": full, "texture": "#t"}}}]
    else:
        x0, x1 = 8 - w / 2, 8 + w / 2
        els = [{"from": [x0, 0, 8], "to": [x1, h, 8], "shade": False, "light_emission": light,
                "faces": {"north": {"uv": full, "texture": "#t"}, "south": {"uv": full, "texture": "#t"}}}]
        if crossed:
            els.append({"from": [8, 0, x0], "to": [8, h, x1], "shade": False, "light_emission": light,
                        "faces": {"east": {"uv": full, "texture": "#t"}, "west": {"uv": full, "texture": "#t"}}})
    for d in ('models/item/cloak/part', 'items/cloak/part'):
        os.makedirs(f'{out}/assets/fractal/{d}', exist_ok=True)
    json.dump({"textures": {"t": f"fractal:item/cloak/part/{tex_name}", "particle": f"fractal:item/cloak/part/{tex_name}"}, "elements": els},
              open(f'{out}/assets/fractal/models/item/cloak/part/{name}.json', 'w'))
    json.dump({"model": {"type": "minecraft:model", "model": f"fractal:item/cloak/part/{name}"}},
              open(f'{out}/assets/fractal/items/cloak/part/{name}.json', 'w'))


def write_all(out, Box, write):
    H = 0.5
    # sparkle stars: flat, upright squares facing you (the display turns them to face the viewer)
    for name, core, tint in (('star_white', '#ffffff', '#cfe4ff'), ('star_pink', '#ffd6f2', '#ff7be5'),
                             ('star_gold', '#fff4c0', '#ffc83d'), ('star_lilac', '#efe0ff', '#b98cff')):
        _sprite_model(out, name, name, 16, 16, tex_img=star(core, tint))
    _sprite_model(out, 'raindrop', 'raindrop', 16, 16, light=12, tex_img=raindrop())
    _sprite_model(out, 'bolt', 'bolt', 16, 16, tex_img=bolt())
    _sprite_model(out, 'sun_rays2', 'sun_rays2', 16, 16, tex_img=sun_rays())
    _sprite_model(out, 'sun_glow', 'sun_glow', 16, 16, tex_img=sun_glow())
    ring = fire_ring_tex()
    tex = f'{out}/assets/fractal/textures/item/cloak/part'
    os.makedirs(tex, exist_ok=True)
    _sprite_model(out, 'fire_ring', 'fire_ring', 16, 16, flat_y=True, tex_img=ring)
    json.dump({"animation": {"frametime": 3, "width": 32, "height": 32}}, open(f'{tex}/fire_ring.png.mcmeta', 'w'))
    fl = flame_tex()
    _sprite_model(out, 'flame_tongue', 'flame_tongue', 8, 16, crossed=True, tex_img=fl)
    json.dump({"animation": {"frametime": 2, "width": 16, "height": 32}}, open(f'{tex}/flame_tongue.png.mcmeta', 'w'))
    for name in ('fire_ring', 'flame_tongue'):                          # (the uv covers one frame: these are taller than wide)
        pass

    # halo: a smooth glowing gold ring with a bright inner edge on top
    halo = ring_segments(Box, 8, 8, 8, 5.2, 24 if False else 16, 1.1, 0.9, '#ffcf5a', glow=15)
    halo += ring_segments(Box, 8, 8.5, 8, 5.2, 16, 0.5, 0.12, '#fff6c8', glow=15)
    write(out, P + 'halo_ring', halo, scale=H)

    # rain cloud: soft grey, darker underneath
    write(out, P + 'rain_cloud2', heightmap_cloud(Box, '#e4e8f0', '#c9cfdb', '#8f98aa'), scale=H)

    # planets
    def ringed_col(x, z, h):
        return '#f2cf8a' if (int((z + 8) * 0.9) % 2 == 0) else '#d8a85a'
    rp = sphere(Box, 8, 8, 8, 3.6, ringed_col, glow=12)
    rp += ring_segments(Box, 8, 8, 8, 5.6, 16, 1.4, 0.3, '#ffe9b8', glow=12)
    write(out, P + 'planet_ringed', rp, scale=H)
    rnd = random.Random(4)
    land = {(rnd.randint(-4, 3), rnd.randint(-4, 3)) for _ in range(9)}
    def earth(x, z, h):
        cell = (int(math.floor(x)), int(math.floor(z)))
        return '#5be37c' if cell in land or (cell[0] + 1, cell[1]) in land else '#4a8cff'
    write(out, P + 'planet_blue', sphere(Box, 8, 8, 8, 3.8, earth, glow=12), scale=H)
    def pink(x, z, h):
        return '#ffb3ec' if h > 3.0 else '#ff6fd8'
    write(out, P + 'planet_pink', sphere(Box, 8, 8, 8, 3.0, pink, glow=12) +
          sphere(Box, 8, 8, 8, 0.01, pink), scale=H)

    # the sun: a round glowing ball, hot white-yellow in the middle columns, deeper gold to the edges
    def sun(x, z, h):
        r = math.hypot(x, z)
        return '#fff6c0' if r < 1.6 else '#ffe066' if r < 3.0 else '#ffc83d'
    write(out, P + 'sun_ball', sphere(Box, 8, 8, 8, 4.2, sun, glow=15), scale=H)

    # the spooky ghost
    write(out, P + 'spook_ghost', ghost(Box), scale=H)
