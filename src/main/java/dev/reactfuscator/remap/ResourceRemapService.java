package dev.reactfuscator.remap;

import dev.reactfuscator.config.ObfuscationConfig;
import dev.reactfuscator.config.RuleMatcher;
import dev.reactfuscator.mapping.MappingModel;
import dev.reactfuscator.model.ArchiveModel;
import dev.reactfuscator.model.RunStatistics;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

public final class ResourceRemapService {
    public void remap(
            ArchiveModel archive,
            MappingModel mapping,
            ObfuscationConfig config,
            RunStatistics runStatistics)
            throws IOException {
        RuleMatcher patterns = new RuleMatcher(config.resourcePatterns);
        Map<String, byte[]> result = new LinkedHashMap<>();
        boolean signed = false;
        for (var e : archive.resources().entrySet()) {
            String path = e.getKey();
            byte[] bytes = e.getValue();
            String upper = path.toUpperCase(Locale.ROOT);
            if ((upper.startsWith("META-INF/")
                            && !upper.substring(9).contains("/")
                            && (upper.endsWith(".SF")
                                    || upper.endsWith(".RSA")
                                    || upper.endsWith(".DSA")
                                    || upper.endsWith(".EC")
                                    || upper.startsWith("META-INF/SIG-")))
                    || upper.equals("META-INF/INDEX.LIST")) {
                signed = true;
                continue;
            }
            if (upper.equals("META-INF/MANIFEST.MF")) {
                bytes = manifest(bytes, mapping);
            } else if (path.startsWith("META-INF/services/")) {
                path = "META-INF/services/" + mapping.mapBinary(path.substring(18));
                bytes = rewrite(bytes, mapping);
            } else if (!path.startsWith("META-INF/versions/") && patterns.matches(path)) {
                bytes = rewrite(bytes, mapping);
            }
            path = mapping.mapResourcePath(path);
            if (result.put(path, bytes) != null) {
                throw new IOException("Resource remap collision: " + path);
            }
        }
        archive.resources().clear();
        archive.resources().putAll(result);
        if (signed) {
            runStatistics.warnings.add(
                    "Old JAR signatures and index removed because transformed bytes invalidate"
                            + " signatures. Re-sign the output if required.");
        }
    }

    private byte[] manifest(byte[] bytes, MappingModel mapping) throws IOException {
        Manifest manifest = new Manifest(new ByteArrayInputStream(bytes));
        for (String key :
                List.of("Main-Class", "Premain-Class", "Agent-Class", "Launcher-Agent-Class")) {
            String value = manifest.getMainAttributes().getValue(key);
            if (value != null) {
                manifest.getMainAttributes().putValue(key, mapping.mapBinary(value));
            }
        }
        cleanDigests(manifest.getMainAttributes());
        Map<String, Attributes> sections = new LinkedHashMap<>();
        manifest.getEntries()
                .forEach(
                        (name, attrs) -> {
                            cleanDigests(attrs);
                            String mapped = name;
                            if (name.endsWith(".class")) {
                                mapped =
                                        mapping.mapClass(name.substring(0, name.length() - 6))
                                                + ".class";
                            } else if (name.endsWith("/")) {
                                mapped =
                                        mapping.packages()
                                                        .getOrDefault(
                                                                name.substring(
                                                                        0, name.length() - 1),
                                                                name.substring(
                                                                        0, name.length() - 1))
                                                + "/";
                            }
                            if (!attrs.isEmpty()) {
                                sections.put(mapped, attrs);
                            }
                        });
        manifest.getEntries().clear();
        manifest.getEntries().putAll(sections);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        manifest.write(out);
        return out.toByteArray();
    }

    private void cleanDigests(Attributes attrs) {
        attrs.keySet()
                .removeIf(
                        key ->
                                key.toString().toLowerCase(Locale.ROOT).contains("-digest")
                                        || key.toString().equalsIgnoreCase("Signature-Version"));
    }

    private byte[] rewrite(byte[] bytes, MappingModel mapping) throws IOException {
        String text;
        try {
            text =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes))
                            .toString();
        } catch (CharacterCodingException e) {
            return bytes;
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < 32 && "\n\r\t".indexOf(text.charAt(i)) < 0) {
                return bytes;
            }
        }
        Map<String, String> tokens = new HashMap<>();
        mapping.classes()
                .forEach(
                        (a, b) -> {
                            if (!a.equals(b)) {
                                tokens.put(a, b);
                                tokens.put(a.replace('/', '.'), b.replace('/', '.'));
                            }
                        });
        if (tokens.isEmpty()) {
            return bytes;
        }
        String alternatives =
                String.join(
                        "|",
                        tokens.keySet().stream()
                                .sorted(Comparator.comparingInt(String::length).reversed())
                                .map(Pattern::quote)
                                .toList());
        var matcher =
                Pattern.compile("(?<![\\w$/])(?:" + alternatives + ")(?![\\w$])").matcher(text);
        return matcher.replaceAll(
                        m -> java.util.regex.Matcher.quoteReplacement(tokens.get(m.group())))
                .getBytes(StandardCharsets.UTF_8);
    }
}
