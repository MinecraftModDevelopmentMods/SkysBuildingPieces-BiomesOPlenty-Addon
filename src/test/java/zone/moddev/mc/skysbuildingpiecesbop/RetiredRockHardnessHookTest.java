package zone.moddev.mc.skysbuildingpiecesbop;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import static org.junit.jupiter.api.Assertions.*;

class RetiredRockHardnessHookTest {
    @Test
    void theNativeHardnessGetterAndPickupTargetStillMatch() throws Exception {
        var state = nativeClass("net/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase");
        var getters = state.methods.stream()
                .filter(method -> method.name.equals("getDestroySpeed")
                        && method.desc.equals("(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"))
                .toList();
        assertEquals(1, getters.size());
        assertEquals(1, java.util.Arrays.stream(getters.getFirst().instructions.toArray())
                .filter(instruction -> instruction.getOpcode() == Opcodes.FRETURN).count());

        var item = nativeClass("net/minecraft/world/entity/item/ItemEntity");
        assertEquals(1, item.methods.stream().filter(method -> method.name.equals("getTarget")
                && method.desc.equals("()Ljava/util/UUID;")).count());
    }

    @Test
    void incompatibleMinecraftHooksFailAtStartup() throws Exception {
        Path resources = Path.of("src/main/resources");
        var config = JsonParser.parseString(Files.readString(resources.resolve("skysbuildingpiecesbop.mixins.json")))
                .getAsJsonObject();
        assertTrue(config.get("required").getAsBoolean());
        assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
        assertEquals(java.util.Set.of("RetiredRockHardnessMixin"),
                config.getAsJsonArray("mixins").asList().stream().map(value -> value.getAsString())
                        .collect(java.util.stream.Collectors.toSet()));
        assertFalse(Files.exists(resources.resolve("META-INF/coremods.json")));
        String metadata = Files.readString(resources.resolve("META-INF/neoforge.mods.toml"));
        assertTrue(metadata.contains("[[mixins]]\nconfig=\"skysbuildingpiecesbop.mixins.json\""));
    }

    private static ClassNode nativeClass(String name) throws Exception {
        try (var stream = RetiredRockHardnessHookTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(stream, name);
            var type = new ClassNode();
            new ClassReader(stream).accept(type, 0);
            return type;
        }
    }
}
