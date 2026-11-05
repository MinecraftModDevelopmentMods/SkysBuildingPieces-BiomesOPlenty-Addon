package zone.moddev.mc.skysbuildingpiecesbop.probe;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/** Keep the target's actual registry states beside the disposable upgrade receipts. */
final class RegistrySnapshot {
    private RegistrySnapshot() {
    }

    static void write() throws java.io.IOException {
        JsonArray states = new JsonArray();

        for (Block block : ForgeRegistries.BLOCKS.getValues()) {
            if (!net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block).getNamespace().equals("biomesoplenty")) {
                continue;
            }
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                JsonObject row = new JsonObject();

                row.addProperty("id", net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block).toString());

                JsonObject properties = new JsonObject();

                state.getValues()
                        .forEach((property, value) -> properties.addProperty(property.getName(), value.toString()));
                row.add("properties", properties);
                states.add(row);
            }
        }
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream("target-bop-states.json"),
                StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(states, writer);
        }

        JsonArray items = new JsonArray();

        ForgeRegistries.ITEMS.getKeys().stream().filter(key -> key.getNamespace().equals("biomesoplenty"))
                .sorted(java.util.Comparator.comparing(Object::toString)).forEach(key -> items.add(key.toString()));

        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream("target-bop-items.json"),
                StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(items, writer);
        }

        JsonArray biomes = new JsonArray();

        var biomeRegistry = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
        biomeRegistry.forEach(biome -> {
            JsonObject row = new JsonObject();

            row.addProperty("id", biomeRegistry.getKey(biome).toString());
            row.addProperty("temperature", biome.getBaseTemperature());
            row.addProperty("rainfall", biome.getModifiedClimateSettings().downfall());
            biomes.add(row);
        });

        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream("target-biomes.json"),
                StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(biomes, writer);
        }
    }
}
