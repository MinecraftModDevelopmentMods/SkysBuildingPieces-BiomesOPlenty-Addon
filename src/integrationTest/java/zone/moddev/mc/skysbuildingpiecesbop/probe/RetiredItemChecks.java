package zone.moddev.mc.skysbuildingpiecesbop.probe;

import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.datafix.FixTypes;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import zone.moddev.mc.skysbuildingpiecesbop.*;
import zone.moddev.mc.skysbuildingpiecesbop.basket.*;
import zone.moddev.mc.skysbuildingpieces.legacy.PiecesWorldState;

/** Target-native item resolution, multiplied counts and fail-closed capacity checks. */
public final class RetiredItemChecks {
    private static int checks;

    public static void run(WorldServer world) {
        checks = 0;

        LegacyItemRecovery recovery = new LegacyItemRecovery();
        NBTTagCompound state = PiecesWorldState.get(world).writeToNBT(new NBTTagCompound());
        String[] legacyIds = { "turnip_seeds", "pinecone", "dull_flower_band" };
        int[] counts = { 1, 2, 8 };

        for (int entryIndex = 0; entryIndex < 3; entryIndex++) {
            require(LegacyItemRecovery.HOLDERS[entryIndex].getCreativeTab() == null, "holder is hidden");

            Item target = ForgeRegistries.ITEMS.getValue(new ResourceLocation(LegacyItemRecovery.target(entryIndex)));

            for (String registryId : new String[] { "biomesoplenty:" + legacyIds[entryIndex],
                    LegacyItemRecovery.HOLDERS[entryIndex].getRegistryName().toString() }) {
                NBTTagCompound raw = stack(registryId, 1, 7);
                NBTTagCompound tag = raw.getCompoundTag("tag").copy();

                if (target == null || target == Items.AIR) {
                    reject(recovery, raw, "unavailable");
                    continue;
                }

                recovery.fixTagCompound(raw);
                require(raw.getString("id").equals(LegacyItemRecovery.target(entryIndex)),
                        "target identity " + registryId);
                require(raw.getInteger("Count") == counts[entryIndex] && raw.getInteger("Damage") == 0,
                        "target quantity and variant");
                require(raw.getCompoundTag("tag").equals(tag), "unrelated custom NBT");

                ItemStack decoded = new ItemStack(raw);

                require(decoded.getItem() == target && decoded.getCount() == counts[entryIndex],
                        "real item deserialization");

                if (entryIndex == 2) {
                    require(decoded.getTranslationKey().contains("clover"), "clover metadata, not another flower");
                }

                NBTTagCompound once = raw.copy();

                recovery.fixTagCompound(raw);
                require(raw.equals(once), "reload idempotence");
            }
        }

        NBTTagCompound chest = chest();
        NBTTagList list = new NBTTagList();
        NBTTagCompound pine = stack("biomesoplenty:pinecone", 64, 4);

        list.appendTag(pine);

        NBTTagCompound stone = new ItemStack(Items.STICK, 64).writeToNBT(new NBTTagCompound());

        stone.setByte("Slot", (byte) 0);
        list.appendTag(stone);
        chest.setTag("Items", list);
        recovery.fixTagCompound(chest);
        require(list.tagCount() == 2, "input references were not mutated");
        list = chest.getTagList("Items", 10);
        require(list.tagCount() == 3, "overflow creates a second legal stack");

        int dyes = 0;

        for (int entryIndex = 0; entryIndex < list.tagCount(); entryIndex++) {
            NBTTagCompound compoundTag = list.getCompoundTagAt(entryIndex);

            if (compoundTag.getString("id").equals("biomesoplenty:brown_dye")) {
                dyes += compoundTag.getInteger("Count");
                require(compoundTag.getInteger("Count") == 64, "legal stack limit");
                require(compoundTag.getCompoundTag("tag").equals(pine.getCompoundTag("tag")),
                        "split copies complete custom data");
            }
        }

        require(dyes == 128 && list.getCompoundTagAt(2).getInteger("Slot") == 1, "exact multiplier and free slot");

        NBTTagCompound full = chest();

        list = new NBTTagList();

        for (int entryIndex = 0; entryIndex < 27; entryIndex++) {
            NBTTagCompound entry = entryIndex == 0 ? stack("biomesoplenty:pinecone", 64, entryIndex)
                    : new ItemStack(Items.STICK, 64).writeToNBT(new NBTTagCompound());

            entry.setByte("Slot", (byte) entryIndex);
            list.appendTag(entry);
        }

        full.setTag("Items", list);
        reject(recovery, full, "empty inventory slots");
        reject(recovery, stack("biomesoplenty:pinecone", 64, 0), "extra slots");

        NBTTagCompound frame = new NBTTagCompound();

        frame.setString("id", "minecraft:item_frame");
        frame.setTag("Item", stack("biomesoplenty:pinecone", 1, 0));
        reject(recovery, frame, "item frame");

        NBTTagCompound bad = stack("biomesoplenty:pinecone", 1, 0);

        bad.setString("tag", "invalid");
        reject(recovery, bad, "malformed");
        bad = stack("biomesoplenty:pinecone", 1, 0);
        bad.setByte("Count", (byte) 0);
        reject(recovery, bad, "invalid saved stack");

        NBTTagCompound basket = new ItemStack(Baskets.ITEM).writeToNBT(new NBTTagCompound());
        NBTTagCompound tag = new NBTTagCompound();

        list = new NBTTagList();
        list.appendTag(stack("biomesoplenty:pinecone", 64, 2));
        tag.setTag("Items", list);
        basket.setTag("tag", tag);
        recovery.fixTagCompound(basket);
        require(basket.getCompoundTag("tag").getTagList("Items", 10).tagCount() == 2,
                "basket overflow is allocated before validation");
        require(new BasketInventory(new ItemStack(basket)).getStackInSlot(0).getCount() == 64,
                "legacy non-plant output stays withdrawable");

        NBTTagCompound player = new NBTTagCompound();

        player.setInteger("DataVersion", 1343);
        player.setTag("abilities", new NBTTagCompound());

        NBTTagCompound versions = new NBTTagCompound();

        versions.setInteger(SkysBuildingPiecesBop.ID, 1);
        player.setTag("ForgeDataVersion", versions);
        list = new NBTTagList();
        list.appendTag(stack("biomesoplenty:pinecone", 64, 0));
        player.setTag("Inventory", list);
        list = new NBTTagList();
        list.appendTag(stack("biomesoplenty:pinecone", 40, 3));
        player.setTag("EnderItems", list);
        FMLCommonHandler.instance().getDataFixer().process(FixTypes.PLAYER, player);
        require(player.getTagList("Inventory", 10).tagCount() == 2
                && player.getTagList("EnderItems", 10).tagCount() == 2,
                "version-one saves receive version-two player and ender conversion");

        NBTTagCompound deferred = stack("biomesoplenty:pinecone", 64, 4);

        deferred.setInteger("DataVersion", 1343);
        deferred.setTag("ForgeDataVersion", versions.copy());

        NBTTagCompound same = deferred.copy();

        FMLCommonHandler.instance().getDataFixer().process(FixTypes.ITEM_INSTANCE, deferred);
        require(deferred.equals(same), "child-first fixing defers larger output without truncation");

        NBTTagCompound parent = chest();

        parent.setInteger("DataVersion", 1343);
        parent.setTag("ForgeDataVersion", versions.copy());
        list = new NBTTagList();
        list.appendTag(deferred);
        parent.setTag("Items", list);
        FMLCommonHandler.instance().getDataFixer().process(FixTypes.BLOCK_ENTITY, parent);

        net.minecraft.tileentity.TileEntityChest decodedChest = new net.minecraft.tileentity.TileEntityChest();

        decodedChest.readFromNBT(parent);
        require(decodedChest.getStackInSlot(4).getCount() == 64 && decodedChest.getStackInSlot(0).getCount() == 64,
                "parent-first and child-first orders both deserialize 128 items");

        NBTTagCompound caps = stack("biomesoplenty:pinecone", 1, 0);
        NBTTagCompound payload = new NBTTagCompound();

        payload.setString("custom", "retain capabilities");
        caps.setTag("ForgeCaps", payload);
        recovery.fixTagCompound(caps);
        require(caps.getCompoundTag("tag").getCompoundTag(SkysBuildingPiecesBop.ID + ":legacy_capabilities")
                .equals(payload), "old capabilities retain an exact archive");
        require(PiecesWorldState.get(world).writeToNBT(new NBTTagCompound()).equals(state),
                "piece counters and schema untouched");
        System.out.println("BOP_RETIRED_ITEMS_PASS checks=" + checks
                + " multipliers=1/2/8 capacity=bounded NBT=preserved schema=unchanged");
    }

