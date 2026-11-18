package zone.moddev.mc.skysbuildingpiecesbop.probe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge;
import net.minecraft.server.level.ServerLevel;

import net.minecraftforge.registries.ForgeRegistries;
import zone.moddev.mc.skysbuildingpiecesbop.*;
import zone.moddev.mc.skysbuildingpiecesbop.basket.*;
import zone.moddev.mc.skysbuildingpieces.legacy.PiecesWorldState;

/** Target-native item resolution, multiplied counts and fail-closed capacity checks. */
public final class RetiredItemChecks {
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger();

    private static int checks;

    public static void run(ServerLevel world) {
        checks = 0;

        LegacyItemRecovery recovery = new LegacyItemRecovery();
        CompoundTag state = PiecesWorldState.get(world).saveTag(new CompoundTag());
        String[] legacyIds = { "turnip_seeds", "pinecone", "dull_flower_band" };
        int[] counts = { 1, 2, 8 };

        for (int entryIndex = 0; entryIndex < 3; entryIndex++) {
            if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
                require(BasketChecks.hidden(LegacyItemRecovery.HOLDERS[entryIndex], world), "holder is hidden");
            }

            Item target = ForgeRegistries.ITEMS.getValue(Identifier.parse(LegacyItemRecovery.target(entryIndex)));

            for (String registryId : new String[] { "biomesoplenty:" + legacyIds[entryIndex],
                    ForgeRegistries.ITEMS.getKey(LegacyItemRecovery.HOLDERS[entryIndex]).toString() }) {
                CompoundTag raw = stack(registryId, 1, 7);
                CompoundTag tag = raw.getCompoundOrEmpty("tag").copy();

                if (target == null || target == Items.AIR) {
                    reject(recovery, raw, "unavailable");
                    continue;
                }

                recovery.fixTagCompound(raw);
                require(raw.getStringOr("id", "").equals(LegacyItemRecovery.target(entryIndex)),
                        "target identity " + registryId);
                require(raw.getIntOr("Count", 0) == counts[entryIndex] && raw.getIntOr("Damage", 0) == 0,
                        "target quantity and variant");
                require(raw.getCompoundOrEmpty("tag").equals(tag), "unrelated custom NBT");

                ItemStack decoded = BopProbeItems.read(raw);

                require(decoded.getItem() == target && decoded.getCount() == counts[entryIndex],
                        "real item deserialization");

                if (entryIndex == 2) {
                    require(decoded.getItem().getDescriptionId().contains("oxeye_daisy"), "retired flower target");
                }

                CompoundTag once = raw.copy();

                recovery.fixTagCompound(raw);
                require(raw.equals(once), "reload idempotence");
            }
        }

        CompoundTag chest = chest();
        ListTag list = new ListTag();
        CompoundTag pine = stack("biomesoplenty:pinecone", 64, 4);

        list.add(pine);

        CompoundTag stone = BopProbeItems.legacy(new ItemStack(Items.STICK, 64));

        stone.putByte("Slot", (byte) 0);
        list.add(stone);
        chest.put("Items", list);
        recovery.fixTagCompound(chest);
        require(list.size() == 2, "input references were not mutated");
        list = chest.getListOrEmpty("Items");
        require(list.size() == 3, "overflow creates a second legal stack");

        int dyes = 0;

        for (int entryIndex = 0; entryIndex < list.size(); entryIndex++) {
            CompoundTag compoundTag = list.getCompoundOrEmpty(entryIndex);

            if (compoundTag.getStringOr("id", "").equals("minecraft:brown_dye")) {
                dyes += compoundTag.getIntOr("Count", 0);
                require(compoundTag.getIntOr("Count", 0) == 64, "legal stack limit");
                require(compoundTag.getCompoundOrEmpty("tag").equals(pine.getCompoundOrEmpty("tag")),
                        "split copies complete custom data");
            }
        }

        require(dyes == 128 && list.getCompoundOrEmpty(2).getIntOr("Slot", 0) == 1, "exact multiplier and free slot");

        CompoundTag full = chest();

        list = new ListTag();

        for (int entryIndex = 0; entryIndex < 27; entryIndex++) {
            CompoundTag entry = entryIndex == 0 ? stack("biomesoplenty:pinecone", 64, entryIndex)
                    : BopProbeItems.legacy(new ItemStack(Items.STICK, 64));

            entry.putByte("Slot", (byte) entryIndex);
            list.add(entry);
        }

        full.put("Items", list);
        reject(recovery, full, "empty inventory slots");
        reject(recovery, stack("biomesoplenty:pinecone", 64, 0), "extra slots");

        CompoundTag frame = new CompoundTag();

        frame.putString("id", "minecraft:item_frame");
        frame.put("Item", stack("biomesoplenty:pinecone", 1, 0));
        reject(recovery, frame, "item frame");

        CompoundTag bad = stack("biomesoplenty:pinecone", 1, 0);

