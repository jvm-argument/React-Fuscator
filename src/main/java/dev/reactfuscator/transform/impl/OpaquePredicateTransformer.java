package dev.reactfuscator.transform.impl;

import dev.reactfuscator.config.ProtectionProfile;
import dev.reactfuscator.core.ObfuscationContext;
import dev.reactfuscator.model.ClassModel;
import dev.reactfuscator.service.FlowTemplateFactory;
import dev.reactfuscator.transform.*;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class OpaquePredicateTransformer implements Transformer {
    private final FlowTemplateFactory templates;

    public OpaquePredicateTransformer() {
        this(new FlowTemplateFactory());
    }

    public OpaquePredicateTransformer(FlowTemplateFactory templates) {
        this.templates = templates;
    }

    public TransformerDescriptor descriptor() {
        return new TransformerDescriptor(
                "opaque",
                "Opaque predicates",
                "Runtime-dependent parity predicates with verifier-valid decoy branches.",
                40,
                ProtectionProfile.STRONG,
                true,
                true);
    }

    public void transform(ObfuscationContext context, ClassModel model) {
        for (MethodNode method : model.node().methods) {
            if (context.eligible(model, method, "opaque")
                    && !method.name.startsWith("<")
                    && context.choose("opaque")) {
                InsnList code = new InsnList();
                for (int i = 0; i < context.rounds("opaque"); i++) {
                    code.add(templates.guard(context, model.node().name));
                    context.changed("opaque");
                }
                method.instructions.insert(code);
            }
        }
    }
}
