# Bedrock (Geyser) version of the Lobby pack

Builds `FractalLobby.mcpack` and `fractal_lobby_mappings.json` from the Java pack Nexo makes
(`plugins/Nexo/pack/pack.zip`), so Bedrock players see the Lobby's item icons.

```
unzip pack.zip -d java/                     # the Java pack
unzip .assetCache/26.2/26.2.zip -d vanilla/ # vanilla models/textures (parents)
python3 pairs.py java ../../src/main/java ../../../radio/src/main/java config.yml pairs.json
python3 convert.py java vanilla pairs.json out
```

- `pairs.py`: Geyser maps by Java base item, so this reads which base item each model sits on
  (same-line pairs, the catalogue's icon items, config items, the method around each model).
- `convert.py`: flat models use their texture; 3D models are drawn as the Java inventory shows them.
  The lobby font's pages (U+F000-F3FF, the drawn menu backgrounds) are blank on Bedrock.

Install: `FractalLobby.mcpack` in the proxy's `plugins/Geyser-Velocity/packs/`, the json in
`plugins/Geyser-Velocity/custom_mappings/`, `enable-custom-content: true` in Geyser's config.
Rebuild after changing the Java pack or adding item models.
