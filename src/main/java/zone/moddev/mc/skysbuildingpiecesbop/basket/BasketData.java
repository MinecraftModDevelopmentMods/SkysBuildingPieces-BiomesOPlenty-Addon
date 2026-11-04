package zone.moddev.mc.skysbuildingpiecesbop.basket;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.item.Item;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.resources.ResourceLocation;
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

    public CompoundTag fixTagCompound(CompoundTag root) {
        if (!containsBasket(root, 0)) {
            return root;
        }

        // Validate a copy first: a bad nested entry must not leave half a conversion.
        CompoundTag checked = root.copy();

        rewrite(checked, 0);

        for (String key : checked.getAllKeys()) {
            root.put(key, checked.get(key));
        }

        return root;
    }

    private static boolean containsBasket(Tag value, int depth) {
        if (depth > 64) {
            throw invalid("inventory nesting exceeds 64 levels");
        }
        if (value instanceof CompoundTag) {
            CompoundTag compoundTag = (CompoundTag) value;

            if (compoundTag.contains("id", 8) && isBasket(compoundTag.getString("id"))) {
                return true;
            }
            for (String key : compoundTag.getAllKeys()) {
                if (containsBasket(compoundTag.get(key), depth + 1)) {
                    return true;
                }
            }
        } else if (value instanceof ListTag) {
            for (Tag child : (ListTag) value) {
                if (containsBasket(child, depth + 1)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static void rewrite(Tag value, int depth) {
        if (depth > 64) {
            throw invalid("inventory nesting exceeds 64 levels");
        }
        if (value instanceof CompoundTag) {
            CompoundTag compoundTag = (CompoundTag) value;

            if (compoundTag.contains("id", 8) && isBasket(compoundTag.getString("id"))) {
                if (count(compoundTag) != 1 || compoundTag.contains("Damage")
                        && (!integer(compoundTag, "Damage") || compoundTag.getInt("Damage") != 0)) {
                    throw invalid("a basket must have count 1 and damage 0");
                }
                CompoundTag tag = savedCustomData(compoundTag);

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
        } else if (value instanceof ListTag) {
            for (Tag child : (ListTag) value) {
                rewrite(child, depth + 1);
            }
        }
    }

    /** Reject malformed entries and duplicate slots before an item loader can discard stored contents. */
    public static void validate(CompoundTag tag) {
        if (!tag.contains("Items")) {
            return;
        }
        if (!tag.contains("Items", 9)) {
            throw invalid("Items is not a list");
        }

        ListTag list = tag.getList("Items", 10);
        ListTag raw = (ListTag) tag.get("Items");

        if (raw.size() != list.size()) {
            throw invalid("Items contains non-compound entries");
        }

        Set<Integer> occupied = new HashSet<Integer>();

        for (int entryIndex = 0; entryIndex < list.size(); entryIndex++) {
            CompoundTag entry = list.getCompound(entryIndex);

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

    private static void validateStack(CompoundTag stack, String where, int depth) {
        if (depth > 64) {
            throw invalid("nested item data exceeds 64 levels");
        }
        if (!stack.contains("id", 8) || count(stack) <= 0
                || stack.contains("Damage") && !integer(stack, "Damage")) {
            throw invalid(where + " has an invalid item stack (" + stack.getAllKeys() + ")");
        }
        savedCustomData(stack);
        if (stack.contains("ForgeCaps") && !stack.contains("ForgeCaps", 10)) {
            throw invalid(where + " has malformed capability data");
        }

        String registryId = stack.getString("id");
        Item item;

        try {
            item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(isBasket(registryId) ? ID : registryId));
        } catch (RuntimeException failure) {
            throw invalid(where + " has an invalid item ID: " + registryId);
        }
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            throw invalid(where + " has an unavailable item: " + registryId);
        }

        if (count(stack) > stackLimit(stack, item)) {
            throw invalid(where + " exceeds the stack limit for " + registryId);
        }

        validateChildren(stack.get("tag"), where, depth + 1);
        validateChildren(stack.get("ForgeCaps"), where, depth + 1);
        validateChildren(stack.get("components"), where, depth + 1);
    }

    private static void validateChildren(Tag value, String where, int depth) {
        if (depth > 64) {
            throw invalid("nested item data exceeds 64 levels");
        }
        if (value instanceof CompoundTag) {
            CompoundTag compoundTag = (CompoundTag) value;

            if (compoundTag.contains("id", 8) && (compoundTag.contains("Count") || compoundTag.contains("count")
                    || compoundTag.contains("components"))) {
                validateStack(compoundTag, where + " / " + compoundTag.getString("id"), depth + 1);
            } else {
                for (String key : compoundTag.getAllKeys()) {
                    validateChildren(compoundTag.get(key), where, depth + 1);
                }
            }
        } else if (value instanceof ListTag) {
            for (Tag child : (ListTag) value) {
                validateChildren(child, where, depth + 1);
            }
        }
    }

    public static CompoundTag customData(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static CompoundTag savedCustomData(CompoundTag stack) {
        if (stack.contains("tag") && !stack.contains("tag", Tag.TAG_COMPOUND)
                || stack.contains("components") && !stack.contains("components", Tag.TAG_COMPOUND)) {
            throw invalid("item data is not a compound");
        }
        CompoundTag components = stack.getCompound("components");
        if (components.contains("minecraft:custom_data")
                && !components.contains("minecraft:custom_data", Tag.TAG_COMPOUND)) {
            throw invalid("custom data is not a compound");
        }
        return stack.contains("Count") ? stack.getCompound("tag") : components.getCompound("minecraft:custom_data");
    }

    public static int count(CompoundTag stack) {
        if (stack.contains("Count")) {
            return integer(stack, "Count") && !stack.contains("count") && !stack.contains("components")
                    ? stack.getInt("Count") : -1;
        }
        if (stack.contains("tag") || stack.contains("Damage")
                && (!integer(stack, "Damage") || stack.getInt("Damage") != 0)) {
            return -1;
        }
        return stack.contains("count") ? (integer(stack, "count") ? stack.getInt("count") : -1) : 1;
    }

    public static int stackLimit(CompoundTag stack, Item item) {
        CompoundTag components = stack.getCompound("components");
        if (components.contains("minecraft:max_stack_size")) {
            int limit = components.getInt("minecraft:max_stack_size");
            if (!integer(components, "minecraft:max_stack_size") || limit < 1 || limit > 99) {
                throw invalid("invalid maximum stack size for " + stack.getString("id"));
            }
            return limit;
        }
        return new ItemStack(item).getMaxStackSize();
    }

    static ItemStack readStack(CompoundTag entry, HolderLookup.Provider registries) {
        CompoundTag item = entry.copy();
        item.remove("Slot");
        if (isBasket(item.getString("id"))) {
            item.putString("id", ID);
        }
        if (item.contains("Count")) {
            // Vanilla upgrades the outer stack but cannot see an inventory stored in custom item data.
            CompoundTag oldCapabilities = item.getCompound("ForgeCaps");
            CompoundTag display = item.getCompound("tag").getCompound("display");
            if (display.contains("Name", Tag.TAG_STRING)) {
                String name = display.getString("Name");
                String trimmed = name.stripLeading();
                if (!trimmed.startsWith("{") && !trimmed.startsWith("[") && !trimmed.startsWith("\"")) {
                    display.putString("Name", new com.google.gson.JsonPrimitive(name).toString());
                }
            }
            item = (CompoundTag) DataFixers.getDataFixer()
                    .update(References.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, item), 3700,
                            SharedConstants.getCurrentVersion().getDataVersion().getVersion())
                    .getValue();
            if (!oldCapabilities.isEmpty()) {
                CompoundTag components = item.getCompound("components");
                CompoundTag custom = components.getCompound("minecraft:custom_data");
                String key = "skysbuildingpiecesbop:legacy_capabilities";
                if (custom.contains(key) && !oldCapabilities.equals(custom.get(key))) {
                    throw invalid("conflicting saved capability data for " + item.getString("id"));
                }
                custom.put(key, oldCapabilities);
                components.put("minecraft:custom_data", custom);
                item.put("components", components);
            }
        }
        ItemStack stack = ItemStack.parse(registries, item)
                .orElseThrow(() -> invalid("cannot read the item in slot " + entry.getInt("Slot")));
        if (stack.isEmpty() || stack.getCount() != count(entry) || stack.getCount() > stack.getMaxStackSize()) {
            throw invalid("invalid stack in slot " + entry.getInt("Slot"));
        }
        return stack;
    }

    private static IllegalStateException invalid(String reason) {
        return new IllegalStateException("Flower basket recovery stopped: " + reason
                + ". Restore the original world backup and required mods; no basket contents may be discarded.");
    }

    private static boolean integer(CompoundTag compoundTag, String key) {
        return compoundTag.contains(key, 99) && compoundTag.get(key).getId() >= 1 && compoundTag.get(key).getId() <= 3;
    }
}
