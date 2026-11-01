package zone.moddev.mc.skysbuildingpiecesbop.probe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.*;
import java.nio.file.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fmllegacy.server.ServerLifecycleHooks;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import zone.moddev.mc.skysbuildingpieces.legacy.*;

/** Loads every original basket carrier in a fresh full-world copy, without visiting unrelated chunks. */
public final class BasketWorldChecks {
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger();

    private int ticks;

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) throws Exception {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

        if (server == null || server.getLevel(net.minecraft.world.level.Level.OVERWORLD) == null) {
            return;
        }

        ServerLevel world = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);

        if (ticks++ == 0) {
            for (String row : Files.readAllLines(Paths.get(System.getProperty("skysbuildingpieces.basketIndex")),
                    java.nio.charset.StandardCharsets.UTF_8)) {
                String[] fields = row.split(",");

                world.getChunk(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]));
            }
        }
        if (ticks != 100) {
            return;
        }

        LegacyBridge.report(world);

        CompoundTag state = PiecesWorldState.get(world).save(new CompoundTag());
        File snapshot = new File(world.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(), "basket-qualification-state.dat");
        boolean reload = System.getProperty("skysbuildingpieces.integrationPhase").endsWith("reload");

        if (reload) {
            CompoundTag before;

            try (InputStream input = new FileInputStream(snapshot)) {
                before = NbtIo.readCompressed(input);
            }
            if (!state.equals(before)) {
                throw new AssertionError(
                        "Basket carrier reload changed piece migration state: before=" + before + " after=" + state);
            }
        } else {
            try (OutputStream output = new FileOutputStream(snapshot)) {
                NbtIo.writeCompressed(state, output);
            }
        }

        LOGGER.info("BOP_BASKET_SYLVESTER_PASS all_carriers_loaded=true schema=" + state.getInt("schema_version")
                + " reload=" + reload);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(this);
        server.halt(false);
    }
}
