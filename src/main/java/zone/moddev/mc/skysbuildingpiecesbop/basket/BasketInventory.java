package zone.moddev.mc.skysbuildingpiecesbop.basket;

import net.minecraft.block.*;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.IShearable;

/** A basket keeps its inventory on the owning stack, including when it is dropped. */
public final class BasketInventory extends InventoryBasic {
    private final ItemStack owner;
    private boolean loading = true;

    public BasketInventory(ItemStack owner) {
        super("container.skysbuildingpiecesbop.flower_basket", false, BasketData.SIZE);
        this.owner = owner;

        NBTTagCompound tag = owner.hasTagCompound() ? owner.getTagCompound() : new NBTTagCompound();

        // Old contents remain recoverable even when the current insertion rules would reject them.
        BasketData.validate(tag);

        NBTTagList entries = tag.getTagList("Items", 10);

        for (int entryIndex = 0; entryIndex < entries.tagCount(); entryIndex++) {
            NBTTagCompound entry = entries.getCompoundTagAt(entryIndex).copy();

            if (BasketData.isBasket(entry.getString("id"))) {
                entry.setString("id", BasketData.ID);
            }

            setInventorySlotContents(entry.getInteger("Slot"), new ItemStack(entry));
        }
        if (owner.hasDisplayName()) {
            setCustomName(owner.getDisplayName());
        } else if (tag.hasKey("CustomName", 8)) {
            setCustomName(tag.getString("CustomName"));
        }

        loading = false;
    }

    public ItemStack owner() {
        return owner;
    }

    public static boolean accepts(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() instanceof BasketItem) {
            return false;
        }

        Block block = Block.getBlockFromItem(stack.getItem());

        return block instanceof IPlantable || block instanceof IGrowable || block instanceof IShearable;
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return accepts(stack);
    }

    @Override
    public void markDirty() {
        super.markDirty();

        if (loading || owner == null) {
            return;
        }

        NBTTagCompound tag = owner.hasTagCompound() ? owner.getTagCompound() : new NBTTagCompound();
        NBTTagList entries = new NBTTagList();

        for (int slot = 0; slot < getSizeInventory(); slot++) {
            ItemStack stack = getStackInSlot(slot);

            if (!stack.isEmpty()) {
                NBTTagCompound entry = stack.writeToNBT(new NBTTagCompound());

                entry.setByte("Slot", (byte) slot);
                entries.appendTag(entry);
            }
        }

        tag.setTag("Items", entries);
        owner.setTagCompound(tag);
    }

    /** Returns what did not fit; the caller's stack is never mutated. */
    public ItemStack insert(ItemStack incoming) {
        ItemStack remainder = incoming.copy();

        if (!accepts(remainder)) {
            return remainder;
        }
        // Fill matching stacks first, then use empty slots without changing the caller's stack.
        for (int pass = 0; pass < 2 && !remainder.isEmpty(); pass++) {
            for (int slot = 0; slot < getSizeInventory() && !remainder.isEmpty(); slot++) {
                ItemStack stored = getStackInSlot(slot);

                if (pass == 0) {
                    if (stored.isEmpty() || !stored.isItemEqual(remainder)
                            || !ItemStack.areItemStackTagsEqual(stored, remainder)) {
                        continue;
                    }

                    int count = Math.min(remainder.getCount(),
                            Math.min(getInventoryStackLimit(), stored.getMaxStackSize()) - stored.getCount());

                    if (count > 0) {
                        stored.grow(count);
                        remainder.shrink(count);
                        markDirty();
                    }
                } else if (stored.isEmpty()) {
                    int count = Math.min(remainder.getCount(),
                            Math.min(getInventoryStackLimit(), remainder.getMaxStackSize()));
                    ItemStack placed = remainder.copy();

                    placed.setCount(count);
                    setInventorySlotContents(slot, placed);
                    remainder.shrink(count);
                }
            }
        }

        return remainder;
    }
}
