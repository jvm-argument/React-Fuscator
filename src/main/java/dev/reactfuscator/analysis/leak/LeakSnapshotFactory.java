package dev.reactfuscator.analysis.leak;

import dev.reactfuscator.config.ObfuscationConfig;
import dev.reactfuscator.model.ArchiveModel;
import dev.reactfuscator.model.ClassModel;
import dev.reactfuscator.model.LeakSnapshot;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

public final class LeakSnapshotFactory {
    private final DebugAttributeInspector debug;

    public LeakSnapshotFactory(DebugAttributeInspector debug) {
        this.debug = debug;
    }

    public LeakSnapshot capture(ArchiveModel archive, ObfuscationConfig config) {
        Set<String> classes = new TreeSet<>(archive.classes().keySet()),
                methods = new TreeSet<>(),
                fields = new TreeSet<>(),
                literals = new TreeSet<>(),
                sensitive = new TreeSet<>();
        Map<String, Long> attributes = new TreeMap<>();
        List<Pattern> patterns =
                config.sensitiveStringPatterns.stream().map(Pattern::compile).toList();
        for (ClassModel model : archive.classes().values()) {
            debug.count(model.node())
                    .forEach((key, count) -> attributes.merge(key, count, Long::sum));
            for (FieldNode field : model.node().fields) {
                fields.add(field.name);
                if (field.value instanceof String text && !text.isEmpty()) {
                    literals.add(text);
                }
            }
            for (MethodNode method : model.node().methods) {
                if (!method.name.startsWith("<")) {
                    methods.add(method.name);
                }
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof LdcInsnNode literal
                            && literal.cst instanceof String text
                            && !text.isEmpty()) {
                        literals.add(text);
                    }
                    if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                        for (Object argument : dynamic.bsmArgs) {
                            if (argument instanceof String text && !text.isEmpty()) {
                                literals.add(text);
                            }
                        }
                    }
                }
            }
        }
        for (String text : literals) {
            if (patterns.stream().anyMatch(p -> p.matcher(text).find())) {
                sensitive.add(text);
            }
        }
        return new LeakSnapshot(
                Set.copyOf(classes),
                Set.copyOf(methods),
                Set.copyOf(fields),
                Set.copyOf(literals),
                Set.copyOf(sensitive),
                Map.copyOf(attributes),
                new LinkedHashMap<>(archive.resources()));
    }
}
