# Existing worlds

## Moving from Minecraft 1.14.4 to 1.15.2

BOP removed ethereal wood in this version. Saved planks, logs, stripped logs,
doors, fences, gates, slabs, stairs, buttons and pressure plates use birch.
Leaves and saplings use white cherry, including potted saplings. Ethereal boat
items become birch boat items. This does not provide recovery for saved BOP
boat entities.

Dried sand becomes BOP's dried salt, and barley becomes tall wheat. Saved
directions, slab halves, waterlogging, stack counts and custom item data are
kept. These are replacements rather than the original textures. An invalid
waterlogged double slab stops recovery with an explanation instead of losing
its water.

Four removed biomes have replacements in the same dimension: Dead Reef uses
End Midlands, Ethereal Forest uses End Highlands, Fungi Forest uses Undergarden
and Glowstone Grotto uses the vanilla Nether. Existing terrain stays in place.

Keep `data/skysbuildingpiecesbop_named_registry.dat` with the world. It preserves
the old biome registry so chunks opened after the first upgrade save still
receive the right replacement. It belongs in world backups alongside the core's
recovery data. Already upgraded chunks are not converted again.

## Moving from Minecraft 1.12.2 to 1.15.2

BOP changed both its block names and its material list. The add-on converts
supported old material palettes before Minecraft reads them, keeping slab
halves, stair directions, gate states, stack counts and custom item data.
Existing migration totals are retained. Chunks containing recovered pieces or
inventories are preserved even if they had not finished population in the old
world; this is not terrain retrogen.

Some original materials no longer exist. Sacred oak and eucalyptus use oak;
mangrove uses birch, ebony uses dark oak, pine uses jungle wood and bamboo
thatching uses willow. Removed gem pieces use similarly coloured terracotta:
amber and topaz become orange, amethyst magenta, malachite cyan, peridot lime,
ruby red, sapphire light blue and tanzanite purple. These are replacements,
not exact matches for the old textures. The original saved piece identities
remain recognised when loading unopened chunks later.

Flower Basket contents use current plant names. White anemones become oxeye
daisies, tiny cacti become regular cacti and the old small water lilies become
vanilla lily pads. Flowering saplings use BOP's flowering oak. Missing or
malformed contents still stop recovery rather than disappearing.

Native BOP logs, leaves, soil, farmland, doors and other supported blocks also
need their old metadata split into named states. Log axes, placed leaves,
door halves, moisture and saved fluid levels are carried across. The add-on
does this while reading old chunks, including chunks opened after the first
upgrade save.

BOP removed some of those blocks altogether. Loamy, sandy and silty soils use
vanilla dirt, grass, paths and farmland, keeping coarse dirt separate. Retired
woods use the same replacements listed above for their logs, leaves and doors.
Old coral uses similar vanilla coral or seagrass; seaweed becomes kelp. Quicksand
becomes sand. Removed blood, poison, honey and hot-spring liquids become water,
so their special effects do not carry over.

Retired gem ores use similar vanilla ores: redstone for ruby, emerald for
peridot and malachite, lapis for amethyst, tanzanite and sapphire, and gold for
amber and topaz. Stored gems become redstone, emeralds, lapis lazuli or gold
nuggets respectively, one for one. The coloured full gem blocks and building
pieces still use the terracotta replacements listed above.

Old terraria become small potted plants. Crystal becomes purpur, honey blocks
become slime blocks, hive blocks become yellow terracotta and blue fire becomes
ordinary fire. These replacements keep occupied positions; they do not recreate
the removed features. Removed fruits become apples, berries become sweet
berries, empty jars become glass bottles and dyes use their vanilla equivalents.
Mud balls keep BOP's current mud-ball item. Stack counts and unrelated custom
data are retained.

Saved BOP biome numbers are updated too. Biomes that still exist keep their
names. Removed biomes use similar current ones: for example, Lavender Fields
becomes Lavender Field, Tropical Island becomes Tropics, and the old snowy
tundra uses vanilla snowy tundra so it stays cold enough for snow. This updates
the saved biome without generating its terrain again. Unrelated mods' biomes
still need their own upgrade support.

These mappings cover the add-on's materials and supported recovered contents.
They are not a promise that every old BOP biome, entity or unrelated block has
an upgrade path. Test the whole pack on a copy before moving your main world.

## Earlier pieces and decorations

Back up the world and test a separate copy. Remove BuildingBricks only after
every saved material, shape and item has a proven recovery path. Unsupported
content still needs a replacement, even if the buildings look correct.

