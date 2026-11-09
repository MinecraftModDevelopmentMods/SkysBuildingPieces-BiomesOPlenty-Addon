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
import java.util.Set;
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
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.resources.Identifier;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Recovers named BOP content removed after flattening, before native loaders read it. */
public final class NamedBopRecovery {
    private static final Map<String, String> RECENT_BLOCKS = mappings("renamed_1_21_11");
    private static final Map<String, String> NATIVE_RENAMES = mappings("renamed_26_2");
    private static final Map<String, String> MAPLE_RENAMES = mappings("renamed_26_3");
    private static final Map<String, String> BLOCKS = blockMappings();
    private static final Map<String, String> ITEMS = itemMappings();
    private static final Map<String, String> BIOMES = mappings("biomes");
    private static final Map<String, String> SECTION_BIOMES = mappings("section_biomes");
    private static final Set<String> OLD_BOATS = Set.of("biomesoplenty:boat", "biomesoplenty:chest_boat");
    private static final Set<String> BOAT_WOODS = Set.of("fir", "pine", "maple", "redwood", "mahogany", "jacaranda",
            "palm", "willow", "dead", "magic", "umbran", "hellbark", "empyreal");
    private static final Set<String> IMPLICIT_ITEM_KEYS = Set.of("id", "components", "Slot");
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
        // The planted sapling has a block ID but no corresponding vanilla item.
        result.replaceAll((source, target) -> target.equals("minecraft:potted_poplar_sapling")
                ? "minecraft:poplar_sapling" : target);
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, String> blockMappings() {
        Map<String, String> result = new LinkedHashMap<>(mappings("blocks"));
        result.putAll(RECENT_BLOCKS);
        result.putAll(NATIVE_RENAMES);
        // Resolve older maple names straight to their current blocks.
        result.replaceAll((source, target) -> MAPLE_RENAMES.getOrDefault(target, target));
        result.putAll(MAPLE_RENAMES);
        return Collections.unmodifiableMap(result);
    }

    static String currentMapleBlock(String id) {
        return MAPLE_RENAMES.getOrDefault(id, id);
    }

    static String currentMapleItem(String id) {
        return MAPLE_RENAMES.containsKey(id) ? ITEMS.get(id) : id;
    }

    private static boolean namedItem(CompoundTag tag) {
        String name = tag.getStringOr("id", "");
        if (!ITEMS.containsKey(name)) {
            return false;
        }
        if (tag.get("Count") instanceof net.minecraft.nbt.NumericTag
                || tag.get("count") instanceof net.minecraft.nbt.NumericTag) {
            return true;
        }
        // Modern stacks can omit a count of one. Limit that form to the names
        // known to have been items in the preceding BOP release.
        return (RECENT_BLOCKS.containsKey(name) || NATIVE_RENAMES.containsKey(name) || MAPLE_RENAMES.containsKey(name))
                && IMPLICIT_ITEM_KEYS.containsAll(tag.keySet());
    }

    private static boolean namedBoat(CompoundTag tag) {
        return OLD_BOATS.contains(tag.getStringOr("id", "")) && tag.getList("Pos").isPresent()
                && tag.getIntArray("UUID").isPresent();
    }

    private static void rewriteBoat(CompoundTag boat) {
        String source = boat.getStringOr("id", "");
        String wood = boat.getStringOr("model", "");
        String shape = source.equals("biomesoplenty:chest_boat") ? "_chest_boat" : "_boat";
        Identifier target = Identifier.fromNamespaceAndPath("biomesoplenty", wood + shape);
        if (!BOAT_WOODS.contains(wood) || !ForgeRegistries.ENTITY_TYPES.containsKey(target)) {
            throw new IllegalStateException("No replacement for " + source + " with saved wood type " + wood);
        }
        // New boat types encode the material in the registry ID. Keep the UUID, chest, and passengers.
        boat.putString("id", target.toString());
        boat.remove("model");
    }

    /** Keep earlier numeric biome tables available for chunks not opened during the first upgrade. */
    public static synchronized void begin(MinecraftServer server) throws IOException {
        File level = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.LEVEL_DATA_FILE).toFile();
        File sidecar = new File(level.getParentFile(), "data/skysbuildingpiecesbop_named_registry.dat");
        CompoundTag saved = new CompoundTag();

