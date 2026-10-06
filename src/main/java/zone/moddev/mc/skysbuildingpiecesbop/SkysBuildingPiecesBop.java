package zone.moddev.mc.skysbuildingpiecesbop;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import zone.moddev.mc.skysbuildingpieces.api.BuildingPiecesApi;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;

/** BOP material definitions; placement, crafting and recovery belong to the core. */
@Mod(modid=SkysBuildingPiecesBop.ID,name="Sky's Building Pieces - Biomes O Plenty",version=SkysBuildingPiecesBop.VERSION,
    acceptedMinecraftVersions="[1.10.2]",dependencies="required-after:skysbuildingpieces@[0.3.0.110021,0.4);required-after:BiomesOPlenty@[5.0.0,6.0);before:buildingbricks")
public final class SkysBuildingPiecesBop {
    public static final String ID="skysbuildingpiecesbop", VERSION="0.1.0.110021";
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        if(BuildingPiecesApi.VERSION!=1)throw new IllegalStateException("Unsupported Building Pieces catalogue API");
        Catalogue.Module module=BuildingPiecesApi.registerCatalogue("biomesoplenty",ID,80,SkysBuildingPiecesBop.class);
        if(module.materials.size()!=33||module.palettes.size()!=66)throw new IllegalStateException("BOP catalogue contract changed");
    }
}
