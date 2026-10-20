package zone.moddev.mc.skysbuildingpiecesbop.probe;

import java.io.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import zone.moddev.mc.skysbuildingpiecesbop.LegacyItemRecovery;

/** Compare genuine original stacks after the normal chunk loader, including a delayed second chunk. */
public final class RetiredItemUpgrade {
    public static void run(WorldServer world, String phase) throws Exception {
        NBTTagCompound expected;

        try (InputStream input = new FileInputStream(
                new File(world.getSaveHandler().getWorldDirectory().getParentFile(), "expected-retired-items.dat"))) {
            expected = CompressedStreamTools.readCompressed(input);
        }

        NBTTagList source = expected.getTagList("Items", 10);
        int split = (source.tagCount() + 1) / 2;
        boolean reload = phase.endsWith("reload");
        int units = 0;

        for (int group = 0; group < (reload ? 2 : 1); group++) {
            int blockX = 299 + 1024 * group;
            int blockZ = -22 + 1024 * group;

            world.getChunk(blockX >> 4, blockZ >> 4);

            TileEntityChest chest = (TileEntityChest) world.getTileEntity(new BlockPos(blockX, 69, blockZ));

            if (chest == null) {
                throw new AssertionError("Retired-item source chest lost");
            }

            int start = group == 0 ? 0 : split;
            int end = group == 0 ? split : source.tagCount();

            for (int entryIndex = start; entryIndex < end; entryIndex++) {
                NBTTagCompound raw = source.getCompoundTagAt(entryIndex).copy();

                new LegacyItemRecovery().fixTagCompound(raw);

                ItemStack target = new ItemStack(raw);
                ItemStack actual = chest.getStackInSlot(entryIndex - start);

                if (!ItemStack.areItemStacksEqual(target, actual)) {
                    throw new AssertionError(
                            "Retired-item fixture changed identity, quantity or NBT: " + actual + " != " + target);
                }

                units += actual.getCount();
            }
            if (group == 0) {
                int expanded = 0;
                int current = 0;

                for (int slot = 0; slot < chest.getSizeInventory(); slot++) {
                    ItemStack stack = chest.getStackInSlot(slot);

                    if (stack.hasTagCompound() && stack.getTagCompound().hasKey("Fixture", 8)) {
                        if (stack.getCount() > 64 || stack.getTagCompound().getInteger("Unrelated") != 719) {
                            throw new AssertionError("Expanded item payload changed");
                        }
                        if (stack.getTagCompound().getString("Fixture").equals("stack-expansion")) {
                            expanded += stack.getCount();
                        }
                        if (stack.getTagCompound().getString("Fixture").equals("current-dye")) {
                            current += stack.getCount();
                        }
                    }
                }
                if (expanded != 128 || current != 9) {
                    throw new AssertionError(
                            "Stack expansion or mixed current dye changed: " + expanded + " / " + current);
                }
            }
        }
        if (reload && units != 77) {
            throw new AssertionError("Expected exactly 1 seed + 68 dyes + 8 clover, got " + units);
        }

        System.out.println(
                "BOP_RETIRED_ITEM_UPGRADE_PASS phase=" + phase + " units=" + units + " delayed_chunk=" + reload);
    }
}
