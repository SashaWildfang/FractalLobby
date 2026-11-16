"""The lobby NPC skins (Survival wanderer, Prison miner, Capture the Flag knight), drawn here and shipped in the pack."""
import sys
from skinlib import Skin, preview, faces, hexc, shade
OUT = sys.argv[1] if len(sys.argv) > 1 else '.'

SKIN, SKIN_D = '#E2B08F', '#C99677'

# ---------------------------------------------------------------- Survival: a hooded wanderer in Fractal purple
s = Skin(11)
s.fill('head', SKIN)
s.rows('head', 'front', ["HHHHHHHH", "HHHHHHHH", "HhSSSShH", "SSSSSSSS", "SWIssIWS", "SSSSSSSS", "SSSmmSSS", "SSSSSSSS"],
       {'H': '#4A2F22', 'h': '#5C3B2A', 'S': SKIN, 's': SKIN_D, 'W': '#F4F1FA', 'I': '#8E2DE2', 'm': '#A86A5A'})
for side in ('left', 'right', 'back', 'top'): s.fill('head', '#4A2F22', [side])
s.rows('head', 'left', ["HHHHHHHH", "HHHHHHHH", "HHHHHSSS", "HHHHSSSS", "HHHHSSSS", "HHHSSSSS", "HHHSSSSS", "HHSSSSSS"],
       {'H': '#4A2F22', 'S': SKIN})
s.rows('head', 'right', ["HHHHHHHH", "HHHHHHHH", "SSSHHHHH", "SSSSHHHH", "SSSSHHHH", "SSSSSHHH", "SSSSSHHH", "SSSSSSHH"],
       {'H': '#4A2F22', 'S': SKIN})
s.fill('hat', '#6A3FB5')
s.rows('hat', 'front', ["PPPPPPPP", "PGGGGGGP", "PG____GP", "P______P", "P______P", "P______P", "________", "________"],
       {'P': '#6A3FB5', 'G': '#E8C15A'})
s.rows('hat', 'left', ["PPPPPPPP"] * 6 + ["PPPPPP__", "PPPPP___"], {'P': '#6A3FB5'})
s.rows('hat', 'right', ["PPPPPPPP"] * 6 + ["__PPPPPP", "___PPPPP"], {'P': '#6A3FB5'})
s.rows('hat', 'bottom', ["________"] * 8, {})
s.fill('body', '#3B2763')
s.rows('body', 'front', ["SSSSSSSS", "SgTTTTgS", "TTTTTTBT", "TTTTTBTT", "TTTTBTTT", "TTTBTTTT", "TTBTTTTT", "LLLLLLLL", "LLLGGLLL",
                         "TTTTTTTT", "TTTTTTTT", "TTTTTTTT"],
       {'S': '#5FB24F', 'g': '#E8C15A', 'T': '#3B2763', 'B': '#7A4B2A', 'L': '#2A1B12', 'G': '#E8C15A'})
s.fill('jacket', '#6A3FB5', ['back', 'left', 'right', 'top', 'bottom'])
s.rows('jacket', 'front', ["Pg____gP"] + ["P______P"] * 11, {'P': '#6A3FB5', 'g': '#E8C15A'})
s.rows('jacket', 'back', ["PPPPPPPP", "PPPPPPPP", "PPPGGPPP", "PPGPPGPP", "PPGPPGPP", "PPPGGPPP", "PPPPPPPP", "PPPPPPPP",
                          "PPPPPPPP", "PPPPPPPP", "PpPpPpPp", "pPpPpPpP"], {'P': '#6A3FB5', 'G': '#E8C15A', 'p': '#4E2C8C'})
s.rows('jacket', 'bottom', ["________"] * 4, {})
for arm, sleeve in (('rarm', 'rsleeve'), ('larm', 'lsleeve')):
    s.fill(arm, '#3B2763')
    s.band(arm, 9, 10, '#E8C15A')
    s.band(arm, 10, 12, SKIN)
    s.rows(arm, 'bottom', ["SSSS"] * 4, {'S': SKIN})
    s.fill(sleeve, '#6A3FB5', ['top', 'left', 'right', 'front', 'back'])
    s.band(sleeve, 6, 12, '#000000', noise=0)
    for name in ('front', 'back', 'left', 'right'):
        x0, y0, w, h = faces(sleeve)[name]
        for y in range(6, 12):
            for x in range(w): s.img.putpixel((x0 + x, y0 + y), (0, 0, 0, 0))
for leg in ('rleg', 'lleg'):
    s.fill(leg, '#5A3D2B')
    s.band(leg, 8, 12, '#2B1D16')
    s.band(leg, 8, 9, '#E8C15A', ('front',))
    s.rows(leg, 'bottom', ["BBBB"] * 4, {'B': '#21160F'})
