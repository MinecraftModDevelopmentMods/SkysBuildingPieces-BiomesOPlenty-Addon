package zone.moddev.mc.skysbuildingpiecesbop.probe;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import zone.moddev.mc.skysbuildingpieces.legacy.*;
import zone.moddev.mc.skysbuildingpiecesbop.*;

/** Real registry, storage, physics and early recovery checks for removed BOP decorations. */
public final class RetiredDecorationChecks {
    public static void run(WorldServer world) {
        LegacyBridge.report(world);

        PiecesWorldState counters = PiecesWorldState.get(world);
        NBTTagCompound previous = counters.writeToNBT(new NBTTagCompound());
        BlockPos pos = new BlockPos(930, 90, 930);
        IBlockState before = world.getBlockState(pos);
        IBlockState above = world.getBlockState(pos.up());
        IBlockState below = world.getBlockState(pos.down());

        try {
            String[] names = { "stone_formations", "jelled_poison", "tree_moss" };
            Block[] holders = { RetiredMaterialHolders.FORMATIONS, RetiredMaterialHolders.POISON,
                    RetiredMaterialHolders.MOSS };

            for (int type = 0; type < names.length; type++) {
                for (int meta = 0; meta < (type == 2 ? 16 : 1); meta++) {
                    String legacyId = "biomesoplenty:" + names[type];
                    IBlockState target = HistoricalFullBlocks.resolve(legacyId, meta);

                    require(target
                            .equals(HistoricalFullBlocks.resolve(holders[type].getRegistryName().toString(), meta)),
                            "holder target");

                    NBTTagCompound root = chunk(holders[type], meta, target);
                    NBTTagCompound level = root.getCompoundTag("Level");
                    NBTTagCompound coverage = new NBTTagCompound();

                    coverage.setInteger(HistoricalFullBlocks.MODULE, 1);
                    coverage.setInteger("vanilla", 1);
                    coverage.setInteger("biomesoplenty", 1);
                    coverage.setInteger("future", 9);
                    level.setTag("skysbuildingpieces_coverage", coverage);

                    Map<Integer, String> registryIds = Collections.singletonMap(Block.getIdFromBlock(holders[type]),
                            legacyId);

                    HistoricalFullBlocks.prepareChunk(root, registryIds);

                    NBTTagCompound section = level.getTagList("Sections", 10).getCompoundTagAt(0);

                    require(read(section, 0).equals(target) && read(section, 1).equals(target),
                            "mixed historical and permanent state " + legacyId + "/" + meta);
                    require(coverage.getInteger(HistoricalFullBlocks.MODULE) == 1 && coverage.getInteger("future") == 9
                            && coverage.getInteger(RetiredDecorations.MODULE) == 1, "independent coverage");
                    require(root.getCompoundTag("skysbuildingpieces_historical_delta")
                            .getLong(RetiredDecorations.MODULE) == 1, "only one old block counted");

                    NBTTagCompound once = root.copy();

                    HistoricalFullBlocks.prepareChunk(root, registryIds);
                    require(once.equals(root), "chunk reload idempotence");

                    NBTTagCompound stack = new NBTTagCompound();
                    NBTTagCompound tag = new NBTTagCompound();

                    stack.setString("id", legacyId);
                    stack.setByte("Count", (byte) 37);
                    stack.setShort("Damage", (short) meta);
                    tag.setString("custom_label", "Preserved decoration");
                    tag.setInteger("RepairCost", 12);
                    stack.setTag("tag", tag);
                    LegacyBridge.prepareStack(stack);

                    ItemStack actual = new ItemStack(stack);

                    require(actual.getCount() == 37 && actual.getTagCompound().equals(tag),
                            "complete stack count and NBT");
                    require(actual.getItem() == net.minecraft.item.Item.getItemFromBlock(target.getBlock()),
                            "stack target");
                    once = stack.copy();
                    LegacyBridge.prepareStack(stack);
                    require(once.equals(stack), "stack reload idempotence");
                }
            }
            for (String legacyId : new String[] { "stone_formations", "jelled_poison" }) {
                boolean stopped = false;

                try {
                    HistoricalFullBlocks.resolve("biomesoplenty:" + legacyId, 1);
                } catch (LegacyBridge.RecoveryAbort expected) {
                    stopped = true;
                }

                require(stopped, "unassigned metadata stops safely");
            }

            IBlockState formation = RetiredDecorations.STONE_FORMATIONS.getDefaultState();

            require(!formation.isFullCube() && !formation.isOpaqueCube(), "formation is not a full cube");
            require(formation.getCollisionBoundingBox(world, pos) == null, "formation has no physical collision");

            for (net.minecraft.util.EnumFacing face : net.minecraft.util.EnumFacing.values()) {
                require(formation.getBlockFaceShape(world, pos,
                        face) == net.minecraft.block.state.BlockFaceShape.UNDEFINED,
                        "formation has no solid attachment face");
            }

            require(formation.getBlock().quantityDropped(world.rand) == 0, "formation has no survival drop");

            Set<RetiredDecorations.Form> forms = EnumSet.noneOf(RetiredDecorations.Form.class);

            for (Block top : new Block[] { Blocks.AIR, Blocks.STONE, RetiredDecorations.STONE_FORMATIONS }) {
                for (Block bottom : new Block[] { Blocks.AIR, Blocks.STONE, RetiredDecorations.STONE_FORMATIONS }) {
                    world.setBlockState(pos, formation, 2);
                    world.setBlockState(pos.up(), top.getDefaultState(), 2);
                    world.setBlockState(pos.down(), bottom.getDefaultState(), 2);
                    forms.add(formation.getActualState(world, pos).getValue(RetiredDecorations.Formation.FORM));
                }
            }

            require(forms.size() == 8, "all calculated formation profiles");
            world.setBlockToAir(pos.up());
            world.setBlockToAir(pos.down());
            formation.getBlock().neighborChanged(formation, world, pos, Blocks.AIR, pos.down());
            require(world.getBlockState(pos).equals(formation), "migrated formation survives missing anchors");

            IBlockState poison = RetiredDecorations.JELLED_POISON.getDefaultState();

            world.setBlockState(pos, poison, 2);
            require(poison.isFullCube() && !poison.isOpaqueCube(), "poison full collision but translucent rendering");
            require(poison.getCollisionBoundingBox(world, pos).equals(Block.FULL_BLOCK_AABB), "poison collision");
            require(poison.getBlock().slipperiness == .9f
                    && poison.getBlock().getBlockHardness(poison, world, pos) == .4f, "poison friction and hardness");

            net.minecraft.util.NonNullList<ItemStack> drops = net.minecraft.util.NonNullList.create();

            poison.getBlock().getDrops(drops, world, pos, poison, 0);
            require(drops.size() == 1
                    && drops.get(0).getItem() == net.minecraft.item.Item.getItemFromBlock(poison.getBlock()),
                    "poison self drop");

            int registryCount = 0;

            for (Block block : Block.REGISTRY) {
                if (block.getRegistryName().getNamespace().equals(SkysBuildingPiecesBop.ID)) {
                    registryCount++;
                }
            }

            require(registryCount == 74 && registryCount <= 80, "complete add-on registry budget");
            System.out.println(
                    "BOP_RETIRED_DECORATIONS_PASS states=18 profiles=8 mixed_ids=true NBT=preserved reload=idempotent block_ids="
                            + registryCount);
        } finally {
            world.setBlockState(pos, before, 2);
            world.setBlockState(pos.up(), above, 2);
            world.setBlockState(pos.down(), below, 2);
            LegacyBridge.report(world);
            counters.readFromNBT(previous);
            counters.markDirty();
        }
    }

