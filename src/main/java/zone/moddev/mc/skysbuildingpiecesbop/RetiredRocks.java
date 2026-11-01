package zone.moddev.mc.skysbuildingpiecesbop;

import java.util.Collections;
import java.util.List;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.state.IntegerProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorldReader;
import net.minecraft.loot.LootContext;
import net.minecraftforge.common.ToolType;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Stable full-block fallbacks for the rock materials removed from BOP. */
@Mod.EventBusSubscriber(
        modid = SkysBuildingPiecesBop.ID,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RetiredRocks extends Block {
    public static final String[] NAMES = { "gabbro", "limestone", "limestone_smooth", "shale", "shale_smooth",
            "siltstone", "siltstone_smooth" };
    private static final float[] HARDNESS = { 5, 1.5f, 1.5f, 1.5f, 1.5f, 1, 1 };
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 6);
    public static final RetiredRocks BLOCK = new RetiredRocks();
    public static final Item[] ITEMS = new Item[NAMES.length];

    private RetiredRocks() {
        super(AbstractBlock.Properties.of(Material.STONE).strength(1.5f, 6));
        setRegistryName(SkysBuildingPiecesBop.ID, "retired_rocks");
        registerDefaultState(stateDefinition.any().setValue(VARIANT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(VARIANT);
    }

    public BlockState getStateFromMeta(int variant) {
        if (variant < 0 || variant >= NAMES.length) {
            throw new IllegalArgumentException("Unassigned retired-rock slot: " + variant);
        }

        return defaultBlockState().setValue(VARIANT, variant);
    }

    public int getMetaFromState(BlockState state) {
        return state.getValue(VARIANT);
    }

    public float materialDestroySpeed(BlockState state, IBlockReader world, BlockPos position) {
        return HARDNESS[state.getValue(VARIANT)];
    }

    @Override
    public float getExplosionResistance(BlockState state, IBlockReader world, BlockPos position, Explosion explosion) {
        return state.getValue(VARIANT) == 0 ? 60 : 6;
    }

    @Override
    public ToolType getHarvestTool(BlockState state) {
        return ToolType.PICKAXE;
    }

    @Override
    public int getHarvestLevel(BlockState state) {
        return state.getValue(VARIANT) == 0 ? 2 : 0;
    }

    public static Item item(BlockState state) {
        return ITEMS[state.getValue(VARIANT)];
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootContext.Builder context) {
        return Collections.singletonList(new ItemStack(item(state)));
    }

    @Override
    public ItemStack getPickBlock(BlockState state, RayTraceResult hit, IBlockReader world, BlockPos position,
            PlayerEntity player) {
        return new ItemStack(item(state));
    }

    @SubscribeEvent(
            priority = EventPriority.HIGHEST)
    public static void blocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(BLOCK);
    }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> event) {
        // The old palette identity remains a hidden recovery carrier. Normal inventory stacks have a distinct item
        // identity for each rock.
        event.getRegistry()
                .register(new BlockItem(BLOCK, new Item.Properties()).setRegistryName(BLOCK.getRegistryName()));

        for (int variant = 0; variant < NAMES.length; variant++) {
            VariantItem item = new VariantItem(variant);

            item.setRegistryName(SkysBuildingPiecesBop.ID, "retired_" + NAMES[variant]);
            ITEMS[variant] = item;
            event.getRegistry().register(item);
        }

        zone.moddev.mc.skysbuildingpieces.api.BuildingPiecesApi.registerStateItems(BLOCK, RetiredRocks::item);
    }

    private static final class VariantItem extends BlockItem {
        private final int variant;

        VariantItem(int variant) {
            super(BLOCK, new Item.Properties().tab(ItemGroup.TAB_BUILDING_BLOCKS));
            this.variant = variant;
        }

        @Override
        protected BlockState getPlacementState(BlockItemUseContext context) {
            return BLOCK.getStateFromMeta(variant);
        }

        @Override
        public String getDescriptionId() {
            return "block.skysbuildingpiecesbop.retired_" + NAMES[variant];
        }

        @Override
        public void fillItemCategory(ItemGroup group, NonNullList<ItemStack> items) {
            Block nativeRock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("mineralogy", NAMES[variant]));

            if (nativeRock == null || nativeRock == net.minecraft.block.Blocks.AIR) {
                super.fillItemCategory(group, items);
            }
        }
    }
}
