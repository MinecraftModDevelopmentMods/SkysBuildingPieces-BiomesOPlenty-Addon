package zone.moddev.mc.skysbuildingpiecesbop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import static org.junit.jupiter.api.Assertions.*;

class RetiredMaterialsTest {
    @Test
    void retiredMaterialsHaveExactOptionalTargetsAndPermanentFallbackSlots() throws Exception {
        String[] legacyIds = { "crag_rock", "limestone", "limestone_polished", "shale", "shale_polished", "siltstone",
                "siltstone_polished" };
        String[] rocks = { "gabbro", "limestone", "limestone_smooth", "shale", "shale_smooth", "siltstone",
                "siltstone_smooth" };
        Set<Integer> slots = new HashSet<Integer>();
        int count = 0;

        for (JsonElement element : Catalogue.read(getClass(),
                "/assets/skysbuildingpiecesbop/catalogue/materials.json")) {
            JsonObject material = element.getAsJsonObject();
            String registryId = material.get("id").getAsString();

            for (int meta = 0; meta < legacyIds.length; meta++) {
                if (registryId.equals("biomesoplenty:" + legacyIds[meta])) {
                    JsonObject full = material.getAsJsonObject("blocks").getAsJsonObject("full");

                    assertEquals("mineralogy:" + rocks[meta], full.get("id").getAsString());
                    assertEquals(0, full.get("meta").getAsInt());

                    JsonObject fallback = full.getAsJsonObject("fallback");

                    assertEquals("skysbuildingpiecesbop:retired_rocks", fallback.get("id").getAsString());
                    assertEquals(meta, fallback.get("meta").getAsInt());
                    assertTrue(slots.add(meta));
                    assertEquals("skysbuildingpiecesbop:blocks/retired_" + rocks[meta],
                            material.get("textures").getAsString());
                    assertTrue(Files.isRegularFile(
                            Paths.get("src/main/resources/assets/skysbuildingpiecesbop/textures/blocks/retired_"
                                    + rocks[meta] + ".png")));
                    count++;
                }
            }
        }

        assertEquals(7, count);
        assertEquals(7, slots.size());
        assertTrue(Files.isRegularFile(Paths.get("MINERALOGY-LGPL-3.0.txt")));
        assertTrue(Files.isRegularFile(Paths.get("MINERALOGY-GPL-3.0.txt")));
    }
}
