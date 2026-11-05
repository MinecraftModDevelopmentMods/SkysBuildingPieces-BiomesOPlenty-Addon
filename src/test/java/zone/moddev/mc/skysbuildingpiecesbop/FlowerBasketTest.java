package zone.moddev.mc.skysbuildingpiecesbop;

import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlowerBasketTest {
    private static final Path ROOT = Paths.get("src/main/resources/assets/skysbuildingpiecesbop");

    @Test
    void recipeUsesSixReusableWoodenSticks() throws Exception {
        JsonObject json = new JsonParser().parse(new String(
                Files.readAllBytes(
                        Paths.get("src/main/resources/data/skysbuildingpiecesbop/recipe/flower_basket.json")),
                StandardCharsets.UTF_8)).getAsJsonObject();

        assertEquals("minecraft:crafting_shaped", json.get("type").getAsString());
        assertEquals("#forge:rods/wooden", json.getAsJsonObject("key").get("S").getAsString());
        assertEquals("skysbuildingpiecesbop:flower_basket", json.getAsJsonObject("result").get("id").getAsString());

        StringBuilder pattern = new StringBuilder();

        for (JsonElement row : json.getAsJsonArray("pattern")) {
            pattern.append(row.getAsString());
        }

        assertEquals(" S S SSSS", pattern.toString());
        assertEquals(6, pattern.chars().filter(c -> c == 'S').count());
    }

    @Test
    void inventoryModelHasOriginalEmptyAndFullSprites() throws Exception {
        JsonObject model = json("models/item/flower_basket.json");

        assertEquals("skysbuildingpiecesbop:item/flower_basket_empty",
                model.getAsJsonObject("textures").get("layer0").getAsString());
        assertFalse(model.has("overrides"));
        JsonObject definition = json("items/flower_basket.json").getAsJsonObject("model");
        assertEquals("minecraft:condition", definition.get("type").getAsString());
        assertEquals("minecraft:component", definition.get("property").getAsString());
        assertEquals("minecraft:custom_data", definition.get("predicate").getAsString());
        assertEquals("{Items:[{}]}", definition.get("value").getAsString());
        assertEquals("skysbuildingpiecesbop:item/flower_basket_full",
                definition.getAsJsonObject("on_true").get("model").getAsString());
        assertEquals("skysbuildingpiecesbop:item/flower_basket",
                definition.getAsJsonObject("on_false").get("model").getAsString());

        for (String variant : new String[] { "empty", "full" }) {
            Path png = ROOT.resolve("textures/item/flower_basket_" + variant + ".png");
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(png.toFile());

            assertEquals(32, image.getWidth());
            assertEquals(32, image.getHeight());
            assertTrue(image.getColorModel().hasAlpha());
            assertEquals(0, image.getRGB(0, 0) >>> 24);
        }
    }

    private JsonObject json(String path) throws Exception {
        return new JsonParser().parse(new String(Files.readAllBytes(ROOT.resolve(path)), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }
}
