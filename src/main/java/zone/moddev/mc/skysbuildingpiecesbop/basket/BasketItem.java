package zone.moddev.mc.skysbuildingpiecesbop.basket;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;

/** The container binds to the held stack, rather than a global open-basket flag. */
public final class BasketItem extends Item {
    BasketItem(boolean hidden) {
        super(hidden ? new Item.Properties().stacksTo(1)
                : new Item.Properties().stacksTo(1).tab(ItemGroup.TAB_TOOLS));
        setRegistryName(hidden ? BasketData.HOLDER : BasketData.ID);

    }

    @Override
    public String getDescriptionId() {
        return "item.skysbuildingpiecesbop.flower_basket";
    }

    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack held = player.getItemInHand(hand);

        if (!world.isClientSide) {
            NetworkHooks.openGui((ServerPlayerEntity) player, new INamedContainerProvider() {
                @Override
                public ITextComponent getDisplayName() {
                    if (!held.hasCustomHoverName() && held.hasTag() && held.getTag().contains("CustomName", 8)) {
                        return new StringTextComponent(held.getTag().getString("CustomName"));
                    }

                    return held.getHoverName();
                }

                @Override
                public Container createMenu(int windowId, PlayerInventory inventory, PlayerEntity owner) {
                    return new BasketContainer(windowId, owner, hand);
                }
            }, data -> data.writeEnum(hand));
        }

        return new ActionResult<>(ActionResultType.SUCCESS, held);
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack stack, PlayerEntity player) {
        return !(player.containerMenu instanceof BasketContainer)
                || ((BasketContainer) player.containerMenu).basket.owner() != stack;
    }
}
