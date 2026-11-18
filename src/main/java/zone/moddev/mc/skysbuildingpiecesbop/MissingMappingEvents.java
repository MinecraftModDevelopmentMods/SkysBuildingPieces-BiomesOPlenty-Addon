package zone.moddev.mc.skysbuildingpiecesbop;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.registries.ForgeRegistries;
import zone.moddev.mc.skysbuildingpiecesbop.basket.Baskets;

/** Forge posts missing mappings on its gameplay bus, after registration. */
public final class MissingMappingEvents {
    private MissingMappingEvents() {
    }

    public static void missing(MissingMappingsEvent event) {
        if (event.getKey().equals(ForgeRegistries.Keys.BLOCKS)) {
            RetiredMaterialHolders.missingBlocks(event);
        } else if (event.getKey().equals(ForgeRegistries.Keys.ITEMS)) {
            RetiredMaterialHolders.missingItems(event);
            LegacyItemRecovery.missing(event);
            Baskets.missing(event);
        }
    }
}
