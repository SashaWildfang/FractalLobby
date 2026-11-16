# Lobby resource pack

`FractalLobby/` is the whole pack the Lobby server sends: zip its contents and put
it in `plugins/Nexo/pack/external_packs/FractalLobby.zip`. It has:

- the rank icons and badges (`minecraft:default` font, from the Fractal pack),
- the realm selector backgrounds (`fractal:lobby` font, `realm-selector.custom-gui`),
- slate inventory and chest textures, with no "Inventory" label in menus, and
- the Survival realm icon (item model `fractal:survival`).

To rebuild it: `python3 build_pack.py <Fractal pack> <CrazySurvival>/assets/minecraft/textures/gui/menus <vanilla assets> <out>`.
`make_gui.py` draws the realm menu backgrounds from Survival's menu textures.
