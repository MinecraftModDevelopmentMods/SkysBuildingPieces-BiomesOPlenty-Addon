package zone.moddev.mc.skysbuildingpiecesbop.probe;

import zone.moddev.mc.skysbuildingpieces.legacy.HistoricalBiomes;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Registry;

/** A pre-flattening BOP number must resolve in the active world's copied biome registry. */
public final class NativeBopBiomeChecks {
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger();

    private static void invoke(String name, CompoundTag value) throws Exception {
        java.lang.reflect.Method method = HistoricalBiomes.class.getDeclaredMethod(name, CompoundTag.class);
        method.setAccessible(true);
        method.invoke(null, value);
    }

    public static void run(MinecraftServer server) throws Exception {
        java.lang.reflect.Field active = HistoricalBiomes.class.getDeclaredField("active");
        active.setAccessible(true);
        Object original = active.get(null);
        try {
            CompoundTag entry = new CompoundTag();
            entry.putString("K", "biomesoplenty:bog");
            entry.putInt("V", 200);
            ListTag entries = new ListTag();
            entries.add(entry);
            CompoundTag biomes = new CompoundTag();
            biomes.put("ids", entries);
            CompoundTag registries = new CompoundTag();
            registries.put("minecraft:biomes", biomes);
            CompoundTag snapshot = new CompoundTag();
            snapshot.put("Registries", registries);
            invoke("begin", snapshot);
            CompoundTag root = new CompoundTag();
            root.putInt("DataVersion", 1343);
            CompoundTag level = new CompoundTag();
            byte[] cells = new byte[256];
            java.util.Arrays.fill(cells, (byte) 200);
            level.putByteArray("Biomes", cells);
            root.put("Level", level);
            invoke("prepare", root);
            invoke("finish", root);
            Registry<net.minecraft.world.level.biome.Biome> current = server.registryAccess()
                    .registryOrThrow(Registry.BIOME_REGISTRY);
            int expected = current.getId(current.get(new ResourceLocation("biomesoplenty:wetland")));
            if (expected < 0 || root.getCompound("Level").getIntArray("Biomes")[0] != expected) {
                throw new AssertionError("Pre-flattening BOP biome was not resolved in the active registry");
            }
            LOGGER.info("BOP_NATIVE_NUMERIC_BIOME_PASS cells=256 active_registry=true");
        } finally {
            active.set(null, original);
        }
    }
}
