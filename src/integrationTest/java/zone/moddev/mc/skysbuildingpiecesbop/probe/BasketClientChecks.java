package zone.moddev.mc.skysbuildingpiecesbop.probe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.event.TickEvent;

import zone.moddev.mc.skysbuildingpiecesbop.basket.*;

/** Verify both basket variants against the real client atlas before the client probe exits. */
public final class BasketClientChecks {
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
        IBakedModel previous = null;

        for (boolean filled : new boolean[] { false, true }) {
            ItemStack stack = new ItemStack(Baskets.ITEM);

            if (filled) {
                CompoundNBT tag = new CompoundNBT();
                ListNBT entries = new ListNBT();
                CompoundNBT child = new ItemStack(net.minecraft.block.Blocks.OAK_SAPLING).write(new CompoundNBT());

                child.putByte("Slot", (byte) 0);
                entries.add(child);
                tag.put("Items", entries);
                stack.setTag(tag);
            }

            IBakedModel model = minecraft.getItemRenderer().getItemModelWithOverrides(stack, null, null);

            if (model == minecraft.getItemRenderer().getItemModelMesher().getModelManager().getMissingModel()
                    || model.getParticleTexture().getName().toString().contains("missingno")) {
                throw new AssertionError("Missing basket model");
            }
            if (filled && model == previous) {
                throw new AssertionError("Filled basket override did not change the model");
            }

            previous = model;
        }
        for (net.minecraft.item.Item holder : zone.moddev.mc.skysbuildingpiecesbop.LegacyItemRecovery.HOLDERS) {
            IBakedModel model = minecraft.getItemRenderer().getItemModelWithOverrides(new ItemStack(holder), null,
                    null);

            if (model == minecraft.getItemRenderer().getItemModelMesher().getModelManager().getMissingModel()
                    || model.getParticleTexture().getName().toString().contains("missingno")) {
                throw new AssertionError("Missing retired-item holder model");
            }
        }

        System.out.println("BOP_BASKET_CLIENT_PASS empty=true filled=true atlas=true retired_item_models=3");
    }
}
