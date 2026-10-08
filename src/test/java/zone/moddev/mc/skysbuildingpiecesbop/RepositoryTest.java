package zone.moddev.mc.skysbuildingpiecesbop;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RepositoryTest {
    @Test void newerCoreOverrideIsLocalOnly() throws Exception {
        String build=text("build.gradle");assertTrue(build.contains("buildingPiecesCoreVersion"));
        assertTrue(build.contains("localCoreVersion.isPresent() && (System.getenv('CI') == 'true'"));
        assertTrue(build.contains("actualCoreProperties.mod_version != effectiveCoreVersion"));
        String pin=text("gradle/core-dependency.properties");assertTrue(pin.contains("version=0.3.0.110021"));assertTrue(pin.contains("api=1"));
    }
    @Test void runtimeHarnessDoesNotClaimTheNormalMinecraftPort() throws Exception {
        String build=text("build.gradle");
        assertTrue(build.contains("server-ip=127.0.0.1"));assertTrue(build.contains("server-port=0"));
    }
    @Test void documentationSupportFilesUsePortableLineEndings() throws Exception {
        String build=text("build.gradle");
        assertTrue(build.contains("'.css'"));assertTrue(build.contains("'.js'"));
        assertTrue(build.contains("Non-portable archive line endings"));
    }
    @Test void localMaterialIsIgnoredAndNoCoreClassesAreVendored() throws Exception {
        for(String name:new String[]{"AGENTS.md","agent-notes/result.json",".codex/local",".claude/local","run/world","build/dependencies/core"}) {
            Process p=new ProcessBuilder("git","check-ignore","-q",name).start();assertEquals(0,p.waitFor(),name);
        }
        assertFalse(Files.exists(Paths.get("src/main/java/zone/moddev/mc/skysbuildingpieces")));
        String build=text("build.gradle");assertTrue(build.contains("coreDevelopmentJar"));assertTrue(build.contains("bop_sha256"));
        assertFalse(build.contains("FMLCorePluginContainsFMLMod"));
        assertFalse(build.contains("'FMLCorePlugin':"));
        assertTrue(build.contains("accessTransformers.from(bopDevelopmentAccessRules)"));
        assertTrue(build.contains("prepareDevelopmentDependencies"));
    }
    @Test void workflowsBuildTheLockedCoreAndCannotPublish() throws Exception {
        for(String file:new String[]{"ci.yml","codeql-analysis.yml"}) {
            String workflow=text(".github/workflows/"+file);assertTrue(workflow.contains("core-dependency.properties"));
            assertTrue(workflow.contains("steps.content.outputs.pieces"));assertTrue(workflow.contains("./gradlew jar"));
            assertFalse(workflow.contains("secrets."));assertFalse(workflow.contains("gh release"));assertFalse(workflow.contains("publishRelease"));
        }
        String ci=text(".github/workflows/ci.yml");assertTrue(ci.contains("if-no-files-found: error"));
        for(String classifier:new String[]{"","-sources","-javadoc"})assertTrue(ci.contains("SkysBuildingPieces-BiomesOPlenty-Addon-0.1.0.110021"+classifier+".jar"));
        assertTrue(ci.contains("-PbuildingPiecesCoreDir=dependencies/pieces"));
        String tags=text(".github/workflows/release-on-tag.yml");
        assertTrue(tags.contains("verifyReleaseArtifacts"));assertFalse(tags.contains("secrets."));
    }
    private String text(String path)throws Exception {return new String(Files.readAllBytes(Paths.get(path)),StandardCharsets.UTF_8);}
}
