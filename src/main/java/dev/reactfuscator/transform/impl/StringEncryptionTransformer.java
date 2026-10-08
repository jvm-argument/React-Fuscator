package dev.reactfuscator.transform.impl;

import dev.reactfuscator.config.ProtectionProfile;
import dev.reactfuscator.config.RuleMatcher;
import dev.reactfuscator.core.ObfuscationContext;
import dev.reactfuscator.model.ClassModel;
import dev.reactfuscator.service.StringCipherService;
import dev.reactfuscator.transform.Transformer;
import dev.reactfuscator.transform.TransformerDescriptor;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

public final class StringEncryptionTransformer implements Transformer {
    private final StringCipherService cipher;

    public StringEncryptionTransformer(StringCipherService cipher) {
        this.cipher = cipher;
    }

    public TransformerDescriptor descriptor() {
        return new TransformerDescriptor(
                "strings",
                "String encryption",
                "Per-literal UTF-16 stream masking with an injected decoder; preserves intern"
                        + " identity.",
                10,
                ProtectionProfile.LIGHT);
    }

    public void transform(ObfuscationContext context, ClassModel model) {
        String helper = context.names().next();
        boolean used = false;
        int variant =
                context.config().profile.ordinal() >= ProtectionProfile.STRONG.ordinal()
                        ? context.random().nextInt(1, 4)
                        : 0;
        MethodNode initializer =
                model.node().methods.stream()
                        .filter(m -> m.name.equals("<clinit>"))
                        .findFirst()
                        .orElse(null);
        InsnList initialValues = new InsnList();
        for (FieldNode field : model.node().fields) {
            if ((field.access & Opcodes.ACC_PRIVATE) != 0
                    && field.value instanceof String text
                    && !text.isEmpty()
                    && text.length() <= 16000
                    && context.keeps().transformField(model.originalName(), field)
                    && !new RuleMatcher(context.config().settings("strings").exclude)
                            .matches(model.originalName() + "#" + field.name)
                    && context.choose("strings")) {
                if ((field.access & Opcodes.ACC_STATIC) != 0) {
                    if (initializer != null && !context.eligible(model, initializer, "strings")) {
                        continue;
                    }
                    int key = context.random().nextInt();
                    initialValues.add(new LdcInsnNode(cipher.encrypt(text, key, variant)));
                    initialValues.add(context.bytecode().integer(key));
                    initialValues.add(
                            new MethodInsnNode(
                                    Opcodes.INVOKESTATIC,
                                    model.node().name,
                                    helper,
                                    "(Ljava/lang/String;I)Ljava/lang/String;",
                                    false));
                    initialValues.add(
                            new FieldInsnNode(
                                    Opcodes.PUTSTATIC, model.node().name, field.name, field.desc));
                    used = true;
                }
                field.value = null;
                context.changed("strings");
            }
        }
        for (MethodNode method : List.copyOf(model.node().methods)) {
            if (context.eligible(model, method, "strings")) {
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof LdcInsnNode ldc
                            && ldc.cst instanceof String text
                            && !text.isEmpty()
                            && text.length() <= 16000
                            && context.choose("strings")) {
                        int key = context.random().nextInt();
                        InsnList replacement = new InsnList();
                        replacement.add(new LdcInsnNode(cipher.encrypt(text, key, variant)));
                        replacement.add(context.bytecode().integer(key));
                        replacement.add(
                                new MethodInsnNode(
                                        Opcodes.INVOKESTATIC,
                                        model.node().name,
                                        helper,
                                        "(Ljava/lang/String;I)Ljava/lang/String;",
                                        false));
                        method.instructions.insertBefore(ldc, replacement);
                        method.instructions.remove(ldc);
                        context.changed("strings");
                        used = true;
                    }
                }
            }
        }
        if (initialValues.size() > 0) {
            if (initializer == null) {
                initializer = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
                initializer.instructions.add(new InsnNode(Opcodes.RETURN));
                model.node().methods.add(initializer);
            }
            initializer.instructions.insert(initialValues);
        }
        if (used) {
            MethodNode method = cipher.decoder(helper, variant);
            model.node().methods.add(method);
            context.generated(method);
        }
    }
}
