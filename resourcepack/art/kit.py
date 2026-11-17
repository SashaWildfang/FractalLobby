"""
A small painting kit for the HD (32x32) cosmetic icons.

Shapes are drawn 8x larger with soft gradients (light from the top left), then scaled down, cleaned up (no
half-see-through fringe) and given a dark outline in a deeper shade of whatever they touch, so they read like the
game's own items but with more detail.
"""
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

S = 8
PIXEL = False   # True: crisp pixel art (no smoothing, flat bands, dark outline)
BANDS = 0   # >0: gradients become this many flat bands (pixel-art shading)


def hexc(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def rgb(c):
    return hexc(c) if isinstance(c, str) else tuple(c[:3])


def mix(a, b, t):
    a, b = rgb(a), rgb(b)
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def light(c, f=0.35):
    return mix(c, (255, 255, 255), f)


def dark(c, f=0.35):
    """Darker, keeping the hue (shadows lean a little cool)."""
    c = rgb(c)
    target = (c[0] * 0.28 + 6, c[1] * 0.26 + 4, c[2] * 0.34 + 18)
    return mix(c, target, min(1.0, f * 1.25))


class Canvas:
    def __init__(self, w=32, h=32):
        self.w, self.h = w, h
        self.img = Image.new('RGBA', (w * S, h * S), (0, 0, 0, 0))

    # ---------------------------------------------------------------- masks
    def _mask(self):
        return Image.new('L', self.img.size, 0)

    @staticmethod
    def _pts(points):
        return [(x * S, y * S) for x, y in points]

    def mask(self, kind, geom, width=1.0):
        m = self._mask()
        d = ImageDraw.Draw(m)
        if kind == 'ellipse':
            x0, y0, x1, y1 = geom
            d.ellipse([x0 * S, y0 * S, x1 * S - 1, y1 * S - 1], fill=255)
        elif kind == 'rect':
            x0, y0, x1, y1 = geom
            d.rectangle([x0 * S, y0 * S, x1 * S - 1, y1 * S - 1], fill=255)
        elif kind == 'rrect':
            x0, y0, x1, y1, r = geom
            d.rounded_rectangle([x0 * S, y0 * S, x1 * S - 1, y1 * S - 1], radius=r * S, fill=255)
        elif kind == 'poly':
            d.polygon(self._pts(geom), fill=255)
        elif kind == 'line':
            pts = self._pts(geom)
            d.line(pts, fill=255, width=max(1, round(width * S)), joint='curve')
            r = width * S / 2
            for x, y in (pts[0], pts[-1]):
                d.ellipse([x - r, y - r, x + r, y + r], fill=255)
        elif kind == 'arc':
            (x0, y0, x1, y1), a0, a1 = geom
            d.arc([x0 * S, y0 * S, x1 * S, y1 * S], a0, a1, fill=255, width=max(1, round(width * S)))
        return m

    # ---------------------------------------------------------------- fills
    def _fill_image(self, fill, m):
        W, H = self.img.size
        if isinstance(fill, tuple) and fill and fill[0] in ('lin', 'rad'):
            bbox = m.getbbox() or (0, 0, W, H)
            ys, xs = np.mgrid[0:H, 0:W].astype(np.float32)
            if fill[0] == 'lin':
                _, c0, c1, angle = fill
                a = math.radians(angle)
                dx, dy = math.cos(a), math.sin(a)
                corners = [(bbox[0], bbox[1]), (bbox[2], bbox[1]), (bbox[0], bbox[3]), (bbox[2], bbox[3])]
                proj = [cx * dx + cy * dy for cx, cy in corners]
                lo, hi = min(proj), max(proj)
                t = ((xs * dx + ys * dy) - lo) / max(1e-6, hi - lo)
            else:
                _, c0, c1, fx, fy, fr = fill   # fx, fy, fr in 0..1 of the bbox (centre of light, radius)
                bw, bh = bbox[2] - bbox[0], bbox[3] - bbox[1]
                cx, cy = bbox[0] + fx * bw, bbox[1] + fy * bh
                r = fr * max(bw, bh)
                t = np.sqrt((xs - cx) ** 2 + (ys - cy) ** 2) / max(1e-6, r)
            t = np.clip(t, 0, 1)
            if BANDS:
                t = np.minimum(np.floor(t * BANDS), BANDS - 1) / max(1, BANDS - 1)
            t = t[..., None]
            a, b = np.array(rgb(c0), np.float32), np.array(rgb(c1), np.float32)
            arr = a + (b - a) * t
            out = np.concatenate([arr, np.full((H, W, 1), 255, np.float32)], axis=2).astype(np.uint8)
            return Image.fromarray(out, 'RGBA')
        c = rgb(fill)
        alpha = fill[3] if isinstance(fill, tuple) and len(fill) == 4 else 255
        return Image.new('RGBA', self.img.size, c + (alpha,))

    def paint(self, m, fill, opacity=1.0):
        g = self._fill_image(fill, m)
        a = np.array(g.getchannel('A'), np.float32) * (np.array(m, np.float32) / 255.0) * opacity
        g.putalpha(Image.fromarray(a.astype(np.uint8), 'L'))
        self.img.alpha_composite(g)

    def shape(self, kind, geom, fill, width=1.0, opacity=1.0):
        m = self.mask(kind, geom, width)
        self.paint(m, fill, opacity)
        return m

    # ---------------------------------------------------------------- shaded helpers
    def ball(self, cx, cy, r, base, shine=True):
        """A shaded sphere with a highlight."""
        self.shape('ellipse', (cx - r, cy - r, cx + r, cy + r), ('rad', light(base, 0.45), dark(base, 0.3), 0.32, 0.28, 1.0))
        if shine:
            self.shape('ellipse', (cx - r * 0.62, cy - r * 0.68, cx - r * 0.12, cy - r * 0.25), (255, 255, 255), opacity=0.75)

    def solid(self, kind, geom, base, angle=60, width=1.0, strength=0.3):
        """A shape lit from the top left."""
        return self.shape(kind, geom, ('lin', light(base, strength), dark(base, strength), angle), width)

    def line(self, pts, colour, width=1.0, opacity=1.0):
        return self.shape('line', pts, colour, width, opacity)

    def star(self, cx, cy, r, colour, points=4, inner=0.38, rot=-90, opacity=1.0):
        pts = []
        for i in range(points * 2):
            a = math.radians(rot + i * 180 / points)
            rr = r if i % 2 == 0 else r * inner
            pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
        return self.shape('poly', pts, colour, opacity=opacity)

    def glow(self, cx, cy, r, colour, opacity=0.6):
        if PIXEL:
            return
        m = self.mask('ellipse', (cx - r, cy - r, cx + r, cy + r)).filter(ImageFilter.GaussianBlur(r * S / 2.5))
        self.paint(m, colour, opacity)

    def erase(self, kind, geom, width=1.0):
        m = self.mask(kind, geom, width)
        a = np.array(self.img.getchannel('A'), np.float32) * (1 - np.array(m, np.float32) / 255.0)
        self.img.putalpha(Image.fromarray(a.astype(np.uint8), 'L'))

    # ---------------------------------------------------------------- finishing
    def done(self, outline=True, keep_soft=False):
        if PIXEL:
            small = self.img.resize((self.w, self.h), Image.NEAREST)
            keep_soft = False
        else:
            small = self.img.convert('RGBa').resize((self.w, self.h), Image.LANCZOS).convert('RGBA')
        arr = np.array(small).astype(np.int32)
        a = arr[..., 3]
        if keep_soft:
            # fade glows out before the edge, so no square shows around them
            H, W = a.shape
            ys, xs = np.mgrid[0:H, 0:W]
            cx, cy = (W - 1) / 2, (H - 1) / 2
            d = np.sqrt(((xs - cx) / (W / 2)) ** 2 + ((ys - cy) / (H / 2)) ** 2)
            fade = np.clip((1.02 - d) / 0.22, 0, 1)
            arr[..., 3] = (a * fade).astype(np.int32)
            arr[arr[..., 3] < 8, 3] = 0
        if not keep_soft:
            arr[a < 70, 3] = 0
            arr[a >= 70, 3] = 255
            # un-premultiply the dark fringe LANCZOS leaves on soft edges
        if outline:
            solid = arr[..., 3] > 0
            out = arr.copy()
            H, W = solid.shape
            for y in range(H):
                for x in range(W):
                    if solid[y, x]:
                        continue
                    near = [(y + dy, x + dx) for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0))
                            if 0 <= y + dy < H and 0 <= x + dx < W and solid[y + dy, x + dx]]
                    if near:
                        c = np.mean([arr[p][:3] for p in near], axis=0)
                        d = dark(tuple(int(v) for v in c), 0.85 if PIXEL else 0.62)
                        out[y, x] = list(d) + [255]
            arr = out
        return Image.fromarray(arr.clip(0, 255).astype(np.uint8), 'RGBA')


def sheet(images, scale=4, cols=8, bg=(60, 63, 85)):
    """A preview of many icons side by side."""
    w, h = images[0].size
    rows = (len(images) + cols - 1) // cols
    out = Image.new('RGBA', (cols * (w * scale + 8) + 8, rows * (h * scale + 8) + 8), bg + (255,))
    for i, im in enumerate(images):
        x, y = 8 + (i % cols) * (w * scale + 8), 8 + (i // cols) * (h * scale + 8)
        out.alpha_composite(im.resize((im.width * scale, im.height * scale), Image.NEAREST), (x, y))
    return out
