from PIL import Image, ImageDraw
import json, os, zipfile, sys
W = 176
OUT = '/tmp/gui/pack'
def hexc(h, a=255): h = h.lstrip('#'); return (int(h[0:2],16), int(h[2:4],16), int(h[4:6],16), a)
BG = hexc('#100A1C'); PANEL = hexc('#160E27'); TITLE = hexc('#1B1030')
CELL = hexc('#0B0713'); CELL_HI = hexc('#2A1B45'); CELL_LO = hexc('#06040B')
G1, G2 = hexc('#8E2DE2'), hexc('#E58BFF')
def lerp(a, b, t): return tuple(round(a[i] + (b[i]-a[i])*t) for i in range(4))

def gui(rows):
    top = 17 + rows * 18          # where the player inventory part starts
    H = top + 96                  # whole container
    img = Image.new('RGBA', (W, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # body with rounded corners
    d.rounded_rectangle([0, 0, W-1, H-1], radius=5, fill=BG)
    # gradient frame (2px), purple → pink left to right, following the rounded corners
    alpha = img.getchannel('A')
    inner = Image.new('L', img.size, 0)
    ImageDraw.Draw(inner).rounded_rectangle([2, 2, W-3, H-3], radius=3, fill=255)
    for x in range(W):
        c = lerp(G1, G2, x/(W-1))
        for y in range(H):
            if alpha.getpixel((x, y)) and not inner.getpixel((x, y)): img.putpixel((x, y), c)
    # title bar and divider under it
    d.rectangle([3, 3, W-4, 14], fill=TITLE)
    for x in range(3, W-3): img.putpixel((x, 15), lerp(G1, G2, x/(W-1))[:3] + (160,))
    # chest panel
    d.rectangle([5, 16, W-6, top+1], fill=PANEL)
    def cell(x, y, fill=CELL):
        # x,y = item position (16x16); frame sits 1px outside like a vanilla slot
        d.rectangle([x-1, y-1, x+16, y+16], fill=fill)
        d.line([x-1, y-1, x+16, y-1], fill=CELL_LO); d.line([x-1, y-1, x-1, y+16], fill=CELL_LO)
        d.line([x-1, y+16, x+16, y+16], fill=CELL_HI); d.line([x+16, y-1, x+16, y+16], fill=CELL_HI)
    for r in range(rows):
        for c in range(9): cell(8 + c*18, 18 + r*18)
    # player inventory (3 rows) and hotbar
    inv = top + 14
    for r in range(3):
        for c in range(9): cell(8 + c*18, inv + r*18)
    for c in range(9): cell(8 + c*18, inv + 58)
    return img, H

os.makedirs(f'{OUT}/assets/fractal/textures/gui', exist_ok=True)
os.makedirs(f'{OUT}/assets/fractal/font', exist_ok=True)
providers = [{"type": "space", "advances": {"": -8, "": -169}}]
for rows in range(1, 7):
    img, H = gui(rows)
    img.save(f'{OUT}/assets/fractal/textures/gui/realms_{rows}.png')
    providers.append({"type": "bitmap", "file": f"fractal:gui/realms_{rows}.png", "ascent": 13, "height": 256,
                      "chars": [chr(0xf100 + rows)]})
json.dump({"providers": providers}, open(f'{OUT}/assets/fractal/font/lobby.json', 'w'), indent=2)
json.dump({"pack": {"pack_format": 75, "supported_formats": [34, 99], "description": "Fractal Lobby menus"}},
          open(f'{OUT}/pack.mcmeta', 'w'), indent=2)
with zipfile.ZipFile('/tmp/gui/FractalLobbyGUI.zip', 'w', zipfile.ZIP_DEFLATED) as z:
    for root, _, files in os.walk(OUT):
        for f in files:
            p = os.path.join(root, f); z.write(p, os.path.relpath(p, OUT))
print('ok')
