"""
Lobby resource pack - built by Sasha Wildfang for Fractal Network.

Designs for the Mystery Vault (fractal:mystery/vault_body and vault_lid): flat, clean colours with a crisp dark
outline, no speckles. All keep the same shape so the lid still swings open on its hinge: a 14x10x14 body with a
glowing inside, a 14x4x14 lid on top, and something on the lid.

  clean   the slate chest with purple bands, gold corners and a gold lock on the front, a plain lid
  banded  chunkier: gold corner brackets, a raised panel on the lid, a wide gold lock
  royal   a purple chest with dark bands, gold trim, a gold lock with a purple gem and a gold diamond on the lid

    python vault_models.py <FractalLobby folder> [design]          writes that design as the vault (default: flat)
    python vault_models.py --preview <preview.png>                  renders all three to compare
"""
import json
import shutil
import sys
import tempfile
from pathlib import Path

from PIL import Image

INK = (26, 27, 38)
SLATE, SLATE_L, SLATE_D = (58, 61, 86), (78, 82, 112), (40, 42, 60)
STEEL, STEEL_L, STEEL_D = (104, 110, 140), (140, 146, 176), (72, 76, 100)
PURPLE, PURPLE_L, PURPLE_D = (160, 96, 240), (196, 150, 255), (110, 62, 180)
GOLD, GOLD_L, GOLD_D = (255, 200, 90), (255, 232, 160), (200, 140, 40)
GLOW_IN, GLOW_OUT = (255, 240, 200), (178, 102, 255)


def canvas(fill):
    return Image.new("RGBA", (16, 16), fill + (255,))


def rect(img, x0, y0, x1, y1, colour):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            img.putpixel((x, y), colour + (255,))


def frame(img, y0=2):
    """A dark edge round a face (sides use rows 2-15: lid 2-5, body 6-15), and the seam under the lid."""
    rect(img, 0, y0, 15, y0, INK)
    rect(img, 0, 15, 15, 15, INK)
    rect(img, 0, y0, 0, 15, INK)
    rect(img, 15, y0, 15, 15, INK)


def inside():
    img = canvas(GLOW_OUT)
    for y in range(16):
        for x in range(16):
            d = min(1.0, ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5 / 10.6 * 1.3)
            img.putpixel((x, y), tuple(round(GLOW_IN[i] + (GLOW_OUT[i] - GLOW_IN[i]) * d) for i in range(3)) + (255,))
    return img


# ---------------------------------------------------------------- the designs (all chests, the favourite shape)
# Each returns (side, front, top, extras): plain sides, the front and back with the lock, the lid's top, and any
# extra boxes on the lid. Side and front textures use rows 2-5 for the lid and rows 6-15 for the body.

def lock(img, x0=6, gem=None):
    """A gold lock plate across the seam, outlined, with a keyhole (or a gem)."""
    rect(img, x0, 4, x0 + 3, 10, INK)
    rect(img, x0 + 1, 5, x0 + 2, 9, GOLD)
    rect(img, x0 + 1, 5, x0 + 1, 5, GOLD_L)
    rect(img, x0 + 2, 9, x0 + 2, 9, GOLD_D)
    if gem:
        rect(img, x0 + 1, 7, x0 + 2, 7, gem)
    else:
        rect(img, x0 + 1, 7, x0 + 2, 7, INK)
        img.putpixel((x0 + 1, 8), INK + (255,))


def bands(img, colour, shade, top=False):
    for x in (3, 12):
        rect(img, x, 0 if top else 2, x, 15, colour)
        rect(img, x + 1, 0 if top else 2, x + 1, 15, shade)


def banded_sides():
    """B's sides and front: slate with purple bands, gold L-brackets on every corner, a wide gold lock."""
    side, front = canvas(SLATE), canvas(SLATE)
    for img in (side, front):
        rect(img, 0, 2, 15, 5, SLATE_L)
        bands(img, PURPLE, PURPLE_D)
        frame(img)
        rect(img, 0, 6, 15, 6, INK)
        for (x, y, dx, dy) in ((1, 3, 1, 1), (14, 3, -1, 1), (1, 14, 1, -1), (14, 14, -1, -1)):
            rect(img, min(x, x + dx), y, max(x, x + dx), y, GOLD)
            rect(img, x, min(y, y + dy), x, max(y, y + dy), GOLD)
    rect(front, 5, 3, 10, 11, INK)
    rect(front, 6, 4, 9, 10, GOLD)
    rect(front, 6, 4, 9, 4, GOLD_L)
    rect(front, 6, 10, 9, 10, GOLD_D)
    rect(front, 7, 7, 8, 7, INK)
    front.putpixel((7, 8), INK + (255,))
    return side, front


def brackets(top):
    for (x, y, dx, dy) in ((1, 1, 1, 1), (14, 1, -1, 1), (1, 14, 1, -1), (14, 14, -1, -1)):
        rect(top, min(x, x + dx), y, max(x, x + dx), y, GOLD)
        rect(top, x, min(y, y + dy), x, max(y, y + dy), GOLD)


def flat():
    """B1: the banded chest with a plain flat lid: purple bands and gold corner brackets."""
    side, front = banded_sides()
    top = canvas(SLATE_L)
    bands(top, PURPLE, PURPLE_D, top=True)
    frame(top, 0)
    brackets(top)
    return side, front, top, []


