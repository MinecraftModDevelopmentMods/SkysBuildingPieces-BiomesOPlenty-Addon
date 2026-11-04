package zone.moddev.mc.skysbuildingpiecesbop;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SectionBiomeRecoveryTest {
    @Test
    void removedSectionBiomesKeepTheirCellIndicesAndUnrelatedData() {
        CompoundTag section = new CompoundTag();
        CompoundTag biomes = new CompoundTag();
        ListTag palette = new ListTag();
        palette.add(StringTag.valueOf("biomesoplenty:rainbow_hills"));
        palette.add(StringTag.valueOf("biomesoplenty:wooded_scrubland"));
        palette.add(StringTag.valueOf("biomesoplenty:wooded_wasteland"));
        palette.add(StringTag.valueOf("biomesoplenty:boreal_forest"));
        palette.add(StringTag.valueOf("biomesoplenty:seasonal_orchard"));
        biomes.put("palette", palette);
        biomes.putLongArray("data", new long[] {0x3210432104321043L, 0x1234012340123401L});
        section.put("biomes", biomes);
        section.putByte("Y", (byte) -4);
        section.putString("custom_name", "biomesoplenty:rainbow_hills");
        CompoundTag original = section.copy();
        CompoundTag chunk = new CompoundTag();
        chunk.putInt("DataVersion", 2975);
        chunk.putInt("xPos", 0);
        chunk.putInt("zPos", 0);
        ListTag sections = new ListTag();
        sections.add(section);
        chunk.put("sections", sections);

        assertTrue(NamedBopRecovery.relevant(chunk));
        NamedBopRecovery.rewrite(chunk);

        ListTag current = section.getCompound("biomes").getList("palette", 8);
        assertEquals("biomesoplenty:auroral_garden", current.getString(0));
        assertEquals("biomesoplenty:scrubland", current.getString(1));
        assertEquals("biomesoplenty:wasteland", current.getString(2));
        assertEquals("biomesoplenty:coniferous_forest", current.getString(3));
        assertEquals("biomesoplenty:seasonal_orchard", current.getString(4));
        assertArrayEquals(original.getCompound("biomes").getLongArray("data"),
                section.getCompound("biomes").getLongArray("data"));
        assertEquals(original.getByte("Y"), section.getByte("Y"));
        assertEquals(original.getString("custom_name"), section.getString("custom_name"));

        CompoundTag once = section.copy();
        assertFalse(NamedBopRecovery.relevant(chunk));
        NamedBopRecovery.rewrite(chunk);
        assertEquals(once, section);
    }

    @Test
    void ordinaryStringPalettesAndNamesAreNotBiomeStorage() {
        CompoundTag custom = new CompoundTag();
        ListTag palette = new ListTag();
        palette.add(StringTag.valueOf("biomesoplenty:rainbow_hills"));
        custom.put("palette", palette);
        CompoundTag original = custom.copy();

        assertFalse(NamedBopRecovery.relevant(custom));
        NamedBopRecovery.rewrite(custom);
        assertEquals(original, custom);
    }

    @Test
    void sectionLikeCustomDataIsNotAChunk() {
        CompoundTag custom = new CompoundTag();
        custom.putByte("Y", (byte) 0);
        CompoundTag biomes = new CompoundTag();
        ListTag palette = new ListTag();
        palette.add(StringTag.valueOf("biomesoplenty:rainbow_hills"));
        biomes.put("palette", palette);
        custom.put("biomes", biomes);
        CompoundTag original = custom.copy();

        assertFalse(NamedBopRecovery.relevant(custom));
        NamedBopRecovery.rewrite(custom);
        assertEquals(original, custom);
    }
}
