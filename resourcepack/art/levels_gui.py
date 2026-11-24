"""The Levels menu background (U+F1C6): the LEVELS plaque, an open panel for the snake (rows 1-5) and wells for the
one row of buttons below it (scroll, back, multiplier, level, claim all, re-center, scroll).
Run from resourcepack/: python3 art/levels_gui.py <CrazySurvival menus folder>"""
import sys
src = open('make_gui.py').read()
src = src[:src.index("os.makedirs(f'{OUT}/assets/fractal/textures/gui'")]
sys.argv = ['make_gui.py', sys.argv[1], '/tmp/unused']
g = {}
exec(compile(src, 'make_gui.py', 'exec'), g)
img = g['background'](6, [], "LEVELS", cells=[45, 46, 48, 49, 50, 52, 53], dividers=(106,))
img.save('FractalLobby/assets/fractal/textures/gui/levels.png')
