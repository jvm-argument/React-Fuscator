package dev.reactfuscator.platform.paper;

import dev.reactfuscator.analysis.KeepPolicy;
import dev.reactfuscator.mapping.MappingModel;
import dev.reactfuscator.model.ArchiveModel;
import dev.reactfuscator.platform.PlatformHandler;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PaperMetadataHandler implements PlatformHandler {
    private final List<String> descriptors = List.of("plugin.yml", "paper-plugin.yml");
    private final List<String> entryKeys =
            List.of("main", "bootstrapper", "loader", "paper-plugin-loader");
    private final Yaml yaml;

    public PaperMetadataHandler() {
        LoaderOptions loader = new LoaderOptions();
        loader.setAllowDuplicateKeys(false);
        loader.setMaxAliasesForCollections(32);
        DumperOptions dumper = new DumperOptions();
        dumper.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumper.setAllowUnicode(true);
        yaml =
                new Yaml(
                        new SafeConstructor(loader),
                        new org.yaml.snakeyaml.representer.Representer(dumper),
                        dumper,
                        loader);
    }

    public String id() {
        return "paper";
    }

    public boolean matches(ArchiveModel archive) {
        return descriptors.stream().anyMatch(archive.resources()::containsKey);
    }

    private Map<String, Object> read(byte[] bytes) throws IOException {
        try {
            Object value = yaml.load(new String(bytes, StandardCharsets.UTF_8));
            if (!(value instanceof Map<?, ?> raw)) {
                throw new IOException("Plugin descriptor must be a YAML object");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((k, v) -> result.put(k.toString(), v));
            return result;
        } catch (RuntimeException e) {
            throw new IOException("Invalid plugin descriptor", e);
        }
    }

    public void analyze(ArchiveModel archive, KeepPolicy keeps) throws IOException {
        for (String path : descriptors) {
            if (archive.resources().containsKey(path)) {
                Map<String, Object> data = read(archive.resources().get(path));
                Object main = data.get("main");
                if (!(main instanceof String)
                        || !archive.classes().containsKey(main.toString().replace('.', '/'))) {
                    throw new IOException(path + ": main class is absent from JAR");
                }
            }
        }
    }

    public void remap(ArchiveModel archive, MappingModel mapping) throws IOException {
        for (String path : descriptors) {
            if (archive.resources().containsKey(path)) {
                Map<String, Object> data = read(archive.resources().get(path));
                for (String key : entryKeys) {
                    if (data.get(key) instanceof String value) {
                        data.put(key, mapping.mapBinary(value));
                    }
                }
                archive.resources().put(path, yaml.dump(data).getBytes(StandardCharsets.UTF_8));
            }
        }
    }
}
