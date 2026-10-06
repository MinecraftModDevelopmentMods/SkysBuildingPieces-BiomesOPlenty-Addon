# Building from source

Use Java 17 for Gradle and Temurin 8.0.502+7 for production. Set
`SBP_JAVA8_HOME` to the Java 8 installation. The wrapper uses Gradle 9.6.1
and ForgeGradle 7.0.34 with stable 29-1.10.2 mappings.

Check out the core repository at the commit in
`gradle/core-dependency.properties`, then run its `gradlew jar` task. Build
this project with `gradlew check build javadoc verifyReleaseArtifacts
writeReleaseChecksums verifyEclipseProductionClasspath
-PbuildingPiecesCoreDir=<core checkout>`.

The development dependency is the core's `build/libs-dev` jar, not a shaded
copy or a published Maven dependency. Packaged games use both normal main
jars. BOP is fetched as a checksum-verified development dependency, not bundled.

The pinned core is 0.3.0.110021. A local-only `buildingPiecesCoreVersion`
override requires an explicit core directory and is rejected in CI.
The add-on catalogue and API version remain unchanged. Hosted qualification
awaits the authorised push of the pinned core source.

Use `genEclipseRuns eclipse` with the same core-directory property. Ordinary
client and server launches exclude the build-only probe and unit tests.
Publication remains disabled until each mod has its own release metadata.
