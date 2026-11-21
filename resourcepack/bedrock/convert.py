"""
Fractal Lobby: Java resource pack -> Bedrock resource pack + Geyser custom item mappings (format 2).

Every fractal:* item model gets a Bedrock item whose icon is either its flat texture (generated/handheld
models) or a picture of its 3D model drawn the way the Java inventory shows it (display.gui). One
Bedrock item per (Java base item, model) pair the Lobby and Radio plugins use (pairs.json).

Usage: python3 convert.py JAVA_PACK_DIR VANILLA_DIR PAIRS_JSON OUT_DIR
"""
import json, math, os, re, sys, uuid, zipfile, shutil
import numpy as np
from PIL import Image

JAVA, VANILLA, PAIRS, OUT = sys.argv[1:5]
ROOTS = [JAVA, VANILLA]
ICON = 64                      # size of rendered 3D icons
NS_UUID = uuid.UUID("6f1b4b2e-6a51-4d3e-9a1e-f2ac7a1b0001")


def find(kind, ref, ext):
    ns, path = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
    for r in ROOTS:
        f = os.path.join(r, "assets", ns, kind, path + ext)
        if os.path.exists(f):
            return f
    return None


def load_json(kind, ref):
    f = find(kind, ref, ".json")
    return json.load(open(f)) if f else None


_tex = {}
def texture(ref):
    if ref in _tex:
        return _tex[ref]
    f = find("textures", ref, ".png")
    if not f:
        img = Image.new("RGBA", (16, 16), (255, 0, 255, 255))
    else:
        img = Image.open(f).convert("RGBA")
        if img.height > img.width and img.height % img.width == 0:   # animated: first frame
            img = img.crop((0, 0, img.width, img.width))
    _tex[ref] = img
    return img


def resolve_model(ref):
    """Follow parents: merged textures, the nearest elements, merged display, gui_light."""
    textures, display, elements, gui_light, chain = {}, {}, None, None, []
    cur = ref
    while cur:
        m = load_json("models", cur)
        if m is None:
            break
        chain.append(cur)
        for k, v in m.get("textures", {}).items():
            textures.setdefault(k, v)
        for k, v in m.get("display", {}).items():
            display.setdefault(k, v)
        if elements is None and "elements" in m:
            elements = m["elements"]
        if gui_light is None and "gui_light" in m:
            gui_light = m["gui_light"]
        cur = m.get("parent")
    def tex(name, depth=0):
        v = textures.get(name.lstrip("#"))
        if v is None or depth > 10:
            return None
        return tex(v, depth + 1) if v.startswith("#") else v
    flat = any(c.endswith(("item/generated", "builtin/generated")) for c in chain) or (elements is None)
    return {"chain": chain, "tex": tex, "display": display, "elements": elements, "flat": flat,
            "gui_light": gui_light or "side"}


def model_of(item_def):
    """The model an item definition shows (the fallback / first case for select and condition)."""
    m = item_def
    for _ in range(10):
        t = m.get("type", "").replace("minecraft:", "")
        if t == "model":
            return m["model"]
        if t == "select":
            m = m.get("fallback") or m["cases"][0]["model"]
        elif t == "condition":
            m = m.get("on_false") or m.get("on_true")
        elif t == "range_dispatch":
            m = m.get("fallback") or m["entries"][0]["model"]
        elif t in ("composite",):
            m = m["models"][0]
        else:
            return None
    return None


# ------------------------------------------------------------------ icons

def flat_icon(model):
    layers = []
    for i in range(8):
        t = model["tex"]("layer%d" % i)
        if not t:
            break
        layers.append(texture(t))
    if not layers:
        t = model["tex"]("particle")
        layers = [texture(t)] if t else [Image.new("RGBA", (16, 16))]
    size = max(l.width for l in layers)
    out = Image.new("RGBA", (size, size))
    for l in layers:
        out.alpha_composite(l.resize((size, size), Image.NEAREST))
    return out


def rot_matrix(rx, ry, rz):
    rx, ry, rz = map(math.radians, (rx, ry, rz))
    X = np.array([[1, 0, 0], [0, math.cos(rx), -math.sin(rx)], [0, math.sin(rx), math.cos(rx)]])
    Y = np.array([[math.cos(ry), 0, math.sin(ry)], [0, 1, 0], [-math.sin(ry), 0, math.cos(ry)]])
    Z = np.array([[math.cos(rz), -math.sin(rz), 0], [math.sin(rz), math.cos(rz), 0], [0, 0, 1]])
    return X @ Y @ Z


