package zone.moddev.mc.skysbuildingpiecesbop;

import java.util.Collections;
import java.util.List;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.VineBlock;
import net.minecraft.block.material.Material;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.state.EnumProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.util.Direction;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.IBooleanFunction;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.loot.LootContext;
import net.minecraftforge.event.RegistryEvent;
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
    public static final Formation STONE_FORMATIONS = new Formation();
    public static final Block JELLED_POISON = new JelledPoison();

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
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("biomesoplenty", "willow_vine"));

        if (block == null || block == net.minecraft.block.Blocks.AIR || metadata < 0 || metadata > 15) {
            return null;
        }

        return block.defaultBlockState().setValue(VineBlock.SOUTH, (metadata & 1) != 0)
                .setValue(VineBlock.WEST, (metadata & 2) != 0).setValue(VineBlock.NORTH, (metadata & 4) != 0)
                .setValue(VineBlock.EAST, (metadata & 8) != 0);
    }

    @SubscribeEvent
    public static void blocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().registerAll(STONE_FORMATIONS, JELLED_POISON);
    }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> event) {
        for (Block block : new Block[] { STONE_FORMATIONS, JELLED_POISON }) {
            event.getRegistry().register(new BlockItem(block, new Item.Properties().tab(ItemGroup.TAB_DECORATIONS))
                    .setRegistryName(block.getRegistryName()));
        }
    }

    public enum Form implements IStringSerializable {
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
            super(AbstractBlock.Properties.of(Material.STONE).strength(1).sound(SoundType.STONE).noOcclusion()
                    .isRedstoneConductor((state, world, position) -> false));
            setRegistryName(SkysBuildingPiecesBop.ID, "stone_formations");
            registerDefaultState(stateDefinition.any().setValue(FORM, Form.FLOOR));
            // Loading an old decoration must not start a removal cascade when its anchor has changed.
        }

        @Override
        protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) {
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

        public BlockState getActualState(BlockState state, IBlockReader world, BlockPos position) {
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

        private boolean fullSupport(BlockState state, IBlockReader world, BlockPos position) {
            return !VoxelShapes.joinIsNotEmpty(state.getShape(world, position), VoxelShapes.block(),
                    IBooleanFunction.NOT_SAME);
        }

        @Override
        public BlockState getStateForPlacement(BlockItemUseContext context) {
            return getActualState(defaultBlockState(), context.getLevel(), context.getClickedPos());
        }

        @Override
        public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, IWorld world,
                BlockPos position, BlockPos neighbourPosition) {
            return getActualState(state, world, position);
        }

        @Override
        public void onPlace(BlockState state, World world, BlockPos position, BlockState previous,
                boolean moving) {
            BlockState actual = getActualState(state, world, position);

            if (!world.isClientSide && actual != state) {
                world.setBlock(position, actual, 2);
            }
        }

        @Override
        public VoxelShape getShape(BlockState state, IBlockReader world, BlockPos position, ISelectionContext context) {
            return OUTLINE;
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, IBlockReader world, BlockPos position,
                ISelectionContext context) {
            return VoxelShapes.empty();
        }

        @Override
        public List<ItemStack> getDrops(BlockState state, LootContext.Builder context) {
            return Collections.emptyList();
        }
    }

    private static final class JelledPoison extends Block {
        private JelledPoison() {
            super(AbstractBlock.Properties.of(Material.CLAY).strength(.4f).sound(SoundType.SLIME_BLOCK)
                    .friction(.9f).noOcclusion());
            setRegistryName(SkysBuildingPiecesBop.ID, "jelled_poison");
        }

    }
}
