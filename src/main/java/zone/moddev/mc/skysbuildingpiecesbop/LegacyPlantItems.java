package zone.moddev.mc.skysbuildingpiecesbop;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.datafixers.Dynamic;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTDynamicOps;
import net.minecraft.util.SharedConstants;
import net.minecraft.util.datafix.DataFixesManager;
import net.minecraft.util.datafix.TypeReferences;
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

    static void rewrite(INBT value, boolean basketContents, int depth) {
        if (depth > 64) {
            throw new IllegalStateException("Saved plant inventory nesting exceeds 64 levels");
        }
        if (value instanceof CompoundNBT) {
            CompoundNBT compound = (CompoundNBT) value;
            String identifier = compound.getString("id");
            boolean basket = BasketData.isBasket(identifier);
            String nativeTarget = null;

            if (compound.contains("Count", 99) && compound.contains("Damage", 99)) {
                if (matches(identifier)) {
                    JsonObject variants = PLANTS.getAsJsonObject(identifier);
                    String metadata = Integer.toString(compound.getInt("Damage"));

                    if (!variants.has(metadata)) {
                        throw new IllegalStateException(
                                "No safe saved plant target for " + identifier + "/" + metadata);
                    }

                    nativeTarget = variants.get(metadata).getAsString();
                    compound.putString("id", nativeTarget);
                    compound.remove("Damage");

                    if (!basketContents && nativeTarget.equals("minecraft:grass")) {
                        compound.putString(zone.moddev.mc.skysbuildingpieces.legacy.HistoricalItems.NATIVE_ITEM_ID,
                                nativeTarget);
                    }
                }
                if (basketContents && (identifier.startsWith("minecraft:") || matches(identifier))) {
                    // Call vanilla directly. NBTUtil is already wrapped by the core's early inventory hook, so entering
                    // it here would recurse.
                    CompoundNBT fixed = (CompoundNBT) DataFixesManager.getDataFixer()
                            .update(TypeReferences.ITEM_STACK, new Dynamic<>(NBTDynamicOps.INSTANCE, compound.copy()),
                                    1343, SharedConstants.getVersion().getWorldVersion())
                            .getValue();

                    for (String key : new ArrayList<>(compound.keySet())) {
                        compound.remove(key);
                    }
                    for (String key : fixed.keySet()) {
                        compound.put(key, fixed.get(key));
                    }
                    if (nativeTarget != null) {
                        compound.putString("id", nativeTarget);
                    }
                }
            }
            for (String key : new ArrayList<>(compound.keySet())) {
                rewrite(compound.get(key), basketContents || basket && key.equals("tag"), depth + 1);
            }
        } else if (value instanceof ListNBT) {
            for (INBT child : (ListNBT) value) {
                rewrite(child, basketContents, depth + 1);
            }
        }
    }
}
