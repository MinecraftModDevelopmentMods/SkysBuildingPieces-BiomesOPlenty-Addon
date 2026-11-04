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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Recovers named BOP content removed after flattening, before native loaders read it. */
@Mod.EventBusSubscriber(modid = SkysBuildingPiecesBop.ID)
public final class NamedBopRecovery {
    private static final Map<String, String> BLOCKS = mappings("blocks");
    private static final Map<String, String> ITEMS = itemMappings();
    private static final Map<String, String> BIOMES = mappings("biomes");
    private static final Map<String, String> SECTION_BIOMES = mappings("section_biomes");
    private static final int SECTION_BIOMES_RELEASE = 2860;
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

        // The wall sign only existed as a placed block. Its item is cherry_sign.
        result.remove("biomesoplenty:cherry_wall_sign");
        result.putAll(mappings("items"));
        return Collections.unmodifiableMap(result);
    }

    /** Keep earlier numeric biome tables available for chunks not opened during the first upgrade. */
    public static synchronized void begin(MinecraftServer server) throws IOException {
        File level = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.LEVEL_DATA_FILE).toFile();
        File sidecar = new File(level.getParentFile(), "data/skysbuildingpiecesbop_named_registry.dat");
        CompoundTag saved = new CompoundTag();

        saved.putInt("schema_version", 1);
        if (sidecar.isFile()) {
            try (FileInputStream input = new FileInputStream(sidecar)) {
                saved = NbtIo.readCompressed(input);
            }
            if (saved.getInt("schema_version") != 1) {
                throw new IllegalStateException("Unsupported named BOP registry snapshot schema");
            }
        }

        CompoundTag snapshots = saved.getCompound("snapshots");
        boolean changed = false;

        CompoundTag original = zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge.sourceBiomeSnapshot(server);

        if (original.isEmpty() && level.isFile()) {
            CompoundTag root;

            try (FileInputStream input = new FileInputStream(level)) {
                root = NbtIo.readCompressed(input);
            }

            original.putInt("DataVersion", root.getCompound("Data").getInt("DataVersion"));
            CompoundTag registries = root.getCompound("fml").getCompound("Registries");
            CompoundTag registry = registries.getCompound("minecraft:worldgen/biome");
            if (registry.isEmpty()) {
                registry = registries.getCompound("minecraft:biome");
            }
            original.put("Registry", registry.copy());
        }

        int sourceVersion = original.getInt("DataVersion");

        if (sourceVersion >= 1451 && sourceVersion < SharedConstants.getCurrentVersion().getDataVersion().getVersion()) {
            CompoundTag registry = original.getCompound("Registry");
            Tag ids = registry.get("ids");
            // Named sections can leave Forge's numeric registry empty. Keep
            // older sidecar tables for unopened chunks that still use numbers.
            boolean namedOnly = sourceVersion >= SECTION_BIOMES_RELEASE
                    && ids instanceof ListTag entries && entries.isEmpty();

            if (ids instanceof ListTag && !namedOnly) {
                String key = Integer.toString(sourceVersion);

                if (snapshots.contains(key, 10) && !snapshots.getCompound(key).equals(registry)) {
                    throw new IllegalStateException("Conflicting BOP biome tables for save version " + sourceVersion
                            + ". Restore the original world backup and mod list before upgrading.");
                }
                if (!snapshots.contains(key, 10)) {
                    snapshots.put(key, registry.copy());
                    changed = true;
                }
            }
        }

        NavigableMap<Integer, Map<Integer, String>> resolved = new TreeMap<>();

        for (String version : snapshots.getAllKeys()) {
            resolved.put(Integer.parseInt(version), resolveBiomes(snapshots.getCompound(version)));
        }
        if (changed) {
            saved.put("snapshots", snapshots);
            writeSnapshot(sidecar.toPath(), saved);
        }
        biomeSnapshots = resolved;
    }

    private static Registry<Biome> biomeRegistry() {
        // The active world's registry owns the saved numeric IDs, not Forge's registration objects.
        return net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
    }

    private static Map<Integer, String> resolveBiomes(CompoundTag registry) {
        Map<Integer, String> result = new LinkedHashMap<>();
        ListTag entries = registry.getList("ids", 10);
        java.util.Set<Integer> numbers = new java.util.HashSet<>();
        java.util.Set<String> names = new java.util.HashSet<>();

        if (entries.isEmpty() || !(registry.get("ids") instanceof ListTag)
                || ((ListTag) registry.get("ids")).size() != entries.size()) {
            throw new IllegalStateException("Malformed saved BOP biome registry table");
        }

        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
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
            Biome biome = biomeRegistry().get(new ResourceLocation(target));

            if (biome == null) {
                throw new IllegalStateException("No safe named BOP biome replacement for " + source);
            }

            result.put(oldId, target);
        }
        return Collections.unmodifiableMap(result);
    }

    private static void writeSnapshot(Path target, CompoundTag saved) throws IOException {
        Files.createDirectories(target.getParent());

        Path temporary = Files.createTempFile(target.getParent(), ".bop-named-registry-", ".tmp");

        try {
            try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                NbtIo.writeCompressed(saved, output);
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

    static boolean relevant(Tag value) {
        return relevant(value, 0, false);
    }

    private static boolean relevant(Tag value, int depth, boolean blockState) {
        depth(depth);
        if (value instanceof CompoundTag) {
            CompoundTag tag = (CompoundTag) value;

            if (blockState && (BLOCKS.containsKey(tag.getString("Name")) || oldShortCattail(tag)
                    || oldWall(tag.getString("Name"), tag.getCompound("Properties")))
                    || tag.contains("Count", 99) && (ITEMS.containsKey(tag.getString("id"))
                    || oldWall(tag.getString("id"), tag.getCompound("tag").getCompound("BlockStateTag")))
                    || biomeTable(tag) != null || removedSectionBiomes(tag)) {
                return true;
            }
            for (String key : tag.getAllKeys()) {
                if (relevant(tag.get(key), depth + 1, stateField(key))) {
                    return true;
                }
            }
        } else if (value instanceof ListTag) {
            for (Tag child : (ListTag) value) {
                if (relevant(child, depth + 1, blockState)) {
                    return true;
                }
            }
        }
        return false;
    }

    static void rewrite(CompoundTag root) {
        rewrite(root, 0, false);
    }

    private static void rewrite(Tag value, int depth, boolean blockState) {
        depth(depth);
        if (value instanceof CompoundTag) {
            CompoundTag tag = (CompoundTag) value;
            String name = tag.getString("Name");

            ListTag sections = chunkSections(tag);
            retargetCherrySigns(tag, sections);
            for (Tag entry : sections) {
                CompoundTag section = (CompoundTag) entry;
                if (!removedSectionBiome(section)) {
                    continue;
                }
                // Section palettes already store names. Keep their indices and packed cells unchanged.
                ListTag palette = section.getCompound("biomes").getList("palette", Tag.TAG_STRING);
                for (int index = 0; index < palette.size(); index++) {
                    String replacement = SECTION_BIOMES.get(palette.getString(index));
                    if (replacement != null) {
                        palette.set(index, net.minecraft.nbt.StringTag.valueOf(replacement));
                    }
                }
            }

            if (blockState && name.equals("biomesoplenty:nether_crystal")) {
                if (tag.contains("Properties") && !tag.contains("Properties", 10)) {
                    throw new IllegalStateException("Malformed saved nether crystal properties: " + tag);
                }
                CompoundTag candidate = tag.copy();
                candidate.put("Properties", crystalProperties(tag.getCompound("Properties")));
                rewriteState(candidate, BLOCKS.get(name));
                tag.putString("Name", candidate.getString("Name"));
                tag.put("Properties", candidate.getCompound("Properties"));
            } else if (blockState && oldWall(name, tag.getCompound("Properties"))) {
                rewriteWall(tag.getCompound("Properties"));
                rewriteState(tag, BLOCKS.getOrDefault(name, name));
            } else if (blockState && oldShortCattail(tag)) {
                rewriteState(tag, "biomesoplenty:dead_grass");
            } else if (blockState && BLOCKS.containsKey(name)) {
                rewriteState(tag, BLOCKS.get(name));
            }
            if (tag.contains("Count", 99) && ITEMS.containsKey(tag.getString("id"))) {
                if (tag.getString("id").equals("biomesoplenty:nether_crystal")) {
                    CompoundTag itemData = tag.getCompound("tag");
                    if (itemData.contains("BlockStateTag")) {
                        if (!itemData.contains("BlockStateTag", 10)) {
                            throw new IllegalStateException("Malformed nether crystal item properties: " + tag);
                        }
                        itemData.put("BlockStateTag", crystalProperties(itemData.getCompound("BlockStateTag")));
                    }
                }
                String target = ITEMS.get(tag.getString("id"));
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(target));

                if (item == null || item == Items.AIR) {
                    throw new IllegalStateException("Missing named BOP item replacement: " + target);
                }
                tag.putString("id", target);
            }

            if (tag.contains("Count", 99)
                    && oldWall(tag.getString("id"), tag.getCompound("tag").getCompound("BlockStateTag"))) {
                rewriteWall(tag.getCompound("tag").getCompound("BlockStateTag"));
            }

            Map<Integer, String> biomes = biomeTable(tag);

            if (biomes != null) {
                CompoundTag level = tag.getCompound("Level");
                int[] values = level.getIntArray("Biomes");
                int[] originalValues = values.clone();
                Map<Integer, Integer> currentIds = new LinkedHashMap<>();

                if (values.length != 256 && values.length != 1024) {
                    throw new IllegalStateException("Malformed saved BOP biome array: " + values.length + " cells");
                }

                // Forge restores a world's numeric registry after AboutToStart. Resolve names at the chunk read.
                biomes.forEach((source, target) -> {
                    Biome biome = biomeRegistry().get(new ResourceLocation(target));
                    int currentId = biome == null ? -1 : biomeRegistry().getId(biome);

                    if (currentId < 0) {
                        throw new IllegalStateException("Missing current BOP biome replacement: " + target);
                    }
                    currentIds.put(source, currentId);
                });

                for (int index = 0; index < values.length; index++) {
                    values[index] = currentIds.getOrDefault(values[index], values[index]);
                }
                level.putIntArray("Biomes", values);
                zone.moddev.mc.skysbuildingpieces.legacy.HistoricalBiomes.preserveNamed(tag, originalValues, biomes);
                // The early inventory wrapper can see the same tag again before vanilla updates DataVersion.
                level.putInt(BIOME_MARKER, SharedConstants.getCurrentVersion().getDataVersion().getVersion());
            }
            for (String key : new ArrayList<>(tag.getAllKeys())) {
                rewrite(tag.get(key), depth + 1, stateField(key));
            }
        } else if (value instanceof ListTag) {
            for (Tag child : (ListTag) value) {
                rewrite(child, depth + 1, blockState);
            }
        }
    }

    private static boolean oldWall(String name, CompoundTag properties) {
        if (!name.equals("biomesoplenty:white_sandstone_wall") && !name.equals("biomesoplenty:mud_brick_wall")
                && !name.equals("minecraft:mud_brick_wall")) {
            return false;
        }
        for (String direction : new String[] {"north", "south", "east", "west"}) {
            String value = properties.getString(direction);
            if (value.equals("true") || value.equals("false")) {
                return true;
            }
        }
        return false;
    }

    private static void retargetCherrySigns(CompoundTag chunk, ListTag sections) {
        if (sections.isEmpty()) {
            return;
        }
        for (Tag entry : chunk.getList("block_entities", Tag.TAG_COMPOUND)) {
            CompoundTag sign = (CompoundTag) entry;
            if (!sign.getString("id").equals("biomesoplenty:sign")) {
                continue;
            }
            int x = sign.getInt("x");
            int y = sign.getInt("y");
            int z = sign.getInt("z");
            for (Tag sectionEntry : sections) {
                CompoundTag section = (CompoundTag) sectionEntry;
                if (section.getByte("Y") != (y >> 4)) {
                    continue;
                }
                String block = savedSignBlock(section.getCompound("block_states"), x, y, z);
                if (isCherrySign(block)) {
                    // Vanilla's sign fixer converts the old text, filtering,
                    // dye and glow once the block entity has the vanilla ID.
                    sign.putString("id", "minecraft:sign");
                }
                break;
            }
        }
    }

    private static String savedSignBlock(CompoundTag states, int x, int y, int z) {
        ListTag palette = states.getList("palette", Tag.TAG_COMPOUND);
        if (palette.stream().map(entry -> ((CompoundTag) entry).getString("Name"))
                .noneMatch(NamedBopRecovery::isCherrySign)) {
            return "";
        }
        if (palette.size() == 1) {
            return palette.getCompound(0).getString("Name");
        }
        int bits = Math.max(4, Integer.SIZE - Integer.numberOfLeadingZeros(palette.size() - 1));
        int entriesPerWord = Long.SIZE / bits;
        int index = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
        long[] storage = states.getLongArray("data");
        if (index / entriesPerWord >= storage.length) {
            throw new IllegalStateException("Incomplete saved block palette for BOP sign at " + x + ", " + y + ", " + z);
        }
        int paletteIndex = (int) (storage[index / entriesPerWord] >>> (index % entriesPerWord * bits) & ((1L << bits) - 1));
        if (paletteIndex >= palette.size()) {
            throw new IllegalStateException("Invalid saved block palette index for BOP sign at " + x + ", " + y + ", " + z);
        }
        return palette.getCompound(paletteIndex).getString("Name");
    }

    private static boolean isCherrySign(String block) {
        return block.equals("biomesoplenty:cherry_sign") || block.equals("biomesoplenty:cherry_wall_sign");
    }

    static CompoundTag crystalProperties(CompoundTag original) {
        for (String property : original.getAllKeys()) {
            if ((!property.equals("face") && !property.equals("facing")) || !original.contains(property, 8)) {
                throw new IllegalStateException("Cannot recover nether crystal property " + property + ": " + original);
            }
        }
        String face = original.contains("face") ? original.getString("face") : "floor";
        String facing = original.contains("facing") ? original.getString("facing") : "north";
        if (!facing.equals("north") && !facing.equals("south") && !facing.equals("east") && !facing.equals("west")) {
            throw new IllegalStateException("Cannot recover nether crystal direction: " + facing);
        }
        String target;
        if (face.equals("floor")) {
            target = "up";
        } else if (face.equals("ceiling")) {
            target = "down";
        } else if (face.equals("wall")) {
            target = facing;
        } else {
            throw new IllegalStateException("Cannot recover nether crystal attachment: " + face);
        }
        // The old floor and ceiling rotations use identical geometry. The new
        // cluster expresses its attachment with one six-direction property.
        CompoundTag result = new CompoundTag();
        result.putString("facing", target);
        result.putString("waterlogged", "false");
        return result;
    }

    private static void rewriteWall(CompoundTag properties) {
        // Vanilla only upgrades its own walls. Keep BOP's old arms, post and water state too.
        for (String direction : new String[] {"north", "south", "east", "west"}) {
            if (!properties.contains(direction)) {
                continue;
            }
            String value = properties.getString(direction);
            if (value.equals("true") || value.equals("false")) {
                properties.putString(direction, value.equals("true") ? "low" : "none");
            } else if (!value.equals("low") && !value.equals("none") && !value.equals("tall")) {
                throw new IllegalStateException("Cannot recover BOP wall connection " + direction + "=" + value);
            }
        }
    }

    private static boolean oldShortCattail(CompoundTag state) {
        // BOP reused this ID for the tall plant. The old one-block state has no half property.
        return state.getString("Name").equals("biomesoplenty:cattail")
                && !state.getCompound("Properties").contains("half");
    }

    private static boolean stateField(String key) {
        // A custom display name can also be a registry-looking string. Only native state fields identify blocks.
        return key.equals("Palette") || key.equals("palette") || key.equals("BlockState")
                || key.equals("blockState") || key.equals("carriedBlockState") || key.equals("DisplayState");
    }

    private static ListTag chunkSections(CompoundTag chunk) {
        if (!chunk.contains("DataVersion", Tag.TAG_ANY_NUMERIC) || !chunk.contains("xPos", Tag.TAG_ANY_NUMERIC)
                || !chunk.contains("zPos", Tag.TAG_ANY_NUMERIC)) {
            return new ListTag();
        }
        return chunk.getList("sections", Tag.TAG_COMPOUND);
    }

    private static boolean removedSectionBiomes(CompoundTag chunk) {
        for (Tag entry : chunkSections(chunk)) {
            if (removedSectionBiome((CompoundTag) entry)) {
                return true;
            }
        }
        return false;
    }

    private static boolean removedSectionBiome(CompoundTag section) {
        if (!section.contains("Y", Tag.TAG_BYTE) || !section.contains("biomes", Tag.TAG_COMPOUND)) {
            return false;
        }
        ListTag palette = section.getCompound("biomes").getList("palette", Tag.TAG_STRING);
        for (int index = 0; index < palette.size(); index++) {
            if (SECTION_BIOMES.containsKey(palette.getString(index))) {
                return true;
            }
        }
        return false;
    }

    private static Map<Integer, String> biomeTable(CompoundTag root) {
        int version = root.getInt("DataVersion");
        CompoundTag level = root.getCompound("Level");

        if (version < 1451 || !level.contains("Biomes", 11)) {
            return null;
        }

        int current = SharedConstants.getCurrentVersion().getDataVersion().getVersion();

        if (version >= current || level.getInt(BIOME_MARKER) >= current) {
            return null;
        }

        Map.Entry<Integer, Map<Integer, String>> snapshot = biomeSnapshots.ceilingEntry(version);

        return snapshot == null || snapshot.getValue().isEmpty() ? null : snapshot.getValue();
    }

    private static void rewriteState(CompoundTag saved, String target) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(target));

        if (block == null || block == Blocks.AIR) {
            throw new IllegalStateException("Missing named BOP block replacement: " + target);
        }

        BlockState state = block.defaultBlockState();
        CompoundTag properties = saved.getCompound("Properties");

        if (saved.contains("Properties") && !saved.contains("Properties", 10)) {
            throw new IllegalStateException("Malformed saved BOP block properties: " + saved);
        }
        for (String name : properties.getAllKeys()) {
            Property<?> property = block.getStateDefinition().getProperty(name);

            if (property == null || !properties.contains(name, 8)) {
                throw new IllegalStateException("No safe " + name + " property on replacement " + target);
            }
            state = withProperty(state, property, properties.getString(name));
        }
        if (state.hasProperty(net.minecraft.world.level.block.SlabBlock.TYPE)
                && state.getValue(net.minecraft.world.level.block.SlabBlock.TYPE) == net.minecraft.world.level.block.state.properties.SlabType.DOUBLE) {
            if (state.getValue(net.minecraft.world.level.block.SlabBlock.WATERLOGGED)) {
                throw new IllegalStateException("Cannot safely normalize a waterlogged double slab: " + target + ". "
                        + "Restore the backup and remove that invalid combination before upgrading.");
            }
            if (state.is(Blocks.BIRCH_SLAB)) {
                state = Blocks.BIRCH_PLANKS.defaultBlockState();
            } else if (state.is(Blocks.CHERRY_SLAB)) {
                state = Blocks.CHERRY_PLANKS.defaultBlockState();
            } else if (state.is(Blocks.MUD_BRICK_SLAB)) {
                state = Blocks.MUD_BRICKS.defaultBlockState();
            } else {
                throw new IllegalStateException("No full-block replacement for double slab " + target);
            }
        }

        CompoundTag result = NbtUtils.writeBlockState(state);

        saved.putString("Name", result.getString("Name"));
        if (result.contains("Properties", 10)) {
            saved.put("Properties", result.getCompound("Properties"));
        } else {
            saved.remove("Properties");
        }
    }

    private static <T extends Comparable<T>> BlockState withProperty(BlockState state, Property<T> property,
            String value) {
        return state.setValue(property, property.getValue(value).orElseThrow(
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
    public static void missingBlocks(MissingMappingsEvent event) {
        if (!event.getKey().equals(ForgeRegistries.Keys.BLOCKS)) return;
        event.getAllMappings(ForgeRegistries.Keys.BLOCKS).stream().filter(mapping -> BLOCKS.containsKey(mapping.getKey().toString()))
                .forEach(MissingMappingsEvent.Mapping::ignore);
    }

    @SubscribeEvent
    public static void missingItems(MissingMappingsEvent event) {
        if (!event.getKey().equals(ForgeRegistries.Keys.ITEMS)) return;
        event.getAllMappings(ForgeRegistries.Keys.ITEMS).stream().filter(mapping -> ITEMS.containsKey(mapping.getKey().toString()))
                .forEach(MissingMappingsEvent.Mapping::ignore);
    }

    @SubscribeEvent
    public static void missingBiomes(MissingMappingsEvent event) {
        if (!event.getKey().equals(net.minecraft.core.registries.Registries.BIOME)) return;
        event.getAllMappings(net.minecraft.core.registries.Registries.BIOME).stream().filter(mapping -> BIOMES.containsKey(mapping.getKey().toString()))
                .forEach(MissingMappingsEvent.Mapping::ignore);
    }
}
