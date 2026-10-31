package zone.moddev.mc.skysbuildingpiecesbop.client;

import net.minecraft.client.gui.ScreenManager;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import zone.moddev.mc.skysbuildingpiecesbop.basket.Baskets;

/** Native item JSON supplies the models; only the basket screen needs a factory. */
public final class RetiredRockModels {
    private RetiredRockModels() {
    }

    public static void register() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(RetiredRockModels::setup);
    }

    private static void setup(FMLClientSetupEvent event) {
        ScreenManager.registerFactory(Baskets.TYPE, BasketScreen::new);
    }
}
