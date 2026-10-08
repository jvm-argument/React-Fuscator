package dev.reactfuscator.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public final class ConfigParser {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public ObfuscationConfig read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            Set<String> fields =
                    java.util.Arrays.stream(ObfuscationConfig.class.getFields())
                            .map(java.lang.reflect.Field::getName)
                            .collect(java.util.stream.Collectors.toSet());
            for (String key : json.keySet()) {
                if (!fields.contains(key)) {
                    throw new IllegalArgumentException("Unknown configuration key: " + key);
                }
            }
            if (json.has("transformers") && json.get("transformers").isJsonObject()) {
                for (var entry : json.getAsJsonObject("transformers").entrySet()) {
                    if (!entry.getValue().isJsonObject()) {
                        throw new IllegalArgumentException(
                                "Transformer settings must be an object: " + entry.getKey());
                    }
                    for (String key : entry.getValue().getAsJsonObject().keySet()) {
                        if (!Set.of("enabled", "density", "rounds", "exclude").contains(key)) {
                            throw new IllegalArgumentException(
                                    "Unknown transformer setting: " + entry.getKey() + "." + key);
                        }
                    }
                }
            }
            ObfuscationConfig config = gson.fromJson(json, ObfuscationConfig.class);
            validate(config);
            Path base = path.toAbsolutePath().getParent();
            config.libraries =
                    new java.util.ArrayList<>(
                            config.libraries.stream()
                                    .map(p -> base.resolve(p).normalize().toString())
                                    .toList());
            validate(config);
            return config;
        } catch (JsonParseException | IllegalStateException e) {
            throw new IOException("Invalid configuration " + path, e);
        }
    }

    public ObfuscationConfig copy(ObfuscationConfig config) {
        return gson.fromJson(gson.toJson(config), ObfuscationConfig.class);
    }

    public void write(Path path, ObfuscationConfig config) throws IOException {
        Files.writeString(path, gson.toJson(config), StandardCharsets.UTF_8);
    }

    public void validate(ObfuscationConfig config) {
        if (config.profile == null
                || config.include == null
                || config.exclude == null
                || config.keep == null
                || config.keepMembers == null
                || config.libraries == null
                || config.resourcePatterns == null
                || config.transformers == null) {
            throw new IllegalArgumentException(
                    "Configuration collections and profile cannot be null");
        }
        if (config.maxMethodBytes < 1024 || config.maxMethodBytes > 60000) {
            throw new IllegalArgumentException("maxMethodBytes must be 1024..60000");
        }
        if (config.sensitiveStringPatterns == null) {
            throw new IllegalArgumentException("sensitiveStringPatterns cannot be null");
        }
        config.sensitiveStringPatterns.forEach(java.util.regex.Pattern::compile);
        config.transformers.forEach(
                (id, s) -> {
                    if (s == null
                            || s.exclude == null
                            || s.density < -1
                            || s.density > 100
                            || s.rounds < -1
                            || s.rounds == 0
                            || s.rounds > 8) {
                        throw new IllegalArgumentException(
                                "Invalid settings for "
                                        + id
                                        + ": density -1..100, rounds -1 or 1..8");
                    }
                });
    }
}
