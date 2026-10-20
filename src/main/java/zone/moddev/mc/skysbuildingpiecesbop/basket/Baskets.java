package zone.moddev.mc.skysbuildingpiecesbop.basket;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.datafix.FixTypes;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.common.util.ModFixs;
import zone.moddev.mc.skysbuildingpiecesbop.SkysBuildingPiecesBop;

@Mod.EventBusSubscriber(
        modid = SkysBuildingPiecesBop.ID)
public final class Baskets {
    public static final BasketItem ITEM = new BasketItem(false), HOLDER = new BasketItem(true);

    private Baskets() {
    }

    public static void registerRecovery() {
        ModFixs fixes = FMLCommonHandler.instance().getDataFixer().init(SkysBuildingPiecesBop.ID, 2);
        zone.moddev.mc.skysbuildingpiecesbop.LegacyItemRecovery fix = new zone.moddev.mc.skysbuildingpiecesbop.LegacyItemRecovery();

        for (FixTypes type : new FixTypes[] { FixTypes.CHUNK, FixTypes.PLAYER, FixTypes.ENTITY, FixTypes.BLOCK_ENTITY,
                FixTypes.ITEM_INSTANCE }) {
            fixes.registerFix(type, type == FixTypes.ITEM_INSTANCE ? fix.itemInstanceFix() : fix);
        }
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
        EntityPlayer player = event.getEntityPlayer();

        if (player.world.isRemote || event.isCanceled()) {
            return;
        }

        EntityItem entity = event.getItem();
        ItemStack incoming = entity.getItem();

        if (!BasketInventory.accepts(incoming)) {
            return;
        }
        // getAge is client-only in 1.12; the saved age is also available on a server.
        if (entity.getOwner() != null && !entity.getOwner().equals(player.getName())
                && entity.lifespan - entity.writeToNBT(new net.minecraft.nbt.NBTTagCompound()).getShort("Age") > 200) {
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
        player.addStat(net.minecraft.stats.StatList.getObjectsPickedUpStats(incoming.getItem()), accepted);

        if (remainder.isEmpty()) {
            player.onItemPickup(entity, accepted);
            entity.setDead();
        } else {
            entity.setItem(remainder);
        }

        FMLCommonHandler.instance().firePlayerItemPickupEvent(player, entity, pickedUp);
        player.inventory.markDirty();
        player.openContainer.detectAndSendChanges();
    }
}
