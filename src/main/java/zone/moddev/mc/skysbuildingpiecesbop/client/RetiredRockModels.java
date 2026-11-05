package zone.moddev.mc.skysbuildingpiecesbop.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import zone.moddev.mc.skysbuildingpiecesbop.basket.Baskets;

/** Registers the basket screen on the client. */
public final class RetiredRockModels {
    private RetiredRockModels() {
    }

    public static void register(BusGroup modBus) {
        FMLClientSetupEvent.getBus(modBus).addListener(RetiredRockModels::setup);
    }

    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(Baskets.TYPE, BasketScreen::new));
    }
}