    private static NBTTagCompound chest() {
        NBTTagCompound compoundTag = new NBTTagCompound();

        compoundTag.setString("id", "minecraft:chest");
        return compoundTag;
    }

    private static NBTTagCompound stack(String registryId, int count, int slot) {
        NBTTagCompound compoundTag = new NBTTagCompound();

        compoundTag.setString("id", registryId);
        compoundTag.setByte("Count", (byte) count);
        compoundTag.setShort("Damage", (short) 0);
        compoundTag.setByte("Slot", (byte) slot);

        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound display = new NBTTagCompound();

        display.setString("Name", "Archived plant");
        tag.setTag("display", display);
        tag.setInteger("Unrelated", 711);
        compoundTag.setTag("tag", tag);
        return compoundTag;
    }

    private static void reject(LegacyItemRecovery fix, NBTTagCompound raw, String expected) {
        NBTTagCompound before = raw.copy();
        boolean stopped = false;

        try {
            fix.fixTagCompound(raw);
        } catch (IllegalStateException failure) {
            stopped = failure.getMessage().contains(expected);
        }

        require(stopped && raw.equals(before), "atomic safety stop: " + expected);
    }

    private static void require(boolean value, String message) {
        checks++;

        if (!value) {
            throw new AssertionError("Retired BOP items: " + message);
        }
    }
}
