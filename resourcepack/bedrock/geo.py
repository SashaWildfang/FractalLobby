"""
3D models for the Bedrock pack (on a GeyserDisplayEntity display the model is drawn as Java's
"none" display transform shows it, full size, so world props keep their size): Java item models with elements -> Bedrock geometry + one texture sheet
+ an attachable per Bedrock item (held, worn on the head, and shown by display entities through
GeyserDisplayEntity, which wears the item on its head). Follows the java2bedrock/Blockbench conventions:
Bedrock X is Java X mirrored, origin [8 - to.x, from.y, from.z - 8], rotations [-x, -y, z], up/down face
UVs turned 180 degrees.

Called from convert.py: build(pack_dir, items) with items = [(model_path, model, [bedrock ids])].
"""
import json, math, os
from PIL import Image

ANIM = "animation.fractal_item"


def _region(img, uv, rot):
    """The face's picture, cut from its texture and flipped/turned as Java shows it on the face."""
    tw, th = img.size
    u1, v1, u2, v2 = uv
    x0, x1 = sorted((u1 / 16 * tw, u2 / 16 * tw))
    y0, y1 = sorted((v1 / 16 * th, v2 / 16 * th))
    box = (int(math.floor(x0 + 1e-6)), int(math.floor(y0 + 1e-6)), max(int(math.ceil(x1 - 1e-6)), int(math.floor(x0)) + 1),
           max(int(math.ceil(y1 - 1e-6)), int(math.floor(y0)) + 1))
    r = img.crop(box)
    if u1 > u2:
        r = r.transpose(Image.FLIP_LEFT_RIGHT)
    if v1 > v2:
        r = r.transpose(Image.FLIP_TOP_BOTTOM)
    if rot % 360:
        r = r.rotate(-(rot % 360), expand=True)          # Java turns the texture clockwise
    return r


class Sheet:
    """Shelf-packs face pictures into one texture (sizes rounded up to powers of two)."""
    def __init__(self):
        self.parts, self.x, self.y, self.row, self.w = [], 0, 0, 0, 64

    def add(self, img):
        w, h = img.size
        if self.x + w > self.w:
            self.x, self.y, self.row = 0, self.y + self.row, 0
        if w > self.w:
            self.w = 1 << (w - 1).bit_length()
        pos = (self.x, self.y)
        self.parts.append((img, pos))
        self.x += w
        self.row = max(self.row, h)
        return pos

    def image(self):
        h = max(16, self.y + self.row)
        h = 1 << (h - 1).bit_length()
        out = Image.new("RGBA", (self.w, h))
        for img, pos in self.parts:
            out.paste(img, pos)
        return out


def geometry(ident, model, texture_of):
    sheet, cubes = Sheet(), []
    for el in model["elements"]:
        a, b = el["from"], el["to"]
        cube = {"origin": [8 - b[0], a[1], a[2] - 8], "size": [b[0] - a[0], b[1] - a[1], b[2] - a[2]], "uv": {}}
        r = el.get("rotation")
        if r:
            o = r.get("origin", [8, 8, 8])
            cube["pivot"] = [8 - o[0], o[1], o[2] - 8]
            if "axis" in r:
                ang = r.get("angle", 0)
                cube["rotation"] = {"x": [-ang, 0, 0], "y": [0, -ang, 0], "z": [0, 0, ang]}[r["axis"]]
            else:
                cube["rotation"] = [-r.get("x", 0), -r.get("y", 0), r.get("z", 0)]
        for name, f in el.get("faces", {}).items():
            img = texture_of(model["tex"](f.get("texture", "#missing")))
            uv = f.get("uv") or default_uv(name, a, b)
            pic = _region(img, uv, f.get("rotation", 0))
            x, y = sheet.add(pic)
            w, h = pic.size
            if name in ("up", "down"):
                cube["uv"][name] = {"uv": [x + w, y + h], "uv_size": [-w, -h]}
            else:
                cube["uv"][name] = {"uv": [x, y], "uv_size": [w, h]}
        if cube["uv"]:
            cubes.append(cube)
    tex = sheet.image()
    geo = {"format_version": "1.16.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.fractal." + ident, "texture_width": tex.width, "texture_height": tex.height,
                        "visible_bounds_width": 4, "visible_bounds_height": 4.5, "visible_bounds_offset": [0, 0.75, 0]},
        "bones": [
            {"name": "fractal_item", "pivot": [0, 8, 0], "binding": "c.item_slot == 'head' ? 'head' : q.item_slot_to_bone_name(c.item_slot)"},
            {"name": "fractal_item_x", "parent": "fractal_item", "pivot": [0, 8, 0]},
            {"name": "fractal_item_y", "parent": "fractal_item_x", "pivot": [0, 8, 0]},
            {"name": "fractal_item_z", "parent": "fractal_item_y", "pivot": [0, 8, 0]},
            {"name": "model", "parent": "fractal_item_z", "pivot": [0, 8, 0], "cubes": cubes},
        ]}]}
    return geo, tex


def default_uv(face, a, b):
    x1, y1, z1 = a; x2, y2, z2 = b
    return {"north": [16 - x2, 16 - y2, 16 - x1, 16 - y1], "south": [x1, 16 - y2, x2, 16 - y1],
            "west": [z1, 16 - y2, z2, 16 - y1], "east": [16 - z2, 16 - y2, 16 - z1, 16 - y1],
            "up": [x1, z1, x2, z2], "down": [x1, 16 - z2, x2, 16 - z1]}[face]


