package zone.moddev.mc.skysbuildingpiecesbop;

import java.util.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.datafix.IFixableData;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import zone.moddev.mc.skysbuildingpiecesbop.basket.BasketData;

/** Recover retired BOP items without aliasing existing registry objects or losing multiplied output. */
@Mod.EventBusSubscriber(
        modid = SkysBuildingPiecesBop.ID)
public final class LegacyItemRecovery implements IFixableData {
    private static final String[] OLD = { "biomesoplenty:turnip_seeds", "biomesoplenty:pinecone",
            "biomesoplenty:dull_flower_band" };
    private static final String[] TARGET = { "harvestcraft:turnipseeditem", "biomesoplenty:brown_dye",
            "biomesoplenty:flower_0" };
    private static final int[] MULTIPLIER = { 1, 2, 8 };
    public static final Item[] HOLDERS = new Item[3];
    static {
        for (int holderIndex = 0; holderIndex < HOLDERS.length; holderIndex++) {
            final int targetIndex = holderIndex;

            HOLDERS[holderIndex] = new Item() {
                @Override
                public String getItemStackDisplayName(ItemStack stack) {
                    Item target = ForgeRegistries.ITEMS.getValue(new ResourceLocation(TARGET[targetIndex]));

                    return target == null || target == net.minecraft.init.Items.AIR ? "Retired BOP Item"
                            : new ItemStack(target, 1, 0).getDisplayName();
                }
            }.setRegistryName(SkysBuildingPiecesBop.ID, "legacy_" + OLD[holderIndex].substring(14));
        }
    }

    @Override
    public int getFixVersion() {
        return 2;
    }

    public IFixableData itemInstanceFix() {
        return new IFixableData() {
            @Override
            public int getFixVersion() {
                return 2;
            }

            @Override
            public NBTTagCompound fixTagCompound(NBTTagCompound stack) {
                // Vanilla may visit a child stack before our enclosing-inventory fix. Leave larger output intact until
                // that parent can allocate slots.
                if (index(stack.getString("id")) >= 0 && convert(stack).size() > 1) {
                    return stack;
                }

                return LegacyItemRecovery.this.fixTagCompound(stack);
            }
        };
    }

    @Override
    public NBTTagCompound fixTagCompound(NBTTagCompound root) {
        if (!related(root, 0)) {
            return root;
        }

        NBTTagCompound copy = root.copy();

        rewrite(copy, 0, 0);
        // Substitutions run before basket validation, including plants kept in old baskets.
        new BasketData().fixTagCompound(copy);

        for (String key : copy.getKeySet()) {
            root.setTag(key, copy.getTag(key));
        }

        return root;
    }

    public static String target(int index) {
        return TARGET[index];
    }

    private static int index(String legacyItemId) {
        for (int legacyItemIndex = 0; legacyItemIndex < OLD.length; legacyItemIndex++) {
            if (OLD[legacyItemIndex].equals(legacyItemId)
                    || HOLDERS[legacyItemIndex].getRegistryName().toString().equals(legacyItemId)) {
                return legacyItemIndex;
            }
        }

        return -1;
    }

