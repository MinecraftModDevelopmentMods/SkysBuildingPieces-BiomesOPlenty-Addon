package zone.moddev.mc.skysbuildingpiecesbop.client;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import zone.moddev.mc.skysbuildingpiecesbop.basket.BasketContainer;

/** Two basket rows above the player's inventory, using the native chest texture. */
public final class BasketScreen extends ContainerScreen<BasketContainer> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/container/generic_54.png");

    public BasketScreen(BasketContainer basket, PlayerInventory inventory, ITextComponent title) {
        super(basket, inventory, title);
        ySize = 150;
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        renderBackground();
        super.render(mouseX, mouseY, partialTicks);
        renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        font.drawString(title.getFormattedText(), 8, 6, 4210752);
        font.drawString(I18n.format("container.inventory"), 8, 56, 4210752);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float ticks, int mouseX, int mouseY) {
        GlStateManager.color4f(1, 1, 1, 1);
        minecraft.getTextureManager().bindTexture(TEXTURE);
        blit(guiLeft, guiTop, 0, 0, xSize, 53);
        blit(guiLeft, guiTop + 53, 0, 126, xSize, 97);
    }
}
