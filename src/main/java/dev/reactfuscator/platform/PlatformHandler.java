package dev.reactfuscator.platform;

import dev.reactfuscator.analysis.KeepPolicy;
import dev.reactfuscator.mapping.MappingModel;
import dev.reactfuscator.model.ArchiveModel;

import java.io.IOException;

public interface PlatformHandler {
    String id();

    boolean matches(ArchiveModel archive);

    void analyze(ArchiveModel archive, KeepPolicy keeps) throws IOException;

    void remap(ArchiveModel archive, MappingModel mapping) throws IOException;
}
