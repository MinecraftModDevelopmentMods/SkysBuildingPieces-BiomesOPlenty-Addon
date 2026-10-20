# Existing worlds

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
| One BOP turnip seed | One Pam's HarvestCraft turnip seed |
| One pinecone | Two BOP brown dyes |
| One dull flower band | Eight BOP clover |

Pinecones retain their original dye yield. The flower band returns its original
crafting ingredients rather than pretending that clover is wearable headgear.
Names and unrelated custom data are kept. The old item variant is not copied to
the replacement, which would turn clover into another flower. Old capabilities
that a replacement cannot use retain an archive in the item's custom data.

HarvestCraft is not required for the building pieces or the other recoveries.
It must provide `harvestcraft:turnipseeditem` if the world contains old BOP turnip
seeds. Missing replacements stop the upgrade with an explanation rather than
substituting an unrelated seed or deleting the stack.

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

Together with the core's 236 IDs, that is 310 additional IDs. Existing native
BOP blocks are not counted again. New pieces use fixed metadata palettes
rather than material tile entities; saved material and orientation assignments
stay unchanged. The Flower Basket adds no block IDs.
