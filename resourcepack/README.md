# Lobby resource pack

`FractalLobby/` is the whole pack the Lobby server sends (zip it and put it in
`plugins/Nexo/pack/external_packs/FractalLobby.zip`). It has only:

- the rank icons and badges (`minecraft:default` font, from the Fractal pack), and
- the realm selector backgrounds (`fractal:lobby` font, used when
  `realm-selector.custom-gui` is on).

`make_gui.py` redraws the realm selector backgrounds from Survival's menu
textures: `python3 make_gui.py <CrazySurvival>/assets/minecraft/textures/gui/menus <out>`.
