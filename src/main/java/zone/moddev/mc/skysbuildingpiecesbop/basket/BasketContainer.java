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
        this(windowId, inventory.player, data.readEnumValue(Hand.class));
    }

    public BasketContainer(int windowId, PlayerEntity player, Hand hand) {
        super(Baskets.TYPE, windowId);
        this.player = player;
        ownerSlot = hand == Hand.MAIN_HAND ? player.inventory.currentItem : 40;

        ItemStack held = player.inventory.getStackInSlot(ownerSlot);

        if (!(held.getItem() instanceof BasketItem) || held.getCount() != 1) {
            throw new IllegalArgumentException("The selected hand does not hold one flower basket");
        }

        basket = new BasketInventory(held);

        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(basket, row * 9 + column, 8 + column * 18, 18 + row * 18) {
                    @Override
                    public boolean isItemValid(ItemStack stack) {
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
            public boolean canTakeStack(PlayerEntity viewingPlayer) {
                return getSlotIndex() != ownerSlot;
            }

            @Override
            public boolean isItemValid(ItemStack stack) {
                return getSlotIndex() != ownerSlot;
            }
        });
    }

    @Override
    public boolean canInteractWith(PlayerEntity viewingPlayer) {
        // Window sync replaces client stack objects; the exact-reference lock belongs only to the server.
        if (viewingPlayer.world.isRemote) {
            return viewingPlayer == player && viewingPlayer.isAlive()
                    && viewingPlayer.inventory.getStackInSlot(ownerSlot).getItem() instanceof BasketItem
                    && viewingPlayer.inventory.getStackInSlot(ownerSlot).getCount() == 1;
        }
        if (viewingPlayer != player || !viewingPlayer.isAlive() || basket.owner().getCount() != 1) {
            return false;
        }
        // Hand-swap packets bypass slot clicks. Restore the original stack, never a matching copy.
        if (viewingPlayer.inventory.getStackInSlot(ownerSlot) != basket.owner()) {
            for (int slotIndex = 0; slotIndex < viewingPlayer.inventory.getSizeInventory(); slotIndex++) {
                if (viewingPlayer.inventory.getStackInSlot(slotIndex) == basket.owner()) {
                    ItemStack displaced = viewingPlayer.inventory.getStackInSlot(ownerSlot);

                    viewingPlayer.inventory.setInventorySlotContents(ownerSlot, basket.owner());
                    viewingPlayer.inventory.setInventorySlotContents(slotIndex, displaced);
                    viewingPlayer.inventory.markDirty();
                    break;
                }
            }
        }

        return viewingPlayer.inventory.getStackInSlot(ownerSlot) == basket.owner();
    }

    @Override
    public ItemStack slotClick(int slot, int button, ClickType type, PlayerEntity viewingPlayer) {
        if (!canInteractWith(viewingPlayer)) {
            return ItemStack.EMPTY;
        }
        if (slot >= 0 && slot < inventorySlots.size()) {
            Slot selected = inventorySlots.get(slot);

            if (selected.inventory == player.inventory && selected.getSlotIndex() == ownerSlot) {
                return ItemStack.EMPTY;
            }
        }
        if (type == ClickType.SWAP && button == ownerSlot) {
            return ItemStack.EMPTY;
        }

        return super.slotClick(slot, button, type, viewingPlayer);
    }

    @Override
    public boolean canMergeSlot(ItemStack stack, Slot slot) {
        return !(slot.inventory == player.inventory && slot.getSlotIndex() == ownerSlot)
                && super.canMergeSlot(stack, slot);
    }

    @Override
    public ItemStack transferStackInSlot(PlayerEntity viewingPlayer, int index) {
        if (!canInteractWith(viewingPlayer) || index < 0 || index >= inventorySlots.size()) {
            return ItemStack.EMPTY;
        }

        Slot slot = inventorySlots.get(index);

        if (!slot.getHasStack() || !slot.canTakeStack(viewingPlayer)) {
            return ItemStack.EMPTY;
        }

        ItemStack stored = slot.getStack();
        ItemStack before = stored.copy();

        if (index < BasketData.SIZE) {
            if (!mergeItemStack(stored, BasketData.SIZE, inventorySlots.size(), true)) {
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
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }

        slot.onTake(viewingPlayer, stored);
        return before;
    }

    @Override
    public void onContainerClosed(PlayerEntity viewingPlayer) {
        basket.markDirty();
        super.onContainerClosed(viewingPlayer);
    }
}
