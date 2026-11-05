# Building from source

Use Temurin 21.0.7+6. The wrapper runs Gradle 9.2.1 and NeoGradle 7.1.38
for Minecraft 1.20.6 and NeoForge 20.6.139.

Check out the Building Pieces commit in `gradle/core-dependency.properties`
and run `gradlew assemble` there. Put the core checkout alongside this
one in the usual version folders, or pass
`-PbuildingPiecesCoreDir=<core checkout>`. The add-on uses the core's
ordinary main jar and does not bundle its classes. The build checks the
core version, source commit and jar hash, and verifies the BOP,
TerraBlender and GlitchCore jars fetched from Modrinth Maven.

Build and audit the add-on with:

```text
gradlew check build javadoc verifyReleaseArtifacts writeReleaseChecksums
```

Use `gradlew eclipse verifyEclipseConfiguration` to prepare the IDE. Run it
with `-Declipse.application=org.eclipse.ui.ide.workbench` from a terminal
to export the native launch files. Use one Gradle home for this checkout's
command line and Eclipse launches. The regular client and server use the
merged production output, without test classes or test mods.

## Runtime checks

The test harnesses use Python 3.11 or later. Build the separate runtime
probe with `gradlew preparePackagedNativeProbe`. Run
`python tools/run_packaged_checks.py --help` for native server and client
checks, or `python tools/run_development_checks.py --help` for development
runs. Start with a fresh folder under `build` and use the same verified
jars on reload. The regular game runs do not include the probe.

The packaged checks cover the Flower Basket, placed pieces, recipes and
world recovery. The client can also test basket packets and closing an open
basket during a disconnect. Upgrades always use copies of the sealed saves.

Recipe and advancement files under `src/main/resources/data` use
NeoForge conditions for optional materials. Retired rock recipes keep the
same IDs whether they use a Mineralogy block or the add-on's fallback.

See [the release guide](RELEASING.md) for the artifact checks.
