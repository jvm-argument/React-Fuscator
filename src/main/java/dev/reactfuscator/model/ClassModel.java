package dev.reactfuscator.model;

import org.objectweb.asm.tree.ClassNode;

public final class ClassModel {
    private final String entryName;
    private final String originalName;
    private final byte[] originalBytes;
    private ClassNode node;

    public ClassModel(String entryName, byte[] bytes, ClassNode node) {
        this.entryName = entryName;
        this.originalBytes = bytes;
        this.node = node;
        this.originalName = node.name;
    }

    public String entryName() {
        return entryName;
    }

    public String originalName() {
        return originalName;
    }

    public byte[] originalBytes() {
        return originalBytes;
    }

    public ClassNode node() {
        return node;
    }

    public void node(ClassNode node) {
        this.node = node;
    }
}
