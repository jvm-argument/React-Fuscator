package dev.reactfuscator.analysis;

import dev.reactfuscator.model.*;
import dev.reactfuscator.verification.HierarchyVerifier;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.util.*;

public final class ControlFlowGraphAnalyzer {
    public Optional<FlowGraphModel> analyze(
            ClassNode owner, MethodNode method, HierarchyService hierarchy) {
        if (!method.tryCatchBlocks.isEmpty()
                || method.maxLocals > 128
                || method.instructions.size() > 4000) {
            return Optional.empty();
        }
        for (AbstractInsnNode node : method.instructions) {
            if (node.getOpcode() == Opcodes.NEW
                    || node.getOpcode() == Opcodes.MONITORENTER
                    || node.getOpcode() == Opcodes.MONITOREXIT
                    || node.getOpcode() == Opcodes.JSR
                    || node.getOpcode() == Opcodes.RET) {
                return Optional.empty();
            }
        }
        LabelNode entry = new LabelNode();
        method.instructions.insert(entry);
        Set<LabelNode> leaders = new HashSet<>();
        leaders.add(entry);
        for (AbstractInsnNode node : method.instructions.toArray()) {
            if (node instanceof JumpInsnNode jump) {
                leaders.add(jump.label);
                if (jump.getOpcode() != Opcodes.GOTO) {
                    LabelNode next = new LabelNode();
                    method.instructions.insert(jump, next);
                    leaders.add(next);
                }
            } else if (node instanceof LookupSwitchInsnNode s) {
                leaders.add(s.dflt);
                leaders.addAll(s.labels);
            } else if (node instanceof TableSwitchInsnNode s) {
                leaders.add(s.dflt);
                leaders.addAll(s.labels);
            }
        }
        if (leaders.size() < 3 || leaders.size() > 128) {
            return Optional.empty();
        }
        Frame<BasicValue>[] frames;
        try {
            frames =
                    new Analyzer<>(new HierarchyVerifier(owner, hierarchy))
                            .analyzeAndComputeMaxs(owner.name, method);
        } catch (AnalyzerException e) {
            throw new IllegalStateException(
                    "Cannot analyze CFG for " + owner.name + "#" + method.name, e);
        }
        Type[] types = new Type[method.maxLocals];
        Map<AbstractInsnNode, Type> loads = new IdentityHashMap<>();
        int index = 0;
        for (AbstractInsnNode node : method.instructions) {
            Frame<BasicValue> frame = frames[index++];
            if (node instanceof LabelNode label
                    && leaders.contains(label)
                    && (frame == null || frame.getStackSize() != 0)) {
                return Optional.empty();
            }
            if (frame == null) {
                continue;
            }
            for (int slot = 0; slot < frame.getLocals(); slot++) {
                Type type = frame.getLocal(slot).getType();
                if (type == null || type.equals(Type.getObjectType("null"))) {
                    continue;
                }
                Type normalized =
                        type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY
                                ? Type.getObjectType("java/lang/Object")
                                : type;
                if (types[slot] != null && !types[slot].equals(normalized)) {
                    return Optional.empty();
                }
                types[slot] = normalized;
            }
            if (node instanceof VarInsnNode variable && variable.getOpcode() == Opcodes.ALOAD) {
                Type type = frame.getLocal(variable.var).getType();
                if (type != null && !type.equals(Type.getObjectType("null"))) {
                    loads.put(node, type);
                }
            }
        }
        for (int slot = 0; slot < types.length - 1; slot++) {
            if (types[slot] != null && types[slot].getSize() == 2 && types[slot + 1] != null) {
                return Optional.empty();
            }
        }
        List<List<AbstractInsnNode>> parts = new ArrayList<>();
        List<LabelNode> entries = new ArrayList<>();
        List<AbstractInsnNode> current = null;
        for (AbstractInsnNode node : method.instructions) {
            if (node instanceof LabelNode label && leaders.contains(label)) {
                current = new ArrayList<>();
                parts.add(current);
                entries.add(label);
            }
            if (current != null) {
                current.add(node);
            }
        }
        List<FlowBlockModel> blocks = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            blocks.add(
                    new FlowBlockModel(
                            entries.get(i),
                            parts.get(i),
                            i + 1 < entries.size() ? entries.get(i + 1) : null));
        }
        return Optional.of(new FlowGraphModel(blocks, types, loads));
    }
}
