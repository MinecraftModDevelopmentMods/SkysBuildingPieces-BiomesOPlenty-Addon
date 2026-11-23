package zone.moddev.mc.skysbuildingpiecesbop;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Base64;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.util.datafix.fixes.CavesAndCliffsRenames;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independently checks the raw saved content behind the narrow log profiles. */
class NativeRegistryNoticeProofTest {
    @Test
    void permittedRegistryNamesHaveCompleteSavedContentProof() throws Exception {
        JsonObject profiles = new JsonParser().parse(new String(
                Files.readAllBytes(Paths.get("tools/native-log-profiles.json")), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("profiles");
        assertEquals(8, profiles.size());
        Set<String> versions = new HashSet<>();

        for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : profiles.entrySet()) {
            JsonObject profile = entry.getValue().getAsJsonObject();
            Set<String> names = new HashSet<>();
            for (java.util.Map.Entry<String, com.google.gson.JsonElement> registry
                    : profile.getAsJsonObject("mappings").entrySet()) {
                // Biomes are used terrain data. Check their complete source
                // volumes below, separately from genuinely unused entries.
                if (registry.getKey().equals("minecraft:worldgen/biome")) {
                    if (profile.has("static_biome_contract")) {
                        JsonObject proof = boundJson(profile.getAsJsonObject("static_biome_contract"));
                        assertEquals(profile.get("sha256"), proof.get("source_world_sha256"));
                        assertEquals(registry.getValue(), proof.get("snapshot_entries"));
                        assertEquals(profile.getAsJsonObject("native_biome_contract").get("sha256"),
                                proof.get("native_biome_contract_sha256"));
                    } else {
                        assertEquals(37, registry.getValue().getAsJsonObject().size());
                    }
                    continue;
                }
                for (java.util.Map.Entry<String, com.google.gson.JsonElement> mapping
                        : registry.getValue().getAsJsonObject().entrySet()) {
                    names.add(mapping.getKey());
                }
            }
            int expectedUnused = (profile.has("legacy_biome_registry") ? 10 : 0)
                    + (profile.has("retired_poi_contract") ? 2 : 0);
            if (profile.has("forge47_unused_registry_contract")) {
                JsonObject proof = boundJson(profile.getAsJsonObject("forge47_unused_registry_contract"));
                assertEquals(profile.get("sha256"), proof.get("source_world_sha256"));
                assertEquals(profile.get("mappings"), proof.get("snapshot_entries"));
                JsonObject uses = proof.getAsJsonObject("saved_uses");
                assertEquals(names, uses.keySet());
                assertEquals(7, uses.size());
                uses.entrySet().forEach(usage -> assertEquals(0, usage.getValue().getAsInt(), usage.getKey()));
                expectedUnused += uses.size();
            }
            assertEquals(expectedUnused, names.size());
            assertEquals(0, profile.get("non_biome_saved_uses").getAsInt());
            BiomeProof biomes = new BiomeProof(profile);
            versions.add(biomes.version);
            assertEquals(entry.getKey(), checksum(Files.readAllBytes(Paths.get(profile.get("fixture").getAsString()))));
            int records = 0;

            try (ZipFile archive = new ZipFile(profile.get("fixture").getAsString())) {
                java.util.Enumeration<? extends ZipEntry> members = archive.entries();
                while (members.hasMoreElements()) {
                    ZipEntry member = members.nextElement();
                    String label = member.getName();
                    try (InputStream input = archive.getInputStream(member)) {
                        if (label.endsWith(".dat") || label.endsWith(".dat_old")) {
                            CompoundTag root = NbtIo.readCompressed(input);
                            if (label.equals("level.dat")) verifyRegistryNumbers(root, profile);
                            // Registry availability is not a saved block, entity or inventory use.
                            root.remove("FML");
                            root.remove("fml");
                            inspect(root, names, label);
                            biomes.inspect(root);
                            records++;
                        } else if (label.endsWith(".mca")) {
                            records += region(readAll(input), names, label, biomes);
                        }
                    }
                }
            }
            assertEquals(profile.get("inspected_nbt_records").getAsInt(), records);
            biomes.complete(profile);
        }
        assertEquals(Set.of("1.14.4", "1.15.2", "1.16.5", "1.17.1", "1.18.2", "1.19.4"), versions);
    }

    private static JsonObject boundJson(JsonObject binding) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(binding.get("path").getAsString()));
        assertEquals(binding.get("sha256").getAsString(), checksum(bytes));
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void verifyRegistryNumbers(CompoundTag root, JsonObject profile) {
        CompoundTag registries = root.getCompound(root.contains("fml", 10) ? "fml" : "FML").getCompound("Registries");
        for (var registry : profile.getAsJsonObject("mappings").entrySet()) {
            CompoundTag snapshot = registries.getCompound(registry.getKey());
            if (registry.getKey().equals("minecraft:worldgen/biome") && snapshot.isEmpty()) {
                snapshot = registries.getCompound(registries.contains("minecraft:biome", 10)
                        ? "minecraft:biome" : "minecraft:biomes");
            }
            assertFalse(snapshot.isEmpty(), "Missing source registry: " + registry.getKey());
            Map<String, Integer> numbers = new HashMap<>();
            ListTag rows = snapshot.getList("ids", 10);
            for (int index = 0; index < rows.size(); index++) {
                CompoundTag row = rows.getCompound(index);
                assertNull(numbers.put(row.getString("K"), row.getInt("V")));
            }
            for (var entry : registry.getValue().getAsJsonObject().entrySet()) {
                assertEquals(entry.getValue().getAsInt(), numbers.get(entry.getKey()),
                        "The permitted notice does not match the source registry: " + entry.getKey());
            }
        }
    }

