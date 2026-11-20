"""
Lobby resource pack - built by Sasha Wildfang for Fractal Network.

Draws two 16x16 menu icons in the pack's flat pixel style (dark outline, a few shades):
  fractal:menu/locked   a padlock, shown in place of a cosmetic that isn't unlocked yet
  fractal:mystery/empty an empty open box, for "No boxes yet" in the Mystery Vault

    python status_icons.py <FractalLobby folder>
"""
import json
import sys
from pathlib import Path

from PIL import Image

OUTLINE = (26, 27, 38, 255)

LOCK = [
    "................",
    ".....OOOOOO.....",
    "....OSSSSSSO....",
    "...OSSOOOOSsO...",
    "...OSsO..OSsO...",
    "...OSsO..OSsO...",
    "...OSsO..OSsO...",
    ".OOOOOOOOOOOOOO.",
    ".OHHHHHHHHHHHbO.",
    ".OBBBBBKKBBBBbO.",
    ".OBBBBKKKKBBBbO.",
    ".OBBBBBKKBBBBbO.",
    ".OBBBBBKKBBBBbO.",
    ".ObbbbbbbbbbbdO.",
    ".OOOOOOOOOOOOOO.",
    "................",
]
LOCK_COLOURS = {
    "O": OUTLINE,
    "S": (169, 174, 200, 255), "s": (110, 112, 136, 255),
    "H": (196, 170, 240, 255), "B": (150, 118, 205, 255), "b": (104, 80, 156, 255), "d": (74, 56, 112, 255),
    "K": OUTLINE,
}

EMPTY_BOX = [
    "................",
    "................",
    "..OO........OO..",
    ".OFFO......OFfO.",
    ".OfFFO....OFFfO.",
    "..OfFFOOOOFFfO..",
    "..OOIIIIIIIIOO..",
    "..OIIiiiiiiIIO..",
    "..OOOOOOOOOOOO..",
    "..OHHHHHHHHHbO..",
    "..OBBBBBBBBBbO..",
    "..OBBBBBBBBBbO..",
    "..OBBBBBBBBBbO..",
    "..ObbbbbbbbbdO..",
    "..OOOOOOOOOOOO..",
    "................",
]
BOX_COLOURS = {
    "O": OUTLINE,
    "F": (184, 150, 236, 255), "f": (130, 98, 190, 255),
    "I": (40, 34, 56, 255), "i": (58, 50, 80, 255),
    "H": (200, 170, 245, 255), "B": (160, 120, 220, 255), "b": (118, 86, 176, 255), "d": (84, 60, 130, 255),
}


def draw(rows, colours):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), "icons are 16x16"
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, key in enumerate(row):
            if key != ".":
                image.putpixel((x, y), colours[key])
    return image


def save(pack: Path, name: str, image: Image.Image):
    texture = pack / "assets" / "fractal" / "textures" / "item" / f"{name}.png"
    model = pack / "assets" / "fractal" / "models" / "item" / f"{name}.json"
    item = pack / "assets" / "fractal" / "items" / f"{name}.json"
    for path in (texture, model, item):
        path.parent.mkdir(parents=True, exist_ok=True)
    image.save(texture)
    model.write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"fractal:item/{name}"}}))
    item.write_text(json.dumps({"model": {"type": "minecraft:model", "model": f"fractal:item/{name}"}}))


def main():
    pack = Path(sys.argv[1])
    save(pack, "menu/locked", draw(LOCK, LOCK_COLOURS))
    save(pack, "mystery/empty", draw(EMPTY_BOX, BOX_COLOURS))
    print("Drew fractal:menu/locked and fractal:mystery/empty")


if __name__ == "__main__":
    main()
