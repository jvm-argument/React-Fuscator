package dev.reactfuscator.mapping;

import dev.reactfuscator.analysis.ClassInfo;
import dev.reactfuscator.analysis.HierarchyService;
import dev.reactfuscator.analysis.KeepPolicy;
import dev.reactfuscator.config.ObfuscationConfig;
import dev.reactfuscator.model.ArchiveModel;
import dev.reactfuscator.model.ClassModel;
import dev.reactfuscator.util.NameFactory;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MemberMappingPlanner {
    public void plan(
            ArchiveModel archive,
            ObfuscationConfig config,
            KeepPolicy keeps,
            HierarchyService hierarchy,
            MappingModel mapping,
            NameFactory names) {
        Map<MemberKey, MemberKey> roots = new HashMap<>();
        Map<MemberKey, Integer> access = new LinkedHashMap<>();
        for (ClassModel model : archive.classes().values()) {
            ClassNode classNode = model.node();
            for (MethodNode methodNode : classNode.methods) {
                MemberKey key = new MemberKey(classNode.name, methodNode.name, methodNode.desc);
                mapping.methodDeclarations().add(key);
                roots.put(key, key);
                access.put(key, methodNode.access);
                for (AbstractInsnNode instruction : methodNode.instructions) {
                    if (instruction instanceof InvokeDynamicInsnNode indy) {
                        Type result = Type.getReturnType(indy.desc);
                        if (result.getSort() == Type.OBJECT
                                && archive.classes().containsKey(result.getInternalName())) {
                            keeps.keepMemberName(indy.name);
                        }
                    }
                }
            }
            classNode.fields.forEach(
                    f ->
                            mapping.fieldDeclarations()
                                    .add(new MemberKey(classNode.name, f.name, f.desc)));
        }
        Set<MemberKey> external = new HashSet<>();
        for (ClassModel model : archive.classes().values()) {
            Map<String, List<MemberKey>> inherited = new HashMap<>();
            Set<String> externalSignatures = new HashSet<>();
            Deque<String> pending = new ArrayDeque<>();
            pending.add(model.node().name);
            Set<String> seen = new HashSet<>();
            while (!pending.isEmpty()) {
                String owner = pending.remove();
                if (!seen.add(owner)) {
                    continue;
                }
                ClassInfo info = hierarchy.resolve(owner);
                for (var declaration : info.methods().entrySet()) {
                    if (virtual(declaration.getValue())
                            && ((declaration.getValue()
                                                    & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED))
                                            != 0
                                    || owner.equals(model.node().name)
                                    || pkg(owner).equals(pkg(model.node().name)))) {
                        int split = declaration.getKey().indexOf('\0');
                        MemberKey key =
                                new MemberKey(
                                        owner,
                                        declaration.getKey().substring(0, split),
                                        declaration.getKey().substring(split + 1));
                        if (roots.containsKey(key)) {
                            inherited
                                    .computeIfAbsent(declaration.getKey(), x -> new ArrayList<>())
                                    .add(key);
                        } else {
                            externalSignatures.add(declaration.getKey());
                        }
                    }
                }
                if (info.parent() != null) {
                    pending.add(info.parent());
                }
                pending.addAll(info.interfaces());
            }
            for (var entry : inherited.entrySet()) {
                List<MemberKey> family = entry.getValue();
                for (int i = 1; i < family.size(); i++) {
                    join(roots, family.get(0), family.get(i));
                }
                if (externalSignatures.contains(entry.getKey())) {
                    external.addAll(family);
                }
            }
        }
        for (var entry : access.entrySet()) {
            if (virtual(entry.getValue())) {
                MemberKey key = entry.getKey();
                Deque<String> parents =
                        new ArrayDeque<>(mapping.parents().getOrDefault(key.owner(), List.of()));
                Set<String> seen = new HashSet<>();
                while (!parents.isEmpty()) {
                    String parent = parents.remove();
                    if (!seen.add(parent)) {
                        continue;
                    }
                    ClassInfo info = hierarchy.resolve(parent);
                    Integer flags = info.methods().get(key.name() + "\0" + key.descriptor());
                    if (flags != null && virtual(flags)) {
                        MemberKey inherited = new MemberKey(parent, key.name(), key.descriptor());
                        if (roots.containsKey(inherited)) {
                            join(roots, key, inherited);
                        } else {
                            external.add(key);
                        }
                    }
                    if (info.parent() != null) {
                        parents.add(info.parent());
                    }
                    parents.addAll(info.interfaces());
                }
            }
        }
        Map<MemberKey, List<MemberKey>> groups = new LinkedHashMap<>();
        access.keySet()
                .forEach(
                        k -> groups.computeIfAbsent(root(roots, k), x -> new ArrayList<>()).add(k));
        Set<String> occupied = new HashSet<>();
        archive.classes()
                .values()
                .forEach(
                        c -> {
                            c.node().methods.forEach(m -> occupied.add(m.name));
                            c.node().fields.forEach(f -> occupied.add(f.name));
                        });
        if (config.renameMethods) {
            for (List<MemberKey> family : groups.values()) {
                boolean eligible =
                        family.stream()
                                .allMatch(
                                        k ->
                                                !k.name().startsWith("<")
                                                        && !k.name().equals("main")
                                                        && (access.get(k) & Opcodes.ACC_NATIVE) == 0
                                                        && !external.contains(k)
                                                        && !keeps.keepMember(
                                                                k.owner(), k.name(), k.descriptor())
                                                        && (!config.preservePublicApi
                                                                || (access.get(k)
                                                                                & Opcodes
                                                                                        .ACC_PRIVATE)
                                                                        != 0));
                if (eligible) {
                    String name = unique(names, occupied);
                    family.forEach(k -> mapping.methods().put(k, name));
                }
            }
        }
        if (config.renameFields) {
            for (ClassModel model : archive.classes().values()) {
                for (FieldNode f : model.node().fields) {
                    if (!f.name.equals("serialVersionUID")
                            && !keeps.keepMember(model.node().name, f.name, f.desc)
                            && (!config.preservePublicApi
                                    || (f.access & Opcodes.ACC_PRIVATE) != 0)) {
                        mapping.fields()
                                .put(
                                        new MemberKey(model.node().name, f.name, f.desc),
                                        unique(names, occupied));
                    }
                }
            }
        }
    }

    private boolean virtual(int access) {
        return (access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0;
    }

    private String pkg(String owner) {
        int slash = owner.lastIndexOf('/');
        return slash < 0 ? "" : owner.substring(0, slash);
    }

    private MemberKey root(Map<MemberKey, MemberKey> roots, MemberKey key) {
        MemberKey parent = roots.get(key);
        if (parent.equals(key)) {
            return key;
        }
        MemberKey result = root(roots, parent);
        roots.put(key, result);
        return result;
    }

    private void join(Map<MemberKey, MemberKey> roots, MemberKey a, MemberKey b) {
        roots.put(root(roots, a), root(roots, b));
    }

    private String unique(NameFactory names, Set<String> occupied) {
        String name;
        do {
            name = names.next();
        } while (!occupied.add(name));
        return name;
    }
}
