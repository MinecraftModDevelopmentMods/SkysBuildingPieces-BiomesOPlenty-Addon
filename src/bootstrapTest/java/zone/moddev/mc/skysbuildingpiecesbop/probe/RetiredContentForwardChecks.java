package zone.moddev.mc.skysbuildingpiecesbop.probe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/** Checks the real 1.20.1 save, including a chunk deliberately left unopened on the first pass. */
final class RetiredContentForwardChecks {
    private static final Set<ChunkPos> CHUNKS = new LinkedHashSet<>();
    private static JsonObject fixture;

    private RetiredContentForwardChecks() {
    }

    static boolean applies(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        JsonObject manifest = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        if (!manifest.has("kind") || !manifest.get("kind").getAsString().equals("retired_bop")) {
            return false;
        }
        fixture = manifest;
        return true;
    }

    static boolean active() {
        return fixture != null;
    }

    static void prepare(ServerLevel world) {
        for (String group : new String[] { "blocks", "biomes" }) {
            for (JsonElement item : fixture.getAsJsonArray(group)) {
                JsonObject row = item.getAsJsonObject();
                if (shouldCheck(row)) {
                    CHUNKS.add(ChunkPos.containing(position(row)));
                }
            }
        }
        CHUNKS.add(ChunkPos.containing(position(fixture.getAsJsonObject("chest"))));
        for (ChunkPos chunk : CHUNKS) {
            world.setChunkForced(chunk.x(), chunk.z(), true);
            world.getChunk(chunk.x(), chunk.z());
        }
    }

    static boolean ready(ServerLevel world) {
        return CHUNKS.stream().allMatch(chunk -> world.getChunkSource().isPositionTicking(chunk.pack()));
    }

    static void verify(ServerLevel world) {
        int blocks = 0;
        for (JsonElement item : fixture.getAsJsonArray("blocks")) {
            JsonObject row = item.getAsJsonObject();
            if (!shouldCheck(row)) {
                continue;
            }
            BlockPos position = position(row);
            var state = world.getBlockState(position);
            String actual = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            String expected = ForwardUpgradeChecks.currentTarget("blocks", row.get("target").getAsString());
            check(actual.equals(expected), "Incorrect recovered block at " + position + ": " + state
                    + ", expected " + expected);
            JsonObject properties = row.getAsJsonObject("properties");
            Set<String> presentProperties = new LinkedHashSet<>();
            state.getValues().forEach(property -> {
                String name = property.property().getName();
                presentProperties.add(name);
                if (properties.has(name)) {
                    check(properties.get(name).getAsString().equals(property.value().toString()),
                            "A recovered block lost " + name + " at " + position);
                }
            });
            check(presentProperties.containsAll(properties.keySet()),
                    "A recovered block lost properties at " + position);
            blocks++;
        }

        var blockEntity = world.getBlockEntity(position(fixture.getAsJsonObject("chest")));
        check(blockEntity instanceof ChestBlockEntity, "The source chest was lost");
        ChestBlockEntity chest = (ChestBlockEntity) blockEntity;
        int items = 0;
        for (JsonElement entry : fixture.getAsJsonArray("items")) {
            JsonObject row = entry.getAsJsonObject();
            var stack = chest.getItem(row.get("slot").getAsInt());
            String expected = ForwardUpgradeChecks.currentItemTarget(row.get("target").getAsString());
            check(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(expected)
                            && stack.getCount() == row.get("count").getAsInt()
                            && BopProbeItems.custom(stack).getStringOr("PortSample", "").equals(row.get("custom").getAsString()),
                    "A retired BOP item lost its name, count or custom data: " + row + " -> " + stack);
            items += stack.getCount();
        }

        int biomes = 0;
        for (JsonElement entry : fixture.getAsJsonArray("biomes")) {
            JsonObject row = entry.getAsJsonObject();
            if (!shouldCheck(row)) {
                continue;
            }
            BlockPos position = position(row);
            String actual = world.getBiome(position).unwrapKey().orElseThrow().identifier().toString();
            // The fixture records the 1.21.1 target. Some of those biome names were retired again.
            String expected = ForwardUpgradeChecks.currentTarget("section_biomes", row.get("target").getAsString());
            check(actual.equals(expected), "Incorrect recovered biome at " + position + ": " + actual
                    + ", expected " + expected);
            biomes++;
        }
        boolean delayed = !phase().equals("forward");
        check(blocks == (delayed ? 11 : 10) && items == 65 && biomes == (delayed ? 4 : 3),
                "Incomplete retired-content coverage");
        for (ChunkPos chunk : CHUNKS) {
            world.setChunkForced(chunk.x(), chunk.z(), false);
        }
        world.getServer().saveAllChunks(true, true, true);
        org.apache.logging.log4j.LogManager.getLogger().info(
                "BOP_RETIREMENT_FORWARD_PASS phase={} blocks={} item_units={} biomes={}", phase(), blocks, items, biomes);
    }

    private static boolean shouldCheck(JsonObject row) {
        return !phase().equals("forward") || !row.has("deferred") || !row.get("deferred").getAsBoolean();
    }

    private static String phase() {
        return System.getProperty("skysbuildingpieces.integrationPhase", "");
    }

    private static BlockPos position(JsonObject row) {
        return new BlockPos(row.get("x").getAsInt(), row.get("y").getAsInt(), row.get("z").getAsInt());
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
