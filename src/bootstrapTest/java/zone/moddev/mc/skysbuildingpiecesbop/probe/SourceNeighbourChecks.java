package zone.moddev.mc.skysbuildingpiecesbop.probe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Follow vanilla's door and chest updates in the old range fixtures. */
final class SourceNeighbourChecks {
    private static final Map<BlockPos, BlockState> SOURCE_CONTAINERS = new LinkedHashMap<>();
    private static final Map<BlockPos, BlockPos> CONTAINER_POSITIONS = new LinkedHashMap<>();

    private SourceNeighbourChecks() {
    }

    static Map<BlockPos, java.util.List<BlockState>> expectedDoors(ServerLevel world, JsonObject aliases) throws Exception {
        SOURCE_CONTAINERS.clear();
        CONTAINER_POSITIONS.clear();
        Map<BlockPos, java.util.List<BlockState>> result = new LinkedHashMap<>();
        Path path = Path.of("forward-neighbours.json");
        if (!Files.isRegularFile(path)) return result;
        JsonObject controls = new JsonParser().parse(Files.readString(path)).getAsJsonObject();
        JsonObject fixture = new JsonParser().parse(Files.readString(Path.of("forward-fixture.json"))).getAsJsonObject();
        require(controls.get("source_world_sha256").equals(fixture.get("archive_sha256")),
                "The source door controls belong to a different fixture");
        require(controls.get("source_minecraft").equals(fixture.get("source_minecraft")),
                "The source door controls belong to a different Minecraft version");
        for (JsonElement entry : controls.getAsJsonArray("containers")) {
            JsonObject row = entry.getAsJsonObject();
            BlockPos position = new BlockPos(row.get("x").getAsInt(), row.get("y").getAsInt(), row.get("z").getAsInt());
            require(SOURCE_CONTAINERS.put(position, state(row.get("state").getAsString(), aliases)) == null,
                    "Repeated source container position");
        }
        for (JsonElement entry : controls.getAsJsonArray("doors")) {
            JsonObject row = entry.getAsJsonObject();
            BlockPos position = new BlockPos(row.get("x").getAsInt(), row.get("y").getAsInt(), row.get("z").getAsInt());
            BlockState source = state(row.get("source").getAsString(), aliases);
            BlockState neighbour = state(row.get("neighbour").getAsString(), aliases);
            Direction side = Direction.byName(row.get("side").getAsString());
            require(source.getBlock() instanceof DoorBlock && neighbour.getBlock() == source.getBlock(),
                    "A door control changed block identity");
            require(side == Direction.UP || side == Direction.DOWN, "A door control has no paired half");
            require(source.getValue(DoorBlock.HALF) != neighbour.getValue(DoorBlock.HALF),
                    "A door control does not contain opposite halves");
            BlockPos lowerPosition = side == Direction.UP ? position : position.below();
            BlockState lower = side == Direction.UP ? source : neighbour;
            BlockState upper = side == Direction.UP ? neighbour : source;
            // These source halves disagree. A pending update can leave the original state,
            // and either half can be validated first as neighbouring chunks become ready.
            BlockState lowerFirst = lower.updateShape(Direction.UP, upper, world, lowerPosition, lowerPosition.above());
            BlockState upperAfterLower = upper.updateShape(Direction.DOWN, lowerFirst, world,
                    lowerPosition.above(), lowerPosition);
            BlockState upperFirst = upper.updateShape(Direction.DOWN, lower, world,
                    lowerPosition.above(), lowerPosition);
            BlockState lowerAfterUpper = lower.updateShape(Direction.UP, upperFirst, world,
                    lowerPosition, lowerPosition.above());
            var expected = java.util.List.of(source, side == Direction.UP ? lowerFirst : upperAfterLower,
                    side == Direction.UP ? lowerAfterUpper : upperFirst);
            require(result.put(position, expected) == null, "Repeated source door position");
        }
        return result;
    }

    static BlockPos containerPosition(ServerLevel world, BlockPos position) {
        if (CONTAINER_POSITIONS.containsKey(position)) return CONTAINER_POSITIONS.get(position);
        BlockPos storage = position;
        BlockState source = SOURCE_CONTAINERS.get(position);
        if (source != null) {
            BlockState current = world.getBlockState(position);
            require(source.getBlock() == current.getBlock(), "A source container changed identity");
            var type = net.minecraft.world.level.block.ChestBlock.TYPE;
            var facing = net.minecraft.world.level.block.ChestBlock.FACING;
            require(source.getValue(facing) == current.getValue(facing), "A source chest changed facing");
            if (source.getValue(type) == net.minecraft.world.level.block.state.properties.ChestType.SINGLE
                    && current.getValue(type) != net.minecraft.world.level.block.state.properties.ChestType.SINGLE
                    && (source.getValue(facing) == Direction.NORTH || source.getValue(facing) == Direction.EAST)) {
                // Vanilla's chest upgrader swaps contents when it joins north/east-facing single chests.
                storage = position.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(current));
                BlockState otherSource = SOURCE_CONTAINERS.get(storage);
                BlockState other = world.getBlockState(storage);
                require(otherSource != null && otherSource.getBlock() == source.getBlock()
                        && otherSource.getValue(type) == net.minecraft.world.level.block.state.properties.ChestType.SINGLE
                        && otherSource.getValue(facing) == source.getValue(facing), "The joined chest has no matching source half");
                require(other.getBlock() == current.getBlock() && other.getValue(type) == current.getValue(type).getOpposite()
                        && storage.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(other)).equals(position),
                        "The joined chest has inconsistent halves");
            }
        }
        require(!CONTAINER_POSITIONS.containsValue(storage), "Two source containers resolve to one inventory");
        CONTAINER_POSITIONS.put(position, storage);
        return storage;
    }

    private static BlockState state(String text, JsonObject aliases) throws Exception {
        var saved = TagParser.parseTag(text);
        String name = saved.getString("Name");
        if (aliases.has(name)) saved.putString("Name", aliases.get(name).getAsString());
        return NbtUtils.readBlockState(saved);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
