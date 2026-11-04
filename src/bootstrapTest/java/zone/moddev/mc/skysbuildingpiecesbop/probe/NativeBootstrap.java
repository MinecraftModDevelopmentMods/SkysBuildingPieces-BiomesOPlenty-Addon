package zone.moddev.mc.skysbuildingpiecesbop.probe;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.server.ServerStartedEvent;
import zone.moddev.mc.skysbuildingpieces.catalogue.Catalogue;
import zone.moddev.mc.skysbuildingpieces.content.Pieces;
import zone.moddev.mc.skysbuildingpiecesbop.RetiredRocks;

/** A short native startup gate before the complete gameplay and upgrade suites. */
@Mod("buildingpiecesbopchecks")
public final class NativeBootstrap {
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger();

    private net.minecraft.server.MinecraftServer pendingServer;
    private int startupTicks;

    public NativeBootstrap() {
        zone.moddev.mc.skysbuildingpieces.probe.PersistenceRegistrationChecks.register(
                net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
        MinecraftForge.EVENT_BUS.addListener(this::started);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus()
                .addListener(BopPersistenceOrderChecks::ready);
        net.minecraftforge.fml.DistExecutor.runWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
            new zone.moddev.mc.skysbuildingpieces.probe.ClientProbe().init();
            MinecraftForge.EVENT_BUS.register(BasketGuiClientChecks.class);
            MinecraftForge.EVENT_BUS.register(BasketDisconnectClientChecks.class);
            MinecraftForge.EVENT_BUS.register(BasketClientChecks.class);
        });
    }

    private void started(ServerStartedEvent event) {
        try {
            if (NativeNamedForwardChecks.applies(new java.io.File("forward-fixture.json"))) {
                pendingServer = event.getServer();
                NativeNamedForwardChecks.prepare(pendingServer.getLevel(net.minecraft.world.level.Level.OVERWORLD));
                return;
            }
            audit(event.getServer());
        } catch (Exception failure) {
            LOGGER.error("Cannot prepare the native BOP fixture", failure);
            event.getServer().halt(false);
        }
    }

    private void tick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || pendingServer == null || ++startupTicks < 20) {
            return;
        }
        if (!NativeNamedForwardChecks.ready(pendingServer.getLevel(net.minecraft.world.level.Level.OVERWORLD))) {
            if (startupTicks > 1200) {
                LOGGER.error("The BOP fixture never finished loading its chunks and entities");
                pendingServer.halt(false);
                pendingServer = null;
            }
            return;
        }
        net.minecraft.server.MinecraftServer server = pendingServer;
        pendingServer = null;
        audit(server);
    }

    private void audit(net.minecraft.server.MinecraftServer server) {
        boolean client = System.getProperty("skysbuildingpieces.integrationPhase", "").startsWith("client");
        boolean success = false;

        try {
            RegistrySnapshot.write();
            if (Boolean.getBoolean("skysbuildingpieces.bopBiomeFixerOnly")) {
                NamedRecoveryChecks.nativeBiomeDatafix(server);
                return;
            }
            if (Boolean.getBoolean("skysbuildingpieces.bopRegistryOnly")) {
                LOGGER.info("BUILDING_PIECES_BOP_REGISTRY_ONLY_PASS");
                return;
            }
            WallRecoveryChecks.run(server);
            NamedRecoveryChecks.run();
            NativeBopBiomeChecks.run(server);
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
                if (!NativeNamedForwardChecks.applies(manifest)) {
                    NativeSavedBiomeChecks.prepare(server.getLevel(net.minecraft.world.level.Level.OVERWORLD),
                            new java.util.LinkedHashSet<>());
                    NativeSavedBiomeChecks.verify(server.getLevel(net.minecraft.world.level.Level.OVERWORLD));
                }
                if (NativeNamedForwardChecks.applies(manifest)) {
                    NativeNamedForwardChecks.run(server.getLevel(net.minecraft.world.level.Level.OVERWORLD));
                } else if (Boolean.getBoolean("skysbuildingpieces.bopBiomeOnly")) {
                    ForwardUpgradeChecks.biomesOnly(server, manifest);
                } else {
                    ForwardUpgradeChecks.run(server, manifest);
                }
            } else {
                if (Boolean.getBoolean("skysbuildingpieces.bopInstalledPolicy")) {
                    zone.moddev.mc.skysbuildingpieces.probe.BopCompatibilityChecks
                            .run(server.getLevel(net.minecraft.world.level.Level.OVERWORLD));
                }
                BasketChecks.run(server.getLevel(net.minecraft.world.level.Level.OVERWORLD));
                RetiredItemChecks
                        .run(server.getLevel(net.minecraft.world.level.Level.OVERWORLD));
                PlantRecoveryChecks.run();
                DecorationChecks.run(server.getLevel(net.minecraft.world.level.Level.OVERWORLD));
                MaterialChecks.run(server.getLevel(net.minecraft.world.level.Level.OVERWORLD));
                zone.moddev.mc.skysbuildingpieces.probe.BopMigrationChecks
                        .run(server.getLevel(net.minecraft.world.level.Level.OVERWORLD));
                zone.moddev.mc.skysbuildingpieces.probe.RecipeChecks
                        .run(server.getLevel(net.minecraft.world.level.Level.OVERWORLD));
                zone.moddev.mc.skysbuildingpieces.probe.RecipeBookChecks
                        .run(server.getLevel(net.minecraft.world.level.Level.OVERWORLD));
            }

            BopPersistenceOrderChecks.verify(server.getLevel(net.minecraft.world.level.Level.OVERWORLD));
            LOGGER.info("BUILDING_PIECES_BOP_NATIVE_BOOTSTRAP_PASS materials=33 palettes=66 retired_items=7");
            success = true;

            if (client) {
                zone.moddev.mc.skysbuildingpieces.probe.PresentationChecks.connectedGlass(
                        server.getLevel(net.minecraft.world.level.Level.OVERWORLD));
                zone.moddev.mc.skysbuildingpieces.probe.ClientStatus.complete = true;
            }
        } catch (Throwable failure) {
            LOGGER.error("Native BOP verification failed", failure);
            zone.moddev.mc.skysbuildingpieces.probe.ClientStatus.failure = failure;
            if (client) {
                net.minecraftforge.fml.DistExecutor.runWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                        () -> () -> net.minecraft.client.Minecraft.getInstance().delayCrash(
                                net.minecraft.CrashReport.forThrowable(failure, "BOP client verification failed")));
            }
        } finally {
            if (!client || !success) {
                server.halt(false);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
