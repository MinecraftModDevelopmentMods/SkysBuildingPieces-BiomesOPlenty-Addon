package zone.moddev.mc.skysbuildingpiecesbop.probe;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import zone.moddev.mc.skysbuildingpiecesbop.RetiredRocks;

/** A short native startup gate before the complete gameplay and upgrade suites. */
@Mod("buildingpiecesbopchecks")
public final class NativeBootstrap {
    public NativeBootstrap() {
        MinecraftForge.EVENT_BUS.addListener(this::started);
        net.minecraftforge.fml.DistExecutor.runWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
            new zone.moddev.mc.skysbuildingpieces.probe.ClientProbe().init();
            MinecraftForge.EVENT_BUS.register(BasketGuiClientChecks.class);
            MinecraftForge.EVENT_BUS.register(BasketDisconnectClientChecks.class);
            MinecraftForge.EVENT_BUS.register(BasketClientChecks.class);
        });
    }

    private void started(FMLServerStartedEvent event) {
        boolean client = System.getProperty("skysbuildingpieces.integrationPhase", "").startsWith("client");
        boolean success = false;

        try {
            RegistrySnapshot.write();
            NamedRecoveryChecks.run();
            require(ModList.get().isLoaded("skysbuildingpieces"), "The core is missing");
            require(ModList.get().isLoaded("skysbuildingpiecesbop"), "The add-on is missing");
            require(ModList.get().isLoaded("biomesoplenty"), "BOP is missing");

            Catalogue.Module module = Catalogue.INSTANCE.modules.get("biomesoplenty");

            require(module != null && module.materials.size() == 33 && module.palettes.size() == 66,
                    "Fixed BOP catalogue changed");

            for (Catalogue.MaterialDef material : module.materials.values()) {
                require(Pieces.nativeState(material, "full") != null, "No full material for " + material.id);
            }
            for (int variant = 0; variant < RetiredRocks.NAMES.length; variant++) {
                ItemStack result = Pieces.stack(RetiredRocks.BLOCK.getStateFromMeta(variant), 3);

                require(result != null && result.getItem() == RetiredRocks.ITEMS[variant] && result.getCount() == 3,
                        "Retired full-block inventory identity changed: " + variant);
            }

            java.io.File manifest = new java.io.File("forward-fixture.json");

            if (manifest.isFile()) {
                if (Boolean.getBoolean("skysbuildingpieces.bopBiomeOnly")) {
                    ForwardUpgradeChecks.biomesOnly(event.getServer(), manifest);
                } else {
                    ForwardUpgradeChecks.run(event.getServer(), manifest);
                }
            } else {
                BasketChecks.run(event.getServer().getWorld(net.minecraft.world.dimension.DimensionType.OVERWORLD));
                RetiredItemChecks
                        .run(event.getServer().getWorld(net.minecraft.world.dimension.DimensionType.OVERWORLD));
                PlantRecoveryChecks.run();
                DecorationChecks.run(event.getServer().getWorld(net.minecraft.world.dimension.DimensionType.OVERWORLD));
                MaterialChecks.run(event.getServer().getWorld(net.minecraft.world.dimension.DimensionType.OVERWORLD));
                zone.moddev.mc.skysbuildingpieces.probe.BopMigrationChecks
                        .run(event.getServer().getWorld(net.minecraft.world.dimension.DimensionType.OVERWORLD));
                zone.moddev.mc.skysbuildingpieces.probe.RecipeChecks
                        .run(event.getServer().getWorld(net.minecraft.world.dimension.DimensionType.OVERWORLD));
                zone.moddev.mc.skysbuildingpieces.probe.RecipeBookChecks
                        .run(event.getServer().getWorld(net.minecraft.world.dimension.DimensionType.OVERWORLD));
            }

            System.out.println("BUILDING_PIECES_BOP_NATIVE_BOOTSTRAP_PASS materials=33 palettes=66 retired_items=7");
            success = true;

            if (client) {
                zone.moddev.mc.skysbuildingpieces.probe.PresentationChecks.connectedGlass(
                        event.getServer().getWorld(net.minecraft.world.dimension.DimensionType.OVERWORLD));
                zone.moddev.mc.skysbuildingpieces.probe.ClientStatus.complete = true;
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            zone.moddev.mc.skysbuildingpieces.probe.ClientStatus.failure = failure;
        } finally {
            if (!client || !success) {
                event.getServer().initiateShutdown(false);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
