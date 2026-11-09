package zone.moddev.mc.skysbuildingpiecesbop.probe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Set;
import java.util.zip.InflaterInputStream;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;

/** Checks the complete saved biome volume, independently of piece placement. */
public final class NativeSavedBiomeChecks {
    private NativeSavedBiomeChecks() {
    }

    private static JsonObject contract() throws Exception {
        JsonObject data = new JsonParser().parse(Files.readString(Path.of("forward-biomes.json"))).getAsJsonObject();
        JsonObject fixture = new JsonParser().parse(Files.readString(Path.of("forward-fixture.json"))).getAsJsonObject();
        String format = data.get("format").getAsString();
        require(format.equals("zlib-u8-quart-volume-v1") || format.equals("zlib-u8-quart-volume-v2"),
                "Unknown biome contract");
        if (format.equals("zlib-u8-quart-volume-v2")) {
            require(data.get("min_quart_y").getAsInt() == -16 && data.get("quart_height").getAsInt() == 96,
                    "The native 1.18 world height changed");
        }
        require(data.get("source_world_sha256").equals(fixture.get("archive_sha256")), "Wrong source biome archive");
        JsonElement sourceVersion = fixture.has("minecraft")
                ? fixture.get("minecraft") : fixture.get("source_minecraft");
        require(sourceVersion != null && data.get("source_minecraft").equals(sourceVersion),
                "Wrong source biome version");
        return data;
    }

