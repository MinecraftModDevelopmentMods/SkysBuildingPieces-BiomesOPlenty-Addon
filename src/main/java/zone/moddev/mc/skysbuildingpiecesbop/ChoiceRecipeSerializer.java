package zone.moddev.mc.skysbuildingpiecesbop;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.common.conditions.ConditionalOps;

import java.util.List;

/** Picks the installed material's ordinary recipe without changing its saved recipe ID. */
final class ChoiceRecipeSerializer {
    private static final MapCodec<Recipe<?>> CODEC = ConditionalOps.decodeListWithElementConditions(Recipe.CODEC)
            .<Recipe<?>>flatXmap(recipes -> recipes.size() == 1 ? DataResult.success(recipes.getFirst())
                    : DataResult.error(() -> "Expected one active building recipe, found " + recipes.size()),
                    recipe -> DataResult.success(List.of(recipe)))
            .fieldOf("recipes");

    private ChoiceRecipeSerializer() {
    }

    static RecipeSerializer<Recipe<?>> create() {
        // The selected recipe uses its own serializer on the wire.
        StreamCodec<RegistryFriendlyByteBuf, Recipe<?>> dataOnly = StreamCodec.of(
                (buffer, recipe) -> {
                    throw dataPackOnly();
                },
                buffer -> {
                    throw dataPackOnly();
                });
        return new RecipeSerializer<>(CODEC, dataOnly);
    }

    private static UnsupportedOperationException dataPackOnly() {
        return new UnsupportedOperationException("Recipe choices are only read from data packs");
    }
}
