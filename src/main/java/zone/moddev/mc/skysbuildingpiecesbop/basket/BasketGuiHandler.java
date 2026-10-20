package zone.moddev.mc.skysbuildingpiecesbop.basket;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.relauncher.*;

public final class BasketGuiHandler implements IGuiHandler {
    private boolean valid(EntityPlayer player, int guiId, int hand) {
        return guiId == 0 && hand >= 0 && hand < EnumHand.values().length
                && player.getHeldItem(EnumHand.values()[hand]).getItem() instanceof BasketItem;
    }

    @Override
    public Object getServerGuiElement(int guiId, EntityPlayer player, World world, int hand, int unusedY, int unusedZ) {
        return valid(player, guiId, hand) ? new BasketContainer(player, EnumHand.values()[hand]) : null;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Object getClientGuiElement(int guiId, EntityPlayer player, World world, int hand, int unusedY, int unusedZ) {
        return valid(player, guiId, hand)
                ? new zone.moddev.mc.skysbuildingpiecesbop.client.BasketScreen(
                        new BasketContainer(player, EnumHand.values()[hand]))
                : null;
    }
}