    private static boolean related(NBTBase value, int depth) {
        depth(depth);

        if (value instanceof NBTTagCompound) {
            NBTTagCompound compoundTag = (NBTTagCompound) value;

            if (compoundTag.hasKey("id", 8)
                    && (index(compoundTag.getString("id")) >= 0 || BasketData.isBasket(compoundTag.getString("id")))) {
                return true;
            }
            for (String key : compoundTag.getKeySet()) {
                if (related(compoundTag.getTag(key), depth + 1)) {
                    return true;
                }
            }
        } else if (value instanceof NBTTagList) {
            for (NBTBase child : (NBTTagList) value) {
                if (related(child, depth + 1)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static void rewrite(NBTBase value, int basketSlots, int depth) {
        depth(depth);

        if (value instanceof NBTTagCompound) {
            NBTTagCompound compoundTag = (NBTTagCompound) value;

            if (index(compoundTag.getString("id")) >= 0) {
                List<NBTTagCompound> parts = convert(compoundTag);

                if (parts.size() != 1) {
                    throw invalid(
                            "a stack needs extra slots outside a supported inventory; split it in the original world first");
                }

                replace(compoundTag, parts.get(0));
            }

            String registryId = compoundTag.getString("id");

            if ((registryId.equals("ItemFrame") || registryId.equals("minecraft:item_frame"))
                    && compoundTag.hasKey("Item", 10)
                    && index(compoundTag.getCompoundTag("Item").getString("id")) >= 0) {
                int legacyItemIndex = index(compoundTag.getCompoundTag("Item").getString("id"));

                if (compoundTag.getCompoundTag("Item").getInteger("Count") * MULTIPLIER[legacyItemIndex] > 1) {
                    throw invalid(
                            "remove the retired item from its item frame in the original world first; a frame cannot retain multiple replacement items");
                }
            }
            for (String key : compoundTag.getKeySet()) {
                NBTBase child = compoundTag.getTag(key);

                if (child instanceof NBTTagList) {
                    rewriteList((NBTTagList) child, capacity(compoundTag, key, basketSlots), depth + 1);
                } else {
                    rewrite(child, key.equals("tag") && BasketData.isBasket(registryId) ? BasketData.SIZE : 0,
                            depth + 1);
                }
            }
        } else if (value instanceof NBTTagList) {
            rewriteList((NBTTagList) value, 0, depth + 1);
        }
    }

    private static void rewriteList(NBTTagList list, int capacity, int depth) {
        depth(depth);

        BitSet occupied = new BitSet();

        if (capacity > 0) {
            for (NBTBase value : list) {
                if (value instanceof NBTTagCompound) {
                    NBTTagCompound compoundTag = (NBTTagCompound) value;

                    if (compoundTag.hasKey("Slot", 99)) {
                        int slot = compoundTag.getInteger("Slot");

                        if (slot >= 0 && slot < capacity && !occupied.get(slot)) {
                            occupied.set(slot);
                        } else if (slot >= 0 && slot < capacity) {
                            throw invalid("duplicate inventory slot " + slot);
                        }
                    }
                }
            }
        }

        List<NBTTagCompound> extras = new ArrayList<NBTTagCompound>();

        for (NBTBase value : list) {
            if (value instanceof NBTTagCompound && index(((NBTTagCompound) value).getString("id")) >= 0) {
                NBTTagCompound compoundTag = (NBTTagCompound) value;
                List<NBTTagCompound> parts = convert(compoundTag);

                if (parts.size() > 1 && (capacity == 0 || !compoundTag.hasKey("Slot", 99))) {
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

                    NBTTagCompound extra = parts.get(stackPartIndex);

                    extra.setByte("Slot", (byte) free);
                    extras.add(extra);
                }
            }

            rewrite(value, 0, depth + 1);
        }
        for (NBTTagCompound extra : extras) {
            list.appendTag(extra);
        }
    }

    private static int capacity(NBTTagCompound owner, String key, int basketSlots) {
        if (key.equals("EnderItems")) {
            return 27;
        }
        if (key.equals("Inventory") && (owner.hasKey("abilities", 10) || owner.hasKey("EnderItems", 9))) {
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

    private static List<NBTTagCompound> convert(NBTTagCompound old) {
        int legacyItemIndex = index(old.getString("id"));

        if (!integer(old, "Count") || old.getInteger("Count") <= 0 || old.getInteger("Count") > 64
                || old.hasKey("Damage") && !integer(old, "Damage")) {
            throw invalid("invalid saved stack for " + old.getString("id"));
        }
        if (old.hasKey("tag") && !old.hasKey("tag", 10) || old.hasKey("ForgeCaps") && !old.hasKey("ForgeCaps", 10)) {
            throw invalid("malformed saved item data for " + old.getString("id"));
        }

        Item target = ForgeRegistries.ITEMS.getValue(new ResourceLocation(TARGET[legacyItemIndex]));

        if (target == null || target == net.minecraft.init.Items.AIR) {
            throw invalid("replacement " + TARGET[legacyItemIndex] + " is unavailable for " + OLD[legacyItemIndex]
                    + "; install its original supported mod before upgrading");
        }

        int remaining = old.getInteger("Count") * MULTIPLIER[legacyItemIndex];
        List<NBTTagCompound> output = new ArrayList<NBTTagCompound>();

        while (remaining > 0) {
            NBTTagCompound part = old.copy();

            part.setString("id", TARGET[legacyItemIndex]);
            part.setShort("Damage", (short) 0);

            // A different item may not implement the old capabilities. Keep an exact archive rather than rely on its
            // serializer retaining them.
            if (old.hasKey("ForgeCaps", 10)) {
                NBTTagCompound tag = part.getCompoundTag("tag");
                String archive = SkysBuildingPiecesBop.ID + ":legacy_capabilities";

                if (tag.hasKey(archive)) {
                    throw invalid("existing legacy capability archive would be overwritten");
                }

                tag.setTag(archive, old.getCompoundTag("ForgeCaps").copy());
                part.setTag("tag", tag);
            }

            ItemStack decoded = new ItemStack(target, 1, 0);

            if (part.hasKey("tag", 10)) {
                decoded.setTagCompound(part.getCompoundTag("tag").copy());
            }

            int count = Math.min(remaining, Math.min(64, decoded.getMaxStackSize()));

            if (count <= 0) {
                throw invalid("replacement has no usable stack capacity: " + TARGET[legacyItemIndex]);
            }

            part.setByte("Count", (byte) count);
            output.add(part);
            remaining -= count;
        }

        return output;
    }

    private static void replace(NBTTagCompound targetData, NBTTagCompound from) {
        for (String key : from.getKeySet()) {
            targetData.setTag(key, from.getTag(key));
        }
    }

    private static boolean integer(NBTTagCompound compoundTag, String key) {
        return compoundTag.hasKey(key, 99) && compoundTag.getTagId(key) >= 1 && compoundTag.getTagId(key) <= 3;
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
    public static void register(RegistryEvent.Register<Item> event) {
        event.getRegistry().registerAll(HOLDERS);
    }

    @SubscribeEvent
    public static void missing(RegistryEvent.MissingMappings<Item> event) {
        for (RegistryEvent.MissingMappings.Mapping<Item> mapping : event.getAllMappings()) {
            int legacyItemIndex = index(mapping.key.toString());

            if (legacyItemIndex >= 0) {
                mapping.remap(HOLDERS[legacyItemIndex]);
            }
        }
    }
}
