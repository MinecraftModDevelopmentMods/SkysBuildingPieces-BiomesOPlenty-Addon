package zone.moddev.mc.skysbuildingpiecesbop;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.state.IProperty;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SharedConstants;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Recovers named BOP content removed after flattening, before native loaders read it. */
@Mod.EventBusSubscriber(modid = SkysBuildingPiecesBop.ID)
public final class NamedBopRecovery {
    private static final Map<String, String> BLOCKS = mappings("blocks");
    private static final Map<String, String> ITEMS = itemMappings();
    private static final Map<String, String> BIOMES = mappings("biomes");
    private static final String BIOME_MARKER = "skysbuildingpiecesbop_named_biomes";
    private static volatile NavigableMap<Integer, Map<Integer, String>> biomeSnapshots = new TreeMap<>();

    private NamedBopRecovery() {
    }

    private static Map<String, String> mappings(String group) {
        String path = "/assets/skysbuildingpiecesbop/catalogue/named_legacy.json";

        try (InputStreamReader reader = new InputStreamReader(
                NamedBopRecovery.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
            JsonObject data = new Gson().fromJson(reader, JsonObject.class).getAsJsonObject(group);
            Map<String, String> result = new LinkedHashMap<>();

            data.entrySet().forEach(entry -> result.put(entry.getKey(), entry.getValue().getAsString()));
            return Collections.unmodifiableMap(result);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read named BOP upgrade mappings", failure);
        }
    }

    private static Map<String, String> itemMappings() {
        Map<String, String> result = new LinkedHashMap<>(BLOCKS);

        result.putAll(mappings("items"));
        return Collections.unmodifiableMap(result);
    }

    /** Keep earlier numeric biome tables available for chunks not opened during the first upgrade. */
    public static synchronized void begin(MinecraftServer server) throws IOException {
        File level = server.getActiveAnvilConverter().getFile(server.getFolderName(), "level.dat");
        File sidecar = new File(level.getParentFile(), "data/skysbuildingpiecesbop_named_registry.dat");
        CompoundNBT saved = new CompoundNBT();

        saved.putInt("schema_version", 1);
        if (sidecar.isFile()) {
            try (FileInputStream input = new FileInputStream(sidecar)) {
                saved = CompressedStreamTools.readCompressed(input);
            }
            if (saved.getInt("schema_version") != 1) {
                throw new IllegalStateException("Unsupported named BOP registry snapshot schema");
            }
        }

        CompoundNBT snapshots = saved.getCompound("snapshots");
        boolean changed = false;

        if (level.isFile()) {
            CompoundNBT root;

            try (FileInputStream input = new FileInputStream(level)) {
                root = CompressedStreamTools.readCompressed(input);
            }

            int version = root.getCompound("Data").getInt("DataVersion");

            if (version >= 1451 && version < SharedConstants.getVersion().getWorldVersion()) {
                CompoundNBT registry = root.getCompound("fml").getCompound("Registries")
                        .getCompound("minecraft:biome");

                if (registry.contains("ids", 9)) {
                    String key = Integer.toString(version);

                    if (snapshots.contains(key, 10) && !snapshots.getCompound(key).equals(registry)) {
                        throw new IllegalStateException("Conflicting BOP biome tables for save version " + version
                                + ". Restore the original world backup and mod list before upgrading.");
                    }
                    if (!snapshots.contains(key, 10)) {
                        snapshots.put(key, registry.copy());
                        changed = true;
                    }
                }
            }
        }

        NavigableMap<Integer, Map<Integer, String>> resolved = new TreeMap<>();

        for (String version : snapshots.keySet()) {
            resolved.put(Integer.parseInt(version), resolveBiomes(snapshots.getCompound(version)));
        }
        if (changed) {
            saved.put("snapshots", snapshots);
            writeSnapshot(sidecar.toPath(), saved);
        }
        biomeSnapshots = resolved;
    }

    private static Map<Integer, String> resolveBiomes(CompoundNBT registry) {
        Map<Integer, String> result = new LinkedHashMap<>();
        ListNBT entries = registry.getList("ids", 10);
        java.util.Set<Integer> numbers = new java.util.HashSet<>();
        java.util.Set<String> names = new java.util.HashSet<>();

        if (entries.isEmpty() || !(registry.get("ids") instanceof ListNBT)
                || ((ListNBT) registry.get("ids")).size() != entries.size()) {
            throw new IllegalStateException("Malformed saved BOP biome registry table");
        }

        for (int index = 0; index < entries.size(); index++) {
            CompoundNBT entry = entries.getCompound(index);
            String source = entry.getString("K");
            int oldId = entry.getInt("V");

            if (!entry.contains("K", 8) || !entry.contains("V", 3) || oldId < 0
                    || !numbers.add(oldId) || !names.add(source)) {
                throw new IllegalStateException("Invalid or duplicate saved biome identity: " + entry);
            }

            if (!source.startsWith("biomesoplenty:")) {
                continue;
            }

            String target = BIOMES.getOrDefault(source, source);
            Biome biome = ForgeRegistries.BIOMES.getValue(new ResourceLocation(target));

            if (biome == null) {
                throw new IllegalStateException("No safe named BOP biome replacement for " + source);
            }

            result.put(oldId, target);
        }
        return Collections.unmodifiableMap(result);
    }

    private static void writeSnapshot(Path target, CompoundNBT saved) throws IOException {
        Files.createDirectories(target.getParent());

        Path temporary = Files.createTempFile(target.getParent(), ".bop-named-registry-", ".tmp");

        try {
            try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                CompressedStreamTools.writeCompressed(saved, output);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static boolean relevant(INBT value) {
        return relevant(value, 0, false);
    }

    private static boolean relevant(INBT value, int depth, boolean blockState) {
        depth(depth);
        if (value instanceof CompoundNBT) {
            CompoundNBT tag = (CompoundNBT) value;

            if (blockState && BLOCKS.containsKey(tag.getString("Name"))
                    || tag.contains("Count", 99) && ITEMS.containsKey(tag.getString("id"))
                    || biomeTable(tag) != null) {
                return true;
            }
            for (String key : tag.keySet()) {
                if (relevant(tag.get(key), depth + 1, stateField(key))) {
                    return true;
                }
            }
        } else if (value instanceof ListNBT) {
            for (INBT child : (ListNBT) value) {
                if (relevant(child, depth + 1, blockState)) {
                    return true;
                }
            }
        }
        return false;
    }

    static void rewrite(CompoundNBT root) {
        rewrite(root, 0, false);
    }

    private static void rewrite(INBT value, int depth, boolean blockState) {
        depth(depth);
        if (value instanceof CompoundNBT) {
            CompoundNBT tag = (CompoundNBT) value;
            String name = tag.getString("Name");

            if (blockState && BLOCKS.containsKey(name)) {
                rewriteState(tag, BLOCKS.get(name));
            }
            if (tag.contains("Count", 99) && ITEMS.containsKey(tag.getString("id"))) {
                String target = ITEMS.get(tag.getString("id"));
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(target));

                if (item == null || item == Items.AIR) {
                    throw new IllegalStateException("Missing named BOP item replacement: " + target);
                }
                tag.putString("id", target);
            }

            Map<Integer, String> biomes = biomeTable(tag);

            if (biomes != null) {
                CompoundNBT level = tag.getCompound("Level");
                int[] values = level.getIntArray("Biomes");
                Map<Integer, Integer> currentIds = new LinkedHashMap<>();

                if (values.length != 256 && values.length != 1024) {
                    throw new IllegalStateException("Malformed saved BOP biome array: " + values.length + " cells");
                }

                // Forge restores a world's numeric registry after AboutToStart. Resolve names at the chunk read.
                biomes.forEach((source, target) -> {
                    Biome biome = ForgeRegistries.BIOMES.getValue(new ResourceLocation(target));
                    int currentId = biome == null ? -1 : Registry.BIOME.getId(biome);

                    if (currentId < 0) {
                        throw new IllegalStateException("Missing current BOP biome replacement: " + target);
                    }
                    currentIds.put(source, currentId);
                });

                for (int index = 0; index < values.length; index++) {
                    values[index] = currentIds.getOrDefault(values[index], values[index]);
                }
                level.putIntArray("Biomes", values);
                // The early inventory wrapper can see the same tag again before vanilla updates DataVersion.
                level.putInt(BIOME_MARKER, SharedConstants.getVersion().getWorldVersion());
            }
            for (String key : new ArrayList<>(tag.keySet())) {
                rewrite(tag.get(key), depth + 1, stateField(key));
            }
        } else if (value instanceof ListNBT) {
            for (INBT child : (ListNBT) value) {
                rewrite(child, depth + 1, blockState);
            }
        }
    }

    private static boolean stateField(String key) {
        // A custom display name can also be a registry-looking string. Only native state fields identify blocks.
        return key.equals("Palette") || key.equals("palette") || key.equals("BlockState")
                || key.equals("blockState") || key.equals("carriedBlockState") || key.equals("DisplayState");
    }

    private static Map<Integer, String> biomeTable(CompoundNBT root) {
        int version = root.getInt("DataVersion");
        CompoundNBT level = root.getCompound("Level");

        if (version < 1451 || !level.contains("Biomes", 11)) {
            return null;
        }

        int current = SharedConstants.getVersion().getWorldVersion();

        if (version >= current || level.getInt(BIOME_MARKER) >= current) {
            return null;
        }

        Map.Entry<Integer, Map<Integer, String>> snapshot = biomeSnapshots.ceilingEntry(version);

        return snapshot == null || snapshot.getValue().isEmpty() ? null : snapshot.getValue();
    }

    private static void rewriteState(CompoundNBT saved, String target) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(target));

        if (block == null || block == Blocks.AIR) {
            throw new IllegalStateException("Missing named BOP block replacement: " + target);
        }

        BlockState state = block.getDefaultState();
        CompoundNBT properties = saved.getCompound("Properties");

        if (saved.contains("Properties") && !saved.contains("Properties", 10)) {
            throw new IllegalStateException("Malformed saved BOP block properties: " + saved);
        }
        for (String name : properties.keySet()) {
            IProperty<?> property = block.getStateContainer().getProperty(name);

            if (property == null || !properties.contains(name, 8)) {
                throw new IllegalStateException("No safe " + name + " property on replacement " + target);
            }
            state = withProperty(state, property, properties.getString(name));
        }
        if (state.has(net.minecraft.block.SlabBlock.TYPE)
                && state.get(net.minecraft.block.SlabBlock.TYPE) == net.minecraft.state.properties.SlabType.DOUBLE) {
            if (state.get(net.minecraft.block.SlabBlock.WATERLOGGED)) {
                throw new IllegalStateException("Cannot safely normalize a waterlogged double ethereal slab. "
                        + "Restore the backup and remove that invalid combination before upgrading.");
            }
            state = Blocks.BIRCH_PLANKS.getDefaultState();
        }

        CompoundNBT result = NBTUtil.writeBlockState(state);

        saved.putString("Name", result.getString("Name"));
        if (result.contains("Properties", 10)) {
            saved.put("Properties", result.getCompound("Properties"));
        } else {
            saved.remove("Properties");
        }
    }

