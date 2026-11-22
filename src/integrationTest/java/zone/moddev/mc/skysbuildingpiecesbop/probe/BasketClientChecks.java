package zone.moddev.mc.skysbuildingpiecesbop.probe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.event.TickEvent;

import zone.moddev.mc.skysbuildingpiecesbop.basket.*;

/** Verify both basket variants against the real client atlas before the client probe exits. */
public final class BasketClientChecks {
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger();

    private static boolean checked;

    @SubscribeEvent(
            priority = EventPriority.HIGHEST)
    public static void tick(TickEvent.ClientTickEvent event) {
        if (checked || event.phase != TickEvent.Phase.END
                || !System.getProperty("skysbuildingpieces.integrationPhase", "").startsWith("client")) {
            return;
        }

        checked = true;

        Minecraft minecraft = Minecraft.getInstance();
        BakedModel previous = null;

        for (net.minecraft.world.item.Item basket : new net.minecraft.world.item.Item[] {Baskets.ITEM, Baskets.HOLDER}) {
            previous = null;
            for (boolean filled : new boolean[] { false, true }) {
                ItemStack stack = new ItemStack(basket);

                if (filled) {
                    CompoundTag tag = new CompoundTag();
                    ListTag entries = new ListTag();
                    CompoundTag child = new ItemStack(net.minecraft.world.level.block.Blocks.OAK_SAPLING).save(new CompoundTag());

                    child.putByte("Slot", (byte) 0);
                    entries.add(child);
                    tag.put("Items", entries);
                    stack.setTag(tag);
                }

                BakedModel model = minecraft.getItemRenderer().getModel(stack, null, null, 0);

                if (model == minecraft.getItemRenderer().getItemModelShaper().getModelManager().getMissingModel()
                        || model.getParticleIcon().contents().name().toString().contains("missingno")) {
                    throw new AssertionError("Missing basket model");
                }
                if (filled && model == previous) {
                    throw new AssertionError("Filled basket override did not change the model: "
                            + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(basket));
                }

                previous = model;
            }
        }
        for (net.minecraft.world.item.Item holder : zone.moddev.mc.skysbuildingpiecesbop.LegacyItemRecovery.HOLDERS) {
            BakedModel model = minecraft.getItemRenderer().getModel(new ItemStack(holder), null,
                    null, 0);

            if (model == minecraft.getItemRenderer().getItemModelShaper().getModelManager().getMissingModel()
                    || model.getParticleIcon().contents().name().toString().contains("missingno")) {
                throw new AssertionError("Missing retired-item holder model");
            }
        }

        LOGGER.info("BOP_BASKET_CLIENT_PASS empty=true filled=true atlas=true retired_item_models=3");
    }
}
