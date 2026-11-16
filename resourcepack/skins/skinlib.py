"""Tiny helpers to paint 64x64 Minecraft skins box by box, plus a front/back preview renderer."""
from PIL import Image
import random

# (u, v, w, h, d) of each box in the 64x64 layout
BOXES = {
    'head': (0, 0, 8, 8, 8), 'hat': (32, 0, 8, 8, 8),
    'body': (16, 16, 8, 12, 4), 'jacket': (16, 32, 8, 12, 4),
    'rarm': (40, 16, 4, 12, 4), 'rsleeve': (40, 32, 4, 12, 4),
    'larm': (32, 48, 4, 12, 4), 'lsleeve': (48, 48, 4, 12, 4),
    'rleg': (0, 16, 4, 12, 4), 'rpants': (0, 32, 4, 12, 4),
    'lleg': (16, 48, 4, 12, 4), 'lpants': (0, 48, 4, 12, 4),
}

def faces(box):
    u, v, w, h, d = BOXES[box]
    return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h),
            'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h)}

def hexc(h):
    h = h.lstrip('#'); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))

def shade(c, f):
    return tuple(max(0, min(255, round(x * f))) for x in c[:3])

class Skin:
    def __init__(self, seed=1):
        self.img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
        self.rng = random.Random(seed)

    def put(self, x, y, c, noise=4):
        if len(c) == 4 and c[3] == 0:
            self.img.putpixel((x, y), (0, 0, 0, 0)); return
        n = self.rng.randint(-noise, noise) if noise else 0
        self.img.putpixel((x, y), tuple(max(0, min(255, ch + n)) for ch in c[:3]) + (255,))

    def fill(self, box, colour, face_list=None, noise=4, shading=True):
        """Every face of a box in one colour, a touch darker towards the bottom and on the sides/back."""
        c = hexc(colour) if isinstance(colour, str) else colour
        for name, (x0, y0, w, h) in faces(box).items():
            if face_list and name not in face_list: continue
            base = {'top': 1.08, 'bottom': 0.8, 'front': 1.0, 'back': 0.88, 'left': 0.92, 'right': 0.92}[name]
            for y in range(h):
                for x in range(w):
                    f = base * (1 - (0.12 * y / max(1, h - 1) if shading and name not in ('top', 'bottom') else 0))
                    self.put(x0 + x, y0 + y, shade(c, f), noise)

    def rows(self, box, face, art, palette, noise=3):
        """Paint pixel art (list of strings, '.' = leave) onto one face."""
        x0, y0, w, h = faces(box)[face]
        for y, row in enumerate(art):
            for x, ch in enumerate(row):
                if ch == '.' or x >= w or y >= h: continue
                if ch == '_':  # see-through (outer layers)
                    self.img.putpixel((x0 + x, y0 + y), (0, 0, 0, 0)); continue
                col = palette[ch]
                if col is None:
                    self.img.putpixel((x0 + x, y0 + y), (0, 0, 0, 0)); continue
                self.put(x0 + x, y0 + y, hexc(col) if isinstance(col, str) else col, noise)

    def band(self, box, y_from, y_to, colour, faces_list=('front', 'back', 'left', 'right'), noise=3):
        c = hexc(colour)
        for name in faces_list:
            x0, y0, w, h = faces(box)[name]
            f = {'front': 1.0, 'back': 0.88, 'left': 0.92, 'right': 0.92}[name]
            for y in range(max(0, y_from), min(h, y_to)):
                for x in range(w): self.put(x0 + x, y0 + y, shade(c, f * (1 - 0.1 * y / max(1, h - 1))), noise)

    def clear(self, box):
        u, v, w, h, d = BOXES[box]
        for y in range(v, v + d + h):
            for x in range(u, u + 2 * (d + w)):
                self.img.putpixel((x, y), (0, 0, 0, 0))

    def save(self, path):
        self.img.save(path)

def preview(img, scale=8):
    """Front and back views side by side (outer layer drawn over the inner one)."""
    def face(box, name):
        x0, y0, w, h = faces(box)[name]; return img.crop((x0, y0, x0 + w, y0 + h))
    out = Image.new('RGBA', (16 * 2 + 6, 32), (58, 60, 80, 255))
    for col, side in ((0, 'front'), (22, 'back')):
        parts = [('head', 'hat', 4, 0, 8), ('body', 'jacket', 4, 8, 8), ('rarm', 'rsleeve', 0, 8, 4), ('larm', 'lsleeve', 12, 8, 4),
                 ('rleg', 'rpants', 4, 20, 4), ('lleg', 'lpants', 8, 20, 4)]
        for inner, outer, x, y, w in parts:
            if side == 'back':  # mirrored: right arm on the right
                x = {0: 12, 12: 0, 4: 4 if inner in ('head', 'body') else (8 if inner == 'rleg' else 4), 8: 4}.get(x, x)
                if inner in ('head', 'body'): x = 4
            a = face(inner, side); b = face(outer, side)
            out.alpha_composite(a, (col + x, y)); out.alpha_composite(b, (col + x, y))
    return out.resize((out.width * scale, out.height * scale), Image.NEAREST)
