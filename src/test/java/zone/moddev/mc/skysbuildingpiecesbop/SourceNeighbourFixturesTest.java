package zone.moddev.mc.skysbuildingpiecesbop;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipFile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;

/** The controls describe the saved doors, including their originally inconsistent halves. */
class SourceNeighbourFixturesTest {
    @Test
    void waterDatafixControlsAreExactCopiesOfTheOriginalSavedChunks() throws Exception {
        Path directory = Path.of("src/testFixtures/biomes/fluids");
        var fixtures = new JsonParser().parse(Files.readString(directory.resolve("index.json"))).getAsJsonArray();
        assertEquals(4, fixtures.size());
        for (JsonElement fixtureEntry : fixtures) {
            JsonObject fixture = fixtureEntry.getAsJsonObject();
            Path archivePath = Path.of("src/testFixtures/worlds").resolve(fixture.get("source_archive").getAsString());
            assertEquals(fixture.get("source_world_sha256").getAsString(), HexFormat.of().withUpperCase().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archivePath))));
            int cells = 0;
            Map<String, CompoundTag> chunks = new HashMap<>();
            try (ZipFile archive = new ZipFile(archivePath.toFile())) {
                for (JsonElement chunkEntry : fixture.getAsJsonArray("chunks")) {
                    JsonObject chunk = chunkEntry.getAsJsonObject();
                    byte[] bytes = Files.readAllBytes(directory.resolve(chunk.get("file").getAsString()));
                    assertEquals(chunk.get("sha256").getAsString(), HexFormat.of().withUpperCase().formatHex(
                            MessageDigest.getInstance("SHA-256").digest(bytes)));
                    assertEquals(rawChunk(archive, chunk.get("x").getAsInt(), chunk.get("z").getAsInt()),
                            NbtIo.readCompressed(new ByteArrayInputStream(bytes)));
                    for (JsonElement positionEntry : chunk.getAsJsonArray("positions")) {
                        JsonObject row = positionEntry.getAsJsonObject();
                        CompoundTag expected = TagParser.parseTag(row.get("state").getAsString());
                        assertEquals("minecraft:water", expected.getString("Name"));
                        assertEquals(expected, state(archive, chunks, row.get("x").getAsInt(),
                                row.get("y").getAsInt(), row.get("z").getAsInt()));
                        cells++;
                    }
                }
            }
            assertEquals(64, cells);
        }
    }

    @Test
    void savedStatesAndNeighbourControlsMatchTheUnmodifiedSourceArchives() throws Exception {
        for (String fixture : new String[] {"building-pieces-bop-1.14.4", "building-pieces-bop-1.15.2",
                "building-pieces-bop-biomes-1.14.4", "building-pieces-bop-biomes-1.15.2"}) {
            String version = fixture.substring(fixture.lastIndexOf('-') + 1);
            Path archivePath = Path.of("src/testFixtures/worlds/" + fixture + ".zip");
            JsonObject controls = new JsonParser().parse(Files.readString(Path.of(
                    "src/testFixtures/worlds/" + fixture + ".neighbours.json"))).getAsJsonObject();
            String checksum = HexFormat.of().withUpperCase().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archivePath)));
            assertEquals(checksum, controls.get("source_world_sha256").getAsString());
            assertEquals(version, controls.get("source_minecraft").getAsString());
            Set<String> positions = new HashSet<>();
            Map<String, CompoundTag> chunks = new HashMap<>();
            try (ZipFile archive = new ZipFile(archivePath.toFile())) {
                JsonObject manifest = new JsonParser().parse(Files.readString(
                        Path.of("src/testFixtures/worlds/" + fixture + ".json"))).getAsJsonObject();
                assertEquals(1741, manifest.getAsJsonArray("source_saved_states").size());
                for (JsonElement entry : manifest.getAsJsonArray("source_saved_states")) {
                    JsonObject row = entry.getAsJsonObject();
                    assertEquals(TagParser.parseTag(row.get("state").getAsString()),
                            state(archive, chunks, row.get("x").getAsInt(), row.get("y").getAsInt(), row.get("z").getAsInt()),
                            "The saved-state snapshot differs from its source archive");
                }
                assertEquals(fixture.contains("-biomes-") ? 50 : 45, controls.getAsJsonArray("containers").size());
                for (JsonElement entry : controls.getAsJsonArray("containers")) {
                    JsonObject row = entry.getAsJsonObject();
                    assertEquals(TagParser.parseTag(row.get("state").getAsString()),
                            state(archive, chunks, row.get("x").getAsInt(), row.get("y").getAsInt(), row.get("z").getAsInt()));
                }
                for (JsonElement entry : controls.getAsJsonArray("doors")) {
                    JsonObject row = entry.getAsJsonObject();
                    int x = row.get("x").getAsInt(), y = row.get("y").getAsInt(), z = row.get("z").getAsInt();
                    assertTrue(positions.add(x + "," + y + "," + z));
                    String side = row.get("side").getAsString();
                    assertTrue(side.equals("up") || side.equals("down"));
                    CompoundTag source = state(archive, chunks, x, y, z);
                    CompoundTag neighbour = state(archive, chunks, x, y + (side.equals("up") ? 1 : -1), z);
                    assertEquals(source, TagParser.parseTag(row.get("source").getAsString()));
                    assertEquals(neighbour, TagParser.parseTag(row.get("neighbour").getAsString()));
                    assertEquals(source.getString("Name"), neighbour.getString("Name"));
                    assertTrue(source.getString("Name").startsWith("minecraft:")
                            || source.getString("Name").startsWith("biomesoplenty:"));
                    assertTrue(source.getString("Name").endsWith("_door"));
                    assertNotEquals(source.getCompound("Properties").getString("half"),
                            neighbour.getCompound("Properties").getString("half"));
                    boolean inconsistent = false;
                    for (String property : new String[] {"facing", "hinge", "open", "powered"}) {
                        inconsistent |= !source.getCompound("Properties").getString(property)
                                .equals(neighbour.getCompound("Properties").getString(property));
                    }
                    assertTrue(inconsistent, "A consistent door needs no special expectation");
                }
            }
            assertEquals(160, positions.size());
        }
    }

    private static CompoundTag state(ZipFile archive, Map<String, CompoundTag> chunks, int x, int y, int z)
            throws Exception {
        int chunkX = x >> 4, chunkZ = z >> 4;
        String key = chunkX + "," + chunkZ;
        CompoundTag level = chunks.get(key);
        if (level == null) {
            level = rawChunk(archive, chunkX, chunkZ).getCompound("Level");
            chunks.put(key, level);
        }
        ListTag sections = level.getList("Sections", 10);
        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            CompoundTag section = sections.getCompound(sectionIndex);
            if (section.getByte("Y") != y >> 4) continue;
            ListTag palette = section.getList("Palette", 10);
            assertFalse(palette.isEmpty());
            long[] data = section.getLongArray("BlockStates");
            if (data.length == 0) return palette.getCompound(0);
            int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
            int bitOffset = ((y & 15) * 256 + (z & 15) * 16 + (x & 15)) * bits;
            long number = data[bitOffset / 64] >>> (bitOffset % 64);
            if (bitOffset % 64 + bits > 64) number |= data[bitOffset / 64 + 1] << (64 - bitOffset % 64);
            return palette.getCompound((int) (number & ((1L << bits) - 1)));
        }
        fail("Missing source section at " + x + "," + y + "," + z);
        return null;
    }

    private static CompoundTag rawChunk(ZipFile archive, int chunkX, int chunkZ) throws Exception {
        String file = "region/r." + (chunkX >> 5) + "." + (chunkZ >> 5) + ".mca";
        byte[] bytes = archive.getInputStream(archive.getEntry(file)).readAllBytes();
        ByteBuffer header = ByteBuffer.wrap(bytes);
        int location = header.getInt(((chunkX & 31) + (chunkZ & 31) * 32) * 4);
        assertNotEquals(0, location);
        int offset = (location >>> 8) * 4096;
        int length = header.getInt(offset);
        InputStream payload = new ByteArrayInputStream(bytes, offset + 5, length - 1);
        if (bytes[offset + 4] == 2) payload = new InflaterInputStream(payload);
        else if (bytes[offset + 4] == 1) payload = new GZIPInputStream(payload);
        else assertEquals(3, bytes[offset + 4]);
        try (DataInputStream input = new DataInputStream(payload)) {
            return NbtIo.read(input);
        }
    }
}
