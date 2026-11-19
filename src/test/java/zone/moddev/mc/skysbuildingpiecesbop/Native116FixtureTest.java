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

class Native116FixtureTest {
    @Test
    void acceptedNativeSourceCoversSavedStatesCrystalsAndInventories() throws Exception {
        Path archive = Paths.get("src/testFixtures/worlds/building-pieces-bop-1.16.5.zip");
        Path manifest = Paths.get("src/testFixtures/worlds/building-pieces-bop-1.16.5.json");
        JsonObject source = new JsonParser().parse(Files.readString(manifest, StandardCharsets.UTF_8)).getAsJsonObject();

        assertEquals("FBF62E044298691AFA51F53FB5AD47EC87BB9DA9702D564EC85D445EF5E254DE", hash(Files.readAllBytes(archive)));
        assertEquals(source.get("archive_sha256").getAsString(), hash(Files.readAllBytes(archive)));
        assertEquals("73b9285fa4f2d298d203d6dcac48aae97ad302d8", source.get("source_commit").getAsString());
        assertEquals("2470024008508957A7FAEC5ED9171E56725A00100ADBC4481C3F48C808DBDFC9",
                source.get("source_jar_sha256").getAsString());
        assertTrue(source.get("source_unchanged").getAsBoolean());
        assertEquals(1950, source.getAsJsonArray("states").size());
        assertEquals(968, source.get("wet_states").getAsInt());
        assertEquals(200, source.get("source_container_units").getAsInt());
        assertEquals(1, source.getAsJsonArray("entities").size());
        assertTrue(source.has("player_shovel") && source.has("player_nested") && source.has("world_state"));
        try (ZipFile saved = new ZipFile(archive.toFile())) {
            for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : source.getAsJsonObject("files").entrySet()) {
                try (java.io.InputStream input = saved.getInputStream(saved.getEntry(entry.getKey()))) {
                    assertEquals(entry.getValue().getAsString(), hash(input.readAllBytes()), entry.getKey());
                }
            }
            try (java.io.InputStream input = saved.getInputStream(saved.getEntry("level.dat"))) {
                CompoundTag level = NbtIo.readCompressed(input, net.minecraft.nbt.NbtAccounter.unlimitedHeap()).getCompoundOrEmpty("Data");
                assertEquals(2586, level.getIntOr("DataVersion", 0), "The source must be a genuinely flattened 1.16 save");
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
