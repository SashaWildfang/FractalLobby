"""Lobby menu backgrounds for Bedrock, added to the Survival addon's pack (CrazySetup.mcpack).

On Java a Lobby menu's title is a background picture from the fractal:lobby font (U+F001, the background's
character, U+F002). Bedrock can't draw a picture that big in a title, so the CrazyStudios Bedrock addon
replaces Bedrock's chest screen: when the title contains one of its characters, it shows that menu's picture
over the chest (ui/chest_screen.json, with the characters in ui/_global_variables.json). This adds every
Lobby background to that same system, so Survival's and the Lobby's menus both keep working.

Usage: python3 menus.py ADDON_PACK_DIR LOBBY_JAVA_PACK_DIR OUT.mcpack
  ADDON_PACK_DIR      CrazySetup.mcpack unzipped (it is changed in place, then zipped to OUT)
  LOBBY_JAVA_PACK_DIR the Java pack (resourcepack/FractalLobby)

The pack's version goes up by one so Bedrock players download it again. Run it again after updating the
addon (the addon's update replaces CrazySetup.mcpack).
"""
import json
import os
import re
import sys
import zipfile

from PIL import Image

PREFIX = "fl_"  # Lobby names in the addon's lists start with this; reruns remove the old ones first


def load(path):
    text = open(path, encoding="utf-8").read()
    return json.loads(re.sub(r"^\s*//.*$", "", text, flags=re.M))


def main(addon, java, out):
    ui = os.path.join(addon, "ui")
    gvars = load(os.path.join(ui, "_global_variables.json"))
    chest = load(os.path.join(ui, "chest_screen.json"))
    small = chest["cz_small_chest_guis@chest.chest_label"]["controls"]
    large = chest["cz_large_chest_guis@chest.chest_label"]["controls"]

    # A rerun replaces what an earlier run added.
    for k in [k for k in gvars if k.startswith("$cz_unicode_" + PREFIX)]:
        del gvars[k]
    for n in range(1, 7):
        key = "$cz_%drow_chest_guis" % n
        if key in gvars:
            gvars[key] = re.sub(r" or \$ctitle / \$cz_unicode_" + PREFIX + r"\w+", "", gvars[key])
    small[:] = [c for c in small if not next(iter(c)).startswith(PREFIX)]
    large[:] = [c for c in large if not next(iter(c)).startswith(PREFIX)]

    font = json.load(open(os.path.join(java, "assets", "fractal", "font", "lobby.json"), encoding="utf-8"))
    tex_dir = os.path.join(addon, "textures", "gui", "fractal")
    os.makedirs(tex_dir, exist_ok=True)
    added, skipped = [], []
    for p in font["providers"]:
        f = p.get("file", "")
        if p.get("type") != "bitmap" or not f.startswith("fractal:gui/") or p.get("height") != 256:
            continue
        chars = [c for c in "".join(p.get("chars", [])) if c != "\0"]
        if len(chars) != 1:
            continue
        stem = os.path.splitext(f.split("/", 1)[1])[0]
        img = Image.open(os.path.join(java, "assets", "fractal", "textures", "gui", stem + ".png")).convert("RGBA")
        box = img.getbbox()
        if not box:
            continue
        rows = round((box[3] - 45) / 18)  # the picture is the title tab (27 px), 18 px per row and the bottom edge
        if rows < 3 or rows > 6:
            skipped.append("%s (%d rows)" % (stem, rows))
            continue
        name = PREFIX + stem
        # Like the addon's pictures: 256x256, the menu at the top left.
        sheet = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
        sheet.paste(img.crop((0, 0, min(256, img.width), 256)), (0, 0))
        sheet.save(os.path.join(tex_dir, stem + ".png"), optimize=True)
        gvars["$cz_unicode_" + name] = chars[0]
        key = "$cz_%drow_chest_guis" % rows
        gvars[key] = gvars[key][:-1] + " or $ctitle / $cz_unicode_" + name + ")"
        # Java draws the picture 29 px above the title line: 5 px more than the addon's own pictures (ascent 13,
        # offset 18) in a big chest, 4 px in a small one (its ascent-29 Dungeons picture uses 33).
        control = {"$unicode": "$cz_unicode_" + name, "offset": [41.9, "-3px-%dpx" % (33 if rows == 3 else 34)],
                   "texture": "textures/gui/fractal/" + stem}
        (small if rows == 3 else large).append({name + "@chest.cz_gui_image": control})
        added.append("%s (U+%04X, %d rows)" % (stem, ord(chars[0]), rows))

    json.dump(gvars, open(os.path.join(ui, "_global_variables.json"), "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    json.dump(chest, open(os.path.join(ui, "chest_screen.json"), "w", encoding="utf-8"), indent=1, ensure_ascii=False)

    manifest_path = os.path.join(addon, "manifest.json")
    manifest = json.load(open(manifest_path, encoding="utf-8"))
    version = manifest["header"]["version"]
    version[2] += 1
    for m in manifest["modules"]:
        m["version"] = list(version)
    json.dump(manifest, open(manifest_path, "w", encoding="utf-8"), indent=2)

    if os.path.exists(out):
        os.remove(out)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for root, _, files in os.walk(addon):
            for fn in files:
                full = os.path.join(root, fn)
                z.write(full, os.path.relpath(full, addon))
    print("added %d menus: %s" % (len(added), ", ".join(added)))
    if skipped:
        print("skipped (Bedrock shows 1-2 row menus as a 3-row chest): " + ", ".join(skipped))
    print("pack version %s -> %s" % (".".join(map(str, version)), out))


if __name__ == "__main__":
    main(*sys.argv[1:4])
