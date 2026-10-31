package zone.moddev.mc.skysbuildingpiecesbop.probe;

import java.io.*;
import java.nio.file.*;
import net.minecraft.nbt.*;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import zone.moddev.mc.skysbuildingpieces.legacy.*;

/** Loads every original basket carrier in a fresh full-world copy, without visiting unrelated chunks. */
public final class BasketWorldChecks {
    private int ticks;

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) throws Exception {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

        if (server == null || server.getWorld(net.minecraft.world.dimension.DimensionType.OVERWORLD) == null) {
            return;
        }

        ServerWorld world = server.getWorld(net.minecraft.world.dimension.DimensionType.OVERWORLD);

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

        CompoundNBT state = PiecesWorldState.get(world).write(new CompoundNBT());
        File snapshot = new File(world.getSaveHandler().getWorldDirectory(), "basket-qualification-state.dat");
        boolean reload = System.getProperty("skysbuildingpieces.integrationPhase").endsWith("reload");

        if (reload) {
            CompoundNBT before;

            try (InputStream input = new FileInputStream(snapshot)) {
                before = CompressedStreamTools.readCompressed(input);
            }
            if (!state.equals(before)) {
                throw new AssertionError(
                        "Basket carrier reload changed piece migration state: before=" + before + " after=" + state);
            }
        } else {
            try (OutputStream output = new FileOutputStream(snapshot)) {
                CompressedStreamTools.writeCompressed(state, output);
            }
        }

        System.out.println("BOP_BASKET_SYLVESTER_PASS all_carriers_loaded=true schema=" + state.getInt("schema_version")
                + " reload=" + reload);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(this);
        server.initiateShutdown(false);
    }
}
