# Existing worlds

## Moving to Minecraft 26.3

Brimstone becomes orpiment, and thermal calcite becomes sphalerite. The
matching brick slabs, stairs and walls keep their shapes and orientations.
Stored items keep their counts, custom names and other data. Sphalerite no
longer uses the old colour changes based on distance from a vent.

Back up your world before updating the core and add-on together. Use Biomes
O' Plenty 26.3.0.0.16, TerraBlender 26.3.0.0.2 and GlitchCore 26.3.0.0.0.

## Moving to Minecraft 26.1.2

Back up the entire world before upgrading. Update the core mod and this add-on
together. Use Biomes O' Plenty 26.1.2.0.22, TerraBlender 26.1.2.0.3 and
GlitchCore 26.1.2.0.2. Keep the older save and its data folders until you've
checked your builds, inventories and Flower Baskets in the upgraded copy.

## Moving to Minecraft 1.21.11

Use Biomes O' Plenty 21.11.0.32, TerraBlender 21.11.0.0 and GlitchCore 21.11.0.4.
Keep a backup of the whole world before opening it. Saved origin oak and rose
plants use the new BOP names. Maple leaf piles become full patches of matching
leaf litter, and rainbow birches use vanilla birch. Old Wildflowers use BOP's
purple Wildflowers. BOP boats keep their wood type and any chest contents.

Saved Aspen Glade, Grassland and Jacaranda Glade biome entries now use Forested
Field, Field and Lavender Field. Existing terrain and builds stay in place.
New chunks generate with the installed BOP version.

## Moving to Minecraft 1.21.1

Use Biomes O' Plenty 21.1.0.14, TerraBlender 4.1.0.8 and GlitchCore 2.1.0.2.
The BOP blocks, items and biomes from the supported 1.20.6 build have the
same IDs in this version.
Keep a backup of the whole world, including its data folder. Flower Baskets
can read their older inventory format and use Minecraft's new item data when
you change what is inside. Existing pieces keep their material and shape.

## Moving to Minecraft 1.20.1

Old BOP cherry wood uses Minecraft's cherry blocks. Sky's cherry pieces keep
their saved IDs and shapes and use the vanilla cherry texture. Standing and
wall signs keep their orientation, text, dye and glow.

White cherry plants use Snowblossom, and the old autumn leaves and saplings
use the matching maple colours. Overgrown black sand becomes mossy black sand.
Rose quartz shards become rose quartz chunks. Retired biomes use nearby
equivalents, such as Cherry Grove, Moor and Lavender Field. Saved terrain
stays in place.

Use Biomes O' Plenty 19.0.0.96 with TerraBlender and GlitchCore. Keep the
world's data folder with your backup, since older chunks may still need its
registry snapshots when you first visit them.

## Moving to Minecraft 1.19.4

BOP's mud, mud bricks, mud-brick slabs, stairs and walls use the matching
vanilla blocks. Slab halves, stair directions, wall connections and waterlogging
are retained. Existing Sky pieces keep their IDs, materials and orientations.
Mud balls become clay balls and individual mud bricks become ordinary bricks;
stored stacks keep their counts and custom data.

Rooted sand becomes rooted dirt. It stays a solid, non-falling block, although
its appearance and plant support follow rooted dirt. Rainbow Hills becomes
Auroral Garden, Boreal Forest becomes Coniferous Forest, Wooded Scrubland becomes
Scrubland and Wooded Wasteland becomes Wasteland. Existing terrain is not rebuilt.
Chunks opened later receive the same replacements.

Keep the registry sidecars with the world and its backups. Test a copy of the
whole modpack before upgrading; this add-on cannot recover unrelated missing mods.

## Moving to Minecraft 1.18.2

The pieces and Flower Basket retain their saved identities and contents. BOP
now uses TerraBlender; install it alongside BOP on both the client and server.

Removed tree roots become weeping vines, keeping their growth age and hanging
stems. Mangrove roots become small dripleaves, keeping both halves and their
water. These use the replacement plants' appearance and behaviour.
The older single-block root from 1.12 stays one block tall as BOP dead grass.

Several biome variants were removed or renamed. Saved biome identities use
similar surviving biomes: Meadow becomes Field, Grove becomes Mediterranean
Forest, Highland Crag becomes Crag, and the alpine pair uses Frozen Peaks and
Snowy Slopes. Existing terrain stays in place. Biome names are retained through
Minecraft's conversion to section palettes, including chunks opened later.
Keep both registry sidecars with the world and its backups.

## Moving to Minecraft 1.17.1

BOP removed nether crystals and nether crystal blocks. They become rose quartz
clusters and rose quartz blocks. Placed clusters keep their floor, ceiling or
wall attachment; stored items keep their counts, names and custom data. The
original crystals were dry, so their replacements start dry too.

Existing building pieces keep their material palettes and orientations. Old
palette holders can become equivalent native BOP pieces when loaded. Flower
Baskets retain their slots and contents. Earlier plant and biome replacements
continue to use the matching current names; terrain is not regenerated.

This covers supported blocks and items. It does not recover old BOP boat
entities. Check the whole modpack on a backup copy before upgrading.

## Moving to Minecraft 1.16.5

The piece catalogue keeps its existing materials, IDs and orientations. BOP
renamed tall wheat to barley and tall cattails to cattails. Both halves of
those plants are retained. Older one-block cattails and roots use BOP dead
grass, so they remain one block tall. Full ash blocks use basalt. Stored
items keep their counts and custom data. Old white sandstone and mud-brick walls
keep their connections, posts and waterlogging through the changed wall format.

Removed biomes use similar surviving biomes in the same dimension: for example,
Ghost Forest becomes Muskeg, Mangrove becomes Bayou Mangrove, Maple Woods
becomes Seasonal Forest and Origin Hills becomes Origin Valley. Undergarden
uses Undergrowth, while Ashen Inferno uses Basalt Deltas. Existing terrain is
not regenerated. Earlier replacements are carried forward to live names.

Keep the named-registry sidecar with the world so chunks opened later retain
their original biome identities. Test a backup copy of the whole modpack before
upgrading; this add-on cannot recover unrelated missing mods or their entities.

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
