package zone.moddev.mc.skysbuildingpiecesbop.probe;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.VineBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import zone.moddev.mc.skysbuildingpieces.legacy.HistoricalFullBlocks;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge;
import zone.moddev.mc.skysbuildingpiecesbop.RetiredDecorations;

/** Check actual decoration geometry, drops and the saved vine attachment bits. */
final class DecorationChecks {
    private DecorationChecks() {
    }

    static void run(ServerWorld world) {
        BlockPos position = new BlockPos(930, 90, 930);
        BlockState original = world.getBlockState(position);
        BlockState above = world.getBlockState(position.above());
        BlockState below = world.getBlockState(position.below());

        try {
            BlockState formation = RetiredDecorations.STONE_FORMATIONS.defaultBlockState();

            require(formation.getCollisionShape(world, position).isEmpty(), "Formations have no collision");
            require(!formation.isRedstoneConductor(world, position), "Formations do not become full support blocks");
            require(Block.getDrops(formation, world, position, null).isEmpty(), "Formations have no survival drop");

            Set<RetiredDecorations.Form> forms = EnumSet.noneOf(RetiredDecorations.Form.class);

            for (Block upper : new Block[] { Blocks.AIR, Blocks.STONE, RetiredDecorations.STONE_FORMATIONS }) {
                for (Block lower : new Block[] { Blocks.AIR, Blocks.STONE, RetiredDecorations.STONE_FORMATIONS }) {
                    world.setBlock(position.above(), upper.defaultBlockState(), 2);
                    world.setBlock(position.below(), lower.defaultBlockState(), 2);
                    world.setBlock(position, formation, 2);
                    forms.add(RetiredDecorations.STONE_FORMATIONS.getActualState(formation, world, position)
                            .getValue(RetiredDecorations.Formation.FORM));
                }
            }

            require(forms.size() == 8, "All eight formation profiles remain available");
            world.removeBlock(position.above(), false);
            world.removeBlock(position.below(), false);
            formation.getBlock().neighborChanged(formation, world, position, Blocks.AIR, position.below(), false);
            require(world.getBlockState(position).getBlock() == RetiredDecorations.STONE_FORMATIONS,
                    "Old formations survive missing anchors");

            BlockState poison = RetiredDecorations.JELLED_POISON.defaultBlockState();

            world.setBlock(position, poison, 2);
            require(poison.getCollisionShape(world, position).bounds().equals(
                    new net.minecraft.util.math.AxisAlignedBB(0, 0, 0, 1, 1, 1)), "Poison keeps full collision");
            require(poison.getBlock().getSlipperiness(poison, world, position, null) == .9f
                    && poison.getDestroySpeed(world, position) == .4f, "Poison friction and hardness");

            java.util.List<ItemStack> drops = Block.getDrops(poison, world, position, null);

            require(drops.size() == 1
                    && drops.get(0).getItem() == net.minecraft.item.Item.byBlock(poison.getBlock()),
                    "Poison drops itself");

            for (int metadata = 0; metadata < 16; metadata++) {
                BlockState vine = HistoricalFullBlocks.resolve("biomesoplenty:tree_moss", metadata);

                require(vine.getValue(VineBlock.SOUTH) == ((metadata & 1) != 0)
                        && vine.getValue(VineBlock.WEST) == ((metadata & 2) != 0)
                        && vine.getValue(VineBlock.NORTH) == ((metadata & 4) != 0)
                        && vine.getValue(VineBlock.EAST) == ((metadata & 8) != 0), "Saved vine orientation " + metadata);

                CompoundNBT stack = new CompoundNBT();

                stack.putString("id", "skysbuildingpiecesbop:legacy_bop_tree_moss");
                stack.putByte("Count", (byte) 37);
                stack.putShort("Damage", (short) metadata);

                CompoundNBT tag = new CompoundNBT();

                tag.putString("custom", "Preserved decoration");
                stack.put("tag", tag);
                LegacyBridge.prepareStack(stack);

                ItemStack decoded = ItemStack.of(stack);

                require(decoded.getCount() == 37 && decoded.getTag().equals(tag)
                        && decoded.getItem() == net.minecraft.item.Item.byBlock(vine.getBlock()),
                        "Vine item identity, count and custom data");
            }

            System.out.println(
                    "BOP_NATIVE_DECORATIONS_PASS profiles=8 vine_states=16 collision=true drops=true NBT=true");
        } finally {
            world.setBlock(position, original, 2);
            world.setBlock(position.above(), above, 2);
            world.setBlock(position.below(), below, 2);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
