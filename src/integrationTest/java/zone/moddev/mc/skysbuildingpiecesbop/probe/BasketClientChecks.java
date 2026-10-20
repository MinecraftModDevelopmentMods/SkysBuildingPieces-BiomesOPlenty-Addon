package zone.moddev.mc.skysbuildingpiecesbop.probe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
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

        Minecraft minecraft = Minecraft.getMinecraft();
        IBakedModel previous = null;

        for (boolean filled : new boolean[] { false, true }) {
            ItemStack stack = new ItemStack(Baskets.ITEM);

            if (filled) {
                NBTTagCompound tag = new NBTTagCompound();
                NBTTagList entries = new NBTTagList();
                NBTTagCompound child = new ItemStack(net.minecraft.init.Blocks.SAPLING)
                        .writeToNBT(new NBTTagCompound());

                child.setByte("Slot", (byte) 0);
                entries.appendTag(child);
                tag.setTag("Items", entries);
                stack.setTagCompound(tag);
            }

            IBakedModel model = minecraft.getRenderItem().getItemModelWithOverrides(stack, null, null);

            if (model == minecraft.getRenderItem().getItemModelMesher().getModelManager().getMissingModel()
                    || model.getParticleTexture().getIconName().contains("missingno")) {
                throw new AssertionError("Missing basket model");
            }
            if (filled && model == previous) {
                throw new AssertionError("Filled basket override did not change the model");
            }

            previous = model;
        }
        for (net.minecraft.item.Item holder : zone.moddev.mc.skysbuildingpiecesbop.LegacyItemRecovery.HOLDERS) {
            IBakedModel model = minecraft.getRenderItem().getItemModelWithOverrides(new ItemStack(holder), null, null);

            if (model == minecraft.getRenderItem().getItemModelMesher().getModelManager().getMissingModel()
                    || model.getParticleTexture().getIconName().contains("missingno")) {
                throw new AssertionError("Missing retired-item holder model");
            }
        }

        System.out.println("BOP_BASKET_CLIENT_PASS empty=true filled=true atlas=true retired_item_models=3");
    }
}
