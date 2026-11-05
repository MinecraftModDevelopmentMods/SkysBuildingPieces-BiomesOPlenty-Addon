package zone.moddev.mc.skysbuildingpiecesbop;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

/** Separate hidden identities keep mixed old and current palette saves unambiguous. */
@EventBusSubscriber(
        modid = SkysBuildingPiecesBop.ID,
        bus = EventBusSubscriber.Bus.MOD)
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
    public static void blocks(RegisterEvent event) {
        if (!event.getRegistryKey().equals(net.minecraft.core.registries.Registries.BLOCK)) return;
        STONE = new Holder("legacy_bop_stone");
        CRAG = new Holder("legacy_bop_crag_rock");
        FORMATIONS = new Holder("legacy_bop_stone_formations");
        POISON = new Holder("legacy_bop_jelled_poison");
        MOSS = new Holder("legacy_bop_tree_moss");
        Block[] holders = { STONE, CRAG, FORMATIONS, POISON, MOSS };
        System.arraycopy(holders, 0, BLOCKS, 0, holders.length);
        for (int index = 0; index < BLOCKS.length; index++) {
            String id = new String[] { "legacy_bop_stone", "legacy_bop_crag_rock", "legacy_bop_stone_formations",
                    "legacy_bop_jelled_poison", "legacy_bop_tree_moss" }[index];
            net.minecraft.core.Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(SkysBuildingPiecesBop.ID, id), BLOCKS[index]);
        }
    }

    @SubscribeEvent
    public static void items(RegisterEvent event) {
        if (!event.getRegistryKey().equals(net.minecraft.core.registries.Registries.ITEM)) return;
        for (Block block : BLOCKS) {
            net.minecraft.core.Registry.register(BuiltInRegistries.ITEM, BuiltInRegistries.BLOCK.getKey(block), new BlockItem(block, new Item.Properties()));
        }
    }

    static void registerBlockAliases() {
        for (int index = 0; index < BLOCKS.length; index++) {
            BopRegistryAliases.block("biomesoplenty:" + OLD_IDS[index],
                    BuiltInRegistries.BLOCK.getKey(BLOCKS[index]));
        }
    }

    static void registerItemAliases() {
        for (int index = 0; index < BLOCKS.length; index++) {
            BopRegistryAliases.item("biomesoplenty:" + OLD_IDS[index],
                    BuiltInRegistries.ITEM.getKey(Item.byBlock(BLOCKS[index])));
        }
    }

    private static final class Holder extends Block {
        private static final IntegerProperty META = IntegerProperty.create("metadata", 0, 15);

        Holder(String name) {
            super(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(-1, 3600000));
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