To replace supported pieces while BuildingBricks remains installed, enable
`compat.forceReplaceBuildingBricksPieces` in the core's configuration and
restart. The setting is shared: vanilla and installed add-on catalogues are
processed together. Turning it off stops further work but cannot undo it.

BOP has independent progress and totals in the core's migration report.
Chunks already processed by the vanilla core can still receive BOP recovery.
Original material tile data is retained after successful conversion. Shapes,
counts and unrelated item data are preserved; temporary item damage indices
are not used to guess the saved material.

Supported recovery remains automatic if BuildingBricks is absent. Unknown
content without a safe target stops loading with an explanation. Restore the
original mods and return to a backup rather than continuing an unsafe upgrade.

BOP removed several stone materials before Minecraft 1.12. Sky's existing
pieces keep their IDs and orientation. Limestone, shale and siltstone now use
Mineralogy's corresponding raw or polished rock; crag rock uses gabbro. If
Mineralogy is absent, this add-on supplies the same textures and full-block
forms without needing tile entities. Two matching slabs still make the matching
full block. If Mineralogy is added later, a fallback full block can be crafted
one for one into its Mineralogy equivalent.

The original BOP full blocks and item stacks for these retired rocks are also
recovered, not just Sky's building pieces. Raw and polished variants keep their
distinction, stack counts and custom data. Recovery has its own progress and
totals and runs automatically when those old BOP IDs are missing. It does not
depend on the BuildingBricks replacement setting.

Migration totals count converted stacks. They cannot protect contents if
another mod discards the cabinet or entity holding them during an upgrade.
Check those containers in a disposable world before using your main save.

Stone formations and jelled poison also have replacement blocks. Formations
remain non-solid decorations with no survival drops. Their new stone models
follow neighbouring formations and ceiling or floor support; existing formations
are kept even if an old anchor is missing. Jelled poison retains its full-block
collision, translucent appearance and original friction. It does not flow,
bounce or poison entities, just as in the original mod.

Tree moss becomes BOP's willow vines. The saved attachment faces are preserved,
but the texture and foliage colour follow willow vines. All three recoveries
are automatic and have separate progress from retired rocks and building pieces,
so chunks visited by an earlier beta are still eligible. Stack counts and custom
item data are retained. No new world generation or decoration recipes are added.

Only the replacements described here are covered. Read Forge's missing-content
warning before confirming an upgrade; these replacements do not make every BOP
1.10 world safe to load in 1.12 without further checks.

Old Flower Baskets also recover automatically, with all 18 slots and unrelated
item data preserved. Missing or malformed contents stop recovery rather than
being silently removed. This does not protect a basket if another mod discards
the cabinet or entity holding it. See [Flower Basket](FLOWER-BASKET.md).

## Retired seeds, pinecones and flower bands

These substitutions run automatically when upgrading old saved items. They do
not depend on the BuildingBricks replacement setting.

| Old item | Replacement |
|---|---|
| One BOP turnip seed | One vanilla beetroot seed |
| One pinecone | Two vanilla brown dyes |
| One dull flower band | Eight vanilla oxeye daisies |

Pinecones retain their original dye yield. BOP no longer has the old clover, so
the flower band becomes eight similar small white flowers. Names and unrelated
custom data are kept. The old item variant is not copied to the replacement.
Old capabilities
that a replacement cannot use retain an archive in the item's custom data.

These target-version substitutions do not require HarvestCraft. Missing
replacements stop the upgrade with an explanation rather than deleting stacks.

Expanded stacks use spare slots in recognised chests, player and ender
inventories, and Flower Baskets. If there is not enough room, or the inventory
layout is unknown, recovery stops before loading the world. Free a slot or split
the old pinecones into stacks of at most 32 in the original world, then retry
from that backup. Remove pinecones and flower bands from item frames first:
one frame cannot preserve all the items produced by these substitutions.

These item recoveries also apply to chunks not opened during the first upgrade.
They do not rescue an inventory or entity discarded by another mod. Cabinet
contents and old MCA villagers still need their separate upgrade paths.

## Block-ID budget for modpacks

The add-on uses 74 block IDs: 66 piece palettes, one retired-rock palette,
two replacement decorations and five hidden recovery holders. The holders
are not creative entries or playable blocks.

Together with the core's 238 registered blocks, that is 312 additional blocks.
The optional Mineralogy catalogue adds another 35. Existing native
BOP blocks are not counted again. New pieces use fixed metadata palettes
rather than material tile entities; saved material and orientation assignments
stay unchanged. The Flower Basket adds no block IDs.
