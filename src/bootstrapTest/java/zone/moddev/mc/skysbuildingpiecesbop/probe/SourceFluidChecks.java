package zone.moddev.mc.skysbuildingpiecesbop.probe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.ChunkStorage;

/** Check saved water before ticking can move the range fixture's unsupported flows. */
final class SourceFluidChecks {
    private SourceFluidChecks() {
    }

    static Set<BlockPos> verify(ServerLevel world) throws Exception {
        JsonObject fixture = new JsonParser().parse(Files.readString(Path.of("forward-fixture.json")))
                .getAsJsonObject();
        Set<BlockPos> flowing = new HashSet<>();
        try (var input = SourceFluidChecks.class.getResourceAsStream("/fixtures/biomes/fluids/index.json")) {
            var controls = new JsonParser().parse(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonArray();
            for (JsonElement entry : controls) {
                JsonObject receipt = entry.getAsJsonObject();
                if (!receipt.get("source_world_sha256").equals(fixture.get("archive_sha256"))) continue;
                require(receipt.get("source_minecraft").equals(fixture.get("source_minecraft")),
                        "Water controls belong to a different Minecraft version");
                Path temporary = Files.createTempDirectory(Path.of("."), "source-water-fixer-");
                int cells = 0;
                try (ChunkStorage storage = new ChunkStorage(temporary, world.getServer().getFixerUpper(), false)) {
                    for (JsonElement chunkEntry : receipt.getAsJsonArray("chunks")) {
                        JsonObject chunk = chunkEntry.getAsJsonObject();
                        byte[] bytes;
                        try (var source = SourceFluidChecks.class.getResourceAsStream(
                                "/fixtures/biomes/fluids/" + chunk.get("file").getAsString())) {
                            bytes = source.readAllBytes();
                        }
                        require(HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256")
                                .digest(bytes)).equals(chunk.get("sha256").getAsString()), "Saved water chunk changed");
                        CompoundTag source = NbtIo.readCompressed(new ByteArrayInputStream(bytes));
                        CompoundTag before = source.copy();
                        CompoundTag fixed = storage.upgradeChunkTag(Level.OVERWORLD, () -> null,
                                source.copy(), java.util.Optional.empty());
                        require(source.equals(before), "The independent source water tag changed");
                        for (JsonElement positionEntry : chunk.getAsJsonArray("positions")) {
                            JsonObject row = positionEntry.getAsJsonObject();
                            BlockPos position = new BlockPos(row.get("x").getAsInt(), row.get("y").getAsInt(),
                                    row.get("z").getAsInt());
                            CompoundTag expected = TagParser.parseTag(row.get("state").getAsString());
                            require(NbtUtils.readBlockState(expected) == NbtUtils.readBlockState(state(fixed, position)),
                                    "Saved water was lost during native data fixing at " + position);
                            if (Integer.parseInt(expected.getCompound("Properties").getString("level")) > 0) {
                                require(flowing.add(position), "Repeated source water position");
                            }
                            cells++;
                        }
                    }
                }
                require(cells == 64 && flowing.size() == 60, "Incomplete native water conversion check");
                org.apache.logging.log4j.LogManager.getLogger().info(
                        "BOP_NATIVE_FLOW_DATAFIX_PASS cells={} flowing={} source_bound=true", cells, flowing.size());
                return flowing;
            }
        }
        return flowing;
    }

    private static CompoundTag state(CompoundTag chunk, BlockPos position) {
        ListTag sections = chunk.getList("sections", 10);
        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            CompoundTag section = sections.getCompound(sectionIndex);
            if (section.getByte("Y") != position.getY() >> 4) continue;
            CompoundTag states = section.getCompound("block_states");
            ListTag palette = states.getList("palette", 10);
            require(!palette.isEmpty(), "Missing upgraded water palette");
            if (palette.size() == 1) return palette.getCompound(0);
            int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
            int perLong = 64 / bits;
            int index = (position.getY() & 15) * 256 + (position.getZ() & 15) * 16 + (position.getX() & 15);
            long number = states.getLongArray("data")[index / perLong] >>> (index % perLong * bits);
            return palette.getCompound((int) (number & ((1L << bits) - 1)));
        }
        throw new AssertionError("Missing upgraded water section at " + position);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
