package zone.moddev.mc.skysbuildingpiecesbop;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zone.moddev.mc.skysbuildingpiecesbop.basket.Baskets;

/** Forge posts missing mappings on its gameplay bus, after registration. */
@Mod.EventBusSubscriber(modid = SkysBuildingPiecesBop.ID)
public final class MissingMappingEvents {
    private MissingMappingEvents() {
    }

    @SubscribeEvent
    public static void blocks(RegistryEvent.MissingMappings<Block> event) {
        RetiredMaterialHolders.missingBlocks(event);
    }

    @SubscribeEvent
    public static void items(RegistryEvent.MissingMappings<Item> event) {
        RetiredMaterialHolders.missingItems(event);
        LegacyItemRecovery.missing(event);
        Baskets.missing(event);
    }
}
