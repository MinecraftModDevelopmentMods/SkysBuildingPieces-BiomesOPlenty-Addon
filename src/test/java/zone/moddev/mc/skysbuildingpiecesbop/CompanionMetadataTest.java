package zone.moddev.mc.skysbuildingpiecesbop;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Verify compatibility using the jars that the development run will actually load. */
class CompanionMetadataTest {
    @ParameterizedTest
    @ValueSource(strings = {"biomesoplenty", "terrablender", "glitchcore"})
    void resolvedCompanionFitsItsDeclaredRange(String modId) throws Exception {
        String source = Files.readString(Path.of("src/main/resources/META-INF/neoforge.mods.toml"))
                .replace("${mod_id}", "skysbuildingpiecesbop");
        UnmodifiableConfig metadata = new TomlParser().parse(source);
        List<UnmodifiableConfig> dependencies = metadata.get(List.of("dependencies", "skysbuildingpiecesbop"));
        UnmodifiableConfig dependency = dependencies.stream()
                .filter(entry -> modId.equals(entry.get("modId"))).findFirst().orElseThrow();
        String requirement = dependency.get("versionRange");
        String path = System.getProperty("test.companion." + modId);
        assertNotNull(path, "Supply the resolved " + modId + " jar to this test");

        try (JarFile jar = new JarFile(path)) {
            var descriptor = jar.getJarEntry("META-INF/neoforge.mods.toml");
            assertNotNull(descriptor, "The companion must be a NeoForge mod");
            String contents;
            try (var input = jar.getInputStream(descriptor)) {
                contents = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            UnmodifiableConfig companion = new TomlParser().parse(contents);
            List<UnmodifiableConfig> mods = companion.get("mods");
            String version = mods.stream().filter(entry -> modId.equals(entry.get("modId")))
                    .findFirst().orElseThrow().get("version");
            if (version.equals("${file.jarVersion}")) {
                version = jar.getManifest().getMainAttributes().getValue("Implementation-Version");
            }
            assertNotNull(version, "The companion must declare its version");
            assertTrue(VersionRange.createFromVersionSpec(requirement)
                            .containsVersion(new DefaultArtifactVersion(version)),
                    modId + " " + version + " is outside the declared range " + requirement);
        }
    }
}
