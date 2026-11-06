package zone.moddev.mc.skysbuildingpiecesbop.probe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import zone.moddev.mc.skysbuildingpiecesbop.LegacyItemRecovery;
import zone.moddev.mc.skysbuildingpiecesbop.basket.BasketData;
import zone.moddev.mc.skysbuildingpiecesbop.basket.Baskets;

/** Old mod plants need vanilla's name and enchantment updates as well as a new ID. */
final class PlantRecoveryChecks {
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger();

    private PlantRecoveryChecks() {
    }

    static void run() {
        for (String source : new String[] { "biomesoplenty:sapling_0", "minecraft:sapling" }) {
            CompoundTag basket = BopProbeItems.legacy(new ItemStack(Baskets.ITEM));
            CompoundTag tag = new CompoundTag();
            CompoundTag plant = new CompoundTag();

            plant.putString("id", source);
            plant.putByte("Count", (byte) 23);
            plant.putByte("Slot", (byte) 4);
            plant.putShort("Damage", (short) 0);

            CompoundTag custom = new CompoundTag();
            CompoundTag display = new CompoundTag();

            display.putString("Name", "Saved tree");
            custom.put("display", display);
            custom.putString("unrelated", "Keep this data");
            plant.put("tag", custom);

            ListTag contents = new ListTag();

            contents.add(plant);
            tag.put("Items", contents);
            basket.put("tag", tag);

            LegacyItemRecovery recovery = new LegacyItemRecovery();

            recovery.fixTagCompound(basket);

            CompoundTag converted = basket.getCompoundOrEmpty("tag").getListOrEmpty("Items").getCompoundOrEmpty(0);
            ItemStack decoded = BopProbeItems.read(converted);

            var expectedPlant = source.equals("biomesoplenty:sapling_0")
                    ? net.minecraft.world.item.Items.POPLAR_SAPLING : net.minecraft.world.item.Items.OAK_SAPLING;
            require(decoded.is(expectedPlant), "Legacy sapling no longer has a live replacement");
            require(decoded.getHoverName().getString().equals("Saved tree"), "Legacy plant name is readable");
            require(decoded.getCount() == 23 && converted.getByteOr("Slot", (byte) 0) == 4, "Legacy slot and count");
            require(BopProbeItems.custom(decoded).getStringOr("unrelated", "").equals("Keep this data"), "Unrelated plant NBT");

            CompoundTag once = basket.copy();

            recovery.fixTagCompound(basket);
            require(basket.equals(once), "Legacy plant reload is idempotent");

            CompoundTag invalid = once.copy();
            CompoundTag child = invalid.getCompoundOrEmpty("tag").getListOrEmpty("Items").getCompoundOrEmpty(0);

            child.putString("id", "biomesoplenty:sapling_0");
            child.remove("count");
            child.remove("components");
            child.putByte("Count", (byte) 23);
            child.putShort("Damage", (short) 16);

            CompoundTag before = invalid.copy();
            boolean stopped = false;

            try {
                recovery.fixTagCompound(invalid);
            } catch (IllegalStateException expected) {
                stopped = true;
            }

            require(stopped && before.equals(invalid), "Unknown plant variant stops atomically");
        }

        LOGGER.info("BOP_PLANT_RECOVERY_PASS names=true NBT=true slots=true reload=true malformed=blocked");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
