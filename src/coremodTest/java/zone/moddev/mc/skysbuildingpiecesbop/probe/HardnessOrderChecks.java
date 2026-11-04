package zone.moddev.mc.skysbuildingpiecesbop.probe;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.ZipFile;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

/** Apply both actual hardness transformers to the native target, in either order. */
public final class HardnessOrderChecks {
    private static final String TARGET = "net/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase";

    public static void main(String[] arguments) throws Exception {
        String addon = Files.readString(Paths.get(arguments[0]), StandardCharsets.UTF_8);
        String core;
        try (ZipFile archive = new ZipFile(arguments[1]);
                InputStream input = archive.getInputStream(archive.getEntry("coremods/skysbuildingpieces.js"))) {
            core = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (boolean reverse : new boolean[] { false, true }) {
            ClassNode target = nativeClass();
            String[] scripts = reverse ? new String[] { addon, core } : new String[] { core, addon };
            for (String script : scripts) {
                apply(script, target);
            }
            verify(target);
            int instructions = size(target);
            for (String script : scripts) {
                apply(script, target);
            }
            verify(target);
            require(size(target) == instructions, "Repeated transformation changed the native method");
        }
        ClassNode missing = nativeClass();
        missing.methods.clear();
        try {
            apply(addon, missing);
            throw new AssertionError("A missing native target was accepted");
        } catch (ScriptException expected) {
            require(expected.getMessage().contains("expected one Forge 45 hardness method"),
                    "The missing-target refusal must explain the changed method");
        }
        System.out.println("BOP_HARDNESS_COREMOD_ORDER_PASS orders=2 actual_scripts=true idempotent=true"
                + " stack_verification=true missing_target=blocked");
    }

    private static ClassNode nativeClass() throws Exception {
        try (InputStream input = HardnessOrderChecks.class.getClassLoader().getResourceAsStream(TARGET + ".class")) {
            require(input != null, "The native block-state class is missing");
            ClassNode target = new ClassNode();
            new ClassReader(input).accept(target, 0);
            return target;
        }
    }

    private static void apply(String source, ClassNode target) throws Exception {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
        require(engine != null, "The native Forge JavaScript engine is missing");
        engine.eval(source);
        engine.put("candidate", target);
        engine.put("targetName", TARGET.replace('/', '.'));
        Number matches = (Number) engine.eval("var definitions = initializeCoreMod(); var matches = 0;"
                + "for (var key in definitions) { var definition = definitions[key];"
                + "if (definition.target.type === 'CLASS' && String(definition.target.name) === String(targetName)) {"
                + "definition.transformer(candidate); matches++; }} matches;");
        require(matches.intValue() == 1, "Each script must match exactly one native target");
    }

    private static void verify(ClassNode target) throws Exception {
        int methods = 0;
        for (MethodNode method : target.methods) {
            if ((!method.name.equals("getDestroySpeed") && !method.name.equals("m_60800_"))
                    || !method.desc.equals("(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F")) {
                continue;
            }
            methods++;
            int returns = 0;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction.getOpcode() == Opcodes.FRETURN) {
                    returns++;
                }
            }
            require(returns > 0, "The native hardness method has no return");
            for (String owner : new String[] { "zone/moddev/mc/skysbuildingpieces/compat/NativeStateHooks",
                    "zone/moddev/mc/skysbuildingpiecesbop/RetiredRockStateHooks" }) {
                int calls = 0;
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode) instruction;
                        if (call.owner.equals(owner) && call.name.equals("destroySpeed")) {
                            calls++;
                        }
                    }
                }
                require(calls == returns, "A hardness hook is missing or duplicated: " + owner);
            }
            new Analyzer<>(new BasicVerifier()).analyze(target.name, method);
        }
        require(methods == 1, "The native hardness method is missing or ambiguous");
    }

    private static int size(ClassNode target) {
        return target.methods.stream().mapToInt(method -> method.instructions.size()).sum();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
