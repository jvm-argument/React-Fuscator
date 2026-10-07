package dev.reactfuscator.service;

import dev.reactfuscator.core.ObfuscationContext;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.util.*;

public final class DispatcherMethodFactory {
    public record Dispatcher(MethodNode method, Map<String, Integer> tokens) {
    }

    public Dispatcher create(ObfuscationContext context, String owner, List<MethodNode> targets) {
        String original = targets.get(0).desc;
        int close = original.indexOf(')');
        String descriptor = original.substring(0, close) + "I" + original.substring(close);
        MethodNode method =
                new MethodNode(
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                        context.names().next(),
                        descriptor,
                        null,
                        null);
        int slot = 0;
        for (Type argument : Type.getArgumentTypes(original)) {
            slot += argument.getSize();
        }
        method.maxLocals = slot + 1;
        Map<String, Integer> tokens = new LinkedHashMap<>();
        SortedMap<Integer, MethodNode> table = new TreeMap<>();
        for (MethodNode target : targets) {
            int token;
            do {
                token = context.random().nextInt();
            } while (table.containsKey(token));
            table.put(token, target);
            tokens.put(target.name, token);
        }
        int salt = context.random().nextInt();
        LabelNode invalid = new LabelNode();
        Map<Integer, LabelNode> labels = new TreeMap<>();
        table.keySet().forEach(token -> labels.put(token ^ salt, new LabelNode()));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, slot));
        method.instructions.add(context.bytecode().integer(salt));
        method.instructions.add(new InsnNode(Opcodes.IXOR));
        method.instructions.add(
                new LookupSwitchInsnNode(
                        invalid,
                        labels.keySet().stream().mapToInt(Integer::intValue).toArray(),
                        labels.values().toArray(LabelNode[]::new)));
        List<Integer> shuffled = new ArrayList<>(table.keySet());
        for (int i = shuffled.size() - 1; i > 0; i--) {
            Collections.swap(shuffled, i, context.random().nextInt(i + 1));
        }
        for (int token : shuffled) {
            MethodNode target = table.get(token);
            method.instructions.add(labels.get(token ^ salt));
            int argumentSlot = 0;
            for (Type argument : Type.getArgumentTypes(original)) {
                method.instructions.add(
                        new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), argumentSlot));
                argumentSlot += argument.getSize();
            }
            method.instructions.add(
                    new MethodInsnNode(
                            Opcodes.INVOKESTATIC, owner, target.name, target.desc, false));
            method.instructions.add(
                    new InsnNode(Type.getReturnType(original).getOpcode(Opcodes.IRETURN)));
        }
        method.instructions.add(invalid);
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new InsnNode(Opcodes.ATHROW));
        return new Dispatcher(method, tokens);
    }
}
