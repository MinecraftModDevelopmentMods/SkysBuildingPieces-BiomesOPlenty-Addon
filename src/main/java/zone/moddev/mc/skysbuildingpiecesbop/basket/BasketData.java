package zone.moddev.mc.skysbuildingpiecesbop.basket;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.item.Item;
import net.minecraft.nbt.*;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

/** Reads the saved basket before an item serializer can discard its contents. */
public final class BasketData {
    public static final int SIZE = 18;
    public static final String ID = "skysbuildingpiecesbop:flower_basket";
    public static final String HOLDER = "skysbuildingpiecesbop:legacy_bop_flower_basket";
    public static final String OLD = "biomesoplenty:flower_basket";

    public static boolean isBasket(String registryId) {
        return ID.equals(registryId) || HOLDER.equals(registryId) || OLD.equals(registryId);
    }

    public CompoundNBT fixTagCompound(CompoundNBT root) {
        if (!containsBasket(root, 0)) {
            return root;
        }

        // Validate a copy first: a bad nested entry must not leave half a conversion.
        CompoundNBT checked = root.copy();

        rewrite(checked, 0);

        for (String key : checked.getAllKeys()) {
            root.put(key, checked.get(key));
        }

        return root;
    }

    private static boolean containsBasket(INBT value, int depth) {
        if (depth > 64) {
            throw invalid("inventory nesting exceeds 64 levels");
        }
        if (value instanceof CompoundNBT) {
            CompoundNBT compoundTag = (CompoundNBT) value;

            if (compoundTag.contains("id", 8) && isBasket(compoundTag.getString("id"))) {
                return true;
            }
            for (String key : compoundTag.getAllKeys()) {
                if (containsBasket(compoundTag.get(key), depth + 1)) {
                    return true;
                }
            }
        } else if (value instanceof ListNBT) {
            for (INBT child : (ListNBT) value) {
                if (containsBasket(child, depth + 1)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static void rewrite(INBT value, int depth) {
        if (depth > 64) {
            throw invalid("inventory nesting exceeds 64 levels");
        }
        if (value instanceof CompoundNBT) {
            CompoundNBT compoundTag = (CompoundNBT) value;

            if (compoundTag.contains("id", 8) && isBasket(compoundTag.getString("id"))) {
                if (!integer(compoundTag, "Count") || compoundTag.getInt("Count") != 1 || compoundTag.contains("Damage")
                        && (!integer(compoundTag, "Damage") || compoundTag.getInt("Damage") != 0)) {
                    throw invalid("a basket must have count 1 and damage 0");
                }
                if (compoundTag.contains("tag") && !compoundTag.contains("tag", 10)) {
                    throw invalid("basket tag is not a compound");
                }

                CompoundNBT tag = compoundTag.getCompound("tag");

                validate(tag);
                compoundTag.putString("id", ID);

                // This was a transient GUI flag, not part of the stored inventory.
                if (tag.contains("BasketOpen")) {
                    tag.putBoolean("BasketOpen", false);
                }
            }
            for (String key : compoundTag.getAllKeys()) {
                rewrite(compoundTag.get(key), depth + 1);
            }
        } else if (value instanceof ListNBT) {
            for (INBT child : (ListNBT) value) {
                rewrite(child, depth + 1);
            }
        }
    }

    /** Reject malformed entries and duplicate slots before an item loader can discard stored contents. */
    public static void validate(CompoundNBT tag) {
        if (!tag.contains("Items")) {
            return;
        }
        if (!tag.contains("Items", 9)) {
            throw invalid("Items is not a list");
        }

        ListNBT list = tag.getList("Items", 10);
        ListNBT raw = (ListNBT) tag.get("Items");

        if (raw.size() != list.size()) {
            throw invalid("Items contains non-compound entries");
        }

        Set<Integer> occupied = new HashSet<Integer>();

        for (int entryIndex = 0; entryIndex < list.size(); entryIndex++) {
            CompoundNBT entry = list.getCompound(entryIndex);

            if (!integer(entry, "Slot")) {
                throw invalid("an inventory entry has no integral slot");
            }

            int slot = entry.getInt("Slot");

            if (slot < 0 || slot >= SIZE || !occupied.add(slot)) {
                throw invalid("invalid or duplicate slot " + slot);
            }

            validateStack(entry, "slot " + slot, 0);
        }
    }

    private static void validateStack(CompoundNBT stack, String where, int depth) {
        if (depth > 64) {
            throw invalid("nested item data exceeds 64 levels");
        }
        if (!stack.contains("id", 8) || !integer(stack, "Count") || stack.getInt("Count") <= 0
                || stack.contains("Damage") && !integer(stack, "Damage")) {
            throw invalid(where + " has an invalid item stack");
        }
        if (stack.contains("tag") && !stack.contains("tag", 10)) {
            throw invalid(where + " has malformed item data");
        }
        if (stack.contains("ForgeCaps") && !stack.contains("ForgeCaps", 10)) {
            throw invalid(where + " has malformed capability data");
        }

        String registryId = stack.getString("id");
        Item item;

        try {
            item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(isBasket(registryId) ? ID : registryId));
        } catch (RuntimeException failure) {
            throw invalid(where + " has an invalid item ID: " + registryId);
        }
        if (item == null || item == net.minecraft.item.Items.AIR) {
            throw invalid(where + " has an unavailable item: " + registryId);
        }

        net.minecraft.item.ItemStack decoded = net.minecraft.item.ItemStack.of(stack.copy());

        if (stack.contains("tag", 10)) {
            decoded.setTag(stack.getCompound("tag").copy());
        }
        if (stack.getInt("Count") > decoded.getMaxStackSize()) {
            throw invalid(where + " exceeds the stack limit for " + registryId);
        }

        validateChildren(stack.get("tag"), where, depth + 1);
        validateChildren(stack.get("ForgeCaps"), where, depth + 1);
    }

    private static void validateChildren(INBT value, String where, int depth) {
        if (depth > 64) {
            throw invalid("nested item data exceeds 64 levels");
        }
        if (value instanceof CompoundNBT) {
            CompoundNBT compoundTag = (CompoundNBT) value;

            if (compoundTag.contains("id", 8) && compoundTag.contains("Count", 99)) {
                validateStack(compoundTag, where + " / " + compoundTag.getString("id"), depth + 1);
            } else {
                for (String key : compoundTag.getAllKeys()) {
                    validateChildren(compoundTag.get(key), where, depth + 1);
                }
            }
        } else if (value instanceof ListNBT) {
            for (INBT child : (ListNBT) value) {
                validateChildren(child, where, depth + 1);
            }
        }
    }

    private static IllegalStateException invalid(String reason) {
        return new IllegalStateException("Flower basket recovery stopped: " + reason
                + ". Restore the original world backup and required mods; no basket contents may be discarded.");
    }

    private static boolean integer(CompoundNBT compoundTag, String key) {
        return compoundTag.contains(key, 99) && compoundTag.get(key).getId() >= 1 && compoundTag.get(key).getId() <= 3;
    }
}
