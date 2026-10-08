package dev.reactfuscator.model;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.List;
import java.util.Map;

public record FlowGraphModel(
        List<FlowBlockModel> blocks,
        Type[] localTypes,
        Map<AbstractInsnNode, Type> referenceLoads) {
}
