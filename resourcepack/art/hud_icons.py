"""
Lobby resource pack - built by Sasha Wildfang for Fractal Network.

Little 9x9 icons for text (the sidebar, tab list and chat), the same height as the rank badges: one for Mystery
Dust and one for Mystery Boxes, in the pack's style (dark outline, flat shades, purple and gold).

    python hud_icons.py <FractalLobby folder>        writes the chosen ones (font fractal:lobby, U+F024 dust, U+F025 boxes)
    python hud_icons.py --options <preview.png>     the three options for each
"""
import sys

from PIL import Image

PALETTE = {
    "O": (26, 27, 38), "L": (196, 160, 255), "P": (150, 98, 230), "d": (104, 64, 176),
    "Y": (255, 236, 170), "G": (255, 196, 64), "g": (204, 140, 30), "W": (255, 255, 255),
}

DUST = {
    "A: pile with gold specks": [
        ".G.....Y.",
        "....Y....",
        "...OOO...",
        "..OYYGO..",
        ".OYYGGgO.",
        "OYYGGGggO",
        "OGGGGgggO",
        "OgggggggO",
        ".OOOOOOO.",
    ],
    "B: big pile": [
        ".........",
        "...OOO...",
        "..OYYGO..",
        ".OYYGGGO.",
        ".OYGGGgO.",
        "OYGGGGggO",
        "OGGGGGggO",
        "OgggggggO",
        ".OOOOOOO.",
    ],
    "C: pile with a gold glint": [
        "....G....",
        "...GYG...",
        "....G....",
        "...OOO...",
        "..OYGGO..",
        ".OYGGGgO.",
        "OYGGGGggO",
        "OgggggggO",
        ".OOOOOOO.",
    ],
}

BOXES = {
    "A: purple gift": [
        "..OO.OO..",
        ".OGGOGGO.",
        "OOOOOOOOO",
        "OLLLGLLdO",
        "OPPPGPPdO",
        "OPPPGPPdO",
        "OPPPGPPdO",
        "OdddgdddO",
        "OOOOOOOOO",
    ],
    "B: gold gift": [
        "..OO.OO..",
        ".OPPOPPO.",
        "OOOOOOOOO",
        "OYYYPYYgO",
        "OGGGPGGgO",
        "OGGGPGGgO",
        "OGGGPGGgO",
        "OggggdggO"[:9],
        "OOOOOOOOO",
    ],
    "C: big bow": [
        ".OO...OO.",
        "OGgO.OGgO",
        ".OGGOGGO.",
        "OOOOOOOOO",
        "OLLLGLLdO",
        "OPPPGPPdO",
        "OPPPGPPdO",
        "OdddgdddO",
        "OOOOOOOOO",
    ],
}


def draw(rows):
    assert len(rows) == 9 and all(len(r) == 9 for r in rows), [len(r) for r in rows]
    img = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, key in enumerate(row):
            if key != ".":
                img.putpixel((x, y), PALETTE[key] + (255,))
    return img


def preview(path):
    scale, gap = 14, 18
    rows = [list(DUST.values()), list(BOXES.values())]
    sheet = Image.new("RGBA", (3 * (9 * scale + gap) + gap, 2 * (9 * scale + gap) + gap), (54, 48, 66, 255))
    for r, options in enumerate(rows):
        for i, grid in enumerate(options):
            sheet.alpha_composite(draw(grid).resize((9 * scale, 9 * scale), Image.NEAREST),
                                  (gap + i * (9 * scale + gap), gap + r * (9 * scale + gap)))
    sheet.save(path)


CHOSEN = {"dust": ("C: pile with a gold glint", DUST, 0xF024), "boxes": ("B: gold gift", BOXES, 0xF025)}


def save(pack):
    import json
    from pathlib import Path
    root = Path(pack) / "assets" / "fractal"
    font = root / "font" / "lobby.json"
    data = json.loads(font.read_text(encoding="utf-8"))
    for name, (option, options, code) in CHOSEN.items():
        draw(options[option]).save(root / "textures" / "gui" / f"hud_{name}.png")
        file = f"fractal:gui/hud_{name}.png"
        data["providers"] = [p for p in data["providers"] if p.get("file") != file]
        data["providers"].append({"type": "bitmap", "file": file, "ascent": 8, "height": 9, "chars": [chr(code)]})
    font.write_text(json.dumps(data, indent=1), encoding="utf-8")
    print("Wrote the Dust (U+F024) and Boxes (U+F025) icons")


if __name__ == "__main__":
    if sys.argv[1] != "--options":
        save(sys.argv[1])
        sys.exit()
    preview(sys.argv[2])
    print("top row Dust: " + ", ".join(DUST) + "; bottom row Boxes: " + ", ".join(BOXES))
