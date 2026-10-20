package zone.moddev.mc.skysbuildingpiecesbop.client;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import zone.moddev.mc.skysbuildingpiecesbop.basket.BasketContainer;

/** Two rows of storage, using Minecraft's own chest GUI texture. */
public final class BasketScreen extends GuiContainer {
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/gui/container/generic_54.png");
    private final BasketContainer basket;

    public BasketScreen(BasketContainer basket) {
        super(basket);
        this.basket = basket;
        ySize = 150;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(basket.basket.getDisplayName().getUnformattedText(), 8, 6, 4210752);
        fontRenderer.drawString(I18n.format("container.inventory"), 8, 56, 4210752);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float ticks, int mouseX, int mouseY) {
        GlStateManager.color(1, 1, 1, 1);
        mc.getTextureManager().bindTexture(TEXTURE);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, 53);
        drawTexturedModalRect(guiLeft, guiTop + 53, 0, 126, xSize, 97);
    }
}
