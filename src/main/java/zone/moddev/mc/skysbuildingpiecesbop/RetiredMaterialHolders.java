package zone.moddev.mc.skysbuildingpiecesbop;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Separate hidden identities keep mixed old and current palette saves unambiguous. */
@Mod.EventBusSubscriber(
        modid = SkysBuildingPiecesBop.ID)
public final class RetiredMaterialHolders {
    public static final Block STONE = new Holder("legacy_bop_stone");
    public static final Block CRAG = new Holder("legacy_bop_crag_rock");
    public static final Block FORMATIONS = new Holder("legacy_bop_stone_formations");
    public static final Block POISON = new Holder("legacy_bop_jelled_poison");
    public static final Block MOSS = new Holder("legacy_bop_tree_moss");
    public static final Block[] BLOCKS = { STONE, CRAG, FORMATIONS, POISON, MOSS };
    private static final String[] OLD_IDS = { "stone", "crag_rock", "stone_formations", "jelled_poison", "tree_moss" };

    private RetiredMaterialHolders() {
    }

    @SubscribeEvent(
            priority = EventPriority.HIGHEST)
    public static void blocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().registerAll(BLOCKS);
    }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> event) {
        for (Block block : BLOCKS) {
            event.getRegistry()
                    .register(new ItemBlock(block).setHasSubtypes(true).setRegistryName(block.getRegistryName()));
        }
    }

    @SubscribeEvent
    public static void missingBlocks(RegistryEvent.MissingMappings<Block> event) {
        for (RegistryEvent.MissingMappings.Mapping<Block> mapping : event.getAllMappings()) {
            for (int legacyIdIndex = 0; legacyIdIndex < OLD_IDS.length; legacyIdIndex++) {
                if (mapping.key.equals(new ResourceLocation("biomesoplenty", OLD_IDS[legacyIdIndex]))) {
                    mapping.remap(BLOCKS[legacyIdIndex]);
                }
            }
        }
    }

    @SubscribeEvent
    public static void missingItems(RegistryEvent.MissingMappings<Item> event) {
        for (RegistryEvent.MissingMappings.Mapping<Item> mapping : event.getAllMappings()) {
            for (int legacyIdIndex = 0; legacyIdIndex < OLD_IDS.length; legacyIdIndex++) {
                if (mapping.key.equals(new ResourceLocation("biomesoplenty", OLD_IDS[legacyIdIndex]))) {
                    mapping.remap(Item.getItemFromBlock(BLOCKS[legacyIdIndex]));
                }
            }
        }
    }

    private static final class Holder extends Block {
        private static final PropertyInteger META = PropertyInteger.create("metadata", 0, 15);

        Holder(String name) {
            super(Material.ROCK);
            setRegistryName(SkysBuildingPiecesBop.ID, name);
            setDefaultState(blockState.getBaseState().withProperty(META, 0));
        }

        @Override
        protected BlockStateContainer createBlockState() {
            return new BlockStateContainer(this, META);
        }

        @Override
        public IBlockState getStateFromMeta(int meta) {
            return getDefaultState().withProperty(META, meta & 15);
        }

        @Override
        public int getMetaFromState(IBlockState state) {
            return state.getValue(META);
        }
    }
}
