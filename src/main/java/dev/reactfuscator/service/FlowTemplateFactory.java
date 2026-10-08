package dev.reactfuscator.service;

import dev.reactfuscator.core.ObfuscationContext;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

public final class FlowTemplateFactory {
    public InsnList zero(ObfuscationContext context, String owner) {
        InsnList out = new InsnList();
        int source = context.random().nextInt(3), variant = context.random().nextInt(8);
        if (source == 2) {
            out.add(
                    new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "java/lang/Thread",
                            "currentThread",
                            "()Ljava/lang/Thread;",
                            false));
        } else {
            out.add(new LdcInsnNode(Type.getObjectType(owner)));
        }
        if (source == 1) {
            out.add(
                    new MethodInsnNode(
                            Opcodes.INVOKEVIRTUAL, "java/lang/Object", "hashCode", "()I", false));
        } else {
            out.add(
                    new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "java/lang/System",
                            "identityHashCode",
                            "(Ljava/lang/Object;)I",
                            false));
        }
        out.add(context.bytecode().integer(context.random().nextInt()));
        out.add(new InsnNode(Opcodes.IXOR));
        switch (variant) {
            case 0, 1 -> {
                out.add(new InsnNode(Opcodes.DUP));
                out.add(new InsnNode(Opcodes.ICONST_1));
                out.add(new InsnNode(variant == 0 ? Opcodes.IADD : Opcodes.ISUB));
                out.add(new InsnNode(Opcodes.IMUL));
                out.add(new InsnNode(Opcodes.ICONST_1));
                out.add(new InsnNode(Opcodes.IAND));
            }
            case 2, 3 -> {
                out.add(new InsnNode(Opcodes.DUP));
                out.add(new InsnNode(Opcodes.ICONST_M1));
                out.add(new InsnNode(Opcodes.IXOR));
                out.add(new InsnNode(variant == 2 ? Opcodes.IOR : Opcodes.IAND));
                if (variant == 2) {
                    out.add(new InsnNode(Opcodes.ICONST_1));
                    out.add(new InsnNode(Opcodes.IADD));
                }
            }
            case 4 -> {
                out.add(new InsnNode(Opcodes.DUP));
                out.add(new InsnNode(Opcodes.IADD));
                out.add(new InsnNode(Opcodes.ICONST_1));
                out.add(new InsnNode(Opcodes.IAND));
            }
            case 5 -> {
                int mask = context.random().nextInt();
                out.add(new InsnNode(Opcodes.DUP));
                out.add(context.bytecode().integer(mask));
                out.add(new InsnNode(Opcodes.IXOR));
                out.add(new InsnNode(Opcodes.SWAP));
                out.add(new InsnNode(Opcodes.IXOR));
                out.add(context.bytecode().integer(mask));
                out.add(new InsnNode(Opcodes.IXOR));
            }
            case 6 -> {
                out.add(new InsnNode(Opcodes.DUP));
                out.add(new InsnNode(Opcodes.DUP));
                out.add(new InsnNode(Opcodes.IMUL));
                out.add(new InsnNode(Opcodes.IADD));
                out.add(new InsnNode(Opcodes.ICONST_1));
                out.add(new InsnNode(Opcodes.IAND));
            }
            case 7 -> {
                out.add(new InsnNode(Opcodes.DUP));
                out.add(new InsnNode(Opcodes.DUP));
                out.add(new InsnNode(Opcodes.IOR));
                out.add(new InsnNode(Opcodes.SWAP));
                out.add(new InsnNode(Opcodes.ISUB));
            }
        }
        context.statistics().flowVariants.merge("invariant" + variant, 1L, Long::sum);
        return out;
    }

    public InsnList guard(ObfuscationContext context, String owner) {
        InsnList out = zero(context, owner);
        LabelNode live = new LabelNode(), cold = new LabelNode(), other = new LabelNode();
        int layout = context.random().nextInt(5);
        switch (layout) {
            case 0 -> out.add(new JumpInsnNode(Opcodes.IFEQ, live));
            case 1 -> {
                out.add(new JumpInsnNode(Opcodes.IFNE, cold));
                out.add(new JumpInsnNode(Opcodes.GOTO, live));
            }
            case 2 -> {
                int key = context.random().nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE);
                out.add(context.bytecode().integer(key));
                out.add(new InsnNode(Opcodes.IADD));
                out.add(
                        new LookupSwitchInsnNode(
                                cold, new int[] {key, key + 1}, new LabelNode[] {live, other}));
            }
            case 3 -> {
                out.add(new InsnNode(Opcodes.ICONST_0));
                out.add(new JumpInsnNode(Opcodes.IF_ICMPGE, live));
            }
            case 4 -> out.add(new TableSwitchInsnNode(0, 2, cold, live, other, cold));
        }
        out.add(cold);
        switch (context.random().nextInt(3)) {
            case 0 -> {
                out.add(new InsnNode(Opcodes.ACONST_NULL));
                out.add(new InsnNode(Opcodes.ATHROW));
            }
            case 1 -> {
                out.add(context.bytecode().integer(context.random().nextInt()));
                out.add(new InsnNode(Opcodes.POP));
                out.add(new JumpInsnNode(Opcodes.GOTO, other));
            }
            case 2 -> {
                out.add(new TypeInsnNode(Opcodes.NEW, "java/lang/IllegalStateException"));
                out.add(new InsnNode(Opcodes.DUP));
                out.add(
                        new MethodInsnNode(
                                Opcodes.INVOKESPECIAL,
                                "java/lang/IllegalStateException",
                                "<init>",
                                "()V",
                                false));
                out.add(new InsnNode(Opcodes.ATHROW));
            }
        }
        out.add(other);
        out.add(context.bytecode().integer(context.random().nextInt()));
        out.add(new InsnNode(Opcodes.POP));
        out.add(new JumpInsnNode(Opcodes.GOTO, live));
        out.add(live);
        context.statistics().flowVariants.merge("guard" + layout, 1L, Long::sum);
        return out;
    }
}
