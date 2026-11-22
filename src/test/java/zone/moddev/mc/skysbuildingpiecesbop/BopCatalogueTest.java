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

        int pathPalettes = core.materials.containsKey("minecraft:grass_path") ? 2 : 0;

        assertEquals(235 + pathPalettes, core.palettes.size());
        assertEquals(303 + pathPalettes, core.palettes.size() + 2 + catalogueModule.palettes.size());

        if (pathPalettes != 0) {
            assertEquals("path_step_00", core.palettes.get(235).id);
            assertEquals("path_corner_00", core.palettes.get(236).id);
        }

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

            // Permanent source slots remain recoverable even when a later BOP release provides a native equivalent or
            // retires the material.
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
        assertEquals(6, catalogueModule.materials.get("biomesoplenty:limestone").explosionResistance());
        assertEquals(6, catalogueModule.materials.get("biomesoplenty:limestone_polished").explosionResistance());
    }

    @Test
    void modelsAndTexturesAreCompleteAndInternalFacesUnculled() throws Exception {
        for (Catalogue.Palette palette : module().palettes) {
            JsonObject state = json(ROOT.resolve("blockstates/" + palette.id + ".json"));
            JsonObject variants = state.getAsJsonObject("variants");
            int perMeta = palette.shape == Shape.WALL ? 32 : palette.shape == Shape.STAIRS ? 5 : 1;

            boolean singleConnectionBlock = palette.materials.size() == 1
                    && (palette.shape == Shape.WALL || palette.shape == Shape.PANE);
            int assigned = palette.materials.size() * palette.shape.states;

            assertTrue(variants.entrySet().size() >= (singleConnectionBlock ? 1 : assigned) * perMeta * 2, palette.id);
            assertTrue(variants.entrySet().size() <= (singleConnectionBlock ? 1 : 16) * perMeta * 2, palette.id);

            for (int meta = 0; meta < 16; meta++) {
                int covered = 0;

                for (Map.Entry<String, JsonElement> variant : variants.entrySet()) {
                    for (String part : variant.getKey().split(",")) {
                        if (part.equals("meta=" + meta)) {
                            covered++;
                        }
                    }
                }

                if (singleConnectionBlock) {
                    assertEquals(0, covered, palette.id);
                } else if (meta < assigned) {
                    assertEquals(perMeta * 2, covered, palette.id + " / " + meta);
                } else {
                    assertTrue(covered == 0 || covered == perMeta * 2, palette.id + " / reserved " + meta);
                }
            }
            for (Map.Entry<String, JsonElement> entry : state.getAsJsonObject("variants").entrySet()) {
                String model = entry.getValue().getAsJsonObject().get("model").getAsString().split(":")[1];
                JsonObject data = json(ROOT.resolve("models/" + model + ".json"));

                assertTrue(data.has("textures"));

                int maxY = 0;

                for (JsonElement element : data.getAsJsonArray("elements")) {
                    JsonObject box = element.getAsJsonObject();

                    maxY = Math.max(maxY, box.getAsJsonArray("to").get(1).getAsInt());

                    for (Map.Entry<String, JsonElement> face : box.getAsJsonObject("faces").entrySet()) {
                        if (face.getValue().getAsJsonObject().has("cullface")) {
                            String direction = face.getKey();

                            assertEquals(direction, face.getValue().getAsJsonObject().get("cullface").getAsString());

                            int axis = direction.equals("east") || direction.equals("west") ? 0
                                    : direction.equals("up") || direction.equals("down") ? 1 : 2;
                            boolean positive = direction.equals("east") || direction.equals("south")
                                    || direction.equals("up");

                            assertEquals(positive ? 16 : 0,
                                    box.getAsJsonArray(positive ? "to" : "from").get(axis).getAsInt());
                        }
                    }
                }
                if (palette.shape == Shape.WALL) {
                    String key = entry.getKey();

                    assertTrue(key.contains("up="));

                    boolean straight = key.startsWith("connections=5,") || key.startsWith("connections=10,");

                    assertEquals(straight && key.contains("up=false") ? 13 : 16, maxY, key);
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
                Arrays.asList("de_at", "de_au", "de_de", "en_ca", "en_en", "en_gb", "en_pt", "en_us", "es_es", "es_mx",
                        "fr_ca", "fr_fr", "ja_jp", "ko_kr", "pt_br", "pt_pt", "ru_ru", "zh_cn"));
        Set<String> actual = new TreeSet<String>();
        List<String> baseline = null;

        try (java.util.stream.Stream<Path> files = Files.list(ROOT.resolve("lang"))) {
            for (Path file : (Iterable<Path>) files::iterator) {
                actual.add(file.getFileName().toString().replace(".json", ""));

                String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(Files.readAllBytes(file))).toString();

                assertFalse(text.startsWith("\uFEFF"));
                assertFalse(text.contains("\r"));
                assertFalse(text.contains("\uFFFD"));
                assertTrue(text.endsWith("\n"));

                List<String> keys = new ArrayList<String>();

                for (Map.Entry<String, JsonElement> entry : new JsonParser().parse(text).getAsJsonObject().entrySet()) {
                    assertFalse(entry.getValue().getAsString().trim().isEmpty());
                    keys.add(entry.getKey());
                }

                assertEquals(20 + module().materials.size(), keys.size());

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
