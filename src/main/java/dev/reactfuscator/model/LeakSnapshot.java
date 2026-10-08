package dev.reactfuscator.model;

import java.util.Map;
import java.util.Set;

public record LeakSnapshot(
        Set<String> classes,
        Set<String> methods,
        Set<String> fields,
        Set<String> literals,
        Set<String> sensitive,
        Map<String, Long> debugAttributes,
        Map<String, byte[]> resources) {
}
