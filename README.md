# Sky's Building Pieces — Biomes O Plenty

Piece names use the material itself: Gold Wall and Oak Step, rather than the
full block's name followed by the shape. All 18 languages have matching material labels.

Building pieces for the woods, stones and gem blocks in Biomes O Plenty.
Requires Minecraft 1.12.2, Forge 14.23.5.2859, Java 8, Sky's Building Pieces
0.3.6.112021 and Biomes O Plenty 7.0.1. The target BOP build is
7.0.1.2445. Install all three mods on both the client and server.

Version 0.2.2.112021 adds 74 block IDs for 33 materials, including all 16
plank woods, bamboo thatching, eight stone materials and eight gem blocks.
Existing BOP slabs and stairs are reused. New pieces use fixed metadata
palettes and do not need tile entities. No terrain is generated.
The 74 IDs cover 66 piece palettes, one retired-rock palette, two replacement
decorations and five hidden recovery holders. The holders are not playable
blocks or creative entries.

With core 0.3.6.112021, use ordinary crafting patterns for BOP materials;
no template or tool is needed. Native BOP slab and stair recipes remain intact.
Regular slab items also place vertically. Vertical slabs drop regular slabs and
are hidden from creative inventory; no separate vertical slab recipe is needed.
Matching pieces combine into supported larger pieces or their original full
block. Wooden walls, terrain smoothing and materials from other biome mods
are not included.

Optional replacement of installed BuildingBricks content uses the core's
`compat.forceReplaceBuildingBricksPieces=false` setting. Enabling it also
covers supported BOP pieces, with separate BOP progress and totals.
Back up worlds before enabling replacement. Adding this mod can process BOP
pieces in chunks already visited by the vanilla core.

Removing an older building mod requires an audit and a tested world backup.
Unsupported materials, shapes or tools may still need a replacement.
Unsupported absent-mod recovery stops rather than guessing at a replacement.

Old BOP stone formations and jelled poison have replacement blocks. Tree moss
becomes BOP willow vines, keeping its attachment faces. These recoveries run
automatically on upgrade, independently of the BuildingBricks setting. They
do not generate new terrain or replace already missing blocks.

See [crafting](docs/RECIPES.md), [building and testing](docs/BUILDING.md), [existing worlds](docs/WORLD-UPGRADES.md)
and [trying the beta](docs/TRYING-THE-BETA.md). Publication is not yet enabled.

[Release preparation](docs/RELEASING.md) covers the CI checks and publication safeguards.

Licensed under LGPL-2.1-only. Adapted BuildingBricks definitions and core
geometry retain their MIT notice. BOP code and textures are not bundled.

Limestone, shale, siltstone and their polished forms use Mineralogy's matching
blocks when available. Gabbro replaces the retired crag rock material. Without
Mineralogy, one fixed palette supplies these seven full blocks using Mineralogy's
textures. Existing Sky piece IDs and metadata are unchanged. The copied assets
retain their separate LGPL-3.0 notices.
Recovery also preserves the original retired BOP full blocks and item stacks.
It does not cover other removed BOP content.

The Flower Basket is back, with 18 slots for plants, saplings and leaves.
Right-click it in either hand to open it. Suitable plants picked up from the
ground go into the first basket in your main inventory, with anything that does
not fit handled normally. Craft one from six wooden sticks; no template is needed.
Old BOP baskets keep their stored items on upgrade. The basket uses our own
code and artwork and adds no block IDs. See [the basket guide](docs/FLOWER-BASKET.md).

Three other retired BOP items have replacements: turnip seeds become Pam's
HarvestCraft turnip seeds, each pinecone becomes two BOP brown dyes, and each
dull flower band returns eight clover. The flower band is not restored as
headgear. These changes keep stack quantities and custom item data; they do not
add recipes, playable items or blocks. See [existing worlds](docs/WORLD-UPGRADES.md)
for dependencies and inventory-space checks before upgrading.

All custom walls have lower connecting panels and raised posts at ends, corners
and junctions. A block or torch above keeps its supporting post. Selection follows
the visible wall; the wall still prevents jumping over it.
Use this add-on with the matching Building Pieces 0.3.6 core; update both jars together.
