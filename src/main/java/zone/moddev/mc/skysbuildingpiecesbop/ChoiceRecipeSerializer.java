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
final class ChoiceRecipeSerializer implements RecipeSerializer<Recipe<?>> {
    private static final MapCodec<Recipe<?>> CODEC = ConditionalOps.decodeListWithElementConditions(Recipe.CODEC)
            .<Recipe<?>>flatXmap(recipes -> recipes.size() == 1 ? DataResult.success(recipes.getFirst())
                    : DataResult.error(() -> "Expected one active building recipe, found " + recipes.size()),
                    recipe -> DataResult.success(List.of(recipe)))
            .fieldOf("recipes");

    @Override
    public MapCodec<Recipe<?>> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, Recipe<?>> streamCodec() {
        // The selected recipe is already a native shaped or shapeless recipe. It uses its own codec on the wire.
        throw new UnsupportedOperationException("Recipe choices are only read from data packs");
    }
}
