package zone.moddev.mc.skysbuildingpiecesbop.basket;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** The container binds to the held stack, rather than a global open-basket flag. */
public final class BasketItem extends Item {
    BasketItem(boolean hidden) {
        super(new Item.Properties().stacksTo(1).setId(ResourceKey.create(Registries.ITEM,
                Identifier.parse(hidden ? BasketData.HOLDER : BasketData.ID)))
                .overrideDescription("item.skysbuildingpiecesbop.flower_basket"));
    }

    @Override
    public InteractionResult use(Level world, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    var data = BasketData.customData(held);
                    if (!held.has(DataComponents.CUSTOM_NAME) && data.getString("CustomName").isPresent()) {
                        return Component.literal(data.getStringOr("CustomName", ""));
                    }

                    return held.getHoverName();
                }

                @Override
                public AbstractContainerMenu createMenu(int windowId, Inventory inventory, Player owner) {
                    return new BasketContainer(windowId, owner, hand);
                }
            }, data -> data.writeEnum(hand));
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack stack, Player player) {
        return !(player.containerMenu instanceof BasketContainer)
                || ((BasketContainer) player.containerMenu).basket.owner() != stack;
    }
}
