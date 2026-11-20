"""
Lobby resource pack - built by Sasha Wildfang for Fractal Network.

The Collectibles main menu's section icons, drawn like the settings menu icons: 16x16 pixel art, a dark navy
outline, flat purple shades (light on the left, dark on the right) and gold accents. Each is centred in its square.
Chosen one at a time; sections not redone yet keep their earlier picture (category_icons.py).

    python section_icons.py <FractalLobby folder>              writes the chosen icons
    python section_icons.py --options <name> <preview.png>    previews the options for a section
"""
import json
import sys
from pathlib import Path

from PIL import Image

PALETTE = {
    "O": (26, 27, 38), "L": (196, 160, 255), "P": (150, 98, 230), "d": (104, 64, 176), "D": (72, 44, 124),
    "Y": (255, 232, 150), "G": (255, 196, 64), "g": (204, 140, 30), "W": (244, 244, 250), "w": (196, 196, 214),
}

# ---------------------------------------------------------------- chosen

HATS = [
    "................",
    "................",
    "....OOOOOOOO....",
    "...OLPPPPPPdO...",
    "...OLPPPPPPdO...",
    "...OLPPPPPPdO...",
    "...OLPPPPPPdO...",
    "...OLPPPPPPdO...",
    "...OYGGGGGGgO...",
    "...OYGGGGGGgO...",
    ".OOOLPPPPPPdOOO.",
    "OLLPPPPPPPPPPddO",
    "OdddddddddddddDO",
    ".OOOOOOOOOOOOOO.",
    "................",
    "................",
]

SUITS = [
    "................",
    "....OOO..OOO....",
    "..OOLPdOOLPdOO..",
    ".OLPPPPdYPPPPdO.",
    "OLPPPPPdYPPPPPdO",
    "OLPPOLPdYPPdOPdO",
    "ODddOLPdYPPdOddO",
    "OOOOOLPdYPPdOOOO",
    "....OLPdYPPdO...",
    "....OGGGGGGgO...",
    "....OLPdYPPdO...",
    "....OLPdYPPdO...",
    "....OddddddDO...",
    "....OOOOOOOOO...",
    "................",
    "................",
]

GADGETS = [
    "................",
    ".......OO.......",
    "......OGgO......",
    ".....OYGGgO.....",
    ".....OLPPdO.....",
    ".....OLWwdO.....",
    ".....OLwwdO.....",
    ".....OLPPdO.....",
    "....OGLPPdgO....",
    "...OGGLPPdggO...",
    "...OGGOOOOggO...",
    "...OOOOYGOOOO...",
    "......OYGO......",
    ".......OO.......",
    "................",
    "................",
]

CLOAKS = "outline:" + "|".join([
    "................",
    "................",
    "................",
    "................",
    "................",
    ".....YYYYYY.....",
    "...YG......Gg...",
    "..YG........gg..",
    "...gg......gg...",
    ".....gggggg.....",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
])

CHOSEN = {"hats": HATS, "suits": SUITS, "gadgets": GADGETS, "cloaks": CLOAKS}

# ---------------------------------------------------------------- options being looked at

OPTIONS = {}  # nothing being chosen right now


def outlined(rows):
    """Adds the dark outline round a drawing (every empty pixel next to a filled one)."""
    filled = {(x, y) for y, row in enumerate(rows) for x, key in enumerate(row) if key != "."}
    out = []
    for y, row in enumerate(rows):
        line = ""
        for x, key in enumerate(row):
            near = any((x + dx, y + dy) in filled for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            line += key if key != "." else "O" if near else "."
        out.append(line)
    return out


def draw(rows):
    if isinstance(rows, str) and rows.startswith("outline:"):
        rows = outlined(rows[len("outline:"):].split("|"))
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, key in enumerate(row):
            if key != ".":
                image.putpixel((x, y), PALETTE[key] + (255,))
    return centred(image)


def centred(img):
    box = img.getbbox()
    if box is None:
        return img
    x0, y0, x1, y1 = box
    dx = (16 - (x1 - x0)) // 2 - x0
    dy = (16 - (y1 - y0)) // 2 - y0
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (dx, dy))
    return out


def preview(icons, path):
    scale, gap = 10, 16
    sheet = Image.new("RGBA", (len(icons) * (16 * scale + gap) + gap, 16 * scale + 2 * gap), (54, 48, 66, 255))
    for i, icon in enumerate(icons):
        sheet.alpha_composite(icon.resize((16 * scale, 16 * scale), Image.NEAREST), (gap + i * (16 * scale + gap), gap))
    sheet.save(path)


def save(pack: Path, name: str, image):
    texture = pack / "assets" / "fractal" / "textures" / "item" / "category" / f"{name}.png"
    model = pack / "assets" / "fractal" / "models" / "item" / "category" / f"{name}.json"
    item = pack / "assets" / "fractal" / "items" / "category" / f"{name}.json"
    for path in (texture, model, item):
        path.parent.mkdir(parents=True, exist_ok=True)
    image.save(texture)
    model.write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"fractal:item/category/{name}"}}))
    item.write_text(json.dumps({"model": {"type": "minecraft:model", "model": f"fractal:item/category/{name}"}}))


if __name__ == "__main__":
    if sys.argv[1] == "--options":
        options = OPTIONS[sys.argv[2]]
        preview([draw(rows) for rows in options.values()], sys.argv[3])
        print(", ".join(options))
    else:
        for name, rows in CHOSEN.items():
            save(Path(sys.argv[1]), name, draw(rows))
        print("Drew " + ", ".join(f"fractal:category/{n}" for n in CHOSEN))
