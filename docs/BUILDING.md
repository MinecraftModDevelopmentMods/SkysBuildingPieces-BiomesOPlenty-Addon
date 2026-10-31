# Building from source

Use Temurin 17.0.1+12 for Gradle and Temurin 8.0.502+7 for production. Set
`SBP_JAVA8_HOME` to the Java 8 installation. The wrapper uses Gradle 9.6.1
and ForgeGradle 7.0.34 for Minecraft 1.14.4 and Forge 28.2.26. ForgeGradle's
Mavenizer also needs Java 25; it is not used to compile or run the mod.

The runtime log checks use Python 3.11 or later. If `python` is not on your
path, pass `-PpythonExecutable=<python executable>` to Gradle.

Check out Building Pieces 0.3.8.114041 at the exact source commit in
`gradle/core-dependency.properties`, then run its `gradlew assemble` task.
Build this add-on with:

```text
gradlew check build javadoc verifyReleaseArtifacts writeReleaseChecksums verifyEclipseProductionClasspath -PbuildingPiecesCoreDir=<core checkout>
```

The development dependency is the core's `build/libs-dev` jar, not a shaded
copy or a published Maven dependency. Packaged games use both normal main
jars. BOP is fetched as a checksum-verified dependency and is not bundled.

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
The accepted 1.12 jars produced the sealed worlds in `src/testFixtures`; the
forward tests copy those worlds before starting the native 1.14 server. Older
synthetic fixture writers are no longer used to create pre-flattening saves.

`nativeClientTest` checks the baked models and basket GUI through real client
packets. Its `bopClientPhase` property also accepts `client-basket-disconnect`
and `client-basket-disconnect-reload`; run those in that order to check a player
disconnecting with the basket open. All these runs use disposable build folders.

The dense development integration harness uses one mod-loading thread to avoid
a class-hierarchy race observed in ModLauncher 4. Packaged checks retain Forge's
normal loading settings. Unexpected errors and crashes fail either profile.
One native startup config notice is recognised only when settings are unchanged
and the debug log identifies the exact `forge-server.toml` self-save.
