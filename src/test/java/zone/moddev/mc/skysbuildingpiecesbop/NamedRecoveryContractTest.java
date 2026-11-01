package zone.moddev.mc.skysbuildingpiecesbop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.CompoundNBT;
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

            assertEquals(2, packs.size());
            for (java.nio.file.Path pack : packs) {
                assertEquals(5, read(pack.toString()).getAsJsonObject("pack").get("pack_format").getAsInt(),
                        pack.toString());
            }
        }
    }

    @Test
    void everyRemovedNativeBlockAndItemHasAnExplicitTarget() throws Exception {
        JsonObject source = read("src/testFixtures/bop-1.14.4-native-catalogue.json");
        JsonObject target = read("src/testFixtures/bop-1.15.2-native-catalogue.json");
        JsonObject mapping = read("src/main/resources/assets/skysbuildingpiecesbop/catalogue/named_legacy.json");
        Set<String> removedBlocks = identities(source.getAsJsonArray("states"), "id");

        removedBlocks.removeAll(identities(target.getAsJsonArray("blocks"), "id"));
        assertEquals(18, removedBlocks.size());
        assertEquals(removedBlocks, keys(mapping.getAsJsonObject("blocks")));

        Set<String> removedItems = identities(source.getAsJsonArray("items"), null);

        removedItems.removeAll(identities(target.getAsJsonArray("items"), null));

        Set<String> itemMappings = new HashSet<>(keys(mapping.getAsJsonObject("blocks")));

        itemMappings.addAll(keys(mapping.getAsJsonObject("items")));
        assertEquals(19, removedItems.size());
        assertEquals(removedItems, itemMappings);
    }

    @Test
    void retiredBiomesKeepTheirEndOrNetherEnvironment() throws Exception {
        JsonObject source = read("src/testFixtures/bop-1.14.4-native-catalogue.json");
        JsonObject target = read("src/testFixtures/bop-1.15.2-native-catalogue.json");
        JsonObject mapping = read("src/main/resources/assets/skysbuildingpiecesbop/catalogue/named_legacy.json")
                .getAsJsonObject("biomes");
        Set<String> removed = identities(source.getAsJsonArray("biomes"), "id");

        removed.removeAll(identities(target.getAsJsonArray("biomes"), null));
        assertEquals(4, removed.size());
        assertEquals(removed, keys(mapping));
        assertEquals("minecraft:end_midlands", mapping.get("biomesoplenty:dead_reef").getAsString());
        assertEquals("minecraft:end_highlands", mapping.get("biomesoplenty:ethereal_forest").getAsString());
        assertEquals("biomesoplenty:undergarden", mapping.get("biomesoplenty:fungi_forest").getAsString());
        assertEquals("minecraft:nether", mapping.get("biomesoplenty:glowstone_grotto").getAsString());
    }

    @Test
    void everyPreFlatteningBopBiomeResolvesInTheCurrentCatalogue() throws Exception {
        JsonObject fixture = read("src/testFixtures/worlds/building-pieces-bop-biomes-1.12.2.json")
                .getAsJsonObject("manifest");
        JsonObject aliases = read("src/main/resources/assets/skysbuildingpiecesbop/catalogue/legacy_biomes.json");
        Set<String> current = identities(read("src/testFixtures/bop-1.15.2-native-catalogue.json")
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
        CompoundNBT root = new CompoundNBT();
        CompoundNBT display = new CompoundNBT();

        display.putString("Name", "biomesoplenty:ethereal_planks");
        root.put("display", display);
        root.putString("id", "biomesoplenty:ethereal_boat");
        root.putString("unrelated", "biomesoplenty:barley");

        CompoundNBT before = root.copy();

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