def inset():
    """B2: flat lid with a darker inset panel framed in purple, like a lid plate, and gold corner brackets."""
    side, front = banded_sides()
    top = canvas(SLATE_L)
    rect(top, 4, 4, 11, 11, PURPLE_D)
    rect(top, 5, 5, 10, 10, SLATE)
    rect(top, 5, 5, 10, 5, SLATE_D)
    frame(top, 0)
    brackets(top)
    return side, front, top, []


def cross():
    """B3: flat lid with purple bands both ways (a cross) and gold corner brackets."""
    side, front = banded_sides()
    top = canvas(SLATE_L)
    bands(top, PURPLE, PURPLE_D, top=True)
    for y in (3, 12):
        rect(top, 0, y, 15, y, PURPLE)
        rect(top, 0, y + 1, 15, y + 1, PURPLE_D)
    frame(top, 0)
    brackets(top)
    return side, front, top, []


DESIGNS = {"flat": flat, "inset": inset, "cross": cross}


# ---------------------------------------------------------------- writing the models

def write(pack: Path, design: str):
    side, front, top, extras = DESIGNS[design]()
    tex = pack / "assets" / "fractal" / "textures" / "item" / "mystery"
    tex.mkdir(parents=True, exist_ok=True)
    side.save(tex / "vault_side.png")
    front.save(tex / "vault_front.png")
    canvas(SLATE_L).save(tex / "vault_slate_light.png")
    top.save(tex / "vault_top.png")
    inside().save(tex / "vault_inside.png")
    canvas(GOLD).save(tex / "vault_gold.png")
    canvas(GOLD_D).save(tex / "vault_gold_dark.png")
    textures = {"side": "fractal:item/mystery/vault_side", "front": "fractal:item/mystery/vault_front",
                "slate_light": "fractal:item/mystery/vault_slate_light", "top": "fractal:item/mystery/vault_top",
                "inside": "fractal:item/mystery/vault_inside", "gold": "fractal:item/mystery/vault_gold",
                "gold_dark": "fractal:item/mystery/vault_gold_dark", "particle": "fractal:item/mystery/vault_side"}

    def faces(uv_side, up, down_uv):
        f = {d: {"uv": uv_side, "texture": "#front" if d in ("north", "south") else "#side"} for d in ("north", "south", "east", "west")}
        f["up"] = {"uv": [0, 0, 16, 16], "texture": up}
        f["down"] = {"uv": down_uv, "texture": "#side"}
        return f

    body = {"from": [1, 0, 1], "to": [15, 10, 15], "shade": False, "faces": faces([0, 6, 16, 16], "#inside", [1, 7, 15, 14])}
    glow = {"from": [2, 9.6, 2], "to": [14, 10.1, 14], "shade": False, "light_emission": 15,
            "faces": {d: {"uv": [0, 0, 16, 16], "texture": "#inside"} for d in ("north", "south", "east", "west", "up", "down")}}
    lid = {"from": [1, 10, 1], "to": [15, 14, 15], "shade": False, "faces": faces([0, 2, 16, 6], "#top", [1, 7, 15, 14])}
    parts = [lid]
    for extra in extras:
        texture = "#" + extra["colour"]
        parts.append({"from": extra["from"], "to": extra["to"], "shade": False,
                      "faces": {d: {"uv": [0, 0, 16, 16], "texture": texture} for d in ("north", "south", "east", "west", "up", "down")}})
    display = {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
               "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
               "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]}}
    models = pack / "assets" / "fractal" / "models" / "item" / "mystery"
    items = pack / "assets" / "fractal" / "items" / "mystery"
    models.mkdir(parents=True, exist_ok=True)
    items.mkdir(parents=True, exist_ok=True)
    for name, elements in (("vault_body", [body, glow]), ("vault_lid", parts)):
        (models / f"{name}.json").write_text(json.dumps({"textures": textures, "elements": elements, "display": display}))
        (items / f"{name}.json").write_text(json.dumps({"model": {"type": "minecraft:model", "model": f"fractal:item/mystery/{name}"}}))
    # one model of the whole closed vault, for previews
    (models / "vault_preview.json").write_text(json.dumps({"textures": textures, "elements": [body] + parts}))


def preview(path):
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from preview3d import render
    shots = []
    for design in DESIGNS:
        root = Path(tempfile.mkdtemp())
        write(root, design)
        for yaw in (-35, 35):
            shots.append(render(str(root / "assets/fractal/models/item/mystery/vault_preview.json"),
                                str(root / "assets/fractal/textures"), size=256, yaw=yaw, pitch=25, zoom=1.15))
        shutil.rmtree(root, ignore_errors=True)
    sheet = Image.new("RGBA", (256 * 2 * len(DESIGNS) // 2, 256 * 2), (60, 63, 85, 255))
    sheet = Image.new("RGBA", (256 * len(DESIGNS), 512), (60, 63, 85, 255))
    for i in range(len(DESIGNS)):
        sheet.paste(shots[i * 2], (i * 256, 0))
        sheet.paste(shots[i * 2 + 1], (i * 256, 256))
    sheet.save(path)


if __name__ == "__main__":
    if sys.argv[1] == "--preview":
        preview(sys.argv[2])
        print("left to right: " + ", ".join(DESIGNS))
    else:
        design = sys.argv[2] if len(sys.argv) > 2 else "flat"   # the chosen one
        sys.argv[2:] = [design]
        write(Path(sys.argv[1]), design)
        (Path(sys.argv[1]) / "assets/fractal/models/item/mystery/vault_preview.json").unlink(missing_ok=True)
        print("Wrote the " + sys.argv[2] + " vault")
