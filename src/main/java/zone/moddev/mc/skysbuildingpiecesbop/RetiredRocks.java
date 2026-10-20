package zone.moddev.mc.skysbuildingpiecesbop;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Stable full-block fallbacks for the seven rock materials removed from BOP. */
@Mod.EventBusSubscriber(
        modid = SkysBuildingPiecesBop.ID)
public final class RetiredRocks extends Block {
    public static final String[] NAMES = { "gabbro", "limestone", "limestone_smooth", "shale", "shale_smooth",
            "siltstone", "siltstone_smooth" };
    private static final float[] HARDNESS = { 5, 1.5f, 1.5f, 1.5f, 1.5f, 1, 1 };
    public static final PropertyInteger VARIANT = PropertyInteger.create("variant", 0, 6);
    public static final RetiredRocks BLOCK = new RetiredRocks();

    private RetiredRocks() {
        super(Material.ROCK);
        setRegistryName(SkysBuildingPiecesBop.ID, "retired_rocks");
        setTranslationKey(SkysBuildingPiecesBop.ID + ".retired_rocks");
        setSoundType(SoundType.STONE);
        setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
        setDefaultState(blockState.getBaseState().withProperty(VARIANT, 0));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, VARIANT);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        if (meta < 0 || meta >= NAMES.length) {
            throw new IllegalArgumentException("Unassigned retired-rock slot: " + meta);
        }

        return getDefaultState().withProperty(VARIANT, meta);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(VARIANT);
    }

    @Override
    public int damageDropped(IBlockState state) {
        return getMetaFromState(state);
    }

    @Override
    public float getBlockHardness(IBlockState state, World world, BlockPos pos) {
        return HARDNESS[getMetaFromState(state)];
    }

    @Override
    public float getExplosionResistance(World world, BlockPos pos, Entity entity, Explosion explosion) {
        return world.getBlockState(pos).getValue(VARIANT) == 0 ? 60 : 6;
    }

    @Override
    public float getExplosionResistance(Entity entity) {
        return 6;
    }

    @Override
    public String getHarvestTool(IBlockState state) {
        return "pickaxe";
    }

    @Override
    public int getHarvestLevel(IBlockState state) {
        return getMetaFromState(state) == 0 ? 2 : 0;
    }

    @Override
    public void getSubBlocks(CreativeTabs tab, NonNullList<ItemStack> items) {
        for (int meta = 0; meta < NAMES.length; meta++) {
            Block nativeRock = Block.getBlockFromName("mineralogy:" + NAMES[meta]);

            if (nativeRock == null || nativeRock instanceof net.minecraft.block.BlockAir) {
                items.add(new ItemStack(this, 1, meta));
            }
        }
    }

    @SubscribeEvent(
            priority = EventPriority.HIGHEST)
    public static void blocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(BLOCK);
    }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new ItemBlock(BLOCK) {
            {
                setHasSubtypes(true);
            }

            @Override
            public int getMetadata(int damage) {
                return damage;
            }

            @Override
            public String getTranslationKey(ItemStack stack) {
                int meta = stack.getMetadata();

                if (meta < 0 || meta >= NAMES.length) {
                    throw new IllegalArgumentException("Unassigned retired-rock item slot: " + meta);
                }

                return "tile." + SkysBuildingPiecesBop.ID + ".retired_" + NAMES[meta];
            }
        }.setRegistryName(BLOCK.getRegistryName()));
    }

    @SubscribeEvent
    public static void recipes(RegistryEvent.Register<IRecipe> event) {
        // A world created without Mineralogy may still contain these full blocks. Keep them useful if the optional mod
        // is installed later.
        for (int meta = 0; meta < NAMES.length; meta++) {
            Block nativeRock = Block.getBlockFromName("mineralogy:" + NAMES[meta]);

            if (nativeRock == null || Item.getItemFromBlock(nativeRock) == net.minecraft.init.Items.AIR) {
                continue;
            }

            NonNullList<Ingredient> ingredients = NonNullList.create();

            ingredients.add(Ingredient.fromStacks(new ItemStack(BLOCK, 1, meta)));
            event.getRegistry().register(new ShapelessRecipes("", new ItemStack(nativeRock), ingredients)
                    .setRegistryName(new ResourceLocation(SkysBuildingPiecesBop.ID, "restore_" + NAMES[meta])));
        }
    }
}
