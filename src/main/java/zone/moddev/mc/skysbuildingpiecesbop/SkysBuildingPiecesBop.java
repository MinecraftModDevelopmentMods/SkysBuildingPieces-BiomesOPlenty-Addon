package zone.moddev.mc.skysbuildingpiecesbop;

import java.io.IOException;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.listener.Priority;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import zone.moddev.mc.skysbuildingpieces.api.BuildingPiecesApi;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.legacy.HistoricalFullBlocks;
import zone.moddev.mc.skysbuildingpiecesbop.basket.Baskets;
import zone.moddev.mc.skysbuildingpiecesbop.basket.BasketPreflight;

/** The add-on owns materials and restored content; the core owns piece placement. */
@Mod(SkysBuildingPiecesBop.ID)
public final class SkysBuildingPiecesBop {
    public static final String ID = "skysbuildingpiecesbop";
    public static final String VERSION = "0.2.4.121111";

    @SuppressWarnings("removal") // The screen must stay off the dedicated server classpath.
    public SkysBuildingPiecesBop(FMLJavaModLoadingContext context) {
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
        BusGroup modBus = context.getModBusGroup();
        var registration = RegisterEvent.getBus(modBus);
        registration.addListener(Priority.HIGHEST, RetiredMaterialHolders::blocks);
        registration.addListener(Priority.HIGHEST, RetiredRocks::blocks);
        registration.addListener(RetiredMaterialHolders::items);
        registration.addListener(RetiredRocks::items);
        registration.addListener(RetiredDecorations::blocks);
        registration.addListener(RetiredDecorations::items);
        registration.addListener(LegacyItemRecovery::register);
        registration.addListener(SkysBuildingPiecesBop::registerCatalogue);

        var creative = BuildCreativeModeTabContentsEvent.BUS;
        creative.addListener(Baskets::creativeContents);
        creative.addListener(RetiredRocks::creativeContents);
        creative.addListener(RetiredDecorations::creativeContents);

        MissingMappingsEvent.BUS.addListener(MissingMappingEvents::missing);
        MissingMappingsEvent.BUS.addListener(NamedBopRecovery::missingBlocks);
        MissingMappingsEvent.BUS.addListener(NamedBopRecovery::missingItems);
        MissingMappingsEvent.BUS.addListener(NamedBopRecovery::missingBiomes);
        MissingMappingsEvent.BUS.addListener(NamedBopRecovery::missingEntities);
        ServerAboutToStartEvent.BUS.addListener(this::beforeServer);
        EntityItemPickupEvent.BUS.addListener(Priority.LOWEST,
                (java.util.function.Predicate<EntityItemPickupEvent>) Baskets::pickup);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            zone.moddev.mc.skysbuildingpiecesbop.client.RetiredRockModels.register(modBus);
        }
    }

    private void beforeServer(ServerAboutToStartEvent event) {
        try {
            NamedBopRecovery.begin(event.getServer());
            BasketPreflight.check(event.getServer());
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot validate saved basket contents; restore the world backup", failure);
        }
    }

    private static void registerCatalogue(RegisterEvent event) {
        if (event.getRegistryKey().equals(ForgeRegistries.Keys.BLOCKS)) {
            BuildingPiecesApi.registerBlocks(ID, ForgeRegistries.BLOCKS);
        } else if (event.getRegistryKey().equals(ForgeRegistries.Keys.ITEMS)) {
            BuildingPiecesApi.registerItems(ID, ForgeRegistries.ITEMS);
            Baskets.items(event);
        } else if (event.getRegistryKey().equals(ForgeRegistries.Keys.MENU_TYPES)) {
            Baskets.containers(event);
        }
    }

    static BlockBehaviour.Properties blockProperties(String name) {
        return BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK,
                Identifier.fromNamespaceAndPath(ID, name)));
    }

    static Item.Properties itemProperties(Identifier id) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id));
    }
}
