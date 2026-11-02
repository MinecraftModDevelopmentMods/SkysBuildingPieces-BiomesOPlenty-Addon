package zone.moddev.mc.skysbuildingpiecesbop.probe;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.server.ServerStartedEvent;

/** Build-only listeners for real basket windows and copied-world audits. */
@Mod("basketsprobe")
public final class BasketProbe {
    public BasketProbe() {
        MinecraftForge.EVENT_BUS.addListener(this::started);
        MinecraftForge.EVENT_BUS.register(BasketPlayerUpgrade.class);
        DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> {
            MinecraftForge.EVENT_BUS.register(BasketClientChecks.class);
            MinecraftForge.EVENT_BUS.register(BasketGuiClientChecks.class);
            MinecraftForge.EVENT_BUS.register(BasketDisconnectClientChecks.class);
        });
    }

    private void started(ServerStartedEvent event) {
        String phase = System.getProperty("skysbuildingpieces.integrationPhase", "");

        if (phase.startsWith("sylvester-baskets-") || phase.startsWith("sylvester-items-")) {
            Object coreProbe = ModList.get().getModContainerById("buildingpiecesprobe").get().getMod();

            MinecraftForge.EVENT_BUS.unregister(coreProbe);
            MinecraftForge.EVENT_BUS.register(
                    phase.startsWith("sylvester-items-") ? new RetiredItemWorldChecks() : new BasketWorldChecks());
        }
    }
}
