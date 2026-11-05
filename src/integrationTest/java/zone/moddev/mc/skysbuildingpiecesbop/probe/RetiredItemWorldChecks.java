package zone.moddev.mc.skysbuildingpiecesbop.probe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.*;
import java.nio.file.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import zone.moddev.mc.skysbuildingpieces.legacy.*;

/** A fresh full-world copy, normally loading every carrier of the three retired item types. */
public final class RetiredItemWorldChecks {
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger();

    private int ticks;

    @SubscribeEvent
    public void tick(ServerTickEvent.Post event) throws Exception {

        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

        if (server == null || server.getLevel(net.minecraft.world.level.Level.OVERWORLD) == null) {
            return;
        }

        ServerLevel world = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);

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

        CompoundTag state = PiecesWorldState.get(world).save(new CompoundTag(), world.registryAccess());
        File snapshot = new File(world.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(), "retired-item-qualification-state.dat");
        boolean reload = System.getProperty("skysbuildingpieces.integrationPhase").endsWith("reload");

        if (reload) {
            CompoundTag before;

            try (InputStream input = new FileInputStream(snapshot)) {
                before = NbtIo.readCompressed(input, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            }
            if (!state.equals(before)) {
                throw new AssertionError("Retired-item reload changed piece migration state");
            }
        } else {
            try (OutputStream output = new FileOutputStream(snapshot)) {
                NbtIo.writeCompressed(state, output);
            }
        }

        LOGGER.info("BOP_RETIRED_ITEM_SYLVESTER_PASS all_carriers_loaded=true schema="
                + state.getInt("schema_version") + " reload=" + reload);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(this);
        server.halt(false);
    }
}
