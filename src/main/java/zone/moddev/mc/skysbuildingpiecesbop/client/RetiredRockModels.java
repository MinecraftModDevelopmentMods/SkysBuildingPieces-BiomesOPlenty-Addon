package zone.moddev.mc.skysbuildingpiecesbop.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import zone.moddev.mc.skysbuildingpiecesbop.basket.Baskets;

/** Registers the basket screen on the client. */
public final class RetiredRockModels {
    private RetiredRockModels() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RetiredRockModels::screens);
    }

    private static void screens(RegisterMenuScreensEvent event) {
        event.register(Baskets.TYPE, BasketScreen::new);
    }
}
