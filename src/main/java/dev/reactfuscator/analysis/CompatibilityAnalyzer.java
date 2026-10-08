package dev.reactfuscator.analysis;

import dev.reactfuscator.model.ArchiveModel;
import dev.reactfuscator.model.ClassModel;
import dev.reactfuscator.model.RunStatistics;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CompatibilityAnalyzer {
    private final ReflectionAnalyzer reflection;
    private final NativeInteropAnalyzer nativeInterop;
    private final AnnotationContractAnalyzer annotationContracts;

    public CompatibilityAnalyzer() {
        this(
                new ReflectionAnalyzer(),
                new NativeInteropAnalyzer(),
                new AnnotationContractAnalyzer());
    }

    public CompatibilityAnalyzer(
            ReflectionAnalyzer reflection,
            NativeInteropAnalyzer nativeInterop,
            AnnotationContractAnalyzer annotationContracts) {
        this.reflection = reflection;
        this.nativeInterop = nativeInterop;
        this.annotationContracts = annotationContracts;
    }

    public void analyze(
            ArchiveModel archive,
            KeepPolicy keeps,
            HierarchyService hierarchy,
            RunStatistics runStatistics) {
        nativeInterop.analyze(archive, keeps, hierarchy);
        Set<String> names = new HashSet<>();
        boolean multiRelease =
                archive.resources().keySet().stream()
                        .anyMatch(p -> p.startsWith("META-INF/versions/") && p.endsWith(".class"));
        for (ClassModel model : archive.classes().values()) {
            ClassNode classNode = model.node();
            if (multiRelease) {
                keeps.keepClass(classNode.name, "Multi-release ABI is preserved across variants");
            }
            if (classNode.module != null) {
                keeps.dynamicClasses();
            }
            if ((classNode.access
                            & (Opcodes.ACC_ENUM | Opcodes.ACC_RECORD | Opcodes.ACC_ANNOTATION))
                    != 0) {
                if (keeps.preserveSerializationNames()) {
                    keeps.keepClass(classNode.name, "Enum/record/annotation identity");
                }
                if ((classNode.access & Opcodes.ACC_RECORD) != 0
                        || (classNode.access & Opcodes.ACC_ANNOTATION) != 0) {
                    keeps.keepMembersOf(classNode.name);
                }
                if ((classNode.access & Opcodes.ACC_ENUM) != 0) {
                    for (MethodNode methodNode : classNode.methods) {
                        if (Set.of("values", "valueOf").contains(methodNode.name)) {
                            keeps.keepMember(
                                    classNode.name,
                                    methodNode.name,
                                    methodNode.desc,
                                    "Enum lookup contract");
                        }
                    }
                    for (FieldNode fieldNode : classNode.fields) {
                        if ((fieldNode.access & Opcodes.ACC_ENUM) != 0) {
                            keeps.keepMember(
                                    classNode.name,
                                    fieldNode.name,
                                    fieldNode.desc,
                                    "Enum constant contract");
                        }
                    }
                }
            }
            if (classNode.name.endsWith("/package-info") || classNode.name.equals("module-info")) {
                keeps.untouchedClass(classNode.name, "Package/module metadata");
            }
            if ((classNode.access & Opcodes.ACC_ENUM) == 0
                    && (hierarchy.assignable("java/io/Serializable", classNode.name)
                            || hierarchy.assignable("java/io/Externalizable", classNode.name))) {
                if (keeps.preserveSerializationNames()) {
                    keeps.keepClass(classNode.name, "Serialization ABI");
                }
                for (FieldNode f : classNode.fields) {
                    if ((f.access & (Opcodes.ACC_STATIC | Opcodes.ACC_TRANSIENT)) == 0
                            || f.name.equals("serialPersistentFields")) {
                        keeps.keepMember(
                                classNode.name, f.name, f.desc, "Java serialization field layout");
                    }
                }
                for (MethodNode m : classNode.methods) {
                    if (Set.of(
                                    "readObject",
                                    "writeObject",
                                    "readObjectNoData",
                                    "writeReplace",
                                    "readResolve")
                            .contains(m.name)) {
                        keeps.keepMember(classNode.name, m.name, m.desc, "Java serialization hook");
                    }
                }
            }
            annotations(classNode.visibleAnnotations, archive, keeps);
            annotations(classNode.invisibleAnnotations, archive, keeps);
            if (hasAnnotation(classNode, "Lkotlin/Metadata;")) {
                keeps.keepClass(classNode.name, "Kotlin metadata ABI");
                keeps.keepMembersOf(classNode.name);
            }
            if (hasAnnotation(classNode, "Lorg/spongepowered/asm/mixin/Mixin;")) {
                keeps.preserveCode(classNode.name);
                keeps.keepMembersOf(classNode.name);
                if (!keeps.renameMixins()) {
                    keeps.keepClass(classNode.name, "Mixin bytecode and selectors");
                }
            }
            for (FieldNode f : classNode.fields) {
                annotations(f.visibleAnnotations, archive, keeps);
                annotations(f.invisibleAnnotations, archive, keeps);
                if (annotationContracts.requiresMemberName(f.visibleAnnotations)
                        || annotationContracts.requiresMemberName(f.invisibleAnnotations)) {
                    keeps.keepMember(
                            classNode.name, f.name, f.desc, "Unknown annotated field contract");
                }
                if (f.value instanceof String s) {
                    names.add(s);
                }
            }
            for (MethodNode m : classNode.methods) {
                reflection.analyze(classNode, m, archive, keeps, hierarchy);
                annotations(m.visibleAnnotations, archive, keeps);
                annotations(m.invisibleAnnotations, archive, keeps);
                if ((m.access & Opcodes.ACC_NATIVE) != 0) {
                    keeps.keepClass(classNode.name, "JNI native name contract");
                }
                if (annotationContracts.requiresMemberName(m.visibleAnnotations)
                        || annotationContracts.requiresMemberName(m.invisibleAnnotations)) {
                    keeps.keepMember(
                            classNode.name, m.name, m.desc, "Unknown annotated method contract");
                }
                for (AbstractInsnNode instruction : m.instructions) {
                    if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof String s) {
                        names.add(s);
                    }
                    if (instruction instanceof MethodInsnNode call) {
                        if ((call.owner.equals("java/lang/Class") && call.name.equals("forName"))
                                || (call.owner.endsWith("ClassLoader")
                                        && call.name.equals("loadClass"))) {
                            boolean literal = nearbyLiteral(call);
                            if (!literal
                                    && (call.name.equals("forName")
                                            || call.name.equals("loadClass"))) {
                                keeps.dynamicClasses();
                            } else if (!literal) {
                                keeps.dynamicMembers();
                            }
                        }
                        if (call.owner.equals("java/lang/invoke/MethodHandles$Lookup")
                                && call.name.startsWith("find")) {
                            keeps.dynamicMembers(
                                    classNode.name
                                            + "#"
                                            + m.name
                                            + ": dynamic method handle lookup");
                        }
                    }
                }
            }
        }
        for (String value : names) {
            String internal = value.replace('.', '/');
            if (archive.classes().containsKey(internal)) {
                keeps.keepClass(internal, "Literal class name / reflection");
            }
            if (internal.startsWith("L") && internal.endsWith(";")) {
                String owner = internal.substring(1, internal.length() - 1);
                if (archive.classes().containsKey(owner)) {
                    keeps.keepClass(owner, "Descriptor string");
                }
            }
            String resource = value.startsWith("/") ? value.substring(1) : value;
            if (archive.resources().containsKey(resource)) {
                for (String owner : archive.classes().keySet()) {
                    int slash = owner.lastIndexOf('/');
                    if (slash > 0 && resource.startsWith(owner.substring(0, slash) + "/")) {
                        keeps.keepPackage(owner.substring(0, slash));
                    }
                }
            }
        }
        Remapper externalReferences =
                new Remapper(Opcodes.ASM9) {
                    @Override
                    public String map(String name) {
                        if (archive.classes().containsKey(name)) {
                            keeps.keepClass(name, "Referenced by supplied external dependency");
                            keeps.keepMembersOf(name);
                        }
                        return name;
                    }
                };
        for (byte[] bytes : hierarchy.libraryBytes()) {
            ClassReader reader = new ClassReader(bytes);
            if (!archive.classes().containsKey(reader.getClassName())) {
                reader.accept(
                        new ClassRemapper(
                                new ClassVisitor(Opcodes.ASM9) {
                                    @Override
                                    public MethodVisitor visitMethod(
                                            int access,
                                            String name,
                                            String descriptor,
                                            String signature,
                                            String[] exceptions) {
                                        return new MethodVisitor(Opcodes.ASM9) {
                                            @Override
                                            public void visitLdcInsn(Object value) {
                                                if (value instanceof String text
                                                        && archive.classes()
                                                                .containsKey(
                                                                        text.replace('.', '/'))) {
                                                    keeps.keepClass(
                                                            text,
                                                            "External dependency reflection"
                                                                    + " literal");
                                                }
                                            }
                                        };
                                    }
                                },
                                externalReferences),
                        ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        if (keeps.hasDynamicClasses()) {
            runStatistics.warnings.add(
                    "Dynamic class loading or module descriptor detected: class/package names"
                            + " retained. Use separate JAR boundaries to narrow protection scope.");
        }
        if (keeps.hasDynamicMembers()) {
            runStatistics.warnings.add(
                    "Dynamic member reflection detected: member names retained; bytecode transforms"
                            + " remain enabled.");
        }
        runStatistics.warnings.addAll(keeps.dynamicMemberReasons());
        if (multiRelease) {
            runStatistics.warnings.add(
                    "Multi-release class variants preserved byte-for-byte; root ABI names"
                            + " retained.");
        }
    }

    private boolean nearbyLiteral(AbstractInsnNode call) {
        AbstractInsnNode cursor = call.getPrevious();
        for (int i = 0; cursor != null && i < 10; cursor = cursor.getPrevious()) {
            if (cursor.getOpcode() < 0) {
                continue;
            }
            i++;
            if (cursor instanceof LdcInsnNode ldc && ldc.cst instanceof String) {
                return true;
            }
            if (cursor instanceof MethodInsnNode
                    || cursor instanceof InvokeDynamicInsnNode
                    || cursor instanceof FieldInsnNode
                    || cursor instanceof JumpInsnNode
                    || cursor.getOpcode() == Opcodes.ALOAD) {
                return false;
            }
        }
        return false;
    }

    private void annotations(
            List<AnnotationNode> annotations, ArchiveModel archive, KeepPolicy keeps) {
        if (annotations == null) {
            return;
        }
        for (AnnotationNode annotation : annotations) {
            if (annotation.values != null) {
                for (int i = 1; i < annotation.values.size(); i += 2) {
                    annotationValue(annotation.values.get(i), archive, keeps);
                }
            }
        }
    }

    private boolean hasAnnotation(ClassNode node, String descriptor) {
        return (node.visibleAnnotations != null
                        && node.visibleAnnotations.stream()
                                .anyMatch(a -> a.desc.equals(descriptor)))
                || (node.invisibleAnnotations != null
                        && node.invisibleAnnotations.stream()
                                .anyMatch(a -> a.desc.equals(descriptor)));
    }

    private void annotationValue(Object value, ArchiveModel archive, KeepPolicy keeps) {
        if (value instanceof String text) {
            for (String owner : archive.classes().keySet()) {
                if (text.contains(owner) || text.contains(owner.replace('/', '.'))) {
                    keeps.keepClass(owner, "Annotation soft reference");
                    keeps.keepMembersOf(owner);
                }
            }
        } else if (value instanceof List<?> list) {
            list.forEach(v -> annotationValue(v, archive, keeps));
        } else if (value instanceof AnnotationNode nested) {
            annotations(List.of(nested), archive, keeps);
        }
    }
}
