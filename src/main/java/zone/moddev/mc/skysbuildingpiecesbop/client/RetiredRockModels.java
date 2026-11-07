package zone.moddev.mc.skysbuildingpiecesbop.client;

import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import zone.moddev.mc.skysbuildingpiecesbop.basket.BasketData;
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
            for (Item basket : new Item[] {Baskets.ITEM, Baskets.HOLDER}) {
                ItemProperties.register(basket, ResourceLocation.withDefaultNamespace("filled"),
                        (stack, world, entity, seed) -> BasketData.customData(stack).getList("Items", 10).isEmpty()
                                ? 0 : 1);
            }
            ItemBlockRenderTypes.setRenderLayer(RetiredDecorations.STONE_FORMATIONS, RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(RetiredDecorations.JELLED_POISON, RenderType.translucent());
        });
    }
}