FACES = {   # corners TL, TR, BR, BL (as seen from outside) and the outward normal
    "north": (lambda a, b: [(b[0], b[1], a[2]), (a[0], b[1], a[2]), (a[0], a[1], a[2]), (b[0], a[1], a[2])], (0, 0, -1)),
    "south": (lambda a, b: [(a[0], b[1], b[2]), (b[0], b[1], b[2]), (b[0], a[1], b[2]), (a[0], a[1], b[2])], (0, 0, 1)),
    "west":  (lambda a, b: [(a[0], b[1], a[2]), (a[0], b[1], b[2]), (a[0], a[1], b[2]), (a[0], a[1], a[2])], (-1, 0, 0)),
    "east":  (lambda a, b: [(b[0], b[1], b[2]), (b[0], b[1], a[2]), (b[0], a[1], a[2]), (b[0], a[1], b[2])], (1, 0, 0)),
    "up":    (lambda a, b: [(a[0], b[1], a[2]), (b[0], b[1], a[2]), (b[0], b[1], b[2]), (a[0], b[1], b[2])], (0, 1, 0)),
    "down":  (lambda a, b: [(a[0], a[1], b[2]), (b[0], a[1], b[2]), (b[0], a[1], a[2]), (a[0], a[1], a[2])], (0, -1, 0)),
}

def default_uv(face, a, b):
    x1, y1, z1 = a; x2, y2, z2 = b
    return {"north": [16 - x2, 16 - y2, 16 - x1, 16 - y1], "south": [x1, 16 - y2, x2, 16 - y1],
            "west": [z1, 16 - y2, z2, 16 - y1], "east": [16 - z2, 16 - y2, 16 - z1, 16 - y1],
            "up": [x1, z1, x2, z2], "down": [x1, 16 - z2, x2, 16 - z1]}[face]


def element_rotation(el):
    r = el.get("rotation")
    if not r:
        return None
    if "axis" in r:
        ang = math.radians(r.get("angle", 0))
        c, s = math.cos(ang), math.sin(ang)
        M = {"x": np.array([[1, 0, 0], [0, c, -s], [0, s, c]]),
             "y": np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]]),
             "z": np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])}[r["axis"]]
        if r.get("rescale"):
            k = 1 / math.cos(ang) if abs(math.cos(ang)) > 1e-6 else 1
            sc = {"x": [1, k, k], "y": [k, 1, k], "z": [k, k, 1]}[r["axis"]]
            M = M @ np.diag(sc)
    else:   # newer per-axis form
        M = rot_matrix(r.get("x", 0), r.get("y", 0), r.get("z", 0))
    return np.array(r.get("origin", [8, 8, 8]), float), M


