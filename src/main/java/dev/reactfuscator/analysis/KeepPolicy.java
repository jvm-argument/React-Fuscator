package dev.reactfuscator.analysis;

import dev.reactfuscator.config.*;

import org.objectweb.asm.tree.*;

import java.util.*;

public final class KeepPolicy {
    private final Map<String, String> classes = new TreeMap<>();
    private final Set<String> untouched = new HashSet<>();
    private final Set<String> memberNames = new HashSet<>();
    private final Set<String> packages = new HashSet<>();
    private final Set<String> memberOwners = new HashSet<>();
    private final Set<String> exactMembers = new HashSet<>();
    private final Set<String> mixinPackages = new TreeSet<>();
    private final Map<String, String> generatedSources = new HashMap<>();
    private final ObfuscationConfig config;
    private final RuleMatcher include;
    private final RuleMatcher exclude;
    private final RuleMatcher keep;
    private final RuleMatcher keepMembers;

    private boolean dynamicClasses;
    private boolean dynamicMembers;

    private final Set<String> dynamicMemberReasons = new TreeSet<>();

    public KeepPolicy(ObfuscationConfig config) {
        this.config = config;
        include = new RuleMatcher(config.include);
        exclude = new RuleMatcher(config.exclude);
        keep = new RuleMatcher(config.keep);
        keepMembers = new RuleMatcher(config.keepMembers);
    }

    public void keepClass(String name, String reason) {
        classes.putIfAbsent(name.replace('.', '/'), reason);
    }

    public void untouchedClass(String name, String reason) {
        keepClass(name, reason);
        untouched.add(name.replace('.', '/'));
    }

    public void preserveCode(String name) {
        untouched.add(name.replace('.', '/'));
    }

    public void keepMembersOf(String name) {
        memberOwners.add(name.replace('.', '/'));
    }

    public void keepMember(String owner, String name, String descriptor, String reason) {
        exactMembers.add(owner + "#" + name + descriptor);
    }

    public void mixinPackage(String name) {
        mixinPackages.add(name.replace('.', '/'));
    }

    public Set<String> mixinPackages() {
        return Set.copyOf(mixinPackages);
    }

    public boolean renameMixins() {
        return config.renameMixins;
    }

    public boolean preserveSerializationNames() {
        return config.preserveSerializationNames;
    }

    public void generatedClass(String name, String source) {
        generatedSources.put(name, source);
    }

    public void keepMemberName(String name) {
        memberNames.add(name);
    }

    public void keepPackage(String name) {
        packages.add(name);
    }

    public boolean keepPackageName(String name) {
        return packages.contains(name);
    }

    public void dynamicClasses() {
        dynamicClasses = true;
    }

    public void dynamicMembers() {
        dynamicMembers = true;
    }

    public void dynamicMembers(String reason) {
        dynamicMembers = true;
        dynamicMemberReasons.add(reason);
    }

    public Set<String> dynamicMemberReasons() {
        return Set.copyOf(dynamicMemberReasons);
    }

    public boolean selected(String name) {
        String selected = generatedSources.getOrDefault(name, name);
        return include.matches(selected) && !exclude.matches(selected);
    }

    public boolean keepClass(String name) {
        return !selected(name) || keep.matches(name) || classes.containsKey(name) || dynamicClasses;
    }

    public boolean transformClass(String name) {
        return selected(name) && !untouched.contains(name);
    }

    public boolean keepMember(String owner, String name, String desc) {
        return dynamicMembers
                || dynamicClasses
                || !selected(owner)
                || keep.matches(owner)
                || memberOwners.contains(owner)
                || exactMembers.contains(owner + "#" + name + desc)
                || memberNames.contains(name)
                || exclude.matches(owner + "#" + name + desc)
                || exclude.matches(owner + "#" + name)
                || keepMembers.matches(owner + "#" + name + desc)
                || keepMembers.matches(owner + "#" + name);
    }

    public boolean transformMethod(String owner, MethodNode method) {
        return transformClass(owner) && !exclude.matches(owner + "#" + method.name + method.desc);
    }

    public boolean transformField(String owner, FieldNode field) {
        return transformClass(owner)
                && !exclude.matches(owner + "#" + field.name + field.desc)
                && !exclude.matches(owner + "#" + field.name);
    }

    public Map<String, String> reasons() {
        return Map.copyOf(classes);
    }

    public boolean hasDynamicClasses() {
        return dynamicClasses;
    }

    public boolean hasDynamicMembers() {
        return dynamicMembers;
    }
}
