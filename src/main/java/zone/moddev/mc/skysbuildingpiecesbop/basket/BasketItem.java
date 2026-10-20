package zone.moddev.mc.skysbuildingpiecesbop.basket;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.world.World;
import zone.moddev.mc.skysbuildingpiecesbop.SkysBuildingPiecesBop;

public final class BasketItem extends Item {
    BasketItem(boolean hidden) {
        setRegistryName(hidden ? BasketData.HOLDER : BasketData.ID);
        setTranslationKey("skysbuildingpiecesbop.flower_basket");
        setMaxStackSize(1);

        if (!hidden) {
            setCreativeTab(CreativeTabs.TOOLS);
        }

        addPropertyOverride(new ResourceLocation("filled"), (stack, world,
                entity) -> stack.hasTagCompound() && !stack.getTagCompound().getTagList("Items", 10).isEmpty() ? 1 : 0);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);

        if (!world.isRemote) {
            player.openGui(SkysBuildingPiecesBop.INSTANCE, 0, world, hand.ordinal(), 0, 0);
        }

        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, held);
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack stack, EntityPlayer player) {
        return !(player.openContainer instanceof BasketContainer)
                || ((BasketContainer) player.openContainer).basket.owner() != stack;
    }
}