def render_3d(model):
    gui = model["display"].get("gui", {})
    R = rot_matrix(*gui.get("rotation", [0, 0, 0]))
    S = np.array(gui.get("scale", [1, 1, 1]), float)
    T = np.array(gui.get("translation", [0, 0, 0]), float) / 16
    shade = model["gui_light"] != "front"
    SS = ICON * 2                                          # draw at 2x, then shrink
    canvas = np.zeros((SS, SS, 4), float)
    faces = []
    for el in model["elements"] or []:
        a, b = el["from"], el["to"]
        er = element_rotation(el)
        for name, f in el.get("faces", {}).items():
            corners_fn, normal = FACES[name]
            pts = np.array(corners_fn(a, b), float)
            n = np.array(normal, float)
            if er:
                o, M = er
                pts = (pts - o) @ M.T + o
                n = M @ n
            p = (pts / 16 - 0.5) * S
            p = p @ R.T + T
            n = R @ n
            n = n / (np.linalg.norm(n) or 1)
            if n[2] < -1e-4:                               # facing away from the camera
                continue
            tref = model["tex"](f.get("texture", "#missing"))
            img = texture(tref) if tref else Image.new("RGBA", (16, 16), (255, 0, 255, 255))
            uv = f.get("uv") or default_uv(name, a, b)
            faces.append((p[:, 2].mean(), p, n, img, uv, f.get("rotation", 0)))
    faces.sort(key=lambda t: t[0])                         # far first
    ys, xs = np.mgrid[0:SS, 0:SS]
    px = (xs + 0.5) / SS - 0.5
    py = 0.5 - (ys + 0.5) / SS
    for _, p, n, img, uv, frot in faces:
        tl, tr, br, bl = p[:, :2]
        u1, v1, u2, v2 = uv
        uvc = [(u1, v1), (u2, v1), (u2, v2), (u1, v2)]
        k = (frot // 90) % 4
        uvc = uvc[k:] + uvc[:k]
        e1, e2 = tr - tl, bl - tl
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-9:
            continue
        dx, dy = px - tl[0], py - tl[1]
        A = (dx * e2[1] - dy * e2[0]) / det
        B = (e1[0] * dy - e1[1] * dx) / det
        inside = (A >= -1e-6) & (A <= 1 + 1e-6) & (B >= -1e-6) & (B <= 1 + 1e-6)
        if not inside.any():
            continue
        U = uvc[0][0] + A * (uvc[1][0] - uvc[0][0]) + B * (uvc[3][0] - uvc[0][0])
        V = uvc[0][1] + A * (uvc[1][1] - uvc[0][1]) + B * (uvc[3][1] - uvc[0][1])
        t = np.asarray(img, float) / 255
        tw, th = img.width, img.height
        ti = np.clip((U / 16 * tw).astype(int), 0, tw - 1)
        tj = np.clip((V / 16 * th).astype(int), 0, th - 1)
        col = t[tj, ti]
        light = 1.0
        if shade:
            light = 0.62 + 0.38 * max(0.0, 0.85 * n[1] + 0.45 * n[2] - 0.15 * n[0]) if n[1] >= 0 else 0.5
            light = min(1.0, max(0.45, light))
        alpha = col[..., 3] * inside
        alpha = np.where(alpha < 0.1, 0, alpha)
        rgb = col[..., :3] * light
        canvas[..., :3] = canvas[..., :3] * (1 - alpha[..., None]) + rgb * alpha[..., None]
        canvas[..., 3] = canvas[..., 3] * (1 - alpha) + alpha
    img = Image.fromarray((np.clip(canvas, 0, 1) * 255).astype(np.uint8), "RGBA")
    return img.resize((ICON, ICON), Image.LANCZOS)


# ------------------------------------------------------------------ build

def slug(s):
    return re.sub(r"[^a-z0-9_]+", "_", s.lower()).strip("_")


def main():
    pairs = json.load(open(PAIRS))
    if os.path.exists(OUT):
        shutil.rmtree(OUT)
    pack = os.path.join(OUT, "pack")
    os.makedirs(os.path.join(pack, "textures", "fractal"), exist_ok=True)
    item_texture = {"resource_pack_name": "fractal_lobby", "texture_name": "atlas.items", "texture_data": {}}
    mappings = {"format_version": 2, "items": {}}
    report = {"flat": 0, "3d": 0, "missing": [], "items": 0, "attachables": 0}
    three_d = []
    for path in sorted(pairs):
        item_def = load_json("items", "fractal:" + path)
        if not item_def:
            report["missing"].append(path); continue
        mref = model_of(item_def.get("model", {}))
        if not mref:
            report["missing"].append(path + " (no model)"); continue
        model = resolve_model(mref)
        if model["flat"]:
            icon = flat_icon(model); report["flat"] += 1
        else:
            icon = render_3d(model); report["3d"] += 1
            three_d.append((path, model, ["fractal:%s__%s" % (slug(path), b.lower()) for b in pairs[path]]))
        s = slug(path)
        rel = "textures/fractal/" + s
        icon.save(os.path.join(pack, rel + ".png"))
        key = "fractal." + s
        item_texture["texture_data"][key] = {"textures": [rel]}
        for base in pairs[path]:
            mappings["items"].setdefault("minecraft:" + base.lower(), []).append({
                "type": "definition",
                "model": "fractal:" + path,
                "bedrock_identifier": "fractal:%s__%s" % (s, base.lower()),
                "bedrock_options": {"icon": key, "allow_offhand": True},
            })
            report["items"] += 1
    json.dump(item_texture, open(os.path.join(pack, "textures", "item_texture.json"), "w"), indent=1)

    # 3D models: geometry, texture sheet and attachables (held, on heads, on display entities)
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import geo
    report["attachables"] = geo.build(pack, three_d, texture, slug)

    # Text glyphs. Bedrock draws U+XXYY from font/glyph_XX.png (16x16 cells); like the Survival addon's
    # pack, each Java glyph sits at its Java pixel size, left-aligned and centred in a 256 px cell.
    # Pages F1/F2 are the menus' drawn backgrounds (too big for a glyph): blank, so titles show no boxes.
    # E0/E1 (rank badges) are left to the Survival addon's pack, which already has them.
    glyphs = {}
    for font in ("minecraft:default", "fractal:lobby"):
        fj = load_json("font", font) or {"providers": []}
        for p in fj["providers"]:
            if p.get("type") != "bitmap":
                continue
            f = find("textures", p["file"], "")
            if not f:
                continue
            img = Image.open(f).convert("RGBA")
            rows = p["chars"]
            ch, cw = img.height // len(rows), img.width // max(len(r) for r in rows)
            h = p.get("height", 8)
            for ry, row in enumerate(rows):
                for rx, c in enumerate(row):
                    cp = ord(c)
                    if cp and (cp >> 8) in (0xF0, 0xF3, 0xE2) and cp not in glyphs:
                        g = img.crop((rx * cw, ry * ch, (rx + 1) * cw, (ry + 1) * ch))
                        if g.height != h:
                            g = g.resize((max(1, round(g.width * h / g.height)), h), Image.NEAREST)
                        glyphs[cp] = g
    os.makedirs(os.path.join(pack, "font"), exist_ok=True)
    for page in (0xE2, 0xF0, 0xF1, 0xF2, 0xF3):
        sheet = Image.new("RGBA", (4096, 4096))
        for cp, g in glyphs.items():
            if cp >> 8 == page:
                i = cp & 0xFF
                g = g if g.width <= 256 and g.height <= 256 else g.resize((min(256, g.width), min(256, g.height)), Image.NEAREST)
                sheet.paste(g, ((i % 16) * 256, (i // 16) * 256 + (256 - g.height) // 2))
        sheet.save(os.path.join(pack, "font", "glyph_%02X.png" % page), optimize=True)
    report["glyphs"] = len(glyphs)

    # Custom sounds (the radio's extra notes, the lobby's own sounds). Geyser sends a Java sound such as
    # radio:note.harp_1 to Bedrock under the same name (only "minecraft:" is dropped), so each namespace's
    # sounds.json events become Bedrock sound definitions with that full name, and the .ogg files are copied.
    defs = {}
    assets = os.path.join(JAVA, "assets")
    for ns in sorted(os.listdir(assets)):
        sj = os.path.join(assets, ns, "sounds.json")
        if ns == "minecraft" or not os.path.isfile(sj):
            continue
        for event, entry in json.load(open(sj, encoding="utf-8")).items():
            sounds = []
            for snd in entry.get("sounds", []):
                snd = {"name": snd} if isinstance(snd, str) else dict(snd)
                if snd.get("type") == "event":
                    continue
                sns, _, path = snd["name"].rpartition(":")
                sns = sns or "minecraft"
                src = os.path.join(assets, sns, "sounds", path + ".ogg")
                if not os.path.isfile(src):
                    continue
                dst = os.path.join("sounds", sns, path)
                os.makedirs(os.path.dirname(os.path.join(pack, dst)), exist_ok=True)
                shutil.copyfile(src, os.path.join(pack, dst + ".ogg"))
                out = {"name": dst}
                for k in ("volume", "pitch", "stream"):
                    if k in snd:
                        out[k] = snd[k]
                sounds.append(out)
            if sounds:
                defs[ns + ":" + event] = {"category": "record" if ns == "radio" else "neutral", "sounds": sounds}
    if defs:
        os.makedirs(os.path.join(pack, "sounds"), exist_ok=True)
        json.dump({"format_version": "1.14.0", "sound_definitions": defs},
                  open(os.path.join(pack, "sounds", "sound_definitions.json"), "w"), indent=1)
    report["sounds"] = len(defs)

    manifest = {
        "format_version": 2,
        "header": {"name": "Fractal Lobby", "description": "Fractal Network lobby items for Bedrock",
                   "uuid": str(uuid.uuid5(NS_UUID, "header")), "version": [1, 2, 2], "min_engine_version": [1, 21, 0]},
        "modules": [{"type": "resources", "uuid": str(uuid.uuid5(NS_UUID, "resources")), "version": [1, 2, 2]}],
    }
    json.dump(manifest, open(os.path.join(pack, "manifest.json"), "w"), indent=1)
    logo = find("textures", "fractal:gui/logo", ".png")
    (Image.open(logo) if logo else icon).convert("RGBA").resize((128, 128), Image.NEAREST).save(os.path.join(pack, "pack_icon.png"))
    json.dump(mappings, open(os.path.join(OUT, "fractal_lobby_mappings.json"), "w"), indent=1)
    with zipfile.ZipFile(os.path.join(OUT, "FractalLobby.mcpack"), "w", zipfile.ZIP_DEFLATED) as z:
        for root, _, files in os.walk(pack):
            for f in files:
                full = os.path.join(root, f)
                z.write(full, os.path.relpath(full, pack))
    json.dump(report, open(os.path.join(OUT, "report.json"), "w"), indent=1)
    print(json.dumps({k: (v if not isinstance(v, list) else len(v)) for k, v in report.items()}), report["missing"][:10])


main()
