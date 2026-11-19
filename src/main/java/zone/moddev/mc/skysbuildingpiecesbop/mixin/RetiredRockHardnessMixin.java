package zone.moddev.mc.skysbuildingpiecesbop.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zone.moddev.mc.skysbuildingpiecesbop.RetiredRockStateHooks;

/** Keeps the hardness of each retired rock when it uses the fallback palette. */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class RetiredRockHardnessMixin {
    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void skysbuildingpiecesbop$rockHardness(BlockGetter level, BlockPos position,
            CallbackInfoReturnable<Float> callback) {
        float original = callback.getReturnValueF();
        float hardness = RetiredRockStateHooks.destroySpeed(original, (BlockBehaviour.BlockStateBase) (Object) this,
                level, position);
        if (hardness != original) {
            callback.setReturnValue(hardness);
        }
    }
}