        bad.putString("tag", "invalid");
        reject(recovery, bad, "malformed");
        bad = stack("biomesoplenty:pinecone", 1, 0);
        bad.putByte("Count", (byte) 0);
        reject(recovery, bad, "invalid saved stack");

        CompoundTag basket = BopProbeItems.legacy(new ItemStack(Baskets.ITEM));
        CompoundTag tag = new CompoundTag();

        list = new ListTag();
        list.add(stack("biomesoplenty:pinecone", 64, 2));
        tag.put("Items", list);
        basket.put("tag", tag);
        recovery.fixTagCompound(basket);
        require(basket.getCompoundOrEmpty("tag").getListOrEmpty("Items").size() == 2,
                "basket overflow is allocated before validation");
        require(new BasketInventory(BopProbeItems.read(basket), BopProbeItems.registries()).getItem(0).getCount() == 64,
                "legacy non-plant output stays withdrawable");

        CompoundTag player = new CompoundTag();

        player.putInt("DataVersion", 1343);
        player.put("abilities", new CompoundTag());

        CompoundTag versions = new CompoundTag();

        versions.putInt(SkysBuildingPiecesBop.ID, 1);
        player.put("ForgeDataVersion", versions);
        list = new ListTag();
        list.add(stack("biomesoplenty:pinecone", 64, 0));
        player.put("Inventory", list);
        list = new ListTag();
        list.add(stack("biomesoplenty:pinecone", 40, 3));
        player.put("EnderItems", list);
        LegacyBridge.prepareNbt(player);
        require(player.getListOrEmpty("Inventory").size() == 2 && player.getListOrEmpty("EnderItems").size() == 2,
                "version-one saves receive version-two player and ender conversion");

        CompoundTag deferred = stack("biomesoplenty:pinecone", 64, 4);

        deferred.putInt("DataVersion", 1343);
        deferred.put("ForgeDataVersion", versions.copy());

        CompoundTag same = deferred.copy();

        boolean blocked = false;

        try {
            LegacyBridge.prepareNbt(deferred);
        } catch (LegacyBridge.RecoveryAbort expected) {
            blocked = true;
        }

        require(blocked && deferred.equals(same), "a child without parent capacity stops without truncation");

        CompoundTag parent = chest();

        parent.putInt("DataVersion", 1343);
        parent.put("ForgeDataVersion", versions.copy());
        list = new ListTag();
        list.add(deferred);
        parent.put("Items", list);
        LegacyBridge.prepareNbt(parent);

        net.minecraft.world.level.block.entity.ChestBlockEntity decodedChest = new net.minecraft.world.level.block.entity.ChestBlockEntity(
                net.minecraft.core.BlockPos.ZERO, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());

        ListTag upgradedItems = new ListTag();
        for (net.minecraft.nbt.Tag value : parent.getListOrEmpty("Items")) {
            CompoundTag old = (CompoundTag) value;
            CompoundTag item = BopProbeItems.save(BopProbeItems.read(old));
            item.putByte("Slot", old.getByteOr("Slot", (byte) 0));
            upgradedItems.add(item);
        }
        parent.put("Items", upgradedItems);
        decodedChest.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, world.registryAccess(), parent));
        require(decodedChest.getItem(4).getCount() == 64 && decodedChest.getItem(0).getCount() == 64,
                "parent-first and child-first orders both deserialize 128 items");

        CompoundTag caps = stack("biomesoplenty:pinecone", 1, 0);
        CompoundTag payload = new CompoundTag();

        payload.putString("custom", "retain capabilities");
        caps.put("ForgeCaps", payload);
        recovery.fixTagCompound(caps);
        require(caps.getCompoundOrEmpty("tag").getCompoundOrEmpty(SkysBuildingPiecesBop.ID + ":legacy_capabilities").equals(payload),
                "old capabilities retain an exact archive");
        require(PiecesWorldState.get(world).saveTag(new CompoundTag()).equals(state),
                "piece counters and schema untouched");
        LOGGER.info("BOP_RETIRED_ITEMS_PASS checks=" + checks
                + " multipliers=1/2/8 capacity=bounded NBT=preserved schema=unchanged");
    }

    private static CompoundTag chest() {
        CompoundTag compoundTag = new CompoundTag();

        compoundTag.putString("id", "minecraft:chest");
        return compoundTag;
    }

    private static CompoundTag stack(String registryId, int count, int slot) {
        CompoundTag compoundTag = new CompoundTag();

        compoundTag.putString("id", registryId);
        compoundTag.putByte("Count", (byte) count);
        compoundTag.putShort("Damage", (short) 0);
        compoundTag.putByte("Slot", (byte) slot);

        CompoundTag tag = new CompoundTag();
        CompoundTag display = new CompoundTag();

        display.putString("Name", "\"Archived plant\"");
        tag.put("display", display);
        tag.putInt("Unrelated", 711);
        compoundTag.put("tag", tag);
        return compoundTag;
    }

    private static void reject(LegacyItemRecovery fix, CompoundTag raw, String expected) {
        CompoundTag before = raw.copy();
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
