package zone.moddev.mc.skysbuildingpiecesbop;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.zip.ZipFile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Native117FixtureTest {
    @Test
    void acceptedNativeSourceCoversSavedStatesCrystalsAndInventories() throws Exception {
        Path archive = Paths.get("src/testFixtures/worlds/building-pieces-bop-1.17.1.zip");
        Path manifest = Paths.get("src/testFixtures/worlds/building-pieces-bop-1.17.1.json");
        JsonObject source = new JsonParser().parse(Files.readString(manifest, StandardCharsets.UTF_8)).getAsJsonObject();

        assertEquals("69579CBED801CE9FE09559C7D21895EB31639F42ABA511EC3A3AF87EEF72E6DC", hash(Files.readAllBytes(archive)));
        assertEquals(source.get("archive_sha256").getAsString(), hash(Files.readAllBytes(archive)));
        assertEquals("f8510907e2809a71858f14af7ab1d5aaf14d441e", source.get("source_commit").getAsString());
        assertEquals("4BD869BCFE8ABAC82D3D073E51982D8726076B3E0B8B510EEF075E242129E99F",
                source.get("source_jar_sha256").getAsString());
        assertTrue(source.get("source_unchanged").getAsBoolean());
        assertEquals(1981, source.getAsJsonArray("states").size());
        assertEquals(970, source.get("wet_states").getAsInt());
        assertEquals(311, source.get("source_container_units").getAsInt());
        assertEquals(1, source.getAsJsonArray("entities").size());
        assertEquals(31, source.get("removed_root_states").getAsInt());
        assertEquals(84, source.get("source_biome_variants").getAsInt());
        assertEquals(111, source.get("source_root_item_units").getAsInt());
        assertTrue(source.has("player_shovel") && source.has("player_nested") && source.has("world_state"));
        try (ZipFile saved = new ZipFile(archive.toFile())) {
            for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : source.getAsJsonObject("files").entrySet()) {
                try (java.io.InputStream input = saved.getInputStream(saved.getEntry(entry.getKey()))) {
                    assertEquals(entry.getValue().getAsString(), hash(input.readAllBytes()), entry.getKey());
                }
            }
            try (java.io.InputStream input = saved.getInputStream(saved.getEntry("level.dat"))) {
                CompoundTag level = NbtIo.readCompressed(input, net.minecraft.nbt.NbtAccounter.unlimitedHeap()).getCompoundOrEmpty("Data");
                assertEquals(2730, level.getIntOr("DataVersion", 0), "The source must be a genuinely flattened 1.16 save");
            }
        }
    }

    private static String hash(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            result.append(String.format(java.util.Locale.ROOT, "%02X", value & 255));
        }
        return result.toString();
    }
}