        saved.putInt("schema_version", 1);
        if (sidecar.isFile()) {
            try (FileInputStream input = new FileInputStream(sidecar)) {
                saved = NbtIo.readCompressed(input, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            }
            if (saved.getIntOr("schema_version", 0) != 1) {
                throw new IllegalStateException("Unsupported named BOP registry snapshot schema");
            }
        }

        CompoundTag snapshots = saved.getCompoundOrEmpty("snapshots");
        boolean changed = false;

        CompoundTag original = zone.moddev.mc.skysbuildingpieces.legacy.LegacyBridge.sourceBiomeSnapshot(server);

        if (original.isEmpty() && level.isFile()) {
            CompoundTag root;

            try (FileInputStream input = new FileInputStream(level)) {
                root = NbtIo.readCompressed(input, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            }

            original.putInt("DataVersion", root.getCompoundOrEmpty("Data").getIntOr("DataVersion", 0));
            CompoundTag registries = root.getCompoundOrEmpty("fml").getCompoundOrEmpty("Registries");
            CompoundTag registry = registries.getCompoundOrEmpty("minecraft:worldgen/biome");
            if (registry.isEmpty()) {
                registry = registries.getCompoundOrEmpty("minecraft:biome");
            }
            original.put("Registry", registry.copy());
        }

        int sourceVersion = original.getIntOr("DataVersion", 0);

        if (sourceVersion >= 1451 && sourceVersion < SharedConstants.getCurrentVersion().dataVersion().version()) {
            CompoundTag registry = original.getCompoundOrEmpty("Registry");
            Tag ids = registry.get("ids");
            // Named sections can leave Forge's numeric registry empty. Keep
            // older sidecar tables for unopened chunks that still use numbers.
            boolean namedOnly = sourceVersion >= SECTION_BIOMES_RELEASE
                    && ids instanceof ListTag entries && entries.isEmpty();

            if (ids instanceof ListTag && !namedOnly) {
                String key = Integer.toString(sourceVersion);

                if ((snapshots.get(key) instanceof net.minecraft.nbt.CompoundTag) && !snapshots.getCompoundOrEmpty(key).equals(registry)) {
                    throw new IllegalStateException("Conflicting BOP biome tables for save version " + sourceVersion
                            + ". Restore the original world backup and mod list before upgrading.");
                }
                if (!(snapshots.get(key) instanceof net.minecraft.nbt.CompoundTag)) {
                    snapshots.put(key, registry.copy());
                    changed = true;
                }
            }
        }

        NavigableMap<Integer, Map<Integer, String>> resolved = new TreeMap<>();

        for (String version : snapshots.keySet()) {
            resolved.put(Integer.parseInt(version), resolveBiomes(snapshots.getCompoundOrEmpty(version)));
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
                .lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
    }

    private static Map<Integer, String> resolveBiomes(CompoundTag registry) {
        Map<Integer, String> result = new LinkedHashMap<>();
        ListTag entries = registry.getListOrEmpty("ids");
        java.util.Set<Integer> numbers = new java.util.HashSet<>();
        java.util.Set<String> names = new java.util.HashSet<>();

        if (entries.isEmpty() || !(registry.get("ids") instanceof ListTag)
                || ((ListTag) registry.get("ids")).size() != entries.size()) {
            throw new IllegalStateException("Malformed saved BOP biome registry table");
        }

        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompoundOrEmpty(index);
            String source = entry.getStringOr("K", "");
            int oldId = entry.getIntOr("V", 0);

            if (!(entry.get("K") instanceof net.minecraft.nbt.StringTag) || !(entry.get("V") instanceof net.minecraft.nbt.IntTag) || oldId < 0
                    || !numbers.add(oldId) || !names.add(source)) {
                throw new IllegalStateException("Invalid or duplicate saved biome identity: " + entry);
            }

            if (!source.startsWith("biomesoplenty:")) {
                continue;
            }

            String target = BIOMES.getOrDefault(source, source);
            Biome biome = biomeRegistry().getValue(Identifier.parse(target));

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
        if (blockState && (BLOCKS.containsKey(savedBlockName(value))
                || RetiredRocks.LEGACY_ID.equals(savedBlockName(value)))) {
            return true;
        }
        if (value instanceof CompoundTag) {
            CompoundTag tag = (CompoundTag) value;

            if (blockState && (oldShortCattail(tag)
                    || oldWall(savedBlockName(tag), tag.getCompoundOrEmpty(propertiesKey(tag))))
                    || (!blockState && namedItem(tag)) || (tag.get("Count") instanceof net.minecraft.nbt.NumericTag)
                    && oldWall(tag.getStringOr("id", ""), tag.getCompoundOrEmpty("tag").getCompoundOrEmpty("BlockStateTag"))
                    || namedBoat(tag) || biomeTable(tag) != null || removedSectionBiomes(tag)
                    || retiredBlockTicks(tag, false)) {
                return true;
            }
            for (String key : tag.keySet()) {
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

    private static Tag rewrite(Tag value, int depth, boolean blockState) {
        depth(depth);
        if (blockState && value instanceof StringTag compact) {
            if (RetiredRocks.LEGACY_ID.equals(compact.value())) {
                return StringTag.valueOf(RetiredRocks.id(0).toString());
            }
            String replacement = BLOCKS.get(compact.value());
            if (replacement != null) {
                CompoundTag state = new CompoundTag();
                state.putString("id", compact.value());
                rewriteState(state, replacement);
                return state;
            }
        }
        if (value instanceof CompoundTag tag) {
            String name = blockState ? savedBlockName(tag) : "";
            String propertiesKey = propertiesKey(tag);

            ListTag sections = chunkSections(tag);
            retiredBlockTicks(tag, true);
            retargetCherrySigns(tag, sections);
            for (Tag entry : sections) {
                CompoundTag section = (CompoundTag) entry;
                if (!removedSectionBiome(section)) {
                    continue;
                }
                // Section palettes already store names. Keep their indices and packed cells unchanged.
                ListTag palette = section.getCompoundOrEmpty("biomes").getListOrEmpty("palette");
                for (int index = 0; index < palette.size(); index++) {
                    String replacement = SECTION_BIOMES.get(palette.getStringOr(index, ""));
                    if (replacement != null) {
                        palette.set(index, net.minecraft.nbt.StringTag.valueOf(replacement));
                    }
                }
            }

            if (RetiredRocks.LEGACY_ID.equals(name)) {
                if (tag.contains(propertiesKey) && !(tag.get(propertiesKey) instanceof CompoundTag)) {
                    throw new IllegalStateException("Malformed saved retired rock properties: " + tag);
                }
                CompoundTag properties = tag.getCompoundOrEmpty(propertiesKey);
                int slot = properties.contains("variant") ? Integer.parseInt(properties.getStringOr("variant", "")) : 0;
                if (slot < 0 || slot >= RetiredRocks.NAMES.length) {
                    throw new IllegalStateException("Unassigned saved retired rock: " + tag);
                }

                String nameKey = tag.contains("Name") ? "Name" : tag.contains("id") ? "id" : "";
                tag.putString(nameKey, RetiredRocks.id(slot).toString());
                tag.remove(propertiesKey);
            } else if (blockState && name.equals("biomesoplenty:nether_crystal")) {
                if (tag.contains(propertiesKey) && !(tag.get(propertiesKey) instanceof net.minecraft.nbt.CompoundTag)) {
                    throw new IllegalStateException("Malformed saved nether crystal properties: " + tag);
                }
                CompoundTag candidate = tag.copy();
                candidate.put(propertiesKey, crystalProperties(tag.getCompoundOrEmpty(propertiesKey)));
                rewriteState(candidate, BLOCKS.get(name));
                return candidate;
            } else if (blockState && oldWall(name, tag.getCompoundOrEmpty(propertiesKey))) {
                rewriteWall(tag.getCompoundOrEmpty(propertiesKey));
                rewriteState(tag, BLOCKS.getOrDefault(name, name));
            } else if (blockState && oldShortCattail(tag)) {
                rewriteState(tag, "biomesoplenty:dead_grass");
            } else if (blockState && BLOCKS.containsKey(name)) {
                rewriteState(tag, BLOCKS.get(name));
            }
            if (namedBoat(tag)) {
                rewriteBoat(tag);
            }
            if (!blockState && namedItem(tag)) {
                if (tag.getStringOr("id", "").equals("biomesoplenty:nether_crystal")) {
                    CompoundTag itemData = tag.getCompoundOrEmpty("tag");
                    if (itemData.contains("BlockStateTag")) {
                        if (!(itemData.get("BlockStateTag") instanceof net.minecraft.nbt.CompoundTag)) {
                            throw new IllegalStateException("Malformed nether crystal item properties: " + tag);
                        }
                        itemData.put("BlockStateTag", crystalProperties(itemData.getCompoundOrEmpty("BlockStateTag")));
                    }
                }
                String target = ITEMS.get(tag.getStringOr("id", ""));
                Item item = ForgeRegistries.ITEMS.getValue(Identifier.parse(target));

                if (item == null || item == Items.AIR) {
                    throw new IllegalStateException("Missing named BOP item replacement: " + target);
                }
                tag.putString("id", target);
            }

            if ((tag.get("Count") instanceof net.minecraft.nbt.NumericTag)
                    && oldWall(tag.getStringOr("id", ""), tag.getCompoundOrEmpty("tag").getCompoundOrEmpty("BlockStateTag"))) {
                rewriteWall(tag.getCompoundOrEmpty("tag").getCompoundOrEmpty("BlockStateTag"));
            }

            Map<Integer, String> biomes = biomeTable(tag);

            if (biomes != null) {
                CompoundTag level = tag.getCompoundOrEmpty("Level");
                int[] values = level.getIntArray("Biomes").orElseThrow();
                int[] originalValues = values.clone();
                Map<Integer, Integer> currentIds = new LinkedHashMap<>();

                if (values.length != 256 && values.length != 1024) {
                    throw new IllegalStateException("Malformed saved BOP biome array: " + values.length + " cells");
                }

                // Forge restores a world's numeric registry after AboutToStart. Resolve names at the chunk read.
                biomes.forEach((source, target) -> {
                    Biome biome = biomeRegistry().getValue(Identifier.parse(target));
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
                level.putInt(BIOME_MARKER, SharedConstants.getCurrentVersion().dataVersion().version());
            }
            for (String key : new ArrayList<>(tag.keySet())) {
                Tag child = tag.get(key);
                Tag replacement = rewrite(child, depth + 1, stateField(key));
                if (child != replacement) {
                    tag.put(key, replacement);
                }
            }
        } else if (value instanceof ListTag list) {
            for (int index = 0; index < list.size(); index++) {
                Tag child = list.get(index);
                Tag replacement = rewrite(child, depth + 1, blockState);
                if (child != replacement) {
                    list.set(index, replacement);
                }
            }
        }
        return value;
    }

    private static boolean oldWall(String name, CompoundTag properties) {
        if (!name.equals("biomesoplenty:white_sandstone_wall") && !name.equals("biomesoplenty:mud_brick_wall")
                && !name.equals("minecraft:mud_brick_wall")) {
            return false;
        }
        for (String direction : new String[] {"north", "south", "east", "west"}) {
            String value = properties.getStringOr(direction, "");
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
        for (Tag entry : chunk.getListOrEmpty("block_entities")) {
            CompoundTag sign = (CompoundTag) entry;
            if (!sign.getStringOr("id", "").equals("biomesoplenty:sign")) {
                continue;
            }
            int x = sign.getIntOr("x", 0);
            int y = sign.getIntOr("y", 0);
            int z = sign.getIntOr("z", 0);
            for (Tag sectionEntry : sections) {
                CompoundTag section = (CompoundTag) sectionEntry;
                if (section.getByteOr("Y", (byte) 0) != (y >> 4)) {
                    continue;
                }
                String block = savedSignBlock(section.getCompoundOrEmpty("block_states"), x, y, z);
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
        ListTag palette = states.getListOrEmpty("palette");
        if (palette.stream().map(NamedBopRecovery::savedBlockName)
                .noneMatch(NamedBopRecovery::isCherrySign)) {
            return "";
        }
        if (palette.size() == 1) {
            return savedBlockName(palette.get(0));
        }
        int bits = Math.max(4, Integer.SIZE - Integer.numberOfLeadingZeros(palette.size() - 1));
        int entriesPerWord = Long.SIZE / bits;
        int index = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
        long[] storage = states.getLongArray("data").orElseGet(() -> new long[0]);
        if (index / entriesPerWord >= storage.length) {
            throw new IllegalStateException("Incomplete saved block palette for BOP sign at " + x + ", " + y + ", " + z);
        }
        int paletteIndex = (int) (storage[index / entriesPerWord] >>> (index % entriesPerWord * bits) & ((1L << bits) - 1));
        if (paletteIndex >= palette.size()) {
            throw new IllegalStateException("Invalid saved block palette index for BOP sign at " + x + ", " + y + ", " + z);
        }
        return savedBlockName(palette.get(paletteIndex));
    }

    private static boolean isCherrySign(String block) {
        return block.equals("biomesoplenty:cherry_sign") || block.equals("biomesoplenty:cherry_wall_sign");
    }

    static CompoundTag crystalProperties(CompoundTag original) {
        for (String property : original.keySet()) {
            if ((!property.equals("face") && !property.equals("facing")) || !(original.get(property) instanceof net.minecraft.nbt.StringTag)) {
                throw new IllegalStateException("Cannot recover nether crystal property " + property + ": " + original);
            }
        }
        String face = original.contains("face") ? original.getStringOr("face", "") : "floor";
        String facing = original.contains("facing") ? original.getStringOr("facing", "") : "north";
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
            String value = properties.getStringOr(direction, "");
            if (value.equals("true") || value.equals("false")) {
                properties.putString(direction, value.equals("true") ? "low" : "none");
            } else if (!value.equals("low") && !value.equals("none") && !value.equals("tall")) {
                throw new IllegalStateException("Cannot recover BOP wall connection " + direction + "=" + value);
            }
        }
    }

    private static boolean oldShortCattail(CompoundTag state) {
        // BOP reused this ID for the tall plant. The old one-block state has no half property.
        return state.getStringOr("Name", "").equals("biomesoplenty:cattail")
                && !state.getCompoundOrEmpty("Properties").contains("half");
    }

    private static String savedBlockName(Tag state) {
        if (state instanceof StringTag name) {
            return name.value();
        }
        if (state instanceof CompoundTag tag) {
            // Default states can be stored as strings, wrapped when mixed with compounds.
            return tag.getStringOr("Name", tag.getStringOr("id", tag.getStringOr("", "")));
        }
        return "";
    }

    private static String propertiesKey(CompoundTag state) {
        return state.contains("Name") ? "Properties" : "properties";
    }

    private static boolean stateField(String key) {
        // A custom display name can also be a registry-looking string. Only native state fields identify blocks.
        return key.equals("Palette") || key.equals("palette") || key.equals("palettes") || key.equals("BlockState")
                || key.equals("blockState") || key.equals("block_state") || key.equals("carriedBlockState") || key.equals("DisplayState");
    }

    private static ListTag chunkSections(CompoundTag chunk) {
        if (!(chunk.get("DataVersion") instanceof net.minecraft.nbt.NumericTag) || !(chunk.get("xPos") instanceof net.minecraft.nbt.NumericTag)
                || !(chunk.get("zPos") instanceof net.minecraft.nbt.NumericTag)) {
            return new ListTag();
        }
        return chunk.getListOrEmpty("sections");
    }

    private static boolean retiredBlockTicks(CompoundTag chunk, boolean update) {
        if (!(chunk.get("xPos") instanceof net.minecraft.nbt.NumericTag)
                || !(chunk.get("zPos") instanceof net.minecraft.nbt.NumericTag)) {
            return false;
        }
        boolean changed = false;
        for (String key : new String[] { "block_ticks", "TileTicks" }) {
            for (Tag value : chunk.getListOrEmpty(key)) {
                if (!(value instanceof CompoundTag tick)) {
                    continue;
                }
                String replacement = BLOCKS.get(tick.getStringOr("i", ""));
                if (replacement != null) {
                    if (!update) {
                        return true;
                    }
                    tick.putString("i", replacement);
                    changed = true;
                }
            }
        }
        return changed;
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
        if (!(section.get("Y") instanceof net.minecraft.nbt.ByteTag) || !(section.get("biomes") instanceof net.minecraft.nbt.CompoundTag)) {
            return false;
        }
        ListTag palette = section.getCompoundOrEmpty("biomes").getListOrEmpty("palette");
        for (int index = 0; index < palette.size(); index++) {
            if (SECTION_BIOMES.containsKey(palette.getStringOr(index, ""))) {
                return true;
            }
        }
        return false;
    }

    private static Map<Integer, String> biomeTable(CompoundTag root) {
        int version = root.getIntOr("DataVersion", 0);
        CompoundTag level = root.getCompoundOrEmpty("Level");

        if (version < 1451 || !(level.get("Biomes") instanceof net.minecraft.nbt.IntArrayTag)) {
            return null;
        }

        int current = SharedConstants.getCurrentVersion().dataVersion().version();

        if (version >= current || level.getIntOr(BIOME_MARKER, 0) >= current) {
            return null;
        }

        Map.Entry<Integer, Map<Integer, String>> snapshot = biomeSnapshots.ceilingEntry(version);

        return snapshot == null || snapshot.getValue().isEmpty() ? null : snapshot.getValue();
    }

    private static void rewriteState(CompoundTag saved, String target) {
        Block block = ForgeRegistries.BLOCKS.getValue(Identifier.parse(target));

        if (block == null || block == Blocks.AIR) {
            throw new IllegalStateException("Missing named BOP block replacement: " + target);
        }

        BlockState state = block.defaultBlockState();
        String propertiesKey = propertiesKey(saved);
        CompoundTag properties = saved.getCompoundOrEmpty(propertiesKey);

        if (saved.contains(propertiesKey) && !(saved.get(propertiesKey) instanceof CompoundTag)) {
            throw new IllegalStateException("Malformed saved BOP block properties: " + saved);
        }
        String source = savedBlockName(saved);
        if (RECENT_BLOCKS.containsKey(source) && source.endsWith("_maple_leaf_pile")) {
            // The old pile covered the whole block. A single litter segment would leave most of it bare.
            Property<?> amount = block.getStateDefinition().getProperty("segment_amount");
            if (amount == null || !properties.isEmpty()) {
                throw new IllegalStateException("Cannot recover the maple leaf pile: " + saved);
            }
            state = withProperty(state, amount, "4");
        }
        for (String name : properties.keySet()) {
            if (name.equals("distance")
                    && (source.equals("biomesoplenty:thermal_calcite")
                    || source.equals("biomesoplenty:thermal_calcite_vent"))) {
                // Sphalerite no longer colours its surface by distance from a vent.
                String distance = properties.getStringOr(name, "");
                if (!distance.matches("[1-5]")) {
                    throw new IllegalStateException("Invalid thermal calcite distance: " + saved);
                }
                continue;
            }
            Property<?> property = block.getStateDefinition().getProperty(name);

            if (property == null || !(properties.get(name) instanceof net.minecraft.nbt.StringTag)) {
                throw new IllegalStateException("No safe " + name + " property on replacement " + target);
            }
            state = withProperty(state, property, properties.getString(name).orElseThrow());
        }
        // BOP still has native double slabs for its renamed rocks. Keep their saved shape.
        if (!NATIVE_RENAMES.containsKey(source)
                && state.hasProperty(net.minecraft.world.level.block.SlabBlock.TYPE)
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

        String nameKey = saved.contains("Name") ? "Name" : "id";
        if (saved.size() == 1 && saved.contains("")) {
            saved.remove("");
        }
        // Keep the input schema so vanilla's next data fix sees the format it expects.
        saved.putString(nameKey, result.getString("id").orElseThrow());
        if (result.get("properties") instanceof CompoundTag resultProperties) {
            saved.put(propertiesKey, resultProperties);
        } else {
            saved.remove(propertiesKey);
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

    public static void missingBlocks(MissingMappingsEvent event) {
        if (!event.getKey().equals(ForgeRegistries.Keys.BLOCKS)) return;
        event.getAllMappings(ForgeRegistries.Keys.BLOCKS).stream().filter(mapping -> BLOCKS.containsKey(mapping.getKey().toString()))
                .forEach(MissingMappingsEvent.Mapping::ignore);
    }

    public static void missingItems(MissingMappingsEvent event) {
        if (!event.getKey().equals(ForgeRegistries.Keys.ITEMS)) return;
        event.getAllMappings(ForgeRegistries.Keys.ITEMS).stream().filter(mapping -> ITEMS.containsKey(mapping.getKey().toString()))
                .forEach(MissingMappingsEvent.Mapping::ignore);
    }

    public static void missingEntities(MissingMappingsEvent event) {
        for (var mapping : event.getAllMappings(ForgeRegistries.Keys.ENTITY_TYPES)) {
            if (OLD_BOATS.contains(mapping.getKey().toString())) {
                mapping.ignore();
            }
        }
    }

    public static boolean ownsRetiredBiome(Identifier id) {
        return BIOMES.containsKey(id.toString());
    }

    public static void missingBiomes(MissingMappingsEvent event) {
        if (!event.getKey().equals(net.minecraft.core.registries.Registries.BIOME)) return;
        event.getAllMappings(net.minecraft.core.registries.Registries.BIOME).stream().filter(mapping -> ownsRetiredBiome(mapping.getKey()))
                .forEach(MissingMappingsEvent.Mapping::ignore);
    }
}
