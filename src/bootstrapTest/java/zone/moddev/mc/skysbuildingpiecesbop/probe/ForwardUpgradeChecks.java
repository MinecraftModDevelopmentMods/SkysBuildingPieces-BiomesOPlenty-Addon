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
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.ChestTileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import zone.moddev.mc.skysbuildingpieces.legacy.HistoricalFullBlocks;
import zone.moddev.mc.skysbuildingpieces.legacy.PiecesWorldState;

/** Load every saved position through the real server, including chunks outside spawn. */
final class ForwardUpgradeChecks {
    private static final JsonObject TARGET_UPGRADES = targetUpgrades();

    private ForwardUpgradeChecks() {
    }

    static void run(MinecraftServer server, File manifestFile) throws Exception {
        JsonObject manifest = read(manifestFile);
        ServerWorld world = server.getLevel(net.minecraft.world.World.OVERWORLD);

        run(world, manifest);
    }

    static void biomesOnly(MinecraftServer server, File manifestFile) throws Exception {
        checkBiomes(server.getLevel(net.minecraft.world.World.OVERWORLD), read(manifestFile));
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

    private static void run(ServerWorld world, JsonObject manifest) throws Exception {
        int states = 0;
        java.util.Map<BlockPos, BlockState> retainedRocks = retainedNamedRocks(manifest);

        for (JsonElement element : manifest.getAsJsonArray("blocks")) {
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

            require(actual.getBlock() == expected.getBlock(),
                    "Wrong saved block at " + position + ": " + actual + ", expected " + expected);

            // Connections and snow are derived from neighbours. The palette metadata and persisted orientation still
            // have to match exactly.
            for (Map.Entry<net.minecraft.state.Property<?>, Comparable<?>> property : expected.getValues()
                    .entrySet()) {
                String name = property.getKey().getName();

                if (name.equals("north") || name.equals("south") || name.equals("east") || name.equals("west")
                        || name.equals("up") || name.equals("snowy") || name.equals("in_wall")
                        || name.equals("lowered_support") || name.equals("shape")) {
                    continue;
                }

                require(property.getValue().equals(actual.getValues().get(property.getKey())),
                        "Wrong " + name + " at " + position + ": " + actual + ", expected " + expected);
            }

            states++;
        }

        int baskets = 0;
        int units = 0;

        for (JsonElement element : manifest.getAsJsonArray("stacks")) {
            JsonObject row = element.getAsJsonObject();
            BlockPos chestPosition = position(row);

            world.getChunk(chestPosition.getX() >> 4, chestPosition.getZ() >> 4);
            require(world.getBlockEntity(chestPosition) instanceof ChestTileEntity,
                    "Saved chest is missing at " + chestPosition + ": " + world.getBlockState(chestPosition));

            ChestTileEntity chest = (ChestTileEntity) world.getBlockEntity(chestPosition);
            ItemStack actual = chest.getItem(row.get("slot").getAsInt());
            CompoundNBT original = JsonToNBT.parseTag(row.get("item").getAsString());

            require(actual.getCount() == original.getByte("Count"), "Saved stack count changed: " + row);

            if (row.has("basket")) {
                require(actual.getItem().getRegistryName().toString().equals("skysbuildingpiecesbop:flower_basket"),
                        "Saved basket became another item");

                ListNBT contents = actual.getTag().getList("Items", 10);
                int expectedUnits = 0;
                ListNBT source = original.getCompound("tag").getList("Items", 10);

                require(contents.size() == source.size(), "A basket lost stored slots: " + row.get("basket"));

                for (int slot = 0; slot < source.size(); slot++) {
                    CompoundNBT before = source.getCompound(slot);
                    CompoundNBT after = contents.getCompound(slot);

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
                require(actual.getItem().getRegistryName().toString()
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

        ItemStack shovel = ((ChestTileEntity) world.getBlockEntity(shovelPosition)).getItem(0);

        require(shovel.getItem() == net.minecraft.item.Items.DIAMOND_SHOVEL && shovel.getDamageValue() == 17
                && shovel.getHoverName().getString().equals("A saved slope shovel")
                && net.minecraft.enchantment.EnchantmentHelper
                        .getItemEnchantmentLevel(net.minecraft.enchantment.Enchantments.BLOCK_EFFICIENCY, shovel) == 3,
                "Saved shovel identity, damage, name or enchantment changed");
        require(state.blocks == 17 && state.items == 31 && state.chunks == 7, "Existing vanilla totals changed");

        PiecesWorldState.Totals bop = state.modules.get("biomesoplenty");

        require(bop != null && bop.blocks == 23 && bop.items == 19 && bop.chunks == 1, "Existing BOP totals changed");
        require(baskets == 8 && units == 1500, "Saved basket contract changed");

        if (manifest.has("biomes")) {
            checkBiomes(world, manifest);
        }

        checkSavedRecoveryData(world);

        System.out.println("BUILDING_PIECES_BOP_FORWARD_UPGRADE_PASS states=" + states + " baskets=" + baskets
                + " units=" + units);
    }

    private static void checkSavedRecoveryData(ServerWorld world) throws Exception {
        File first = new File("first-save/data");

        // Delayed profiles deliberately open additional chunks after the first save.
        if (!first.isDirectory() || Boolean.getBoolean("skysbuildingpieces.bopDelayedBiomes")) {
            return;
        }

        world.getServer().saveAllChunks(true, true, true);
        File current = new File(world.getServer()
                .getWorldPath(net.minecraft.world.storage.FolderName.ROOT).toFile(), "data");
        int checked = 0;

        for (String name : new String[] {"skysbuildingpieces_world_state.dat",
                "skysbuildingpieces_legacy_registry.dat", "skysbuildingpiecesbop_named_registry.dat",
                "skysgrassslabs_legacy_registry.dat", "mineralogy_legacy_registry.dat"}) {
            File before = new File(first, name);
            File after = new File(current, name);
            require(before.isFile() == after.isFile(), "Recovery data appeared or disappeared on reload: " + name);

            if (before.isFile()) {
                try (InputStream original = new FileInputStream(before);
                        InputStream reloaded = new FileInputStream(after)) {
                    require(net.minecraft.nbt.CompressedStreamTools.readCompressed(original)
                                    .equals(net.minecraft.nbt.CompressedStreamTools.readCompressed(reloaded)),
                            "Saved recovery data changed on reload: " + name);
                }
                checked++;
            }
        }

        require(checked > 0, "The reload profile has no saved recovery data");
        System.out.println("BOP_RECOVERY_DATA_RELOAD_PASS files=" + checked);
    }

    private static java.util.Map<BlockPos, BlockState> retainedNamedRocks(JsonObject manifest) throws Exception {
        java.util.Map<BlockPos, BlockState> states = new java.util.HashMap<>();

        if (manifest.has("source_saved_states")) {
            for (JsonElement element : manifest.getAsJsonArray("source_saved_states")) {
                JsonObject row = element.getAsJsonObject();
                CompoundNBT saved = JsonToNBT.parseTag(row.get("state").getAsString());

                // Installing Mineralogy later does not replace an already named fallback block.
                // Its bridge recipe is the player's explicit conversion route.
                if (saved.getString("Name").equals("skysbuildingpiecesbop:retired_rocks")) {
                    BlockState state = net.minecraft.nbt.NBTUtil.readBlockState(saved);

                    require(state.getBlock() == zone.moddev.mc.skysbuildingpiecesbop.RetiredRocks.BLOCK,
                            "A named fallback rock no longer resolves");
                    states.put(position(row), state);
                }
            }
        }
        return states;
    }

    private static void checkBiomes(ServerWorld world, JsonObject manifest) {
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
                deferred.add(net.minecraft.util.math.ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4));
            }
        }

        int checked = 0;

        for (JsonElement element : manifest.getAsJsonArray("biomes")) {
            JsonObject row = element.getAsJsonObject();
            BlockPos position = position(row);

            if (first && deferred.contains(net.minecraft.util.math.ChunkPos.asLong(
                    position.getX() >> 4, position.getZ() >> 4))) {
                require(world.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4)
                        == null, "A deferred biome chunk was loaded during the first pass");
                continue;
            }

            world.getChunk(position(row));

            String actual = world.getBiome(position(row)).getRegistryName().toString();

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

        System.out.println("BOP_NATIVE_BIOMES_PASS biomes=" + checked + " loaded_chunks=true delayed_first=" + first);
    }

    private static String plant(CompoundNBT original) {
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
                ForwardUpgradeChecks.class.getResourceAsStream(
                        "/assets/skysbuildingpiecesbop/catalogue/named_legacy.json"),
                java.nio.charset.StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader).getAsJsonObject();
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot read the target upgrade contract", failure);
        }
    }

    private static String currentTarget(String group, String id) {
        JsonObject mappings = TARGET_UPGRADES.getAsJsonObject(group);

        String target = mappings.has(id) ? mappings.get(id).getAsString() : id;

        // The sealed older fixtures keep the vanilla biome name used before 1.16.
        return group.equals("biomes") && target.equals("minecraft:nether") ? "minecraft:nether_wastes" : target;
    }

    private static String currentItemTarget(String id) {
        return currentTarget("items", currentTarget("blocks", id));
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
