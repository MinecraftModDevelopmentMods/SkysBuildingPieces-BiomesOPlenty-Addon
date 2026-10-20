package zone.moddev.mc.skysbuildingpiecesbop;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyItemsTest {
    @Test
    void substitutionsAreExplicitAndDoNotAddBlocksOrForceSettings() throws Exception {
        String code = text("src/main/java/zone/moddev/mc/skysbuildingpiecesbop/LegacyItemRecovery.java");

        for (String registryId : new String[] { "turnip_seeds", "pinecone", "dull_flower_band",
                "harvestcraft:turnipseeditem", "biomesoplenty:brown_dye", "biomesoplenty:flower_0" }) {
            assertTrue(code.contains(registryId));
        }

        assertTrue(code.contains("{1,2,8}"));
        assertFalse(code.contains("Register<Block>"));
        assertFalse(code.contains("forceReplace"));
        assertFalse(code.contains("init(SkysBuildingPiecesBop.ID"));

        String registration = text("src/main/java/zone/moddev/mc/skysbuildingpiecesbop/basket/Baskets.java");

        assertTrue(registration.contains("init(SkysBuildingPiecesBop.ID,2)"));
    }

    private String text(String file) throws Exception {
        // These checks concern registrations and multipliers, not the source layout.
        return new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8).replaceAll("\\s+", "");
    }
}
