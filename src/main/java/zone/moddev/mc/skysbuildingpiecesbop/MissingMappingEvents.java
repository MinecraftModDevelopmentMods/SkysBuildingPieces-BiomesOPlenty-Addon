package zone.moddev.mc.skysbuildingpiecesbop;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zone.moddev.mc.skysbuildingpiecesbop.basket.Baskets;

/** Forge posts missing mappings on its gameplay bus, after registration. */
@Mod.EventBusSubscriber(modid = SkysBuildingPiecesBop.ID)
public final class MissingMappingEvents {
    private MissingMappingEvents() {
    }

    @SubscribeEvent
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
