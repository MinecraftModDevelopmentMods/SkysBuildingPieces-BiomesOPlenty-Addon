package zone.moddev.mc.skysbuildingpiecesbop.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import zone.moddev.mc.skysbuildingpiecesbop.basket.BasketContainer;

/** Two basket rows above the player's inventory, using the native chest texture. */
public final class BasketScreen extends AbstractContainerScreen<BasketContainer> {
    private static final ResourceLocation TEXTURE = ResourceLocation.parse("textures/gui/container/generic_54.png");

    public BasketScreen(BasketContainer basket, Inventory inventory, Component title) {
        super(basket, inventory, title);
        imageHeight = 150;
        inventoryLabelY = 56;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.render(graphics, mouseX, mouseY, partialTicks);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float ticks, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, 53);
        graphics.blit(TEXTURE, leftPos, topPos + 53, 0, 126, imageWidth, 97);
    }
}
