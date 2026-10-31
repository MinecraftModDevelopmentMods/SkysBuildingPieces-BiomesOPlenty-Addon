package zone.moddev.mc.skysbuildingpiecesbop;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForwardFixtureTest {
    private static final Path FIXTURES = Paths.get("src/testFixtures");
    private static final Path CATALOGUE = Paths.get("src/main/resources/assets/skysbuildingpiecesbop/catalogue");

    @Test
    void fixtureAndNativeRegistryExportAreChecksumLocked() throws Exception {
        JsonObject metadata = json(FIXTURES.resolve("worlds/building-pieces-bop-1.12.2.json")).getAsJsonObject();

        assertEquals(metadata.get("archive_sha256").getAsString(),
                digest(FIXTURES.resolve("worlds/building-pieces-bop-1.12.2.zip")));
        assertEquals(metadata.get("registry_export_sha256").getAsString(),
                digest(FIXTURES.resolve("bop-1.12.2-registry-states.json")));
        assertEquals(1487, metadata.get("blocks").getAsInt());
        assertEquals(1500, metadata.get("basket_item_units").getAsInt());
        assertEquals(8, metadata.getAsJsonArray("baskets").size());
        assertTrue(metadata.get("source_world_unchanged").getAsBoolean());

        Set<String> artifacts = new HashSet<>();

        metadata.getAsJsonArray("source_artifacts")
                .forEach(entry -> artifacts.add(entry.getAsJsonObject().get("sha256").getAsString()));
        assertTrue(artifacts.contains("4273DD260D2F259F4FCE56680DA79AA9B2FBF90FD3ABCC1B494E228965FE1316"));
        assertTrue(artifacts.contains("4BCF55530A17261187C57AA235A73721BB8A80BA20BB2D8432B5D9BFE8072CF6"));
    }

    @Test
    void everyNativeMappingUsesAnActualSourceState() throws Exception {
        Map<String, JsonObject> original = new HashMap<>();

        for (JsonElement value : json(FIXTURES.resolve("bop-1.12.2-registry-states.json")).getAsJsonArray()) {
            JsonObject row = value.getAsJsonObject();

            original.put(key(row), row);
        }

        Set<String> used = new HashSet<>();
        JsonArray mappings = json(CATALOGUE.resolve("legacy_states.json")).getAsJsonArray();

        assertEquals(486, mappings.size());

        for (JsonElement value : mappings) {
            JsonObject row = value.getAsJsonObject();

            assertTrue(used.add(key(row)), "Duplicate metadata mapping: " + row);
            assertNotNull(original.get(key(row)), "Invented metadata: " + row);
            assertEquals(original.get(key(row)).get("properties"), row.get("properties"));
        }
    }

    @Test
    void plantMappingsCoverTheSavedBasketVariantsWithoutAirTargets() throws Exception {
        JsonObject plants = json(CATALOGUE.resolve("legacy_plants.json")).getAsJsonObject();
        int variants = 0;

        for (Map.Entry<String, JsonElement> entry : plants.entrySet()) {
            assertTrue(entry.getKey().startsWith("biomesoplenty:"));

            for (Map.Entry<String, JsonElement> variant : entry.getValue().getAsJsonObject().entrySet()) {
                assertTrue(Integer.parseInt(variant.getKey()) >= 0 && Integer.parseInt(variant.getKey()) < 16);
                assertTrue(variant.getValue().getAsString().matches("(minecraft|biomesoplenty):[a-z_]+"));
                assertFalse(variant.getValue().getAsString().endsWith(":air"));
                variants++;
            }
        }

        assertEquals(102, variants);
        assertEquals("minecraft:oxeye_daisy", plants.getAsJsonObject("biomesoplenty:flower_0").get("9").getAsString());
        assertEquals("biomesoplenty:flowering_oak_sapling",
                plants.getAsJsonObject("biomesoplenty:sapling_1").get("5").getAsString());
    }

    @Test
    void expandedUpgradeFixturesKeepTheirExactAcceptedSourceArtifacts() throws Exception {
        for (String fixture : new String[] { "owned", "stale-registry", "world", "native-items", "biomes" }) {
            String name = "building-pieces-bop-" + fixture + "-1.12.2";
            JsonObject metadata = json(FIXTURES.resolve("worlds/" + name + ".json")).getAsJsonObject();

            assertEquals(metadata.get("archive_sha256").getAsString(),
                    digest(FIXTURES.resolve("worlds/" + name + ".zip")));
            assertTrue(metadata.get("source_world_unchanged").getAsBoolean());
            assertEquals(1500, metadata.get("basket_item_units").getAsInt());
            assertEquals(8, metadata.getAsJsonArray("baskets").size());
        }

        JsonObject biomes = json(FIXTURES.resolve("worlds/building-pieces-bop-biomes-1.12.2.json")).getAsJsonObject()
                .getAsJsonObject("manifest");

        assertEquals(73, biomes.getAsJsonArray("biomes").size());
        assertEquals(1096, biomes.getAsJsonArray("stacks").size());
        assertEquals(1741, biomes.getAsJsonArray("blocks").size());
    }

    private String key(JsonObject row) {
        return row.get("id").getAsString() + "/" + row.get("meta").getAsInt();
    }

    private JsonElement json(Path path) throws Exception {
        return new JsonParser().parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
    }

    private String digest(Path path) throws Exception {
        StringBuilder result = new StringBuilder();

        for (byte value : MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))) {
            result.append(String.format(Locale.ROOT, "%02X", value));
        }

        return result.toString();
    }
}
