package zone.moddev.mc.skysbuildingpiecesbop.probe;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.bus.api.EventPriority;
import zone.moddev.mc.skysbuildingpieces.probe.BopServerProbe;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Build-only listeners for real basket windows and copied-world audits. */
@Mod("basketsprobe")
public final class BasketProbe {
    public BasketProbe() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::started);
        NeoForge.EVENT_BUS.register(BasketPlayerUpgrade.class);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.register(BasketClientChecks.class);
            NeoForge.EVENT_BUS.register(BasketGuiClientChecks.class);
            NeoForge.EVENT_BUS.register(BasketDisconnectClientChecks.class);
        }
    }

    private void started(ServerStartedEvent event) {
        String phase = System.getProperty("skysbuildingpieces.integrationPhase", "");

        if (phase.equals("fresh") || phase.equals("reload")) {
            // The core has prepared its fixtures. Use a tick-based driver for the larger BOP recipe catalogue.
            Object coreProbe = ModList.get().getModContainerById("buildingpiecesprobe").orElseThrow().getMod();
            NeoForge.EVENT_BUS.unregister(coreProbe);
            BopServerProbe.register(event.getServer(), phase);
        } else if (phase.startsWith("sylvester-baskets-") || phase.startsWith("sylvester-items-")) {
            Object coreProbe = ModList.get().getModContainerById("buildingpiecesprobe").get().getMod();

            NeoForge.EVENT_BUS.unregister(coreProbe);
            NeoForge.EVENT_BUS.register(
                    phase.startsWith("sylvester-items-") ? new RetiredItemWorldChecks() : new BasketWorldChecks());
        }
    }
}
