package zone.moddev.mc.skysbuildingpiecesbop.basket;

import net.minecraft.nbt.CompoundTag;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
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
        ForgeRegistries.ITEMS.register(new ResourceLocation(BasketData.ID), ITEM);
        ForgeRegistries.ITEMS.register(new ResourceLocation(BasketData.HOLDER), HOLDER);
    }

    public static void containers(RegisterEvent event) {
        TYPE = IForgeMenuType.create(BasketContainer::new);
        ForgeRegistries.MENU_TYPES.register(new ResourceLocation(SkysBuildingPiecesBop.ID, "flower_basket"), TYPE);
    }

    @SubscribeEvent
    public static void missing(MissingMappingsEvent event) {
        for (MissingMappingsEvent.Mapping<Item> mapping : event.getAllMappings(ForgeRegistries.Keys.ITEMS)) {
            if (mapping.getKey().equals(new ResourceLocation(BasketData.OLD))) {
                mapping.remap(HOLDER);
            }
        }
    }

    public static void creativeContents(BuildCreativeModeTabContentsEvent event) {
        if (CreativeModeTabs.TOOLS_AND_UTILITIES.equals(event.getTabKey())) {
            event.accept(ITEM);
        }
    }

    @SubscribeEvent
    public static void pickup(EntityItemPickupEvent event) {
        Player player = event.getEntity();

        if (player.level().isClientSide || event.isCanceled()) {
            return;
        }

        ItemEntity entity = event.getItem();
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
                        : new BasketInventory(first);
        ItemStack remainder = inventory.insert(incoming);
        int accepted = incoming.getCount() - remainder.getCount();

        if (accepted == 0) {
            return;
        }

        // Vanilla keeps the original stack reference across this event. Finish this pickup here and let a later
        // collision collect any remainder.
        event.setCanceled(true);

        ItemStack pickedUp = incoming.copy();

        pickedUp.setCount(accepted);
        player.awardStat(net.minecraft.stats.Stats.ITEM_PICKED_UP.get(incoming.getItem()), accepted);

        if (remainder.isEmpty()) {
            player.take(entity, accepted);
            entity.discard();
        } else {
            entity.setItem(remainder);
        }

        net.minecraftforge.event.ForgeEventFactory.firePlayerItemPickupEvent(player, entity, pickedUp);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }
}
