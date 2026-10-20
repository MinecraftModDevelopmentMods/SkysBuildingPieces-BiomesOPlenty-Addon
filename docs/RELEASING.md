# Preparing a release

Pushes and pull requests run the build, tests, artifact audit, CodeQL and Gradle
wrapper checks. CI uploads the main, sources and Javadoc jars with their checksums.
Tags only validate a candidate; they never publish it.

This add-on publishes to CurseForge project 1755976. Publication is enabled only
in MMD; a fork build never uploads a release. Building Pieces and Biomes O Plenty
are marked as required dependencies. Release the matching core version first.

The MMD release workflow uses the organisation's `CURSEFORGE_TOKEN`,
`MAVEN_UPLOAD_URL`, `MAVEN_UPLOAD_USERNAME` and `MAVEN_UPLOAD_PASSWORD`.
It requires a passing CI build and explicit confirmation of live publication.
Enter the full release version, choose `release` for the approved stable
candidate, and confirm live publication.

The workflow resolves the Minecraft branch from the version, builds the exact
commit and its pinned core dependency, and checks one immutable artifact bundle.
It validates or creates the tag, then publishes the same files to Maven,
CurseForge and GitHub, in that order. A failed step prevents later publication.
Check the public downloads before announcing a release.

The dispatcher reads the selected branch's Minecraft, loader, Java and artifact
metadata. It is not restricted to 1.10 or 1.12: later Forge and NeoForge ports
use the same version suffix and branch naming rules. Each port must supply its
own tested build setup, dependency pin and correct metadata.

If publication stops, inspect the completed stages before retrying. Keep the
same commit and immutable bundle; rerun only failed jobs when an upload has
already succeeded. Do not create another CurseForge file while one is awaiting
approval. An existing release with the same jar does not need another upload.
