package zone.moddev.mc.skysbuildingpiecesbop.basket;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BasketPreflightTest {
    private static final String PLAYER = "playerdata/903b39a1-c052-4ac8-8264-177b9abf8e6a.dat";

    @TempDir
    Path world;

    @Test
    void emptyNativeRegionIsValidButNonemptyTruncatedHeadersStopRecovery() throws Exception {
        assertFalse(BasketPreflight.hasRegionHeader(0, "r.0.0.mca"));
        for (long size : new long[] {-1, 1, 4096, 8191}) {
            IOException failure = assertThrows(IOException.class,
                    () -> BasketPreflight.hasRegionHeader(size, "r.0.0.mca"));
            assertTrue(failure.getMessage().contains("r.0.0.mca"));
        }
        assertTrue(BasketPreflight.hasRegionHeader(8192, "r.0.0.mca"));
        assertTrue(BasketPreflight.hasRegionHeader(12288, "r.0.0.mca"));
    }

    @Test
    void unrelatedDatFilesAreNotAssumedToBeNbt() throws Exception {
        writeNbt(world.resolve("level.dat"), "level", true);
        writeNbt(world.resolve(PLAYER), "player", false);
        List<Path> unrelated = new ArrayList<>();
        for (String name : List.of("mod-storage/index.dat", "data/custom/cache.dat", "session.dat",
                "playerdata/not-a-player.dat", "backup/level.dat")) {
            Path file = world.resolve(name);
            Files.createDirectories(file.getParent());
            // This reproduces the report's EOF while reading a root tag's UTF name.
            Files.write(file, new byte[] {10, 0});
            unrelated.add(file);
        }
        Set<String> checked = new HashSet<>();
        assertEquals(0, BasketPreflight.scan(world, tag -> checked.add(tag.getString("marker"))));
        assertEquals(Set.of("level", "player"), checked);
        for (Path file : unrelated) {
            assertArrayEquals(new byte[] {10, 0}, Files.readAllBytes(file));
        }
    }

    @Test
    void scansInventoriesInChunksAndSeparateEntityRegionsAcrossDimensions() throws Exception {
        List<String> directories = List.of("region", "entities", "DIM-1/region", "DIM1/entities",
                "DIM42/region", "dimensions/example/deep/caverns/region",
                "dimensions/example/deep/caverns/entities");
        for (String directory : directories) {
            writeRegion(world.resolve(directory + "/r.-1.2.mca"), directory);
        }
        Set<String> checked = new HashSet<>();
        assertEquals(directories.size(), BasketPreflight.scan(world, tag -> checked.add(tag.getString("marker"))));
        assertEquals(new HashSet<>(directories), checked);
    }

    @Test
    void ignoresArchivedAndModOwnedRegionDirectories() throws Exception {
        for (String directory : List.of("backups/region", "backups/DIM-1/region", "mods/example/region",
                "dimensions/example/region", "dimensions/BadNamespace/test/region")) {
            Path file = world.resolve(directory + "/r.0.0.mca");
            Files.createDirectories(file.getParent());
            Files.write(file, new byte[] {1, 2});
        }
        assertEquals(0, BasketPreflight.scan(world, tag -> fail("An unrelated file was inspected")));
    }

    @Test
    void corruptNativePlayerDataStillStopsAndNamesTheFile() throws Exception {
        Path player = world.resolve(PLAYER);
        Files.createDirectories(player.getParent());
        Files.write(player, new byte[] {10, 0});
        IOException failure = assertThrows(IOException.class, () -> BasketPreflight.scan(world, tag -> {}));
        assertTrue(failure.getMessage().contains(player.toString()));
        assertInstanceOf(java.io.EOFException.class, failure.getCause());
    }

    @Test
    void corruptCompressedLevelDataStillStopsAndNamesTheFile() throws Exception {
        Path level = world.resolve("level.dat");
        Files.write(level, new byte[] {31, (byte) 139});
        IOException failure = assertThrows(IOException.class, () -> BasketPreflight.scan(world, tag -> {}));
        assertTrue(failure.getMessage().contains(level.toString()));
    }

    @Test
    void invalidRegionDataIncludesFileAndChunkSlot() throws Exception {
        Path region = world.resolve("entities/r.0.0.mca");
        writeRegion(region, "entity");
        byte[] bytes = Files.readAllBytes(region);
        ByteBuffer.wrap(bytes).putInt(8192, Integer.MAX_VALUE);
        Files.write(region, bytes);
        IOException failure = assertThrows(IOException.class, () -> BasketPreflight.scan(world, tag -> {}));
        assertTrue(failure.getMessage().contains(region.toString()));
        assertTrue(failure.getMessage().contains("chunk slot 0"));
    }

    @Test
    void acceptsAllNativeCompressionFormatsIncludingLz4() throws Exception {
        for (int compression = 1; compression <= 4; compression++) {
            writeRegion(world.resolve("region/r." + compression + ".0.mca"),
                    "format-" + compression, compression, false, 0);
        }
        Set<String> checked = new HashSet<>();
        assertEquals(4, BasketPreflight.scan(world, tag -> checked.add(tag.getString("marker"))));
        assertEquals(Set.of("format-1", "format-2", "format-3", "format-4"), checked);
    }

    @Test
    void readsOversizedExternalChunksWithoutChangingThem() throws Exception {
        writeRegion(world.resolve("entities/r.-1.2.mca"), "external", 4, true, 33);
        Path payload = world.resolve("entities/c.-31.65.mcc");
        byte[] before = Files.readAllBytes(payload);
        List<String> checked = new ArrayList<>();
        assertEquals(1, BasketPreflight.scan(world, tag -> checked.add(tag.getString("marker"))));
        assertEquals(List.of("external"), checked);
        assertArrayEquals(before, Files.readAllBytes(payload));
    }

    @Test
    void missingExternalChunkStillStopsAndNamesTheRegionAndPayload() throws Exception {
        Path region = world.resolve("entities/r.-1.2.mca");
        writeRegion(region, "external", 2, true, 33);
        Path payload = world.resolve("entities/c.-31.65.mcc");
        Files.delete(payload);
        IOException failure = assertThrows(IOException.class, () -> BasketPreflight.scan(world, tag -> {}));
        assertTrue(failure.getMessage().contains(region.toString()));
        assertTrue(failure.getMessage().contains("chunk slot 33"));
        assertTrue(failure.getMessage().contains(payload.toString()));
    }

    @Test
    void invalidBasketContentsAreNotSilentlySkipped() throws Exception {
        Path player = world.resolve(PLAYER);
        writeNbt(player, "player", true);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> BasketPreflight.scan(world, tag -> {
                    throw new IllegalStateException("duplicate basket slot 3");
                }));
        assertTrue(failure.getMessage().contains(player.toString()));
        assertTrue(failure.getMessage().contains("duplicate basket slot 3"));
    }

    @Test
    void savedDataMustHaveACompoundRoot() throws Exception {
        Path level = world.resolve("level.dat");
        Files.write(level, new byte[] {0});
        IOException failure = assertThrows(IOException.class, () -> BasketPreflight.scan(world, tag -> {}));
        assertTrue(failure.getMessage().contains(level.toString()));
    }

    @Test
    void validFilesAreOnlyReadAndNeverRewritten() throws Exception {
        Path player = world.resolve(PLAYER);
        writeNbt(player, "original", true);
        byte[] before = Files.readAllBytes(player);
        BasketPreflight.scan(world, tag -> tag.putString("marker", "validation copy"));
        assertArrayEquals(before, Files.readAllBytes(player));
    }

    private static byte[] nbt(String marker) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompoundTag root = new CompoundTag();
        root.putString("marker", marker);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            NbtIo.write(root, output);
        }
        return bytes.toByteArray();
    }

    private static void writeNbt(Path file, String marker, boolean compressed) throws IOException {
        Files.createDirectories(file.getParent());
        if (compressed) {
            try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(file))) {
                output.write(nbt(marker));
            }
        } else {
            Files.write(file, nbt(marker));
        }
    }

    private static void writeRegion(Path file, String marker) throws IOException {
        writeRegion(file, marker, 2, false, 0);
    }

    private static void writeRegion(Path file, String marker, int compression, boolean external, int slot)
            throws IOException {
        Files.createDirectories(file.getParent());
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        try (java.io.OutputStream compressed = RegionFileVersion.fromId(compression).wrap(payload)) {
            compressed.write(nbt(marker));
        }
        ByteBuffer region = ByteBuffer.allocate(12288);
        region.putInt(slot * 4, 2 << 8 | 1);
        region.position(8192);
        region.putInt(external ? 1 : payload.size() + 1);
        region.put((byte) (compression | (external ? 128 : 0)));
        if (external) {
            String[] coordinates = file.getFileName().toString().split("\\.");
            int chunkX = Integer.parseInt(coordinates[1]) * 32 + (slot & 31);
            int chunkZ = Integer.parseInt(coordinates[2]) * 32 + (slot >> 5);
            Files.write(file.resolveSibling("c." + chunkX + "." + chunkZ + ".mcc"), payload.toByteArray());
        } else {
            region.put(payload.toByteArray());
        }
        Files.write(file, region.array());
    }
}
