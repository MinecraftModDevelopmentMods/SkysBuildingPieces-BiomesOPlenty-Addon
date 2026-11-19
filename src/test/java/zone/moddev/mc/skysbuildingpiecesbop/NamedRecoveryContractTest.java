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
                JsonObject metadata = read(pack.toString()).getAsJsonObject("pack");
                assertEquals(101, metadata.get("max_format").getAsInt(), pack.toString());
                JsonArray minimum = metadata.getAsJsonArray("min_format");
                assertEquals(101, minimum.get(0).getAsInt(), pack.toString());
                assertEquals(1, minimum.get(1).getAsInt(), pack.toString());
            }
        }
    }

    @Test
    void everyRemovedNativeBlockAndItemHasAnExplicitTarget() throws Exception {
        JsonObject earlier = read("src/testFixtures/bop-1.14.4-native-catalogue.json");
        JsonObject previous = read("src/testFixtures/bop-1.15.2-native-catalogue.json");
        JsonObject intervening = read("src/testFixtures/bop-1.16.5-native-catalogue.json");
        JsonObject last = read("src/testFixtures/bop-1.17.1-native-catalogue.json");
        JsonObject source18 = read("src/testFixtures/bop-1.18.2-native-catalogue.json");
        JsonObject source19 = read("src/testFixtures/bop-1.19.4-native-catalogue.json");
        JsonObject target = read("src/testFixtures/bop-1.20.1-native-catalogue.json");
        JsonObject mapping = read("src/main/resources/assets/skysbuildingpiecesbop/catalogue/named_legacy.json");
        Set<String> removedBlocks = identities(earlier.getAsJsonArray("states"), "id");
        removedBlocks.addAll(identities(previous.getAsJsonArray("blocks"), "id"));
        removedBlocks.addAll(identities(intervening.getAsJsonArray("blocks"), "id"));
        removedBlocks.addAll(identities(last.getAsJsonArray("blocks"), "id"));
        removedBlocks.addAll(identities(source18.getAsJsonArray("blocks"), "id"));
        removedBlocks.addAll(identities(source19.getAsJsonArray("blocks"), "id"));
        removedBlocks.removeAll(identities(target.getAsJsonArray("blocks"), "id"));
        assertEquals(65, removedBlocks.size());
        assertEquals(removedBlocks, keys(mapping.getAsJsonObject("blocks")));

        Set<String> removedItems = identities(earlier.getAsJsonArray("items"), null);
        removedItems.addAll(identities(previous.getAsJsonArray("items"), null));
        removedItems.addAll(identities(intervening.getAsJsonArray("items"), null));
        removedItems.addAll(identities(last.getAsJsonArray("items"), null));
        removedItems.addAll(identities(source18.getAsJsonArray("items"), null));
        removedItems.addAll(identities(source19.getAsJsonArray("items"), null));
        removedItems.removeAll(identities(target.getAsJsonArray("items"), null));
        Set<String> itemMappings = new HashSet<>(keys(mapping.getAsJsonObject("blocks")));
        assertTrue(itemMappings.remove("biomesoplenty:cherry_wall_sign"));
        itemMappings.addAll(keys(mapping.getAsJsonObject("items")));
        assertTrue(itemMappings.remove("biomesoplenty:potted_rainbow_birch_sapling"));
        assertEquals(70, removedItems.size());
        assertEquals(removedItems, itemMappings);
    }

    @Test
    void everyNewlyRemovedIdentityHasALiveReplacement() throws Exception {
        var before = read("src/testFixtures/bop-1.21.1-native-identities.json");
        var target = read("src/testFixtures/bop-1.21.11-native-identities.json");
        var recovery = read("src/main/resources/assets/skysbuildingpiecesbop/catalogue/named_legacy.json");
        var blocks = recovery.getAsJsonObject("renamed_1_21_11");

        var removedBlocks = removed(before, target, "blocks");
        assertEquals(13, removedBlocks.size());
        assertEquals(removedBlocks, keys(blocks));
        requirePresentTargets(blocks, target, "blocks");

        var removedItems = removed(before, target, "items");
        assertEquals(13, removedItems.size());
        assertEquals(removedBlocks, removedItems);
        var items = blocks.deepCopy();
        var itemOnly = recovery.getAsJsonObject("items");
        for (String item : removedItems) {
            if (itemOnly.has(item)) items.add(item, itemOnly.get(item));
        }
        assertEquals("minecraft:birch_sapling", items.get("biomesoplenty:potted_rainbow_birch_sapling").getAsString());
        requirePresentTargets(items, target, "items");

        var removedBiomes = removed(before, target, "biomes");
        assertEquals(Set.of("biomesoplenty:aspen_glade", "biomesoplenty:grassland",
                "biomesoplenty:jacaranda_glade"), removedBiomes);
        for (String format : new String[] { "biomes", "section_biomes" }) {
            var replacements = recovery.getAsJsonObject(format);
            assertTrue(keys(replacements).containsAll(removedBiomes));
            requirePresentTargets(replacements, target, "biomes");
        }
    }

    private static Set<String> removed(JsonObject before, JsonObject target, String registry) {
        var names = identities(before.getAsJsonArray(registry), null);
        names.removeAll(identities(target.getAsJsonArray(registry), null));
        return names;
    }

    private static void requirePresentTargets(JsonObject aliases, JsonObject target, String registry) {
        var present = identities(target.getAsJsonArray(registry), null);
        for (var alias : aliases.entrySet()) {
            String id = alias.getValue().getAsString();
            assertTrue(id.startsWith("minecraft:") || present.contains(id), alias.toString());
        }
    }

    @Test
    void retiredBiomesKeepTheirEndOrNetherEnvironment() throws Exception {
        JsonObject earlier = read("src/testFixtures/bop-1.14.4-native-catalogue.json");
        JsonObject previous = read("src/testFixtures/bop-1.15.2-native-catalogue.json");
        JsonObject intervening = read("src/testFixtures/bop-1.16.5-native-catalogue.json");
        JsonObject last = read("src/testFixtures/bop-1.17.1-native-catalogue.json");
        JsonObject source18 = read("src/testFixtures/bop-1.18.2-native-catalogue.json");
        JsonObject source19 = read("src/testFixtures/bop-1.19.4-native-catalogue.json");
        JsonObject target = read("src/testFixtures/bop-1.20.1-native-catalogue.json");
        JsonObject mapping = read("src/main/resources/assets/skysbuildingpiecesbop/catalogue/named_legacy.json")
                .getAsJsonObject("biomes");
        Set<String> removed = identities(earlier.getAsJsonArray("biomes"), "id");
        removed.addAll(identities(previous.getAsJsonArray("biomes"), null));
        removed.addAll(identities(intervening.getAsJsonArray("biomes"), null));
        removed.addAll(identities(last.getAsJsonArray("biomes"), null));
        removed.addAll(identities(source18.getAsJsonArray("biomes"), null));
        removed.addAll(identities(source19.getAsJsonArray("biomes"), null));
        removed.removeAll(identities(target.getAsJsonArray("biomes"), null));
        assertTrue(removed.size() > 60);
        assertTrue(keys(mapping).containsAll(removed), "Every removed biome needs an explicit replacement");
        Set<String> current = identities(read("src/testFixtures/bop-1.21.11-native-identities.json")
                .getAsJsonArray("biomes"), null);
        mapping.entrySet().forEach(entry -> {
            if (entry.getValue().getAsString().startsWith("biomesoplenty:")) {
                assertTrue(current.contains(entry.getValue().getAsString()), entry.toString());
            }
        });
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
        Set<String> current = identities(read("src/testFixtures/bop-1.21.11-native-identities.json")
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
    void pendingBlockTicksKeepTheirPositionAndDelayDuringRecovery() throws Exception {
        CompoundTag chunk = net.minecraft.nbt.TagParser.parseCompoundFully("""
                {DataVersion:3955,xPos:0,zPos:0,block_ticks:[
                  {i:"biomesoplenty:origin_leaves",p:0,t:19,x:3,y:180,z:6},
                  {i:"minecraft:birch_leaves",p:1,t:22,x:2,y:180,z:8}
                ]}
                """);
        CompoundTag expected = chunk.copy();
        expected.getListOrEmpty("block_ticks").getCompoundOrEmpty(0)
                .putString("i", "biomesoplenty:origin_oak_leaves");

        assertTrue(NamedBopRecovery.relevant(chunk));
        NamedBopRecovery.rewrite(chunk);
        assertEquals(expected, chunk);
        assertFalse(NamedBopRecovery.relevant(chunk));

        CompoundTag unrelated = net.minecraft.nbt.TagParser.parseCompoundFully("""
                {block_ticks:[{i:"biomesoplenty:origin_leaves",t:19}]}
                """);
        CompoundTag control = unrelated.copy();
        assertFalse(NamedBopRecovery.relevant(unrelated));
        NamedBopRecovery.rewrite(unrelated);
        assertEquals(control, unrelated);
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

    private static Set<String> identities(JsonArray values, String key) {
        Set<String> result = new HashSet<>();

        values.forEach(value -> result.add(key == null ? value.getAsString()
                : value.getAsJsonObject().get(key).getAsString()));
        return result;
    }
}

