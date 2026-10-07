package dev.reactfuscator.transform.impl;

import dev.reactfuscator.config.ProtectionProfile;
import dev.reactfuscator.core.ObfuscationContext;
import dev.reactfuscator.model.ClassModel;
import dev.reactfuscator.transform.*;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;

public final class ControlFlowTransformer implements Transformer {
    public TransformerDescriptor descriptor() {
        return new TransformerDescriptor(
                "flow",
                "Control flow",
                "Invert conditionals, encode switch keys and replace direct jumps with switch"
                        + " dispatch.",
                30,
                ProtectionProfile.NORMAL);
    }

    public void transform(ObfuscationContext context, ClassModel model) {
        for (MethodNode method : model.node().methods) {
            if (context.eligible(model, method, "flow") && !method.name.startsWith("<")) {
                for (AbstractInsnNode node : method.instructions.toArray()) {
                    if (!context.choose("flow")) {
                        continue;
                    }
                    if (node instanceof JumpInsnNode jump) {
                        int inverse = context.bytecode().invert(jump.getOpcode());
                        InsnList code = new InsnList();
                        if (inverse >= 0) {
                            LabelNode skip = new LabelNode();
                            int layout = context.random().nextInt(3);
                            if (layout == 0) {
                                code.add(new JumpInsnNode(inverse, skip));
                                code.add(new JumpInsnNode(Opcodes.GOTO, jump.label));
                            } else if (layout == 1) {
                                LabelNode yes = new LabelNode(), join = new LabelNode();
                                code.add(new JumpInsnNode(jump.getOpcode(), yes));
                                code.add(new InsnNode(Opcodes.ICONST_0));
                                code.add(new JumpInsnNode(Opcodes.GOTO, join));
                                code.add(yes);
                                code.add(new InsnNode(Opcodes.ICONST_1));
                                code.add(join);
                                code.add(
                                        new LookupSwitchInsnNode(
                                                skip, new int[] {1}, new LabelNode[] {jump.label}));
                            } else {
                                LabelNode yes = new LabelNode();
                                code.add(new JumpInsnNode(jump.getOpcode(), yes));
                                code.add(new JumpInsnNode(Opcodes.GOTO, skip));
                                code.add(yes);
                                int token = context.random().nextInt();
                                code.add(context.bytecode().integer(token));
                                code.add(
                                        new LookupSwitchInsnNode(
                                                jump.label,
                                                new int[] {token},
                                                new LabelNode[] {jump.label}));
                            }
                            code.add(skip);
                            context.statistics()
                                    .flowVariants
                                    .merge("conditional" + layout, 1L, Long::sum);
                        } else if (jump.getOpcode() == Opcodes.GOTO) {
                            int key = context.random().nextInt();
                            code.add(context.bytecode().integer(key));
                            code.add(
                                    new LookupSwitchInsnNode(
                                            jump.label,
                                            new int[] {key},
                                            new LabelNode[] {jump.label}));
                        } else {
                            continue;
                        }
                        method.instructions.insertBefore(node, code);
                        method.instructions.remove(node);
                        context.changed("flow");
                    } else if (node instanceof LookupSwitchInsnNode
                            || node instanceof TableSwitchInsnNode) {
                        int mask = context.random().nextInt(),
                                add = context.random().nextInt(),
                                factor = context.random().nextInt() | 1;
                        SortedMap<Integer, LabelNode> targets = new TreeMap<>();
                        LabelNode fallback;
                        if (node instanceof LookupSwitchInsnNode s) {
                            fallback = s.dflt;
                            for (int i = 0; i < s.keys.size(); i++) {
                                targets.put((s.keys.get(i) * factor + add) ^ mask, s.labels.get(i));
                            }
                        } else {
                            TableSwitchInsnNode s = (TableSwitchInsnNode) node;
                            fallback = s.dflt;
                            for (int i = 0; i < s.labels.size(); i++) {
                                targets.put(((s.min + i) * factor + add) ^ mask, s.labels.get(i));
                            }
                        }
                        InsnList code = new InsnList();
                        code.add(context.bytecode().integer(factor));
                        code.add(new InsnNode(Opcodes.IMUL));
                        code.add(context.bytecode().integer(add));
                        code.add(new InsnNode(Opcodes.IADD));
                        code.add(context.bytecode().integer(mask));
                        code.add(new InsnNode(Opcodes.IXOR));
                        code.add(
                                new LookupSwitchInsnNode(
                                        fallback,
                                        targets.keySet().stream()
                                                .mapToInt(Integer::intValue)
                                                .toArray(),
                                        targets.values().toArray(LabelNode[]::new)));
                        method.instructions.insertBefore(node, code);
                        method.instructions.remove(node);
                        context.changed("flow");
                    }
                }
            }
        }
    }
}
