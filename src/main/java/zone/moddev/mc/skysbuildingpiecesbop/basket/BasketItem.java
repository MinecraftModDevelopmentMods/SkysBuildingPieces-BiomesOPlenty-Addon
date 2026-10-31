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
        super(hidden ? new Item.Properties().maxStackSize(1)
                : new Item.Properties().maxStackSize(1).group(ItemGroup.TOOLS));
        setRegistryName(hidden ? BasketData.HOLDER : BasketData.ID);
        addPropertyOverride(new ResourceLocation("filled"),
                (stack, world, entity) -> stack.hasTag() && !stack.getTag().getList("Items", 10).isEmpty() ? 1 : 0);
    }

    @Override
    public String getTranslationKey() {
        return "item.skysbuildingpiecesbop.flower_basket";
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, PlayerEntity player, Hand hand) {
        ItemStack held = player.getHeldItem(hand);

        if (!world.isRemote) {
            NetworkHooks.openGui((ServerPlayerEntity) player, new INamedContainerProvider() {
                @Override
                public ITextComponent getDisplayName() {
                    if (!held.hasDisplayName() && held.hasTag() && held.getTag().contains("CustomName", 8)) {
                        return new StringTextComponent(held.getTag().getString("CustomName"));
                    }

                    return held.getDisplayName();
                }

                @Override
                public Container createMenu(int windowId, PlayerInventory inventory, PlayerEntity owner) {
                    return new BasketContainer(windowId, owner, hand);
                }
            }, data -> data.writeEnumValue(hand));
        }

        return new ActionResult<>(ActionResultType.SUCCESS, held);
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack stack, PlayerEntity player) {
        return !(player.openContainer instanceof BasketContainer)
                || ((BasketContainer) player.openContainer).basket.owner() != stack;
    }
}
