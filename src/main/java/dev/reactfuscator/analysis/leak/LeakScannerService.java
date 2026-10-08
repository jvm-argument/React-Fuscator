package dev.reactfuscator.analysis.leak;

import dev.reactfuscator.analysis.KeepPolicy;
import dev.reactfuscator.mapping.*;
import dev.reactfuscator.model.*;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

public final class LeakScannerService {
    private final ConstantPoolReader constants;
    private final DebugAttributeInspector debug;
    private final LeakSymbolService symbols;

    public LeakScannerService(
            ConstantPoolReader constants,
            DebugAttributeInspector debug,
            LeakSymbolService symbols) {
        this.constants = constants;
        this.debug = debug;
        this.symbols = symbols;
    }

    public ProtectionReport scan(
            LeakSnapshot original,
            MappingModel mapping,
            Map<String, byte[]> entries,
            KeepPolicy keeps,
            RunStatistics statistics)
            throws IOException {
        ProtectionReport report = new ProtectionReport();
        report.renamedClasses = statistics.renamedClasses;
        report.renamedMethods = statistics.renamedMethods;
        report.renamedFields = statistics.renamedFields;
        report.encryptedStrings =
                statistics.transformations.getOrDefault("strings", 0L)
                        + statistics.transformations.getOrDefault("indystrings", 0L)
                        + statistics.transformations.getOrDefault("concatstrings", 0L);
        report.transformedMethods = statistics.transformedMethodKeys.size();
        report.generatedOpaquePredicates =
                statistics.transformations.getOrDefault("opaque", 0L)
                        + statistics.transformations.getOrDefault("helpers", 0L)
                        + statistics.transformations.getOrDefault("bogusbranches", 0L);
        report.generatedProxies =
                statistics.transformations.getOrDefault("proxy", 0L)
                        + statistics.transformations.getOrDefault("bridges", 0L);
        report.generatedDispatchers = statistics.transformations.getOrDefault("dispatchers", 0L);
        SymbolTokenIndex index = symbols.buildIndex(original);
        Set<String> retainedMethods = new HashSet<>(), retainedFields = new HashSet<>();
        mapping.methodDeclarations().stream()
                .filter(k -> !mapping.methods().containsKey(k))
                .forEach(k -> retainedMethods.add(k.name()));
        mapping.fieldDeclarations().stream()
                .filter(k -> !mapping.fields().containsKey(k))
                .forEach(k -> retainedFields.add(k.name()));
        Map<String, String> reverse = new HashMap<>();
        mapping.classes().forEach((a, b) -> reverse.put(b, a));
        Map<String, Long> remainingDebug = new TreeMap<>();
        Map<String, LeakFinding> findings = new LinkedHashMap<>();
        for (var entry : original.resources().entrySet()) {
            String text = symbols.readText(entry.getValue());
            if (text != null && symbols.isMetadata(entry.getKey())) {
                index.scan(
                        text,
                        (symbol, offset) -> {
                            if (!symbol.ambiguous() && symbols.isStale(symbol, mapping)) {
                                report.metadataLeaksFound++;
                            }
                        });
            }
        }
        for (var entry : entries.entrySet()) {
            String path = entry.getKey();
            boolean metadata = symbols.isMetadata(path), mixin = symbols.isMixin(path);
            Set<String> externalNames = new HashSet<>();
            if (path.endsWith(".class")) {
                ClassNode owner = new ClassNode(Opcodes.ASM9);
                new ClassReader(entry.getValue()).accept(owner, 0);
                String oldOwner = reverse.getOrDefault(owner.name, owner.name);
                debug.count(owner)
                        .forEach(
                                (attribute, count) -> {
                                    remainingDebug.merge(attribute, count, Long::sum);
                                    String status =
                                            keeps.selected(oldOwner) ? "UNRESOLVED" : "EXCLUDED";
                                    add(
                                            report,
                                            findings,
                                            new LeakFinding(
                                                    "DEBUG",
                                                    path,
                                                    attribute,
                                                    "class attributes",
                                                    status,
                                                    status.equals("EXCLUDED")
                                                            ? "Explicit class exclusion or"
                                                                    + " preserved multi-release"
                                                                    + " variant"
                                                            : "Debug attribute remains in output"),
                                            count);
                                });
                for (MethodNode method : owner.methods) {
                    for (AbstractInsnNode instruction : method.instructions) {
                        if (instruction instanceof MethodInsnNode call
                                && !entries.containsKey(call.owner + ".class")) {
                            externalNames.add(call.name);
                        }
                        if (instruction instanceof FieldInsnNode field
                                && !entries.containsKey(field.owner + ".class")) {
                            externalNames.add(field.name);
                        }
                    }
                }
                var pool = constants.read(entry.getValue());
                Set<String> requiredValues = new HashSet<>();
                owner.fields.forEach(
                        f -> {
                            if (f.value instanceof String s) {
                                requiredValues.add(s);
                            }
                        });
                collectAnnotations(owner.visibleAnnotations, requiredValues);
                collectAnnotations(owner.invisibleAnnotations, requiredValues);
                for (MethodNode method : owner.methods) {
                    collectAnnotations(method.visibleAnnotations, requiredValues);
                    collectAnnotations(method.invisibleAnnotations, requiredValues);
                }
                for (FieldNode field : owner.fields) {
                    collectAnnotations(field.visibleAnnotations, requiredValues);
                    collectAnnotations(field.invisibleAnnotations, requiredValues);
                }
                for (MethodNode method : owner.methods) {
                    for (AbstractInsnNode instruction : method.instructions) {
                        if (instruction instanceof InvokeDynamicInsnNode call
                                && call.bsm.getOwner().equals("java/lang/runtime/ObjectMethods")) {
                            for (Object argument : call.bsmArgs) {
                                if (argument instanceof String value) {
                                    requiredValues.add(value);
                                }
                            }
                        }
                    }
                }
                Set<String> encryptedPayloads = new HashSet<>();
                for (MethodNode method : owner.methods) {
                    for (AbstractInsnNode instruction : method.instructions) {
                        if (instruction instanceof InvokeDynamicInsnNode call
                                && call.bsm.getOwner().equals(owner.name)
                                && (call.bsm
                                                .getDesc()
                                                .equals(
                                                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;I)Ljava/lang/invoke/CallSite;")
                                        || call.bsm
                                                .getDesc()
                                                .equals(
                                                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;ILjava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;"))) {
                            for (int argument = 0; argument + 1 < call.bsmArgs.length; argument++) {
                                if (call.bsmArgs[argument] instanceof String text
                                        && call.bsmArgs[argument + 1] instanceof Integer) {
                                    encryptedPayloads.add(text);
                                }
                            }
                        }
                    }
                }
                for (String value : pool.utf8()) {
                    index.scan(
                            value,
                            (symbol, offset) -> {
                                String status =
                                        symbols.classify(
                                                symbol,
                                                mapping,
                                                keeps,
                                                retainedMethods,
                                                retainedFields,
                                                externalNames);
                                add(
                                        report,
                                        findings,
                                        new LeakFinding(
                                                symbol.category(),
                                                path,
                                                symbol.original(),
                                                "constant pool UTF8",
                                                status,
                                                symbols.reason(status)),
                                        1);
                            });
                    if (original.literals().contains(value)
                            && (pool.literals().contains(value)
                                    || original.sensitive().contains(value))) {
                        boolean sensitive = original.sensitive().contains(value);
                        String status =
                                requiredValues.contains(value)
                                                || retainedMethods.contains(value)
                                                || retainedFields.contains(value)
                                                || !keeps.selected(oldOwner)
                                                || !keeps.transformClass(oldOwner)
                                        ? "RETAINED_CONTRACT"
                                        : "UNRESOLVED";
                        String explanation =
                                "Original plaintext literal remains; annotations, constant ABI and"
                                        + " loader selectors can require plaintext";
                        if (status.equals("UNRESOLVED")
                                && (owner.access & Opcodes.ACC_INTERFACE) != 0) {
                            status = "EXCLUDED";
                            explanation =
                                    "Interface instruction transforms are excluded by the"
                                            + " compatibility pipeline";
                        } else if (status.equals("UNRESOLVED")
                                && !sensitive
                                && encryptedPayloads.contains(value)) {
                            status = "AMBIGUOUS_TOKEN";
                            explanation =
                                    "Encrypted bootstrap payload coincides with an input literal;"
                                            + " this is not evidence of retained plaintext";
                        }
                        add(
                                report,
                                findings,
                                new LeakFinding(
                                        sensitive ? "SENSITIVE_STRING" : "PLAINTEXT_STRING",
                                        path,
                                        value,
                                        "constant pool UTF8",
                                        status,
                                        explanation),
                                1);
                    }
                }
            } else {
                String text = symbols.readText(entry.getValue());
                if (text == null) {
                    continue;
                }
                index.scan(
                        path + "\n" + text,
                        (symbol, offset) -> {
                            String status =
                                    symbols.classify(
                                            symbol,
                                            mapping,
                                            keeps,
                                            retainedMethods,
                                            retainedFields,
                                            externalNames);
                            String category =
                                    metadata
                                            ? (mixin ? "MIXIN_METADATA" : "METADATA")
                                            : symbol.category();
                            if (metadata
                                    && !symbol.ambiguous()
                                    && symbols.isStale(symbol, mapping)) {
                                report.metadataLeaksRemaining++;
                            }
                            add(
                                    report,
                                    findings,
                                    new LeakFinding(
                                            category,
                                            path,
                                            symbol.original(),
                                            "resource path/text",
                                            status,
                                            symbols.reason(status)),
                                    1);
                        });
                for (String sensitive : original.sensitive()) {
                    if (sensitive.length() >= 4 && text.contains(sensitive)) {
                        add(
                                report,
                                findings,
                                new LeakFinding(
                                        "SENSITIVE_STRING",
                                        path,
                                        sensitive,
                                        "resource text",
                                        "RESOURCE_DATA",
                                        "Resources are runtime data; review keep rules and payload"
                                                + " requirements"),
                                1);
                    }
                }
            }
        }
        report.metadataLeaksResolved =
                Math.max(0, report.metadataLeaksFound - report.metadataLeaksRemaining);
        original.debugAttributes()
                .forEach(
                        (attribute, count) -> {
                            long removed =
                                    Math.max(0, count - remainingDebug.getOrDefault(attribute, 0L));
                            if (removed > 0) {
                                report.removedDebugAttributesByType.put(attribute, removed);
                                report.removedDebugAttributes += removed;
                            }
                        });
        report.leaks.addAll(findings.values());
        report.leaks.sort(
                Comparator.comparing((LeakFinding f) -> f.entry)
                        .thenComparing(f -> f.category)
                        .thenComparing(f -> f.symbol));
        return report;
    }

    private void add(
            ProtectionReport report,
            Map<String, LeakFinding> findings,
            LeakFinding finding,
            long count) {
        report.totalFindings += count;
        report.findingsByCategory.merge(finding.category, count, Long::sum);
        report.findingsByStatus.merge(finding.status, count, Long::sum);
        String key =
                finding.entry
                        + '\0'
                        + finding.category
                        + '\0'
                        + finding.symbol
                        + '\0'
                        + finding.status;
        LeakFinding existing = findings.get(key);
        if (existing != null) {
            existing.occurrences += count;
        } else if (findings.size() < 25000) {
            finding.occurrences = count;
            findings.put(key, finding);
        } else {
            report.omittedDetails += count;
        }
    }

    private void collectAnnotations(List<AnnotationNode> annotations, Set<String> values) {
        if (annotations != null) {
            for (AnnotationNode annotation : annotations) {
                if (annotation.values != null) {
                    for (int i = 1; i < annotation.values.size(); i += 2) {
                        annotationValue(annotation.values.get(i), values);
                    }
                }
            }
        }
    }

    private void annotationValue(Object value, Set<String> values) {
        if (value instanceof String text) {
            values.add(text);
        } else if (value instanceof List<?> list) {
            list.forEach(v -> annotationValue(v, values));
        } else if (value instanceof AnnotationNode nested) {
            collectAnnotations(List.of(nested), values);
        }
    }
}
