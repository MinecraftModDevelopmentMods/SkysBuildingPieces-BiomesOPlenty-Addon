package zone.moddev.mc.skysbuildingpiecesbop.probe;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.EnumHand;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import zone.moddev.mc.skysbuildingpiecesbop.basket.*;

/** Real item, container, pickup and raw-storage checks; never shipped in the mod. */
public final class BasketChecks {
    private static int checks;

    public static void run(WorldServer world) {
        checks = 0;

        EntityPlayerMP player = FakePlayerFactory.getMinecraft(world);

        player.inventory.clear();
        player.inventory.currentItem = 0;

        try {
            net.minecraft.inventory.InventoryCrafting crafting = new net.minecraft.inventory.InventoryCrafting(
                    new net.minecraft.inventory.Container() {
                        @Override
                        public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) {
                            return true;
                        }
                    }, 3, 3);

            for (int slot : new int[] { 1, 3, 5, 6, 7, 8 }) {
                crafting.setInventorySlotContents(slot, new ItemStack(Items.STICK));
            }

            net.minecraft.item.crafting.IRecipe recipe = net.minecraft.item.crafting.CraftingManager
                    .findMatchingRecipe(crafting, world);

            require(recipe != null && recipe.getCraftingResult(crafting).getItem() == Baskets.ITEM
                    && recipe.getCraftingResult(crafting).getCount() == 1, "real six-stick crafting recipe");
            require(Baskets.ITEM.getItemStackLimit(new ItemStack(Baskets.ITEM)) == 1, "one basket per stack");
            require(Baskets.HOLDER.getCreativeTab() == null, "hidden recovery item");
            require(BasketInventory.accepts(new ItemStack(Blocks.SAPLING)), "saplings accepted");
            require(BasketInventory.accepts(new ItemStack(Blocks.LEAVES)), "leaves accepted");
            require(!BasketInventory.accepts(new ItemStack(Items.DIAMOND)), "non-plants rejected");
            require(!BasketInventory.accepts(new ItemStack(Items.WHEAT_SEEDS)), "retain block-item eligibility");
            require(!BasketInventory.accepts(new ItemStack(Baskets.ITEM)), "no nested baskets");

            ItemStack basket = new ItemStack(Baskets.ITEM);

            basket.setStackDisplayName("Test plants");
            basket.getTagCompound().setInteger("unrelated", 19);

            BasketInventory inventory = new BasketInventory(basket);

            require(inventory.insert(new ItemStack(Blocks.SAPLING, 60)).isEmpty(), "initial insert");
            require(inventory.insert(new ItemStack(Blocks.SAPLING, 12)).isEmpty(), "merge and spill to next slot");
            require(inventory.getStackInSlot(0).getCount() == 64 && inventory.getStackInSlot(1).getCount() == 8,
                    "merge before empty slots");
            require(basket.getTagCompound().getInteger("unrelated") == 19
                    && basket.getDisplayName().equals("Test plants"), "custom data retained");
            require(new BasketInventory(basket).getStackInSlot(1).getCount() == 8, "storage reload");

            for (int entryIndex = 0; entryIndex < 18; entryIndex++) {
                inventory.setInventorySlotContents(entryIndex,
                        new ItemStack(Blocks.SAPLING, entryIndex == 17 ? 60 : 64));
            }

            ItemStack rest = inventory.insert(new ItemStack(Blocks.SAPLING, 12));

            require(rest.getCount() == 8, "partial insertion retains remainder");
            player.inventory.setInventorySlotContents(0, basket);

            for (EnumHand hand : EnumHand.values()) {
                int index = hand == EnumHand.MAIN_HAND ? 0 : 40;

                player.inventory.setInventorySlotContents(0, ItemStack.EMPTY);
                player.inventory.setInventorySlotContents(index, basket);

                BasketContainer container = new BasketContainer(player, hand);

                player.openContainer = container;
                require(container.canInteractWith(player), "opens exact " + hand);
                require(!Baskets.ITEM.onDroppedByPlayer(basket, player), "throwing owner is blocked before removal");

                int other = index == 40 ? 0 : 40;

                player.inventory.setInventorySlotContents(index, new ItemStack(Items.STICK));
                player.inventory.setInventorySlotContents(other, basket);
                require(container.canInteractWith(player) && player.inventory.getStackInSlot(index) == basket
                        && player.inventory.getStackInSlot(other).getItem() == Items.STICK,
                        "hand-swap packet is reversed without copying");

                if (index == 0) {
                    for (ClickType type : ClickType.values()) {
                        container.slotClick(45, 0, type, player);
                        require(player.inventory.getStackInSlot(0) == basket, "owner click locked " + type);
                    }
                }

                player.inventory.setInventorySlotContents(9, new ItemStack(Blocks.SAPLING, 5));
                container.slotClick(18, index, ClickType.SWAP, player);
                require(player.inventory.getStackInSlot(index) == basket, "swap owner locked");
                player.inventory.setInventorySlotContents(index, basket.copy());
                require(!container.canInteractWith(player), "copied stack is not the owner");
                player.inventory.setInventorySlotContents(index, basket);
                container.onContainerClosed(player);
            }

            ItemStack editable = new ItemStack(Baskets.ITEM);

            player.inventory.clear();
            player.inventory.setInventorySlotContents(0, editable);

            BasketContainer editor = new BasketContainer(player, EnumHand.MAIN_HAND);

            player.openContainer = editor;
            player.inventory.setInventorySlotContents(9, new ItemStack(Blocks.SAPLING, 5));
            editor.transferStackInSlot(player, 18);
            require(editor.basket.getStackInSlot(0).getCount() == 5 && player.inventory.getStackInSlot(9).isEmpty(),
                    "shift-click inserts");
            player.inventory.setInventorySlotContents(9, new ItemStack(Items.DIAMOND));
            editor.transferStackInSlot(player, 18);
            require(editor.basket.getStackInSlot(1).isEmpty()
                    && player.inventory.getStackInSlot(9).getItem() == Items.DIAMOND,
                    "shift-click rejects unrelated items");
            editor.transferStackInSlot(player, 0);
            require(editor.basket.isEmpty(), "shift-click withdraws");
            editor.basket.setInventorySlotContents(3, new ItemStack(Items.DIAMOND, 2));
            require(new BasketInventory(editable).getStackInSlot(3).getCount() == 2,
                    "stored non-plant remains recoverable");
            editor.transferStackInSlot(player, 3);
            require(editor.basket.isEmpty(), "stored non-plant can be withdrawn");
            editor.basket.insert(new ItemStack(Blocks.SAPLING, 11));
            editor.onContainerClosed(player);
            require(new BasketInventory(editable).getStackInSlot(0).getCount() == 11,
                    "close persists contents immediately");
            player.inventory.clear();

            ItemStack collected = new ItemStack(Baskets.ITEM);

            player.inventory.setInventorySlotContents(0, collected);
            player.inventory.setInventorySlotContents(1, new ItemStack(Baskets.ITEM));
            player.openContainer = player.inventoryContainer;

            EntityItem entity = new EntityItem(world, 0, 100, 0, new ItemStack(Blocks.SAPLING, 7));
            EntityItemPickupEvent event = new EntityItemPickupEvent(player, entity);

            Baskets.pickup(event);
            require(event.isCanceled() && entity.isDead, "complete pickup consumed once");
            require(new BasketInventory(collected).getStackInSlot(0).getCount() == 7, "first basket collects");
            require(!player.inventory.getStackInSlot(1).hasTagCompound(), "later basket unchanged");

            BasketInventory full = new BasketInventory(collected);

            for (int entryIndex = 0; entryIndex < 18; entryIndex++) {
                full.setInventorySlotContents(entryIndex, new ItemStack(Blocks.SAPLING, entryIndex == 0 ? 61 : 64));
            }

            entity = new EntityItem(world, 0, 100, 0, new ItemStack(Blocks.SAPLING, 9));
            event = new EntityItemPickupEvent(player, entity);
            Baskets.pickup(event);
            require(event.isCanceled() && !entity.isDead && entity.getItem().getCount() == 6,
                    "partial pickup leaves exact remainder for the next collision");
            // Exercise the caller too: vanilla retains the stack reference from before the pickup event, so replacing
            // only EntityItem's stack is unsafe.
            player.inventory.clear();
            player.inventory.setInventorySlotContents(0, collected);
            player.capabilities.isCreativeMode = false;
            full = new BasketInventory(collected);

            for (int entryIndex = 0; entryIndex < 18; entryIndex++) {
                full.setInventorySlotContents(entryIndex, new ItemStack(Blocks.SAPLING, entryIndex == 0 ? 61 : 64));
            }

            entity = new EntityItem(world, 0, 100, 0, new ItemStack(Blocks.SAPLING, 9));
            entity.onCollideWithPlayer(player);

            if (!entity.isDead) {
                entity.onCollideWithPlayer(player);
            }

            int loose = 0;

            for (ItemStack stack : player.inventory.mainInventory) {
                if (stack.getItem() == net.minecraft.item.Item.getItemFromBlock(Blocks.SAPLING)) {
                    loose += stack.getCount();
                }
            }

            require(new BasketInventory(collected).getStackInSlot(0).getCount() == 64 && loose == 6 && entity.isDead,
                    "real partial pickup conserves all nine items");

            for (int entryIndex = 1; entryIndex < player.inventory.mainInventory.size(); entryIndex++) {
                player.inventory.setInventorySlotContents(entryIndex, new ItemStack(Blocks.STONE, 64));
            }

            full = new BasketInventory(collected);
            full.setInventorySlotContents(0, new ItemStack(Blocks.SAPLING, 61));
            entity = new EntityItem(world, 0, 100, 0, new ItemStack(Blocks.SAPLING, 9));
            entity.onCollideWithPlayer(player);
            entity.onCollideWithPlayer(player);
            require(!entity.isDead && entity.getItem().getCount() == 6
                    && new BasketInventory(collected).getStackInSlot(0).getCount() == 64,
                    "real full inventory retains six ground items");
            player.inventory.clear();

            ItemStack ownedBasket = new ItemStack(Baskets.ITEM);

            player.inventory.setInventorySlotContents(0, ownedBasket);
            entity = new EntityItem(world, 0, 100, 0, new ItemStack(Blocks.SAPLING, 4));
            entity.setOwner("OtherPickupOwner");
            entity.onCollideWithPlayer(player);
            require(!entity.isDead && entity.getItem().getCount() == 4 && new BasketInventory(ownedBasket).isEmpty(),
                    "respect vanilla exclusive pickup ownership");
            entity.setOwner(player.getName());
            entity.onCollideWithPlayer(player);
            require(entity.isDead && new BasketInventory(ownedBasket).getStackInSlot(0).getCount() == 4,
                    "the drop owner can collect plants");
            rawRecovery();
            System.out.println("BOP_FLOWER_BASKET_PASS checks=" + checks
                    + " slots=18 collection=true NBT=preserved reload=idempotent");
        } finally {
            player.inventory.clear();
            player.openContainer = player.inventoryContainer;
        }
    }

    private static void rawRecovery() {
        BasketData fixer = new BasketData();

        for (String registryId : new String[] { BasketData.OLD, BasketData.HOLDER, BasketData.ID }) {
            NBTTagCompound stack = new ItemStack(Baskets.ITEM).writeToNBT(new NBTTagCompound());

            stack.setString("id", registryId);

            NBTTagCompound tag = new NBTTagCompound();

            tag.setBoolean("BasketOpen", true);
            tag.setString("CustomName", "Archived plants");
            tag.setInteger("other", 77);

            NBTTagList list = new NBTTagList();
            NBTTagCompound child = new ItemStack(Blocks.SAPLING, 53, 2).writeToNBT(new NBTTagCompound());

            child.setByte("Slot", (byte) 17);
            list.appendTag(child);
            tag.setTag("Items", list);
            stack.setTag("tag", tag);
            fixer.fixTagCompound(stack);
            require(stack.getString("id").equals(BasketData.ID), "canonical item ID");
            require(!stack.getCompoundTag("tag").getBoolean("BasketOpen")
                    && stack.getCompoundTag("tag").getInteger("other") == 77, "clear only stale open state");
            require(stack.getCompoundTag("tag").getTagList("Items", 10).getCompoundTagAt(0).equals(child),
                    "raw child NBT unchanged");

            NBTTagCompound once = stack.copy();

            fixer.fixTagCompound(stack);
            require(stack.equals(once), "raw reload idempotence");
            require(new BasketInventory(new ItemStack(stack)).getStackInSlot(17).getCount() == 53,
                    "deserialization preserved contents");

            for (String carrier : new String[] { "Inventory", "EnderItems", "Items", "Equipment", "CustomHandler" }) {
                NBTTagCompound root = new NBTTagCompound();
                NBTTagList listCarrier = new NBTTagList();
                NBTTagCompound historical = once.copy();

                historical.setString("id", BasketData.OLD);
                listCarrier.appendTag(historical);
                root.setTag(carrier, listCarrier);
                fixer.fixTagCompound(root);
                require(root.getTagList(carrier, 10).getCompoundTagAt(0).getString("id").equals(BasketData.ID),
                        "nested carrier " + carrier);
            }
            for (String error : new String[] { "unknown", "duplicate", "badslot", "badcount", "fractional", "wronglist",
                    "badtag", "badcaps" }) {
                NBTTagCompound broken = once.copy();
                NBTTagCompound entry = broken.getCompoundTag("tag").getTagList("Items", 10).getCompoundTagAt(0);

                if (error.equals("unknown")) {
                    entry.setString("id", "missing_test:flower");
                }
                if (error.equals("duplicate")) {
                    broken.getCompoundTag("tag").getTagList("Items", 10).appendTag(entry.copy());
                }
                if (error.equals("badslot")) {
                    entry.setByte("Slot", (byte) 18);
                }
                if (error.equals("badcount")) {
                    entry.setByte("Count", (byte) 65);
                }
                if (error.equals("fractional")) {
                    entry.setFloat("Slot", .5f);
                }
                if (error.equals("wronglist")) {
                    broken.getCompoundTag("tag").setString("Items", "not an inventory");
                }
                if (error.equals("badtag")) {
                    entry.setString("tag", "not item data");
                }
                if (error.equals("badcaps")) {
                    entry.setString("ForgeCaps", "not capability data");
                }

                NBTTagCompound before = broken.copy();
                boolean stopped = false;

                try {
                    fixer.fixTagCompound(broken);
                } catch (IllegalStateException expected) {
                    stopped = true;
                }

                require(stopped && broken.equals(before), "atomic rejection " + error);
            }
        }
    }

    private static void require(boolean condition, String label) {
        checks++;

        if (!condition) {
            throw new AssertionError("Flower basket: " + label);
        }
    }
}
