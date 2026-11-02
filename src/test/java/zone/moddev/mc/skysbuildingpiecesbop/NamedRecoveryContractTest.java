package zone.moddev.mc.skysbuildingpiecesbop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NamedRecoveryContractTest {
    @Test
    void productionAndBuildOnlyResourcesUseTheTargetPackFormat() throws Exception {
        try (java.util.stream.Stream<java.nio.file.Path> files = Files.walk(Paths.get("src"))) {
            java.util.List<java.nio.file.Path> packs = files
                    .filter(path -> path.getFileName().toString().equals("pack.mcmeta"))
                    .filter(path -> !path.toString().contains("testFixtures"))
                    .collect(java.util.stream.Collectors.toList());

            assertEquals(Set.of(Paths.get("src/main/resources/pack.mcmeta"),
                    Paths.get("src/bootstrapTest/resources/pack.mcmeta"),
                    Paths.get("src/compatibilityTest/resources/pack.mcmeta")), new HashSet<>(packs));
            for (java.nio.file.Path pack : packs) {
                assertEquals(7, read(pack.toString()).getAsJsonObject("pack").get("pack_format").getAsInt(),
                        pack.toString());
            }
        }
    }

    @Test
    void everyRemovedNativeBlockAndItemHasAnExplicitTarget() throws Exception {
        JsonObject earlier = read("src/testFixtures/bop-1.14.4-native-catalogue.json");
        JsonObject previous = read("src/testFixtures/bop-1.15.2-native-catalogue.json");
        JsonObject intervening = read("src/testFixtures/bop-1.16.5-native-catalogue.json");
        JsonObject target = read("src/testFixtures/bop-1.17.1-native-catalogue.json");
        JsonObject mapping = read("src/main/resources/assets/skysbuildingpiecesbop/catalogue/named_legacy.json");
        Set<String> removedBlocks = identities(earlier.getAsJsonArray("states"), "id");
        removedBlocks.addAll(identities(previous.getAsJsonArray("blocks"), "id"));
        removedBlocks.addAll(identities(intervening.getAsJsonArray("blocks"), "id"));
        removedBlocks.removeAll(identities(target.getAsJsonArray("blocks"), "id"));
        assertEquals(23, removedBlocks.size());
        assertEquals(removedBlocks, keys(mapping.getAsJsonObject("blocks")));

        Set<String> removedItems = identities(earlier.getAsJsonArray("items"), null);
        removedItems.addAll(identities(previous.getAsJsonArray("items"), null));
        removedItems.addAll(identities(intervening.getAsJsonArray("items"), null));
        removedItems.removeAll(identities(target.getAsJsonArray("items"), null));
        Set<String> itemMappings = new HashSet<>(keys(mapping.getAsJsonObject("blocks")));
        itemMappings.addAll(keys(mapping.getAsJsonObject("items")));
        assertEquals(24, removedItems.size());
        assertEquals(removedItems, itemMappings);
    }

    @Test
    void retiredBiomesKeepTheirEndOrNetherEnvironment() throws Exception {
        JsonObject earlier = read("src/testFixtures/bop-1.14.4-native-catalogue.json");
        JsonObject previous = read("src/testFixtures/bop-1.15.2-native-catalogue.json");
        JsonObject intervening = read("src/testFixtures/bop-1.16.5-native-catalogue.json");
        JsonObject target = read("src/testFixtures/bop-1.17.1-native-catalogue.json");
        JsonObject mapping = read("src/main/resources/assets/skysbuildingpiecesbop/catalogue/named_legacy.json")
                .getAsJsonObject("biomes");
        Set<String> removed = identities(earlier.getAsJsonArray("biomes"), "id");
        removed.addAll(identities(previous.getAsJsonArray("biomes"), null));
        removed.addAll(identities(intervening.getAsJsonArray("biomes"), null));
        removed.removeAll(identities(target.getAsJsonArray("biomes"), null));
        assertEquals(36, removed.size());
        assertEquals(removed, keys(mapping));
        assertEquals("minecraft:end_midlands", mapping.get("biomesoplenty:dead_reef").getAsString());
        assertEquals("minecraft:end_highlands", mapping.get("biomesoplenty:ethereal_forest").getAsString());
        assertEquals("biomesoplenty:undergrowth", mapping.get("biomesoplenty:fungi_forest").getAsString());
        assertEquals("biomesoplenty:undergrowth", mapping.get("biomesoplenty:undergarden").getAsString());
        assertEquals("minecraft:nether_wastes", mapping.get("biomesoplenty:glowstone_grotto").getAsString());
        assertEquals("minecraft:basalt_deltas", mapping.get("biomesoplenty:ashen_inferno").getAsString());
    }

    @Test
    void everyPreFlatteningBopBiomeResolvesInTheCurrentCatalogue() throws Exception {
        JsonObject fixture = read("src/testFixtures/worlds/building-pieces-bop-biomes-1.12.2.json")
                .getAsJsonObject("manifest");
        JsonObject aliases = read("src/main/resources/assets/skysbuildingpiecesbop/catalogue/legacy_biomes.json");
        Set<String> current = identities(read("src/testFixtures/bop-1.17.1-native-catalogue.json")
                .getAsJsonArray("biomes"), null);

        assertEquals(73, fixture.getAsJsonArray("biomes").size());
        for (com.google.gson.JsonElement element : fixture.getAsJsonArray("biomes")) {
            String source = element.getAsJsonObject().get("id").getAsString();
            String target = aliases.has(source) ? aliases.get(source).getAsString() : source;

            if (target.startsWith("biomesoplenty:")) {
                assertTrue(current.contains(target), "Missing pre-flattening biome target: " + source + " -> " + target);
            }
        }
    }

    @Test
    void registryLookingCustomNamesAndOrdinaryEntityTagsStayUntouched() {
        CompoundTag root = new CompoundTag();
        CompoundTag display = new CompoundTag();

        display.putString("Name", "biomesoplenty:ethereal_planks");
        root.put("display", display);
        root.putString("id", "biomesoplenty:ethereal_boat");
        root.putString("unrelated", "biomesoplenty:barley");

        CompoundTag before = root.copy();

        assertFalse(NamedBopRecovery.relevant(root));
        NamedBopRecovery.rewrite(root);
        assertEquals(before, root);
    }

    private Set<String> keys(JsonObject value) {
        return value.entrySet().stream().map(java.util.Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    private JsonObject read(String path) throws Exception {
        return new JsonParser().parse(new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private Set<String> identities(JsonArray values, String key) {
        Set<String> result = new HashSet<>();

        values.forEach(value -> result.add(key == null ? value.getAsString()
                : value.getAsJsonObject().get(key).getAsString()));
        return result;
    }
}

