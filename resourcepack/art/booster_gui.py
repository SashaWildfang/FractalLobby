"""Adds the Boosters menu background (U+F1C5, from the 5-row trails one with a BOOSTERS title) and the two
level/booster wells to the 4-row profile background. Run from resourcepack/: python3 art/booster_gui.py <menus>"""
import json, sys
from PIL import Image
src = open('make_gui.py').read()
src = src[:src.index("os.makedirs(f'{OUT}/assets/fractal/textures/gui'")]
sys.argv = ['make_gui.py', sys.argv[1], '/tmp/unused']
g = {}
exec(compile(src, 'make_gui.py', 'exec'), g)
GUI = 'FractalLobby/assets/fractal/textures/gui/'

img = Image.open(GUI + 'trails_5.png').convert('RGBA')
g['plaque_title'](img, 'BOOSTERS')
img.save(GUI + 'boosters.png')

profile = Image.open(GUI + 'profile_4.png').convert('RGBA')
for slot in (11, 15):
    r, c = divmod(slot, 9)
    g['cell'](profile, 8 + c * 18, 16 + 18 + r * 18)
profile.save(GUI + 'profile_4.png')

font = 'FractalLobby/assets/fractal/font/lobby.json'
d = json.load(open(font))
if not any(p.get('file') == 'fractal:gui/boosters.png' for p in d['providers']):
    d['providers'].append({"type": "bitmap", "file": "fractal:gui/boosters.png", "ascent": 29, "height": 256, "chars": [""]})
json.dump(d, open(font, 'w'), indent=1)
