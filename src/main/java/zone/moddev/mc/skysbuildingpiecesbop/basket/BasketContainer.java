package zone.moddev.mc.skysbuildingpiecesbop.basket;

import net.minecraft.world.inventory.AbstractContainerMenu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;

/** The open basket cannot be moved into its own inventory or swapped underneath the GUI. */
public final class BasketContainer extends AbstractContainerMenu {
    private final Player player;
    private final int ownerSlot;
    public final BasketInventory basket;

    public BasketContainer(int windowId, Inventory inventory, FriendlyByteBuf data) {
        this(windowId, inventory.player, data.readEnum(InteractionHand.class));
    }

    public BasketContainer(int windowId, Player player, InteractionHand hand) {
        super(Baskets.TYPE, windowId);
        this.player = player;
        ownerSlot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;

        ItemStack held = player.getInventory().getItem(ownerSlot);

        if (!(held.getItem() instanceof BasketItem) || held.getCount() != 1) {
            throw new IllegalArgumentException("The selected hand does not hold one flower basket");
        }

        basket = new BasketInventory(held);

        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(basket, row * 9 + column, 8 + column * 18, 18 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return BasketInventory.accepts(stack);
                    }
                });
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addPlayerSlot(row * 9 + column + 9, 8 + column * 18, 67 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            addPlayerSlot(column, 8 + column * 18, 125);
        }
    }

    private void addPlayerSlot(int slot, int screenX, int screenY) {
        addSlot(new Slot(player.getInventory(), slot, screenX, screenY) {
            @Override
            public boolean mayPickup(Player viewingPlayer) {
                return getSlotIndex() != ownerSlot;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return getSlotIndex() != ownerSlot;
            }
        });
    }

    @Override
    public boolean stillValid(Player viewingPlayer) {
        // Window sync replaces client stack objects; the exact-reference lock belongs only to the server.
        if (viewingPlayer.level().isClientSide) {
            return viewingPlayer == player && viewingPlayer.isAlive()
                    && viewingPlayer.getInventory().getItem(ownerSlot).getItem() instanceof BasketItem
                    && viewingPlayer.getInventory().getItem(ownerSlot).getCount() == 1;
        }
        if (viewingPlayer != player || !viewingPlayer.isAlive() || basket.owner().getCount() != 1) {
            return false;
        }
        // Hand-swap packets bypass slot clicks. Restore the original stack, never a matching copy.
        if (viewingPlayer.getInventory().getItem(ownerSlot) != basket.owner()) {
            for (int slotIndex = 0; slotIndex < viewingPlayer.getInventory().getContainerSize(); slotIndex++) {
                if (viewingPlayer.getInventory().getItem(slotIndex) == basket.owner()) {
                    ItemStack displaced = viewingPlayer.getInventory().getItem(ownerSlot);

                    viewingPlayer.getInventory().setItem(ownerSlot, basket.owner());
                    viewingPlayer.getInventory().setItem(slotIndex, displaced);
                    viewingPlayer.getInventory().setChanged();
                    break;
                }
            }
        }

        return viewingPlayer.getInventory().getItem(ownerSlot) == basket.owner();
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player viewingPlayer) {
        if (!stillValid(viewingPlayer)) {
            return;
        }
        if (slot >= 0 && slot < slots.size()) {
            Slot selected = slots.get(slot);

            if (selected.container == player.getInventory() && selected.getSlotIndex() == ownerSlot) {
                return;
            }
        }
        if (type == ClickType.SWAP && button == ownerSlot) {
            return;
        }

        super.clicked(slot, button, type, viewingPlayer);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot.container == player.getInventory() && slot.getSlotIndex() == ownerSlot)
                && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public ItemStack quickMoveStack(Player viewingPlayer, int index) {
        if (!stillValid(viewingPlayer) || index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot slot = slots.get(index);

        if (!slot.hasItem() || !slot.mayPickup(viewingPlayer)) {
            return ItemStack.EMPTY;
        }

        ItemStack stored = slot.getItem();
        ItemStack before = stored.copy();

        if (index < BasketData.SIZE) {
            if (!moveItemStackTo(stored, BasketData.SIZE, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!BasketInventory.accepts(stored)) {
                return ItemStack.EMPTY;
            }

            ItemStack rest = basket.insert(stored);

            if (rest.getCount() == stored.getCount()) {
                return ItemStack.EMPTY;
            }

            stored.setCount(rest.getCount());
        }
        if (stored.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        slot.onTake(viewingPlayer, stored);
        return before;
    }

    @Override
    public void removed(Player viewingPlayer) {
        basket.setChanged();
        super.removed(viewingPlayer);
    }
}
