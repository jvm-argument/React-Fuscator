package dev.reactfuscator.analysis.leak;

import dev.reactfuscator.analysis.KeepPolicy;
import dev.reactfuscator.mapping.MappingModel;
import dev.reactfuscator.model.LeakSnapshot;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public final class LeakSymbolService {
    public SymbolTokenIndex buildIndex(LeakSnapshot original) {
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
            if (!simple.isEmpty()) {
                index.add(new SymbolTokenIndex.Symbol(simple, "CLASS", owner, true));
            }
        }
        for (String pkg : packages) {
            index.add(new SymbolTokenIndex.Symbol(pkg, "PACKAGE", pkg, false));
            index.add(new SymbolTokenIndex.Symbol(pkg.replace('/', '.'), "PACKAGE", pkg, false));
        }
        for (String method : new TreeSet<>(original.methods())) {
            if (!method.isEmpty() && !method.startsWith("<")) {
                index.add(new SymbolTokenIndex.Symbol(method, "METHOD", method, true));
            }
        }
        for (String field : new TreeSet<>(original.fields())) {
            if (!field.isEmpty()) {
                index.add(new SymbolTokenIndex.Symbol(field, "FIELD", field, true));
            }
        }
        index.build();
        return index;
    }

    public String classify(
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

    public boolean isStale(SymbolTokenIndex.Symbol symbol, MappingModel mapping) {
        return symbol.category().equals("CLASS")
                        && !mapping.mapClass(symbol.original()).equals(symbol.original())
                || symbol.category().equals("PACKAGE")
                        && mapping.classes().entrySet().stream()
                                .anyMatch(
                                        e ->
                                                e.getKey().startsWith(symbol.original() + "/")
                                                        && !e.getKey().equals(e.getValue()));
    }

    public String reason(String status) {
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

    public boolean isMetadata(String path) {
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

    public boolean isMixin(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.contains("mixin") || lower.contains("refmap");
    }

    public String readText(byte[] bytes) {
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
}
