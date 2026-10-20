# Building from source

Use Java 17 for Gradle and Temurin 8.0.502+7 for production. Set
`SBP_JAVA8_HOME` to the Java 8 installation. The wrapper uses Gradle 9.6.1
and ForgeGradle 7.0.34 for Minecraft 1.12.2.

Check out Building Pieces 0.3.6.112021 at the exact source commit in
`gradle/core-dependency.properties`, then run its `gradlew jar` task.
Build this add-on with:

```text
gradlew check build javadoc verifyReleaseArtifacts writeReleaseChecksums verifyEclipseProductionClasspath -PbuildingPiecesCoreDir=<core checkout>
```

The development dependency is the core's `build/libs-dev` jar, not a shaded
copy or a published Maven dependency. Packaged games use both normal main
jars. BOP is fetched as a checksum-verified dependency and is not bundled.

CI builds the pinned core commit before building the add-on. A local-only
`buildingPiecesCoreVersion` override is available for testing another core
candidate; it requires an explicit core directory and is rejected in CI.

Use `genEclipseRuns eclipse` with the same core-directory property to prepare
the IDE. Ordinary client and server launches exclude unit tests and the
build-only runtime probe. Release preparation is covered in [the release guide](RELEASING.md).
