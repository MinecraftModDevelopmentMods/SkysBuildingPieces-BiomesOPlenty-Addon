package zone.moddev.mc.skysbuildingpiecesbop.probe;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource.LevelDirectory;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;

/** Exercises real capture/write callbacks; native transformer orders are checked separately. */
final class BopPersistenceOrderChecks {
    private static List<Class<?>> callbacks = List.of();
    static void ready(FMLLoadCompleteEvent event) {
        String order = System.getProperty("skysbuildingpieces.persistenceOrder", "");
        if (!order.isEmpty()) {
            event.enqueueWork(() -> arrange(order));
        }
    }

    private static void arrange(String order) {
        if (!order.equals("forward") && !order.equals("reverse")) {
            throw new AssertionError("Unknown persistence order: " + order);
        }
        try {
            List<Class<?>> selected = new ArrayList<>();
            List<String> owners = new ArrayList<>(List.of(
                    "zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge",
                    "zone.moddev.mc.skysgrassslabs.compat.LegacyWorldDataHook"));
            if (ModList.get().isLoaded("mineralogy")) {
                owners.add("zone.moddev.mc.mineralogy.patching.LegacyWorldDataHook");
            }
            for (String name : owners) {
                Class<?> owner = Class.forName(name);
                owner.getMethod("captureLegacyLevelData", CompoundTag.class, LevelDirectory.class);
                selected.add(owner);
            }
            selected.sort(Comparator.comparing(Class::getName));
            if (order.equals("reverse")) {
                java.util.Collections.reverse(selected);
            }
            callbacks = List.copyOf(selected);
            org.apache.logging.log4j.LogManager.getLogger().info("BOP_PERSISTENCE_ORDER_SET "
                    + order + " actual_hooks=" + callbacks.size() + " mechanism=neoforge_level_hooks callback_order_only=true");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot arrange the native persistence callbacks", failure);
        }
    }

    static void verify(ServerLevel world) throws Exception {
        File original = new File("source-level.dat");
        if (!original.isFile() || System.getProperty("skysbuildingpieces.persistenceOrder", "").isEmpty()) {
            return;
        }
        CompoundTag expected = read(original).getCompoundOrEmpty("FML");
        if (expected.isEmpty()) {
            return; // Named source fixtures do not need a pre-flattening registry snapshot.
        }
        File directory = world.getServer().getWorldPath(LevelResource.ROOT).toFile();
        CompoundTag originalRoot = read(original);
        CompoundTag combined = new CompoundTag();
        int writers = 0;
        for (Class<?> owner : callbacks) {
            owner.getMethod("captureLegacyLevelData", CompoundTag.class, LevelDirectory.class)
                    .invoke(null, originalRoot.copy(), new LevelDirectory(directory.toPath()));
            Method writer;
            try {
                writer = owner.getMethod("writeLegacyLevelData", CompoundTag.class);
            } catch (NoSuchMethodException captureOnly) {
                require(owner.getName().contains(".mineralogy."), "An expected write callback is missing");
                continue;
            }
            writer.invoke(null, combined);
            require(combined.getCompoundOrEmpty("FML").equals(expected), "A callback overwrote the shared snapshot");
            writers++;
        }
        require(writers == 2, "The two native write wrappers must both preserve the snapshot");
        world.getServer().saveAllChunks(true, true, true);
        CompoundTag saved = read(new File(directory, "level.dat")).getCompoundOrEmpty("FML");
        require(saved.equals(expected), "The complete uppercase registry snapshot changed");
        CompoundTag blocks = expected.getCompoundOrEmpty("Registries").getCompoundOrEmpty("minecraft:blocks");
        require(!blocks.isEmpty(), "The source must contain a real numeric registry");
        List<String> legacySidecars = new ArrayList<>(List.of("skysgrassslabs"));
        if (ModList.get().isLoaded("mineralogy")) {
            legacySidecars.add("mineralogy");
        }
        for (String name : legacySidecars) {
            CompoundTag sidecar = read(new File(directory, "data/" + name + "_legacy_registry.dat"));
            require(sidecar.getCompoundOrEmpty("Blocks").equals(blocks), name + " lost its independent sidecar");
        }
        CompoundTag core = read(new File(directory, "data/skysbuildingpieces_legacy_registry.dat"));
        File originalSidecar = new File("source-pieces-legacy-registry.dat");
        if (originalSidecar.isFile()) {
            // Older chunks can use the original IDs while later chunks use the table from level.dat.
            CompoundTag before = read(originalSidecar);
            CompoundTag originalRegistry = before.getCompound("FML").orElse(before);
            CompoundTag laterRegistry = before.getCompound("source_registry").orElse(expected);
            require(core.getCompoundOrEmpty("FML").equals(originalRegistry),
                    "Building Pieces changed the original sidecar registry");
            require(core.getCompoundOrEmpty("source_registry").equals(laterRegistry),
                    "Building Pieces lost the later registry alongside the original");
        } else {
            require(core.getCompoundOrEmpty("FML").equals(expected), "Building Pieces lost its complete sidecar");
        }
        org.apache.logging.log4j.LogManager.getLogger().info("BOP_PERSISTENCE_ORDER_PASS "
                + System.getProperty("skysbuildingpieces.persistenceOrder") + " actual_hooks=" + callbacks.size()
                + " sidecars=" + (legacySidecars.size() + 1) + " ids="
                + blocks.getListOrEmpty("ids").size() + " complete_uppercase_snapshot=true");
    }

    private static CompoundTag read(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file)) {
            return NbtIo.readCompressed(input, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
