package zone.moddev.mc.skysbuildingpiecesbop;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PieceNamesTest {
    @Test
    void translatedGemNamesDoNotIncludeTheFullBlockLabel() throws Exception {
        Map<String, String> expected = new HashMap<String, String>();

        expected.put("en", "Sapphire Wall");
        expected.put("de", "Mauer aus Saphir");
        expected.put("es", "Muro de zafiro");
        expected.put("fr", "Muret en saphir");
        expected.put("ja", "サファイアの塀");
        expected.put("ko", "사파이어 담장");
        expected.put("pt", "Muro de safira");
        expected.put("ru", "Ограда из сапфира");
        expected.put("zh", "蓝宝石墙");

        Path root = Paths.get("src/main/resources/assets/skysbuildingpiecesbop/lang");

        try (java.util.stream.Stream<Path> files = Files.list(root)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Properties names = new Properties();

                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    names.load(reader);
                }

                assertEquals(expected.get(file.getFileName().toString().substring(0, 2)),
                        String.format(Locale.ROOT, names.getProperty("pieces.skysbuildingpiecesbop.wall"),
                                names.getProperty("material.skysbuildingpiecesbop.biomesoplenty.sapphire_block")));

                for (String key : names.stringPropertyNames()) {
                    if (key.startsWith("pieces.")) {
                        String result = String.format(Locale.ROOT, names.getProperty(key), "TEST");

                        assertEquals(1, result.split("TEST", -1).length - 1, key);
                    }
                }
            }
        }

        Path file = root.resolve(Files.exists(root.resolve("en_US.lang")) ? "en_US.lang" : "en_us.lang");
        Properties english = new Properties();

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            english.load(reader);
        }

        assertEquals("Redwood", english.getProperty("material.skysbuildingpiecesbop.biomesoplenty.planks_redwood"));
        assertEquals("Polished Limestone",
                english.getProperty("material.skysbuildingpiecesbop.biomesoplenty.limestone_polished"));
    }
}
