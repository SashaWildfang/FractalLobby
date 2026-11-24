"""The realm selector's Capture the Flag icon (fractal:ctf): a red and a blue flag, outlined like survival.png."""
from PIL import Image
import sys

C = {'g': (250, 206, 72), 'G': (196, 140, 36), 'w': (150, 104, 56), 'W': (98, 66, 34),
     'h': (255, 118, 104), 'r': (222, 52, 52), 'R': (150, 26, 32),
     'c': (130, 182, 255), 'b': (58, 112, 232), 'B': (32, 58, 156)}
OUT = (24, 18, 20)
ART = [
    "................",
    "...g............",
    "...Ghhh.........",
    "...wrrrhhh......",
    "...wrrrrrr.g....",
    "...wrrrrrr.Gcc..",
    "...wrrrrrr.wbbcc",
    "...wRRRrrr.wbbbb",
    "...w...RRR.wbbbb",
    "...w.......wBBbb",
    "...w.......w..BB",
    "...W.......w....",
    "...w.......W....",
    "...w.......w....",
    "...W.......w....",
    "................",
]
img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
for y, row in enumerate(ART):
    for x, ch in enumerate(row):
        if ch in C:
            img.putpixel((x, y), C[ch] + (255,))
filled = {(x, y) for y in range(16) for x in range(16) if img.getpixel((x, y))[3]}
for y in range(16):
    for x in range(16):
        if (x, y) in filled:
            continue
        if any((x + dx, y + dy) in filled for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            img.putpixel((x, y), OUT + (255,))
img.save(sys.argv[1])
