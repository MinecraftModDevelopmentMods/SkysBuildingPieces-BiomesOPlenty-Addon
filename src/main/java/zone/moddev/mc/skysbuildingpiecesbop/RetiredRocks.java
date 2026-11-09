package zone.moddev.mc.skysbuildingpiecesbop;

import java.util.Collections;
import java.util.List;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.registries.ForgeRegistries;

/** Stable full-block fallbacks for the rock materials removed from BOP. */
public final class RetiredRocks extends Block {
    public static final String[] NAMES = { "gabbro", "limestone", "limestone_smooth", "shale", "shale_smooth",
            "siltstone", "siltstone_smooth" };
    private static final float[] HARDNESS = { 5, 1.5f, 1.5f, 1.5f, 1.5f, 1, 1 };
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 6);
    static final String LEGACY_ID = SkysBuildingPiecesBop.ID + ":retired_rocks";
    public static RetiredRocks BLOCK;
    private static final Block[] ROCKS = new Block[NAMES.length];
    public static final Item[] ITEMS = new Item[NAMES.length];

    private RetiredRocks() {
        super(SkysBuildingPiecesBop.blockProperties("retired_rocks").mapColor(MapColor.STONE)
                .strength(1.5f, 6).requiresCorrectToolForDrops());

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
        return player.getDestroySpeed(reference, position) / materialDestroySpeed(state, world, position) / divisor;
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
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder context) {
        return Collections.singletonList(new ItemStack(item(state)));
    }

    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader world, BlockPos position,
            BlockState state, boolean includeData) {
        return new ItemStack(item(state));
    }

    public static BlockState state(int variant) {
        return ROCKS[variant].defaultBlockState();
    }

    static Identifier id(int variant) {
        return Identifier.fromNamespaceAndPath(SkysBuildingPiecesBop.ID, "retired_" + NAMES[variant]);
    }

    private static final class FlatRock extends Block {
        private final int variant;

        FlatRock(int variant) {
            super(SkysBuildingPiecesBop.blockProperties("retired_" + NAMES[variant]).mapColor(MapColor.STONE)
                    .strength(HARDNESS[variant], variant == 0 ? 60 : 6).requiresCorrectToolForDrops());
            this.variant = variant;
        }

        @Override
        public List<ItemStack> getDrops(BlockState state, LootParams.Builder context) {
            return List.of(new ItemStack(ITEMS[variant]));
        }
    }

    public static void blocks(RegisterEvent event) {
        if (!event.getRegistryKey().equals(ForgeRegistries.Keys.BLOCKS)) return;
        BLOCK = new RetiredRocks();
        ForgeRegistries.BLOCKS.register(Identifier.parse(LEGACY_ID), BLOCK);
        for (int variant = 0; variant < NAMES.length; variant++) {
            ROCKS[variant] = new FlatRock(variant);
            ForgeRegistries.BLOCKS.register(id(variant), ROCKS[variant]);
        }
    }

    public static void items(RegisterEvent event) {
        if (!event.getRegistryKey().equals(ForgeRegistries.Keys.ITEMS)) return;
        // The old palette identity remains a hidden recovery carrier. Normal inventory stacks have a distinct item
        // identity for each rock.
        Identifier blockId = ForgeRegistries.BLOCKS.getKey(BLOCK);
        ForgeRegistries.ITEMS.register(blockId, new Item(SkysBuildingPiecesBop.itemProperties(blockId)));

        for (int variant = 0; variant < NAMES.length; variant++) {
            Identifier id = id(variant);
            VariantItem item = new VariantItem(variant, id);

            ITEMS[variant] = item;
            ForgeRegistries.ITEMS.register(id, item);
        }

        zone.moddev.mc.skysbuildingpieces.api.BuildingPiecesApi.registerStateItems(BLOCK, RetiredRocks::item);
    }

    public static void creativeContents(BuildCreativeModeTabContentsEvent event) {
        if (!CreativeModeTabs.BUILDING_BLOCKS.equals(event.getTabKey())) return;
        for (Item item : ITEMS) {
            if (((VariantItem) item).visibleInCreativeTab()) {
                event.accept(item);
            }
        }
    }

    private static final class VariantItem extends BlockItem {
        private final int variant;

        VariantItem(int variant, Identifier id) {
            super(ROCKS[variant], SkysBuildingPiecesBop.itemProperties(id)
                    .overrideDescription("block.skysbuildingpiecesbop.retired_" + NAMES[variant]));
            this.variant = variant;
        }

        boolean visibleInCreativeTab() {
            Block nativeRock = ForgeRegistries.BLOCKS.getValue(Identifier.fromNamespaceAndPath("mineralogy", NAMES[variant]));
            return nativeRock == null || nativeRock == net.minecraft.world.level.block.Blocks.AIR;
        }

    }
}
