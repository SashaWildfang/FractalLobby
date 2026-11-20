"""
Lobby resource pack - built by Sasha Wildfang for Fractal Network.

The Collectibles join item (fractal:menu/collectibles), drawn like the Radio's music note join item: 16x16 with a
dark outline, a smooth lilac-to-purple-to-pink gradient across the shape, a bright edge on the left and a gold part.

Letters: O outline, S the gradient fill, H its bright edge, s its shaded edge, G gold (with its own gradient),
. empty.

    python join_item.py --options <preview.png>
    python join_item.py <FractalLobby folder> <option>
"""
import sys
from pathlib import Path

from PIL import Image

INK = (26, 27, 38)
TOP, BOTTOM, PINK = (226, 166, 250), (176, 98, 250), (246, 122, 232)
GOLD_TOP, GOLD_BOTTOM = (255, 226, 140), (226, 150, 40)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


# The chest is orange with purple metal (the music note's purple-pink, swapped round for some more colour).
ORANGE_BOTTOM, ORANGE_TOP = (214, 96, 24), (255, 184, 72)
METAL_TOP, METAL_BOTTOM = (206, 160, 255), (124, 66, 206)
SCHEME = {"chest": "orange"}


def colour(key, x, y, scheme="purple"):
    """Like the music note: dark at the bottom left shading lighter to the top right."""
    if key == "O":
        return INK
    if scheme == "orange":
        if key in ("G", "Y", "g"):
            metal = mix(METAL_TOP, METAL_BOTTOM, y / 15)
            return mix(metal, (255, 255, 255), 0.35) if key == "Y" else mix(metal, (70, 30, 130), 0.3) if key == "g" else metal
        base = mix(ORANGE_BOTTOM, ORANGE_TOP, ((15 - y) + x) / 30 * 1.2 - 0.05)
        if key == "H":
            return mix(base, (255, 232, 190), 0.55)
        if key == "s":
            return mix(base, (140, 52, 16), 0.4)
        return base
    if key == "G":
        return mix(GOLD_TOP, GOLD_BOTTOM, y / 15)
    if key == "Y":
        return (255, 238, 176)
    if key == "g":
        return GOLD_BOTTOM
    base = mix(BOTTOM, PINK, ((15 - y) + x) / 30 * 1.25 - 0.1)
    if key == "H":
        return mix(base, (240, 200, 255), 0.6)
    if key == "s":
        return mix(base, (96, 44, 160), 0.4)
    return base


OPTIONS = {
    "A: crate": [
        "................",
        "................",
        "..OOOOOOOOOOOO..",
        "..OGHSSSSSSsGO..",
        "..OHGSSSSSSGsO..",
        "..OHSGSSSSGSsO..",
        "..OHSSGSSGSSsO..",
        "..OHSSSGGSSSsO..",
        "..OHSSSGGSSSsO..",
        "..OHSSGSSGSSsO..",
        "..OHSGSSSSGSsO..",
        "..OHGSSSSSSGsO..",
        "..OGssssssssGO..",
        "..OOOOOOOOOOOO..",
        "................",
        "................",
    ],
    "B: chest": [
        "................",
        "................",
        ".OOOOOOOOOOOOOO.",
        ".OGHHHHHHHHHHGO.",
        ".OHSSSSSSSSSSsO.",
        ".OHSSSSSSSSSSsO.",
        ".OHSSSOOOOSSSsO.",
        ".OOOOOGYGGOOOOO.",
        ".OHSSOGOOGOSSsO.",
        ".OHSSOGggGOSSsO.",
        ".OHSSSOOOOSSSsO.",
        ".OHSSSSSSSSSSsO.",
        ".OGssssssssssGO.",
        ".OOOOOOOOOOOOOO.",
        "................",
        "................",
    ],
    "C: open chest": [
        "................",
        ".....G....G.....",
        "....GYG..GYG....",
        ".....G....G.....",
        "..OOOOOOOOOOOO..",
        "..OHHHHHHHHHsO..",
        "..OYYYYYYYYYYO..",
        "..OOOOGYGGOOOO..",
        "..OHSSGOOGSSsO..",
        "..OHSSGggGSSsO..",
        "..OHSSOOOOSSsO..",
        "..OssssssssssO..",
        "..OOOOOOOOOOOO..",
        "................",
        "................",
        "................",
    ],
}


def draw(rows, scheme="purple"):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, key in enumerate(row):
            if key != ".":
                img.putpixel((x, y), colour(key, x, y, scheme) + (255,))
    box = img.getbbox()
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    out.paste(img, ((16 - (box[2] - box[0])) // 2 - box[0], (16 - (box[3] - box[1])) // 2 - box[1]))
    return out


if __name__ == "__main__":
    if sys.argv[1] == "--options":
        reference = Image.open(Path(__file__).resolve().parent.parent / "FractalLobby/assets/fractal/textures/item/menu/radio.png").convert("RGBA")
        icons = [reference] + [draw(rows) for rows in OPTIONS.values()]
        scale, gap = 10, 16
        sheet = Image.new("RGBA", (len(icons) * (16 * scale + gap) + gap, 16 * scale + 2 * gap), (54, 48, 66, 255))
        for i, icon in enumerate(icons):
            sheet.alpha_composite(icon.resize((16 * scale, 16 * scale), Image.NEAREST), (gap + i * (16 * scale + gap), gap))
        sheet.save(sys.argv[2])
        print("the music note for reference, then " + ", ".join(OPTIONS))
    else:
        name, rows = next((k, v) for k, v in OPTIONS.items() if k.startswith(sys.argv[2]))
        scheme = "orange" if "chest" in name else "purple"
        draw(rows, scheme).save(Path(sys.argv[1]) / "assets/fractal/textures/item/menu/collectibles.png")
        print("Wrote fractal:menu/collectibles")
