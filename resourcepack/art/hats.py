"""
The 3D hats (fractal:hat/<name>), built from coloured boxes.

Coordinates are in the wearer's head pixels: x and z from -4 to 4 across the head (-z is the face), y from the top
of the head (0) up; negative y goes down over the head. Each box has one colour; its top is drawn a little lighter
and its underside darker, so the shapes read. Every hat gets its own palette texture (2x2 cells per shade).
"""
import json, os, zlib
from PIL import Image

U = 1.6           # model units per head pixel (a worn item is drawn at 0.625 of a block)
TOP = 14.4        # model y of the top of the head


def hexc(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def shade(c, f):
    return tuple(max(0, min(255, round(v * f))) for v in c)


def mirror(boxes):
    """The same boxes on the other side (x flipped)."""
    return [(-b[3], b[1], b[2], -b[0], b[4], b[5], b[6]) for b in boxes]


B, BLACK, WHITE, GOLD, PURPLE = '#1c1c24', '#1c1c24', '#f5f5f5', '#ffd27f', '#b266ff'

HATS = {
    'top_hat': [(-5.5, -0.2, -5.5, 5.5, 0.6, 5.5, '#1c1c24'), (-3.5, 0.6, -3.5, 3.5, 6, 3.5, '#24242e'),
                (-3.75, 0.6, -3.75, 3.75, 1.6, 3.75, PURPLE)],
    'beanie': [(-4.6, -2.6, -4.6, 4.6, -0.6, 4.6, '#2e8c85'), (-4.4, -0.6, -4.4, 4.4, 1.8, 4.4, '#3fb8af'),
               (-4.45, 0.2, -4.45, 4.45, 0.9, 4.45, '#f5f5f5'), (-3.7, 1.8, -3.7, 3.7, 2.8, 3.7, '#3fb8af'),
               (-2.6, 2.8, -2.6, 2.6, 3.4, 2.6, '#3fb8af'), (-1.5, 3.4, -1.5, 1.5, 5.8, 1.5, '#fafafa'),
               (-1.9, 3.8, -1.0, 1.9, 5.4, 1.0, '#fafafa'), (-1.0, 3.8, -1.9, 1.0, 5.4, 1.9, '#fafafa')],
    'chef_hat': [(-4.3, -0.6, -4.3, 4.3, 1.6, 4.3, '#ffffff'), (-4.9, 1.6, -4.9, 4.9, 4.6, 4.9, '#f0f0f0'),
                 (-3.8, 4.6, -3.8, 3.8, 5.8, 3.8, '#fafafa')],
    'headphones': [(-4.7, 0, -0.8, 4.7, 1, 0.8, '#2a2a36'), (-5.3, -5, -0.8, -4.3, 0, 0.8, '#2a2a36'),
                   (4.3, -5, -0.8, 5.3, 0, 0.8, '#2a2a36'), (-5.8, -5.6, -2, -4.4, -2.4, 2, PURPLE),
                   (4.4, -5.6, -2, 5.8, -2.4, 2, PURPLE), (-6, -5, -1.2, -5.7, -3, 1.2, '#ff7be5'),
                   (5.7, -5, -1.2, 6, -3, 1.2, '#ff7be5')],
    'cowboy_hat': [(-6.5, -0.2, -5.5, 6.5, 0.6, 5.5, '#8b5a2b'), (-6.5, 0.6, -5.5, -5.5, 1.8, 5.5, '#8b5a2b'),
                   (5.5, 0.6, -5.5, 6.5, 1.8, 5.5, '#8b5a2b'), (-3.5, 0.6, -3, 3.5, 4.2, 3, '#7a4a22'),
                   (-3.6, 0.6, -3.1, 3.6, 1.4, 3.1, '#3a2410'), (-1.2, 3.8, -3.1, 1.2, 4.2, 3.1, '#6a3e1c')],
    'wizard_hat': [(-6.4, -0.2, -6.4, 6.4, 0.5, 6.4, '#262a6e'), (-4.1, 0.5, -4.1, 4.1, 1.4, 4.1, GOLD),
                   (-4, 1.4, -4, 4, 2.8, 4, '#353a94'), (-3.1, 2.8, -2.8, 3.5, 4.4, 3.6, '#353a94'),
                   (-2.1, 4.4, -1.4, 3.1, 5.9, 3.5, '#3d43a8'), (-1.0, 5.9, -0.1, 2.9, 7.2, 3.4, '#3d43a8'),
                   (0.1, 7.2, 0.9, 2.9, 8.3, 3.2, '#454cbc'), (1.2, 8.1, 1.4, 3.6, 9.0, 3.0, '#454cbc'),
                   (2.8, 7.5, 1.6, 4.8, 8.6, 2.8, '#454cbc'), (4.2, 6.4, 1.8, 5.4, 7.8, 2.6, '#4c54cc'),
                   (4.5, 5.6, 1.9, 5.5, 6.5, 2.5, GOLD),
                   (-2.6, 1.8, -4.15, -1.6, 2.6, -4.05, GOLD), (1.8, 3.2, -2.9, 2.6, 4.0, -2.8, GOLD),
                   (-1.2, 4.8, -1.5, -0.4, 5.5, -1.4, GOLD), (3.6, 1.8, -1.0, 4.1, 2.4, 0.2, '#fff2a0'),
                   (-4.1, 2.0, 0.6, -4.0, 2.6, 1.6, '#fff2a0')],
    'viking_helmet': [(-4.4, -1.2, -4.4, 4.4, 1.8, 4.4, '#9aa0a8'), (-4.6, -1.6, -4.6, 4.6, -0.6, 4.6, '#c9963a'),
                      (-0.5, -3.5, -4.8, 0.5, -0.6, -4.4, '#9aa0a8'),
                      (-7, 0, -0.8, -4.4, 1.6, 0.8, '#f2e6c8'), (-7.8, 1.6, -0.6, -6.2, 4, 0.6, '#f2e6c8'),
                      (-8, 4, -0.4, -7, 5.2, 0.4, '#d8c8a0'),
                      (4.4, 0, -0.8, 7, 1.6, 0.8, '#f2e6c8'), (6.2, 1.6, -0.6, 7.8, 4, 0.6, '#f2e6c8'),
                      (7, 4, -0.4, 8, 5.2, 0.4, '#d8c8a0')],
    'propeller_cap': [(-4.4, -1.2, -4.4, 4.4, 1.2, 4.4, '#e03a3a'), (-4.4, -1.2, -4.4, 0, 1.25, 0, '#2f5bd8'),
                      (0, -1.2, 0, 4.4, 1.25, 4.4, '#2f5bd8'), (-4.4, -1.2, -7.2, 4.4, -0.5, -4.4, '#2f5bd8'),
                      (-0.4, 1.2, -0.4, 0.4, 2.4, 0.4, '#9aa0a8'), (-4.4, 2.4, -0.6, 4.4, 2.8, 0.6, '#ffd23f'),
                      (-0.6, 2.4, -4.4, 0.6, 2.8, 4.4, '#ffd23f')],
    'crown': [(-4.5, 0, -4.5, 4.5, 2, -3.8, GOLD), (-4.5, 0, 3.8, 4.5, 2, 4.5, GOLD),
              (-4.5, 0, -3.8, -3.8, 2, 3.8, GOLD), (3.8, 0, -3.8, 4.5, 2, 3.8, GOLD),
              (-4.5, 2, -4.5, -3.3, 3.6, -3.8, GOLD), (-0.6, 2, -4.5, 0.6, 3.8, -3.8, GOLD), (3.3, 2, -4.5, 4.5, 3.6, -3.8, GOLD),
              (-4.5, 2, 3.8, -3.3, 3.6, 4.5, GOLD), (-0.6, 2, 3.8, 0.6, 3.8, 4.5, GOLD), (3.3, 2, 3.8, 4.5, 3.6, 4.5, GOLD),
              (-4.5, 2, -0.6, -3.8, 3.8, 0.6, GOLD), (3.8, 2, -0.6, 4.5, 3.8, 0.6, GOLD),
              (-0.7, 0.5, -4.75, 0.7, 1.5, -4.45, '#e03a3a'), (-4.75, 0.5, -0.7, -4.45, 1.5, 0.7, '#3f7fff'),
              (4.45, 0.5, -0.7, 4.75, 1.5, 0.7, '#3f7fff'), (-0.7, 0.5, 4.45, 0.7, 1.5, 4.75, '#5be37c'),
              (-0.4, 3.8, -4.5, 0.4, 4.4, -3.8, '#e03a3a')],
    'sun_hat': [(-7, -0.2, -7, 7, 0.5, 7, '#e8c77a'), (-3.8, 0.5, -3.8, 3.8, 3, 3.8, '#ddb868'),
                (-3.9, 0.5, -3.9, 3.9, 1.3, 3.9, '#ff7be5'), (3.9, 0.4, -1.6, 4.9, 1.6, -0.2, '#ffe14d'),
                (4.1, 0.7, -1.2, 5.1, 1.3, -0.6, '#ff9f3f')],
    'snorkel': [(-3.8, -4.7, -4.7, 3.8, -1.3, -4.2, '#1c1c24'), (-3.2, -4.2, -4.9, 3.2, -1.8, -4.6, '#7fd7ff'),
                (-4.5, -3.5, -4.2, -4.2, -2.5, 4.5, '#1c1c24'), (4.2, -3.5, -4.2, 4.5, -2.5, 4.5, '#1c1c24'),
                (-4.2, -3.5, 4.2, 4.2, -2.5, 4.5, '#1c1c24'), (4.5, -5, -2, 5.3, 3, -1.2, '#ffd23f'),
                (4.5, 2.2, -2, 5.3, 3, 1, '#ff7590')],
    'pineapple': [(-4.5, -8.3, -4.5, 4.5, 0.4, 4.5, '#e8b33a'), (-4.6, -6, -4.6, 4.6, -5.4, 4.6, '#c98a24'),
                  (-4.6, -3, -4.6, 4.6, -2.4, 4.6, '#c98a24'), (-0.4, -8.3, -4.65, 0.4, 0.4, 4.65, '#c98a24'),
                  (-1.2, 0.4, -1.2, 1.2, 4.4, 1.2, '#3a8a2e'), (-3, 0.4, -0.6, -1.2, 2.8, 0.6, '#4aa83c'),
                  (1.2, 0.4, -0.6, 3, 2.8, 0.6, '#4aa83c'), (-0.6, 0.4, -3, 0.6, 2.6, -1.2, '#4aa83c'),
                  (-0.6, 0.4, 1.2, 0.6, 2.6, 3, '#4aa83c'), (-0.5, 4.4, -0.5, 0.5, 6, 0.5, '#5bc24a')],
    'santa_hat': [(-4.7, -0.8, -4.7, 4.7, 1, 4.7, WHITE), (-4, 1, -4, 4, 2.8, 4, '#d62828'),
                  (-3.3, 2.8, -3, 3.5, 4.4, 3.8, '#d62828'), (-2.3, 4.4, -1.5, 3.2, 5.8, 4, '#cc2424'),
                  (-1.2, 5.8, 0, 2.9, 7, 4.1, '#c22020'), (0, 6.8, 1.4, 2.8, 7.8, 4, '#c22020'),
                  (1.6, 6.4, 2, 4, 7.4, 3.6, '#b81c1c'), (3.4, 5.4, 2.2, 4.8, 6.8, 3.4, '#b81c1c'),
                  (3.4, 3.6, 1.6, 5.6, 5.6, 3.8, WHITE)],
    'elf_hat': [(-4.6, -0.6, -4.6, 4.6, 1, 4.6, '#d62828'), (-3.8, 1, -3.8, 3.8, 2.8, 3.8, '#2e9e4a'),
                (-3, 2.8, -2.8, 3.2, 4.4, 3.4, '#2e9e4a'), (-2, 4.4, -1.6, 2.8, 5.8, 3.4, '#2a9444'),
                (-1, 5.8, -0.4, 2.4, 7, 3.2, '#278a40'), (0, 7, 0.6, 2.2, 8, 3, '#278a40'),
                (1, 7.8, 1.2, 2.4, 8.6, 2.8, '#24823c'), (2, 7.2, 1.4, 3.6, 8.2, 2.6, '#24823c'),
                (3.2, 6.4, 1.6, 4.2, 7.4, 2.4, '#24823c'), (3.0, 5.0, 1.3, 4.4, 6.4, 2.7, GOLD)],
    'reindeer_antlers': [(-4.5, -0.4, -0.6, 4.5, 0.4, 0.6, '#5a3a1e'), (-3.4, 0, -0.5, -2.4, 4.4, 0.5, '#8b5a2b'),
                         (-5.4, 2, -0.5, -3.4, 3, 0.5, '#8b5a2b'), (-5.4, 3, -0.5, -4.4, 5.2, 0.5, '#8b5a2b'),
                         (-2.4, 3, -0.5, -1.4, 5.6, 0.5, '#8b5a2b'),
                         (2.4, 0, -0.5, 3.4, 4.4, 0.5, '#8b5a2b'), (3.4, 2, -0.5, 5.4, 3, 0.5, '#8b5a2b'),
                         (4.4, 3, -0.5, 5.4, 5.2, 0.5, '#8b5a2b'), (1.4, 3, -0.5, 2.4, 5.6, 0.5, '#8b5a2b'),
                         (-0.7, -5.4, -4.9, 0.7, -4, -4.3, '#ff3030')],
    'snowman_head': [(-4.5, -8.3, -4.5, 4.5, 0.4, 4.5, '#f5f7ff'), (-0.6, -5, -6.6, 0.6, -3.8, -4.5, '#ff8a1f'),
                     (-2.6, -3, -4.7, -1.4, -1.8, -4.5, '#1c1c24'), (1.4, -3, -4.7, 2.6, -1.8, -4.5, '#1c1c24'),
                     (-2.6, -6.6, -4.7, -1.8, -5.8, -4.5, '#1c1c24'), (-1, -7.2, -4.7, -0.2, -6.4, -4.5, '#1c1c24'),
                     (0.6, -7.2, -4.7, 1.4, -6.4, -4.5, '#1c1c24'), (1.8, -6.6, -4.7, 2.6, -5.8, -4.5, '#1c1c24'),
                     (-4, 0.4, -4, 4, 1, 4, '#1c1c24'), (-2.6, 1, -2.6, 2.6, 4.4, 2.6, '#24242e'),
                     (-2.7, 1, -2.7, 2.7, 1.8, 2.7, '#d62828')],
    'bunny_ears': [(-4.5, -0.4, -0.6, 4.5, 0.4, 0.6, '#f2c4d8'), (-3.2, 0, -0.7, -1.4, 7.2, 0.7, '#f5f0f0'),
                   (-2.8, 0.8, -0.8, -1.8, 6.6, -0.7, '#ff9fc8'), (1.4, 0, -0.7, 3.2, 6.2, 0.7, '#f5f0f0'),
                   (1.8, 0.8, -0.8, 2.8, 5.6, -0.7, '#ff9fc8'), (1.4, 6.2, -0.7, 3.2, 7, 2, '#f5f0f0')],
    'chick_hat': [(-2.6, 0, -2.4, 2.6, 3.2, 2.6, '#ffe14d'), (-1.8, 3.2, -2.6, 1.8, 5.8, 0.8, '#ffe14d'),
                  (-0.6, 4, -3.8, 0.6, 4.8, -2.6, '#ff9f3f'), (-1.4, 4.8, -2.7, -0.6, 5.4, -2.5, '#1c1c24'),
                  (0.6, 4.8, -2.7, 1.4, 5.4, -2.5, '#1c1c24'), (-3.2, 0.8, -1.4, -2.6, 2.6, 1.6, '#f2c93a'),
                  (2.6, 0.8, -1.4, 3.2, 2.6, 1.6, '#f2c93a'), (-0.4, 5.8, -1.2, 0.4, 6.6, 0, '#f2c93a')],
    'egg_shell': [(-4.4, -0.6, -4.4, 4.4, 2.4, 4.4, '#fff5dc'), (-3.4, 2.4, -3.4, 3.4, 3.8, 3.4, '#fff5dc'),
                  (-2.2, 3.8, -2.2, 2.2, 4.8, 2.2, '#fff5dc'),
                  (-4.4, -1.6, -4.4, -3.2, -0.6, -4.2, '#f2e6c8'), (-1.6, -1.6, -4.4, -0.4, -0.6, -4.2, '#f2e6c8'),
                  (1.2, -1.6, -4.4, 2.4, -0.6, -4.2, '#f2e6c8'), (-4.4, -1.6, 4.2, -3.2, -0.6, 4.4, '#f2e6c8'),
                  (-0.6, -1.6, 4.2, 0.6, -0.6, 4.4, '#f2e6c8'), (3.2, -1.6, 4.2, 4.4, -0.6, 4.4, '#f2e6c8'),
                  (-4.4, -1.6, -1.6, -4.2, -0.6, -0.4, '#f2e6c8'), (4.2, -1.6, 0.4, 4.4, -0.6, 1.6, '#f2e6c8'),
                  (-2, 0.4, -4.6, -1, 1.4, -4.4, '#7fd7ff'), (1.6, 1.6, -4.6, 2.4, 2.2, -4.4, '#ff9fc8')],
    'witch_hat': [(-6.5, -0.2, -6.5, 6.5, 0.6, 6.5, '#2a1f3a'), (-3.6, 0.6, -3.6, 3.6, 1.4, 3.6, '#8a4fd1'),
                  (-3.5, 0.6, -3.5, 3.5, 3, 3.5, '#2a1f3a'), (-2.5, 3, -2, 2.5, 5, 3, '#322645'),
                  (-1.5, 5, -0.4, 1.5, 7, 3.4, '#322645'), (-0.7, 6.6, 2.2, 0.7, 8.4, 5, '#3a2e52'),
                  (-0.8, 0.6, -3.8, 0.8, 1.4, -3.6, GOLD)],
    'devil_horns': [(-3.6, 0, -1.6, -1.6, 1.4, 0.4, '#c22020'), (-4.2, 1.2, -1.4, -2.4, 2.6, 0.2, '#b51c1c'),
                    (-4.6, 2.4, -1.2, -3.2, 3.8, 0, '#a01818'), (-4.6, 3.6, -1.0, -3.6, 4.8, -0.2, '#8a1414'),
                    (-4.2, 4.6, -0.9, -3.6, 5.6, -0.4, '#701010'), (-3.8, 5.4, -0.8, -3.4, 6.2, -0.5, '#5a0c0c'),
                    (1.6, 0, -1.6, 3.6, 1.4, 0.4, '#c22020'), (2.4, 1.2, -1.4, 4.2, 2.6, 0.2, '#b51c1c'),
                    (3.2, 2.4, -1.2, 4.6, 3.8, 0, '#a01818'), (3.6, 3.6, -1.0, 4.6, 4.8, -0.2, '#8a1414'),
                    (3.6, 4.6, -0.9, 4.2, 5.6, -0.4, '#701010'), (3.4, 5.4, -0.8, 3.8, 6.2, -0.5, '#5a0c0c')],
    'cat_ears': [(-4.5, -0.4, -0.6, 4.5, 0.4, 0.6, '#1c1c24'), (-3.9, 0, -0.6, -1.3, 1.2, 0.6, '#1c1c24'),
                 (-3.5, 1.2, -0.6, -1.7, 2.4, 0.6, '#1c1c24'), (-3.1, 2.4, -0.6, -2.1, 3.4, 0.6, '#1c1c24'),
                 (-3.3, 0.4, -0.7, -1.9, 1.8, -0.6, '#ff9fc8'),
                 (1.3, 0, -0.6, 3.9, 1.2, 0.6, '#1c1c24'), (1.7, 1.2, -0.6, 3.5, 2.4, 0.6, '#1c1c24'),
                 (2.1, 2.4, -0.6, 3.1, 3.4, 0.6, '#1c1c24'), (1.9, 0.4, -0.7, 3.3, 1.8, -0.6, '#ff9fc8')],
    'pumpkin_head': [(-4.3, 0, -4.3, 4.3, 4.4, 4.3, '#f28c28'), (-0.5, 0, -4.62, 0.5, 4.4, 4.5, '#d9761c'),
                     (-4.5, 0, -0.5, 4.5, 4.4, 0.5, '#d9761c'), (-3.4, 4.4, -3.4, 3.4, 5, 3.4, '#e8801e'),
                     (-0.6, 5, -0.6, 0.6, 6.6, 0.6, '#6b5a2a'), (0.6, 5, -0.2, 2.6, 5.4, 1.4, '#4caa3c'),
                     (-3.6, 2.4, -4.62, -1.4, 3.8, -4.42, '#ffd84a'), (-3.0, 3.8, -4.62, -2.0, 4.2, -4.42, '#ffd84a'),
                     (1.4, 2.4, -4.62, 3.6, 3.8, -4.42, '#ffd84a'), (2.0, 3.8, -4.62, 3.0, 4.2, -4.42, '#ffd84a'),
                     (-0.5, 1.8, -4.62, 0.5, 2.4, -4.42, '#ffd84a'),
                     (-3.4, 0.5, -4.62, 3.4, 1.4, -4.42, '#ffd84a'), (-2.4, 1.4, -4.62, -1.6, 1.8, -4.42, '#ffd84a'),
                     (0.2, 1.4, -4.62, 1.0, 1.8, -4.42, '#ffd84a'), (-1.0, 0.2, -4.62, -0.2, 0.5, -4.42, '#ffd84a'),
                     (1.6, 0.2, -4.62, 2.4, 0.5, -4.42, '#ffd84a')],
}


# What each hat is made of (by colour; '*' for the rest): knit, felt, fur, metal, straw, cloth, shine
MATERIALS = {
    'beanie': {'#fafafa': 'fur', '*': 'knit'},
    'top_hat': {'#b266ff': 'shine', '*': 'felt'},
    'chef_hat': {'*': 'cloth'},
    'cowboy_hat': {'*': 'leather'},
    'wizard_hat': {GOLD: 'metal', '#fff2a0': 'shine', '*': 'stars'},
    'viking_helmet': {'#9aa0a8': 'metal', '#c9963a': 'metal', '*': 'bone'},
    'propeller_cap': {'#9aa0a8': 'metal', '*': 'cloth'},
    'crown': {'*': 'metal'},
    'sun_hat': {'#e8c77a': 'straw', '#ddb868': 'straw', '*': 'cloth'},
    'santa_hat': {WHITE: 'fur', '*': 'felt'},
    'elf_hat': {GOLD: 'metal', '*': 'felt'},
    'snowman_head': {'#f5f7ff': 'snow', '*': 'felt'},
    'bunny_ears': {'*': 'fur'},
    'chick_hat': {'*': 'fur'},
    'witch_hat': {GOLD: 'metal', '*': 'felt'},
    'pineapple': {'#e8b33a': 'scales', '#c98a24': 'scales', '*': 'leaf'},
    'pumpkin_head': {'#f28c28': 'felt', '#d9761c': 'felt', '#e8801e': 'felt', '*': 'cloth'},
    'devil_horns': {'*': 'felt'},
    'reindeer_antlers': {'*': 'bone'},
}


def material_cell(rgb, kind, face, seed):
    """An 8x8 texture cell for one colour: face 0 sides, 1 top, 2 underside."""
    import random
    r = random.Random(seed)
    cell = Image.new('RGBA', (8, 8))
    f = (1.0, 1.12, 0.7)[face]
    for y in range(8):
        for x in range(8):
            k = f
            if face == 0:
                k *= 1.08 - y * 0.025          # lighter at the top of each side
            if kind == 'knit':
                k *= 1.08 if x % 2 == 0 else 0.9
                k *= 0.95 if (y + (x % 2)) % 3 == 0 else 1
            elif kind in ('felt', 'cloth', 'stars'):
                k *= 1 + r.uniform(-0.05, 0.05)
            elif kind in ('fur', 'snow'):
                k *= 1 + r.uniform(-0.1, 0.07)
            elif kind == 'metal':
                k *= 1.25 if (x + y) % 7 == 0 else (1.1 if y < 3 else 0.92)
            elif kind == 'shine':
                k *= 1.2 if y < 2 else 1
            elif kind == 'straw':
                k *= 1.1 if (x + y) % 4 < 2 else 0.88
            elif kind == 'leather':
                k *= 1 + r.uniform(-0.08, 0.04)
            elif kind == 'bone':
                k *= 1 + r.uniform(-0.06, 0.03) - (0.1 if (x * 3 + y) % 11 == 0 else 0)
            elif kind == 'scales':
                k *= 0.82 if (x + y) % 4 == 0 or (x - y) % 4 == 0 else 1.06
            elif kind == 'leaf':
                k *= 1.12 if x in (3, 4) else 1 + r.uniform(-0.06, 0.04)
            elif kind == 'pumpkin':
                k *= 0.88 if x in (0, 4) else 1.04
            cell.putpixel((x, y), shade(rgb, k) + (255,))
    if kind == 'stars' and face != 2:
        for _ in range(2):
            sx, sy = r.randrange(1, 7), r.randrange(1, 7)
            cell.putpixel((sx, sy), (255, 230, 140, 255))
    return cell


# The propeller cap as worn has no propeller: a separate model spins on top of it (the plugin turns it).
HATS['propeller_cap_base'] = HATS['propeller_cap'][:4]


def propeller(out_root):
    tex = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    cols = {'blade_a': (255, 210, 63), 'blade_b': (63, 140, 255), 'tip': (224, 58, 58), 'hub': (154, 160, 168)}
    cells = {}
    for i, (k, c) in enumerate(cols.items()):
        for dx in range(4):
            for dy in range(4):
                tex.putpixel((i * 4 + dx, dy), shade(c, 1.08 - dy * 0.06) + (255,))
        cells[k] = [i * 4 + 0.5, 0.5, i * 4 + 3.5, 3.5]
    tex.save(f'{out_root}/assets/fractal/textures/item/hat/propeller.png')

    def box(f, t, k):
        return {"from": f, "to": t, "faces": {d: {"uv": cells[k], "texture": "#t"} for d in ('north', 'south', 'east', 'west', 'up', 'down')}}
    elements = [box([7.3, 2, 7.3], [8.7, 8.4, 8.7], 'hub'), box([6.8, 8.2, 6.8], [9.2, 9.4, 9.2], 'hub'),
                box([1, 8.4, 7.2], [7, 9.0, 8.8], 'blade_a'), box([9, 8.4, 7.2], [15, 9.0, 8.8], 'blade_b'),
                box([0, 8.4, 7.2], [1.2, 9.0, 8.8], 'tip'), box([14.8, 8.4, 7.2], [16, 9.0, 8.8], 'tip')]
    json.dump({"textures": {"t": "fractal:item/hat/propeller", "particle": "fractal:item/hat/propeller"}, "elements": elements},
              open(f'{out_root}/assets/fractal/models/item/hat/propeller.json', 'w'))
    json.dump({"model": {"type": "minecraft:model", "model": "fractal:item/hat/propeller"}},
              open(f'{out_root}/assets/fractal/items/hat/propeller.json', 'w'))


# The flower crown: a twisted vine around the head with flowers on it (a yellow middle and four petals, facing out).
def _flower_crown():
    vine, leaf = '#3f8f34', '#5bbf45'
    boxes = [(-4.6, 0, -4.6, 4.6, 0.7, -4.0, vine), (-4.6, 0, 4.0, 4.6, 0.7, 4.6, vine),
             (-4.6, 0, -4.0, -4.0, 0.7, 4.0, vine), (4.0, 0, -4.0, 4.6, 0.7, 4.0, vine)]
    flowers = [(-2.6, 'z', -1, '#ff8cc8'), (0.6, 'z', -1, '#ffffff'), (3.2, 'z', -1, '#c9a0ff'),
               (-2.0, 'x', -1, '#ffe14d'), (1.8, 'x', -1, '#ff8cc8'), (-1.8, 'x', 1, '#ffffff'), (2.2, 'x', 1, '#c9a0ff'),
               (-1.0, 'z', 1, '#ff8cc8'), (2.6, 'z', 1, '#ffe14d')]
    for (p, axis, side, col) in flowers:
        out = side * 4.6
        if axis == 'z':        # on the front (-z) or back (+z) band, p along x
            x, y = p, 0.35
            boxes += [(x - 0.5, y - 0.5, out - 0.35, x + 0.5, y + 0.5, out + 0.35, '#ffcf3a'),
                      (x - 0.5, y + 0.5, out - 0.25, x + 0.5, y + 1.4, out + 0.25, col), (x - 0.5, y - 1.2, out - 0.25, x + 0.5, y - 0.5, out + 0.25, col),
                      (x - 1.4, y - 0.5, out - 0.25, x - 0.5, y + 0.5, out + 0.25, col), (x + 0.5, y - 0.5, out - 0.25, x + 1.4, y + 0.5, out + 0.25, col)]
        else:                  # on the left (-x) or right (+x) band, p along z
            z, y = p, 0.35
            boxes += [(out - 0.35, y - 0.5, z - 0.5, out + 0.35, y + 0.5, z + 0.5, '#ffcf3a'),
                      (out - 0.25, y + 0.5, z - 0.5, out + 0.25, y + 1.4, z + 0.5, col), (out - 0.25, y - 1.2, z - 0.5, out + 0.25, y - 0.5, z + 0.5, col),
                      (out - 0.25, y - 0.5, z - 1.4, out + 0.25, y + 0.5, z - 0.5, col), (out - 0.25, y - 0.5, z + 0.5, out + 0.25, y + 0.5, z + 1.4, col)]
    for (x, z) in [(-1.2, -4.75), (2.0, -4.75), (-4.75, 0.2), (4.75, -0.4), (0.8, 4.75)]:
        boxes.append((x - 0.6, 0.6, z - 0.2, x + 0.6, 1.1, z + 0.2, leaf) if abs(z) > 4 else (x - 0.2, 0.6, z - 0.6, x + 0.2, 1.1, z + 0.6, leaf))
    return boxes


HATS['flower_crown'] = _flower_crown()
MATERIALS['flower_crown'] = {'*': 'cloth'}


# Suit helmets with shapes armour can't have (worn like hats): a hood around the face, plus ears, eyes or a beak.
def hood(col, trim=None):
    t = trim or col
    return [(-4.5, 0, -4.5, 4.5, 0.7, 4.5, col), (-4.5, -8.4, -4.5, -4.0, 0, 4.5, col), (4.0, -8.4, -4.5, 4.5, 0, 4.5, col),
            (-4.0, -8.4, 4.0, 4.0, 0, 4.5, col), (-4.0, -1.6, -4.6, 4.0, 0, -4.0, t), (-4.0, -8.4, -4.6, -3.2, -1.6, -4.0, t),
            (3.2, -8.4, -4.6, 4.0, -1.6, -4.0, t), (-3.2, -8.4, -4.6, 3.2, -7.4, -4.0, t)]


HATS['suit_bunny'] = hood('#f6f2f2') + [
    (-3.4, 0.7, -0.7, -1.4, 7.6, 0.7, '#f6f2f2'), (-3.0, 1.4, -0.85, -1.8, 6.9, -0.7, '#f6b8d2'),
    (1.4, 0.7, -0.7, 3.4, 5.2, 0.7, '#f6f2f2'), (1.8, 1.4, -0.85, 3.0, 4.8, -0.7, '#f6b8d2'),
    (1.4, 5.2, -0.7, 3.4, 6.6, 3.0, '#f6f2f2'), (-0.9, -6.6, -4.75, 0.9, -5.6, -4.55, '#f6b8d2')]
HATS['suit_frog'] = hood('#4fbf3c', '#2e8a24') + [
    (-3.8, 0.7, -3.8, -1.0, 3.2, -1.0, '#ffffff'), (-3.0, 1.2, -3.95, -1.8, 2.6, -3.8, '#1c1c24'),
    (1.0, 0.7, -3.8, 3.8, 3.2, -1.0, '#ffffff'), (1.8, 1.2, -3.95, 3.0, 2.6, -3.8, '#1c1c24'),
    (-3.2, -7.9, -4.75, 3.2, -7.4, -4.55, '#c8f0a0')]
HATS['suit_chick'] = hood('#ffe14d', '#f2b82a') + [
    (-1.2, -2.8, -6.0, 1.2, -1.6, -4.6, '#ff8a1f'), (-0.4, 0.7, -1.0, 0.4, 2.6, 0.2, '#f2b82a'),
    (-0.4, 0.7, 0.2, 0.4, 2.0, 1.4, '#f2b82a')]
MATERIALS.update({'suit_bunny': {'*': 'fur'}, 'suit_frog': {'#ffffff': 'cloth', '#1c1c24': 'cloth', '*': 'felt'},
                  'suit_chick': {'#ff8a1f': 'cloth', '*': 'fur'}})

VOX_PATTERN = {'knit': 'knit', 'felt': 'flat', 'cloth': 'solid', 'stars': 'solid', 'leather': 'solid', 'fur': 'flat',
               'snow': 'flat', 'metal': 'metal', 'shine': 'metal', 'straw': 'wood', 'bone': 'solid', 'scales': 'brick',
               'leaf': 'solid', 'pumpkin': 'solid'}


def build(out_root):
    """Every hat as a blocky model with its own pixels (2 per head pixel), in the game's style."""
    from vox import Box, write
    for d in ('textures/item/hat', 'models/item/hat', 'items/hat'):
        os.makedirs(f'{out_root}/assets/fractal/{d}', exist_ok=True)
    for name, boxes in HATS.items():
        mats = MATERIALS.get(name.replace('_base', ''), {})
        vboxes, lo, hi = [], [99] * 3, [-99] * 3
        for (x0, y0, z0, x1, y1, z1, c) in boxes:
            f = [round(max(-16, min(32, v)), 3) for v in (8 + x0 * U, TOP + y0 * U, 8 + z0 * U)]
            t = [round(max(-16, min(32, v)), 3) for v in (8 + x1 * U, TOP + y1 * U, 8 + z1 * U)]
            for k in range(3):
                lo[k], hi[k] = min(lo[k], f[k]), max(hi[k], t[k])
            vboxes.append(Box(f, t, c, VOX_PATTERN.get(mats.get(c, mats.get('*', 'cloth')), 'solid')))
        size = max(hi[k] - lo[k] for k in range(3))
        s = round(min(0.85, 14.0 / size), 3)
        cy = (lo[1] + hi[1]) / 2 - 8
        display = {
            "gui": {"rotation": [25, 200, 0], "translation": [0, round(-cy * s, 2), 0], "scale": [s, s, s]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, round(-cy * s * 0.5, 2), 0], "scale": [s * 0.5] * 3},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, round(-cy * s, 2), 0], "scale": [s, s, s]},
            "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
            "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, round(-cy * 0.4, 2), 0], "scale": [0.4, 0.4, 0.4]},
            "firstperson_righthand": {"rotation": [0, 225, 0], "translation": [0, round(-cy * 0.4, 2), 0], "scale": [0.4, 0.4, 0.4]},
        }
        write(out_root, f'hat/{name}', vboxes, scale=U / 2, display=display)
    propeller(out_root)
    return list(HATS)


