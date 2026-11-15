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
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.CompressedStreamTools;
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
        for (String name : java.util.Arrays.asList("mod-storage/index.dat", "data/custom/cache.dat", "session.dat",
                "playerdata/not-a-player.dat", "backup/level.dat")) {
            Path file = world.resolve(name);
            Files.createDirectories(file.getParent());
            // This reproduces the report's EOF while reading a root tag's UTF name.
            Files.write(file, new byte[] {10, 0});
            unrelated.add(file);
        }
        Set<String> checked = new HashSet<>();
        assertEquals(0, BasketPreflight.scan(world, tag -> checked.add(tag.getString("marker"))));
        assertEquals(set("level", "player"), checked);
        for (Path file : unrelated) {
            assertArrayEquals(new byte[] {10, 0}, Files.readAllBytes(file));
        }
    }

    @Test
    void scansChunkInventoriesAcrossLegacyDimensions() throws Exception {
        List<String> directories = java.util.Arrays.asList("region", "DIM-1/region", "DIM1/region", "DIM42/region");
        for (String directory : directories) {
            writeRegion(world.resolve(directory + "/r.-1.2.mca"), directory);
        }
        Set<String> checked = new HashSet<>();
        assertEquals(directories.size(), BasketPreflight.scan(world, tag -> checked.add(tag.getString("marker"))));
        assertEquals(new HashSet<>(directories), checked);
    }

    @Test
    void ignoresArchivedAndModOwnedRegionDirectories() throws Exception {
        for (String directory : java.util.Arrays.asList("backups/region", "backups/DIM-1/region", "mods/example/region",
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
        Path region = world.resolve("region/r.0.0.mca");
        writeRegion(region, "entity");
        byte[] bytes = Files.readAllBytes(region);
        ByteBuffer.wrap(bytes).putInt(8192, Integer.MAX_VALUE);
        Files.write(region, bytes);
        IOException failure = assertThrows(IOException.class, () -> BasketPreflight.scan(world, tag -> {}));
        assertTrue(failure.getMessage().contains(region.toString()));
        assertTrue(failure.getMessage().contains("chunk slot 0"));
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
        BasketPreflight.scan(world, tag -> tag.setString("marker", "validation copy"));
        assertArrayEquals(before, Files.readAllBytes(player));
    }

    private static Set<String> set(String... entries) {
        return new HashSet<>(java.util.Arrays.asList(entries));
    }

    private static byte[] nbt(String marker) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NBTTagCompound root = new NBTTagCompound();
        root.setString("marker", marker);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            CompressedStreamTools.write(root, output);
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
        Files.createDirectories(file.getParent());
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        try (java.util.zip.DeflaterOutputStream compressed = new java.util.zip.DeflaterOutputStream(payload)) {
            compressed.write(nbt(marker));
        }
        ByteBuffer region = ByteBuffer.allocate(12288);
        region.putInt(0, 2 << 8 | 1);
        region.position(8192);
        region.putInt(payload.size() + 1);
        region.put((byte) 2);
        region.put(payload.toByteArray());
        Files.write(file, region.array());
    }
}
