package zone.moddev.mc.skysbuildingpiecesbop.mixin;

import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.MissingMappingsEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zone.moddev.mc.skysbuildingpiecesbop.NamedBopRecovery;

/** Lets the saved-biome fixer handle aliases before the dynamic registry is available. */
@Mixin(value = MissingMappingsEvent.Mapping.class, remap = false)
abstract class RetiredBiomeRemapMixin {
    @Inject(method = "remap(Ljava/lang/Object;)V", at = @At("HEAD"), cancellable = true)
    private void skysbuildingpiecesbop$deferKnownBiome(Object target, CallbackInfo callback) {
        var mapping = (MissingMappingsEvent.Mapping<?>) (Object) this;
        if (target == null && Registries.BIOME.equals(mapping.getRegistry().getRegistryKey())
                && NamedBopRecovery.ownsRetiredBiome(mapping.getKey())) {
            // GlitchCore can request a dynamic biome before Forge has a live target.
            // The saved registry and chunk fixer already have the exact replacement.
            mapping.ignore();
            callback.cancel();
        }
    }
}
