package zone.moddev.mc.skysbuildingpieces.probe;

import net.minecraft.server.level.ServerLevel;

/** Exercise the full catalogue without holding the server on a single tick. */
public final class BopRecipeAudit {
    private final ServerLevel world;
    private final RecipeChecks.Audit recipes;

    public BopRecipeAudit(ServerLevel world) {
        this.world = world;
        recipes = new RecipeChecks.Audit(world);
    }

    public boolean advance() {
        if (!recipes.advance(24)) {
            return false;
        }
        recipes.finish();
        RecipeUniquenessChecks.run(world);
        FuelChecks.run(world);
        return true;
    }
}
