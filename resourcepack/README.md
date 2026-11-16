# Lobby resource pack

`FractalLobby/` is the whole pack the Lobby server sends: zip its contents and put
it in `plugins/Nexo/pack/external_packs/FractalLobby.zip`. It has:

- the rank icons and badges (`minecraft:default` font, from the Fractal pack),
- the backgrounds for the realm selector, settings, chat colour, trails, parkour and Radio menus (`fractal:lobby` font),
- the settings menu icons (item models `fractal:menu/NAME`, most from Survival's pack),
- the Survival realm icon (item model `fractal:survival`),
- slate textures for every container screen, the creative menu (tabs, scroller, item and inventory tabs)
  and the hotbar, a purple XP bar, white creative tab titles, no "Inventory" label in menus, and no hearts or hunger,
- no crafting corner in the inventory (grid, arrow, result slot and "Crafting" label painted over) and no recipe
  book button; the plugin blocks the grid and keeps the recipe book closed (`join-items.no-crafting`).

To rebuild it:
`python3 build_pack.py <Fractal pack> <CrazySurvival>/assets/minecraft/textures/gui/menus <vanilla assets (gui/container, gui/container/creative_inventory, gui/sprites/container/creative_inventory)> <out> <CrazySurvival>/assets/minecraft/textures/item`
