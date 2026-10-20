package zone.moddev.mc.skysbuildingpiecesbop.probe;

import java.io.*;
import java.nio.file.*;
import net.minecraft.nbt.*;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import zone.moddev.mc.skysbuildingpieces.legacy.*;

/** A fresh full-world copy, normally loading every carrier of the three retired item types. */
public final class RetiredItemWorldChecks {
    private int ticks;

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) throws Exception {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        net.minecraft.server.MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();

        if (server == null || server.getWorld(0) == null) {
            return;
        }

        WorldServer world = server.getWorld(0);

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

        NBTTagCompound state = PiecesWorldState.get(world).writeToNBT(new NBTTagCompound());
        File snapshot = new File(world.getSaveHandler().getWorldDirectory(), "retired-item-qualification-state.dat");
        boolean reload = System.getProperty("skysbuildingpieces.integrationPhase").endsWith("reload");

        if (reload) {
            NBTTagCompound before;

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
                + state.getInteger("schema_version") + " reload=" + reload);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(this);
        server.initiateShutdown();
    }
}
