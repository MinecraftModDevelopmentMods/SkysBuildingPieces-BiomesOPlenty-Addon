package zone.moddev.mc.skysbuildingpiecesbop.probe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;

import zone.moddev.mc.skysbuildingpiecesbop.basket.*;

/** Verify both basket variants against the real client atlas before the client probe exits. */
public final class BasketClientChecks {
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger();

    private static boolean checked;

    static void register() {
        TickEvent.ClientTickEvent.Post.BUS.addListener(net.minecraftforge.eventbus.api.listener.Priority.HIGHEST,
                BasketClientChecks::tick);
    }

    public static void tick(TickEvent.ClientTickEvent.Post event) {
        if (checked
                || !System.getProperty("skysbuildingpieces.integrationPhase", "").startsWith("client")) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        // The title screen can bake models before the item's components are bound.
        if (minecraft.gui.overlay() != null || minecraft.level == null || minecraft.player == null) {
            return;
        }
        checked = true;
        TextureAtlasSprite previous;

        for (net.minecraft.world.item.Item basket : new net.minecraft.world.item.Item[] {Baskets.ITEM, Baskets.HOLDER}) {
            previous = null;
            for (boolean filled : new boolean[] { false, true }) {
                ItemStack stack = new ItemStack(basket);

                if (filled) {
                    CompoundTag tag = new CompoundTag();
                    ListTag entries = new ListTag();
                    CompoundTag child = BopProbeItems.legacy(new ItemStack(net.minecraft.world.level.block.Blocks.OAK_SAPLING));

                    child.putByte("Slot", (byte) 0);
                    entries.add(child);
                    tag.put("Items", entries);
                    BopProbeItems.setCustom(stack, tag);
                }

                TextureAtlasSprite model = checkModel(minecraft, stack);
                if (filled && model == previous) {
                    throw new AssertionError("Filled basket override did not change the model: "
                            + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(basket));
                }

                previous = model;
            }
        }
        for (net.minecraft.world.item.Item holder : zone.moddev.mc.skysbuildingpiecesbop.LegacyItemRecovery.HOLDERS) {
            checkModel(minecraft, new ItemStack(holder));
        }

        LOGGER.info("BOP_BASKET_CLIENT_PASS empty=true filled=true atlas=true retired_item_models=3");
    }

    private static TextureAtlasSprite checkModel(Minecraft minecraft, ItemStack stack) {
        var state = new ItemStackRenderState();
        minecraft.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI,
                minecraft.level, minecraft.player, 0);
        var material = state.isEmpty() ? null : state.pickParticleMaterial(RandomSource.create(0));
        TextureAtlasSprite sprite = material == null ? null : material.sprite();
        if (sprite == null || sprite.contents().name().toString().contains("missingno")) {
            throw new AssertionError("Missing basket or recovery item model: " + stack);
        }
        return sprite;
    }
}
