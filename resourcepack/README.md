# Lobby resource pack

`FractalLobby/` is the whole pack the Lobby server sends: zip its contents and put
it in `plugins/Nexo/pack/external_packs/FractalLobby.zip`. It has:

- the rank icons and badges (`minecraft:default` font, from the Fractal pack),
- the realm selector and settings menu backgrounds (`fractal:lobby` font),
- the settings menu icons (item models `fractal:menu/NAME`, most from Survival's pack),
- the Survival realm icon (item model `fractal:survival`),
- slate inventory, chest and hotbar textures, a purple XP bar, no "Inventory" label
  in menus, and no hearts or hunger.

To rebuild it:
`python3 build_pack.py <Fractal pack> <CrazySurvival>/assets/minecraft/textures/gui/menus <vanilla assets> <out> <CrazySurvival>/assets/minecraft/textures/item`
