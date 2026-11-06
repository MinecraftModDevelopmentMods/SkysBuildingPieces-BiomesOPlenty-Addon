package zone.moddev.mc.skysbuildingpiecesbop.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import zone.moddev.mc.skysbuildingpiecesbop.basket.BasketContainer;

/** Two basket rows above the player's inventory, using the native chest texture. */
public final class BasketScreen extends AbstractContainerScreen<BasketContainer> {
    private static final Identifier TEXTURE = Identifier.parse("textures/gui/container/generic_54.png");

    public BasketScreen(BasketContainer basket, Inventory inventory, Component title) {
        super(basket, inventory, title, 176, 150);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(graphics, mouseX, mouseY, partialTicks);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, imageWidth, 53, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos + 53, 0, 126, imageWidth, 97, 256, 256);
    }
}
