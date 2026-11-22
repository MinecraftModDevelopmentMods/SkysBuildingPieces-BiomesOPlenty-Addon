package zone.moddev.mc.skysbuildingpiecesbop;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrystalOrientationTest {
    @Test
    void allTwelveSavedAttachmentsKeepTheirMountingDirection() {
        for (String face : new String[] {"floor", "ceiling", "wall"}) {
            for (String facing : new String[] {"north", "east", "south", "west"}) {
                CompoundTag original = new CompoundTag();
                original.putString("face", face);
                original.putString("facing", facing);
                CompoundTag before = original.copy();
                CompoundTag converted = NamedBopRecovery.crystalProperties(original);
                String expected = face.equals("floor") ? "up" : face.equals("ceiling") ? "down" : facing;
                assertEquals(expected, converted.getString("facing"));
                assertEquals("false", converted.getString("waterlogged"));
                assertFalse(converted.contains("face"));
                assertEquals(before, original);
            }
        }
    }

    @Test
    void partialItemPropertiesUseTheOriginalDefaultState() {
        assertEquals("up", NamedBopRecovery.crystalProperties(new CompoundTag()).getString("facing"));
        CompoundTag wall = new CompoundTag();
        wall.putString("face", "wall");
        assertEquals("north", NamedBopRecovery.crystalProperties(wall).getString("facing"));
    }

    @Test
    void malformedOrUnknownPropertiesStopRecovery() {
        for (String field : new String[] {"face", "facing", "unknown"}) {
            CompoundTag original = new CompoundTag();
            original.putInt(field, 3);
            assertThrows(IllegalStateException.class, () -> NamedBopRecovery.crystalProperties(original));
        }
        CompoundTag wrongDirection = new CompoundTag();
        wrongDirection.putString("face", "wall");
        wrongDirection.putString("facing", "up");
        assertThrows(IllegalStateException.class, () -> NamedBopRecovery.crystalProperties(wrongDirection));
        CompoundTag wrongFace = new CompoundTag();
        wrongFace.putString("face", "sideways");
        assertThrows(IllegalStateException.class, () -> NamedBopRecovery.crystalProperties(wrongFace));
    }
}
