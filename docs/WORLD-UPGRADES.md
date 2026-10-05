# Existing worlds

Back up the world and test a separate copy. Keep BuildingBricks installed in
mixed packs such as Sylvester until every remaining material, shape and tool
has a supported replacement.

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
