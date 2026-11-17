package zone.moddev.mc.skysbuildingpiecesbop.mixin;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignText;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Preserves the filtered lines when Minecraft copies a saved sign for text resolution. */
@Mixin(SignText.Mutable.class)
abstract class SignTextCopyMixin {
    @Redirect(
            method = "<init>(Lnet/minecraft/world/level/block/entity/SignText;)V",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/world/level/block/entity/SignText;messages:Ljava/util/List;",
                    opcode = Opcodes.GETFIELD, ordinal = 1),
            require = 1)
    private List<Component> skysbuildingpiecesbop$copyFilteredMessages(SignText original) {
        // Minecraft 26.3 reads the visible lines twice. Only the filtered copy needs replacing.
        return original.getMessages(true);
    }
}
