package dev.reactfuscator.verification;

import dev.reactfuscator.analysis.HierarchyService;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.SimpleVerifier;

public final class HierarchyVerifier extends SimpleVerifier {
    private final HierarchyService hierarchy;

    public HierarchyVerifier(ClassNode node, HierarchyService hierarchy) {
        super(
                Opcodes.ASM9,
                Type.getObjectType(node.name),
                node.superName == null ? null : Type.getObjectType(node.superName),
                node.interfaces.stream().map(Type::getObjectType).toList(),
                (node.access & Opcodes.ACC_INTERFACE) != 0);
        this.hierarchy = hierarchy;
    }

    @Override
    protected boolean isInterface(Type type) {
        return type.getSort() != Type.ARRAY && hierarchy.isInterface(type.getInternalName());
    }

    @Override
    protected Type getSuperClass(Type type) {
        if (type.getSort() == Type.ARRAY) {
            return Type.getObjectType("java/lang/Object");
        }
        String parent = hierarchy.resolve(type.getInternalName()).parent();
        return parent == null ? null : Type.getObjectType(parent);
    }

    @Override
    protected boolean isAssignableFrom(Type target, Type source) {
        if (target.equals(source)) {
            return true;
        }
        if ((target.getSort() != Type.OBJECT && target.getSort() != Type.ARRAY)
                || (source.getSort() != Type.OBJECT && source.getSort() != Type.ARRAY)) {
            return false;
        }
        return hierarchy.assignable(target.getInternalName(), source.getInternalName());
    }

    @Override
    protected boolean isSubTypeOf(BasicValue value, BasicValue expected) {
        Type source = value.getType(), target = expected.getType();
        if (source == null || target == null) {
            return source == null && target == null;
        }
        if (source.equals(target)) {
            return true;
        }
        if (target.getSort() != Type.OBJECT && target.getSort() != Type.ARRAY) {
            return false;
        }
        if (source.equals(Type.getObjectType("null"))) {
            return true;
        }
        if (source.getSort() != Type.OBJECT && source.getSort() != Type.ARRAY) {
            return false;
        }
        if (isAssignableFrom(target, source)) {
            return true;
        }
        int sourceDimensions = source.getSort() == Type.ARRAY ? source.getDimensions() : 0;
        int targetDimensions = target.getSort() == Type.ARRAY ? target.getDimensions() : 0;
        Type sourceElement = sourceDimensions > 0 ? source.getElementType() : source;
        Type targetElement = targetDimensions > 0 ? target.getElementType() : target;
        if (sourceElement.getSort() != Type.OBJECT) {
            sourceDimensions--;
            sourceElement = Type.getObjectType("java/lang/Object");
        }
        if (targetElement.getSort() != Type.OBJECT || sourceDimensions < targetDimensions) {
            return false;
        }
        if (sourceDimensions > targetDimensions) {
            sourceElement = Type.getObjectType("java/lang/Object");
        }
        return isAssignableFrom(targetElement, sourceElement) || isInterface(targetElement);
    }

    @Override
    protected Class<?> getClass(Type type) {
        throw new IllegalStateException(
                "Class loading is forbidden during bytecode verification: " + type);
    }
}
