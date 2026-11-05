package zone.moddev.mc.skysbuildingpiecesbop;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecipeAdvancementContractTest {
    @Test
    void everyBookEntryRewardsItsRecipeAndUsesAlternativeUnlockCriteria() throws Exception {
        JsonArray entries = new JsonParser().parse(read(Paths.get("src/main/recipe-book/native-1.14-generated.json")))
                .getAsJsonArray();
        int count = 0;
        Set<String> recipes = new HashSet<>();

        for (JsonElement element : entries) {
            String relative = element.getAsString();

            if (!relative.contains("/advancements/")) {
                continue;
            }

            assertTrue(recipes.add(relative));

            Path path = Paths.get("src/main/resources", relative);
            JsonObject advancement = new JsonParser().parse(read(path)).getAsJsonObject();

            assertFalse(advancement.has("advancements"), "NeoForge uses native conditional advancement data");
            check(advancement);
            count++;
        }

        assertEquals(416, count);
    }

    private void check(JsonObject advancement) {
        JsonArray rewards = advancement.getAsJsonObject("rewards").getAsJsonArray("recipes");

        assertEquals(1, rewards.size());

        String recipe = rewards.get(0).getAsString();
        String[] identity = recipe.split(":", 2);

        assertTrue(
                Files.isRegularFile(
                        Paths.get("src/main/resources/data/" + identity[0] + "/recipes/" + identity[1] + ".json")),
                recipe);
        assertFalse(advancement.has("parent"),
                "Large recipe catalogues must not share a recursive visibility parent: " + recipe);
        assertEquals(recipe, advancement.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString());

        JsonObject criteria = advancement.getAsJsonObject("criteria");

        assertEquals("minecraft:recipe_unlocked",
                criteria.getAsJsonObject("has_the_recipe").get("trigger").getAsString());

        JsonArray requirements = advancement.getAsJsonArray("requirements");

        assertEquals(1, requirements.size(), "Unlock criteria must be alternatives: " + recipe);
        assertEquals(criteria.size(), requirements.get(0).getAsJsonArray().size());

        for (Map.Entry<String, JsonElement> criterion : criteria.entrySet()) {
            if (criterion.getKey().equals("has_the_recipe")) {
                continue;
            }

            assertEquals("minecraft:inventory_changed",
                    criterion.getValue().getAsJsonObject().get("trigger").getAsString());
        }
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
