package dev.reactfuscator.remap;

import dev.reactfuscator.mapping.MappingModel;
import dev.reactfuscator.model.ArchiveModel;
import dev.reactfuscator.model.ClassModel;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.tree.ClassNode;

public final class BytecodeRemapService {
    public void remap(ArchiveModel archive, MappingModel mapping) {
        SymbolRemapper remapper = new SymbolRemapper(mapping);
        for (ClassModel model : archive.classes().values()) {
            ClassNode result = new ClassNode(Opcodes.ASM9);
            model.node().accept(new ClassRemapper(result, remapper));
            model.node(result);
        }
    }
}
