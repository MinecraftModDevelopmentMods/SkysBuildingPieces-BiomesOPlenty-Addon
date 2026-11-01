package zone.moddev.mc.skysbuildingpiecesbop.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import zone.moddev.mc.skysbuildingpiecesbop.basket.BasketContainer;

/** Two basket rows above the player's inventory, using the native chest texture. */
public final class BasketScreen extends ContainerScreen<BasketContainer> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/container/generic_54.png");

    public BasketScreen(BasketContainer basket, PlayerInventory inventory, ITextComponent title) {
        super(basket, inventory, title);
        imageHeight = 150;
        inventoryLabelY = 56;
    }

    @Override
    public void render(MatrixStack pose, int mouseX, int mouseY, float partialTicks) {
        renderBackground(pose);
        super.render(pose, mouseX, mouseY, partialTicks);
        renderTooltip(pose, mouseX, mouseY);
    }

    @Override
    protected void renderBg(MatrixStack pose, float ticks, int mouseX, int mouseY) {
        RenderSystem.color4f(1, 1, 1, 1);
        minecraft.getTextureManager().bind(TEXTURE);
        blit(pose, leftPos, topPos, 0, 0, imageWidth, 53);
        blit(pose, leftPos, topPos + 53, 0, 126, imageWidth, 97);
    }
}
