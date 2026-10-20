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
        JsonObject json = json("recipes/flower_basket.json");

        assertEquals("forge:ore_shaped", json.get("type").getAsString());
        assertEquals("stickWood", json.getAsJsonObject("key").getAsJsonObject("S").get("ore").getAsString());
        assertEquals("skysbuildingpiecesbop:flower_basket", json.getAsJsonObject("result").get("item").getAsString());

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

        assertEquals("skysbuildingpiecesbop:items/flower_basket_empty",
                model.getAsJsonObject("textures").get("layer0").getAsString());
        assertEquals(1, model.getAsJsonArray("overrides").size());

        for (String variant : new String[] { "empty", "full" }) {
            Path png = ROOT.resolve("textures/items/flower_basket_" + variant + ".png");
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
