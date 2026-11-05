package zone.moddev.mc.skysbuildingpiecesbop.probe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import zone.moddev.mc.skysbuildingpiecesbop.basket.*;

/** Real item, container, pickup and raw-storage checks; never shipped in the mod. */
public final class BasketChecks {
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger();

    private static int checks;

    public static boolean hidden(net.minecraft.world.item.Item item, ServerLevel world) {
        net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(
                world.enabledFeatures(), true, world.registryAccess());
        return net.minecraft.world.item.CreativeModeTabs.allTabs().stream()
                .flatMap(tab -> tab.getDisplayItems().stream()).noneMatch(stack -> stack.is(item));
    }

    public static void run(ServerLevel world) {
        checks = 0;

        ServerPlayer player = BopProbeItems.player(world);

        player.getInventory().clearContent();
        player.getInventory().selected = 0;

        try {
            net.minecraft.world.inventory.CraftingContainer crafting = new net.minecraft.world.inventory.TransientCraftingContainer(
                    new net.minecraft.world.inventory.AbstractContainerMenu(null, 0) {
                        @Override
                        public ItemStack quickMoveStack(Player player, int slot) {
                            return ItemStack.EMPTY;
                        }

                        @Override
                        public boolean stillValid(net.minecraft.world.entity.player.Player player) {
                            return true;
                        }
                    }, 3, 3);

            for (int slot : new int[] { 1, 3, 5, 6, 7, 8 }) {
                crafting.setItem(slot, new ItemStack(Items.STICK));
            }

            net.minecraft.world.item.crafting.CraftingRecipe recipe = world
                    .getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, crafting.asCraftInput(), world)
                    .map(net.minecraft.world.item.crafting.RecipeHolder::value).orElse(null);

            require(recipe != null && recipe.assemble(crafting.asCraftInput(), world.registryAccess()).getItem() == Baskets.ITEM
                    && recipe.assemble(crafting.asCraftInput(), world.registryAccess()).getCount() == 1, "real six-stick crafting recipe");
            require(new ItemStack(Baskets.ITEM).getMaxStackSize() == 1, "one basket per stack");
            if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
                require(hidden(Baskets.HOLDER, world), "hidden recovery item");
            }
            require(BasketInventory.accepts(new ItemStack(Blocks.OAK_SAPLING)), "saplings accepted");
            require(BasketInventory.accepts(new ItemStack(Blocks.OAK_LEAVES)), "leaves accepted");
            require(BasketInventory.accepts(new ItemStack(Blocks.CACTUS)), "cactus accepted");
            require(BasketInventory.accepts(new ItemStack(Blocks.SUGAR_CANE)), "sugar cane accepted");
            require(BasketInventory.accepts(new ItemStack(Blocks.BAMBOO)), "bamboo accepted");
            require(!BasketInventory.accepts(new ItemStack(Blocks.STONE)), "ordinary blocks rejected");
            require(!BasketInventory.accepts(new ItemStack(Items.DIAMOND)), "non-plants rejected");
            require(BasketInventory.accepts(new ItemStack(Items.WHEAT_SEEDS)),
                    "native crop seed block-items retain plant eligibility");
            require(!BasketInventory.accepts(new ItemStack(Baskets.ITEM)), "no nested baskets");

            ItemStack basket = new ItemStack(Baskets.ITEM);

            BopProbeItems.name(basket, "Test plants");
            CompoundTag custom = new CompoundTag();
            custom.putInt("unrelated", 19);
            BopProbeItems.setCustom(basket, custom);

            BasketInventory inventory = new BasketInventory(basket, BopProbeItems.registries());

            require(inventory.insert(new ItemStack(Blocks.OAK_SAPLING, 60)).isEmpty(), "initial insert");
            require(inventory.insert(new ItemStack(Blocks.OAK_SAPLING, 12)).isEmpty(), "merge and spill to next slot");
            require(inventory.getItem(0).getCount() == 64 && inventory.getItem(1).getCount() == 8,
                    "merge before empty slots");
            require(BopProbeItems.custom(basket).getInt("unrelated") == 19
                    && basket.getHoverName().getString().equals("Test plants"), "custom data retained");
            require(new BasketInventory(basket, BopProbeItems.registries()).getItem(1).getCount() == 8, "storage reload");

