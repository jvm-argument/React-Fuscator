package dev.reactfuscator.transform.impl;

import dev.reactfuscator.config.ProtectionProfile;
import dev.reactfuscator.core.ObfuscationContext;
import dev.reactfuscator.model.ClassModel;
import dev.reactfuscator.service.StringCipherService;
import dev.reactfuscator.transform.*;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.util.*;

public final class DynamicStringTransformer implements Transformer {
    private final StringCipherService cipher;

    public DynamicStringTransformer(StringCipherService cipher) {
        this.cipher = cipher;
    }

    public TransformerDescriptor descriptor() {
        return new TransformerDescriptor(
                "indystrings",
                "Invokedynamic strings",
                "Encrypted bootstrap arguments and cached ConstantCallSite values with intern"
                        + " identity.",
                8,
                ProtectionProfile.STRONG);
    }

    public void transform(ObfuscationContext context, ClassModel model) {
        if (model.node().version < Opcodes.V1_8) {
            return;
        }
        record Link(String decoder, String bootstrap) {
        }
        Map<Integer, Link> links = new LinkedHashMap<>();
        String descriptor =
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;I)Ljava/lang/invoke/CallSite;";
        for (MethodNode method : List.copyOf(model.node().methods)) {
            if (context.eligible(model, method, "indystrings")) {
                for (AbstractInsnNode n : method.instructions.toArray()) {
                    if (n instanceof LdcInsnNode literal
                            && literal.cst instanceof String text
                            && !text.isEmpty()
                            && text.length() <= 16000
                            && context.choose("indystrings")) {
                        int variant = context.random().nextInt(1, 4);
                        Link link =
                                links.computeIfAbsent(
                                        variant,
                                        v ->
                                                new Link(
                                                        context.names().next(),
                                                        context.names().next()));
                        Handle handle =
                                new Handle(
                                        Opcodes.H_INVOKESTATIC,
                                        model.node().name,
                                        link.bootstrap(),
                                        descriptor,
                                        false);
                        int key = context.random().nextInt();
                        method.instructions.set(
                                n,
                                new InvokeDynamicInsnNode(
                                        context.names().next(),
                                        "()Ljava/lang/String;",
                                        handle,
                                        cipher.encrypt(text, key, variant),
                                        key));
                        context.changed("indystrings");
                        context.statistics()
                                .cipherVariants
                                .merge("variant" + variant, 1L, Long::sum);
                    }
                }
            }
        }
        for (var entry : links.entrySet()) {
            String decoder = entry.getValue().decoder(), bootstrap = entry.getValue().bootstrap();
            MethodNode decode = cipher.decoder(decoder, entry.getKey());
            model.node().methods.add(decode);
            context.generated(decode);
            MethodNode link =
                    new MethodNode(
                            Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                            bootstrap,
                            descriptor,
                            null,
                            null);
            link.maxLocals = 5;
            link.instructions.add(
                    new TypeInsnNode(Opcodes.NEW, "java/lang/invoke/ConstantCallSite"));
            link.instructions.add(new InsnNode(Opcodes.DUP));
            link.instructions.add(new LdcInsnNode(Type.getType(String.class)));
            link.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
            link.instructions.add(new VarInsnNode(Opcodes.ILOAD, 4));
            link.instructions.add(
                    new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            model.node().name,
                            decoder,
                            "(Ljava/lang/String;I)Ljava/lang/String;",
                            false));
            link.instructions.add(
                    new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "java/lang/invoke/MethodHandles",
                            "constant",
                            "(Ljava/lang/Class;Ljava/lang/Object;)Ljava/lang/invoke/MethodHandle;",
                            false));
            link.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
            link.instructions.add(
                    new MethodInsnNode(
                            Opcodes.INVOKEVIRTUAL,
                            "java/lang/invoke/MethodHandle",
                            "asType",
                            "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",
                            false));
            link.instructions.add(
                    new MethodInsnNode(
                            Opcodes.INVOKESPECIAL,
                            "java/lang/invoke/ConstantCallSite",
                            "<init>",
                            "(Ljava/lang/invoke/MethodHandle;)V",
                            false));
            link.instructions.add(new InsnNode(Opcodes.ARETURN));
            model.node().methods.add(link);
            context.generated(link);
        }
    }
}
