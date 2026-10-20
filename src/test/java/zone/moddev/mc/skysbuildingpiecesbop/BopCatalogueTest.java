package zone.moddev.mc.skysbuildingpiecesbop;

import com.google.gson.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.skysbuildingpieces.catalogue.*;
import static org.junit.jupiter.api.Assertions.*;

class BopCatalogueTest {
    private static final Path ROOT = Paths.get("src/main/resources/assets/skysbuildingpiecesbop");

    private Catalogue.Module module() {
        return new Catalogue().registerModule("biomesoplenty", "skysbuildingpiecesbop",
                Catalogue.read(getClass(), "/assets/skysbuildingpiecesbop/catalogue/materials.json"),
                Catalogue.read(getClass(), "/assets/skysbuildingpiecesbop/catalogue/palettes.json"), 80);
    }

    @Test
    void fixedCatalogueBudgetAndAllMetadata() throws Exception {
        Catalogue.Module catalogueModule = module();

        assertEquals(33, catalogueModule.materials.size());
        assertEquals(66, catalogueModule.palettes.size());

        Catalogue core = new Catalogue();

        assertEquals(235, core.palettes.size());
        assertEquals(302, core.palettes.size() + 1 + catalogueModule.palettes.size());

        Catalogue.Palette dirt = core.palettes.get(234);

        assertEquals("dirt_vertical_slab_00", dirt.id);
        assertEquals(Collections.singletonList("minecraft:dirt"), dirt.materials);
        assertTrue(catalogueModule.materials.containsKey("biomesoplenty:planks_ebony"));
        assertTrue(catalogueModule.materials.containsKey("biomesoplenty:planks_eucalyptus"));

        Set<String> identities = new HashSet<String>();

        for (Catalogue.Palette palette : catalogueModule.palettes) {
            assertTrue(identities.add(palette.registryId()));
            assertEquals("skysbuildingpiecesbop", palette.namespace);

            for (String material : palette.materials) {
                for (int orientation = 0; orientation < palette.shape.states; orientation++) {
                    int meta = palette.meta(material, orientation);

                    assertTrue(meta < 16);
                    assertEquals(material, palette.material(meta));
                    assertEquals(orientation, meta % palette.shape.states);
                }
            }

            assertFalse(palette.group.equals("wood") && palette.shape == Shape.WALL);

            for (String material : palette.materials) {
                assertNull(catalogueModule.materials.get(material).block(palette.shape.name().toLowerCase(Locale.ROOT)),
                        "native equivalent must be reused");
            }
        }

        Properties locked = new Properties();

        try (java.io.InputStream in = Files.newInputStream(Paths.get("gradle/catalogue-checksums.properties"))) {
            locked.load(in);
        }
        for (String name : new String[] { "materials", "palettes" }) {
            StringBuilder hash = new StringBuilder();

            for (byte b : MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(ROOT.resolve("catalogue/" + name + ".json")))) {
                hash.append(String.format("%02X", b));
            }

            assertEquals(locked.getProperty(name), hash.toString());
        }
    }

    @Test
    void hellbarkAndAllNativeMaterialsHaveExplicitFireRules() {
        Catalogue.Module catalogueModule = module();

        for (Catalogue.MaterialDef materialDefinition : catalogueModule.materials.values()) {
            assertTrue(materialDefinition.nativeProperties());
            assertTrue(materialDefinition.flammability() >= 0);
            assertTrue(materialDefinition.fireSpread() >= 0);
        }

        assertEquals(0, catalogueModule.materials.get("biomesoplenty:planks_hellbark").flammability());
        assertEquals(0, catalogueModule.materials.get("biomesoplenty:planks_hellbark").fireSpread());
        assertEquals(0, catalogueModule.materials.get("biomesoplenty:bamboo_thatching").flammability());
        assertEquals(20, catalogueModule.materials.get("biomesoplenty:planks_redwood").flammability());
        assertEquals(5, catalogueModule.materials.get("biomesoplenty:limestone").explosionResistance());
        assertEquals(7, catalogueModule.materials.get("biomesoplenty:limestone_polished").explosionResistance());
    }

    @Test
    void modelsAndTexturesAreCompleteAndInternalFacesUnculled() throws Exception {
        for (Catalogue.Palette palette : module().palettes) {
            JsonObject state = json(ROOT.resolve("blockstates/" + palette.id + ".json"));
            JsonObject variants = state.getAsJsonObject("variants");
            int perMeta = palette.shape == Shape.WALL ? 32 : palette.shape == Shape.STAIRS ? 5 : 1;

            assertEquals(16 * perMeta, variants.entrySet().size(), palette.id);

            for (int meta = 0; meta < 16; meta++) {
                int covered = 0;

                for (Map.Entry<String, JsonElement> variant : variants.entrySet()) {
                    for (String part : variant.getKey().split(",")) {
                        if (part.equals("meta=" + meta)) {
                            covered++;
                        }
                    }
                }

                assertEquals(perMeta, covered, palette.id + " / " + meta);
            }
            for (Map.Entry<String, JsonElement> entry : state.getAsJsonObject("variants").entrySet()) {
                String model = entry.getValue().getAsJsonObject().get("model").getAsString().split(":")[1];
                JsonObject data = json(ROOT.resolve("models/block/" + model + ".json"));

                assertTrue(data.has("textures"));

                int maxY = 0;

                for (JsonElement element : data.getAsJsonArray("elements")) {
                    JsonObject box = element.getAsJsonObject();

                    maxY = Math.max(maxY, box.getAsJsonArray("to").get(1).getAsInt());

                    for (Map.Entry<String, JsonElement> face : box.getAsJsonObject("faces").entrySet()) {
                        if (face.getValue().getAsJsonObject().has("cullface")) {
                            assertEquals(Shape.WALL, palette.shape, "only exterior wall ends are culled");

                            String direction = face.getKey();

                            assertFalse(direction.equals("up") || direction.equals("down"));
                            assertEquals(direction, face.getValue().getAsJsonObject().get("cullface").getAsString());

                            int axis = direction.equals("east") || direction.equals("west") ? 0 : 2;
                            boolean positive = direction.equals("east") || direction.equals("south");

                            assertEquals(positive ? 16 : 0,
                                    box.getAsJsonArray(positive ? "to" : "from").get(axis).getAsInt());
                        }
                    }
                }
                if (palette.shape == Shape.WALL) {
                    String key = entry.getKey();

                    assertTrue(key.contains("up="));

                    boolean straight = key.startsWith("connections=5,") || key.startsWith("connections=10,");

                    assertEquals(straight && key.endsWith("up=false") ? 13 : 16, maxY, key);
                }
            }
            for (String material : palette.materials) {
                assertTrue(Files.isRegularFile(ROOT.resolve("models/item/" + material.split(":")[1] + "_"
                        + palette.shape.name().toLowerCase(Locale.ROOT) + ".json")));
            }
        }

        assertFalse(Files.exists(Paths.get("src/main/resources/assets/biomesoplenty")));
    }

    @Test
    void allEighteenUtf8LocalesMatch() throws Exception {
        Set<String> expected = new TreeSet<String>(
                Arrays.asList("de_AT", "de_AU", "de_DE", "en_CA", "en_EN", "en_GB", "en_PT", "en_US", "es_ES", "es_MX",
                        "fr_CA", "fr_FR", "ja_JP", "ko_KR", "pt_BR", "pt_PT", "ru_RU", "zh_CN"));
        Set<String> actual = new TreeSet<String>();
        List<String> baseline = null;

        try (java.util.stream.Stream<Path> files = Files.list(ROOT.resolve("lang"))) {
            for (Path file : (Iterable<Path>) files::iterator) {
                actual.add(file.getFileName().toString().replace(".lang", ""));

                String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(Files.readAllBytes(file))).toString();

                assertFalse(text.startsWith("\uFEFF"));
                assertFalse(text.contains("\r"));
                assertFalse(text.contains("\uFFFD"));
                assertTrue(text.endsWith("\n"));

                List<String> keys = new ArrayList<String>();

                for (String line : text.split("\n")) {
                    assertEquals(line.trim(), line);

                    int split = line.indexOf('=');

                    assertTrue(split > 0 && split < line.length() - 1);

                    String key = line.substring(0, split);

                    assertFalse(keys.contains(key));
                    keys.add(key);
                }

                assertEquals(9 + module().materials.size(), keys.size());

                if (baseline == null) {
                    baseline = keys;
                } else {
                    assertEquals(baseline, keys);
                }
                for (String material : module().materials.keySet()) {
                    assertTrue(keys.contains("material.skysbuildingpiecesbop." + material.replace(':', '.')),
                            file + " / " + material);
                }
            }
        }

        assertEquals(expected, actual);
    }

    private JsonObject json(Path file) throws Exception {
        return new JsonParser().parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
