package dev.reactfuscator.model;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.*;

public record FlowGraphModel(
        List<FlowBlockModel> blocks,
        Type[] localTypes,
        Map<AbstractInsnNode, Type> referenceLoads) {
        }
