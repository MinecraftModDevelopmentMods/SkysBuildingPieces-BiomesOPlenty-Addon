package zone.moddev.mc.skysbuildingpiecesbop;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Separate hidden identities keep mixed old and current palette saves unambiguous. */
@Mod.EventBusSubscriber(
        modid = SkysBuildingPiecesBop.ID,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RetiredMaterialHolders {
    public static Block STONE;
    public static Block CRAG;
    public static Block FORMATIONS;
    public static Block POISON;
    public static Block MOSS;
    public static final Block[] BLOCKS = new Block[5];
    private static final String[] OLD_IDS = { "stone", "crag_rock", "stone_formations", "jelled_poison", "tree_moss" };

    private RetiredMaterialHolders() {
    }

    @SubscribeEvent(
            priority = EventPriority.HIGHEST)
    public static void blocks(RegistryEvent.Register<Block> event) {
        STONE = new Holder("legacy_bop_stone");
        CRAG = new Holder("legacy_bop_crag_rock");
        FORMATIONS = new Holder("legacy_bop_stone_formations");
        POISON = new Holder("legacy_bop_jelled_poison");
        MOSS = new Holder("legacy_bop_tree_moss");
        Block[] holders = { STONE, CRAG, FORMATIONS, POISON, MOSS };
        System.arraycopy(holders, 0, BLOCKS, 0, holders.length);
        event.getRegistry().registerAll(BLOCKS);
    }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> event) {
        for (Block block : BLOCKS) {
            event.getRegistry()
                    .register(new BlockItem(block, new Item.Properties()).setRegistryName(block.getRegistryName()));
        }
    }

    public static void missingBlocks(RegistryEvent.MissingMappings<Block> event) {
        for (RegistryEvent.MissingMappings.Mapping<Block> mapping : event.getAllMappings()) {
            for (int legacyIdIndex = 0; legacyIdIndex < OLD_IDS.length; legacyIdIndex++) {
                if (mapping.key.equals(new ResourceLocation("biomesoplenty", OLD_IDS[legacyIdIndex]))) {
                    mapping.remap(BLOCKS[legacyIdIndex]);
                }
            }
        }
    }

    public static void missingItems(RegistryEvent.MissingMappings<Item> event) {
        for (RegistryEvent.MissingMappings.Mapping<Item> mapping : event.getAllMappings()) {
            for (int legacyIdIndex = 0; legacyIdIndex < OLD_IDS.length; legacyIdIndex++) {
                if (mapping.key.equals(new ResourceLocation("biomesoplenty", OLD_IDS[legacyIdIndex]))) {
                    mapping.remap(Item.byBlock(BLOCKS[legacyIdIndex]));
                }
            }
        }
    }

    private static final class Holder extends Block {
        private static final IntegerProperty META = IntegerProperty.create("metadata", 0, 15);

        Holder(String name) {
            super(BlockBehaviour.Properties.of(Material.STONE).strength(-1, 3600000));
            setRegistryName(SkysBuildingPiecesBop.ID, name);
            registerDefaultState(stateDefinition.any().setValue(META, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, net.minecraft.world.level.block.state.BlockState> builder) {
            builder.add(META);
        }

        public BlockState getStateFromMeta(int meta) {
            return defaultBlockState().setValue(META, meta & 15);
        }

        public int getMetaFromState(BlockState state) {
            return state.getValue(META);
        }
    }
}
