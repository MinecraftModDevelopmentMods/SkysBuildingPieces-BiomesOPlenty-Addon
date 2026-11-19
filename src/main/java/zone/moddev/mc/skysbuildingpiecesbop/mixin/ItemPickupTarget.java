package zone.moddev.mc.skysbuildingpiecesbop.mixin;

import java.util.UUID;
import net.minecraft.world.entity.item.ItemEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the basket honour an item's exclusive pickup target. */
@Mixin(ItemEntity.class)
public interface ItemPickupTarget {
    @Accessor("target")
    @Nullable UUID skysbuildingpiecesbop$pickupTarget();
}