            ItemStack customPlant = new ItemStack(Blocks.BIRCH_SAPLING, 3);
            BopProbeItems.name(customPlant, "Orchard saplings");
            customPlant.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 16);
            CompoundTag plantData = new CompoundTag();
            plantData.putString("plot", "orchard");
            BopProbeItems.setCustom(customPlant, plantData);
            inventory.setItem(4, customPlant);
            ItemStack reloadedPlant = new BasketInventory(basket, world.registryAccess()).getItem(4);
            require(ItemStack.matches(customPlant, reloadedPlant) && reloadedPlant.getMaxStackSize() == 16,
                    "component-rich contents survive a save and restore");

            for (int entryIndex = 0; entryIndex < 18; entryIndex++) {
                inventory.setItem(entryIndex,
                        new ItemStack(Blocks.OAK_SAPLING, entryIndex == 17 ? 60 : 64));
            }

            ItemStack rest = inventory.insert(new ItemStack(Blocks.OAK_SAPLING, 12));

            require(rest.getCount() == 8, "partial insertion retains remainder");
            player.getInventory().setItem(0, basket);

            for (InteractionHand hand : InteractionHand.values()) {
                int index = hand == InteractionHand.MAIN_HAND ? 0 : 40;

                player.getInventory().setItem(0, ItemStack.EMPTY);
                player.getInventory().setItem(index, basket);

                BasketContainer container = new BasketContainer(1, player, hand);

                player.containerMenu = container;
                require(container.stillValid(player), "opens exact " + hand);
                require(!Baskets.ITEM.onDroppedByPlayer(basket, player), "throwing owner is blocked before removal");

                int other = index == 40 ? 0 : 40;

                player.getInventory().setItem(index, new ItemStack(Items.STICK));
                player.getInventory().setItem(other, basket);
                require(container.stillValid(player) && player.getInventory().getItem(index) == basket
                        && player.getInventory().getItem(other).getItem() == Items.STICK,
                        "hand-swap packet is reversed without copying");

                if (index == 0) {
                    for (ClickType type : ClickType.values()) {
                        container.clicked(45, 0, type, player);
                        require(player.getInventory().getItem(0) == basket, "owner click locked " + type);
                    }
                }

                player.getInventory().setItem(9, new ItemStack(Blocks.OAK_SAPLING, 5));
                container.clicked(18, index, ClickType.SWAP, player);
                require(player.getInventory().getItem(index) == basket, "swap owner locked");
                player.getInventory().setItem(index, basket.copy());
                require(!container.stillValid(player), "copied stack is not the owner");
                player.getInventory().setItem(index, basket);
                container.removed(player);
            }

            ItemStack editable = new ItemStack(Baskets.ITEM);

            player.getInventory().clearContent();
            player.getInventory().setItem(0, editable);

            BasketContainer editor = new BasketContainer(1, player, InteractionHand.MAIN_HAND);

            player.containerMenu = editor;
            player.getInventory().setItem(9, new ItemStack(Blocks.OAK_SAPLING, 5));
            editor.quickMoveStack(player, 18);
            require(editor.basket.getItem(0).getCount() == 5 && player.getInventory().getItem(9).isEmpty(),
                    "shift-click inserts");
            player.getInventory().setItem(9, new ItemStack(Items.DIAMOND));
            editor.quickMoveStack(player, 18);
            require(editor.basket.getItem(1).isEmpty()
                    && player.getInventory().getItem(9).getItem() == Items.DIAMOND,
                    "shift-click rejects unrelated items");
            editor.quickMoveStack(player, 0);
            require(editor.basket.isEmpty(), "shift-click withdraws");
            editor.basket.setItem(3, new ItemStack(Items.DIAMOND, 2));
            require(new BasketInventory(editable, BopProbeItems.registries()).getItem(3).getCount() == 2,
                    "stored non-plant remains recoverable");
            editor.quickMoveStack(player, 3);
            require(editor.basket.isEmpty(), "stored non-plant can be withdrawn");
            editor.basket.insert(new ItemStack(Blocks.OAK_SAPLING, 11));
            editor.removed(player);
            require(new BasketInventory(editable, BopProbeItems.registries()).getItem(0).getCount() == 11,
                    "close persists contents immediately");
            player.getInventory().clearContent();

            ItemStack collected = new ItemStack(Baskets.ITEM);

            player.getInventory().setItem(0, collected);
            player.getInventory().setItem(1, new ItemStack(Baskets.ITEM));
            player.containerMenu = player.inventoryMenu;

            ItemEntity entity = new ItemEntity(world, 0, 100, 0, new ItemStack(Blocks.OAK_SAPLING, 7));
            ItemEntityPickupEvent.Pre event = new ItemEntityPickupEvent.Pre(player, entity);

            Baskets.pickup(event);
            require(event.canPickup() == net.neoforged.neoforge.common.util.TriState.FALSE && !entity.isAlive(), "complete pickup consumed once");
            require(new BasketInventory(collected, BopProbeItems.registries()).getItem(0).getCount() == 7, "first basket collects");
            require(!BopProbeItems.hasCustom(player.getInventory().getItem(1)), "later basket unchanged");

            BasketInventory full = new BasketInventory(collected, BopProbeItems.registries());

            for (int entryIndex = 0; entryIndex < 18; entryIndex++) {
                full.setItem(entryIndex, new ItemStack(Blocks.OAK_SAPLING, entryIndex == 0 ? 61 : 64));
            }

            entity = new ItemEntity(world, 0, 100, 0, new ItemStack(Blocks.OAK_SAPLING, 9));
            event = new ItemEntityPickupEvent.Pre(player, entity);
            Baskets.pickup(event);
            require(entity.isAlive() && entity.getItem().getCount() == 6,
                    "partial pickup leaves exact remainder for the next collision");
            // Exercise the caller too: vanilla retains the stack reference from before the pickup event, so replacing
            // only ItemEntity's stack is unsafe.
            player.getInventory().clearContent();
            player.getInventory().setItem(0, collected);
            player.getAbilities().instabuild = false;
            full = new BasketInventory(collected, BopProbeItems.registries());

            for (int entryIndex = 0; entryIndex < 18; entryIndex++) {
                full.setItem(entryIndex, new ItemStack(Blocks.OAK_SAPLING, entryIndex == 0 ? 61 : 64));
            }

            entity = new ItemEntity(world, 0, 100, 0, new ItemStack(Blocks.OAK_SAPLING, 9));
            entity.playerTouch(player);

            if (entity.isAlive()) {
                entity.playerTouch(player);
            }

            int loose = 0;

            for (ItemStack stack : player.getInventory().items) {
                if (stack.getItem() == net.minecraft.world.item.Item.byBlock(Blocks.OAK_SAPLING)) {
                    loose += stack.getCount();
                }
            }

            require(new BasketInventory(collected, BopProbeItems.registries()).getItem(0).getCount() == 64 && loose == 6
                    && !entity.isAlive(), "real partial pickup conserves all nine items");

            for (int entryIndex = 1; entryIndex < player.getInventory().items.size(); entryIndex++) {
                player.getInventory().setItem(entryIndex, new ItemStack(Blocks.STONE, 64));
            }

            full = new BasketInventory(collected, BopProbeItems.registries());
            full.setItem(0, new ItemStack(Blocks.OAK_SAPLING, 61));
            entity = new ItemEntity(world, 0, 100, 0, new ItemStack(Blocks.OAK_SAPLING, 9));
            entity.playerTouch(player);
            entity.playerTouch(player);
            require(entity.isAlive() && entity.getItem().getCount() == 6
                    && new BasketInventory(collected, BopProbeItems.registries()).getItem(0).getCount() == 64,
                    "real full inventory retains six ground items");
            player.getInventory().clearContent();

            ItemStack ownedBasket = new ItemStack(Baskets.ITEM);

            player.getInventory().setItem(0, ownedBasket);
            entity = new ItemEntity(world, 0, 100, 0, new ItemStack(Blocks.OAK_SAPLING, 4));
            entity.setTarget(new java.util.UUID(1, 2));
            entity.playerTouch(player);
            require(entity.isAlive() && entity.getItem().getCount() == 4 && new BasketInventory(ownedBasket, BopProbeItems.registries()).isEmpty(),
                    "respect vanilla exclusive pickup ownership");
            entity.setTarget(player.getUUID());
            entity.playerTouch(player);
            require(!entity.isAlive() && new BasketInventory(ownedBasket, BopProbeItems.registries()).getItem(0).getCount() == 4,
                    "the drop owner can collect plants");
            rawRecovery();
            LOGGER.info("BOP_FLOWER_BASKET_PASS checks=" + checks
                    + " slots=18 collection=true NBT=preserved reload=idempotent");
        } finally {
            player.getInventory().clearContent();
            player.containerMenu = player.inventoryMenu;
        }
    }

    private static void rawRecovery() {
        BasketData fixer = new BasketData();

        for (String registryId : new String[] { BasketData.OLD, BasketData.HOLDER, BasketData.ID }) {
            CompoundTag stack = BopProbeItems.legacy(new ItemStack(Baskets.ITEM));

            stack.putString("id", registryId);

            CompoundTag tag = new CompoundTag();

            tag.putBoolean("BasketOpen", true);
            tag.putString("CustomName", "Archived plants");
            tag.putInt("other", 77);

            ListTag list = new ListTag();
            CompoundTag child = BopProbeItems.legacy(new ItemStack(Blocks.BIRCH_SAPLING, 53));

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

            CompoundTag once = stack.copy();

            fixer.fixTagCompound(stack);
            require(stack.equals(once), "raw reload idempotence");
            require(new BasketInventory(BopProbeItems.read(stack), BopProbeItems.registries()).getItem(17).getCount() == 53,
                    "deserialization preserved contents");

            for (String carrier : new String[] { "Inventory", "EnderItems", "Items", "Equipment", "CustomHandler" }) {
                CompoundTag root = new CompoundTag();
                ListTag listCarrier = new ListTag();
                CompoundTag historical = once.copy();

                historical.putString("id", BasketData.OLD);
                listCarrier.add(historical);
                root.put(carrier, listCarrier);
                fixer.fixTagCompound(root);
                require(root.getList(carrier, 10).getCompound(0).getString("id").equals(BasketData.ID),
                        "nested carrier " + carrier);
            }
            for (String error : new String[] { "unknown", "duplicate", "badslot", "badcount", "fractional", "wronglist",
                    "badtag", "badcaps" }) {
                CompoundTag broken = once.copy();
                CompoundTag entry = broken.getCompound("tag").getList("Items", 10).getCompound(0);

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

                CompoundTag before = broken.copy();
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
