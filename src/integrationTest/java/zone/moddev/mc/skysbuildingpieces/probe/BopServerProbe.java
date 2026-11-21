package zone.moddev.mc.skysbuildingpieces.probe;

import java.nio.file.Files;
import java.util.Properties;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import zone.moddev.mc.skysbuildingpiecesbop.probe.BopChecks;

/** Runs the shared gameplay checks and the larger BOP catalogue across normal server ticks. */
public final class BopServerProbe {
    private final MinecraftServer server;
    private final ServerLevel world;
    private final String phase;
    private RecipeChecks.Audit recipes;
    private RecombinationChecks.Audit combinations;
    private int startupTicks;
    private long nextBatch;

    private BopServerProbe(MinecraftServer server, String phase) {
        this.server = server;
        this.world = server.overworld();
        this.phase = phase;
    }

    public static void register(MinecraftServer server, String phase) {
        NeoForge.EVENT_BUS.register(new BopServerProbe(server, phase));
    }

    @SubscribeEvent
    public void tick(ServerTickEvent.Post event) {
        if (System.nanoTime() < nextBatch) {
            return;
        }
        try {
            if (recipes == null) {
                if (++startupTicks < 20 || !GameplayChecks.ready(world)) {
                    if (startupTicks >= 1200) {
                        throw new AssertionError("The BOP gameplay fixture never finished loading");
                    }
                    return;
                }
                world.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, server);
                world.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, server);
                recipes = new RecipeChecks.Audit(world);
            }
            if (combinations == null) {
                if (recipes.advance(24)) {
                    recipes.finish();
                    NativeLegacyChecks.run(world);
                    GameplayChecks.run(world);
                    WaterloggingChecks.run(world);
                    PathChecks.run(world);
                    SharedGrassChecks.run(world);
                    MineralogyChecks.run();
                    combinations = new RecombinationChecks.Audit(world);
                }
            } else if (combinations.advance(4)) {
                combinations.finish();
                RecipeBookChecks.run(world);
                PresentationChecks.run(world);
                BopChecks.run(world);
                finish();
                stop();
                return;
            }
            nextBatch = System.nanoTime() + 75_000_000L;
        } catch (Throwable failure) {
            LogManager.getLogger().error("BOP runtime check failed", failure);
            stop();
        }
    }

    private void finish() throws java.io.IOException {
        var marker = server.getWorldPath(LevelResource.ROOT).resolve("skysbuildingpieces-integration.properties");
        Properties result = new Properties();
        if (Files.isRegularFile(marker)) {
            try (var input = Files.newInputStream(marker)) {
                result.load(input);
            }
        }
        if (phase.equals("reload") && !"true".equals(result.getProperty("fresh_complete"))) {
            throw new AssertionError("Fresh gameplay evidence is missing");
        }
        result.setProperty(phase + "_complete", "true");
        result.setProperty("gameplay_checks", "placement, recipes, drops, paths, grass and walls");
        result.setProperty("migration_checks", "source registry, all 16 step codes, raw inventories and native data fixing");
        try (var output = Files.newOutputStream(marker)) {
            result.store(output, "Disposable runtime checks");
        }
        LogManager.getLogger().info("BUILDING_PIECES_PROBE_PASS {}", phase);
    }

    private void stop() {
        GameplayChecks.release(world);
        NeoForge.EVENT_BUS.unregister(this);
        server.halt(false);
    }
}
