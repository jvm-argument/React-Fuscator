package dev.reactfuscator.transform.impl;

import dev.reactfuscator.config.ProtectionProfile;
import dev.reactfuscator.config.RuleMatcher;
import dev.reactfuscator.core.ObfuscationContext;
import dev.reactfuscator.model.ClassModel;
import dev.reactfuscator.transform.Transformer;
import dev.reactfuscator.transform.TransformerDescriptor;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;

public final class DebugMetadataTransformer implements Transformer {
    public TransformerDescriptor descriptor() {
        return new TransformerDescriptor(
                "debug",
                "Debug metadata",
                "Remove local/line/parameter/source debug attributes, including interfaces and"
                        + " Mixin helpers.",
                99,
                ProtectionProfile.LIGHT,
                false,
                false);
    }

    public void transform(ObfuscationContext context, ClassModel model) {
        model.node().sourceFile = null;
        model.node().sourceDebug = null;
        RuleMatcher excluded = new RuleMatcher(context.config().exclude),
                passExcluded = new RuleMatcher(context.config().settings("debug").exclude);
        for (MethodNode method : model.node().methods) {
            if (!excluded.matches(model.originalName() + "#" + method.name + method.desc)
                    && !passExcluded.matches(
                            model.originalName() + "#" + method.name + method.desc)) {
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof LineNumberNode) {
                        method.instructions.remove(instruction);
                    }
                }
                method.localVariables = null;
                method.parameters = null;
                method.visibleLocalVariableAnnotations = null;
                method.invisibleLocalVariableAnnotations = null;
                if (method.attrs != null) {
                    method.attrs.removeIf(
                            a ->
                                    java.util.Set.of(
                                                    "CharacterRangeTable",
                                                    "CompilationID",
                                                    "SourceID")
                                            .contains(a.type));
                }
                context.changed("debug");
            }
        }
        if (model.node().attrs != null) {
            model.node()
                    .attrs
                    .removeIf(
                            a ->
                                    java.util.Set.of(
                                                    "CharacterRangeTable",
                                                    "CompilationID",
                                                    "SourceID")
                                            .contains(a.type));
        }
    }
}
