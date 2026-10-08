package dev.reactfuscator.transform.impl;

import dev.reactfuscator.config.ProtectionProfile;
import dev.reactfuscator.core.ObfuscationContext;
import dev.reactfuscator.model.ClassModel;
import dev.reactfuscator.service.FlowTemplateFactory;
import dev.reactfuscator.transform.Transformer;
import dev.reactfuscator.transform.TransformerDescriptor;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;

public final class JunkCodeTransformer implements Transformer {
    private final FlowTemplateFactory templates;

    public JunkCodeTransformer() {
        this(new FlowTemplateFactory());
    }

    public JunkCodeTransformer(FlowTemplateFactory templates) {
        this.templates = templates;
    }

    public TransformerDescriptor descriptor() {
        return new TransformerDescriptor(
                "junk",
                "Junk & dead code",
                "Synthetic decoy methods and unreachable-by-value branches with legal stack"
                        + " frames.",
                50,
                ProtectionProfile.STRONG,
                true,
                true);
    }

    public void transform(ObfuscationContext context, ClassModel model) {
        for (MethodNode method : List.copyOf(model.node().methods)) {
            if (context.eligible(model, method, "junk")
                    && !method.name.startsWith("<")
                    && context.choose("junk")) {
                InsnList code = templates.guard(context, model.node().name);
                method.instructions.insert(code);
                context.changed("junk");
                context.changed("bogusbranches");
            }
        }
        for (int i = 0; i < context.rounds("junk"); i++) {
            MethodNode decoy =
                    new MethodNode(
                            Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                            context.names().next(),
                            "(I)I",
                            null,
                            null);
            decoy.maxLocals = 1;
            LabelNode one = new LabelNode(), two = new LabelNode(), fallback = new LabelNode();
            int a = context.random().nextInt(10000), b = a + 1;
            decoy.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
            decoy.instructions.add(
                    new LookupSwitchInsnNode(
                            fallback, new int[] {a, b}, new LabelNode[] {one, two}));
            for (LabelNode label : List.of(one, two, fallback)) {
                decoy.instructions.add(label);
                decoy.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
                decoy.instructions.add(context.bytecode().integer(context.random().nextInt()));
                decoy.instructions.add(new InsnNode(Opcodes.IXOR));
                decoy.instructions.add(new InsnNode(Opcodes.IRETURN));
            }
            model.node().methods.add(decoy);
            context.generated(decoy);
            context.changed("junk");
        }
    }
}
