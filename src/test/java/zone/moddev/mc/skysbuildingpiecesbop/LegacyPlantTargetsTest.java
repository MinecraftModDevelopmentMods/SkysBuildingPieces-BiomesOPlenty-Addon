package zone.moddev.mc.skysbuildingpiecesbop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class LegacyPlantTargetsTest {
    @Test
    void oldSingleRootKeepsAOneBlockReplacement() {
        assertEquals("biomesoplenty:dead_grass", LegacyPlantItems.target("biomesoplenty:plant_1", 9));
    }

    @Test
    void laterNamedRootStillUsesSmallDripleaf() {
        assertEquals("minecraft:small_dripleaf", NamedBopRecovery.currentItemId("biomesoplenty:mangrove_root"));
    }

    @Test
    void oldGrassItemsUseTheCurrentPlantIdentity() {
        for (int metadata : new int[] {0, 1, 7, 10}) {
            assertEquals("minecraft:short_grass", LegacyPlantItems.target("biomesoplenty:plant_0", metadata));
        }
    }

    @Test
    void recoveredRootTargetRemainsStableOnReload() {
        assertEquals("biomesoplenty:dead_grass", NamedBopRecovery.currentItemId(
                LegacyPlantItems.target("biomesoplenty:plant_1", 9)));
    }

    @Test
    void unknownPlantSlotsStopWithAUsefulFailure() {
        assertThrows(IllegalStateException.class,
                () -> LegacyPlantItems.target("biomesoplenty:plant_1", 16));
    }
}
