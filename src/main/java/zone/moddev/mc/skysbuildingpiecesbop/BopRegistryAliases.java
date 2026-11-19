package zone.moddev.mc.skysbuildingpiecesbop;

import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import zone.moddev.mc.skysbuildingpieces.legacy.LegacyRegistryAliases;

/** Gives old saved names a target without replacing a block or item BOP still registers. */
@EventBusSubscriber(modid = SkysBuildingPiecesBop.ID)
public final class BopRegistryAliases {
    // BOP already supplies fallback aliases for these four blocks. The raw save fixer
    // keeps our existing replacements, including the plants in both flower pots.
    private static final Map<String, String> BOP_BLOCK_ALIASES = Map.of(
            "biomesoplenty:potted_clover", "minecraft:flower_pot",
            "biomesoplenty:potted_wildflower", "minecraft:flower_pot",
            "biomesoplenty:root", "minecraft:hanging_roots",
            "biomesoplenty:rooted_sand", "minecraft:sand");

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

    static void block(String former, Identifier target) {
        register(BuiltInRegistries.BLOCK, former, target);
    }

    static void item(String former, Identifier target) {
        register(BuiltInRegistries.ITEM, former, target);
    }

    private static void register(Registry<?> registry, String name, Identifier target) {
        Identifier former = Identifier.parse(name);
        if (registry.key().equals(Registries.BLOCK)
                && registry.resolve(former).toString().equals(BOP_BLOCK_ALIASES.get(name))) {
            return;
        }
        if (!registry.containsKey(former) && registry.containsKey(target)) {
            LegacyRegistryAliases.register(registry, former, target);
        }
    }
}
