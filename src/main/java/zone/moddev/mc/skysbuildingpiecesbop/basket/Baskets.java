package zone.moddev.mc.skysbuildingpiecesbop.basket;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.resources.Identifier;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import zone.moddev.mc.skysbuildingpiecesbop.SkysBuildingPiecesBop;
import zone.moddev.mc.skysbuildingpiecesbop.mixin.ItemPickupTarget;

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

    public static void items(RegisterEvent event) {
        ITEM = new BasketItem(false);
        HOLDER = new BasketItem(true);
        ForgeRegistries.ITEMS.register(Identifier.parse(BasketData.ID), ITEM);
        ForgeRegistries.ITEMS.register(Identifier.parse(BasketData.HOLDER), HOLDER);
    }

    public static void containers(RegisterEvent event) {
        TYPE = IForgeMenuType.create(BasketContainer::new);
        ForgeRegistries.MENU_TYPES.register(Identifier.fromNamespaceAndPath(SkysBuildingPiecesBop.ID, "flower_basket"), TYPE);
    }

    public static void missing(MissingMappingsEvent event) {
        for (MissingMappingsEvent.Mapping<Item> mapping : event.getAllMappings(ForgeRegistries.Keys.ITEMS)) {
            if (mapping.getKey().equals(Identifier.parse(BasketData.OLD))) {
                mapping.remap(HOLDER);
            }
        }
    }

    public static void creativeContents(BuildCreativeModeTabContentsEvent event) {
        if (CreativeModeTabs.TOOLS_AND_UTILITIES.equals(event.getTabKey())) {
            event.accept(ITEM);
        }
    }

    public static boolean pickup(EntityItemPickupEvent event) {
        Player player = event.getEntity();

        if (player.level().isClientSide()) {
            return false;
        }

        ItemEntity entity = event.getItem();
        ItemStack incoming = entity.getItem();

        if (!BasketInventory.accepts(incoming)) {
            return false;
        }
        // Forge posts the event before vanilla checks who can pick up the drop.
        var pickupTarget = ((ItemPickupTarget) entity).skysbuildingpiecesbop$pickupTarget();
        if (entity.hasPickUpDelay() || pickupTarget != null && !pickupTarget.equals(player.getUUID())) {
            return false;
        }

        ItemStack first = ItemStack.EMPTY;

        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.getItem() instanceof BasketItem) {
                first = stack;
                break;
            }
        }
        if (first.isEmpty()) {
            return false;
        }

        BasketInventory inventory = player.containerMenu instanceof BasketContainer
                && ((BasketContainer) player.containerMenu).basket.owner() == first
                        ? ((BasketContainer) player.containerMenu).basket
                        : new BasketInventory(first, player.level().registryAccess());
        ItemStack remainder = inventory.insert(incoming);
        int accepted = incoming.getCount() - remainder.getCount();

        if (accepted == 0) {
            return false;
        }

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
        // Cancel vanilla's current pickup attempt: it still holds the original stack.
        return true;
    }
}
