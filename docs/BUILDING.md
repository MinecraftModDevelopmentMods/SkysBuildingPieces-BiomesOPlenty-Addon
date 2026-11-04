# Building from source

Use Temurin 17.0.1+12 for Gradle and Temurin 17.0.1+12 for production. Set
`SBP_JAVA17_HOME` to the Java 17 installation. The wrapper uses Gradle 9.6.1
and ForgeGradle 7.0.34 for Minecraft 1.19.4 and Forge 45.4.0. ForgeGradle's
Mavenizer also needs Java 25; it is not used to compile or run the mod.

The runtime log checks use Python 3.11 or later. If `python` is not on your
path, pass `-PpythonExecutable=<python executable>` to Gradle.

Check out Building Pieces 0.3.8.119041 at the exact source commit in
`gradle/core-dependency.properties`, then run its `gradlew assemble` task.
Build this add-on with:

```text
gradlew check build javadoc verifyReleaseArtifacts writeReleaseChecksums verifyEclipseProductionClasspath -PbuildingPiecesCoreDir=<core checkout>
```

The development dependency is the core's `build/libs-dev` jar, not a shaded
copy or a published Maven dependency. Packaged games use both normal main
jars. BOP and TerraBlender are fetched from Modrinth Maven as checksum-verified
dependencies. Neither is bundled in the add-on.

The normal build checks both core jar hashes, its source commit and its clean
working tree. CI builds that pinned commit before building the add-on. A local-only
`buildingPiecesCoreVersion` override is available for testing another core
candidate; it requires an explicit core directory and is rejected in CI.

Use `genEclipseRuns eclipse` with the same core-directory property to prepare
the IDE. Ordinary client and server launches exclude unit tests and the
build-only runtime probe. Release preparation is covered in [the release guide](RELEASING.md).

## Recipe-book resources

Recipes and unlock advancements live under `src/main/resources/data`. The
runtime checks compare those entries with the registered recipes and exercise
inventory discovery, crafting-table discovery and recipe-book persistence.
Optional catalogue entries load only when their recipe exists.

Recipe unlocks have no shared advancement parent. This keeps a large catalogue
from creating a deeply nested visibility tree. Optional recipes and their
advancements use matching Forge conditions.

## Runtime checks

`integrationTest` runs the shared core placement checks together with the BOP
material, basket, decoration and migration checks, then reloads the saved world.
The accepted 1.12, 1.14, 1.15, 1.16, 1.17 and 1.18 jars produced the sealed worlds in
`src/testFixtures`; the forward tests copy those worlds before starting the
native 1.19 server.
Older synthetic fixture writers are no longer used to create pre-flattening saves.

`nativeClientTest` checks the baked models and basket GUI through real client
packets. Its `bopClientPhase` property also accepts `client-basket-disconnect`
and `client-basket-disconnect-reload`; run those in that order to check a player
disconnecting with the basket open. All these runs use disposable build folders.

The development integration harness uses one mod-loading thread. Packaged checks
retain Forge's normal loading settings. Unexpected errors or crashes fail either
profile; native Forge45 controls complete without severe log messages.

`nativeInstalledBopPolicyTest` checks all 33 materials with replacement off and
on, including placed pieces and inventories. It uses a labelled test fixture,
which is excluded from ordinary launches and release jars. `check` includes it.

For combined native checks, `-PbopNativeCompanions=true` adds the exact Grass
Slabs, Mineralogy and OreSpawn jars listed in `gradle/optional-runtime.properties`.
Their main and development hashes are checked before copying. These profiles
remain separate from ordinary client and server runs.
Run `nativeClientTest` once, then repeat with `-PbopClientReload=true` to check
the same saved client world. The second run refuses changed test or dependency
jars. Disconnect tests use their separate `bopClientPhase` values instead.

Hosted checks build the pinned Grass Slabs and Building Pieces sources before
the add-on. They run compilation, unit tests and artifact checks. Gameplay,
packaged launches and world upgrades are qualified locally.
