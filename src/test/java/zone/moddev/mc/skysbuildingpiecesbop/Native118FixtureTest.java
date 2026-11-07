package zone.moddev.mc.skysbuildingpiecesbop;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Native118FixtureTest {
    private static final Path FIXTURE = Path.of("src/testFixtures/worlds/building-pieces-bop-1.18.2");

    @Test
    void nativeSourceIsBoundToBothAcceptedProductionJars() throws Exception {
        JsonObject source = JsonParser.parseString(Files.readString(Path.of(FIXTURE + ".json"))).getAsJsonObject();
        assertEquals("1.18.2", source.get("minecraft").getAsString());
        assertEquals(2975, source.get("source_data_version").getAsInt());
        assertEquals("6746ba05e7bd0586aefd29f7208270570a5460b4", source.get("source_commit").getAsString());
        assertEquals("2be3ed9bb7f01c853e1da7f2fc1829c171132382", source.get("source_core_commit").getAsString());
        assertEquals("0A52E35356AE6229E1331A5E4BAAF99CBA22489EC4EC28A87F5BCE024BE918B5",
                java.util.HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(Path.of(FIXTURE + ".zip")))));
        assertTrue(source.get("source_world_unchanged").getAsBoolean());
        assertEquals(2394, source.getAsJsonArray("states").size());
        assertEquals(1174, source.get("wet_states").getAsInt());
        assertEquals(9, source.get("source_baskets").getAsInt());
        assertEquals(1671, source.get("source_basket_item_units").getAsInt());
        assertEquals(8, source.get("original_baskets_verified").getAsInt());
        assertEquals(1500, source.get("original_basket_item_units").getAsInt());
        Set<String> artifacts = new HashSet<>();
        source.getAsJsonArray("source_artifacts").forEach(value ->
                artifacts.add(value.getAsJsonObject().get("sha256").getAsString()));
        assertTrue(artifacts.contains("95390406BE1B3B81E6C55E802F970E03753810CBCBB2151CDA26BB1D7AF7A594"));
        assertTrue(artifacts.contains("82709CF16A7282DB5562BE5FCC2F417D0F103620EAE86DBDE9E0CBD760B51B5A"));

        int[] expectedUnits = {170, 508, 1, 8, 709, 0, 70, 34};
        var originals = source.getAsJsonObject("basket_source").getAsJsonArray("baskets");
        assertEquals(8, originals.size());
        for (int basket = 0; basket < originals.size(); basket++) {
            var stack = TagParser.parseTag(originals.get(basket).getAsJsonObject().get("stack").getAsString());
            int units = 0;
            Set<Integer> slots = new HashSet<>();
            for (var entry : stack.getCompound("tag").getList("Items", 10)) {
                var item = (net.minecraft.nbt.CompoundTag) entry;
                assertTrue(slots.add(item.getByte("Slot") & 255));
                units += item.getByte("Count") & 255;
            }
            assertEquals(expectedUnits[basket], units);
        }
    }

    @Test
    void fullHeightBiomeContractIncludesEverySourceBiome() throws Exception {
        JsonObject contract = JsonParser.parseString(Files.readString(Path.of(FIXTURE + ".biomes.json"))).getAsJsonObject();
        assertEquals("zlib-u8-quart-volume-v2", contract.get("format").getAsString());
        assertEquals(-16, contract.get("min_quart_y").getAsInt());
        assertEquals(96, contract.get("quart_height").getAsInt());
        assertEquals(368640, contract.get("checked_cells").getAsInt());
        assertEquals(93, contract.get("bop_renamed_cells").getAsInt());
        assertEquals(240, contract.getAsJsonArray("chunks").size());
        Set<String> names = new HashSet<>();
        contract.getAsJsonObject("source_names").entrySet().forEach(entry -> names.add(entry.getValue().getAsString()));
        assertEquals(65, names.stream().filter(name -> name.startsWith("biomesoplenty:")).count());
        var targets = contract.getAsJsonObject("target_names");
        assertEquals("biomesoplenty:auroral_garden", targets.get("biomesoplenty:rainbow_hills").getAsString());
        assertEquals("biomesoplenty:scrubland", targets.get("biomesoplenty:wooded_scrubland").getAsString());
        assertEquals("biomesoplenty:wasteland", targets.get("biomesoplenty:wooded_wasteland").getAsString());
        assertEquals("biomesoplenty:coniferous_forest", targets.get("biomesoplenty:boreal_forest").getAsString());
    }
}