for pants in ('rpants', 'lpants'): s.clear(pants)
s.save(f'{OUT}/survival.png')
preview(s.img).save(f'{OUT}/preview_survival.png')

# ---------------------------------------------------------------- Prison: a miner in a hi-vis jumpsuit with a lamp helmet
p = Skin(23)
FACE = '#C98B63'
p.fill('head', FACE)
p.rows('head', 'front', ["HHHHHHHH", "HHHHHHHH", "SSSSSSSS", "SdSSSSdS", "SWEssEWS", "SSSSSSSS", "SccmmccS", "SccccccS"],
       {'H': '#2E2622', 'S': FACE, 's': '#B27652', 'd': '#3A2A22', 'W': '#F2F2F2', 'E': '#4B8BD9', 'm': '#8C4A3A', 'c': '#5A4A40'})
for side in ('left', 'right', 'back', 'top'): p.fill('head', '#2E2622', [side])
p.rows('head', 'left', ["HHHHHHHH", "HHHHHHHH", "HHHHHSSS", "HHHHSSSS", "HHHHSSSS", "HHHSSSSS", "HHccSSSS", "HHccccSS"],
       {'H': '#2E2622', 'S': FACE, 'c': '#5A4A40'})
p.rows('head', 'right', ["HHHHHHHH", "HHHHHHHH", "SSSHHHHH", "SSSSHHHH", "SSSSHHHH", "SSSSSHHH", "SSSSccHH", "SSccccHH"],
       {'H': '#2E2622', 'S': FACE, 'c': '#5A4A40'})
p.clear('hat')
p.fill('hat', '#F2C230', ['top'])
p.rows('hat', 'front', ["YYYLLYYY", "YYYLLYYY", "RRRRRRRR", "........", "........", "........", "........", "........"],
       {'Y': '#F2C230', 'L': '#FFF6C8', 'R': '#C99A1E'})
for side in ('left', 'right', 'back'):
    p.rows('hat', side, ["YYYYYYYY", "YYYYYYYY", "RRRRRRRR"] + ["........"] * 5, {'Y': '#F2C230', 'R': '#C99A1E'})
p.rows('hat', 'bottom', ["........"] * 8, {})
SUIT, SUIT_D, HIVIS = '#E8792B', '#C9611D', '#E9E9E0'
p.fill('body', SUIT)
p.rows('body', 'front', ["CCCzzCCC", "OOOzOOOO", "OppzOOPO", "OppzOPPP", "OOOzOOPO", "OOOzOOOO", "VVVVVVVV", "OOOzOOOO",
                         "GGGGGGGG", "GGGbbGGG", "OOOzOOOO", "OOOOOOOO"],
       {'C': SUIT_D, 'O': SUIT, 'z': SUIT_D, 'p': '#C9611D', 'P': '#8E2DE2', 'V': HIVIS, 'G': '#5B5B5B', 'b': '#C9C9C9'})
p.rows('body', 'back', ["OOOOOOOO", "OOOOOOOO", "OOOOOOOO", "OOFXXFOO", "OOOOOOOO", "OOOOOOOO", "VVVVVVVV", "OOOOOOOO",
                        "GGGGGGGG", "GGGGGGGG", "OOOOOOOO", "OOOOOOOO"],
       {'O': shade(hexc(SUIT), .9), 'F': '#2A2A2A', 'X': '#2A2A2A', 'V': HIVIS, 'G': '#5B5B5B'})
p.clear('jacket')
for arm, sleeve in (('rarm', 'rsleeve'), ('larm', 'lsleeve')):
    p.fill(arm, SUIT)
    p.band(arm, 6, 7, HIVIS)
    p.band(arm, 9, 12, '#8A8A8A')
    p.rows(arm, 'bottom', ["GGGG"] * 4, {'G': '#7A7A7A'})
    p.clear(sleeve)
for leg, pants in (('rleg', 'rpants'), ('lleg', 'lpants')):
    p.fill(leg, SUIT)
    p.band(leg, 5, 6, HIVIS)
    p.band(leg, 9, 12, '#262626')
    p.band(leg, 9, 10, '#4A4A4A', ('front',))
    p.rows(leg, 'bottom', ["BBBB"] * 4, {'B': '#1C1C1C'})
    p.clear(pants)
p.save(f'{OUT}/prison.png')
preview(p.img).save(f'{OUT}/preview_prison.png')

