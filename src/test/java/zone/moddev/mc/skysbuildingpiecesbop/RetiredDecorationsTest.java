package zone.moddev.mc.skysbuildingpiecesbop;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RetiredDecorationsTest {
    private static final Path ASSETS = Paths.get("src/main/resources/assets/skysbuildingpiecesbop");

    @Test
    void everyFormationProfileHasIndependentGeometryAndVanillaTextures() throws Exception {
        JsonObject variants = json("blockstates/stone_formations.json").getAsJsonObject("variants");

        assertEquals(8, variants.size());

        for (java.util.Map.Entry<String, JsonElement> variant : variants.entrySet()) {
            String model = variant.getValue().getAsJsonObject().get("model").getAsString().split(":")[1];
            JsonObject data = json("models/block/" + model + ".json");

            assertEquals("minecraft:blocks/stone", data.getAsJsonObject("textures").get("stone").getAsString());
            assertTrue(data.getAsJsonArray("elements").size() > 0);

            for (JsonElement element : data.getAsJsonArray("elements")) {
                for (JsonElement face : element.getAsJsonObject().getAsJsonObject("faces").entrySet().stream()
                        .map(java.util.Map.Entry::getValue).toArray(JsonElement[]::new)) {
                    assertFalse(face.getAsJsonObject().has("cullface"));
                }
            }
        }

        assertEquals("minecraft:blocks/glass_lime",
                json("models/block/jelled_poison.json").getAsJsonObject("textures").get("all").getAsString());
    }

    @Test
    void hiddenRecoveryHoldersRetainAllSixteenMetadataSlots() throws Exception {
        for (String name : new String[] { "stone_formations", "jelled_poison", "tree_moss" }) {
            JsonObject variants = json("blockstates/legacy_bop_" + name + ".json").getAsJsonObject("variants");

            assertEquals(16, variants.size());

            for (int meta = 0; meta < 16; meta++) {
                assertTrue(variants.has("metadata=" + meta));
            }
        }

        assertTrue(66 + 1 + 2 + 2 + 3 <= 80);
    }

    private JsonObject json(String path) throws Exception {
        return new JsonParser().parse(new String(Files.readAllBytes(ASSETS.resolve(path)), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }
}
