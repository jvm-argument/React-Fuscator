package dev.reactfuscator.mapping;

import java.util.*;

public final class MappingModel {
    private final Map<String, String> classes = new LinkedHashMap<>();
    private final Map<String, String> packages = new LinkedHashMap<>();
    private final Map<String, String> mixinPackages = new LinkedHashMap<>();
    private final Map<MemberKey, String> methods = new LinkedHashMap<>();
    private final Map<MemberKey, String> fields = new LinkedHashMap<>();
    private final Map<String, List<String>> parents = new HashMap<>();
    private final Set<MemberKey> methodDeclarations = new HashSet<>();
    private final Set<MemberKey> fieldDeclarations = new HashSet<>();

    public Map<String, String> classes() {
        return classes;
    }

    public Map<String, String> packages() {
        return packages;
    }

    public Map<String, String> mixinPackages() {
        return mixinPackages;
    }

    public Map<MemberKey, String> methods() {
        return methods;
    }

    public Map<MemberKey, String> fields() {
        return fields;
    }

    public Map<String, List<String>> parents() {
        return parents;
    }

    public Set<MemberKey> methodDeclarations() {
        return methodDeclarations;
    }

    public Set<MemberKey> fieldDeclarations() {
        return fieldDeclarations;
    }

    public String mapClass(String name) {
        return classes.getOrDefault(name, name);
    }

    public String mapBinary(String name) {
        return mapClass(name.replace('.', '/')).replace('/', '.');
    }

    public String mapResourcePath(String path) {
        for (var entry :
                packages.entrySet().stream()
                        .sorted(
                                Comparator.<Map.Entry<String, String>>comparingInt(
                                                e -> e.getKey().length())
                                        .reversed())
                        .toList()) {
            if (!entry.getKey().isEmpty() && path.startsWith(entry.getKey() + "/")) {
                return entry.getValue() + path.substring(entry.getKey().length());
            }
        }
        return path;
    }

    public String mapMethod(String owner, String name, String descriptor) {
        return lookup(methods, owner, name, descriptor, new HashSet<>());
    }

    public String mapField(String owner, String name, String descriptor) {
        return lookup(fields, owner, name, descriptor, new HashSet<>());
    }

    private String lookup(
            Map<MemberKey, String> table,
            String owner,
            String name,
            String descriptor,
            Set<String> seen) {
        if (!seen.add(owner)) {
            return name;
        }
        MemberKey key = new MemberKey(owner, name, descriptor);
        String result = table.get(key);
        if (result != null) {
            return result;
        }
        if ((table == methods ? methodDeclarations : fieldDeclarations).contains(key)) {
            return name;
        }
        for (String parent : parents.getOrDefault(owner, List.of())) {
            result = lookup(table, parent, name, descriptor, seen);
            if (!result.equals(name)) {
                return result;
            }
        }
        return name;
    }
}