# ---------------------------------------------------------------- Capture the Flag: a knight in a red and blue tabard
k = Skin(37)
STEEL, STEEL_D, STEEL_L = '#A7AEC2', '#7D8399', '#D6DBE8'
RED, BLUE, GOLD = '#E04848', '#4A78E0', '#F2C94C'
k.fill('head', '#E0B08A')
k.rows('head', 'front', ["HHHHHHHH", "HHHHHHHH", "SSSSSSSS", "SSSSSSSS", "SWBSSBWS", "SSSSSSSS", "SSSmmSSS", "SSSSSSSS"],
       {'H': '#3A2A1E', 'S': '#E0B08A', 'W': '#F4F4F4', 'B': '#3060C0', 'm': '#A06A5A'})
k.fill('hat', STEEL)
k.rows('hat', 'front', ["LLLLLLLL", "SSSSSSSS", "SSSSSSSS", "SDDDDDDS", "S______S", "SSSSSSSS", "S_SSSS_S", "S_S__S_S"],
       {'L': STEEL_L, 'S': STEEL, 'D': '#2A2D38'})
k.rows('hat', 'top', ["..RRBB..", "..RRBB..", "..RRBB..", "..RRBB..", "..RRBB..", "..RRBB..", "..RRBB..", "..RRBB.."],
       {'R': RED, 'B': BLUE})
k.rows('hat', 'bottom', ["________"] * 8, {})
k.fill('body', STEEL)
k.rows('body', 'front', ["LLLLLLLL", "SRRRBBBS", "SRRRBBBS", "SRRGGBBS", "SRGGGGBS", "SRRGGBBS", "SRRRBBBS", "dddddddd",
                         "SRRRBBBS", "SRRRBBBS", "SRRRBBBS", ".RRRBBB."],
       {'L': STEEL_L, 'S': STEEL, 'R': RED, 'B': BLUE, 'G': GOLD, 'd': '#4A3020'})
k.rows('body', 'back', ["SSSSSSSS", "SBBBRRRS", "SBBBRRRS", "SBBBRRRS", "SBBBRRRS", "SBBBRRRS", "SBBBRRRS", "dddddddd",
                        "SBBBRRRS", "SBBBRRRS", "SBBBRRRS", "SBBBRRRS"],
       {'S': STEEL_D, 'R': RED, 'B': BLUE, 'd': '#4A3020'})
k.clear('jacket')
for arm, sleeve, team in (('rarm', 'rsleeve', RED), ('larm', 'lsleeve', BLUE)):
    k.fill(arm, STEEL)
    k.band(arm, 0, 3, STEEL_L)
    k.band(arm, 3, 4, team)
    k.band(arm, 10, 12, STEEL_D)
    k.rows(arm, 'bottom', ["SSSS"] * 4, {'S': STEEL_D})
    k.clear(sleeve)
for leg, pants in (('rleg', 'rpants'), ('lleg', 'lpants')):
    k.fill(leg, STEEL)
    k.band(leg, 4, 5, STEEL_L)
    k.band(leg, 9, 12, '#3A3E4C')
    k.rows(leg, 'bottom', ["BBBB"] * 4, {'B': '#2A2D38'})
    k.clear(pants)
k.save(f'{OUT}/ctf.png')
preview(k.img).save(f'{OUT}/preview_ctf.png')

# ---------------------------------------------------------------- the Fractal cape all three wear
from PIL import Image
cape = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
def cfill(x0, y0, w, h, top, bottom, edge=None):
    for y in range(h):
        t = y / max(1, h - 1)
        c = tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        for x in range(w):
            cc = edge if edge and (x in (0, w - 1) or y == h - 1) else c
            cape.putpixel((x0 + x, y0 + y), cc + (255,))
PURPLE, DEEP, GOLDC = hexc('#7A45D0'), hexc('#3B1F78'), hexc('#E8C15A')
cfill(1, 1, 10, 16, PURPLE, DEEP, GOLDC)        # back (what others see)
cfill(12, 1, 10, 16, hexc('#2A1656'), hexc('#1E0F40'))  # inside
cfill(0, 1, 1, 16, DEEP, DEEP); cfill(11, 1, 1, 16, DEEP, DEEP)  # sides
cfill(1, 0, 10, 1, PURPLE, PURPLE); cfill(11, 0, 10, 1, DEEP, DEEP)  # top and bottom edges
for x, y in ((5, 5), (4, 6), (6, 6), (3, 7), (7, 7), (4, 8), (6, 8), (5, 9), (5, 7)):  # a gold diamond (the Fractal mark)
    cape.putpixel((1 + x, 1 + y), GOLDC + (255,))
for x in range(1, 11, 2): cape.putpixel((x, 16), hexc('#B98F2E') + (255,))  # fringe
cape.save(f'{OUT}/cape.png')
