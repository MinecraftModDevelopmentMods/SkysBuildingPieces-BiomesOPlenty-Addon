package zone.moddev.mc.skysbuildingpiecesbop.probe;

import net.minecraft.server.level.ServerLevel;

/** Run the add-on's native regressions after the shared placement and recipe checks. */
public final class BopChecks {
    private BopChecks() {
    }

    public static void run(ServerLevel world) throws Exception {
        BasketChecks.run(world);
        RetiredItemChecks.run(world);
        PlantRecoveryChecks.run();
        DecorationChecks.run(world);
        MaterialChecks.run(world);
        zone.moddev.mc.skysbuildingpieces.probe.BopMigrationChecks.run(world);
    }
}
