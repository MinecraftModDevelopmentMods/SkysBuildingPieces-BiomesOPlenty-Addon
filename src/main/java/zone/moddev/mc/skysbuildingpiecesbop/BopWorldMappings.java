package zone.moddev.mc.skysbuildingpiecesbop;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

/** Named equivalents for native BOP blocks saved before flattening. */
public final class BopWorldMappings {
    private BopWorldMappings() {
    }

    public static void registerBiomes() {
        Map<String, String> aliases = new LinkedHashMap<>();

        try (InputStreamReader reader = new InputStreamReader(BopWorldMappings.class.getResourceAsStream(
                "/assets/skysbuildingpiecesbop/catalogue/legacy_biomes.json"), StandardCharsets.UTF_8)) {
            JsonObject values = new Gson().fromJson(reader, JsonObject.class);

            values.entrySet().forEach(entry -> aliases.put(entry.getKey(), entry.getValue().getAsString()));
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot read retired BOP biome mappings", failure);
        }

        zone.moddev.mc.skysbuildingpieces.legacy.HistoricalBiomes.register("biomesoplenty", aliases);
    }

    public static JsonArray rows() {
        try (InputStreamReader reader = new InputStreamReader(BopWorldMappings.class.getResourceAsStream(
                "/assets/skysbuildingpiecesbop/catalogue/legacy_world_states.json"), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, JsonArray.class);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot read native BOP world mappings", failure);
        }
    }

    public static BlockState state(JsonObject target) {
        if (target.has("unsupported")) {
            throw new IllegalStateException(target.get("unsupported").getAsString());
        }

        String savedTarget = NamedBopRecovery.currentBlockId(target.get("id").getAsString());
        ResourceLocation identity = new ResourceLocation(savedTarget.equals("minecraft:grass")
                ? "minecraft:short_grass" : savedTarget);
        Block block = BuiltInRegistries.BLOCK.getOptional(identity).orElse(null);

        if (block == null || block == Blocks.AIR) {
            throw new IllegalStateException("Missing BOP world replacement: " + identity);
        }

        BlockState state = block.defaultBlockState();

        for (Map.Entry<String, JsonElement> entry : target.getAsJsonObject("properties").entrySet()) {
            Property<?> property = block.getStateDefinition().getProperty(entry.getKey());

            if (property == null) {
                throw new IllegalStateException("Missing recovered property " + entry.getKey() + " on " + identity);
            }

            state = withProperty(state, property, entry.getValue().getAsString());
        }
        if (state.hasBlockEntity()) {
            throw new IllegalStateException("BOP replacements must not introduce a block entity: " + identity);
        }

        return state;
    }

    private static <T extends Comparable<T>> BlockState withProperty(BlockState state, Property<T> property,
            String value) {
        return state.setValue(property, property.getValue(value).orElseThrow(
                () -> new IllegalStateException("Invalid recovered property " + property.getName() + "=" + value)));
    }
}
