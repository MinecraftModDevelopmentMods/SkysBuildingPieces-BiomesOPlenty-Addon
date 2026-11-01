package zone.moddev.mc.skysbuildingpiecesbop.basket;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.BonemealableBlock;

import net.minecraft.world.level.block.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.IForgeShearable;

/** A basket keeps its inventory on the owning stack, including when it is dropped. */
public final class BasketInventory extends SimpleContainer {
    private final ItemStack owner;
    private boolean loading = true;

    public BasketInventory(ItemStack owner) {
        super(BasketData.SIZE);
        this.owner = owner;

        CompoundTag tag = owner.hasTag() ? owner.getTag() : new CompoundTag();

        // Old contents remain recoverable even when the current insertion rules would reject them.
        BasketData.validate(tag);

        ListTag entries = tag.getList("Items", 10);

        for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
            CompoundTag entry = entries.getCompound(entryIndex).copy();

            if (BasketData.isBasket(entry.getString("id"))) {
                entry.putString("id", BasketData.ID);
            }

            setItem(entry.getInt("Slot"), ItemStack.of(entry));
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

        Block block = Block.byItem(stack.getItem());

        return block instanceof IPlantable || block instanceof BonemealableBlock || block instanceof IForgeShearable;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return accepts(stack);
    }

    @Override
    public void setChanged() {
        super.setChanged();

        if (loading || owner == null) {
            return;
        }

        CompoundTag tag = owner.hasTag() ? owner.getTag() : new CompoundTag();
        ListTag entries = new ListTag();

        for (int slot = 0; slot < getContainerSize(); slot++) {
            ItemStack stack = getItem(slot);

            if (!stack.isEmpty()) {
                CompoundTag entry = stack.save(new CompoundTag());

                entry.putByte("Slot", (byte) slot);
                entries.add(entry);
            }
        }

        tag.put("Items", entries);
        owner.setTag(tag);
    }

    /** Returns what did not fit; the caller's stack is never mutated. */
    public ItemStack insert(ItemStack incoming) {
        ItemStack remainder = incoming.copy();

        if (!accepts(remainder)) {
            return remainder;
        }
        // Fill matching stacks first, then use empty slots without changing the caller's stack.
        for (int pass = 0; pass < 2 && !remainder.isEmpty(); pass++) {
            for (int slot = 0; slot < getContainerSize() && !remainder.isEmpty(); slot++) {
                ItemStack stored = getItem(slot);

                if (pass == 0) {
                    if (stored.isEmpty() || !stored.sameItem(remainder)
                            || !ItemStack.tagMatches(stored, remainder)) {
                        continue;
                    }

                    int count = Math.min(remainder.getCount(),
                            Math.min(getMaxStackSize(), stored.getMaxStackSize()) - stored.getCount());

                    if (count > 0) {
                        stored.grow(count);
                        remainder.shrink(count);
                        setChanged();
                    }
                } else if (stored.isEmpty()) {
                    int count = Math.min(remainder.getCount(),
                            Math.min(getMaxStackSize(), remainder.getMaxStackSize()));
                    ItemStack placed = remainder.copy();

                    placed.setCount(count);
                    setItem(slot, placed);
                    remainder.shrink(count);
                }
            }
        }

        return remainder;
    }
}
