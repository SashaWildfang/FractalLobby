# Lobby resource pack

`FractalLobby/` is the whole pack the Lobby server sends: zip its contents and put
it in `plugins/Nexo/pack/external_packs/FractalLobby.zip`. It has:

- the rank icons and badges (`minecraft:default` font, from the Fractal pack), and a light red AFK badge in the same style (U+E2A0),
- the backgrounds for the realm selector, settings, chat colour, trails, parkour and Radio menus (`fractal:lobby` font),
- the settings menu icons (item models `fractal:menu/NAME`, most from Survival's pack),
- the Survival realm icon (item model `fractal:survival`),
- the status emojis from Survival's chat emojis (item models `fractal:status/NAME`, glyphs U+F300 on) and the status menu backgrounds,
- slate textures for every container screen, the creative menu (tabs, scroller, item and inventory tabs)
  and the hotbar, a purple XP bar, white creative tab titles, no "Inventory" label in menus, and no hearts or hunger,
- no crafting corner in the inventory (grid, arrow, result slot and "Crafting" label painted over) and no recipe
  book button, and no armour or shield outlines in the empty equipment slots; the plugin blocks the grid and keeps the recipe book closed (`join-items.no-crafting`).

To rebuild it:
`python3 build_pack.py <Fractal pack> <CrazySurvival>/assets/minecraft/textures/gui/menus <vanilla assets (gui/container, gui/container/creative_inventory, gui/sprites/container/creative_inventory)> <out> <CrazySurvival>/assets/minecraft/textures/item`

Collectibles (built by `art/`): `kit.py` is the little painting kit for the 32x32 pixel-art icons (crisp, flat
shades, dark outline, like the Survival pack), `vox.py` builds the blocky 3D models (one texture pixel per model
unit), `preview3d.py` renders models to a picture for checking, `icons.py` draws the gadget,
cloak, gesture and season icons and the cloak pieces, `armor.py` draws each suit's armour layers
(`fractal:suit_<set>`, an equipment asset) and its piece icons, `models.py` writes the 3D models (cloak pieces,
cookie fountain, trampoline, balloon) and `hats.py` the hats. The pack also hides the armour bar.

