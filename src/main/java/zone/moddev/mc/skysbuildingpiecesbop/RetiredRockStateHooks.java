package zone.moddev.mc.skysbuildingpiecesbop;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/** Retired rocks keep their individual hardness; every other block keeps its native result. */
public final class RetiredRockStateHooks {
    private RetiredRockStateHooks() {
    }

    public static float destroySpeed(float original, BlockBehaviour.BlockStateBase state,
            BlockGetter world, BlockPos position) {
        if (state.getBlock() instanceof RetiredRocks && state instanceof BlockState) {
            return ((RetiredRocks) state.getBlock()).materialDestroySpeed((BlockState) state, world, position);
        }
        return original;
    }
}
