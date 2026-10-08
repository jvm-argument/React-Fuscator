package dev.reactfuscator.core;

import dev.reactfuscator.analysis.HierarchyService;
import dev.reactfuscator.analysis.KeepPolicy;
import dev.reactfuscator.config.ObfuscationConfig;
import dev.reactfuscator.config.RuleMatcher;
import dev.reactfuscator.model.ArchiveModel;
import dev.reactfuscator.model.ClassModel;
import dev.reactfuscator.model.RunStatistics;
import dev.reactfuscator.service.CancellationToken;
import dev.reactfuscator.service.ProgressListener;
import dev.reactfuscator.util.BytecodeHelper;
import dev.reactfuscator.util.NameFactory;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

public final class ObfuscationContext {
    private final ArchiveModel archive;
    private final ObfuscationConfig config;
    private final KeepPolicy keeps;
    private final HierarchyService hierarchy;
    private final RunStatistics statistics;
    private final ProgressListener listener;
    private final CancellationToken cancellation;
    private final SplittableRandom random;
    private final NameFactory names;
    private final BytecodeHelper bytecode;
    private final Set<MethodNode> generated = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Class<?>, Object> attachments = new HashMap<>();

    public ObfuscationContext(
            ArchiveModel archive,
            ObfuscationConfig config,
            KeepPolicy keeps,
            HierarchyService hierarchy,
            RunStatistics statistics,
            ProgressListener listener,
            CancellationToken cancellation,
            SplittableRandom random,
            NameFactory names,
            BytecodeHelper bytecode) {
        this.archive = archive;
        this.config = config;
        this.keeps = keeps;
        this.hierarchy = hierarchy;
        this.statistics = statistics;
        this.listener = listener;
        this.cancellation = cancellation;
        this.random = random;
        this.names = names;
        this.bytecode = bytecode;
        archive.classes()
                .values()
                .forEach(
                        c -> {
                            names.reserve(c.node().methods.stream().map(m -> m.name).toList());
                            names.reserve(c.node().fields.stream().map(f -> f.name).toList());
                        });
    }

    public ArchiveModel archive() {
        return archive;
    }

    public ObfuscationConfig config() {
        return config;
    }

    public KeepPolicy keeps() {
        return keeps;
    }

    public HierarchyService hierarchy() {
        return hierarchy;
    }

    public RunStatistics statistics() {
        return statistics;
    }

    public ProgressListener listener() {
        return listener;
    }

    public CancellationToken cancellation() {
        return cancellation;
    }

    public SplittableRandom random() {
        return random;
    }

    public NameFactory names() {
        return names;
    }

    public BytecodeHelper bytecode() {
        return bytecode;
    }

    public void generated(MethodNode method) {
        generated.add(method);
    }

    public boolean isGenerated(MethodNode method) {
        return generated.contains(method);
    }

    public <T> T attachment(Class<T> key, java.util.function.Supplier<T> create) {
        return key.cast(attachments.computeIfAbsent(key, k -> create.get()));
    }

    public Set<Class<?>> attachmentKeys() {
        return Set.copyOf(attachments.keySet());
    }

    public void restoreAttachments(Set<Class<?>> before) {
        attachments.keySet().retainAll(before);
    }

    public Set<String> generatedKeys(ClassNode owner) {
        Set<String> keys = new HashSet<>();
        for (MethodNode method : owner.methods) {
            if (generated.contains(method)) {
                keys.add(method.name + method.desc);
            }
        }
        return keys;
    }

    public void restoreGenerated(
            ClassNode current,
            ClassNode restored,
            Collection<ClassNode> removed,
            Set<String> keys) {
        generated.removeAll(current.methods);
        removed.forEach(c -> generated.removeAll(c.methods));
        restored.methods.stream()
                .filter(m -> keys.contains(m.name + m.desc))
                .forEach(generated::add);
    }

    public boolean eligible(ClassModel owner, MethodNode method, String id) {
        return !generated.contains(method)
                && method.instructions.size() > 0
                && keeps.transformMethod(owner.originalName(), method)
                && !new RuleMatcher(config.settings(id).exclude)
                        .matches(owner.originalName() + "#" + method.name + method.desc)
                && bytecode.size(method) < config.maxMethodBytes;
    }

    public boolean choose(String id) {
        return random.nextInt(100) < config.settings(id).density(config.profile);
    }

    public int rounds(String id) {
        return config.settings(id).rounds(config.profile);
    }

    public void changed(String id) {
        statistics.changed(id);
    }
}
