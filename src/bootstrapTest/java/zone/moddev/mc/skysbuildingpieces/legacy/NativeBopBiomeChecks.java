package zone.moddev.mc.skysbuildingpieces.legacy;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;

/** A pre-flattening BOP number must resolve in the active world's copied biome registry. */
public final class NativeBopBiomeChecks {
    public static void run(MinecraftServer server) throws Exception {
        java.lang.reflect.Field active = HistoricalBiomes.class.getDeclaredField("active");
        active.setAccessible(true);
        Object original = active.get(null);
        try {
            CompoundNBT entry = new CompoundNBT();
            entry.putString("K", "biomesoplenty:bog");
            entry.putInt("V", 200);
            ListNBT entries = new ListNBT();
            entries.add(entry);
            CompoundNBT biomes = new CompoundNBT();
            biomes.put("ids", entries);
            CompoundNBT registries = new CompoundNBT();
            registries.put("minecraft:biomes", biomes);
            CompoundNBT snapshot = new CompoundNBT();
            snapshot.put("Registries", registries);
            HistoricalBiomes.begin(snapshot);
            CompoundNBT root = new CompoundNBT();
            root.putInt("DataVersion", 1343);
            CompoundNBT level = new CompoundNBT();
            byte[] cells = new byte[256];
            java.util.Arrays.fill(cells, (byte) 200);
            level.putByteArray("Biomes", cells);
            root.put("Level", level);
            HistoricalBiomes.prepare(root);
            HistoricalBiomes.finish(root);
            Registry<net.minecraft.world.biome.Biome> current = server.registryAccess()
                    .registryOrThrow(Registry.BIOME_REGISTRY);
            int expected = current.getId(current.get(new ResourceLocation("biomesoplenty:wetland")));
            if (expected < 0 || root.getCompound("Level").getIntArray("Biomes")[0] != expected) {
                throw new AssertionError("Pre-flattening BOP biome was not resolved in the active registry");
            }
            System.out.println("BOP_NATIVE_NUMERIC_BIOME_PASS cells=256 active_registry=true");
        } finally {
            active.set(null, original);
        }
    }
}
