"""
Lobby resource pack - built by Sasha Wildfang for Fractal Network.

Draws the 16x16 padlock menu icon in the pack's flat pixel style (dark outline, a few shades):
  fractal:menu/locked   shown in place of a cosmetic that isn't unlocked yet, and for box crafting you can't do
(The Mystery Vault's icons, "No boxes yet" included, are in mystery_icons.py.)

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
    print("Drew fractal:menu/locked")


if __name__ == "__main__":
    main()
