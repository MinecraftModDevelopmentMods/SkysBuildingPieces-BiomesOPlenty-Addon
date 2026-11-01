package zone.moddev.mc.skysbuildingpiecesbop;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import static org.junit.jupiter.api.Assertions.*;

class RetiredRockHardnessHookTest {
    private ScriptEngine engine() throws Exception {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
        assertNotNull(engine);
        engine.eval(new String(Files.readAllBytes(Paths.get("src/main/resources/coremods/skysbuildingpiecesbop.js")),
                StandardCharsets.UTF_8));
        return engine;
    }

    private ClassNode nativeState() throws Exception {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("net/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase.class")) {
            assertNotNull(input);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private void apply(ScriptEngine engine, ClassNode node) throws Exception {
        engine.put("candidate", node);
        engine.eval("initializeCoreMod()['skysbuildingpiecesbop_retired_hardness'].transformer(candidate)");
    }

    @Test
    void nativeGetterHasOneValidIdempotentHookWithoutChangingOtherFloatGetters() throws Exception {
        ClassNode node = nativeState();
        ScriptEngine engine = engine();
        apply(engine, node);
        int hooks = 0;
        int returns = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (call.owner.equals("zone/moddev/mc/skysbuildingpiecesbop/RetiredRockStateHooks")) {
                        assertEquals("getDestroySpeed", method.name);
                        assertEquals("destroySpeed", call.name);
                        hooks++;
                    }
                }
                if (method.name.equals("getDestroySpeed") && instruction.getOpcode() == Opcodes.FRETURN) returns++;
            }
            if (method.name.equals("getDestroySpeed")) {
                new Analyzer<BasicValue>(new BasicVerifier()).analyze(node.name, method);
            }
        }
        assertEquals(1, returns);
        assertEquals(returns, hooks);
        int size = node.methods.stream().mapToInt(method -> method.instructions.size()).sum();
        apply(engine, node);
        assertEquals(size, node.methods.stream().mapToInt(method -> method.instructions.size()).sum());
    }

    @Test
    void coreAndAddonWrappersCoexistInEitherOrder() throws Exception {
        for (boolean addonFirst : new boolean[] {false, true}) {
            ClassNode node = nativeState();
            ScriptEngine addon = engine();
            ScriptEngine core = new ScriptEngineManager().getEngineByName("nashorn");
            try (InputStream input = getClass().getClassLoader()
                    .getResourceAsStream("coremods/skysbuildingpieces.js")) {
                assertNotNull(input);
                core.eval(new String(read(input), StandardCharsets.UTF_8));
            }
            if (addonFirst) apply(addon, node);
            applyCore(core, node);
            apply(addon, node);
            if (!addonFirst) applyCore(core, node);
            int size = node.methods.stream().mapToInt(method -> method.instructions.size()).sum();
            apply(addon, node);
            applyCore(core, node);
            assertEquals(size, node.methods.stream().mapToInt(method -> method.instructions.size()).sum());
            MethodNode getter = node.methods.stream().filter(method -> method.name.equals("getDestroySpeed"))
                    .findFirst().get();
            int wrappers = 0;
            for (AbstractInsnNode instruction : getter.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (call.name.equals("destroySpeed")) wrappers++;
                }
            }
            assertEquals(2, wrappers);
            new Analyzer<BasicValue>(new BasicVerifier()).analyze(node.name, getter);
        }
    }

    private void applyCore(ScriptEngine engine, ClassNode node) throws Exception {
        engine.put("candidate", node);
        engine.eval("initializeCoreMod()['skysbuildingpieces_material_hardness'].transformer(candidate)");
    }

    private byte[] read(InputStream input) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    @Test
    void missingOrChangedNativeGetterStopsLoading() throws Exception {
        assertThrows(ScriptException.class, () -> apply(engine(), new ClassNode()));
        ClassNode changed = nativeState();
        changed.methods.stream().filter(method -> method.name.equals("getDestroySpeed"))
                .forEach(method -> method.name = "unexpectedGetter");
        assertThrows(ScriptException.class, () -> apply(engine(), changed));
    }
}
