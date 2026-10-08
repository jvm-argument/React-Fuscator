package dev.reactfuscator.transform.impl;

import dev.reactfuscator.config.ProtectionProfile;
import dev.reactfuscator.core.ObfuscationContext;
import dev.reactfuscator.model.ClassModel;
import dev.reactfuscator.service.FlowTemplateFactory;
import dev.reactfuscator.transform.Transformer;
import dev.reactfuscator.transform.TransformerDescriptor;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class NumberObfuscationTransformer implements Transformer {
    private final FlowTemplateFactory templates;

    public NumberObfuscationTransformer() {
        this(new FlowTemplateFactory());
    }

    public NumberObfuscationTransformer(FlowTemplateFactory templates) {
        this.templates = templates;
    }

    public TransformerDescriptor descriptor() {
        return new TransformerDescriptor(
                "numbers",
                "Number obfuscation",
                "Layered masks; Strong/Extreme bind constants to a runtime opaque seed, including"
                        + " raw IEEE-754 bits.",
                20,
                ProtectionProfile.NORMAL,
                true,
                true);
    }

    public void transform(ObfuscationContext context, ClassModel model) {
        for (MethodNode method : model.node().methods) {
            if (context.eligible(model, method, "numbers")) {
                boolean runtime =
                        context.config().profile.ordinal() >= ProtectionProfile.STRONG.ordinal();
                int slot = runtime ? method.maxLocals : -1,
                        key = runtime ? context.random().nextInt() : 0;
                boolean used = false;
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    Number value = constant(instruction);
                    if (value == null || !context.choose("numbers")) {
                        continue;
                    }
                    InsnList code = new InsnList();
                    if (value instanceof Integer) {
                        encodeInt(code, value.intValue(), context, slot, key);
                    } else if (value instanceof Long) {
                        encodeLong(code, value.longValue(), context, slot, key);
                    } else if (value instanceof Float) {
                        encodeInt(
                                code,
                                Float.floatToRawIntBits(value.floatValue()),
                                context,
                                slot,
                                key);
                        code.add(
                                new MethodInsnNode(
                                        Opcodes.INVOKESTATIC,
                                        "java/lang/Float",
                                        "intBitsToFloat",
                                        "(I)F",
                                        false));
                    } else if (value instanceof Double) {
                        encodeLong(
                                code,
                                Double.doubleToRawLongBits(value.doubleValue()),
                                context,
                                slot,
                                key);
                        code.add(
                                new MethodInsnNode(
                                        Opcodes.INVOKESTATIC,
                                        "java/lang/Double",
                                        "longBitsToDouble",
                                        "(J)D",
                                        false));
                    } else {
                        continue;
                    }
                    method.instructions.insertBefore(instruction, code);
                    method.instructions.remove(instruction);
                    context.changed("numbers");
                    used = true;
                }
                if (runtime && used) {
                    method.maxLocals = slot + 1;
                    method.instructions.insert(seed(model.node().name, slot, key, context));
                }
            }
        }
    }

    private Number constant(AbstractInsnNode node) {
        if (node instanceof LdcInsnNode ldc && ldc.cst instanceof Number number) {
            return number;
        }
        int op = node.getOpcode();
        if (op >= Opcodes.ICONST_M1 && op <= Opcodes.ICONST_5) {
            return op - Opcodes.ICONST_0;
        }
        if (op == Opcodes.BIPUSH || op == Opcodes.SIPUSH) {
            return ((IntInsnNode) node).operand;
        }
        if (op == Opcodes.LCONST_0 || op == Opcodes.LCONST_1) {
            return (long) (op - Opcodes.LCONST_0);
        }
        if (op >= Opcodes.FCONST_0 && op <= Opcodes.FCONST_2) {
            return (float) (op - Opcodes.FCONST_0);
        }
        if (op == Opcodes.DCONST_0 || op == Opcodes.DCONST_1) {
            return (double) (op - Opcodes.DCONST_0);
        }
        return null;
    }

    private void encodeInt(
            InsnList code, int value, ObfuscationContext context, int slot, int key) {
        int[] masks = new int[context.rounds("numbers")], operations = new int[masks.length];
        int encoded = value ^ key;
        for (int i = 0; i < masks.length; i++) {
            masks[i] = context.random().nextInt();
            operations[i] = context.random().nextInt(3);
        }
        for (int i = masks.length - 1; i >= 0; i--) {
            encoded =
                    switch (operations[i]) {
                        case 0 -> encoded ^ masks[i];
                        case 1 -> encoded - masks[i];
                        default -> encoded + masks[i];
                    };
        }
        code.add(context.bytecode().integer(encoded));
        for (int i = 0; i < masks.length; i++) {
            code.add(context.bytecode().integer(masks[i]));
            code.add(
                    new InsnNode(
                            switch (operations[i]) {
                                case 0 -> Opcodes.IXOR;
                                case 1 -> Opcodes.IADD;
                                default -> Opcodes.ISUB;
                            }));
        }
        if (slot >= 0) {
            code.add(new VarInsnNode(Opcodes.ILOAD, slot));
            code.add(new InsnNode(Opcodes.IXOR));
        }
    }

    private void encodeLong(
            InsnList code, long value, ObfuscationContext context, int slot, int key) {
        long[] masks = new long[context.rounds("numbers")];
        int[] operations = new int[masks.length];
        long encoded = value ^ (long) key;
        for (int i = 0; i < masks.length; i++) {
            masks[i] = context.random().nextLong();
            operations[i] = context.random().nextInt(3);
        }
        for (int i = masks.length - 1; i >= 0; i--) {
            encoded =
                    switch (operations[i]) {
                        case 0 -> encoded ^ masks[i];
                        case 1 -> encoded - masks[i];
                        default -> encoded + masks[i];
                    };
        }
        code.add(new LdcInsnNode(encoded));
        for (int i = 0; i < masks.length; i++) {
            code.add(new LdcInsnNode(masks[i]));
            code.add(
                    new InsnNode(
                            switch (operations[i]) {
                                case 0 -> Opcodes.LXOR;
                                case 1 -> Opcodes.LADD;
                                default -> Opcodes.LSUB;
                            }));
        }
        if (slot >= 0) {
            code.add(new VarInsnNode(Opcodes.ILOAD, slot));
            code.add(new InsnNode(Opcodes.I2L));
            code.add(new InsnNode(Opcodes.LXOR));
        }
    }

    private InsnList seed(String owner, int slot, int key, ObfuscationContext context) {
        InsnList code = templates.zero(context, owner);
        code.add(context.bytecode().integer(key));
        code.add(new InsnNode(Opcodes.IADD));
        code.add(new VarInsnNode(Opcodes.ISTORE, slot));
        return code;
    }
}
