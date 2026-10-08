package dev.reactfuscator.model;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ArchiveModel {
    private final Map<String, ClassModel> classes = new LinkedHashMap<>();
    private final Map<String, byte[]> resources = new LinkedHashMap<>();

    public Map<String, ClassModel> classes() {
        return classes;
    }

    public Map<String, byte[]> resources() {
        return resources;
    }
}
