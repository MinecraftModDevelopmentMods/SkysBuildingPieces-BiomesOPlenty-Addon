package zone.moddev.mc.skysbuildingpiecesbop.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import zone.moddev.mc.skysbuildingpiecesbop.basket.BasketContainer;

/** Two basket rows above the player's inventory, using the native chest texture. */
public final class BasketScreen extends AbstractContainerScreen<BasketContainer> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/container/generic_54.png");

    public BasketScreen(BasketContainer basket, Inventory inventory, Component title) {
        super(basket, inventory, title);
        imageHeight = 150;
        inventoryLabelY = 56;
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTicks) {
        renderBackground(pose);
        super.render(pose, mouseX, mouseY, partialTicks);
        renderTooltip(pose, mouseX, mouseY);
    }

    @Override
    protected void renderBg(PoseStack pose, float ticks, int mouseX, int mouseY) {
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderTexture(0, TEXTURE);
        blit(pose, leftPos, topPos, 0, 0, imageWidth, 53);
        blit(pose, leftPos, topPos + 53, 0, 126, imageWidth, 97);
    }
}
