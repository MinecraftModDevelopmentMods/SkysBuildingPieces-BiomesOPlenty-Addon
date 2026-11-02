package zone.moddev.mc.skysbuildingpiecesbop.basket;

import net.minecraft.nbt.CompoundTag;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
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
    public static void items(RegistryEvent.Register<Item> event) {
        ITEM = new BasketItem(false);
        HOLDER = new BasketItem(true);
        event.getRegistry().registerAll(ITEM, HOLDER);
    }

    public static void containers(RegistryEvent.Register<MenuType<?>> event) {
        TYPE = IForgeMenuType.create(BasketContainer::new);
        TYPE.setRegistryName(SkysBuildingPiecesBop.ID, "flower_basket");
        event.getRegistry().register(TYPE);
    }

    @SubscribeEvent
    public static void missing(RegistryEvent.MissingMappings<Item> event) {
        for (RegistryEvent.MissingMappings.Mapping<Item> mapping : event.getAllMappings()) {
            if (mapping.key.equals(new ResourceLocation(BasketData.OLD))) {
                mapping.remap(HOLDER);
            }
        }
    }

    @SubscribeEvent
    public static void pickup(EntityItemPickupEvent event) {
        Player player = event.getPlayer();

        if (player.level.isClientSide || event.isCanceled()) {
            return;
        }

        ItemEntity entity = event.getItem();
        ItemStack incoming = entity.getItem();

        if (!BasketInventory.accepts(incoming)) {
            return;
        }
        // Read the saved age on the server without depending on the client-only accessor.
        if (entity.hasPickUpDelay()
                || entity.getOwner() != null && !entity.getOwner().equals(player.getUUID()) && entity.lifespan
                        - entity.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getShort("Age") > 200) {
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
