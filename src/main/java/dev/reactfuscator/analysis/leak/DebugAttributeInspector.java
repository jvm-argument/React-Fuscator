package dev.reactfuscator.analysis.leak;

import org.objectweb.asm.tree.*;

import java.util.*;

public final class DebugAttributeInspector {
    public Map<String, Long> count(ClassNode owner) {
        Map<String, Long> attributes = new TreeMap<>();
        if (owner.sourceFile != null) {
            add(attributes, "SourceFile");
        }
        if (owner.sourceDebug != null) {
            add(attributes, "SourceDebugExtension");
        }
        for (MethodNode method : owner.methods) {
            if (method.parameters != null && !method.parameters.isEmpty()) {
                add(attributes, "MethodParameters");
            }
            if (method.localVariables != null && !method.localVariables.isEmpty()) {
                add(attributes, "LocalVariableTable");
                if (method.localVariables.stream().anyMatch(v -> v.signature != null)) {
                    add(attributes, "LocalVariableTypeTable");
                }
            }
            if (method.visibleLocalVariableAnnotations != null
                    && !method.visibleLocalVariableAnnotations.isEmpty()) {
                add(attributes, "RuntimeVisibleLocalVariableAnnotations");
            }
            if (method.invisibleLocalVariableAnnotations != null
                    && !method.invisibleLocalVariableAnnotations.isEmpty()) {
                add(attributes, "RuntimeInvisibleLocalVariableAnnotations");
            }
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof LineNumberNode) {
                    add(attributes, "LineNumberTable");
                    break;
                }
            }
            if (method.attrs != null) {
                method.attrs.stream()
                        .filter(
                                a ->
                                        Set.of("CharacterRangeTable", "CompilationID", "SourceID")
                                                .contains(a.type))
                        .forEach(a -> add(attributes, a.type));
            }
        }
        if (owner.attrs != null) {
            owner.attrs.stream()
                    .filter(
                            a ->
                                    Set.of("CharacterRangeTable", "CompilationID", "SourceID")
                                            .contains(a.type))
                    .forEach(a -> add(attributes, a.type));
        }
        return attributes;
    }

    private void add(Map<String, Long> values, String key) {
        values.merge(key, 1L, Long::sum);
    }
}
