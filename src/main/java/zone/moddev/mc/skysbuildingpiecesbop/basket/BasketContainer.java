package zone.moddev.mc.skysbuildingpiecesbop.basket;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.container.*;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketBuffer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

/** The open basket cannot be moved into its own inventory or swapped underneath the GUI. */
public final class BasketContainer extends Container {
    private final PlayerEntity player;
    private final int ownerSlot;
    public final BasketInventory basket;

    public BasketContainer(int windowId, PlayerInventory inventory, PacketBuffer data) {
        this(windowId, inventory.player, data.readEnum(Hand.class));
    }

    public BasketContainer(int windowId, PlayerEntity player, Hand hand) {
        super(Baskets.TYPE, windowId);
        this.player = player;
        ownerSlot = hand == Hand.MAIN_HAND ? player.inventory.selected : 40;

        ItemStack held = player.inventory.getItem(ownerSlot);

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
        addSlot(new Slot(player.inventory, slot, screenX, screenY) {
            @Override
            public boolean mayPickup(PlayerEntity viewingPlayer) {
                return getSlotIndex() != ownerSlot;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return getSlotIndex() != ownerSlot;
            }
        });
    }

    @Override
    public boolean stillValid(PlayerEntity viewingPlayer) {
        // Window sync replaces client stack objects; the exact-reference lock belongs only to the server.
        if (viewingPlayer.level.isClientSide) {
            return viewingPlayer == player && viewingPlayer.isAlive()
                    && viewingPlayer.inventory.getItem(ownerSlot).getItem() instanceof BasketItem
                    && viewingPlayer.inventory.getItem(ownerSlot).getCount() == 1;
        }
        if (viewingPlayer != player || !viewingPlayer.isAlive() || basket.owner().getCount() != 1) {
            return false;
        }
        // Hand-swap packets bypass slot clicks. Restore the original stack, never a matching copy.
        if (viewingPlayer.inventory.getItem(ownerSlot) != basket.owner()) {
            for (int slotIndex = 0; slotIndex < viewingPlayer.inventory.getContainerSize(); slotIndex++) {
                if (viewingPlayer.inventory.getItem(slotIndex) == basket.owner()) {
                    ItemStack displaced = viewingPlayer.inventory.getItem(ownerSlot);

                    viewingPlayer.inventory.setItem(ownerSlot, basket.owner());
                    viewingPlayer.inventory.setItem(slotIndex, displaced);
                    viewingPlayer.inventory.setChanged();
                    break;
                }
            }
        }

        return viewingPlayer.inventory.getItem(ownerSlot) == basket.owner();
    }

    @Override
    public ItemStack clicked(int slot, int button, ClickType type, PlayerEntity viewingPlayer) {
        if (!stillValid(viewingPlayer)) {
            return ItemStack.EMPTY;
        }
        if (slot >= 0 && slot < slots.size()) {
            Slot selected = slots.get(slot);

            if (selected.container == player.inventory && selected.getSlotIndex() == ownerSlot) {
                return ItemStack.EMPTY;
            }
        }
        if (type == ClickType.SWAP && button == ownerSlot) {
            return ItemStack.EMPTY;
        }

        return super.clicked(slot, button, type, viewingPlayer);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot.container == player.inventory && slot.getSlotIndex() == ownerSlot)
                && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public ItemStack quickMoveStack(PlayerEntity viewingPlayer, int index) {
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
    public void removed(PlayerEntity viewingPlayer) {
        basket.setChanged();
        super.removed(viewingPlayer);
    }
}
