package zone.moddev.mc.skysbuildingpiecesbop.client;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import zone.moddev.mc.skysbuildingpiecesbop.RetiredRocks;
import zone.moddev.mc.skysbuildingpiecesbop.RetiredMaterialHolders;
import zone.moddev.mc.skysbuildingpiecesbop.RetiredDecorations;
import zone.moddev.mc.skysbuildingpiecesbop.SkysBuildingPiecesBop;

@Mod.EventBusSubscriber(
        modid = SkysBuildingPiecesBop.ID,
        value = Side.CLIENT)
public final class RetiredRockModels {
    @SubscribeEvent
    public static void models(ModelRegistryEvent event) {
        String[] legacyModels = { "minecraft:wheat_seeds", "biomesoplenty:brown_dye", "biomesoplenty:clover" };

        for (int modelIndex = 0; modelIndex < legacyModels.length; modelIndex++) {
            ModelLoader.setCustomModelResourceLocation(
                    zone.moddev.mc.skysbuildingpiecesbop.LegacyItemRecovery.HOLDERS[modelIndex], 0,
                    new ModelResourceLocation(legacyModels[modelIndex], "inventory"));
        }
        for (Item basket : new Item[] { zone.moddev.mc.skysbuildingpiecesbop.basket.Baskets.ITEM,
                zone.moddev.mc.skysbuildingpiecesbop.basket.Baskets.HOLDER }) {
            ModelLoader.setCustomModelResourceLocation(basket, 0,
                    new ModelResourceLocation("skysbuildingpiecesbop:flower_basket", "inventory"));
        }

        Item item = Item.getItemFromBlock(RetiredRocks.BLOCK);

        for (net.minecraft.block.Block block : new net.minecraft.block.Block[] { RetiredDecorations.STONE_FORMATIONS,
                RetiredDecorations.JELLED_POISON }) {
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block), 0,
                    new ModelResourceLocation(block.getRegistryName(), "inventory"));
        }
        for (int meta = 0; meta < RetiredRocks.NAMES.length; meta++) {
            ModelLoader.setCustomModelResourceLocation(item, meta, new ModelResourceLocation(
                    SkysBuildingPiecesBop.ID + ":retired_" + RetiredRocks.NAMES[meta], "inventory"));
        }
        for (int meta = 0; meta < 16; meta++) {
            int slot = meta == 0 ? 1
                    : meta == 1 ? 5 : meta == 2 ? 3 : meta == 8 ? 2 : meta == 9 ? 6 : meta == 10 ? 4 : 0;
            String rock = RetiredRocks.NAMES[slot];

            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(RetiredMaterialHolders.STONE), meta,
                    new ModelResourceLocation(SkysBuildingPiecesBop.ID + ":retired_" + rock, "inventory"));
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(RetiredMaterialHolders.CRAG), meta,
                    new ModelResourceLocation(SkysBuildingPiecesBop.ID + ":retired_gabbro", "inventory"));
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(RetiredMaterialHolders.FORMATIONS), meta,
                    new ModelResourceLocation(SkysBuildingPiecesBop.ID + ":stone_formations", "inventory"));
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(RetiredMaterialHolders.POISON), meta,
                    new ModelResourceLocation(SkysBuildingPiecesBop.ID + ":jelled_poison", "inventory"));
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(RetiredMaterialHolders.MOSS), meta,
                    new ModelResourceLocation("biomesoplenty:willow_vine", "inventory"));
        }
    }

    private RetiredRockModels() {
    }
}
