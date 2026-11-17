"""Renders item models (elements, uv, y/x/z rotations) to a picture, to check the 3D models by eye."""
import json, math, sys
from PIL import Image, ImageDraw

FACE_CORNERS = {  # corners (as from/to picks) in order top-left, top-right, bottom-right, bottom-left of the texture
    'north': [(1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)],
    'south': [(0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)],
    'east': [(1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)],
    'west': [(0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)],
    'up': [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)],
    'down': [(0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)],
}
LIGHT = {'up': 1.0, 'down': 0.5, 'north': 0.8, 'south': 0.8, 'east': 0.6, 'west': 0.6}


def rot(p, r):
    if not r:
        return p
    a = math.radians(r['angle'])
    o = r.get('origin', [8, 8, 8])
    x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
    c, s = math.cos(a), math.sin(a)
    if r['axis'] == 'y':
        x, z = x * c + z * s, -x * s + z * c
    elif r['axis'] == 'x':
        y, z = y * c - z * s, y * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + o[0], y + o[1], z + o[2])


def render(model_path, tex_root, size=256, yaw=-35, pitch=25, zoom=1.0):
    m = json.load(open(model_path))
    texs = {}
    for k, v in m['textures'].items():
        if k == 'particle':
            continue
        p = v.split(':')[-1]
        try:
            texs['#' + k] = Image.open(f'{tex_root}/{p}.png').convert('RGBA')
        except FileNotFoundError:
            pass
    yw, pt = math.radians(yaw), math.radians(pitch)

    def proj(p):
        x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
        x, z = x * math.cos(yw) - z * math.sin(yw), x * math.sin(yw) + z * math.cos(yw)
        y, z = y * math.cos(pt) - z * math.sin(pt), y * math.sin(pt) + z * math.cos(pt)
        k = size / 40 * zoom
        return (size / 2 + x * k, size / 2 - y * k, z)
    faces = []
    for el in m['elements']:
        f, t = el['from'], el['to']
        for name, face in el['faces'].items():
            pts3 = [rot((t[0] if c[0] else f[0], t[1] if c[1] else f[1], t[2] if c[2] else f[2]), el.get('rotation'))
                    for c in FACE_CORNERS[name]]
            pts = [proj(p) for p in pts3]
            depth = sum(p[2] for p in pts) / 4
            faces.append((depth, pts, face, name, el.get('light_emission', 0)))
    img = Image.new('RGBA', (size, size), (60, 63, 85, 255))
    for depth, pts, face, name, glow in sorted(faces, key=lambda f: f[0]):
        tex = texs.get(face['texture'])
        if tex is None:
            continue
        u0, v0, u1, v1 = face['uv']
        W, H = tex.size
        crop = tex.crop((min(u0, u1) * W / 16, min(v0, v1) * H / 16, max(u0, u1) * W / 16, max(v0, v1) * H / 16))
        if crop.width < 1 or crop.height < 1:
            continue
        if u0 > u1:
            crop = crop.transpose(Image.FLIP_LEFT_RIGHT)
        if v0 > v1:
            crop = crop.transpose(Image.FLIP_TOP_BOTTOM)
        crop = crop.resize((max(1, crop.width * 8), max(1, crop.height * 8)), Image.NEAREST)
        l = 1.0 if glow else LIGHT[name]
        if l < 1:
            r, g, b, a = crop.split()
            r, g, b = (ch.point(lambda v: int(v * l)) for ch in (r, g, b))
            crop = Image.merge('RGBA', (r, g, b, a))
        quad = [(p[0], p[1]) for p in pts]
        xs, ys = [q[0] for q in quad], [q[1] for q in quad]
        bx0, by0, bx1, by1 = int(min(xs)), int(min(ys)), int(max(xs)) + 1, int(max(ys)) + 1
        if bx1 - bx0 < 1 or by1 - by0 < 1:
            continue
        # map the destination quad back onto the texture with a perspective transform
        try:
            coeffs = _coeffs([(q[0] - bx0, q[1] - by0) for q in quad], [(0, 0), (crop.width, 0), (crop.width, crop.height), (0, crop.height)])
        except Exception:
            continue
        warped = crop.transform((bx1 - bx0, by1 - by0), Image.PERSPECTIVE, coeffs, Image.NEAREST)
        mask = Image.new('L', warped.size, 0)
        ImageDraw.Draw(mask).polygon([(q[0] - bx0, q[1] - by0) for q in quad], fill=255)
        a = warped.getchannel('A').point(lambda v: 255 if v > 0 else 0)
        from PIL import ImageChops
        warped.putalpha(ImageChops.multiply(a, mask))
        img.alpha_composite(warped, (bx0, by0))
    return img


def _coeffs(dst, src):
    import numpy as np
    A, B = [], []
    for (x, y), (u, v) in zip(dst, src):
        A.append([x, y, 1, 0, 0, 0, -u * x, -u * y]); B.append(u)
        A.append([0, 0, 0, x, y, 1, -v * x, -v * y]); B.append(v)
    return np.linalg.solve(np.array(A, float), np.array(B, float)).tolist()


if __name__ == '__main__':
    root = sys.argv[1]
    out = sys.argv[2]
    paths = sys.argv[3:]
    ims = []
    for p in paths:
        for yaw in ((180, 0) if 'wing' in p else (-35, 145)):
            ims.append(render(f'{root}/assets/fractal/models/item/{p}.json', f'{root}/assets/fractal/textures', yaw=yaw, pitch=0 if 'wing' in p else 25))
    cols = 6
    sheet = Image.new('RGBA', (cols * 256, ((len(ims) + cols - 1) // cols) * 256))
    for i, im in enumerate(ims):
        sheet.paste(im, ((i % cols) * 256, (i // cols) * 256))
    sheet.save(out)