    public static void prepare(ServerLevel world, Set<ChunkPos> tracked) throws Exception {
        if (!Files.isRegularFile(Path.of("forward-biomes.json"))) return;
        var registry = world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).asHolderIdMap();
        int maximum = -1;
        for (var biome : registry) {
            maximum = Math.max(maximum, registry.getId(biome));
        }
        int expectedBits = 32 - Integer.numberOfLeadingZeros(maximum);
        var strategy = net.minecraft.world.level.chunk.Strategy.createForBiomes(registry);
        var configurationForSize = net.minecraft.world.level.chunk.Strategy.class
                .getDeclaredMethod("getConfigurationForPaletteSize", int.class);
        configurationForSize.setAccessible(true);
        var configuration = (net.minecraft.world.level.chunk.Configuration) configurationForSize.invoke(strategy, 9);
        // A saved local palette still needs four bits. Its runtime global palette needs the registry width.
        require(configuration.bitsInStorage() == 4 && configuration.bitsInMemory() == expectedBits,
                "Biome palette widths cannot retain the saved registry: entries=" + registry.size() + " max=" + maximum
                        + " storage=" + configuration.bitsInStorage() + " memory=" + configuration.bitsInMemory());
        int actualBits = configuration.bitsInMemory();
        org.apache.logging.log4j.LogManager.getLogger().info(
                "BOP_NATIVE_BIOME_STORAGE_PASS entries={} max={} bits={}", registry.size(), maximum, actualBits);
        Set<ChunkPos> deferred = deferredChunks();
        // Loading the surrounding range would also pull in the chunks reserved for reload.
        // The first delayed pass checks their raw conversion without asking the world for them.
        if (!deferred.isEmpty()) return;
        for (JsonElement value : contract().getAsJsonArray("chunks")) {
            JsonObject row = value.getAsJsonObject();
            ChunkPos position = new ChunkPos(row.get("x").getAsInt(), row.get("z").getAsInt());
            if (!deferred.contains(position) && tracked.add(position)) {
                world.setChunkForced(position.x(), position.z(), true);
                world.getChunk(position.x(), position.z());
            }
        }
    }

    public static void verify(ServerLevel world) throws Exception {
        if (!Files.isRegularFile(Path.of("forward-biomes.json"))) return;
        JsonObject data = contract();
        JsonObject names = data.getAsJsonObject("source_names");
        boolean expandedHeight = data.get("format").getAsString().equals("zlib-u8-quart-volume-v2");
        boolean numericBlocks = data.get("source_minecraft").getAsString().equals("1.12.2");
        int minimumQuartY = expandedHeight ? data.get("min_quart_y").getAsInt() : 0;
        int expectedCells = expandedHeight ? data.get("quart_height").getAsInt() * 16 : 1024;
        var registry = world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
        Set<ChunkPos> deferred = deferredChunks();
        long cells = 0;
        long renamed = 0;
        Path temporary = deferred.isEmpty() ? null : Files.createTempDirectory(Path.of("."), "deferred-biome-fixer-");
        try (SimpleRegionStorage storage = temporary == null ? null
                : new SimpleRegionStorage(new net.minecraft.world.level.chunk.storage.RegionStorageInfo("test", net.minecraft.world.level.Level.OVERWORLD, "chunk"), temporary, world.getServer().getFixerUpper(), false, net.minecraft.util.datafix.DataFixTypes.CHUNK)) {
            for (JsonElement entry : data.getAsJsonArray("chunks")) {
                JsonObject row = entry.getAsJsonObject();
                int chunkX = row.get("x").getAsInt();
                int chunkZ = row.get("z").getAsInt();
                byte[] source;
                try (InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(
                        Base64.getDecoder().decode(row.get("cells").getAsString())))) {
                    source = input.readAllBytes();
                }
                require(source.length == expectedCells, "Incomplete saved biome height");
                ChunkPos position = new ChunkPos(chunkX, chunkZ);
                boolean unopened = !deferred.isEmpty()
                        && world.getChunkSource().getChunkNow(chunkX, chunkZ) == null;
                if (deferred.contains(position)) {
                    require(unopened, "A deferred biome chunk was loaded before its conversion check: " + position);
                }
                CompoundTag converted = unopened ? convertUnopened(world, storage, position) : null;
                var chunk = unopened ? null : world.getChunk(chunkX, chunkZ);
                for (int cell = 0; cell < source.length; cell++) {
                    String oldName = names.get(Integer.toString(Byte.toUnsignedInt(source[cell]))).getAsString();
                    String priorTarget = data.getAsJsonObject("target_names").get(oldName).getAsString();
                    String expected = oldName.startsWith("biomesoplenty:") && !numericBlocks
                            ? ForwardUpgradeChecks.currentTarget(expandedHeight ? "section_biomes" : "biomes", oldName)
                            : priorTarget;
                    if (numericBlocks || expected.equals(oldName)) {
                        expected = ForwardUpgradeChecks.currentTarget("section_biomes", priorTarget);
                    }
                    String actual = unopened ? convertedBiome(converted, cell, minimumQuartY)
                            : registry.getKey(chunk.getNoiseBiome((chunkX << 2) + (cell & 3),
                                    minimumQuartY + (cell >> 4), (chunkZ << 2) + (cell >> 2 & 3)).value()).toString();
                    require(actual.equals(expected),
                            "Native biome conversion at " + chunkX + "," + chunkZ + " cell=" + cell
                                    + " expected=" + expected + " actual=" + actual);
                    cells++;
                    if (oldName.startsWith("minecraft:") && !oldName.equals(priorTarget)) {
                        renamed++;
                    }
                }
            }
        }
        require(cells == data.get("checked_cells").getAsLong(), "Incomplete biome audit");
        require(renamed == data.get("native_renamed_cells").getAsLong(), "Native rename coverage changed");
        org.apache.logging.log4j.LogManager.getLogger().info(
                "BOP_SAVED_BIOME_VOLUME_PASS source={} cells={} renamed={}",
                data.get("source_minecraft").getAsString(), cells, renamed);
        if (!deferred.isEmpty()) {
            org.apache.logging.log4j.LogManager.getLogger().info(
                    "BOP_DEFERRED_BIOME_DATAFIX_PASS chunks={} still_unloaded=true", deferred.size());
        }
    }

    static Set<ChunkPos> deferredChunks() throws Exception {
        Set<ChunkPos> deferred = new java.util.LinkedHashSet<>();
        if (!Boolean.getBoolean("skysbuildingpieces.bopDelayedBiomes")
                || Files.isRegularFile(Path.of("delayed-biomes-first-pass.txt"))) return deferred;
        JsonObject fixture = new JsonParser().parse(Files.readString(Path.of("forward-fixture.json"))).getAsJsonObject();
        if (fixture.has("kind") && fixture.get("kind").getAsString().equals("named")) {
            if (fixture.has("source_biome_chunk_x") && fixture.has("source_biome_chunk_z")) {
                deferred.add(new ChunkPos(fixture.get("source_biome_chunk_x").getAsInt(),
                        fixture.get("source_biome_chunk_z").getAsInt()));
            } else {
                for (JsonElement value : fixture.getAsJsonArray("states")) {
                    JsonObject row = value.getAsJsonObject();
                    if (row.has("role") && row.get("role").getAsString().equals("native source biome chunk")) {
                        deferred.add(new ChunkPos(row.get("x").getAsInt() >> 4, row.get("z").getAsInt() >> 4));
                    }
                }
            }
            require(deferred.size() == 1, "The named fixture needs its isolated source-biome chunk");
            return deferred;
        }
        JsonObject aliases;
        try (var input = zone.moddev.mc.skysbuildingpiecesbop.RetiredRocks.class.getResourceAsStream(
                "/assets/skysbuildingpiecesbop/catalogue/named_legacy.json")) {
            require(input != null, "The add-on's biome mapping resource is missing");
            aliases = new JsonParser().parse(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("biomes");
        }
        int number = 0;
        for (JsonElement entry : fixture.getAsJsonObject("manifest").getAsJsonArray("biomes")) {
            JsonObject row = entry.getAsJsonObject();
            if ((number++ & 1) != 0 || aliases.has(row.get("id").getAsString())) {
                deferred.add(new ChunkPos(row.get("x").getAsInt() >> 4, row.get("z").getAsInt() >> 4));
            }
        }
        return deferred;
    }

    private static CompoundTag convertUnopened(ServerLevel world, SimpleRegionStorage storage, ChunkPos position)
            throws Exception {
        require(world.getChunkSource().getChunkNow(position.x(), position.z()) == null,
                "An unopened biome chunk was loaded before its conversion check: " + position);
        Path root = world.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        String relative = "region/r." + (position.x() >> 5) + "." + (position.z() >> 5) + ".mca";
        Path region = root.resolve(relative);
        if (!Files.isRegularFile(region)) {
            region = root.resolve("dimensions/minecraft/overworld").resolve(relative);
        }
        byte[] bytes = Files.readAllBytes(region);
        var header = java.nio.ByteBuffer.wrap(bytes);
        int location = header.getInt(((position.x() & 31) + (position.z() & 31) * 32) * 4);
        require(location != 0, "A deferred source chunk is missing");
        int offset = (location >>> 8) * 4096;
        int length = header.getInt(offset);
        java.io.InputStream payload = new ByteArrayInputStream(bytes, offset + 5, length - 1);
        if (bytes[offset + 4] == 2) payload = new InflaterInputStream(payload);
        else if (bytes[offset + 4] == 1) payload = new java.util.zip.GZIPInputStream(payload);
        else require(bytes[offset + 4] == 3, "Unknown deferred chunk compression");
        CompoundTag source;
        try (var input = new java.io.DataInputStream(payload)) {
            source = net.minecraft.nbt.NbtIo.read(input);
        }
        CompoundTag chunk = (source.get("Level") instanceof net.minecraft.nbt.CompoundTag) ? source.getCompoundOrEmpty("Level") : source;
        require(chunk.getIntOr("xPos", 0) == position.x() && chunk.getIntOr("zPos", 0) == position.z(),
                "Wrong deferred source coordinates");
        CompoundTag fixed = BopProbeItems.upgradeChunk(storage, source);
        require(world.getChunkSource().getChunkNow(position.x(), position.z()) == null,
                "The datafix check loaded a deferred biome chunk");
        return fixed;
    }

    private static String convertedBiome(CompoundTag chunk, int cell, int minimumQuartY) {
        int sectionY = (minimumQuartY >> 2) + (cell >> 6);
        ListTag sections = chunk.getListOrEmpty("sections");
        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            CompoundTag section = sections.getCompoundOrEmpty(sectionIndex);
            if (section.getByteOr("Y", (byte) 0) != sectionY) continue;
            CompoundTag biomes = section.getCompoundOrEmpty("biomes");
            ListTag palette = biomes.getListOrEmpty("palette");
            require(!palette.isEmpty(), "Missing deferred biome palette");
            if (palette.size() == 1) return palette.getStringOr(0, "");
            int bits = 32 - Integer.numberOfLeadingZeros(palette.size() - 1);
            int perLong = 64 / bits;
            long number = biomes.getLongArray("data").orElseThrow()[(cell & 63) / perLong]
                    >>> ((cell & 63) % perLong * bits);
            return palette.getString((int) (number & ((1L << bits) - 1))).orElseThrow();
        }
        throw new AssertionError("Missing deferred biome section " + sectionY);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
