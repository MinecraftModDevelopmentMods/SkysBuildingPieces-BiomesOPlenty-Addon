package zone.moddev.mc.skysbuildingpiecesbop.probe;

import net.minecraft.world.WorldServer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.BlockPos;
import zone.moddev.mc.skysbuildingpiecesbop.basket.*;

/** Loads genuine old basket stacks, leaving one old chunk unopened until the next start. */
public final class BasketUpgrade {
    public static void run(WorldServer world, String phase) throws Exception {
        boolean reload = phase.endsWith("reload");
        int baskets = 0;
        int units = 0;

        for (int group = 0; group < (reload ? 2 : 1); group++) {
            int blockX = 299 + 1024 * group;
            int blockZ = -22 + 1024 * group;

            world.getChunk(blockX >> 4, blockZ >> 4);

            TileEntityChest chest = (TileEntityChest) world.getTileEntity(new BlockPos(blockX, 69, blockZ));

            if (chest == null) {
                throw new AssertionError("Basket fixture chest lost at " + blockX + ", " + blockZ + ": "
                        + world.getBlockState(new BlockPos(blockX, 69, blockZ)));
            }
            for (int slot = 0; slot < 4; slot++) {
                ItemStack stack = chest.getStackInSlot(slot);

                if (stack.getItem() != Baskets.ITEM) {
                    throw new AssertionError("Basket fixture item identity: " + stack);
                }

                BasketInventory inventory = new BasketInventory(stack);

                baskets++;

                for (int entryIndex = 0; entryIndex < 18; entryIndex++) {
                    units += inventory.getStackInSlot(entryIndex).getCount();
                }
            }
        }

        int expected = reload ? 1500 : 687;

        if (units != expected) {
            throw new AssertionError("Basket fixture units: " + units + " expected " + expected);
        }

        System.out.println("BOP_BASKET_UPGRADE_PASS phase=" + phase + " baskets=" + baskets + " units=" + units
                + " delayed_chunk=" + reload);
    }
}
