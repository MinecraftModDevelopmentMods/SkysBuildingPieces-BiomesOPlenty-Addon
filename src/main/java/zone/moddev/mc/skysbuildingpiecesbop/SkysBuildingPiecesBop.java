package zone.moddev.mc.skysbuildingpiecesbop;

import java.io.IOException;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fmlserverevents.FMLServerAboutToStartEvent;
import zone.moddev.mc.skysbuildingpieces.api.BuildingPiecesApi;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.legacy.HistoricalFullBlocks;
import zone.moddev.mc.skysbuildingpiecesbop.basket.Baskets;
import zone.moddev.mc.skysbuildingpiecesbop.basket.BasketPreflight;

/** The add-on owns materials and restored content; the core owns piece placement. */
@Mod(SkysBuildingPiecesBop.ID)
public final class SkysBuildingPiecesBop {
    public static final String ID = "skysbuildingpiecesbop";
    public static final String VERSION = "0.2.4.117011";

    public SkysBuildingPiecesBop() {
        if (BuildingPiecesApi.VERSION != 1) {
            throw new IllegalStateException("Unsupported Building Pieces catalogue API");
        }

        Catalogue.Module module = BuildingPiecesApi.registerCatalogue("biomesoplenty", ID, 80,
                SkysBuildingPiecesBop.class);

        if (module.materials.size() != 33 || module.palettes.size() != 66) {
            throw new IllegalStateException("BOP catalogue contract changed");
        }

        HistoricalFullBlocks.register("biomesoplenty:stone", ID + ":legacy_bop_stone", "biomesoplenty:limestone",
                "biomesoplenty:siltstone", "biomesoplenty:shale", null, null, null, null, null,
                "biomesoplenty:limestone_polished", "biomesoplenty:siltstone_polished", "biomesoplenty:shale_polished");
        HistoricalFullBlocks.register("biomesoplenty:crag_rock", ID + ":legacy_bop_crag_rock",
                "biomesoplenty:crag_rock");
        RetiredDecorations.registerRecovery();
        BopFlattening.register();
        BopWorldMappings.registerBiomes();
        Baskets.registerRecovery();
        MinecraftForge.EVENT_BUS.addListener(this::beforeServer);
        MinecraftForge.EVENT_BUS.addListener(Baskets::pickup);
        DistExecutor.runWhenOn(Dist.CLIENT,
                () -> zone.moddev.mc.skysbuildingpiecesbop.client.RetiredRockModels::register);
    }

    private void beforeServer(FMLServerAboutToStartEvent event) {
        try {
            NamedBopRecovery.begin(event.getServer());
            BasketPreflight.check(event.getServer());
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot validate saved basket contents; restore the world backup", failure);
        }
    }

    @Mod.EventBusSubscriber(
            modid = ID,
            bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registries {
        private Registries() {
        }

        @SubscribeEvent
        public static void blocks(RegistryEvent.Register<Block> event) {
            BuildingPiecesApi.registerBlocks(ID, event.getRegistry());
        }

        @SubscribeEvent
        public static void items(RegistryEvent.Register<Item> event) {
            BuildingPiecesApi.registerItems(ID, event.getRegistry());
            Baskets.items(event);
        }

        @SubscribeEvent
        public static void containers(RegistryEvent.Register<MenuType<?>> event) {
            event.getRegistry().register(Baskets.TYPE);
        }

    }
}