    private static NBTTagCompound chunk(Block holder, int meta, IBlockState current) {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound level = new NBTTagCompound();
        NBTTagCompound section = new NBTTagCompound();

        root.setTag("Level", level);

        byte[] blockIdLowBytes = new byte[4096];
        byte[] high = new byte[2048];
        byte[] data = new byte[2048];

        int[] ids = { Block.getIdFromBlock(holder), Block.getIdFromBlock(current.getBlock()) };

        for (int entryIndex = 0; entryIndex < 2; entryIndex++) {
            blockIdLowBytes[entryIndex] = (byte) ids[entryIndex];
            LegacyBridge.setNibble(high, entryIndex, ids[entryIndex] >> 8);
            LegacyBridge.setNibble(data, entryIndex,
                    entryIndex == 0 ? meta : current.getBlock().getMetaFromState(current));
        }

        section.setByte("Y", (byte) 4);
        section.setByteArray("Blocks", blockIdLowBytes);
        section.setByteArray("Add", high);
        section.setByteArray("Data", data);

        NBTTagList sections = new NBTTagList();

        sections.appendTag(section);
        level.setTag("Sections", sections);
        return root;
    }

    private static IBlockState read(NBTTagCompound section, int index) {
        int numeric = (section.getByteArray("Blocks")[index] & 255)
                | (LegacyBridge.nibble(section.getByteArray("Add"), index) << 8);

        return Block.getBlockById(numeric).getStateFromMeta(LegacyBridge.nibble(section.getByteArray("Data"), index));
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
