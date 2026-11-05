package zone.moddev.mc.skysbuildingpiecesbop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldMappingsTest {
    @Test
    void oldBopPathsUseTheCurrentVanillaPathIdentity() throws Exception {
        Set<Integer> variants = new HashSet<>();
        for (JsonElement value : read("src/main/resources/assets/skysbuildingpiecesbop/catalogue/legacy_world_states.json")) {
            JsonObject row = value.getAsJsonObject();
            if (row.get("id").getAsString().equals("biomesoplenty:grass_path")) {
                variants.add(row.get("meta").getAsInt());
                assertEquals("minecraft:dirt_path", row.getAsJsonObject("target").get("id").getAsString());
            }
        }
        assertEquals(new HashSet<>(java.util.Arrays.asList(0, 1, 2)), variants);
    }

    @Test
    void everySourceStateHasOneExplicitMappingOrAnActionableRefusal() throws Exception {
        Map<String, JsonObject> source = new HashMap<>();

        for (JsonElement element : read("src/testFixtures/bop-1.12.2-registry-states.json")) {
            JsonObject row = element.getAsJsonObject();

            source.put(key(row), row);
        }

        Set<String> assigned = new HashSet<>();

        for (String file : new String[] { "legacy_states.json", "legacy_world_states.json" }) {
            for (JsonElement element : read("src/main/resources/assets/skysbuildingpiecesbop/catalogue/" + file)) {
                JsonObject row = element.getAsJsonObject();

                assertTrue(assigned.add(key(row)), "Duplicate mapping: " + row);
                assertNotNull(source.get(key(row)), "Invented source state: " + row);
                assertEquals(source.get(key(row)).get("properties"), row.get("properties"));

                if (row.has("target")) {
                    JsonObject target = row.getAsJsonObject("target");

                    if (target.has("unsupported")) {
                        assertEquals("biomesoplenty:biome_block/0", key(row));
                        assertFalse(target.get("unsupported").getAsString().isEmpty());
                    } else {
                        assertTrue(target.get("id").getAsString().matches("(minecraft|biomesoplenty):[a-z_]+"));
                        assertFalse(target.get("id").getAsString().endsWith(":air"));
                        assertTrue(target.get("properties").isJsonObject());
                    }
                }
            }
        }

        assertEquals(1200, source.size());
        assertEquals(source.keySet(), assigned);
    }

    @Test
    void numericWorldMappingsDoNotTargetRemovedBopBlocks() throws Exception {
        JsonObject nativeCatalogue = new JsonParser().parse(Files.readString(
                Paths.get("src/testFixtures/bop-1.21.11-native-identities.json"))).getAsJsonObject();
        Set<String> current = new HashSet<>();
        for (JsonElement block : nativeCatalogue.getAsJsonArray("blocks")) {
            current.add(block.getAsString());
        }
        for (JsonElement value : read("src/main/resources/assets/skysbuildingpiecesbop/catalogue/legacy_world_states.json")) {
            JsonObject row = value.getAsJsonObject();
            JsonObject target = row.getAsJsonObject("target");
            if (!target.has("id")) continue;
            String identity = target.get("id").getAsString();
            if (row.get("id").getAsString().equals("biomesoplenty:plant_1") && row.get("meta").getAsInt() == 9) {
                assertEquals("biomesoplenty:dead_grass", identity, "The old root must stay one block tall");
            }
            if (identity.startsWith("biomesoplenty:")) {
                assertTrue(current.contains(identity), "Removed numeric-world target: " + row);
            }
        }
    }

    private JsonArray read(String path) throws Exception {
        return new JsonParser().parse(new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8))
                .getAsJsonArray();
    }

    private String key(JsonObject row) {
        return row.get("id").getAsString() + "/" + row.get("meta").getAsInt();
    }
}
