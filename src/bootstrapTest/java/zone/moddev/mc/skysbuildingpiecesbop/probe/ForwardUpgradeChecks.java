package zone.moddev.mc.skysbuildingpiecesbop.probe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.util.Map;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import zone.moddev.mc.skysbuildingpieces.legacy.HistoricalFullBlocks;
import zone.moddev.mc.skysbuildingpieces.legacy.PiecesWorldState;

/** Load every saved position through the real server, including chunks outside spawn. */
final class ForwardUpgradeChecks {
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger();

    private static final JsonObject TARGET_UPGRADES = targetUpgrades();

    private ForwardUpgradeChecks() {
    }

    static void run(MinecraftServer server, File manifestFile) throws Exception {
        JsonObject manifest = read(manifestFile);
        ServerLevel world = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);

        run(world, manifest);
    }

    static void biomesOnly(MinecraftServer server, File manifestFile) throws Exception {
        checkBiomes(server.getLevel(net.minecraft.world.level.Level.OVERWORLD), read(manifestFile));
    }

    private static JsonObject read(File manifestFile) throws Exception {
        try (FileReader reader = new FileReader(manifestFile)) {
            JsonObject manifest = new JsonParser().parse(reader).getAsJsonObject();

            if (manifest.has("manifest")) {
                JsonObject source = manifest;

                manifest = source.getAsJsonObject("manifest");
                if (source.has("source_saved_states")) {
                    manifest.add("source_saved_states", source.get("source_saved_states"));
                }
            }

            return manifest;
        }
    }

    private static void run(ServerLevel world, JsonObject manifest) throws Exception {
        int states = 0;
        java.util.Map<BlockPos, java.util.List<BlockState>> doors = SourceNeighbourChecks.expectedDoors(
                world, TARGET_UPGRADES.getAsJsonObject("blocks"));
        int normalisedDoors = doors.size();
        int nativePlantNormalisations = 0;
        int flowingSamples = 0;
        java.util.Set<BlockPos> verifiedFlows = SourceFluidChecks.verify(world);
        java.util.Map<BlockPos, BlockState> retainedRocks = retainedNamedRocks(manifest);
        java.util.Map<BlockPos, CompoundTag> sourceStates = new java.util.HashMap<>();
        if (manifest.has("source_saved_states")) {
            for (JsonElement value : manifest.getAsJsonArray("source_saved_states")) {
                JsonObject row = value.getAsJsonObject();
                sourceStates.put(position(row), TagParser.parseTag(row.get("state").getAsString()));
            }
        }

        java.util.List<String> blockFailures = new java.util.ArrayList<>();
        for (JsonElement element : manifest.getAsJsonArray("blocks")) {
            try {
                JsonObject row = element.getAsJsonObject();
                BlockPos position = position(row);
                BlockState actual = world.getBlockState(position);
                BlockState expected;

                if (retainedRocks.containsKey(position)) {
                    expected = retainedRocks.get(position);
                } else if (row.has("role") && row.get("role").getAsString().equals("world")) {
                    JsonObject target = new JsonParser().parse(row.getAsJsonObject("target").toString())
                            .getAsJsonObject();

                    target.addProperty("id", currentTarget("blocks", target.get("id").getAsString()));
                    // The numeric source had a single root, before BOP's later two-block mangrove plant.
                    if (!manifest.has("source_saved_states") && row.get("id").getAsString().equals("biomesoplenty:plant_1")
                            && row.get("meta").getAsInt() == 9) {
                        target.addProperty("id", "biomesoplenty:dead_grass");
                    }
                    if (target.get("id").getAsString().equals("biomesoplenty:cattail")
                            && !target.getAsJsonObject("properties").has("half")) {
                        target.addProperty("id", "biomesoplenty:dead_grass");
                    }
                    if (target.get("id").getAsString().equals("biomesoplenty:white_sandstone_wall")
                            || target.get("id").getAsString().equals("biomesoplenty:mud_brick_wall")) {
                        JsonObject properties = target.getAsJsonObject("properties");
                        for (String direction : new String[] {"north", "south", "east", "west"}) {
                            if (!properties.has(direction)) continue;
                            String value = properties.get(direction).getAsString();
                            if (value.equals("true") || value.equals("false")) {
                                properties.addProperty(direction, value.equals("true") ? "low" : "none");
                            }
                        }
                    }
                    expected = zone.moddev.mc.skysbuildingpiecesbop.BopWorldMappings.state(target);
                } else if (row.has("role") || row.has("owned_decoration")) {
                    expected = HistoricalFullBlocks.resolve(row.get("id").getAsString(), row.get("meta").getAsInt());
                } else if (row.get("shape").getAsString().equals("FULL")) {
                    expected = Pieces.nativeState(Catalogue.INSTANCE.materials.get(row.get("material").getAsString()),
                            "full");
                } else {
                    expected = Pieces.state(row.get("material").getAsString(),
                            Shape.valueOf(row.get("shape").getAsString()), row.get("orientation").getAsInt());
                }

                // These older range fixtures include unsupported plants and incomplete tall plants.
                // Keep their saved properties, then use the same native neighbour validation as chunk loading.
                if (manifest.has("source_saved_states")
                        && (expected.getBlock() instanceof net.minecraft.world.level.block.BushBlock
                        || expected.getBlock() instanceof net.minecraft.world.level.block.VineBlock)) {
                    CompoundTag original = sourceStates.get(position);
                    if (original != null) {
                        for (String key : original.getCompound("Properties").getAllKeys()) {
                            var property = expected.getBlock().getStateDefinition().getProperty(key);
                            if (property != null) {
                                expected = sourceProperty(expected, property, original.getCompound("Properties").getString(key));
                            }
                        }
                    }
                    BlockState validated = net.minecraft.world.level.block.Block.updateFromNeighbourShapes(expected, world, position);
                    // Border updates can remain pending. A valid plant still has just one permitted state;
                    // an unsupported fixture plant may be unchanged or have its native update applied.
                    if (actual != expected) {
                        expected = validated;
                        nativePlantNormalisations++;
                    }
                }

                CompoundTag sourceState = sourceStates.get(position);
                if (verifiedFlows.contains(position) && expected.is(net.minecraft.world.level.block.Blocks.WATER)
                        && sourceState != null && sourceState.getString("Name").equals("minecraft:water")
                        && Integer.parseInt(sourceState.getCompound("Properties").getString("level")) > 0) {
                    // The native data fixer preserved this exact source state. Ticking can then move its water.
                    // Source water and waterlogged pieces retain their usual exact-state checks.
                    require(actual.isAir() || actual.is(net.minecraft.world.level.block.Blocks.WATER),
                            "A flowing-water sample became an unrelated block at " + position + ": " + actual);
                    expected = actual;
                    flowingSamples++;
                }

                require(actual.getBlock() == expected.getBlock(),
                        "Wrong saved block at " + position + ": " + actual + ", expected " + expected);

                java.util.List<BlockState> doorStates = doors.remove(position);
                if (doorStates != null) {
                    require(expected.getBlock() == doorStates.get(0).getBlock(), "A source door changed identity");
                    require(doorStates.contains(actual), "A source door matches neither its original state nor native updates at "
                            + position + ": " + actual + ", expected one of " + doorStates);
                    expected = actual;
                }

                // Connections and snow are derived from neighbours. The palette metadata and persisted orientation still
                // have to match exactly.
                for (Map.Entry<net.minecraft.world.level.block.state.properties.Property<?>, Comparable<?>> property : expected.getValues()
                        .entrySet()) {
                    String name = property.getKey().getName();

                    if (name.equals("north") || name.equals("south") || name.equals("east") || name.equals("west")
                            || name.equals("up") || name.equals("down") || name.equals("snowy") || name.equals("in_wall")
                            || name.equals("lowered_support") || name.equals("shape") || name.equals("connections")) {
                        continue;
                    }

                    require(property.getValue().equals(actual.getValues().get(property.getKey())),
                            "Wrong " + name + " at " + position + ": " + actual + ", expected " + expected);
                }

                states++;
            } catch (AssertionError failure) {
                blockFailures.add(failure.getMessage());
            }
        }
        require(blockFailures.isEmpty(), "Saved block mismatches (" + blockFailures.size() + "):\n"
                + String.join("\n", blockFailures.subList(0, Math.min(100, blockFailures.size()))));

        if (flowingSamples > 0) {
            LOGGER.info("BOP_NATIVE_FLOW_UPDATES_PASS dynamic_cells=" + flowingSamples + " datafix_verified=true");
        }
        LOGGER.info("BOP_NATIVE_PLANT_NORMALIZATION_PASS applied=" + nativePlantNormalisations
                + " source_bound=true native_updates=true");
        require(doors.isEmpty(), "Some source door controls were not checked");
        if (normalisedDoors > 0) {
            LOGGER.info("BOP_NATIVE_DOOR_NORMALIZATION_PASS doors=" + normalisedDoors);
        }

        int baskets = 0;
        int units = 0;

        for (JsonElement element : manifest.getAsJsonArray("stacks")) {
            JsonObject row = element.getAsJsonObject();
            BlockPos chestPosition = position(row);

            world.getChunk(chestPosition.getX() >> 4, chestPosition.getZ() >> 4);
            require(world.getBlockEntity(chestPosition) instanceof ChestBlockEntity,
                    "Saved chest is missing at " + chestPosition + ": " + world.getBlockState(chestPosition));

            BlockPos storagePosition = SourceNeighbourChecks.containerPosition(world, chestPosition);
            ChestBlockEntity chest = (ChestBlockEntity) world.getBlockEntity(storagePosition);
            ItemStack actual = chest.getItem(row.get("slot").getAsInt());
            CompoundTag original = TagParser.parseTag(row.get("item").getAsString());

            require(actual.getCount() == original.getByte("Count"), "Saved stack count changed: " + row + ", actual " + actual);

            if (row.has("basket")) {
                require(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(actual.getItem()).toString().equals("skysbuildingpiecesbop:flower_basket"),
                        "Saved basket became another item");

                ListTag contents = actual.getTag().getList("Items", 10);
                int expectedUnits = 0;
                ListTag source = original.getCompound("tag").getList("Items", 10);

                require(contents.size() == source.size(), "A basket lost stored slots: " + row.get("basket"));

                for (int slot = 0; slot < source.size(); slot++) {
                    CompoundTag before = source.getCompound(slot);
                    CompoundTag after = contents.getCompound(slot);

                    require(before.getByte("Slot") == after.getByte("Slot")
                            && before.getByte("Count") == after.getByte("Count"), "Basket slot/count changed");

                    String expectedId = currentItemTarget(plant(before));

                    require(after.getString("id").equals(expectedId),
                            "Basket plant changed: " + before + ", expected " + expectedId + ", actual " + after);
                    expectedUnits += before.getByte("Count");
                }

                units += expectedUnits;
                baskets++;
            } else if (row.has("native_item")) {
                require(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(actual.getItem()).toString()
                        .equals(currentItemTarget(row.get("target_item").getAsString())),
                        "Saved native BOP item changed: " + row + ", actual " + actual);
                require(original.getCompound("tag").equals(actual.getTag()), "Saved native item custom data changed");
            } else {
                ItemStack expected = Pieces.legacyItem(original.getString("id"), original.getShort("Damage"), 1);

                require(expected.getItem() == actual.getItem(), "Saved piece item changed: " + row);
                require(original.getCompound("tag").equals(actual.getTag()), "Saved custom item NBT changed: " + row);
            }
        }

        PiecesWorldState state = PiecesWorldState.get(world);
        BlockPos shovelPosition = new BlockPos(600, 72, 546);

        world.getChunk(shovelPosition.getX() >> 4, shovelPosition.getZ() >> 4);

        ItemStack shovel = ((ChestBlockEntity) world.getBlockEntity(
                SourceNeighbourChecks.containerPosition(world, shovelPosition))).getItem(0);

        require(shovel.getItem() == net.minecraft.world.item.Items.DIAMOND_SHOVEL && shovel.getDamageValue() == 17
                && shovel.getHoverName().getString().equals("A saved slope shovel")
                && net.minecraft.world.item.enchantment.EnchantmentHelper
                        .getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.BLOCK_EFFICIENCY, shovel) == 3,
                "Saved shovel identity, damage, name or enchantment changed");
        require(state.blocks == 17 && state.items == 31 && state.chunks == 7, "Existing vanilla totals changed");

        PiecesWorldState.Totals bop = state.modules.get("biomesoplenty");

        require(bop != null && bop.blocks == 23 && bop.items == 19 && bop.chunks == 1, "Existing BOP totals changed");
        require(baskets == 8 && units == 1500, "Saved basket contract changed");

        if (manifest.has("biomes")) {
            checkBiomes(world, manifest);
        }

        checkSavedRecoveryData(world);

        LOGGER.info("BUILDING_PIECES_BOP_FORWARD_UPGRADE_PASS states=" + states + " baskets=" + baskets
                + " units=" + units);
    }

    static void checkSavedRecoveryData(ServerLevel world) throws Exception {
        File first = new File("first-save/data");

        // Delayed profiles deliberately open additional chunks after the first save.
        if (!first.isDirectory() || Boolean.getBoolean("skysbuildingpieces.bopDelayedBiomes")) {
            return;
        }

        world.getServer().saveAllChunks(true, true, true);
        File current = new File(world.getServer()
                .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(), "data");
        int checked = 0;
        int restoredOptionalSidecars = 0;

        for (String name : new String[] {"skysbuildingpieces_world_state.dat",
                "skysbuildingpieces_legacy_registry.dat", "skysbuildingpiecesbop_named_registry.dat",
                "skysgrassslabs_legacy_registry.dat", "mineralogy_legacy_registry.dat"}) {
            File before = new File(first, name);
            File after = new File(current, name);
            if (!before.isFile() && after.isFile() && copiedOptionalRegistry(first, after)) {
                restoredOptionalSidecars++;
                continue;
            }
            require(before.isFile() == after.isFile(), "Recovery data appeared or disappeared on reload: " + name);

            if (before.isFile()) {
                try (InputStream original = new FileInputStream(before);
                        InputStream reloaded = new FileInputStream(after)) {
                    CompoundTag firstData = net.minecraft.nbt.NbtIo.readCompressed(original);
                    CompoundTag currentData = net.minecraft.nbt.NbtIo.readCompressed(reloaded);
                    if (name.equals("skysbuildingpieces_world_state.dat")) {
                        require(firstData.contains("data", 10) && currentData.contains("data", 10),
                                "The saved-data wrapper is missing its contents");
                        int previousVersion = firstData.getInt("DataVersion");
                        int currentVersion = currentData.getInt("DataVersion");
                        int targetVersion = net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion();
                        require(previousVersion <= targetVersion
                                && (currentVersion == previousVersion || currentVersion == targetVersion),
                                "Unexpected saved-data version change");
                        // Minecraft may stamp an unchanged old SavedData wrapper on its next save.
                        // The complete mod data and every other wrapper field must still match.
                        firstData.remove("DataVersion");
                        currentData.remove("DataVersion");
                    }
                    require(firstData.equals(currentData), "Saved recovery data changed on reload: " + name);
                }
                checked++;
            }
        }

        require(checked > 0, "The reload profile has no saved recovery data");
        LOGGER.info("BOP_RECOVERY_DATA_RELOAD_PASS files={} copied_optional_registries={}",
                checked, restoredOptionalSidecars);
    }

    private static boolean copiedOptionalRegistry(File first, File after) throws Exception {
        boolean grass = after.getName().equals("skysgrassslabs_legacy_registry.dat");
        if (!grass && !after.getName().equals("mineralogy_legacy_registry.dat")) {
            return false;
        }
        CompoundTag source = readCompressed(new File("source-level.dat"));
        if (source.contains("FML", 10) || source.getCompound("Data").getInt("DataVersion") < 1451) {
            return false;
        }
        // A named source can carry the old FML table in the core sidecar
        // alone. Forge writes it to level.dat on the first save, where the
        // other mods can read it on the next start.
        File coreSidecar = new File(first, "skysbuildingpieces_legacy_registry.dat");
        if (!coreSidecar.isFile()) {
            return false;
        }
        CompoundTag fml = readCompressed(coreSidecar).getCompound("FML");
        CompoundTag blocks = fml.getCompound("Registries").getCompound("minecraft:blocks");
        CompoundTag expected = new CompoundTag();
        expected.put("Blocks", blocks);
        if (grass) {
            expected.put("FML", fml);
        }
        return !blocks.isEmpty() && expected.equals(readCompressed(after));
    }

    private static CompoundTag readCompressed(File file) throws java.io.IOException {
        try (InputStream input = new FileInputStream(file)) {
            return net.minecraft.nbt.NbtIo.readCompressed(input);
        }
    }

    private static java.util.Map<BlockPos, BlockState> retainedNamedRocks(JsonObject manifest) throws Exception {
        java.util.Map<BlockPos, BlockState> states = new java.util.HashMap<>();

        if (manifest.has("source_saved_states")) {
            for (JsonElement element : manifest.getAsJsonArray("source_saved_states")) {
                JsonObject row = element.getAsJsonObject();
                CompoundTag saved = TagParser.parseTag(row.get("state").getAsString());

                // Installing Mineralogy later does not replace an already named fallback block.
                // Its bridge recipe is the player's explicit conversion route.
                if (saved.getString("Name").equals("skysbuildingpiecesbop:retired_rocks")) {
                    BlockState state = net.minecraft.nbt.NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), saved);

                    require(state.getBlock() == zone.moddev.mc.skysbuildingpiecesbop.RetiredRocks.BLOCK,
                            "A named fallback rock no longer resolves");
                    states.put(position(row), state);
                }
            }
        }
        return states;
    }

    private static void checkBiomes(ServerLevel world, JsonObject manifest) {
        require(manifest.has("biomes") && manifest.getAsJsonArray("biomes").size() == 73,
                "The biome regression fixture is incomplete");

        File delayedMarker = new File("delayed-biomes-first-pass.txt");
        boolean delay = Boolean.getBoolean("skysbuildingpieces.bopDelayedBiomes");
        boolean first = delay && !delayedMarker.isFile();
        java.util.Set<Long> deferred = new java.util.HashSet<>();
        int number = 0;

        for (JsonElement element : manifest.getAsJsonArray("biomes")) {
            JsonObject row = element.getAsJsonObject();
            BlockPos position = position(row);

            if ((number++ & 1) != 0 || TARGET_UPGRADES.getAsJsonObject("biomes").has(row.get("id").getAsString())) {
                deferred.add(net.minecraft.world.level.ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4));
            }
        }

        int checked = 0;

        for (JsonElement element : manifest.getAsJsonArray("biomes")) {
            JsonObject row = element.getAsJsonObject();
            BlockPos position = position(row);

            if (first && deferred.contains(net.minecraft.world.level.ChunkPos.asLong(
                    position.getX() >> 4, position.getZ() >> 4))) {
                require(world.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4)
                        == null, "A deferred biome chunk was loaded during the first pass");
                continue;
            }

            world.getChunk(position(row));

            String actual = world.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
                    .getKey(world.getBiome(position(row)).value()).toString();

            require(actual.equals(currentTarget("biomes", row.get("target_biome").getAsString())),
                    "Saved BOP biome changed after loading its chunk: " + row + ", actual " + actual);
            checked++;
        }

        if (first) {
            require(checked > 0 && checked < 73 && !deferred.isEmpty(), "Delayed biome coverage is empty");
            try {
                java.nio.file.Files.write(delayedMarker.toPath(), ("deferred_chunks=" + deferred.size() + "\n")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (java.io.IOException failure) {
                throw new IllegalStateException("Cannot retain delayed-biome evidence", failure);
            }
        } else {
            require(checked == 73, "A saved biome was not checked");
        }

        LOGGER.info("BOP_NATIVE_BIOMES_PASS biomes=" + checked + " loaded_chunks=true delayed_first=" + first);
    }

    private static String plant(CompoundTag original) {
        String id = original.getString("id");
        int metadata = original.getShort("Damage");

        if (id.equals("minecraft:sapling")) {
            return "minecraft:" + new String[] { "oak", "spruce", "birch", "jungle", "acacia", "dark_oak" }[metadata]
                    + "_sapling";
        }
        if (id.equals("minecraft:waterlily") || id.equals("biomesoplenty:waterlily")) {
            return "minecraft:lily_pad";
        }
        if (id.equals("biomesoplenty:flower_0")) {
            return metadata == 4 ? "biomesoplenty:blue_hydrangea"
                    : metadata == 8 ? "biomesoplenty:violet" : "minecraft:oxeye_daisy";
        }
        if (id.equals("biomesoplenty:plant_1")) {
            return metadata == 4 ? "biomesoplenty:cattail" : "minecraft:cactus";
        }
        if (id.equals("biomesoplenty:sapling_0")) {
            return "biomesoplenty:" + (metadata == 0 ? "yellow_autumn" : metadata == 4 ? "umbran" : "fir") + "_sapling";
        }
        if (id.equals("biomesoplenty:sapling_1")) {
            return "biomesoplenty:" + (metadata == 3 ? "maple" : metadata == 5 ? "flowering_oak" : "jacaranda")
                    + "_sapling";
        }
        if (id.equals("biomesoplenty:sapling_2")) {
            return "biomesoplenty:" + (metadata == 1 ? "palm" : metadata == 2 ? "redwood" : "willow") + "_sapling";
        }

        return id;
    }

    private static JsonObject targetUpgrades() {
        try (java.io.InputStreamReader reader = new java.io.InputStreamReader(
                zone.moddev.mc.skysbuildingpiecesbop.RetiredRocks.class.getResourceAsStream(
                        "/assets/skysbuildingpiecesbop/catalogue/named_legacy.json"),
                java.nio.charset.StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader).getAsJsonObject();
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot read the target upgrade contract", failure);
        }
    }

    static String currentTarget(String group, String id) {
        if ((group.equals("blocks") || group.equals("items")) && id.equals("minecraft:grass_path")) {
            return "minecraft:dirt_path";
        }
        JsonObject mappings = TARGET_UPGRADES.getAsJsonObject(group);

        String target = mappings.has(id) ? mappings.get(id).getAsString() : id;

        if (group.equals("biomes")) {
            if (target.equals("minecraft:nether")) return "minecraft:nether_wastes";
            if (target.equals("minecraft:deep_warm_ocean")) return "minecraft:warm_ocean";
            return net.minecraft.util.datafix.fixes.CavesAndCliffsRenames.RENAMES.getOrDefault(target, target);
        }
        return target;
    }

    private static String currentItemTarget(String id) {
        return currentTarget("blocks", currentTarget("items", id));
    }

    private static <T extends Comparable<T>> BlockState sourceProperty(BlockState state,
            net.minecraft.world.level.block.state.properties.Property<T> property, String value) {
        return state.setValue(property, property.getValue(value).orElseThrow(
                () -> new AssertionError("A saved plant property cannot be recovered: " + property.getName() + "=" + value)));
    }

    private static BlockPos position(JsonObject row) {
        return new BlockPos(row.get("x").getAsInt(), row.get("y").getAsInt(), row.get("z").getAsInt());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
