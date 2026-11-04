package zone.moddev.mc.skysbuildingpiecesbop.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
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
            for (net.minecraft.world.item.Item basket : new net.minecraft.world.item.Item[] {Baskets.ITEM, Baskets.HOLDER}) {
                net.minecraft.client.renderer.item.ItemProperties.register(basket, new net.minecraft.resources.ResourceLocation("filled"),
                        (stack, world, entity, seed) -> !zone.moddev.mc.skysbuildingpiecesbop.basket.BasketData.customData(stack).getList("Items", 10).isEmpty() ? 1 : 0);
            }
            MenuScreens.register(Baskets.TYPE, BasketScreen::new);
            ItemBlockRenderTypes.setRenderLayer(RetiredDecorations.STONE_FORMATIONS, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(RetiredDecorations.JELLED_POISON, RenderType.translucent());
        });
    }
}
