package dev.reactfuscator.mapping;

import dev.reactfuscator.analysis.HierarchyService;
import dev.reactfuscator.analysis.KeepPolicy;
import dev.reactfuscator.config.ObfuscationConfig;
import dev.reactfuscator.model.ArchiveModel;
import dev.reactfuscator.model.ClassModel;
import dev.reactfuscator.util.NameFactory;

import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MappingPlanner {
    private final PackageLayoutPlanner packages;
    private final MemberMappingPlanner members;

    public MappingPlanner() {
        this(new PackageLayoutPlanner(), new MemberMappingPlanner());
    }

    public MappingPlanner(PackageLayoutPlanner packages, MemberMappingPlanner members) {
        this.packages = packages;
        this.members = members;
    }

    public MappingModel plan(
            ArchiveModel archive,
            ObfuscationConfig config,
            KeepPolicy keeps,
            HierarchyService hierarchy,
            NameFactory names) {
        MappingModel mapping = new MappingModel();
        archive.classes()
                .values()
                .forEach(
                        model -> {
                            ClassNode classNode = model.node();
                            List<String> parents = new ArrayList<>(classNode.interfaces);
                            if (classNode.superName != null) {
                                parents.add(classNode.superName);
                            }
                            mapping.parents().put(classNode.name, parents);
                        });
        Map<String, String> layout =
                packages.plan(archive, config, keeps, hierarchy, mapping, names);
        Set<String> occupied = new HashSet<>(archive.classes().keySet());
        for (ClassModel model : archive.classes().values()) {
            ClassNode c = model.node();
            String target = c.name;
            c.methods.forEach(
                    m -> mapping.methodDeclarations().add(new MemberKey(c.name, m.name, m.desc)));
            c.fields.forEach(
                    f -> mapping.fieldDeclarations().add(new MemberKey(c.name, f.name, f.desc)));
            if (config.renameClasses && !keeps.keepClass(c.name)) {
                String targetPackage = layout.get(c.name);
                do {
                    target = (targetPackage.isEmpty() ? "" : targetPackage + "/") + names.next();
                } while (!occupied.add(target));
            }
            mapping.classes().put(c.name, target);
        }
        members.plan(archive, config, keeps, hierarchy, mapping, names);
        return mapping;
    }

    private String unique(NameFactory names, Set<String> occupied) {
        String name;
        do {
            name = names.next();
        } while (!occupied.add(name));
        return name;
    }

    private String pkg(String name) {
        int i = name.lastIndexOf('/');
        return i < 0 ? "" : name.substring(0, i);
    }
}