def _old_build(out_root):
    """Writes every hat's texture, model and item definition under out_root/assets/fractal."""
    for d in ('textures/item/hat', 'models/item/hat', 'items/hat'):
        os.makedirs(f'{out_root}/assets/fractal/{d}', exist_ok=True)
    for name, boxes in HATS.items():
        colours = []
        for b in boxes:
            if b[6] not in colours:
                colours.append(b[6])
        tex = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
        cell = {}
        mats = MATERIALS.get(name.replace('_base', ''), {})
        for i, c in enumerate(colours):
            rgb = hexc(c)
            kind = mats.get(c, mats.get('*', 'cloth'))
            for j in range(3):                                  # sides, top, underside
                k = i * 3 + j
                x, y = (k % 8) * 8, (k // 8) * 8
                tex.paste(material_cell(rgb, kind, j, zlib.crc32(f'{name}/{i}/{j}'.encode())), (x, y))
                cell[(c, j)] = [x / 4, y / 4, (x + 8) / 4, (y + 8) / 4]
        tex.save(f'{out_root}/assets/fractal/textures/item/hat/{name}.png')
        elements = []
        lo = [99, 99, 99]
        hi = [-99, -99, -99]
        for (x0, y0, z0, x1, y1, z1, c) in boxes:
            f = [8 + x0 * U, TOP + y0 * U, 8 + z0 * U]
            t = [8 + x1 * U, TOP + y1 * U, 8 + z1 * U]
            f = [round(max(-16, min(32, v)), 3) for v in f]
            t = [round(max(-16, min(32, v)), 3) for v in t]
            for k in range(3):
                lo[k] = min(lo[k], f[k])
                hi[k] = max(hi[k], t[k])
            faces = {d: {"uv": cell[(c, 0)], "texture": "#t"} for d in ('north', 'south', 'east', 'west')}
            faces['up'] = {"uv": cell[(c, 1)], "texture": "#t"}
            faces['down'] = {"uv": cell[(c, 2)], "texture": "#t"}
            elements.append({"from": f, "to": t, "faces": faces})
        # In menus the hat is shown turned, scaled to fit and moved to the middle of the slot.
        size = max(hi[k] - lo[k] for k in range(3))
        s = round(min(0.85, 14.0 / size), 3)
        cy = (lo[1] + hi[1]) / 2 - 8
        display = {
            "gui": {"rotation": [25, 200, 0], "translation": [0, round(-cy * s, 2), 0], "scale": [s, s, s]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, round(-cy * s * 0.5, 2), 0], "scale": [s * 0.5] * 3},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, round(-cy * s, 2), 0], "scale": [s, s, s]},
            "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
            "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, round(-cy * 0.4, 2), 0], "scale": [0.4, 0.4, 0.4]},
            "firstperson_righthand": {"rotation": [0, 225, 0], "translation": [0, round(-cy * 0.4, 2), 0], "scale": [0.4, 0.4, 0.4]},
        }
        json.dump({"textures": {"t": f"fractal:item/hat/{name}", "particle": f"fractal:item/hat/{name}"},
                   "elements": elements, "display": display},
                  open(f'{out_root}/assets/fractal/models/item/hat/{name}.json', 'w'))
        json.dump({"model": {"type": "minecraft:model", "model": f"fractal:item/hat/{name}"}},
                  open(f'{out_root}/assets/fractal/items/hat/{name}.json', 'w'))
    propeller(out_root)
    return list(HATS)
