package dev.reactfuscator.transform;

import dev.reactfuscator.core.ObfuscationContext;
import dev.reactfuscator.model.ClassModel;
import dev.reactfuscator.registry.TransformerRegistry;
import dev.reactfuscator.service.MethodFingerprintService;
import dev.reactfuscator.verification.VerificationService;

import org.objectweb.asm.tree.ClassNode;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class TransformerPipeline {
    private final TransformerRegistry registry;
    private final VerificationService verification;
    private final MethodFingerprintService fingerprints;

    public TransformerPipeline(
            TransformerRegistry registry,
            VerificationService verification,
            MethodFingerprintService fingerprints) {
        this.registry = registry;
        this.verification = verification;
        this.fingerprints = fingerprints;
    }

    public void execute(ObfuscationContext context) {
        List<Transformer> enabled =
                registry.ordered().stream()
                        .filter(
                                t ->
                                        context.config()
                                                        .transformers
                                                        .containsKey(t.descriptor().id())
                                                ? context.config()
                                                        .settings(t.descriptor().id())
                                                        .enabled
                                                : t.descriptor()
                                                        .defaultEnabled(context.config().profile))
                        .toList();
        int pass = 0;
        for (Transformer transformer : enabled) {
            String id = transformer.descriptor().id();
            context.listener().log("Pass: " + transformer.descriptor().name());
            List<ClassModel> snapshot = List.copyOf(context.archive().classes().values());
            int done = 0;
            for (ClassModel model : snapshot) {
                context.cancellation().check();
                if (id.equals("debug")
                        ? context.keeps().selected(model.originalName())
                        : context.keeps().transformClass(model.originalName())
                                && (model.node().access & org.objectweb.asm.Opcodes.ACC_INTERFACE)
                                        == 0) {
                    ClassNode before = context.bytecode().copy(model.node());
                    Map<String, Long> counts =
                            new LinkedHashMap<>(context.statistics().transformations);
                    Map<String, Long>
                            cipherVariants =
                                    new LinkedHashMap<>(context.statistics().cipherVariants),
                            flowVariants = new LinkedHashMap<>(context.statistics().flowVariants);
                    Map<String, Integer> sizes = new HashMap<>();
                    Map<String, Long> beforeMethods = new HashMap<>();
                    before.methods.forEach(
                            m -> {
                                sizes.put(m.name + m.desc, context.bytecode().size(m));
                                beforeMethods.put(m.name + m.desc, fingerprints.fingerprint(m));
                            });
                    Set<String> generatedKeys = context.generatedKeys(model.node());
                    Set<Class<?>> attachmentKeys = context.attachmentKeys();
                    int classesBefore = context.archive().classes().size(),
                            generatedBefore = context.statistics().generatedClasses;
                    transformer.transform(context, model);
                    context.hierarchy().update(model.node());
                    boolean oversized =
                            model.node().methods.stream()
                                    .anyMatch(
                                            m -> {
                                                int size = context.bytecode().size(m);
                                                return size > context.config().maxMethodBytes
                                                        && size
                                                                > sizes.getOrDefault(
                                                                        m.name + m.desc, 0);
                                            });
                    if (oversized) {
                        List<ClassNode> removed =
                                context.archive().classes().values().stream()
                                        .skip(classesBefore)
                                        .map(ClassModel::node)
                                        .toList();
                        context.restoreGenerated(model.node(), before, removed, generatedKeys);
                        context.restoreAttachments(attachmentKeys);
                        model.node(before);
                        context.hierarchy().update(before);
                        context.statistics().transformations.clear();
                        context.statistics().transformations.putAll(counts);
                        context.statistics().cipherVariants.clear();
                        context.statistics().cipherVariants.putAll(cipherVariants);
                        context.statistics().flowVariants.clear();
                        context.statistics().flowVariants.putAll(flowVariants);
                        for (String added :
                                context.archive().classes().keySet().stream()
                                        .skip(classesBefore)
                                        .toList()) {
                            context.archive().classes().remove(added);
                        }
                        context.statistics().generatedClasses = generatedBefore;
                        context.hierarchy().refresh(context.archive());
                        String message =
                                id + ": rolled back size expansion in " + model.originalName();
                        context.statistics().warnings.add(message);
                        context.listener().log(message);
                    } else {
                        if (context.config().verifyEachPass) {
                            verification.verify(
                                    verification.encode(model.node(), context.hierarchy()),
                                    context.hierarchy());
                        }
                        model.node()
                                .methods
                                .forEach(
                                        m -> {
                                            if (!Objects.equals(
                                                    beforeMethods.get(m.name + m.desc),
                                                    fingerprints.fingerprint(m))) {
                                                context.statistics()
                                                        .transformedMethodKeys
                                                        .add(
                                                                model.originalName()
                                                                        + "#"
                                                                        + m.name
                                                                        + m.desc);
                                            }
                                        });
                    }
                }
                context.listener()
                        .progress(
                                0.2
                                        + 0.6
                                                * (pass
                                                        + (double) ++done
                                                                / Math.max(1, snapshot.size()))
                                                / Math.max(1, enabled.size()),
                                "Transforming " + id);
            }
            pass++;
        }
    }
}
