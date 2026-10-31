package zone.moddev.mc.skysbuildingpiecesbop.probe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileReader;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.ChestTileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.server.ServerWorld;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.catalogue.Shape;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import zone.moddev.mc.skysbuildingpieces.legacy.HistoricalFullBlocks;
import zone.moddev.mc.skysbuildingpieces.legacy.PiecesWorldState;

/** Load every saved position through the real server, including chunks outside spawn. */
final class ForwardUpgradeChecks {
    private ForwardUpgradeChecks() {
    }

    static void run(MinecraftServer server, File manifestFile) throws Exception {
        JsonObject manifest = read(manifestFile);
        ServerWorld world = server.getWorld(DimensionType.OVERWORLD);

        run(world, manifest);
    }

    static void biomesOnly(MinecraftServer server, File manifestFile) throws Exception {
        checkBiomes(server.getWorld(DimensionType.OVERWORLD), read(manifestFile));
    }

    private static JsonObject read(File manifestFile) throws Exception {
        try (FileReader reader = new FileReader(manifestFile)) {
            JsonObject manifest = new JsonParser().parse(reader).getAsJsonObject();

            if (manifest.has("manifest")) {
                manifest = manifest.getAsJsonObject("manifest");
            }

            return manifest;
        }
    }

    private static void run(ServerWorld world, JsonObject manifest) throws Exception {
        int states = 0;

        for (JsonElement element : manifest.getAsJsonArray("blocks")) {
            JsonObject row = element.getAsJsonObject();
            BlockPos position = position(row);
            BlockState actual = world.getBlockState(position);
            BlockState expected;

            if (row.has("role") && row.get("role").getAsString().equals("world")) {
                expected = zone.moddev.mc.skysbuildingpiecesbop.BopWorldMappings.state(row.getAsJsonObject("target"));
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
            for (Map.Entry<net.minecraft.state.IProperty<?>, Comparable<?>> property : expected.getValues()
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
            require(world.getTileEntity(chestPosition) instanceof ChestTileEntity,
                    "Saved chest is missing at " + chestPosition + ": " + world.getBlockState(chestPosition));

            ChestTileEntity chest = (ChestTileEntity) world.getTileEntity(chestPosition);
            ItemStack actual = chest.getStackInSlot(row.get("slot").getAsInt());
            CompoundNBT original = JsonToNBT.getTagFromJson(row.get("item").getAsString());

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

                    String expectedId = plant(before);

                    require(after.getString("id").equals(expectedId),
                            "Basket plant changed: " + before + ", expected " + expectedId + ", actual " + after);
                    expectedUnits += before.getByte("Count");
                }

                units += expectedUnits;
                baskets++;
            } else if (row.has("native_item")) {
                require(actual.getItem().getRegistryName().toString().equals(row.get("target_item").getAsString()),
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

        ItemStack shovel = ((ChestTileEntity) world.getTileEntity(shovelPosition)).getStackInSlot(0);

        require(shovel.getItem() == net.minecraft.item.Items.DIAMOND_SHOVEL && shovel.getDamage() == 17
                && shovel.getDisplayName().getString().equals("A saved slope shovel")
                && net.minecraft.enchantment.EnchantmentHelper
                        .getEnchantmentLevel(net.minecraft.enchantment.Enchantments.EFFICIENCY, shovel) == 3,
                "Saved shovel identity, damage, name or enchantment changed");
        require(state.blocks == 17 && state.items == 31 && state.chunks == 7, "Existing vanilla totals changed");

        PiecesWorldState.Totals bop = state.modules.get("biomesoplenty");

        require(bop != null && bop.blocks == 23 && bop.items == 19 && bop.chunks == 1, "Existing BOP totals changed");
        require(baskets == 8 && units == 1500, "Saved basket contract changed");

        if (manifest.has("biomes")) {
            checkBiomes(world, manifest);
        }

        System.out.println("BUILDING_PIECES_BOP_FORWARD_UPGRADE_PASS states=" + states + " baskets=" + baskets
                + " units=" + units);
    }

    private static void checkBiomes(ServerWorld world, JsonObject manifest) {
        require(manifest.has("biomes") && manifest.getAsJsonArray("biomes").size() == 73,
                "The biome regression fixture is incomplete");

        for (JsonElement element : manifest.getAsJsonArray("biomes")) {
            JsonObject row = element.getAsJsonObject();

            world.getChunk(position(row));

            String actual = world.getBiome(position(row)).getRegistryName().toString();

            require(actual.equals(row.get("target_biome").getAsString()),
                    "Saved BOP biome changed after loading its chunk: " + row + ", actual " + actual);
        }

        System.out.println("BOP_NATIVE_BIOMES_PASS biomes=73 loaded_chunks=true");
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

    private static BlockPos position(JsonObject row) {
        return new BlockPos(row.get("x").getAsInt(), row.get("y").getAsInt(), row.get("z").getAsInt());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
