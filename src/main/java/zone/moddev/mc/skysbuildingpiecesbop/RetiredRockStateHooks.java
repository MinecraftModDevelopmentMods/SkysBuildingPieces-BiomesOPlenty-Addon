package zone.moddev.mc.skysbuildingpiecesbop;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;

/** Retired rocks keep their individual hardness; every other block keeps its native result. */
public final class RetiredRockStateHooks {
    private RetiredRockStateHooks() {
    }

    public static float destroySpeed(float original, AbstractBlock.AbstractBlockState state,
            IBlockReader world, BlockPos position) {
        if (state.getBlock() instanceof RetiredRocks && state instanceof BlockState) {
            return ((RetiredRocks) state.getBlock()).materialDestroySpeed((BlockState) state, world, position);
        }
        return original;
    }
}
