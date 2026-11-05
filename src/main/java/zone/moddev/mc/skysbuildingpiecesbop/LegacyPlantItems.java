package zone.moddev.mc.skysbuildingpiecesbop;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.SharedConstants;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import zone.moddev.mc.skysbuildingpiecesbop.basket.BasketData;

/** Update saved BOP item identities before a basket or container reads its inventory. */
final class LegacyPlantItems {
    private static final JsonObject PLANTS = readMappings();

    private LegacyPlantItems() {
    }

    private static JsonObject readMappings() {
        JsonObject mappings = read("legacy_plants.json");

        for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : read("legacy_items.json").entrySet()) {
            if (mappings.has(entry.getKey())) {
                throw new IllegalStateException("Duplicate saved BOP item mapping: " + entry.getKey());
            }

            mappings.add(entry.getKey(), entry.getValue());
        }

        return mappings;
    }

    private static JsonObject read(String name) {
        try (InputStreamReader reader = new InputStreamReader(
                LegacyPlantItems.class.getResourceAsStream("/assets/skysbuildingpiecesbop/catalogue/" + name),
                StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, JsonObject.class);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot read saved BOP item mappings: " + name, failure);
        }
    }

    static boolean matches(String identifier) {
        return PLANTS.has(identifier);
    }

    static void rewrite(Tag value, boolean basketContents, int depth) {
        if (depth > 64) {
            throw new IllegalStateException("Saved plant inventory nesting exceeds 64 levels");
        }
        if (value instanceof CompoundTag) {
            CompoundTag compound = (CompoundTag) value;
            String identifier = compound.getString("id");
            boolean basket = BasketData.isBasket(identifier);
            String nativeTarget = null;

            if (basketContents && identifier.equals("minecraft:grass") && compound.contains("Count", 99)) {
                compound.putString("id", "minecraft:short_grass");
            }

            if (compound.contains("Count", 99) && compound.contains("Damage", 99)) {
                if (matches(identifier)) {
                    JsonObject variants = PLANTS.getAsJsonObject(identifier);
                    String metadata = Integer.toString(compound.getInt("Damage"));

                    if (!variants.has(metadata)) {
                        throw new IllegalStateException(
                                "No safe saved plant target for " + identifier + "/" + metadata);
                    }

                    nativeTarget = NamedBopRecovery.currentItemId(variants.get(metadata).getAsString());
                    if (nativeTarget.equals("minecraft:grass")) {
                        nativeTarget = "minecraft:short_grass";
                    }
                    compound.putString("id", nativeTarget);
                    compound.remove("Damage");

                    if (!basketContents && nativeTarget.equals("minecraft:short_grass")) {
                        compound.putString(zone.moddev.mc.skysbuildingpieces.legacy.HistoricalItems.NATIVE_ITEM_ID,
                                nativeTarget);
                    }
                }
                if (basketContents && (identifier.startsWith("minecraft:") || matches(identifier))) {
                    // Call vanilla directly. NBTUtil is already wrapped by the core's early inventory hook, so entering
                    // it here would recurse.
                    CompoundTag fixed = (CompoundTag) DataFixers.getDataFixer()
                            .update(References.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, compound.copy()),
                                    1343, SharedConstants.getCurrentVersion().getDataVersion().getVersion())
                            .getValue();

                    for (String key : new ArrayList<>(compound.getAllKeys())) {
                        compound.remove(key);
                    }
                    for (String key : fixed.getAllKeys()) {
                        compound.put(key, fixed.get(key));
                    }
                    if (nativeTarget != null) {
                        compound.putString("id", nativeTarget);
                    }
                }
            }
            for (String key : new ArrayList<>(compound.getAllKeys())) {
                rewrite(compound.get(key), basketContents || basket && key.equals("tag"), depth + 1);
            }
        } else if (value instanceof ListTag) {
            for (Tag child : (ListTag) value) {
                rewrite(child, basketContents, depth + 1);
            }
        }
    }
}
