package zone.moddev.mc.skysbuildingpiecesbop.client;

import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.bus.api.IEventBus;
import zone.moddev.mc.skysbuildingpiecesbop.basket.Baskets;
import zone.moddev.mc.skysbuildingpiecesbop.RetiredDecorations;

/** Registers the basket screen and the restored decorations' native render layers. */
public final class RetiredRockModels {
    private RetiredRockModels() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RetiredRockModels::setup);
        modBus.addListener(RetiredRockModels::screens);
    }

    private static void screens(RegisterMenuScreensEvent event) {
        event.register(Baskets.TYPE, BasketScreen::new);
    }

    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (net.minecraft.world.item.Item basket : new net.minecraft.world.item.Item[] {Baskets.ITEM, Baskets.HOLDER}) {
                net.minecraft.client.renderer.item.ItemProperties.register(basket, new net.minecraft.resources.ResourceLocation("filled"),
                        (stack, world, entity, seed) -> !zone.moddev.mc.skysbuildingpiecesbop.basket.BasketData.customData(stack).getList("Items", 10).isEmpty() ? 1 : 0);
            }
            ItemBlockRenderTypes.setRenderLayer(RetiredDecorations.STONE_FORMATIONS, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(RetiredDecorations.JELLED_POISON, RenderType.translucent());
        });
    }
}
