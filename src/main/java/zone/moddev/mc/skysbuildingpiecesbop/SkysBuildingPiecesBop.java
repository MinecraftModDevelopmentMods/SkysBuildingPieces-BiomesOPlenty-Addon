package zone.moddev.mc.skysbuildingpiecesbop;

import java.io.IOException;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import zone.moddev.mc.skysbuildingpieces.api.BuildingPiecesApi;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.legacy.HistoricalFullBlocks;
import zone.moddev.mc.skysbuildingpiecesbop.basket.Baskets;
import zone.moddev.mc.skysbuildingpiecesbop.basket.BasketPreflight;

/** The add-on owns materials and restored content; the core owns piece placement. */
@Mod(SkysBuildingPiecesBop.ID)
public final class SkysBuildingPiecesBop {
    public static final String ID = "skysbuildingpiecesbop";
    public static final String VERSION = "0.2.7.121012";

    public SkysBuildingPiecesBop(IEventBus modBus) {
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
        NeoForge.EVENT_BUS.addListener(this::beforeServer);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, Baskets::pickup);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            zone.moddev.mc.skysbuildingpiecesbop.client.RetiredRockModels.register(modBus);
        }
    }

    private void beforeServer(ServerAboutToStartEvent event) {
        try {
            NamedBopRecovery.begin(event.getServer());
            BasketPreflight.check(event.getServer());
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot complete the saved-item safety check: " + failure.getMessage(), failure);
        }
    }

    @EventBusSubscriber(
            modid = ID,
            bus = EventBusSubscriber.Bus.MOD)
    public static final class Registries {
        private Registries() {
        }

        @SubscribeEvent
        public static void register(RegisterEvent event) {
            if (event.getRegistryKey().equals(net.minecraft.core.registries.Registries.BLOCK)) {
                BuildingPiecesApi.registerBlocks(ID, BuiltInRegistries.BLOCK);
            } else if (event.getRegistryKey().equals(net.minecraft.core.registries.Registries.ITEM)) {
                BuildingPiecesApi.registerItems(ID, BuiltInRegistries.ITEM);
                Baskets.items(event);
            } else if (event.getRegistryKey().equals(net.minecraft.core.registries.Registries.MENU)) {
                Baskets.containers(event);
            } else if (event.getRegistryKey().equals(net.minecraft.core.registries.Registries.RECIPE_SERIALIZER)) {
                event.register(net.minecraft.core.registries.Registries.RECIPE_SERIALIZER,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ID, "material_recipe"),
                        ChoiceRecipeSerializer::new);
            }
        }

        @SubscribeEvent
        public static void creativeContents(BuildCreativeModeTabContentsEvent event) {
            Baskets.creativeContents(event);
        }
    }
}
