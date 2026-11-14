# EvoSim resource pack

Model for the tilted mounting sheep (`animation.tilted-mount` in config.yml).

- Merge `assets/` into your server resource pack (or make a new pack with these
  files plus a `pack.mcmeta` for your game version).
- No textures are bundled. `assets/minecraft/atlases/{blocks,items}.json` pull
  the vanilla `entity/sheep/sheep` and `entity/sheep/sheep_wool` textures into
  the item atlas. If your game version names them differently, change the paths
  there and in `models/item/mounting_sheep.json`.
- The wool colour is tinted from the item's custom model data colour, set by the plugin.
- Model: faces +z, pivot (8,8,8) is the hip (rear end of the body), so the plugin
  can tilt it nose-up by setting the display's pitch.
- Without the pack, set `animation.tilted-mount: false`, otherwise the mounting
  sheep shows as a missing-model cube.
