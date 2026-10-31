package zone.moddev.mc.skysbuildingpiecesbop.probe;

import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.Hand;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import zone.moddev.mc.skysbuildingpiecesbop.basket.*;

/** Real item, container, pickup and raw-storage checks; never shipped in the mod. */
public final class BasketChecks {
    private static int checks;

    public static void run(ServerWorld world) {
        checks = 0;

        ServerPlayerEntity player = FakePlayerFactory.getMinecraft(world);

        player.inventory.clear();
        player.inventory.currentItem = 0;

        try {
            net.minecraft.inventory.CraftingInventory crafting = new net.minecraft.inventory.CraftingInventory(
                    new net.minecraft.inventory.container.Container(null, 0) {
                        @Override
                        public boolean canInteractWith(net.minecraft.entity.player.PlayerEntity player) {
                            return true;
                        }
                    }, 3, 3);

            for (int slot : new int[] { 1, 3, 5, 6, 7, 8 }) {
                crafting.setInventorySlotContents(slot, new ItemStack(Items.STICK));
            }

            net.minecraft.item.crafting.IRecipe<net.minecraft.inventory.CraftingInventory> recipe = world
                    .getRecipeManager().getRecipe(net.minecraft.item.crafting.IRecipeType.CRAFTING, crafting, world)
                    .orElse(null);

            require(recipe != null && recipe.getCraftingResult(crafting).getItem() == Baskets.ITEM
                    && recipe.getCraftingResult(crafting).getCount() == 1, "real six-stick crafting recipe");
            require(Baskets.ITEM.getItemStackLimit(new ItemStack(Baskets.ITEM)) == 1, "one basket per stack");
            require(Baskets.HOLDER.getGroup() == null, "hidden recovery item");
            require(BasketInventory.accepts(new ItemStack(Blocks.OAK_SAPLING)), "saplings accepted");
            require(BasketInventory.accepts(new ItemStack(Blocks.OAK_LEAVES)), "leaves accepted");
            require(!BasketInventory.accepts(new ItemStack(Items.DIAMOND)), "non-plants rejected");
            require(BasketInventory.accepts(new ItemStack(Items.WHEAT_SEEDS)),
                    "native crop seed block-items retain plant eligibility");
            require(!BasketInventory.accepts(new ItemStack(Baskets.ITEM)), "no nested baskets");

            ItemStack basket = new ItemStack(Baskets.ITEM);

            basket.setDisplayName(new net.minecraft.util.text.StringTextComponent("Test plants"));
            basket.getTag().putInt("unrelated", 19);

            BasketInventory inventory = new BasketInventory(basket);

            require(inventory.insert(new ItemStack(Blocks.OAK_SAPLING, 60)).isEmpty(), "initial insert");
            require(inventory.insert(new ItemStack(Blocks.OAK_SAPLING, 12)).isEmpty(), "merge and spill to next slot");
            require(inventory.getStackInSlot(0).getCount() == 64 && inventory.getStackInSlot(1).getCount() == 8,
                    "merge before empty slots");
            require(basket.getTag().getInt("unrelated") == 19
                    && basket.getDisplayName().getString().equals("Test plants"), "custom data retained");
            require(new BasketInventory(basket).getStackInSlot(1).getCount() == 8, "storage reload");

            for (int entryIndex = 0; entryIndex < 18; entryIndex++) {
                inventory.setInventorySlotContents(entryIndex,
                        new ItemStack(Blocks.OAK_SAPLING, entryIndex == 17 ? 60 : 64));
            }

            ItemStack rest = inventory.insert(new ItemStack(Blocks.OAK_SAPLING, 12));

            require(rest.getCount() == 8, "partial insertion retains remainder");
            player.inventory.setInventorySlotContents(0, basket);

            for (Hand hand : Hand.values()) {
                int index = hand == Hand.MAIN_HAND ? 0 : 40;

                player.inventory.setInventorySlotContents(0, ItemStack.EMPTY);
                player.inventory.setInventorySlotContents(index, basket);

                BasketContainer container = new BasketContainer(1, player, hand);

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

                player.inventory.setInventorySlotContents(9, new ItemStack(Blocks.OAK_SAPLING, 5));
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

            BasketContainer editor = new BasketContainer(1, player, Hand.MAIN_HAND);

            player.openContainer = editor;
            player.inventory.setInventorySlotContents(9, new ItemStack(Blocks.OAK_SAPLING, 5));
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
            editor.basket.insert(new ItemStack(Blocks.OAK_SAPLING, 11));
            editor.onContainerClosed(player);
            require(new BasketInventory(editable).getStackInSlot(0).getCount() == 11,
                    "close persists contents immediately");
            player.inventory.clear();

            ItemStack collected = new ItemStack(Baskets.ITEM);

            player.inventory.setInventorySlotContents(0, collected);
            player.inventory.setInventorySlotContents(1, new ItemStack(Baskets.ITEM));
            player.openContainer = player.container;

            ItemEntity entity = new ItemEntity(world, 0, 100, 0, new ItemStack(Blocks.OAK_SAPLING, 7));
            EntityItemPickupEvent event = new EntityItemPickupEvent(player, entity);

            Baskets.pickup(event);
            require(event.isCanceled() && !entity.isAlive(), "complete pickup consumed once");
            require(new BasketInventory(collected).getStackInSlot(0).getCount() == 7, "first basket collects");
            require(!player.inventory.getStackInSlot(1).hasTag(), "later basket unchanged");

            BasketInventory full = new BasketInventory(collected);

            for (int entryIndex = 0; entryIndex < 18; entryIndex++) {
                full.setInventorySlotContents(entryIndex, new ItemStack(Blocks.OAK_SAPLING, entryIndex == 0 ? 61 : 64));
            }

            entity = new ItemEntity(world, 0, 100, 0, new ItemStack(Blocks.OAK_SAPLING, 9));
            event = new EntityItemPickupEvent(player, entity);
            Baskets.pickup(event);
            require(event.isCanceled() && entity.isAlive() && entity.getItem().getCount() == 6,
                    "partial pickup leaves exact remainder for the next collision");
            // Exercise the caller too: vanilla retains the stack reference from before the pickup event, so replacing
            // only ItemEntity's stack is unsafe.
            player.inventory.clear();
            player.inventory.setInventorySlotContents(0, collected);
            player.abilities.isCreativeMode = false;
            full = new BasketInventory(collected);

            for (int entryIndex = 0; entryIndex < 18; entryIndex++) {
                full.setInventorySlotContents(entryIndex, new ItemStack(Blocks.OAK_SAPLING, entryIndex == 0 ? 61 : 64));
            }

            entity = new ItemEntity(world, 0, 100, 0, new ItemStack(Blocks.OAK_SAPLING, 9));
            entity.onCollideWithPlayer(player);

            if (entity.isAlive()) {
                entity.onCollideWithPlayer(player);
            }

            int loose = 0;

            for (ItemStack stack : player.inventory.mainInventory) {
                if (stack.getItem() == net.minecraft.item.Item.getItemFromBlock(Blocks.OAK_SAPLING)) {
                    loose += stack.getCount();
                }
            }

            require(new BasketInventory(collected).getStackInSlot(0).getCount() == 64 && loose == 6
                    && !entity.isAlive(), "real partial pickup conserves all nine items");

            for (int entryIndex = 1; entryIndex < player.inventory.mainInventory.size(); entryIndex++) {
                player.inventory.setInventorySlotContents(entryIndex, new ItemStack(Blocks.STONE, 64));
            }

            full = new BasketInventory(collected);
            full.setInventorySlotContents(0, new ItemStack(Blocks.OAK_SAPLING, 61));
            entity = new ItemEntity(world, 0, 100, 0, new ItemStack(Blocks.OAK_SAPLING, 9));
            entity.onCollideWithPlayer(player);
            entity.onCollideWithPlayer(player);
            require(entity.isAlive() && entity.getItem().getCount() == 6
                    && new BasketInventory(collected).getStackInSlot(0).getCount() == 64,
                    "real full inventory retains six ground items");
            player.inventory.clear();

            ItemStack ownedBasket = new ItemStack(Baskets.ITEM);

            player.inventory.setInventorySlotContents(0, ownedBasket);
            entity = new ItemEntity(world, 0, 100, 0, new ItemStack(Blocks.OAK_SAPLING, 4));
            entity.setOwnerId(new java.util.UUID(1, 2));
            entity.onCollideWithPlayer(player);
            require(entity.isAlive() && entity.getItem().getCount() == 4 && new BasketInventory(ownedBasket).isEmpty(),
                    "respect vanilla exclusive pickup ownership");
            entity.setOwnerId(player.getUniqueID());
            entity.onCollideWithPlayer(player);
            require(!entity.isAlive() && new BasketInventory(ownedBasket).getStackInSlot(0).getCount() == 4,
                    "the drop owner can collect plants");
            rawRecovery();
            System.out.println("BOP_FLOWER_BASKET_PASS checks=" + checks
                    + " slots=18 collection=true NBT=preserved reload=idempotent");
        } finally {
            player.inventory.clear();
            player.openContainer = player.container;
        }
    }

    private static void rawRecovery() {
        BasketData fixer = new BasketData();

        for (String registryId : new String[] { BasketData.OLD, BasketData.HOLDER, BasketData.ID }) {
            CompoundNBT stack = new ItemStack(Baskets.ITEM).write(new CompoundNBT());

            stack.putString("id", registryId);

            CompoundNBT tag = new CompoundNBT();

            tag.putBoolean("BasketOpen", true);
            tag.putString("CustomName", "Archived plants");
            tag.putInt("other", 77);

            ListNBT list = new ListNBT();
            CompoundNBT child = new ItemStack(Blocks.BIRCH_SAPLING, 53).write(new CompoundNBT());

            child.putByte("Slot", (byte) 17);
            list.add(child);
            tag.put("Items", list);
            stack.put("tag", tag);
            fixer.fixTagCompound(stack);
            require(stack.getString("id").equals(BasketData.ID), "canonical item ID");
            require(!stack.getCompound("tag").getBoolean("BasketOpen")
                    && stack.getCompound("tag").getInt("other") == 77, "clear only stale open state");
            require(stack.getCompound("tag").getList("Items", 10).getCompound(0).equals(child),
                    "raw child NBT unchanged");

            CompoundNBT once = stack.copy();

            fixer.fixTagCompound(stack);
            require(stack.equals(once), "raw reload idempotence");
            require(new BasketInventory(ItemStack.read(stack)).getStackInSlot(17).getCount() == 53,
                    "deserialization preserved contents");

            for (String carrier : new String[] { "Inventory", "EnderItems", "Items", "Equipment", "CustomHandler" }) {
                CompoundNBT root = new CompoundNBT();
                ListNBT listCarrier = new ListNBT();
                CompoundNBT historical = once.copy();

                historical.putString("id", BasketData.OLD);
                listCarrier.add(historical);
                root.put(carrier, listCarrier);
                fixer.fixTagCompound(root);
                require(root.getList(carrier, 10).getCompound(0).getString("id").equals(BasketData.ID),
                        "nested carrier " + carrier);
            }
            for (String error : new String[] { "unknown", "duplicate", "badslot", "badcount", "fractional", "wronglist",
                    "badtag", "badcaps" }) {
                CompoundNBT broken = once.copy();
                CompoundNBT entry = broken.getCompound("tag").getList("Items", 10).getCompound(0);

                if (error.equals("unknown")) {
                    entry.putString("id", "missing_test:flower");
                }
                if (error.equals("duplicate")) {
                    broken.getCompound("tag").getList("Items", 10).add(entry.copy());
                }
                if (error.equals("badslot")) {
                    entry.putByte("Slot", (byte) 18);
                }
                if (error.equals("badcount")) {
                    entry.putByte("Count", (byte) 65);
                }
                if (error.equals("fractional")) {
                    entry.putFloat("Slot", .5f);
                }
                if (error.equals("wronglist")) {
                    broken.getCompound("tag").putString("Items", "not an inventory");
                }
                if (error.equals("badtag")) {
                    entry.putString("tag", "not item data");
                }
                if (error.equals("badcaps")) {
                    entry.putString("ForgeCaps", "not capability data");
                }

                CompoundNBT before = broken.copy();
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
