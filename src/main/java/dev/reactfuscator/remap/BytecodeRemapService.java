package dev.reactfuscator.remap;

import dev.reactfuscator.mapping.MappingModel;
import dev.reactfuscator.model.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.tree.ClassNode;

public final class BytecodeRemapService {
    public void remap(ArchiveModel archive,MappingModel mapping) {
        SymbolRemapper remapper=new SymbolRemapper(mapping);
        for (ClassModel model : archive.classes().values()) {
            ClassNode result=new ClassNode(Opcodes.ASM9);
            model.node().accept(new ClassRemapper(result,remapper)); model.node(result);
        }
    }
}
