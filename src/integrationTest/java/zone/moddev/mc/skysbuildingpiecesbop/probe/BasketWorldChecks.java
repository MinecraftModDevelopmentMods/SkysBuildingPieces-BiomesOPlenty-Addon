package zone.moddev.mc.skysbuildingpiecesbop.probe;

import java.io.*;
import java.nio.file.*;
import net.minecraft.nbt.*;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import zone.moddev.mc.skysbuildingpieces.legacy.*;

/** Loads every original basket carrier in a fresh full-world copy, without visiting unrelated chunks. */
public final class BasketWorldChecks {
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

        NBTTagCompound state = PiecesWorldState.get(world).writeToNBT(new NBTTagCompound());
        File snapshot = new File(world.getSaveHandler().getWorldDirectory(), "basket-qualification-state.dat");
        boolean reload = System.getProperty("skysbuildingpieces.integrationPhase").endsWith("reload");

        if (reload) {
            NBTTagCompound before;

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

        System.out.println("BOP_BASKET_SYLVESTER_PASS all_carriers_loaded=true schema="
                + state.getInteger("schema_version") + " reload=" + reload);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(this);
        server.initiateShutdown();
    }
}
