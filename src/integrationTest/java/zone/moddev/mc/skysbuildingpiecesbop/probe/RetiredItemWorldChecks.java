package zone.moddev.mc.skysbuildingpiecesbop.probe;

import java.io.*;
import java.nio.file.*;
import net.minecraft.nbt.*;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import zone.moddev.mc.skysbuildingpieces.legacy.*;

/** A fresh full-world copy, normally loading every carrier of the three retired item types. */
public final class RetiredItemWorldChecks {
    private int ticks;

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) throws Exception {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

        if (server == null || server.getLevel(net.minecraft.world.World.OVERWORLD) == null) {
            return;
        }

        ServerWorld world = server.getLevel(net.minecraft.world.World.OVERWORLD);

        if (ticks++ == 0) {
            RetiredItemChecks.run(world);

            for (String row : Files.readAllLines(Paths.get(System.getProperty("skysbuildingpieces.retiredItemIndex")),
                    java.nio.charset.StandardCharsets.UTF_8)) {
                String[] fields = row.split(",");

                world.getChunk(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]));
            }
        }
        if (ticks != 100) {
            return;
        }

        LegacyBridge.report(world);

        CompoundNBT state = PiecesWorldState.get(world).save(new CompoundNBT());
        File snapshot = new File(world.getServer().getWorldPath(net.minecraft.world.storage.FolderName.ROOT).toFile(), "retired-item-qualification-state.dat");
        boolean reload = System.getProperty("skysbuildingpieces.integrationPhase").endsWith("reload");

        if (reload) {
            CompoundNBT before;

            try (InputStream input = new FileInputStream(snapshot)) {
                before = CompressedStreamTools.readCompressed(input);
            }
            if (!state.equals(before)) {
                throw new AssertionError("Retired-item reload changed piece migration state");
            }
        } else {
            try (OutputStream output = new FileOutputStream(snapshot)) {
                CompressedStreamTools.writeCompressed(state, output);
            }
        }

        System.out.println("BOP_RETIRED_ITEM_SYLVESTER_PASS all_carriers_loaded=true schema="
                + state.getInt("schema_version") + " reload=" + reload);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(this);
        server.halt(false);
    }
}
