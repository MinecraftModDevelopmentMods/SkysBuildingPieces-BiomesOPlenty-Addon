package zone.moddev.mc.skysbuildingpiecesbop.probe;

import net.minecraft.item.Items;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.util.ResourceLocation;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge;
import net.minecraft.world.server.ServerWorld;

import net.minecraftforge.registries.ForgeRegistries;
import zone.moddev.mc.skysbuildingpiecesbop.*;
import zone.moddev.mc.skysbuildingpiecesbop.basket.*;
import zone.moddev.mc.skysbuildingpieces.legacy.PiecesWorldState;

/** Target-native item resolution, multiplied counts and fail-closed capacity checks. */
public final class RetiredItemChecks {
    private static int checks;

    public static void run(ServerWorld world) {
        checks = 0;

        LegacyItemRecovery recovery = new LegacyItemRecovery();
        CompoundNBT state = PiecesWorldState.get(world).save(new CompoundNBT());
        String[] legacyIds = { "turnip_seeds", "pinecone", "dull_flower_band" };
        int[] counts = { 1, 2, 8 };

        for (int entryIndex = 0; entryIndex < 3; entryIndex++) {
            require(LegacyItemRecovery.HOLDERS[entryIndex].getItemCategory() == null, "holder is hidden");

            Item target = ForgeRegistries.ITEMS.getValue(new ResourceLocation(LegacyItemRecovery.target(entryIndex)));

            for (String registryId : new String[] { "biomesoplenty:" + legacyIds[entryIndex],
                    LegacyItemRecovery.HOLDERS[entryIndex].getRegistryName().toString() }) {
                CompoundNBT raw = stack(registryId, 1, 7);
                CompoundNBT tag = raw.getCompound("tag").copy();

                if (target == null || target == Items.AIR) {
                    reject(recovery, raw, "unavailable");
                    continue;
                }

                recovery.fixTagCompound(raw);
                require(raw.getString("id").equals(LegacyItemRecovery.target(entryIndex)),
                        "target identity " + registryId);
                require(raw.getInt("Count") == counts[entryIndex] && raw.getInt("Damage") == 0,
                        "target quantity and variant");
                require(raw.getCompound("tag").equals(tag), "unrelated custom NBT");

                ItemStack decoded = ItemStack.of(raw);

                require(decoded.getItem() == target && decoded.getCount() == counts[entryIndex],
                        "real item deserialization");

                if (entryIndex == 2) {
                    require(decoded.getDescriptionId().contains("oxeye_daisy"), "retired flower target");
                }

                CompoundNBT once = raw.copy();

                recovery.fixTagCompound(raw);
                require(raw.equals(once), "reload idempotence");
            }
        }

        CompoundNBT chest = chest();
        ListNBT list = new ListNBT();
        CompoundNBT pine = stack("biomesoplenty:pinecone", 64, 4);

        list.add(pine);

        CompoundNBT stone = new ItemStack(Items.STICK, 64).save(new CompoundNBT());

        stone.putByte("Slot", (byte) 0);
        list.add(stone);
        chest.put("Items", list);
        recovery.fixTagCompound(chest);
        require(list.size() == 2, "input references were not mutated");
        list = chest.getList("Items", 10);
        require(list.size() == 3, "overflow creates a second legal stack");

        int dyes = 0;

        for (int entryIndex = 0; entryIndex < list.size(); entryIndex++) {
            CompoundNBT compoundTag = list.getCompound(entryIndex);

            if (compoundTag.getString("id").equals("minecraft:brown_dye")) {
                dyes += compoundTag.getInt("Count");
                require(compoundTag.getInt("Count") == 64, "legal stack limit");
                require(compoundTag.getCompound("tag").equals(pine.getCompound("tag")),
                        "split copies complete custom data");
            }
        }

        require(dyes == 128 && list.getCompound(2).getInt("Slot") == 1, "exact multiplier and free slot");

        CompoundNBT full = chest();

        list = new ListNBT();

        for (int entryIndex = 0; entryIndex < 27; entryIndex++) {
            CompoundNBT entry = entryIndex == 0 ? stack("biomesoplenty:pinecone", 64, entryIndex)
                    : new ItemStack(Items.STICK, 64).save(new CompoundNBT());

            entry.putByte("Slot", (byte) entryIndex);
            list.add(entry);
        }

        full.put("Items", list);
        reject(recovery, full, "empty inventory slots");
        reject(recovery, stack("biomesoplenty:pinecone", 64, 0), "extra slots");

        CompoundNBT frame = new CompoundNBT();

        frame.putString("id", "minecraft:item_frame");
        frame.put("Item", stack("biomesoplenty:pinecone", 1, 0));
        reject(recovery, frame, "item frame");

        CompoundNBT bad = stack("biomesoplenty:pinecone", 1, 0);

        bad.putString("tag", "invalid");
        reject(recovery, bad, "malformed");
        bad = stack("biomesoplenty:pinecone", 1, 0);
        bad.putByte("Count", (byte) 0);
        reject(recovery, bad, "invalid saved stack");

        CompoundNBT basket = new ItemStack(Baskets.ITEM).save(new CompoundNBT());
        CompoundNBT tag = new CompoundNBT();

        list = new ListNBT();
        list.add(stack("biomesoplenty:pinecone", 64, 2));
        tag.put("Items", list);
        basket.put("tag", tag);
        recovery.fixTagCompound(basket);
        require(basket.getCompound("tag").getList("Items", 10).size() == 2,
                "basket overflow is allocated before validation");
        require(new BasketInventory(ItemStack.of(basket)).getItem(0).getCount() == 64,
                "legacy non-plant output stays withdrawable");

        CompoundNBT player = new CompoundNBT();

        player.putInt("DataVersion", 1343);
        player.put("abilities", new CompoundNBT());

        CompoundNBT versions = new CompoundNBT();

        versions.putInt(SkysBuildingPiecesBop.ID, 1);
        player.put("ForgeDataVersion", versions);
        list = new ListNBT();
        list.add(stack("biomesoplenty:pinecone", 64, 0));
        player.put("Inventory", list);
        list = new ListNBT();
        list.add(stack("biomesoplenty:pinecone", 40, 3));
        player.put("EnderItems", list);
        LegacyBridge.prepareNbt(player);
        require(player.getList("Inventory", 10).size() == 2 && player.getList("EnderItems", 10).size() == 2,
                "version-one saves receive version-two player and ender conversion");

        CompoundNBT deferred = stack("biomesoplenty:pinecone", 64, 4);

        deferred.putInt("DataVersion", 1343);
        deferred.put("ForgeDataVersion", versions.copy());

        CompoundNBT same = deferred.copy();

        boolean blocked = false;

        try {
            LegacyBridge.prepareNbt(deferred);
        } catch (LegacyBridge.RecoveryAbort expected) {
            blocked = true;
        }

        require(blocked && deferred.equals(same), "a child without parent capacity stops without truncation");

        CompoundNBT parent = chest();

        parent.putInt("DataVersion", 1343);
        parent.put("ForgeDataVersion", versions.copy());
        list = new ListNBT();
        list.add(deferred);
        parent.put("Items", list);
        LegacyBridge.prepareNbt(parent);

        net.minecraft.tileentity.ChestTileEntity decodedChest = new net.minecraft.tileentity.ChestTileEntity();

        decodedChest.load(net.minecraft.block.Blocks.CHEST.defaultBlockState(), parent);
        require(decodedChest.getItem(4).getCount() == 64 && decodedChest.getItem(0).getCount() == 64,
                "parent-first and child-first orders both deserialize 128 items");

        CompoundNBT caps = stack("biomesoplenty:pinecone", 1, 0);
        CompoundNBT payload = new CompoundNBT();

        payload.putString("custom", "retain capabilities");
        caps.put("ForgeCaps", payload);
        recovery.fixTagCompound(caps);
        require(caps.getCompound("tag").getCompound(SkysBuildingPiecesBop.ID + ":legacy_capabilities").equals(payload),
                "old capabilities retain an exact archive");
        require(PiecesWorldState.get(world).save(new CompoundNBT()).equals(state),
                "piece counters and schema untouched");
        System.out.println("BOP_RETIRED_ITEMS_PASS checks=" + checks
                + " multipliers=1/2/8 capacity=bounded NBT=preserved schema=unchanged");
    }

    private static CompoundNBT chest() {
        CompoundNBT compoundTag = new CompoundNBT();

        compoundTag.putString("id", "minecraft:chest");
        return compoundTag;
    }

    private static CompoundNBT stack(String registryId, int count, int slot) {
        CompoundNBT compoundTag = new CompoundNBT();

        compoundTag.putString("id", registryId);
        compoundTag.putByte("Count", (byte) count);
        compoundTag.putShort("Damage", (short) 0);
        compoundTag.putByte("Slot", (byte) slot);

        CompoundNBT tag = new CompoundNBT();
        CompoundNBT display = new CompoundNBT();

        display.putString("Name", "Archived plant");
        tag.put("display", display);
        tag.putInt("Unrelated", 711);
        compoundTag.put("tag", tag);
        return compoundTag;
    }

    private static void reject(LegacyItemRecovery fix, CompoundNBT raw, String expected) {
        CompoundNBT before = raw.copy();
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
