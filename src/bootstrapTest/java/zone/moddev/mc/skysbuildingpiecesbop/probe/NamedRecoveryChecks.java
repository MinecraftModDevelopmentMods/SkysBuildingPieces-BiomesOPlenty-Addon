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
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtUtils;
import zone.moddev.mc.skysbuildingpiecesbop.LegacyItemRecovery;

/** Exercises the early callback with the real previous release's exported native states. */
final class NamedRecoveryChecks {
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger();

    private NamedRecoveryChecks() {
    }

    static void nativeBiomeDatafix(net.minecraft.server.MinecraftServer server) throws Exception {
        JsonObject receipt = read("/fixtures/biomes/saved-bop-1.15.2.json");
        byte[] bytes;
        try (java.io.InputStream input = NamedRecoveryChecks.class.getResourceAsStream(
                "/fixtures/biomes/saved-bop-1.15.2.nbt")) {
            bytes = input.readAllBytes();
        }
        require(java.util.HexFormat.of().withUpperCase().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(bytes))
                .equals(receipt.get("source_chunk_sha256").getAsString()), "Saved biome fixture changed");
        CompoundTag source = net.minecraft.nbt.NbtIo.readCompressed(new java.io.ByteArrayInputStream(bytes), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        CompoundTag original = source.copy();
        java.lang.reflect.Field snapshots = Class.forName(
                "zone.moddev.mc.skysbuildingpiecesbop.NamedBopRecovery").getDeclaredField("biomeSnapshots");
        snapshots.setAccessible(true);
        Object previous = snapshots.get(null);
        java.util.Map<Integer, String> table = new java.util.LinkedHashMap<>();
        receipt.getAsJsonObject("source_ids").entrySet().forEach(entry ->
                table.put(Integer.parseInt(entry.getKey()), receipt.get("target").getAsString()));
        java.util.NavigableMap<Integer, java.util.Map<Integer, String>> saved = new java.util.TreeMap<>();
        saved.put(source.getIntOr("DataVersion", 0), table);
        java.nio.file.Path temporary = java.nio.file.Files.createTempDirectory(
                java.nio.file.Path.of("."), "named-biome-fixer-");
        try (net.minecraft.world.level.chunk.storage.SimpleRegionStorage storage =
                new net.minecraft.world.level.chunk.storage.SimpleRegionStorage(new net.minecraft.world.level.chunk.storage.RegionStorageInfo("test", net.minecraft.world.level.Level.OVERWORLD, "chunk"), temporary, server.getFixerUpper(), false, net.minecraft.util.datafix.DataFixTypes.CHUNK)) {
            snapshots.set(null, saved);
            CompoundTag fixed = storage.upgradeChunkTag(source.copy(), 0, BopProbeItems.overworldUpgradeContext());
            ListTag sections = fixed.getListOrEmpty("sections");
            require(sections.size() >= 16, "Native chunk conversion did not produce biome sections");
            int cells = 0;
            for (Tag entry : sections) {
                CompoundTag section = (CompoundTag) entry;
                int height = section.getByteOr("Y", (byte) 0);
                if (height < 0 || height >= 16) {
                    continue;
                }
                ListTag palette = section.getCompoundOrEmpty("biomes").getListOrEmpty("palette");
                require(palette.size() == 1 && palette.getStringOr(0, "").equals(receipt.get("target").getAsString()),
                        "Saved BOP biome was lost through native chunk fixing: section=" + height + " " + palette);
                cells += 64;
            }
            require(cells == receipt.get("cells").getAsInt(), "Incomplete saved biome volume check");
            require(source.equals(original), "Native recovery changed the independent source tag");
            LOGGER.info("BOP_NATIVE_NAMED_BIOME_FIXER_PASS cells={} source=1.15.2 runtime={}",
                    cells, net.minecraft.SharedConstants.getCurrentVersion().name());
        } finally {
            snapshots.set(null, previous);
        }
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
            boolean doubleSlab = properties.getStringOr("type", "").equals("double");

            if (doubleSlab && properties.getStringOr("waterlogged", "").equals("true")) {
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
            BlockState actual = NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK, result);
            String target = blocks.get(name).getAsString();
            if (doubleSlab) {
                target = switch (name) {
                    case "biomesoplenty:mud_brick_slab" -> "minecraft:mud_bricks";
                    case "biomesoplenty:cherry_slab" -> "minecraft:cherry_planks";
                    default -> "minecraft:birch_planks";
                };
            }

            require(actual.getBlock() != Blocks.AIR && net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(actual.getBlock()).toString().equals(target),
                    "Named block target changed: " + saved + " -> " + result);
            if (!doubleSlab) {
                for (String key : properties.keySet()) {
                    String expectedValue = properties.getStringOr(key, "");
                    if (name.equals("biomesoplenty:mud_brick_wall")
                            && java.util.Set.of("north", "south", "east", "west").contains(key)) {
                        expectedValue = expectedValue.equals("true") ? "low" : "none";
                    }
                    require(expectedValue.equals(result.getCompoundOrEmpty("Properties").getStringOr(key, "")),
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
        invalid.getCompoundOrEmpty("Properties").putString("unsupported_shape", "true");

        CompoundTag good = new CompoundTag();

        good.putString("Name", "biomesoplenty:ethereal_planks");
        malformed.getCompoundOrEmpty("Level").getListOrEmpty("Sections").getCompoundOrEmpty(0).getListOrEmpty("Palette").add(0, good);

        CompoundTag before = malformed.copy();

        try {
            new LegacyItemRecovery().fixTagCompound(malformed);
            throw new AssertionError("An unsupported named property was discarded");
        } catch (IllegalStateException expected) {
            require(malformed.equals(before), "Failed named conversion changed its source");
        }

        require(items == 58 && states > 300 && rejected > 0, "Named coverage is incomplete: items=" + items + " states=" + states + " rejected=" + rejected);
        biomeSnapshotControls();
        nativeBiomeDatafix(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());
        removedRoots();
        LOGGER.info("BOP_NAMED_RECOVERY_PASS states=" + states + " items=" + items
                + " invalid_wet_double_refusals=" + rejected + " properties=true counts=true atomic=true");
    }

    private static void removedRoots() throws Exception {
        JsonObject catalogue = read("/fixtures/bop-1.17.1-native-catalogue.json");
        JsonObject mappings = read("/assets/skysbuildingpiecesbop/catalogue/named_legacy.json");
        int checked = 0;
        for (JsonElement entry : catalogue.getAsJsonArray("blocks")) {
            JsonObject block = entry.getAsJsonObject();
            String source = block.get("id").getAsString();
            if (!source.equals("biomesoplenty:mangrove_root") && !source.equals("biomesoplenty:tree_roots")
                    && !source.equals("biomesoplenty:tree_roots_stem")) {
                continue;
            }
            for (JsonElement variant : block.getAsJsonArray("states")) {
                CompoundTag state = new CompoundTag();
                CompoundTag properties = new CompoundTag();
                state.putString("Name", source);
                variant.getAsJsonObject().entrySet().forEach(property ->
                        properties.putString(property.getKey(), property.getValue().getAsString()));
                state.put("Properties", properties);
                CompoundTag root = palette(state);
                new LegacyItemRecovery().fixTagCompound(root);
                CompoundTag result = state(root);
                require(result.getStringOr("Name", "").equals(mappings.getAsJsonObject("blocks").get(source).getAsString()),
                        "Removed root replacement changed: " + source);
                for (String property : properties.keySet()) {
                    require(properties.getStringOr(property, "").equals(result.getCompoundOrEmpty("Properties").getStringOr(property, "")),
                            "A removed root lost " + property + ": " + result);
                }
                CompoundTag once = root.copy();
                new LegacyItemRecovery().fixTagCompound(root);
                require(root.equals(once), "Root recovery repeated");
                checked++;
            }
            CompoundTag stack = new CompoundTag();
            stack.putString("id", source);
            stack.putByte("Count", (byte) 37);
            CompoundTag custom = new CompoundTag();
            custom.putString("Keep", "Stored root data");
            stack.put("tag", custom);
            new LegacyItemRecovery().fixTagCompound(stack);
            String target = source.equals("biomesoplenty:tree_roots_stem") ? "minecraft:weeping_vines"
                    : mappings.getAsJsonObject("blocks").get(source).getAsString();
            require(stack.getStringOr("id", "").equals(target) && stack.getByteOr("Count", (byte) 0) == 37
                    && stack.getCompoundOrEmpty("tag").equals(custom), "A removed root item lost its data");
            require(!BopProbeItems.read(stack).isEmpty(), "A root became an itemless block");
        }
        require(checked == 31, "Incomplete removed-root state coverage: " + checked);
        net.minecraft.server.level.ServerLevel world = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()
                .getLevel(net.minecraft.world.level.Level.OVERWORLD);
        net.minecraft.core.BlockPos position = new net.minecraft.core.BlockPos(112, 180, 112);
        for (boolean water : new boolean[] {false, true}) {
            world.setBlock(position.below(), Blocks.ROOTED_DIRT.defaultBlockState(), 18);
            BlockState lower = Blocks.SMALL_DRIPLEAF.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, water);
            BlockState upper = lower.setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
            world.setBlock(position, lower, 18);
            world.setBlock(position.above(), upper, 18);
            require(lower.canSurvive(world, position) && upper.canSurvive(world, position.above()),
                    "A recovered mangrove plant lost its support, water=" + water);
        }
        world.setBlock(position.above(), Blocks.OAK_LOG.defaultBlockState(), 18);
        world.setBlock(position, Blocks.WEEPING_VINES_PLANT.defaultBlockState(), 18);
        world.setBlock(position.below(), Blocks.WEEPING_VINES.defaultBlockState(), 18);
        require(world.getBlockState(position).canSurvive(world, position)
                && world.getBlockState(position.below()).canSurvive(world, position.below()),
                "A recovered root strand lost its support");
        LOGGER.info("BOP_REMOVED_ROOTS_PASS states=31 item_units=111 water=true support=true idempotent=true");
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

            net.minecraft.world.level.biome.Biome target = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().registryAccess()
                    .lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
                    .getValue(net.minecraft.resources.Identifier.parse("biomesoplenty:undergrowth"));
            int targetId = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().registryAccess()
                            .lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).getId(target);

            require(source.getCompoundOrEmpty("Level").getIntArray("Biomes").orElseThrow()[0] == targetId,
                    "Saved biome identity was not resolved through the current registry");
            require(original.getCompoundOrEmpty("Level").getIntArray("Biomes").orElseThrow()[0] == 600,
                    "Biome recovery changed an independent source copy");

            CompoundTag once = source.copy();

            new LegacyItemRecovery().fixTagCompound(source);
            require(source.equals(once), "A biome table was applied twice before vanilla data fixing");

            for (int version : new int[] { 1343, net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version() }) {
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
        return root.getCompoundOrEmpty("Level").getListOrEmpty("Sections").getCompoundOrEmpty(0).getListOrEmpty("Palette")
                .getCompoundOrEmpty(0);
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
