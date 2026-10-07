package dev.reactfuscator.config;

import java.nio.file.Path;
import java.util.*;

public final class ObfuscationConfig {
    public ProtectionProfile profile = ProtectionProfile.EXTREME;
    public Long seed;
    public List<String> libraries = new ArrayList<>();
    public List<String> include = new ArrayList<>(List.of("**"));
    public List<String> exclude = new ArrayList<>();
    public List<String> keep = new ArrayList<>();
    public List<String> keepMembers = new ArrayList<>();
    public List<String> resourcePatterns = new ArrayList<>(List.of("**"));
    public boolean renameClasses = true;
    public boolean renamePackages = true;
    public boolean renameMethods = true;
    public boolean renameFields = true;
    public boolean preservePublicApi = false;
    public boolean preserveSerializationNames = false;
    public List<String> sensitiveStringPatterns =
            new ArrayList<>(List.of("(?i)(password|secret|api[_-]?key|bearer|token|https?://).*"));
    public boolean renameMixins = true;
    public boolean scatterPackages = true;
    public boolean strictDependencies = true;
    public boolean verifyEachPass = true;
    public int maxMethodBytes = 56000;
    public Map<String, TransformerSettings> transformers = new LinkedHashMap<>();

    public TransformerSettings settings(String id) {
        return transformers.getOrDefault(id, new TransformerSettings());
    }

    public List<Path> libraryPaths() {
        return libraries.stream().map(Path::of).toList();
    }
}
