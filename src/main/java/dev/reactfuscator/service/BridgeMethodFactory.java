package dev.reactfuscator.service;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class BridgeMethodFactory {
    public MethodNode invocation(String name, MethodInsnNode target) {
        boolean isStatic = target.getOpcode() == Opcodes.INVOKESTATIC;
        String descriptor =
                isStatic ? target.desc : "(L" + target.owner + ";" + target.desc.substring(1);
        MethodNode bridge =
                new MethodNode(
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, name, descriptor, null, null);
        loadArguments(bridge, descriptor);
        bridge.instructions.add(
                new MethodInsnNode(
                        target.getOpcode(), target.owner, target.name, target.desc, target.itf));
        bridge.instructions.add(
                new InsnNode(Type.getReturnType(descriptor).getOpcode(Opcodes.IRETURN)));
        return bridge;
    }

    public MethodNode field(String name, FieldInsnNode target) {
        String descriptor =
                switch (target.getOpcode()) {
                    case Opcodes.GETSTATIC -> "()" + target.desc;
                    case Opcodes.PUTSTATIC -> "(" + target.desc + ")V";
                    case Opcodes.GETFIELD -> "(L" + target.owner + ";)" + target.desc;
                    case Opcodes.PUTFIELD -> "(L" + target.owner + ";" + target.desc + ")V";
                    default -> throw new IllegalArgumentException("Invalid field opcode");
                };
        MethodNode bridge =
                new MethodNode(
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, name, descriptor, null, null);
        loadArguments(bridge, descriptor);
        bridge.instructions.add(
                new FieldInsnNode(target.getOpcode(), target.owner, target.name, target.desc));
        bridge.instructions.add(
                new InsnNode(Type.getReturnType(descriptor).getOpcode(Opcodes.IRETURN)));
        return bridge;
    }

    private void loadArguments(MethodNode method, String descriptor) {
        int slot = 0;
        for (Type argument : Type.getArgumentTypes(descriptor)) {
            method.instructions.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), slot));
            slot += argument.getSize();
        }
        method.maxLocals = slot;
    }
}
