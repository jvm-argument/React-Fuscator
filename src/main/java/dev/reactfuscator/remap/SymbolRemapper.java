package dev.reactfuscator.remap;

import dev.reactfuscator.mapping.MappingModel;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;

public final class SymbolRemapper extends Remapper {
    private final MappingModel mapping;

    public SymbolRemapper(MappingModel mapping) {
        super(Opcodes.ASM9);
        this.mapping = mapping;
    }

    @Override
    public String map(String name) {
        return mapping.mapClass(name);
    }

    @Override
    public String mapPackageName(String name) {
        return mapping.packages().getOrDefault(name, name);
    }

    @Override
    public String mapMethodName(String owner, String name, String descriptor) {
        return mapping.mapMethod(owner, name, descriptor);
    }

    @Override
    public String mapFieldName(String owner, String name, String descriptor) {
        return mapping.mapField(owner, name, descriptor);
    }
}
