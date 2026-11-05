package zone.moddev.mc.skysbuildingpiecesbop;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyRegistryAliases;

/** Gives old saved names a target without replacing a block or item BOP still registers. */
@EventBusSubscriber(modid = SkysBuildingPiecesBop.ID, bus = EventBusSubscriber.Bus.MOD)
public final class BopRegistryAliases {
    private BopRegistryAliases() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void register(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.BLOCK)) {
            RetiredMaterialHolders.registerBlockAliases();
            NamedBopRecovery.registerBlockAliases();
        } else if (event.getRegistryKey().equals(Registries.ITEM)) {
            RetiredMaterialHolders.registerItemAliases();
            LegacyItemRecovery.registerAliases();
            item("biomesoplenty:flower_basket",
                    BuiltInRegistries.ITEM.getKey(zone.moddev.mc.skysbuildingpiecesbop.basket.Baskets.HOLDER));
            NamedBopRecovery.registerItemAliases();
        }
    }

    static void block(String former, ResourceLocation target) {
        register(BuiltInRegistries.BLOCK, former, target);
    }

    static void item(String former, ResourceLocation target) {
        register(BuiltInRegistries.ITEM, former, target);
    }

    private static void register(Registry<?> registry, String name, ResourceLocation target) {
        ResourceLocation former = new ResourceLocation(name);
        if (!registry.containsKey(former) && registry.containsKey(target)) {
            LegacyRegistryAliases.register(registry, former, target);
        }
    }
}
