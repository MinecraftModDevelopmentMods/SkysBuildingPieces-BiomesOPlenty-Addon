package zone.moddev.mc.skysbuildingpiecesbop.probe;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import zone.moddev.mc.skysbuildingpiecesbop.LegacyItemRecovery;

/** Exercises the early callback with the real previous release's exported native states. */
final class NamedRecoveryChecks {
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger();

    private NamedRecoveryChecks() {
    }

    static void run() throws Exception {
        JsonObject source = read("/fixtures/bop-1.14.4-native-catalogue.json");
        JsonObject mappings = read("/assets/skysbuildingpiecesbop/catalogue/named_legacy.json");
        JsonObject blocks = mappings.getAsJsonObject("blocks");
        int states = 0;
        int rejected = 0;

        for (JsonElement value : source.getAsJsonArray("states")) {
            JsonObject original = value.getAsJsonObject();
            String name = original.get("id").getAsString();

            if (!blocks.has(name)) {
                continue;
            }

            CompoundTag saved = new CompoundTag();
            CompoundTag properties = new CompoundTag();

            saved.putString("Name", name);
            original.getAsJsonObject("properties").entrySet()
                    .forEach(entry -> properties.putString(entry.getKey(), entry.getValue().getAsString()));
            saved.put("Properties", properties);

            CompoundTag root = palette(saved);
            CompoundTag before = root.copy();
            boolean doubleSlab = properties.getString("type").equals("double");

            if (doubleSlab && properties.getString("waterlogged").equals("true")) {
                try {
                    new LegacyItemRecovery().fixTagCompound(root);
                    throw new AssertionError("An impossible wet double slab lost its water silently");
                } catch (IllegalStateException expected) {
                    require(root.equals(before), "Rejected recovery changed the source tag");
                    rejected++;
                }
                continue;
            }

            new LegacyItemRecovery().fixTagCompound(root);

            CompoundTag result = state(root);
            BlockState actual = NbtUtils.readBlockState(result);
            String target = doubleSlab ? "minecraft:birch_planks" : blocks.get(name).getAsString();

            require(actual.getBlock() != Blocks.AIR && actual.getBlock().getRegistryName().toString().equals(target),
                    "Named block target changed: " + saved + " -> " + result);
            if (!doubleSlab) {
                for (String key : properties.getAllKeys()) {
                    require(properties.getString(key).equals(result.getCompound("Properties").getString(key)),
                            "Named block property lost: " + key + " / " + saved + " -> " + result);
                }
            }

            CompoundTag once = root.copy();

            new LegacyItemRecovery().fixTagCompound(root);
            require(root.equals(once), "Named block conversion repeated");
            states++;
        }

        int items = 0;

        for (JsonElement value : source.getAsJsonArray("items")) {
            String id = value.getAsString();

            if (!blocks.has(id) && !mappings.getAsJsonObject("items").has(id)) {
                continue;
            }

            String target = mappings.getAsJsonObject("items").has(id)
                    ? mappings.getAsJsonObject("items").get(id).getAsString() : blocks.get(id).getAsString();
            CompoundTag stack = new CompoundTag();
            CompoundTag tag = new CompoundTag();

            stack.putString("id", id);
            stack.putByte("Count", (byte) 37);
            stack.putByte("Slot", (byte) 11);
            tag.putString("custom_data", "Keep this exactly");
            stack.put("tag", tag);

            CompoundTag expected = stack.copy();

            expected.putString("id", target);
            new LegacyItemRecovery().fixTagCompound(stack);
            require(stack.equals(expected), "Named stack lost its count, slot or custom data: " + id);
            items++;
        }

        CompoundTag malformed = palette(new CompoundTag());
        CompoundTag invalid = state(malformed);

        invalid.putString("Name", "biomesoplenty:ethereal_stairs");
        invalid.put("Properties", new CompoundTag());
        invalid.getCompound("Properties").putString("unsupported_shape", "true");

        CompoundTag good = new CompoundTag();

        good.putString("Name", "biomesoplenty:ethereal_planks");
        malformed.getCompound("Level").getList("Sections", 10).getCompound(0).getList("Palette", 10).add(0, good);

        CompoundTag before = malformed.copy();

        try {
            new LegacyItemRecovery().fixTagCompound(malformed);
            throw new AssertionError("An unsupported named property was discarded");
        } catch (IllegalStateException expected) {
            require(malformed.equals(before), "Failed named conversion changed its source");
        }

        require(items == 20 && states > 300 && rejected > 0, "Named coverage is incomplete: items=" + items + " states=" + states + " rejected=" + rejected);
        biomeSnapshotControls();
        LOGGER.info("BOP_NAMED_RECOVERY_PASS states=" + states + " items=" + items
                + " invalid_wet_double_refusals=" + rejected + " properties=true counts=true atomic=true");
    }

