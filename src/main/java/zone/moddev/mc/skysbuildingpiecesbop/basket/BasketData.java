package zone.moddev.mc.skysbuildingpiecesbop.basket;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.item.Item;
import net.minecraft.nbt.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.datafix.IFixableData;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/** Reads the saved basket before an item serializer can discard its contents. */
public final class BasketData implements IFixableData {
    public static final int SIZE = 18;
    public static final String ID = "skysbuildingpiecesbop:flower_basket";
    public static final String HOLDER = "skysbuildingpiecesbop:legacy_bop_flower_basket";
    public static final String OLD = "biomesoplenty:flower_basket";

    public static boolean isBasket(String registryId) {
        return ID.equals(registryId) || HOLDER.equals(registryId) || OLD.equals(registryId);
    }

    @Override
    public int getFixVersion() {
        return 1;
    }

    @Override
    public NBTTagCompound fixTagCompound(NBTTagCompound root) {
        if (!containsBasket(root, 0)) {
            return root;
        }

        // Validate a copy first: a bad nested entry must not leave half a conversion.
        NBTTagCompound checked = root.copy();

        rewrite(checked, 0);

        for (String key : checked.getKeySet()) {
            root.setTag(key, checked.getTag(key));
        }

        return root;
    }

    private static boolean containsBasket(NBTBase value, int depth) {
        if (depth > 64) {
            throw invalid("inventory nesting exceeds 64 levels");
        }
        if (value instanceof NBTTagCompound) {
            NBTTagCompound compoundTag = (NBTTagCompound) value;

            if (compoundTag.hasKey("id", 8) && isBasket(compoundTag.getString("id"))) {
                return true;
            }
            for (String key : compoundTag.getKeySet()) {
                if (containsBasket(compoundTag.getTag(key), depth + 1)) {
                    return true;
                }
            }
        } else if (value instanceof NBTTagList) {
            for (NBTBase child : (NBTTagList) value) {
                if (containsBasket(child, depth + 1)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static void rewrite(NBTBase value, int depth) {
        if (depth > 64) {
            throw invalid("inventory nesting exceeds 64 levels");
        }
        if (value instanceof NBTTagCompound) {
            NBTTagCompound compoundTag = (NBTTagCompound) value;

            if (compoundTag.hasKey("id", 8) && isBasket(compoundTag.getString("id"))) {
                if (!integer(compoundTag, "Count") || compoundTag.getInteger("Count") != 1
                        || compoundTag.hasKey("Damage")
                                && (!integer(compoundTag, "Damage") || compoundTag.getInteger("Damage") != 0)) {
                    throw invalid("a basket must have count 1 and damage 0");
                }
                if (compoundTag.hasKey("tag") && !compoundTag.hasKey("tag", 10)) {
                    throw invalid("basket tag is not a compound");
                }

                NBTTagCompound tag = compoundTag.getCompoundTag("tag");

                validate(tag);
                compoundTag.setString("id", ID);

                // This was a transient GUI flag, not part of the stored inventory.
                if (tag.hasKey("BasketOpen")) {
                    tag.setBoolean("BasketOpen", false);
                }
            }
            for (String key : compoundTag.getKeySet()) {
                rewrite(compoundTag.getTag(key), depth + 1);
            }
        } else if (value instanceof NBTTagList) {
            for (NBTBase child : (NBTTagList) value) {
                rewrite(child, depth + 1);
            }
        }
    }

    /** Reject malformed entries and duplicate slots before an item loader can discard stored contents. */
    public static void validate(NBTTagCompound tag) {
        if (!tag.hasKey("Items")) {
            return;
        }
        if (!tag.hasKey("Items", 9)) {
            throw invalid("Items is not a list");
        }

        NBTTagList list = tag.getTagList("Items", 10);
        NBTTagList raw = (NBTTagList) tag.getTag("Items");

        if (raw.tagCount() != list.tagCount()) {
            throw invalid("Items contains non-compound entries");
        }

        Set<Integer> occupied = new HashSet<Integer>();

        for (int entryIndex = 0; entryIndex < list.tagCount(); entryIndex++) {
            NBTTagCompound entry = list.getCompoundTagAt(entryIndex);

            if (!integer(entry, "Slot")) {
                throw invalid("an inventory entry has no integral slot");
            }

            int slot = entry.getInteger("Slot");

            if (slot < 0 || slot >= SIZE || !occupied.add(slot)) {
                throw invalid("invalid or duplicate slot " + slot);
            }

            validateStack(entry, "slot " + slot, 0);
        }
    }

    private static void validateStack(NBTTagCompound stack, String where, int depth) {
        if (depth > 64) {
            throw invalid("nested item data exceeds 64 levels");
        }
        if (!stack.hasKey("id", 8) || !integer(stack, "Count") || stack.getInteger("Count") <= 0
                || stack.hasKey("Damage") && !integer(stack, "Damage")) {
            throw invalid(where + " has an invalid item stack");
        }
        if (stack.hasKey("tag") && !stack.hasKey("tag", 10)) {
            throw invalid(where + " has malformed item data");
        }
        if (stack.hasKey("ForgeCaps") && !stack.hasKey("ForgeCaps", 10)) {
            throw invalid(where + " has malformed capability data");
        }

        String registryId = stack.getString("id");
        Item item;

        try {
            item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(isBasket(registryId) ? ID : registryId));
        } catch (RuntimeException failure) {
            throw invalid(where + " has an invalid item ID: " + registryId);
        }
        if (item == null || item == net.minecraft.init.Items.AIR) {
            throw invalid(where + " has an unavailable item: " + registryId);
        }

        net.minecraft.item.ItemStack decoded = new net.minecraft.item.ItemStack(item, 1, stack.getInteger("Damage"));

        if (stack.hasKey("tag", 10)) {
            decoded.setTagCompound(stack.getCompoundTag("tag").copy());
        }
        if (stack.getInteger("Count") > decoded.getMaxStackSize()) {
            throw invalid(where + " exceeds the stack limit for " + registryId);
        }

        validateChildren(stack.getTag("tag"), where, depth + 1);
        validateChildren(stack.getTag("ForgeCaps"), where, depth + 1);
    }

    private static void validateChildren(NBTBase value, String where, int depth) {
        if (depth > 64) {
            throw invalid("nested item data exceeds 64 levels");
        }
        if (value instanceof NBTTagCompound) {
            NBTTagCompound compoundTag = (NBTTagCompound) value;

            if (compoundTag.hasKey("id", 8) && compoundTag.hasKey("Count", 99)) {
                validateStack(compoundTag, where + " / " + compoundTag.getString("id"), depth + 1);
            } else {
                for (String key : compoundTag.getKeySet()) {
                    validateChildren(compoundTag.getTag(key), where, depth + 1);
                }
            }
        } else if (value instanceof NBTTagList) {
            for (NBTBase child : (NBTTagList) value) {
                validateChildren(child, where, depth + 1);
            }
        }
    }

    private static IllegalStateException invalid(String reason) {
        return new IllegalStateException("Flower basket recovery stopped: " + reason
                + ". Restore the original world backup and required mods; no basket contents may be discarded.");
    }

    private static boolean integer(NBTTagCompound compoundTag, String key) {
        return compoundTag.hasKey(key, 99) && compoundTag.getTagId(key) >= 1 && compoundTag.getTagId(key) <= 3;
    }
}
