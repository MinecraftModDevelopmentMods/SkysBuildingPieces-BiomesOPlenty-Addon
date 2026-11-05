package zone.moddev.mc.skysbuildingpiecesbop.probe;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import zone.moddev.mc.skysbuildingpieces.probe.BopServerProbe;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.server.ServerStartedEvent;

/** Build-only listeners for real basket windows and copied-world audits. */
@Mod("basketsprobe")
public final class BasketProbe {
    public BasketProbe() {
        ServerStartedEvent.BUS.addListener(net.minecraftforge.eventbus.api.listener.Priority.LOWEST,
                this::started);
        net.minecraftforge.event.TickEvent.ServerTickEvent.Pre.BUS.addListener(
                net.minecraftforge.eventbus.api.listener.Priority.HIGHEST, BasketPlayerUpgrade::tick);
        DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> {
            BasketClientChecks.register();
            BasketGuiClientChecks.register();
            BasketDisconnectClientChecks.register();
        });
    }

    private void started(ServerStartedEvent event) {
        String phase = System.getProperty("skysbuildingpieces.integrationPhase", "");
        if (phase.equals("fresh") || phase.equals("reload")) {
            BopServerProbe.register(event.getServer(), phase);
        } else if (phase.startsWith("sylvester-baskets-")) {
            BasketWorldChecks.register();
        } else if (phase.startsWith("sylvester-items-")) {
            RetiredItemWorldChecks.register();
        }
    }
}
