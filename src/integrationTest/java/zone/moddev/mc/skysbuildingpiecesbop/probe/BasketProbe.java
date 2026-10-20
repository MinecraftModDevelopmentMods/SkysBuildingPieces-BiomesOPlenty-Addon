package zone.moddev.mc.skysbuildingpiecesbop.probe;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;

/** Register after both jars are available to the packaged LaunchWrapper. */
@Mod(
        modid = "basketsprobe",
        name = "Flower Basket Runtime Probe",
        version = "1",
        dependencies = "required-after:skysbuildingpiecesbop;after:buildingpiecesprobe")
public final class BasketProbe {
    @Mod.EventHandler
    public void ready(FMLPostInitializationEvent event) throws Exception {
        String phase = System.getProperty("skysbuildingpieces.integrationPhase", "");

        if (phase.startsWith("sylvester-baskets-") || phase.startsWith("sylvester-items-")) {
            MinecraftForge.EVENT_BUS
                    .unregister(Loader.instance().getIndexedModList().get("buildingpiecesprobe").getMod());
            MinecraftForge.EVENT_BUS.register(
                    phase.startsWith("sylvester-items-") ? new RetiredItemWorldChecks() : new BasketWorldChecks());
        }

        MinecraftForge.EVENT_BUS.register(BasketPlayerUpgrade.class);

        if (FMLCommonHandler.instance().getSide().isClient()) {
            MinecraftForge.EVENT_BUS
                    .register(Class.forName("zone.moddev.mc.skysbuildingpiecesbop.probe.BasketClientChecks"));
            MinecraftForge.EVENT_BUS
                    .register(Class.forName("zone.moddev.mc.skysbuildingpiecesbop.probe.BasketGuiClientChecks"));
            MinecraftForge.EVENT_BUS
                    .register(Class.forName("zone.moddev.mc.skysbuildingpiecesbop.probe.BasketDisconnectClientChecks"));
        }
    }
}
