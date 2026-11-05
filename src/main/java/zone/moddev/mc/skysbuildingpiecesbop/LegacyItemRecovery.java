package zone.moddev.mc.skysbuildingpiecesbop;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Inventory;

import java.util.*;
import net.minecraft.world.item.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import zone.moddev.mc.skysbuildingpiecesbop.basket.BasketData;

/** Recover retired BOP items without aliasing existing registry objects or losing multiplied output. */
@Mod.EventBusSubscriber(
        modid = SkysBuildingPiecesBop.ID,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LegacyItemRecovery {
    private static final String[] OLD = { "biomesoplenty:turnip_seeds", "biomesoplenty:pinecone",
            "biomesoplenty:dull_flower_band" };
    private static final String[] TARGET = { "minecraft:beetroot_seeds", "minecraft:brown_dye",
            "minecraft:oxeye_daisy" };
    private static final int[] MULTIPLIER = { 1, 2, 8 };
    public static final Item[] HOLDERS = new Item[3];
    private static void createHolders() {
        for (int holderIndex = 0; holderIndex < HOLDERS.length; holderIndex++) {
            final int targetIndex = holderIndex;

            HOLDERS[holderIndex] = new Item(new Item.Properties()) {
                @Override
                public net.minecraft.network.chat.Component getName(ItemStack stack) {
                    Item target = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(TARGET[targetIndex]));

                    return target == null || target == net.minecraft.world.item.Items.AIR
                            ? net.minecraft.network.chat.Component.translatable(
                                    "item.skysbuildingpiecesbop.retired_item")
                            : new ItemStack(target).getHoverName();
                }
            };
        }
    }

    public CompoundTag fixTagCompound(CompoundTag root) {
        // The core wraps saved staging states alone before vanilla can run a data fix.
        if (isOldStagingGrass(root)) {
            root.getCompound("BlockState").putString("Name", "minecraft:short_grass");
            return root;
        }
        if (!isRelevant(root)) {
            return root;
        }

        CompoundTag copy = root.copy();

        NamedBopRecovery.rewrite(copy);
        LegacyPlantItems.rewrite(copy, false, 0);
        rewrite(copy, 0, 0);
        // Substitutions run before basket validation, including plants kept in old baskets.
        new BasketData().fixTagCompound(copy);

        for (String key : copy.getAllKeys()) {
            root.put(key, copy.get(key));
        }

        return root;
    }

    public static String target(int index) {
        return TARGET[index];
    }

    private static int index(String legacyItemId) {
        for (int legacyItemIndex = 0; legacyItemIndex < OLD.length; legacyItemIndex++) {
            if (OLD[legacyItemIndex].equals(legacyItemId)
                    || ForgeRegistries.ITEMS.getKey(HOLDERS[legacyItemIndex]).toString().equals(legacyItemId)) {
                return legacyItemIndex;
            }
        }

        return -1;
    }

    public boolean isRelevant(CompoundTag root) {
        return isOldStagingGrass(root) || related(root, 0) || NamedBopRecovery.relevant(root);
    }

    private static boolean isOldStagingGrass(CompoundTag root) {
        return root.size() == 1 && root.getCompound("BlockState").getString("Name").equals("minecraft:grass");
    }

    private static boolean related(Tag value, int depth) {
        depth(depth);

        if (value instanceof CompoundTag) {
            CompoundTag compoundTag = (CompoundTag) value;

            if (compoundTag.contains("id", 8)
                    && (index(compoundTag.getString("id")) >= 0 || BasketData.isBasket(compoundTag.getString("id"))
                            || LegacyPlantItems.matches(compoundTag.getString("id")))) {
                return true;
            }
            for (String key : compoundTag.getAllKeys()) {
                if (related(compoundTag.get(key), depth + 1)) {
                    return true;
                }
            }
        } else if (value instanceof ListTag) {
            for (Tag child : (ListTag) value) {
                if (related(child, depth + 1)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static void rewrite(Tag value, int basketSlots, int depth) {
        depth(depth);

        if (value instanceof CompoundTag) {
            CompoundTag compoundTag = (CompoundTag) value;

            if (index(compoundTag.getString("id")) >= 0) {
                List<CompoundTag> parts = convert(compoundTag);

                if (parts.size() != 1) {
                    throw invalid(
                            "a stack needs extra slots outside a supported inventory; split it in the original world first");
                }

                replace(compoundTag, parts.get(0));
            }

            String registryId = compoundTag.getString("id");

            if ((registryId.equals("ItemFrame") || registryId.equals("minecraft:item_frame"))
                    && compoundTag.contains("Item", 10)
                    && index(compoundTag.getCompound("Item").getString("id")) >= 0) {
                int legacyItemIndex = index(compoundTag.getCompound("Item").getString("id"));

                if (BasketData.count(compoundTag.getCompound("Item")) * MULTIPLIER[legacyItemIndex] > 1) {
                    throw invalid(
                            "remove the retired item from its item frame in the original world first; a frame cannot retain multiple replacement items");
                }
            }
            if (BasketData.isBasket(registryId) && compoundTag.contains("components", 10)) {
                rewrite(BasketData.savedCustomData(compoundTag), BasketData.SIZE, depth + 1);
            }
            for (String key : compoundTag.getAllKeys()) {
                Tag child = compoundTag.get(key);

                if (child instanceof ListTag) {
                    rewriteList((ListTag) child, capacity(compoundTag, key, basketSlots), depth + 1);
                } else {
                    rewrite(child, key.equals("tag") && BasketData.isBasket(registryId) ? BasketData.SIZE : 0,
                            depth + 1);
                }
            }
        } else if (value instanceof ListTag) {
            rewriteList((ListTag) value, 0, depth + 1);
        }
    }

    private static void rewriteList(ListTag list, int capacity, int depth) {
        depth(depth);

        BitSet occupied = new BitSet();

        if (capacity > 0) {
            for (Tag value : list) {
                if (value instanceof CompoundTag) {
                    CompoundTag compoundTag = (CompoundTag) value;

                    if (compoundTag.contains("Slot", 99)) {
                        int slot = compoundTag.getInt("Slot");

                        if (slot >= 0 && slot < capacity && !occupied.get(slot)) {
                            occupied.set(slot);
                        } else if (slot >= 0 && slot < capacity) {
                            throw invalid("duplicate inventory slot " + slot);
                        }
                    }
                }
            }
        }

        List<CompoundTag> extras = new ArrayList<CompoundTag>();

        for (Tag value : list) {
            if (value instanceof CompoundTag && index(((CompoundTag) value).getString("id")) >= 0) {
                CompoundTag compoundTag = (CompoundTag) value;
                List<CompoundTag> parts = convert(compoundTag);

                if (parts.size() > 1 && (capacity == 0 || !compoundTag.contains("Slot", 99))) {
                    throw invalid(
                            "a stack needs extra slots in an unknown inventory; split it in the original world first");
                }

                replace(compoundTag, parts.get(0));

                for (int stackPartIndex = 1; stackPartIndex < parts.size(); stackPartIndex++) {
                    int free = occupied.nextClearBit(0);

                    if (free >= capacity) {
                        throw invalid(
                                "not enough empty inventory slots for all replacement items; free a slot in the original world first");
                    }

                    occupied.set(free);

                    CompoundTag extra = parts.get(stackPartIndex);

                    extra.putByte("Slot", (byte) free);
                    extras.add(extra);
                }
            }

            rewrite(value, 0, depth + 1);
        }
        for (CompoundTag extra : extras) {
            list.add(extra);
        }
    }

    private static int capacity(CompoundTag owner, String key, int basketSlots) {
        if (key.equals("EnderItems")) {
            return 27;
        }
        if (key.equals("Inventory") && (owner.contains("abilities", 10) || owner.contains("EnderItems", 9))) {
            return 36;
        }
        if (!key.equals("Items")) {
            return 0;
        }
        if (basketSlots > 0) {
            return basketSlots;
        }

        String registryId = owner.getString("id");

        if (registryId.equals("Chest") || registryId.equals("minecraft:chest")
                || registryId.equals("minecraft:trapped_chest")) {
            return 27;
        }

        return 0;
    }

    private static List<CompoundTag> convert(CompoundTag old) {
        int legacyItemIndex = index(old.getString("id"));

        int savedCount = BasketData.count(old);
        if (savedCount <= 0 || savedCount > 99
                || old.contains("Damage") && !integer(old, "Damage")) {
            throw invalid("invalid saved stack for " + old.getString("id"));
        }
        if (old.contains("tag") && !old.contains("tag", 10)
                || old.contains("ForgeCaps") && !old.contains("ForgeCaps", 10)) {
            throw invalid("malformed saved item data for " + old.getString("id"));
        }

        Item target = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(TARGET[legacyItemIndex]));

        if (target == null || target == net.minecraft.world.item.Items.AIR) {
            throw invalid("replacement " + TARGET[legacyItemIndex] + " is unavailable for " + OLD[legacyItemIndex]
                    + "; install its original supported mod before upgrading");
        }

        boolean legacy = old.contains("Count");
        int remaining = savedCount * MULTIPLIER[legacyItemIndex];
        List<CompoundTag> output = new ArrayList<CompoundTag>();

        while (remaining > 0) {
            CompoundTag part = old.copy();

            part.putString("id", TARGET[legacyItemIndex]);
            if (legacy) {
                part.remove("Damage");
            }

            // A different item may not implement the old capabilities. Keep an exact archive rather than rely on its
            // serializer retaining them.
            if (old.contains("ForgeCaps", 10)) {
                CompoundTag tag = BasketData.savedCustomData(part);
                String archive = SkysBuildingPiecesBop.ID + ":legacy_capabilities";

                if (tag.contains(archive)) {
                    throw invalid("existing legacy capability archive would be overwritten");
                }

                tag.put(archive, old.getCompound("ForgeCaps").copy());
                if (legacy) {
                    part.put("tag", tag);
                } else {
                    CompoundTag components = part.getCompound("components");
                    components.put("minecraft:custom_data", tag);
                    part.put("components", components);
                }
            }

            int count = Math.min(remaining, BasketData.stackLimit(part, target));

            if (count <= 0) {
                throw invalid("replacement has no usable stack capacity: " + TARGET[legacyItemIndex]);
            }

            if (legacy) {
                part.putByte("Count", (byte) count);
            } else {
                part.putInt("count", count);
            }
            output.add(part);
            remaining -= count;
        }

        return output;
    }

    private static void replace(CompoundTag targetData, CompoundTag from) {
        for (String key : from.getAllKeys()) {
            targetData.put(key, from.get(key));
        }
    }

    private static boolean integer(CompoundTag compoundTag, String key) {
        return compoundTag.contains(key, 99) && compoundTag.get(key).getId() >= 1 && compoundTag.get(key).getId() <= 3;
    }

    private static void depth(int value) {
        if (value > 64) {
            throw invalid("saved inventory nesting exceeds 64 levels");
        }
    }

    private static IllegalStateException invalid(String reason) {
        return new IllegalStateException("Retired BOP item recovery stopped: " + reason
                + ". Restore the original backup; no items may be discarded.");
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        if (!event.getRegistryKey().equals(ForgeRegistries.Keys.ITEMS)) return;
        createHolders();
        for (int index = 0; index < HOLDERS.length; index++) {
            ForgeRegistries.ITEMS.register(ResourceLocation.fromNamespaceAndPath(SkysBuildingPiecesBop.ID, "legacy_" + OLD[index].substring(14)), HOLDERS[index]);
        }
    }

    public static void missing(MissingMappingsEvent event) {
        for (MissingMappingsEvent.Mapping<Item> mapping : event.getAllMappings(ForgeRegistries.Keys.ITEMS)) {
            int legacyItemIndex = index(mapping.getKey().toString());

            if (legacyItemIndex >= 0) {
                mapping.remap(HOLDERS[legacyItemIndex]);
            }
        }
    }
}
