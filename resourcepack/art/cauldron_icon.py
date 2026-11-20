"""Witch's Cauldron icon options: crisp 32 x 32 pixel art, centred, outlined (same painter as the scarecrow's)."""
from scarecrow_icon import P

IRON, IRON_L, IRON_D = '#3a3a48', '#5a5a6e', '#24242e'
FIRE, FIRE_L, LOG = '#ff8a1f', '#ffd84a', '#7a4a22'


def pot(p, top=14, brew='#7fe35a', brew_l='#c8ff9a'):
    """A round black cauldron (rim at y = top), filled with a glowing brew, on little feet."""
    p.rect(6, top, 25, top + 1, IRON_L)                                # the rim
    p.rect(7, top, 24, top, brew)                                      # brew showing over the rim
    p.rect(9, top, 13, top, brew_l)
    p.rect(7, top + 2, 24, top + 10, IRON)                             # the belly, rounder towards the bottom
    p.rect(6, top + 3, 25, top + 8, IRON)
    p.rect(9, top + 11, 22, top + 11, IRON)
    p.rect(8, top + 3, 9, top + 7, IRON_L)                              # a shine
    p.rect(20, top + 3, 24, top + 9, IRON_D)
    p.rect(9, top + 12, 10, top + 13, IRON_D)                           # feet
    p.rect(21, top + 12, 22, top + 13, IRON_D)
    p.rect(5, top + 4, 5, top + 6, IRON_D)                              # handles
    p.rect(26, top + 4, 26, top + 6, IRON_D)


def ca1():
    """Classic (Sasha's pick): green bubbling brew with bubbles rising (no fire underneath)."""
    p = P()
    pot(p, 13)
    for (x, y, big) in ((11, 9, True), (17, 6, False), (20, 10, True), (14, 3, False), (22, 4, False)):
        c = '#7fe35a'
        if big:
            p.rect(x, y, x + 1, y + 1, c)
            p.dot(x, y, '#c8ff9a')
        else:
            p.dot(x, y, c)
    p.rect(11, 12, 13, 12, '#7fe35a')
    p.rect(18, 12, 19, 12, '#7fe35a')                                    # bubbles breaking the surface
    return p.done()


def ca2():
    """Magic: purple brew with a sparkling swirl of spell rising out of it, and a potion bottle beside."""
    p = P()
    pot(p, 14, '#b266ff', '#e0b8ff')
    swirl = [(16, 13), (15, 12), (14, 11), (14, 10), (15, 9), (17, 9), (18, 8), (18, 7), (17, 6), (15, 6), (14, 5), (14, 4), (15, 3), (17, 2)]
    for i, (x, y) in enumerate(swirl):
        p.dot(x, y, '#d9a8ff' if i % 3 else '#ffffff')
        p.dot(x + 1, y, '#b266ff')
    for (x, y) in ((10, 6), (22, 4), (21, 10)):                           # sparkles
        p.dot(x, y, '#ffffff')
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p.dot(x + dx, y + dy, '#e0b8ff')
    # a potion bottle leaning on it
    p.rect(26, 21, 29, 26, '#ff6fd8')
    p.rect(27, 19, 28, 20, '#cfe8ff')
    p.rect(27, 18, 28, 18, LOG)
    p.dot(26, 21, '#ffb8ee')
    return p.done()


def ca3():
    """Peeking witch: orange brew bubbling, and behind the pot a witch's hat with glowing eyes peeping over the rim."""
    p = P()
    # the witch's hat and eyes behind
    p.rect(8, 9, 23, 10, '#3a3048')                                      # brim
    p.rect(11, 5, 20, 8, '#3a3048')
    p.rect(13, 2, 18, 4, '#3a3048')
    p.rect(16, 0, 19, 1, '#3a3048')
    p.dot(20, 1, '#3a3048')
    p.rect(11, 8, 20, 8, '#7fe35a')                                      # green band
    p.rect(15, 7, 16, 8, '#ffd84a')                                      # buckle
    p.rect(10, 11, 21, 13, '#5a8a4a')                                    # her green face peeking up
    p.rect(12, 11, 13, 12, '#ffe14d')
    p.rect(18, 11, 19, 12, '#ffe14d')                                     # glowing eyes
    p.dot(12, 12, '#2a1a10')
    p.dot(19, 12, '#2a1a10')
    pot(p, 14, '#ff8a1f', '#ffd27f')
    for (x, y) in ((9, 3), (24, 6), (26, 12)):
        p.rect(x, y, x + 1, y + 1, '#ff8a1f')
    return p.done()


CAULDRON = [ca1, ca2, ca3]