    private static <T extends Comparable<T>> BlockState withProperty(BlockState state, IProperty<T> property,
            String value) {
        return state.with(property, property.parseValue(value).orElseThrow(
                () -> new IllegalStateException("Invalid saved " + property.getName() + "=" + value)));
    }

    private static void depth(int depth) {
        if (depth > 64) {
            throw new IllegalStateException("Saved named BOP content exceeds 64 nesting levels");
        }
    }

    // Raw tags are converted before deserialization. Ignoring these old registry entries avoids aliasing
    // two identities onto an existing object, which would make mixed old/current saves ambiguous.
    @SubscribeEvent
    public static void missingBlocks(RegistryEvent.MissingMappings<Block> event) {
        event.getAllMappings().stream().filter(mapping -> BLOCKS.containsKey(mapping.key.toString()))
                .forEach(RegistryEvent.MissingMappings.Mapping::ignore);
    }

    @SubscribeEvent
    public static void missingItems(RegistryEvent.MissingMappings<Item> event) {
        event.getAllMappings().stream().filter(mapping -> ITEMS.containsKey(mapping.key.toString()))
                .forEach(RegistryEvent.MissingMappings.Mapping::ignore);
    }

    @SubscribeEvent
    public static void missingBiomes(RegistryEvent.MissingMappings<Biome> event) {
        event.getAllMappings().stream().filter(mapping -> BIOMES.containsKey(mapping.key.toString()))
                .forEach(RegistryEvent.MissingMappings.Mapping::ignore);
    }
}
