"""The Levels menu background (U+F1C6): the LEVELS plaque, an open panel for the snake (rows 1-4) and wells for the
buttons below it (row 5: multiplier, level, next box; row 6: scroll, back, re-center, claim all, scroll).
Run from resourcepack/: python3 art/levels_gui.py <CrazySurvival menus folder>"""
import sys
src = open('make_gui.py').read()
src = src[:src.index("os.makedirs(f'{OUT}/assets/fractal/textures/gui'")]
sys.argv = ['make_gui.py', sys.argv[1], '/tmp/unused']
g = {}
exec(compile(src, 'make_gui.py', 'exec'), g)
img = g['background'](6, [], "LEVELS", cells=[38, 40, 42, 45, 47, 49, 51, 53], dividers=(88,))
img.save('FractalLobby/assets/fractal/textures/gui/levels.png')
