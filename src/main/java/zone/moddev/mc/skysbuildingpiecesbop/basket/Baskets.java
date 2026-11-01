package zone.moddev.mc.skysbuildingpiecesbop.basket;

import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.inventory.container.ContainerType;
import net.minecraftforge.common.extensions.IForgeContainerType;
import zone.moddev.mc.skysbuildingpiecesbop.SkysBuildingPiecesBop;

public final class Baskets {
    public static final BasketItem ITEM = new BasketItem(false), HOLDER = new BasketItem(true);
    public static final ContainerType<BasketContainer> TYPE = IForgeContainerType.create(BasketContainer::new);
    static {
        TYPE.setRegistryName(SkysBuildingPiecesBop.ID, "flower_basket");
    }

    private Baskets() {
    }

    public static void registerRecovery() {
        zone.moddev.mc.skysbuildingpiecesbop.LegacyItemRecovery fix = new zone.moddev.mc.skysbuildingpiecesbop.LegacyItemRecovery();

        zone.moddev.mc.skysbuildingpieces.legacy.HistoricalItems.register(SkysBuildingPiecesBop.ID, fix::isRelevant,
                fix::fixTagCompound);
    }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> event) {
        event.getRegistry().registerAll(ITEM, HOLDER);
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
        PlayerEntity player = event.getPlayer();

        if (player.world.isRemote || event.isCanceled()) {
            return;
        }

        ItemEntity entity = event.getItem();
        ItemStack incoming = entity.getItem();

        if (!BasketInventory.accepts(incoming)) {
            return;
        }
        // Read the saved age on the server without depending on the client-only accessor.
        if (entity.cannotPickup()
                || entity.getOwnerId() != null && !entity.getOwnerId().equals(player.getUniqueID()) && entity.lifespan
                        - entity.writeWithoutTypeId(new net.minecraft.nbt.CompoundNBT()).getShort("Age") > 200) {
            return;
        }

        ItemStack first = ItemStack.EMPTY;

        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack.getItem() instanceof BasketItem) {
                first = stack;
                break;
            }
        }
        if (first.isEmpty()) {
            return;
        }

        BasketInventory inventory = player.openContainer instanceof BasketContainer
                && ((BasketContainer) player.openContainer).basket.owner() == first
                        ? ((BasketContainer) player.openContainer).basket
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
        player.addStat(net.minecraft.stats.Stats.ITEM_PICKED_UP.get(incoming.getItem()), accepted);

        if (remainder.isEmpty()) {
            player.onItemPickup(entity, accepted);
            entity.remove();
        } else {
            entity.setItem(remainder);
        }

        net.minecraftforge.fml.hooks.BasicEventHooks.firePlayerItemPickupEvent(player, entity, pickedUp);
        player.inventory.markDirty();
        player.openContainer.detectAndSendChanges();
    }
}
