# Sky's Building Pieces — Biomes O Plenty

Building pieces for the woods, stones and gem blocks in Biomes O Plenty.
Requires Minecraft 1.10.2, Forge 12.18.3.2511, Java 8, Sky's Building Pieces
0.2.0.110021 and Biomes O Plenty 5.0.0. This beta is qualified against BOP
5.0.0.2236. Install all three mods on both the client and server.

Version 0.1.0.110021 adds 66 block IDs for 33 materials, including all 16
plank woods, bamboo thatching, eight stone materials and eight gem blocks.
Existing BOP slabs and stairs are reused. New pieces use fixed metadata
palettes and do not need tile entities. No terrain is generated.

Use the core's seven cutting templates; there are no extra template recipes.
Matching pieces combine into supported larger pieces or their original full
block. Wooden walls, terrain smoothing and materials from other biome mods
are not included.

Optional replacement of installed BuildingBricks content uses the core's
`compat.forceReplaceBuildingBricksPieces=false` setting. Enabling it also
covers supported BOP pieces, with separate BOP progress and totals.
Back up worlds before enabling replacement. Adding this mod can process BOP
pieces in chunks already visited by the vanilla core.

Do not remove BuildingBricks from Sylvester yet: excluded soil slabs,
unsupported materials and tools may still need it. Unsupported absent-mod
recovery stops rather than guessing at a replacement.

See [building and testing](docs/BUILDING.md), [existing worlds](docs/WORLD-UPGRADES.md)
and [trying the beta](docs/TRYING-THE-BETA.md). Publication is not yet enabled.

Licensed under LGPL-2.1-only. Adapted BuildingBricks definitions and core
geometry retain their MIT notice. BOP code and textures are not bundled.