    private static void biomeSnapshotControls() throws Exception {
        Class<?> recovery = Class.forName("zone.moddev.mc.skysbuildingpiecesbop.NamedBopRecovery");
        java.lang.reflect.Field snapshots = recovery.getDeclaredField("biomeSnapshots");

        snapshots.setAccessible(true);

        Object savedSnapshots = snapshots.get(null);
        java.util.NavigableMap<Integer, java.util.Map<Integer, String>> testSnapshots = new java.util.TreeMap<>();

        testSnapshots.put(1976, java.util.Collections.singletonMap(600, "biomesoplenty:undergrowth"));
        try {
            snapshots.set(null, testSnapshots);

            CompoundTag source = biomeChunk(1976, 256);
            CompoundTag original = source.copy();

            new LegacyItemRecovery().fixTagCompound(source);

            net.minecraft.world.level.biome.Biome target = net.minecraftforge.fmllegacy.server.ServerLifecycleHooks.getCurrentServer().registryAccess()
                    .registryOrThrow(net.minecraft.core.Registry.BIOME_REGISTRY)
                    .get(new net.minecraft.resources.ResourceLocation("biomesoplenty:undergrowth"));
            int targetId = net.minecraftforge.fmllegacy.server.ServerLifecycleHooks.getCurrentServer().registryAccess()
                            .registryOrThrow(net.minecraft.core.Registry.BIOME_REGISTRY).getId(target);

            require(source.getCompound("Level").getIntArray("Biomes")[0] == targetId,
                    "Saved biome identity was not resolved through the current registry");
            require(original.getCompound("Level").getIntArray("Biomes")[0] == 600,
                    "Biome recovery changed an independent source copy");

            CompoundTag once = source.copy();

            new LegacyItemRecovery().fixTagCompound(source);
            require(source.equals(once), "A biome table was applied twice before vanilla data fixing");

            for (int version : new int[] { 1343, net.minecraft.SharedConstants.getCurrentVersion().getWorldVersion() }) {
                CompoundTag excluded = biomeChunk(version, 256);
                CompoundTag before = excluded.copy();

                new LegacyItemRecovery().fixTagCompound(excluded);
                require(excluded.equals(before), "Named recovery changed numeric or already-current biome data");
            }

            CompoundTag malformed = biomeChunk(1976, 3);
            CompoundTag before = malformed.copy();

            try {
                new LegacyItemRecovery().fixTagCompound(malformed);
                throw new AssertionError("Malformed biome storage was accepted");
            } catch (IllegalStateException expected) {
                require(malformed.equals(before), "Rejected biome storage changed its source");
            }
        } finally {
            snapshots.set(null, savedSnapshots);
        }

        java.lang.reflect.Method resolve = recovery.getDeclaredMethod("resolveBiomes", CompoundTag.class);

        resolve.setAccessible(true);
        for (boolean duplicateName : new boolean[] { false, true }) {
            CompoundTag registry = new CompoundTag();
            ListTag identities = new ListTag();

            for (int index = 0; index < 2; index++) {
                CompoundTag entry = new CompoundTag();

                entry.putString("K", duplicateName ? "minecraft:plains" : "minecraft:biome_" + index);
                entry.putInt("V", duplicateName ? index : 42);
                identities.add(entry);
            }
            registry.put("ids", identities);
            try {
                resolve.invoke(null, registry);
                throw new AssertionError("Ambiguous saved biome identities were accepted");
            } catch (java.lang.reflect.InvocationTargetException expected) {
                require(expected.getCause() instanceof IllegalStateException,
                        "Malformed registry failed for an unrelated reason");
            }
        }
        LOGGER.info("BOP_NAMED_BIOME_CONTROLS_PASS scopes=2 duplicate_refusals=2 atomic=true idempotent=true");
    }

    private static CompoundTag biomeChunk(int version, int cells) {
        CompoundTag root = new CompoundTag();
        CompoundTag level = new CompoundTag();
        int[] biomes = new int[cells];

        java.util.Arrays.fill(biomes, 600);
        root.putInt("DataVersion", version);
        level.putIntArray("Biomes", biomes);
        root.put("Level", level);
        return root;
    }

    private static CompoundTag palette(CompoundTag saved) {
        CompoundTag root = new CompoundTag();
        CompoundTag level = new CompoundTag();
        CompoundTag section = new CompoundTag();
        ListTag sections = new ListTag();
        ListTag states = new ListTag();

        states.add(saved);
        section.put("Palette", states);
        sections.add(section);
        level.put("Sections", sections);
        root.put("Level", level);
        return root;
    }

    private static CompoundTag state(CompoundTag root) {
        return root.getCompound("Level").getList("Sections", 10).getCompound(0).getList("Palette", 10)
                .getCompound(0);
    }

    private static JsonObject read(String resource) throws Exception {
        Class<?> owner = resource.startsWith("/assets/")
                ? zone.moddev.mc.skysbuildingpiecesbop.RetiredRocks.class : NamedRecoveryChecks.class;
        try (InputStreamReader reader = new InputStreamReader(java.util.Objects.requireNonNull(
                owner.getResourceAsStream(resource), "Missing verification resource: " + resource),
                StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, JsonObject.class);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
