"""Add the Survival server's Nexo glyphs on page U+A4xx to the Bedrock pack.

Nexo puts its own glyphs (the SURVIVAL title, the TOP and DEV tags, menu
backgrounds) at U+A40F..U+A416. Bedrock has no picture for them, so it draws
the Yi syllables that live there, which look like little stick figures.

Usage: python3 survival_glyphs.py NEXO_PACK_DIR BEDROCK_PACK_DIR
  NEXO_PACK_DIR    unzipped plugins/Nexo/pack/pack.zip from the Survival server
  BEDROCK_PACK_DIR the converted pack folder (the one holding font/)

Like the other glyph pages, each glyph sits at its Java pixel size, left-aligned
and vertically centred in a 256 px cell of a 4096x4096 sheet. Glyphs that are
256 px tall in Java are menu backgrounds: they are left blank so menu titles
show nothing instead of a stick figure.
"""
import glob
import json
import os
import sys

from PIL import Image

PAGE = 0xA4


def main(nexo, pack):
    sheet = Image.new("RGBA", (4096, 4096), (0, 0, 0, 0))
    done = []
    for font in glob.glob(os.path.join(nexo, "assets", "*", "font", "*.json")):
        for p in json.load(open(font, encoding="utf-8")).get("providers", []):
            if p.get("type") != "bitmap":
                continue
            for row, line in enumerate(p.get("chars", [])):
                for col, ch in enumerate(line):
                    cp = ord(ch)
                    if cp >> 8 != PAGE or cp == 0:
                        continue
                    done.append(cp)
                    height = p.get("height", 8)
                    if height >= 256:
                        continue  # menu background: leave the cell empty
                    path = p["file"].split(":")[-1]
                    hits = glob.glob(os.path.join(nexo, "assets", "*", "textures", "**", os.path.basename(path)),
                                     recursive=True)
                    if not hits:
                        print("missing texture for U+%04X: %s" % (cp, p["file"]))
                        continue
                    img = Image.open(hits[0]).convert("RGBA")
                    rows, cols = len(p["chars"]), len(line)
                    cw, chh = img.width // cols, img.height // rows
                    g = img.crop((col * cw, row * chh, (col + 1) * cw, (row + 1) * chh))
                    scale = height / chh
                    g = g.resize((max(1, round(g.width * scale)), max(1, round(g.height * scale))), Image.NEAREST)
                    bbox = g.getbbox()
                    if bbox:
                        g = g.crop(bbox)  # trim empty space so the glyph sits centred
                    g = g.crop((0, 0, min(256, g.width), min(256, g.height)))
                    i = cp & 0xFF
                    sheet.paste(g, ((i % 16) * 256, (i // 16) * 256 + (256 - g.height) // 2))
    os.makedirs(os.path.join(pack, "font"), exist_ok=True)
    out = os.path.join(pack, "font", "glyph_%02X.png" % PAGE)
    sheet.save(out, optimize=True)
    print("wrote %s with %d glyphs: %s" % (out, len(done), " ".join("U+%04X" % c for c in sorted(done))))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
