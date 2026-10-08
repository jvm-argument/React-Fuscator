package dev.reactfuscator.model;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LabelNode;

import java.util.List;

public record FlowBlockModel(
        LabelNode entry, List<AbstractInsnNode> instructions, LabelNode fallthrough) {
}