def _display(model, key, fallback=None):
    d = model["display"].get(key) or (model["display"].get(fallback) if fallback else None) or {}
    rot = d.get("rotation", [0, 0, 0]); pos = d.get("translation", [0, 0, 0]); sc = d.get("scale", [1, 1, 1])
    return rot, pos, sum(sc) / 3


def attachable(bid, ident, model):
    v = []
    for var, key, fb in (("thirdperson_mainhand", "thirdperson_righthand", None), ("firstperson_mainhand", "firstperson_righthand", None),
                         ("thirdperson_head", "head", None)):
        rot, pos, sc = _display(model, key, fb)
        v += ["v.%s_rot_%s = %s;" % (var, ax, rot[i]) for i, ax in enumerate("xyz")]
        v += ["v.%s_pos_%s = %s;" % (var, ax, pos[i]) for i, ax in enumerate("xyz")]
        v.append("v.%s_scale = %s;" % (var, round(sc, 4)))
    return {"format_version": "1.10.0", "minecraft:attachable": {"description": {
        "identifier": bid,
        "materials": {"default": "entity_alphatest_one_sided", "enchanted": "entity_alphatest_glint"},
        "textures": {"default": "textures/fractal/models/" + ident, "enchanted": "textures/misc/enchanted_item_glint"},
        "geometry": {"default": "geometry.fractal." + ident},
        "animations": {"firstperson_hand": ANIM + ".firstperson_hand", "firstperson_head": ANIM + ".disable",
                       "thirdperson_hand": ANIM + ".thirdperson_hand", "thirdperson_head": ANIM + ".thirdperson_head"},
        "scripts": {
            "pre_animation": ["v.main_hand = c.item_slot == 'main_hand';", "v.off_hand = c.item_slot == 'off_hand';",
                              "v.head = c.item_slot == 'head';",
                              "v.display = q.is_owner_identifier_any('geyser:item_display');"],
            "initialize": v,
            "animate": [{"thirdperson_hand": "(v.main_hand||v.off_hand)&&!c.is_first_person"},
                        {"thirdperson_head": "v.head&&!c.is_first_person&&!v.display"},
                        {"firstperson_hand": "(v.main_hand||v.off_hand)&&c.is_first_person"},
                        {"firstperson_head": "c.is_first_person&&v.head&&!v.display"}]},
        "render_controllers": ["controller.render.item_default"]}}}


def animations():
    """Hand and head placement from the Java display settings (the java2bedrock set, renamed)."""
    def xyz(var, mirror_x=True):
        return {
            "fractal_item_x": {"position": ["-v.%s_pos_x" % var if mirror_x else "v.%s_pos_x" % var, "v.%s_pos_y" % var, "v.%s_pos_z" % var],
                               "rotation": ["-v.%s_rot_x" % var, 0, 0], "scale": "v.%s_scale" % var},
            "fractal_item_y": {"rotation": [0, "-v.%s_rot_y" % var, 0]},
            "fractal_item_z": {"rotation": [0, 0, "v.%s_rot_z" % var]}}
    fp = xyz("firstperson_mainhand"); fp["fractal_item"] = {
        "position": ["v.main_hand ? 0 : -18.5", "v.main_hand ? 15 : 18.3", "v.main_hand ? 2 : 15"],
        "rotation": ["v.main_hand ? 90 : 180", "v.main_hand ? 60 : 0", "v.main_hand ? -40 : 180"], "scale": "v.main_hand ? 1.4 : 1.2"}
    tp = xyz("thirdperson_mainhand"); tp["fractal_item"] = {"position": [0, 13, -3], "rotation": [90, 0, 0]}
    head = {"fractal_item": {"position": [0, 19.9, 0]},
            "fractal_item_x": {"position": ["-v.thirdperson_head_pos_x*0.625", "v.thirdperson_head_pos_y*0.625", "v.thirdperson_head_pos_z*0.625"],
                               "rotation": ["-v.thirdperson_head_rot_x", 0, 0],
                               "scale": "(v.thirdperson_head_scale==1 ? 0.625 : v.thirdperson_head_scale*0.625)"},
            "fractal_item_y": {"rotation": [0, "-v.thirdperson_head_rot_y", 0]},
            "fractal_item_z": {"rotation": [0, 0, "v.thirdperson_head_rot_z"]}}
    return {"format_version": "1.8.0", "animations": {
        ANIM + ".firstperson_hand": {"loop": True, "bones": fp},
        ANIM + ".thirdperson_hand": {"loop": True, "bones": tp},
        ANIM + ".thirdperson_head": {"loop": True, "bones": head},
        ANIM + ".disable": {"loop": True, "override_previous_animation": True,
                            "bones": {"fractal_item": {"scale": 0}}}}}


def build(pack, items, texture_of, slug):
    os.makedirs(os.path.join(pack, "models", "entity", "fractal"), exist_ok=True)
    os.makedirs(os.path.join(pack, "attachables", "fractal"), exist_ok=True)
    os.makedirs(os.path.join(pack, "textures", "fractal", "models"), exist_ok=True)
    os.makedirs(os.path.join(pack, "animations"), exist_ok=True)
    count = 0
    for path, model, ids in items:
        ident = slug(path)
        geo, tex = geometry(ident, model, texture_of)
        json.dump(geo, open(os.path.join(pack, "models", "entity", "fractal", ident + ".geo.json"), "w"))
        tex.save(os.path.join(pack, "textures", "fractal", "models", ident + ".png"))
        for bid in ids:
            json.dump(attachable(bid, ident, model),
                      open(os.path.join(pack, "attachables", "fractal", bid.split(":", 1)[1] + ".json"), "w"))
            count += 1
    json.dump(animations(), open(os.path.join(pack, "animations", "fractal_item.animation.json"), "w"), indent=1)
    return count
