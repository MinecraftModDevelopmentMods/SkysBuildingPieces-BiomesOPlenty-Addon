package zone.moddev.mc.skysbuildingpiecesbop;

import java.util.Collections;
import java.util.List;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.storage.loot.LootContext;
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
    public static RetiredRocks BLOCK;
    public static final Item[] ITEMS = new Item[NAMES.length];

    private RetiredRocks() {
        super(BlockBehaviour.Properties.of(Material.STONE).strength(1.5f, 6).requiresCorrectToolForDrops());
        setRegistryName(SkysBuildingPiecesBop.ID, "retired_rocks");
        registerDefaultState(stateDefinition.any().setValue(VARIANT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
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

    public float materialDestroySpeed(BlockState state, BlockGetter world, BlockPos position) {
        return HARDNESS[state.getValue(VARIANT)];
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter world, BlockPos position, Explosion explosion) {
        return state.getValue(VARIANT) == 0 ? 60 : 6;
    }

    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter world, BlockPos position, Player player) {
        return miningReference(state).canHarvestBlock(world, position, player);
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter world, BlockPos position) {
        BlockState reference = miningReference(state);
        int divisor = net.minecraftforge.common.ForgeHooks.isCorrectToolForDrops(reference, player) ? 30 : 100;
        return player.getDigSpeed(reference, position) / materialDestroySpeed(state, world, position) / divisor;
    }

    private static BlockState miningReference(BlockState state) {
        // These variants share an ID, so block-wide tool-tier tags cannot
        // distinguish gabbro's iron-pickaxe requirement from the softer rocks.
        return (state.getValue(VARIANT) == 0 ? net.minecraft.world.level.block.Blocks.DIAMOND_ORE
                : net.minecraft.world.level.block.Blocks.STONE).defaultBlockState();
    }

    public static Item item(BlockState state) {
        return ITEMS[state.getValue(VARIANT)];
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootContext.Builder context) {
        return Collections.singletonList(new ItemStack(item(state)));
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult hit, BlockGetter world, BlockPos position,
            Player player) {
        return new ItemStack(item(state));
    }

    @SubscribeEvent(
            priority = EventPriority.HIGHEST)
    public static void blocks(RegistryEvent.Register<Block> event) {
        BLOCK = new RetiredRocks();
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
            super(BLOCK, new Item.Properties().tab(CreativeModeTab.TAB_BUILDING_BLOCKS));
            this.variant = variant;
        }

        @Override
        protected BlockState getPlacementState(BlockPlaceContext context) {
            return BLOCK.getStateFromMeta(variant);
        }

        @Override
        public String getDescriptionId() {
            return "block.skysbuildingpiecesbop.retired_" + NAMES[variant];
        }

        @Override
        public void fillItemCategory(CreativeModeTab group, NonNullList<ItemStack> items) {
            Block nativeRock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("mineralogy", NAMES[variant]));

            if (nativeRock == null || nativeRock == net.minecraft.world.level.block.Blocks.AIR) {
                super.fillItemCategory(group, items);
            }
        }
    }
}
