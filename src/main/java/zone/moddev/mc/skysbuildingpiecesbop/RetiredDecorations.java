package zone.moddev.mc.skysbuildingpiecesbop;

import java.util.Collections;
import java.util.List;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import zone.moddev.mc.skysbuildingpieces.legacy.HistoricalFullBlocks;

/** Restored decorations keep their original purpose and never generate terrain. */
@Mod.EventBusSubscriber(
        modid = SkysBuildingPiecesBop.ID,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RetiredDecorations {
    public static final String MODULE = "retired_bop_decorations";
    public static Formation STONE_FORMATIONS;
    public static Block JELLED_POISON;

    private RetiredDecorations() {
    }

    public static void registerRecovery() {
        HistoricalFullBlocks.registerStates("biomesoplenty:stone_formations",
                SkysBuildingPiecesBop.ID + ":legacy_bop_stone_formations", MODULE,
                metadata -> metadata == 0 ? STONE_FORMATIONS.defaultBlockState() : null);
        HistoricalFullBlocks.registerStates("biomesoplenty:jelled_poison",
                SkysBuildingPiecesBop.ID + ":legacy_bop_jelled_poison", MODULE,
                metadata -> metadata == 0 ? JELLED_POISON.defaultBlockState() : null);
        HistoricalFullBlocks.registerStates("biomesoplenty:tree_moss",
                SkysBuildingPiecesBop.ID + ":legacy_bop_tree_moss", MODULE, RetiredDecorations::vine);
    }

    private static BlockState vine(int metadata) {
        Block block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath("biomesoplenty", "willow_vine"));

        if (block == null || block == net.minecraft.world.level.block.Blocks.AIR || metadata < 0 || metadata > 15) {
            return null;
        }

        return block.defaultBlockState().setValue(VineBlock.SOUTH, (metadata & 1) != 0)
                .setValue(VineBlock.WEST, (metadata & 2) != 0).setValue(VineBlock.NORTH, (metadata & 4) != 0)
                .setValue(VineBlock.EAST, (metadata & 8) != 0);
    }

    @SubscribeEvent
    public static void blocks(RegisterEvent event) {
        if (!event.getRegistryKey().equals(ForgeRegistries.Keys.BLOCKS)) return;
        STONE_FORMATIONS = new Formation();
        JELLED_POISON = new JelledPoison();
        ForgeRegistries.BLOCKS.register(ResourceLocation.fromNamespaceAndPath(SkysBuildingPiecesBop.ID, "stone_formations"), STONE_FORMATIONS);
        ForgeRegistries.BLOCKS.register(ResourceLocation.fromNamespaceAndPath(SkysBuildingPiecesBop.ID, "jelled_poison"), JELLED_POISON);
    }

    @SubscribeEvent
    public static void items(RegisterEvent event) {
        if (!event.getRegistryKey().equals(ForgeRegistries.Keys.ITEMS)) return;
        for (Block block : new Block[] { STONE_FORMATIONS, JELLED_POISON }) {
            ForgeRegistries.ITEMS.register(ForgeRegistries.BLOCKS.getKey(block), new BlockItem(block, new Item.Properties()));
        }
    }

    @SubscribeEvent
    public static void creativeContents(BuildCreativeModeTabContentsEvent event) {
        if (CreativeModeTabs.NATURAL_BLOCKS.equals(event.getTabKey())) {
            event.accept(STONE_FORMATIONS);
            event.accept(JELLED_POISON);
        }
    }

    public enum Form implements StringRepresentable {
        FLOOR, CEILING, SINGLE, CONNECTOR, FLOOR_BASE, CEILING_BASE, FLOOR_TIP, CEILING_TIP;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final class Formation extends Block {
        public static final EnumProperty<Form> FORM = EnumProperty.create("form", Form.class);
        private static final VoxelShape OUTLINE = Block.box(1.6, 0, 1.6, 14.4, 16, 14.4);

        private Formation() {
            super(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1).sound(SoundType.STONE).noOcclusion()
                    .isRedstoneConductor((state, world, position) -> false));

            registerDefaultState(stateDefinition.any().setValue(FORM, Form.FLOOR));
            // Loading an old decoration must not start a removal cascade when its anchor has changed.
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FORM);
        }

        public BlockState getStateFromMeta(int metadata) {
            if (metadata != 0) {
                throw new IllegalArgumentException("Unassigned stone-formation metadata: " + metadata);
            }

            return defaultBlockState();
        }

        public int getMetaFromState(BlockState state) {
            return 0;
        }

        public BlockState getActualState(BlockState state, BlockGetter world, BlockPos position) {
            BlockState above = world.getBlockState(position.above());
            BlockState below = world.getBlockState(position.below());
            boolean continuesAbove = above.getBlock() == this;
            boolean continuesBelow = below.getBlock() == this;
            Form form;

            if (continuesAbove && continuesBelow) {
                form = Form.CONNECTOR;
            } else if (continuesAbove) {
                form = fullSupport(below, world, position.below()) ? Form.FLOOR_BASE : Form.CEILING_TIP;
            } else if (continuesBelow) {
                form = fullSupport(above, world, position.above()) ? Form.CEILING_BASE : Form.FLOOR_TIP;
            } else if (fullSupport(above, world, position.above()) && fullSupport(below, world, position.below())) {
                form = Form.SINGLE;
            } else {
                form = fullSupport(above, world, position.above()) ? Form.CEILING : Form.FLOOR;
            }

            return state.setValue(FORM, form);
        }

        private boolean fullSupport(BlockState state, BlockGetter world, BlockPos position) {
            return !Shapes.joinIsNotEmpty(state.getShape(world, position), Shapes.block(),
                    BooleanOp.NOT_SAME);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return getActualState(defaultBlockState(), context.getLevel(), context.getClickedPos());
        }

        @Override
        public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor world,
                BlockPos position, BlockPos neighbourPosition) {
            return getActualState(state, world, position);
        }

        @Override
        public void onPlace(BlockState state, Level world, BlockPos position, BlockState previous,
                boolean moving) {
            BlockState actual = getActualState(state, world, position);

            if (!world.isClientSide && actual != state) {
                world.setBlock(position, actual, 2);
            }
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos position, CollisionContext context) {
            return OUTLINE;
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos position,
                CollisionContext context) {
            return Shapes.empty();
        }

        @Override
        public List<ItemStack> getDrops(BlockState state, LootParams.Builder context) {
            return Collections.emptyList();
        }
    }

    private static final class JelledPoison extends Block {
        private JelledPoison() {
            super(BlockBehaviour.Properties.of().mapColor(MapColor.CLAY).strength(.4f).sound(SoundType.SLIME_BLOCK)
                    .friction(.9f).noOcclusion());

        }

    }
}