    private static int region(byte[] bytes, Set<String> names, String label, BiomeProof biomes) throws Exception {
        if (bytes.length == 0) {
            return 0;
        }
        assertTrue(bytes.length >= 8192, label);
        ByteBuffer header = ByteBuffer.wrap(bytes);
        int records = 0;
        for (int slot = 0; slot < 1024; slot++) {
            int location = header.getInt(slot * 4);
            if (location == 0) {
                continue;
            }
            int offset = (location >>> 8) * 4096;
            int length = header.getInt(offset);
            int compression = bytes[offset + 4] & 255;
            InputStream payload = new ByteArrayInputStream(bytes, offset + 5, length - 1);
            if (compression == 1) {
                payload = new GZIPInputStream(payload);
            } else if (compression == 2) {
                payload = new InflaterInputStream(payload);
            } else {
                assertEquals(3, compression, "Unexpected fixture compression: " + label);
            }
            try (DataInputStream input = new DataInputStream(payload)) {
                CompoundTag root = NbtIo.read(input);
                inspect(root, names, label + ":" + slot);
                biomes.inspect(root);
            }
            records++;
        }
        return records;
    }

    private static String checksum(byte[] data) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder text = new StringBuilder();
        for (byte value : digest) {
            text.append(String.format("%02X", value & 255));
        }
        return text.toString();
    }

    /** The runtime also checks these same cells after vanilla's data fixing. */
    private static final class BiomeProof {
        private final JsonObject contract;
        private final JsonObject names;
        private final Map<String, byte[]> volumes = new HashMap<>();
        private final Set<String> visited = new HashSet<>();
        private final String version;
        private final boolean nativeHeight;
        private final JsonObject bopReplacements;
        private final Set<String> noticedBiomes = new HashSet<>();
        private final Set<String> liveBiomes = new HashSet<>();
        private long dynamicCells;
        private long replacedBopCells;
        private long unnoticedCells;
        private long cells;
        private long renamed;
        private int partialChunks;

        BiomeProof(JsonObject profile) throws Exception {
            JsonObject proof = profile.getAsJsonObject("native_biome_contract");
            byte[] bytes = Files.readAllBytes(Paths.get(proof.get("path").getAsString()));
            assertEquals(proof.get("sha256").getAsString(), checksum(bytes));
            contract = new JsonParser().parse(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            String format = contract.get("format").getAsString();
            nativeHeight = format.equals("zlib-u8-quart-volume-v2");
            assertTrue(nativeHeight || format.equals("zlib-u8-quart-volume-v1"));
            if (nativeHeight) {
                assertEquals(-16, contract.get("min_quart_y").getAsInt());
                assertEquals(96, contract.get("quart_height").getAsInt());
            }
            // Older receipts use their own target catalogue. Keep those
            // expectations separate from the biome names available here.
            String targetForge = profile.has("target_forge") ? profile.get("target_forge").getAsString() : "45.4.0";
            boolean neo1201 = targetForge.equals("47.1.106");
            boolean target1201 = neo1201 || targetForge.equals("47.4.10");
            byte[] aliases = Files.readAllBytes(Paths.get(target1201
                    ? "src/main/resources/assets/skysbuildingpiecesbop/catalogue/named_legacy.json"
                    : "src/testFixtures/bop-1.19.4-named-recovery.json"));
            if (!target1201) {
                assertEquals("B665129A6A455FD9E7815D2D4E0FE7D358386752541034A991FF4372B56D7CF0", checksum(aliases));
            }
            bopReplacements = JsonParser.parseString(new String(aliases, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject(nativeHeight ? "section_biomes" : "biomes");
            if (profile.has("live_biome_contract")) {
                JsonObject live = boundJson(profile.getAsJsonObject("live_biome_contract"));
                assertEquals(target1201 ? "1.20.1" : "1.19.4", live.get("minecraft").getAsString());
                assertEquals(targetForge, live.get("forge").getAsString());
                for (var row : live.getAsJsonArray("entries")) {
                    assertTrue(liveBiomes.add(row.getAsJsonObject().get("id").getAsString()));
                }
                assertEquals(targetForge.equals("47.4.10") ? 134 : 128, liveBiomes.size());
                JsonObject snapshot = profile.getAsJsonObject("mappings");
                if (snapshot.has("minecraft:worldgen/biome")) {
                    noticedBiomes.addAll(snapshot.getAsJsonObject("minecraft:worldgen/biome").keySet());
                }
                for (var target : contract.getAsJsonObject("target_names").entrySet()) {
                    assertTrue(liveBiomes.contains(target.getValue().getAsString()), target.getKey());
                }
            }
            assertEquals(profile.get("sha256"), contract.get("source_world_sha256"));
            assertEquals(proof.get("source"), contract.get("source_minecraft"));
            version = proof.get("source").getAsString();
            names = contract.getAsJsonObject("source_names");
            for (com.google.gson.JsonElement value : contract.getAsJsonArray("chunks")) {
                JsonObject row = value.getAsJsonObject();
                String position = row.get("x").getAsInt() + "," + row.get("z").getAsInt();
                try (InputStream packed = new InflaterInputStream(new ByteArrayInputStream(
                        Base64.getDecoder().decode(row.get("cells").getAsString())))) {
                    byte[] volume = readAll(packed);
                    assertEquals(nativeHeight ? 1536 : 1024, volume.length, position);
                    assertNull(volumes.put(position, volume), "Repeated source chunk " + position);
                }
            }
        }

        void inspect(CompoundTag root) {
            if (!(nativeHeight ? root.contains("sections", 9) : root.contains("Level", 10))) {
                return;
            }
            CompoundTag level = nativeHeight ? root : root.getCompound("Level");
            String status = level.getString("Status");
            if (!status.equals("full") && !status.equals("minecraft:full")) {
                if (!version.equals("1.19.4")) {
                    return;
                }
                // The adjacent fixture also seals complete palettes in its
                // saved halo, including chunks whose generation is unfinished.
                partialChunks++;
            }
            String position = level.getInt("xPos") + "," + level.getInt("zPos");
            byte[] expected = volumes.get(position);
            assertNotNull(expected, "Missing biome contract for " + position);
            assertTrue(visited.add(position), "Repeated saved chunk " + position);
            if (nativeHeight) {
                inspectNativeSections(level, expected, position);
                return;
            }
            int[] saved;
            if (level.contains("Biomes", 7)) {
                byte[] legacy = level.getByteArray("Biomes");
                saved = new int[legacy.length];
                for (int column = 0; column < legacy.length; column++) {
                    saved[column] = Byte.toUnsignedInt(legacy[column]);
                }
            } else {
                assertTrue(level.contains("Biomes", 11), position);
                saved = level.getIntArray("Biomes");
            }
            assertTrue(saved.length == 256 || saved.length == 1024, position);
            for (int cell = 0; cell < expected.length; cell++) {
                int source = saved.length == 1024 ? saved[cell]
                        : saved[((cell >> 2 & 3) * 4 + 2) * 16 + (cell & 3) * 4 + 2];
                assertEquals(source, Byte.toUnsignedInt(expected[cell]), position + " cell=" + cell);
                assertTrue(names.has(Integer.toString(source)), position);
                String oldName = names.get(Integer.toString(source)).getAsString();
                String current = CavesAndCliffsRenames.RENAMES.getOrDefault(oldName, oldName);
                if (current.equals("minecraft:deep_warm_ocean")) {
                    current = "minecraft:warm_ocean";
                }
                if (oldName.equals("minecraft:nether")) {
                    current = "minecraft:nether_wastes";
                }
                if (oldName.startsWith("minecraft:")) {
                    assertEquals(current, contract.getAsJsonObject("target_names").get(oldName).getAsString());
                } else if (!liveBiomes.isEmpty()) {
                    current = bopReplacements.has(oldName) ? bopReplacements.get(oldName).getAsString() : oldName;
                    assertEquals(current, contract.getAsJsonObject("target_names").get(oldName).getAsString());
                } else {
                    current = contract.getAsJsonObject("target_names").get(oldName).getAsString();
                }
                countBiome(oldName, current);
            }
        }

        private void inspectNativeSections(CompoundTag level, byte[] expected, String position) {
            Map<Integer, CompoundTag> sections = new HashMap<>();
            for (Tag value : level.getList("sections", 10)) {
                CompoundTag section = (CompoundTag) value;
                assertNull(sections.put(section.getInt("Y"), section), "Repeated source section");
            }
            for (int cell = 0; cell < expected.length; cell++) {
                CompoundTag section = sections.get(-4 + cell / 64);
                assertNotNull(section, position + " cell=" + cell);
                CompoundTag container = section.getCompound("biomes");
                ListTag palette = container.getList("palette", 8);
                assertFalse(palette.isEmpty());
                int paletteIndex = 0;
                if (palette.size() > 1) {
                    int bits = 32 - Integer.numberOfLeadingZeros(palette.size() - 1);
                    int valuesPerWord = 64 / bits;
                    int localCell = cell & 63;
                    long word = container.getLongArray("data")[localCell / valuesPerWord];
                    paletteIndex = (int) (word >>> (localCell % valuesPerWord * bits) & ((1L << bits) - 1));
                }
                String source = palette.getString(paletteIndex);
                String recorded = names.get(Integer.toString(Byte.toUnsignedInt(expected[cell]))).getAsString();
                assertEquals(recorded, source, position + " cell=" + cell);
                String current = bopReplacements.has(source) ? bopReplacements.get(source).getAsString() : source;
                assertEquals(current, contract.getAsJsonObject("target_names").get(source).getAsString());
                assertTrue(liveBiomes.contains(current), "Missing live biome " + current);
                countBiome(source, current);
            }
        }

        private void countBiome(String source, String current) {
            if (source.startsWith("minecraft:") && !source.equals(current)) {
                renamed++;
            } else if (source.startsWith("biomesoplenty:") && !source.equals(current)) {
                assertFalse(noticedBiomes.contains(source), "A replaced BOP biome needs its explicit handler");
                replacedBopCells++;
            } else if (noticedBiomes.contains(source)) {
                dynamicCells++;
            } else {
                unnoticedCells++;
            }
            cells++;
        }

        void complete(JsonObject profile) {
            JsonObject proof = profile.getAsJsonObject("native_biome_contract");
            assertEquals(volumes.keySet(), visited);
            assertEquals(proof.get("checked_cells").getAsLong(), cells);
            assertEquals(contract.get("checked_cells").getAsLong(), cells);
            assertEquals(proof.get("renamed_cells").getAsLong(), renamed);
            assertEquals(contract.get("native_renamed_cells").getAsLong(), renamed);
            assertEquals(profile.get("saved_uses").getAsLong(), renamed + dynamicCells);
            if (version.equals("1.19.4")) {
                assertEquals(contract.get("partial_chunks").getAsInt(), partialChunks);
            }
            if (profile.has("static_biome_contract")) {
                assertEquals(profile.get("dynamic_biome_saved_uses").getAsLong(), dynamicCells);
                try {
                    JsonObject snapshot = boundJson(profile.getAsJsonObject("static_biome_contract"));
                    assertEquals(snapshot.get("dynamic_biome_saved_uses").getAsLong(), dynamicCells);
                    assertEquals(snapshot.get("bop_renamed_cells").getAsLong(), replacedBopCells);
                    assertEquals(snapshot.get("unnoticed_biome_cells").getAsLong(), unnoticedCells);
                    assertEquals(snapshot.get("live_biome_contract_sha256"),
                            profile.getAsJsonObject("live_biome_contract").get("sha256"));
                    JsonObject poi = boundJson(profile.getAsJsonObject("retired_poi_contract"));
                    assertEquals(profile.get("inspected_nbt_records"), poi.get("inspected_nbt_records"));
                    assertTrue(poi.get("inspected_nbt_values").getAsLong() > 0);
                    assertEquals(0, poi.getAsJsonObject("saved_uses").get("minecraft:nitwit").getAsInt());
                    assertEquals(0, poi.getAsJsonObject("saved_uses").get("minecraft:unemployed").getAsInt());
                } catch (Exception failure) {
                    throw new AssertionError("Cannot verify the registry proof bindings", failure);
                }
            }
        }
    }

    private static void inspect(Tag value, Set<String> names, String label) {
        if (value instanceof CompoundTag) {
            CompoundTag compound = (CompoundTag) value;
            for (String key : compound.getAllKeys()) {
                assertFalse(names.contains(key), "Saved registry-name key in " + label + ": " + key);
                inspect(compound.get(key), names, label + "/" + key);
            }
        } else if (value instanceof ListTag) {
            for (Tag entry : (ListTag) value) {
                inspect(entry, names, label);
            }
        } else if (value instanceof StringTag) {
            assertFalse(names.contains(value.getAsString()), "Saved registry-name value in " + label);
        }
    }

    private static byte[] readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
