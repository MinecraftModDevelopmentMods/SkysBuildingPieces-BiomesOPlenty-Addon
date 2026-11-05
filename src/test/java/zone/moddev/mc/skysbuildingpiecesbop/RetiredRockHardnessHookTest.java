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
        assertEquals(1, item.fields.stream().filter(field -> field.name.equals("target")
                && field.desc.equals("Ljava/util/UUID;")).count());
    }

    @Test
    void theMissingMappingHookUsesTheNativeForgeEntryPoint() throws Exception {
        var mapping = nativeClass("net/minecraftforge/registries/MissingMappingsEvent$Mapping");
        assertEquals(1, mapping.methods.stream().filter(method ->
                method.name.equals("remap") && method.desc.equals("(Ljava/lang/Object;)V")).count());
        assertTrue(NamedBopRecovery.ownsRetiredBiome(net.minecraft.resources.Identifier.parse("biomesoplenty:alps")));
        assertFalse(NamedBopRecovery.ownsRetiredBiome(net.minecraft.resources.Identifier.parse("othermod:alps")));
    }

    @Test
    void incompatibleMinecraftHooksFailAtStartup() throws Exception {
        Path resources = Path.of("src/main/resources");
        var config = JsonParser.parseString(Files.readString(resources.resolve("skysbuildingpiecesbop.mixins.json")))
                .getAsJsonObject();
        assertTrue(config.get("required").getAsBoolean());
        assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
        assertEquals(java.util.Set.of("ItemPickupTarget", "RetiredBiomeRemapMixin", "RetiredRockHardnessMixin"),
                config.getAsJsonArray("mixins").asList().stream().map(value -> value.getAsString())
                        .collect(java.util.stream.Collectors.toSet()));
        assertFalse(Files.exists(resources.resolve("META-INF/coremods.json")));
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
