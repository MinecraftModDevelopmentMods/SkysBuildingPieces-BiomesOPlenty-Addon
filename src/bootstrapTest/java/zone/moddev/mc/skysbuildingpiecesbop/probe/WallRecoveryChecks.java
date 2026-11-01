package zone.moddev.mc.skysbuildingpiecesbop.probe;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.SharedConstants;
import net.minecraft.util.datafix.fixes.References;
import zone.moddev.mc.skysbuildingpiecesbop.LegacyItemRecovery;

/** The old native BOP wall states must retain every arm through vanilla's real state fixer. */
final class WallRecoveryChecks {
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger();

    static void run(MinecraftServer server) throws Exception {
        JsonObject source;
        try (InputStreamReader input = new InputStreamReader(WallRecoveryChecks.class.getResourceAsStream(
                "/fixtures/bop-1.15.2-native-catalogue.json"), StandardCharsets.UTF_8)) {
            source = new Gson().fromJson(input, JsonObject.class);
        }
        int checked = 0;
        for (JsonElement entry : source.getAsJsonArray("blocks")) {
            JsonObject block = entry.getAsJsonObject();
            String name = block.get("id").getAsString();
            if (!name.equals("biomesoplenty:white_sandstone_wall")
                    && !name.equals("biomesoplenty:mud_brick_wall")) continue;
            for (JsonElement value : block.getAsJsonArray("states")) {
                CompoundTag state = TagParser.parseTag(value.getAsString());
                CompoundTag root = palette(state.copy());
                new LegacyItemRecovery().fixTagCompound(root);
                CompoundTag prepared = root.getCompound("Level").getList("Sections", 10).getCompound(0)
                        .getList("Palette", 10).getCompound(0);
                CompoundTag fixed = (CompoundTag) server.getFixerUpper().update(References.BLOCK_STATE,
                        new Dynamic<>(NbtOps.INSTANCE, prepared), 2230,
                        SharedConstants.getCurrentVersion().getWorldVersion()).getValue();
                BlockState actual = NbtUtils.readBlockState(fixed);
                CompoundTag result = NbtUtils.writeBlockState(actual).getCompound("Properties");
                CompoundTag original = state.getCompound("Properties");
                for (String direction : new String[] {"north", "south", "east", "west"}) {
                    String expected = original.getString(direction).equals("true") ? "low" : "none";
                    if (!result.getString(direction).equals(expected)) {
                        throw new AssertionError("Native BOP wall lost its " + direction + " arm: " + state + " -> " + fixed);
                    }
                }
                for (String property : new String[] {"up", "waterlogged"}) {
                    if (!result.getString(property).equals(original.getString(property))) {
                        throw new AssertionError("Native BOP wall changed " + property + ": " + state);
                    }
                }
                CompoundTag stack = new CompoundTag();
                stack.putString("id", name);
                stack.putByte("Count", (byte) 37);
                stack.putByte("Slot", (byte) 11);
                CompoundTag itemData = new CompoundTag();
                itemData.put("BlockStateTag", original.copy());
                itemData.putString("custom_data", "Keep this exactly");
                stack.put("tag", itemData);
                new LegacyItemRecovery().fixTagCompound(stack);
                if (stack.getByte("Count") != 37 || stack.getByte("Slot") != 11
                        || !stack.getCompound("tag").getString("custom_data").equals("Keep this exactly")) {
                    throw new AssertionError("BOP wall item lost its count, slot or custom data");
                }
                for (String direction : new String[] {"north", "south", "east", "west"}) {
                    String expected = original.getString(direction).equals("true") ? "low" : "none";
                    if (!stack.getCompound("tag").getCompound("BlockStateTag").getString(direction).equals(expected)) {
                        throw new AssertionError("BOP wall item lost its " + direction + " connection");
                    }
                }
                CompoundTag once = stack.copy();
                new LegacyItemRecovery().fixTagCompound(stack);
                if (!once.equals(stack)) {
                    throw new AssertionError("BOP wall item changed on reload");
                }
                CompoundTag before = root.copy();
                new LegacyItemRecovery().fixTagCompound(root);
                if (!before.equals(root)) throw new AssertionError("BOP wall recovery repeated");
                checked++;
            }
        }
        if (checked != 128) throw new AssertionError("Native wall coverage changed: " + checked);
        LOGGER.info("BOP_NATIVE_WALL_RECOVERY_PASS states=" + checked);
    }

    private static CompoundTag palette(CompoundTag state) {
        ListTag palette = new ListTag();
        palette.add(state);
        CompoundTag section = new CompoundTag();
        section.put("Palette", palette);
        ListTag sections = new ListTag();
        sections.add(section);
        CompoundTag level = new CompoundTag();
        level.put("Sections", sections);
        CompoundTag root = new CompoundTag();
        root.put("Level", level);
        return root;
    }
}
