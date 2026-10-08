package dev.reactfuscator.analysis;

import dev.reactfuscator.model.ArchiveModel;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.util.Set;
import java.util.TreeSet;

public final class DependencyAnalyzer {
    public Set<String> references(ArchiveModel archive) {
        Set<String> result = new TreeSet<>();
        Remapper collector =
                new Remapper(Opcodes.ASM9) {
                    @Override
                    public String map(String name) {
                        if (name != null && !name.startsWith("[")) {
                            result.add(name);
                        }
                        return name;
                    }
                };
        archive.classes()
                .values()
                .forEach(
                        model ->
                                model.node()
                                        .accept(
                                                new ClassRemapper(
                                                        new ClassVisitor(Opcodes.ASM9) {
                                                            @Override
                                                            public MethodVisitor visitMethod(
                                                                    int access,
                                                                    String name,
                                                                    String descriptor,
                                                                    String signature,
                                                                    String[] exceptions) {
                                                                return new MethodVisitor(
                                                                        Opcodes.ASM9) {
                                                                };
                                                            }

                                                            @Override
                                                            public FieldVisitor visitField(
                                                                    int access,
                                                                    String name,
                                                                    String descriptor,
                                                                    String signature,
                                                                    Object value) {
                                                                return new FieldVisitor(
                                                                        Opcodes.ASM9) {
                                                                };
                                                            }
                                                        },
                                                        collector)));
        result.removeAll(archive.classes().keySet());
        return result;
    }
}
