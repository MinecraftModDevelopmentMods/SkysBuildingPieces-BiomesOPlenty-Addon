package zone.moddev.mc.skysbuildingpiecesbop.client;

import net.minecraft.client.gui.ScreenManager;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import zone.moddev.mc.skysbuildingpiecesbop.basket.Baskets;
import zone.moddev.mc.skysbuildingpiecesbop.RetiredDecorations;

/** Registers the basket screen and the restored decorations' native render layers. */
public final class RetiredRockModels {
    private RetiredRockModels() {
    }

    public static void register() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(RetiredRockModels::setup);
    }

    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (net.minecraft.item.Item basket : new net.minecraft.item.Item[] {Baskets.ITEM, Baskets.HOLDER}) {
                net.minecraft.item.ItemModelsProperties.register(basket, new net.minecraft.util.ResourceLocation("filled"),
                        (stack, world, entity) -> stack.hasTag() && !stack.getTag().getList("Items", 10).isEmpty() ? 1 : 0);
            }
            ScreenManager.register(Baskets.TYPE, BasketScreen::new);
            RenderTypeLookup.setRenderLayer(RetiredDecorations.STONE_FORMATIONS, RenderType.cutout());
            RenderTypeLookup.setRenderLayer(RetiredDecorations.JELLED_POISON, RenderType.translucent());
        });
    }
}
