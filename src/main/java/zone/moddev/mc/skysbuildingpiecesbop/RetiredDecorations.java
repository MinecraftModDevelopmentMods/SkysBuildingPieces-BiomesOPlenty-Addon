package zone.moddev.mc.skysbuildingpiecesbop;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import zone.moddev.mc.skysbuildingpieces.legacy.HistoricalFullBlocks;

/** Replacements for removed decorations; these blocks never generate terrain. */
@Mod.EventBusSubscriber(
        modid = SkysBuildingPiecesBop.ID)
public final class RetiredDecorations {
    public static final String MODULE = "retired_bop_decorations";
    public static final Formation STONE_FORMATIONS = new Formation();
    public static final Block JELLED_POISON = new JelledPoison();

    private RetiredDecorations() {
    }

    public static void registerRecovery() {
        HistoricalFullBlocks.registerStates("biomesoplenty:stone_formations",
                SkysBuildingPiecesBop.ID + ":legacy_bop_stone_formations", MODULE,
                meta -> meta == 0 ? STONE_FORMATIONS.getDefaultState() : null);
        HistoricalFullBlocks.registerStates("biomesoplenty:jelled_poison",
                SkysBuildingPiecesBop.ID + ":legacy_bop_jelled_poison", MODULE,
                meta -> meta == 0 ? JELLED_POISON.getDefaultState() : null);
        HistoricalFullBlocks.registerStates("biomesoplenty:tree_moss",
                SkysBuildingPiecesBop.ID + ":legacy_bop_tree_moss", MODULE, meta -> {
                    Block vine = Block.getBlockFromName("biomesoplenty:willow_vine");

                    return vine == null ? null : vine.getStateFromMeta(meta);
                });
    }

    @SubscribeEvent
    public static void blocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().registerAll(STONE_FORMATIONS, JELLED_POISON);
    }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> event) {
        for (Block block : new Block[] { STONE_FORMATIONS, JELLED_POISON }) {
            event.getRegistry().register(new ItemBlock(block).setRegistryName(block.getRegistryName()));
        }
    }

    public enum Form implements IStringSerializable {
        FLOOR, CEILING, SINGLE, CONNECTOR, FLOOR_BASE, CEILING_BASE, FLOOR_TIP, CEILING_TIP;

        @Override
        public String getName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final class Formation extends Block {
        public static final PropertyEnum<Form> FORM = PropertyEnum.create("form", Form.class);
        private static final AxisAlignedBB OUTLINE = new AxisAlignedBB(.1, 0, .1, .9, 1, .9);

        private Formation() {
            super(Material.ROCK);
            setRegistryName(SkysBuildingPiecesBop.ID, "stone_formations");
            setTranslationKey(SkysBuildingPiecesBop.ID + ".stone_formations");
            setHardness(1);
            setSoundType(SoundType.STONE);
            setCreativeTab(CreativeTabs.DECORATIONS);
            setDefaultState(blockState.getBaseState().withProperty(FORM, Form.FLOOR));
            // Old formations can have modded or missing anchors. Loading them must not start a support-removal cascade
            // through an existing cave.
            setTickRandomly(false);
        }

        @Override
        protected BlockStateContainer createBlockState() {
            return new BlockStateContainer(this, FORM);
        }

        @Override
        public int getMetaFromState(IBlockState state) {
            return 0;
        }

        @Override
        public IBlockState getStateFromMeta(int meta) {
            return getDefaultState();
        }

        @Override
        public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
            IBlockState above = world.getBlockState(pos.up());
            IBlockState below = world.getBlockState(pos.down());

            boolean continuesAbove = above.getBlock() == this;
            boolean down = below.getBlock() == this;

            Form form;

            if (continuesAbove && down) {
                form = Form.CONNECTOR;
            } else if (continuesAbove) {
                form = below.isFullCube() ? Form.FLOOR_BASE : Form.CEILING_TIP;
            } else if (down) {
                form = above.isFullCube() ? Form.CEILING_BASE : Form.FLOOR_TIP;
            } else if (above.isFullCube() && below.isFullCube()) {
                form = Form.SINGLE;
            } else {
                form = above.isFullCube() ? Form.CEILING : Form.FLOOR;
            }

            return state.withProperty(FORM, form);
        }

        @Override
        public boolean isOpaqueCube(IBlockState state) {
            return false;
        }

        @Override
        public boolean isFullCube(IBlockState state) {
            return false;
        }

        @Override
        public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
            return BlockFaceShape.UNDEFINED;
        }

        @Override
        public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
            return OUTLINE;
        }

        @Override
        public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
            return NULL_AABB;
        }

        @Override
        public BlockRenderLayer getRenderLayer() {
            return BlockRenderLayer.CUTOUT;
        }

        @Override
        public int quantityDropped(Random random) {
            return 0;
        }

        @Override
        protected boolean canSilkHarvest() {
            return false;
        }
    }

    private static final class JelledPoison extends Block {
        private JelledPoison() {
            super(Material.CLAY);
            setRegistryName(SkysBuildingPiecesBop.ID, "jelled_poison");
            setTranslationKey(SkysBuildingPiecesBop.ID + ".jelled_poison");
            setHardness(.4f);
            setSoundType(SoundType.SLIME);
            slipperiness = .9f;
            setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
        }

        @Override
        public boolean isOpaqueCube(IBlockState state) {
            return false;
        }

        @Override
        public BlockRenderLayer getRenderLayer() {
            return BlockRenderLayer.TRANSLUCENT;
        }
    }
}
