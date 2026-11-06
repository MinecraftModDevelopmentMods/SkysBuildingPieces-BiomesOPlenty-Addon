package zone.moddev.mc.skysbuildingpiecesbop.mixin;

import net.minecraft.nbt.NbtAccounter;
import net.minecraft.util.filefix.FileFixerUpper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Gives the local world upgrade room to read a larger modded registry snapshot. */
@Mixin(FileFixerUpper.class)
abstract class LevelFileUpgradeMixin {
    @ModifyArg(
            method = "writeUpdatedLevelData(Ljava/nio/file/Path;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/nbt/NbtIo;readCompressed(Ljava/nio/file/Path;"
                            + "Lnet/minecraft/nbt/NbtAccounter;)Lnet/minecraft/nbt/CompoundTag;"),
            index = 1,
            require = 1,
            allow = 1)
    private NbtAccounter skysbuildingpiecesbop$readUpgradeLevelData(NbtAccounter quota) {
        // Forge stores registry snapshots in level.dat. Its file upgrade rereads
        // that local file with a smaller budget than a normal world load.
        return NbtAccounter.create(32L * 1024 * 1024);
    }
}
