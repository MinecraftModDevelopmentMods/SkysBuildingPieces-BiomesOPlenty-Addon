package zone.moddev.mc.skysbuildingpiecesbop.basket;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/** The container binds to the held stack, rather than a global open-basket flag. */
public final class BasketItem extends Item {
    BasketItem(boolean hidden) {
        super(new Item.Properties().stacksTo(1));

    }

    @Override
    public String getDescriptionId() {
        return "item.skysbuildingpiecesbop.flower_basket";
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        if (!world.isClientSide) {
            NetworkHooks.openScreen((ServerPlayer) player, new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    if (!held.hasCustomHoverName() && held.hasTag() && held.getTag().contains("CustomName", 8)) {
                        return Component.literal(held.getTag().getString("CustomName"));
                    }

                    return held.getHoverName();
                }

                @Override
                public AbstractContainerMenu createMenu(int windowId, Inventory inventory, Player owner) {
                    return new BasketContainer(windowId, owner, hand);
                }
            }, data -> data.writeEnum(hand));
        }

        return new InteractionResultHolder<>(InteractionResult.SUCCESS, held);
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack stack, Player player) {
        return !(player.containerMenu instanceof BasketContainer)
                || ((BasketContainer) player.containerMenu).basket.owner() != stack;
    }
}
