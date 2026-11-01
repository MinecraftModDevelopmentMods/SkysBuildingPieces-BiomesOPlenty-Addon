var Opcodes = Java.type('org.objectweb.asm.Opcodes');
var InsnList = Java.type('org.objectweb.asm.tree.InsnList');
var InsnNode = Java.type('org.objectweb.asm.tree.InsnNode');
var LabelNode = Java.type('org.objectweb.asm.tree.LabelNode');
var JumpInsnNode = Java.type('org.objectweb.asm.tree.JumpInsnNode');
var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');
var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');
var bridge = 'zone/moddev/mc/skysbuildingpieces/legacy/LegacyBridge';

function hasCall(method, owner, name) {
    for (var instruction = method.instructions.getFirst(); instruction !== null;
            instruction = instruction.getNext()) {
        if (instruction.getOpcode() === Opcodes.INVOKESTATIC
                && instruction.owner === owner && instruction.name === name) {
            return true;
        }
    }
    return false;
}

function initializeCoreMod() {
    return {
        'skysbuildingpiecesbop_retired_hardness': {
            'target': {'type': 'CLASS', 'name': 'net.minecraft.block.AbstractBlock$AbstractBlockState'},
            'transformer': function(type) {
                var matches = 0;
                var owner = 'zone/moddev/mc/skysbuildingpiecesbop/RetiredRockStateHooks';
                for (var index = 0; index < type.methods.size(); index++) {
                    var method = type.methods.get(index);
                    if ((method.name !== 'getDestroySpeed' && method.name !== 'func_185887_b')
                            || method.desc !== '(Lnet/minecraft/world/IBlockReader;Lnet/minecraft/util/math/BlockPos;)F') {
                        continue;
                    }
                    matches++;
                    if (hasCall(method, owner, 'destroySpeed')) continue;
                    var returns = 0;
                    for (var instruction = method.instructions.getFirst(); instruction !== null;
                            instruction = instruction.getNext()) {
                        if (instruction.getOpcode() === Opcodes.FRETURN) {
                            var hook = new InsnList();
                            hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                            hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
                            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, 'destroySpeed',
                                    '(FLnet/minecraft/block/AbstractBlock$AbstractBlockState;'
                                    + 'Lnet/minecraft/world/IBlockReader;Lnet/minecraft/util/math/BlockPos;)F', false));
                            method.instructions.insertBefore(instruction, hook);
                            returns++;
                        }
                    }
                    if (returns === 0) throw new Error("Sky's Building Pieces BOP: native hardness method has no return");
                    method.maxStack = Math.max(method.maxStack, 4);
                }
                if (matches !== 1) {
                    throw new Error("Sky's Building Pieces BOP: expected one Forge 36 hardness method, found " + matches);
                }
                return type;
            }
        }
    };
}
