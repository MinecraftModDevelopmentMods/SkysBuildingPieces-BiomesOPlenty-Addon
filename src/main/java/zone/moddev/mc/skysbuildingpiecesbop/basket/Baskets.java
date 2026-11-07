package zone.moddev.mc.skysbuildingpiecesbop.basket;

import net.minecraft.nbt.CompoundTag;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import zone.moddev.mc.skysbuildingpiecesbop.SkysBuildingPiecesBop;

public final class Baskets {
    // Forge opens these registries after mod construction.
    public static BasketItem ITEM;
    public static BasketItem HOLDER;
    public static MenuType<BasketContainer> TYPE;

    private Baskets() {
    }

    public static void registerRecovery() {
        zone.moddev.mc.skysbuildingpiecesbop.LegacyItemRecovery fix = new zone.moddev.mc.skysbuildingpiecesbop.LegacyItemRecovery();

        zone.moddev.mc.skysbuildingpieces.legacy.HistoricalItems.register(SkysBuildingPiecesBop.ID, fix::isRelevant,
                fix::fixTagCompound);
    }

    @SubscribeEvent
    public static void items(RegisterEvent event) {
        ITEM = new BasketItem(false);
        HOLDER = new BasketItem(true);
        net.minecraft.core.Registry.register(BuiltInRegistries.ITEM, ResourceLocation.parse(BasketData.ID), ITEM);
        net.minecraft.core.Registry.register(BuiltInRegistries.ITEM, ResourceLocation.parse(BasketData.HOLDER), HOLDER);
    }

    public static void containers(RegisterEvent event) {
        TYPE = IMenuTypeExtension.create(BasketContainer::new);
        net.minecraft.core.Registry.register(BuiltInRegistries.MENU, ResourceLocation.fromNamespaceAndPath(SkysBuildingPiecesBop.ID, "flower_basket"), TYPE);
    }

    public static void creativeContents(BuildCreativeModeTabContentsEvent event) {
        if (CreativeModeTabs.TOOLS_AND_UTILITIES.equals(event.getTabKey())) {
            event.accept(ITEM);
        }
    }

    @SubscribeEvent
    public static void pickup(ItemEntityPickupEvent.Pre event) {
        Player player = event.getPlayer();

        if (player.level().isClientSide || event.canPickup() == TriState.FALSE) {
            return;
        }

        ItemEntity entity = event.getItemEntity();
        ItemStack incoming = entity.getItem();

        if (!BasketInventory.accepts(incoming)) {
            return;
        }
        // In 1.19 getOwner identifies the thrower, while the saved Owner UUID is
        // the exclusive pickup target. Follow the same target check as vanilla.
        CompoundTag pickupData = entity.saveWithoutId(new CompoundTag());
        if (entity.hasPickUpDelay()
                || pickupData.hasUUID("Owner") && !pickupData.getUUID("Owner").equals(player.getUUID())) {
            return;
        }

        ItemStack first = ItemStack.EMPTY;

        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof BasketItem) {
                first = stack;
                break;
            }
        }
        if (first.isEmpty()) {
            return;
        }

        BasketInventory inventory = player.containerMenu instanceof BasketContainer
                && ((BasketContainer) player.containerMenu).basket.owner() == first
                        ? ((BasketContainer) player.containerMenu).basket
                        : new BasketInventory(first, player.level().registryAccess());
        ItemStack remainder = inventory.insert(incoming);
        int accepted = incoming.getCount() - remainder.getCount();

        if (accepted == 0) {
            return;
        }

        // The pickup event shares the live stack with vanilla. Leave any remainder
        // there so ordinary pickup can continue without reading a second stack.
        player.awardStat(net.minecraft.stats.Stats.ITEM_PICKED_UP.get(incoming.getItem()), accepted);
        player.take(entity, accepted);
        incoming.shrink(accepted);
        if (incoming.isEmpty()) {
            event.setCanPickup(TriState.FALSE);
            entity.discard();
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }
}
