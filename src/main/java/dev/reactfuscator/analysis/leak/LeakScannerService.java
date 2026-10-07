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

    public LeakScannerService(ConstantPoolReader constants, DebugAttributeInspector debug) {
        this.constants = constants;
        this.debug = debug;
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
        SymbolTokenIndex index = index(original);
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
            String text = text(entry.getValue());
            if (text != null && metadata(entry.getKey())) {
                index.scan(
                        text,
                        (symbol, offset) -> {
                            if (!symbol.ambiguous() && stale(symbol, mapping)) {
                                report.metadataLeaksFound++;
                            }
                        });
            }
        }
        for (var entry : entries.entrySet()) {
            String path = entry.getKey();
            boolean metadata = metadata(path), mixin = mixin(path);
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
                for (String value : pool.utf8()) {
                    index.scan(
                            value,
                            (symbol, offset) -> {
                                String status =
                                        status(
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
                                                reason(status)),
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
                        add(
                                report,
                                findings,
                                new LeakFinding(
                                        sensitive ? "SENSITIVE_STRING" : "PLAINTEXT_STRING",
                                        path,
                                        value,
                                        "constant pool UTF8",
                                        status,
                                        "Original plaintext literal remains; annotations/constant"
                                            + " ABI, exclusions and loader selectors can require"
                                            + " plaintext"),
                                1);
                    }
                }
            } else {
                String text = text(entry.getValue());
                if (text == null) {
                    continue;
                }
                index.scan(
                        path + "\n" + text,
                        (symbol, offset) -> {
                            String status =
                                    status(
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
                            if (metadata && !symbol.ambiguous() && stale(symbol, mapping)) {
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
                                            reason(status)),
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

    private SymbolTokenIndex index(LeakSnapshot original) {
        SymbolTokenIndex index = new SymbolTokenIndex();
        Set<String> packages = new TreeSet<>();
        for (String owner : new TreeSet<>(original.classes())) {
            index.add(new SymbolTokenIndex.Symbol(owner, "CLASS", owner, false));
            index.add(new SymbolTokenIndex.Symbol(owner.replace('/', '.'), "CLASS", owner, false));
            int slash = owner.lastIndexOf('/');
            if (slash > 0) {
                packages.add(owner.substring(0, slash));
            }
            String simple = owner.substring(slash + 1);
            if (simple.length() >= 5) {
                index.add(new SymbolTokenIndex.Symbol(simple, "CLASS", owner, true));
            }
        }
        for (String pkg : packages) {
            index.add(new SymbolTokenIndex.Symbol(pkg, "PACKAGE", pkg, false));
            index.add(new SymbolTokenIndex.Symbol(pkg.replace('/', '.'), "PACKAGE", pkg, false));
        }
        for (String method : new TreeSet<>(original.methods())) {
            if (method.length() >= 4) {
                index.add(new SymbolTokenIndex.Symbol(method, "METHOD", method, true));
            }
        }
        for (String field : new TreeSet<>(original.fields())) {
            if (field.length() >= 4) {
                index.add(new SymbolTokenIndex.Symbol(field, "FIELD", field, true));
            }
        }
        index.build();
        return index;
    }

    private String status(
            SymbolTokenIndex.Symbol symbol,
            MappingModel mapping,
            KeepPolicy keeps,
            Set<String> methods,
            Set<String> fields,
            Set<String> external) {
        if (symbol.category().equals("METHOD") && methods.contains(symbol.original())
                || symbol.category().equals("FIELD") && fields.contains(symbol.original())) {
            return "RETAINED_CONTRACT";
        }
        if ((symbol.category().equals("METHOD") || symbol.category().equals("FIELD"))
                && external.contains(symbol.original())) {
            return "EXTERNAL_CONTRACT";
        }
        if (symbol.category().equals("CLASS")
                && mapping.mapClass(symbol.original()).equals(symbol.original())) {
            return "RETAINED_CONTRACT";
        }
        if (symbol.category().equals("PACKAGE")
                && (keeps.keepPackageName(symbol.original())
                        || mapping.classes().entrySet().stream()
                                .anyMatch(
                                        e ->
                                                e.getKey().equals(e.getValue())
                                                        && e.getKey()
                                                                .startsWith(
                                                                        symbol.original()
                                                                                + "/")))) {
            return "RETAINED_CONTRACT";
        }
        return symbol.ambiguous() ? "AMBIGUOUS_TOKEN" : "UNRESOLVED";
    }

    private boolean stale(SymbolTokenIndex.Symbol symbol, MappingModel mapping) {
        return symbol.category().equals("CLASS")
                        && !mapping.mapClass(symbol.original()).equals(symbol.original())
                || symbol.category().equals("PACKAGE")
                        && mapping.classes().entrySet().stream()
                                .anyMatch(
                                        e ->
                                                e.getKey().startsWith(symbol.original() + "/")
                                                        && !e.getKey().equals(e.getValue()));
    }

    private String reason(String status) {
        return switch (status) {
            case "RETAINED_CONTRACT" ->
                    "Input symbol retained by API/reflection/serialization/native/loader or"
                            + " explicit keep contract";
            case "EXTERNAL_CONTRACT" -> "Same name is used by an external JVM/API member";
            case "AMBIGUOUS_TOKEN" ->
                    "Unqualified token may be application text rather than a stale symbol"
                            + " reference";
            default -> "Original symbol remains after remapping; inspect this location";
        };
    }

    private boolean metadata(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return path.equals("fabric.mod.json")
                || path.equals("plugin.yml")
                || path.equals("paper-plugin.yml")
                || path.equalsIgnoreCase("META-INF/MANIFEST.MF")
                || path.startsWith("META-INF/services/")
                || lower.contains("mixin")
                || lower.contains("refmap")
                || lower.endsWith(".accesswidener");
    }

    private boolean mixin(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.contains("mixin") || lower.contains("refmap");
    }

    private String text(byte[] bytes) {
        if (bytes.length > 16 * 1024 * 1024) {
            return null;
        }
        try {
            String result =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes))
                            .toString();
            for (int i = 0; i < result.length(); i++) {
                if (result.charAt(i) < 32 && "\n\r\t".indexOf(result.charAt(i)) < 0) {
                    return null;
                }
            }
            return result;
        } catch (CharacterCodingException failure) {
            return null;
        }
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
