package zone.moddev.mc.skysbuildingpiecesbop.probe;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import zone.moddev.mc.skysbuildingpiecesbop.LegacyItemRecovery;
import zone.moddev.mc.skysbuildingpiecesbop.basket.BasketData;
import zone.moddev.mc.skysbuildingpiecesbop.basket.Baskets;

/** Old mod plants need vanilla's name and enchantment updates as well as a new ID. */
final class PlantRecoveryChecks {
    private PlantRecoveryChecks() {
    }

    static void run() {
        for (String source : new String[] { "biomesoplenty:sapling_0", "minecraft:sapling" }) {
            CompoundNBT basket = new ItemStack(Baskets.ITEM).write(new CompoundNBT());
            CompoundNBT tag = new CompoundNBT();
            CompoundNBT plant = new CompoundNBT();

            plant.putString("id", source);
            plant.putByte("Count", (byte) 23);
            plant.putByte("Slot", (byte) 4);
            plant.putShort("Damage", (short) 0);

            CompoundNBT custom = new CompoundNBT();
            CompoundNBT display = new CompoundNBT();

            display.putString("Name", "Saved tree");
            custom.put("display", display);
            custom.putString("unrelated", "Keep this data");
            plant.put("tag", custom);

            ListNBT contents = new ListNBT();

            contents.add(plant);
            tag.put("Items", contents);
            basket.put("tag", tag);

            LegacyItemRecovery recovery = new LegacyItemRecovery();

            recovery.fixTagCompound(basket);

            CompoundNBT converted = basket.getCompound("tag").getList("Items", 10).getCompound(0);
            ItemStack decoded = ItemStack.read(converted);

            require(decoded.getDisplayName().getString().equals("Saved tree"), "Legacy plant name is readable");
            require(decoded.getCount() == 23 && converted.getByte("Slot") == 4, "Legacy slot and count");
            require(decoded.getTag().getString("unrelated").equals("Keep this data"), "Unrelated plant NBT");

            CompoundNBT once = basket.copy();

            recovery.fixTagCompound(basket);
            require(basket.equals(once), "Legacy plant reload is idempotent");

            CompoundNBT invalid = once.copy();
            CompoundNBT child = invalid.getCompound("tag").getList("Items", 10).getCompound(0);

            child.putString("id", "biomesoplenty:sapling_0");
            child.putShort("Damage", (short) 16);

            CompoundNBT before = invalid.copy();
            boolean stopped = false;

            try {
                recovery.fixTagCompound(invalid);
            } catch (IllegalStateException expected) {
                stopped = true;
            }

            require(stopped && before.equals(invalid), "Unknown plant variant stops atomically");
        }

        System.out.println("BOP_PLANT_RECOVERY_PASS names=true NBT=true slots=true reload=true malformed=blocked");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
