package zone.moddev.mc.skysbuildingpiecesbop;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import zone.moddev.mc.skysbuildingpieces.api.BuildingPiecesApi;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;

/** BOP material definitions; placement, crafting and recovery belong to the core. */
@Mod(
        modid = SkysBuildingPiecesBop.ID,
        name = "Sky's Building Pieces - Biomes O Plenty",
        version = SkysBuildingPiecesBop.VERSION,
        acceptedMinecraftVersions = "[1.12.2]",
        dependencies = "required-after:skysbuildingpieces@[0.3.8.112021,0.4);required-after:biomesoplenty@[7.0.1,8.0);before:buildingbricks")
public final class SkysBuildingPiecesBop {
    public static final String ID = "skysbuildingpiecesbop", VERSION = "0.2.4.112021";
    @Mod.Instance(ID)
    public static SkysBuildingPiecesBop INSTANCE;

    @Mod.EventHandler
    public void beforeServer(net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent event)
            throws java.io.IOException {
        zone.moddev.mc.skysbuildingpiecesbop.basket.BasketPreflight.check(event.getServer());
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if (BuildingPiecesApi.VERSION != 1) {
            throw new IllegalStateException("Unsupported Building Pieces catalogue API");
        }

        Catalogue.Module module = BuildingPiecesApi.registerCatalogue("biomesoplenty", ID, 80,
                SkysBuildingPiecesBop.class);

        if (module.materials.size() != 33 || module.palettes.size() != 66) {
            throw new IllegalStateException("BOP catalogue contract changed");
        }

        zone.moddev.mc.skysbuildingpieces.legacy.HistoricalFullBlocks.register("biomesoplenty:stone",
                ID + ":legacy_bop_stone", "biomesoplenty:limestone", "biomesoplenty:siltstone", "biomesoplenty:shale",
                null, null, null, null, null, "biomesoplenty:limestone_polished", "biomesoplenty:siltstone_polished",
                "biomesoplenty:shale_polished");
        zone.moddev.mc.skysbuildingpieces.legacy.HistoricalFullBlocks.register("biomesoplenty:crag_rock",
                ID + ":legacy_bop_crag_rock", "biomesoplenty:crag_rock");
        RetiredDecorations.registerRecovery();
        zone.moddev.mc.skysbuildingpiecesbop.basket.Baskets.registerRecovery();
        net.minecraftforge.fml.common.network.NetworkRegistry.INSTANCE.registerGuiHandler(this,
                new zone.moddev.mc.skysbuildingpiecesbop.basket.BasketGuiHandler());
    }
}
