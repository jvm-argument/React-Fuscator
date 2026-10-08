package dev.reactfuscator.registry;

import dev.reactfuscator.model.ArchiveModel;
import dev.reactfuscator.platform.PlatformHandler;

import java.util.List;

public final class PlatformRegistry {
    private final List<PlatformHandler> handlers;

    public PlatformRegistry(List<PlatformHandler> handlers) {
        this.handlers = List.copyOf(handlers);
    }

    public List<PlatformHandler> detect(ArchiveModel archive) {
        return handlers.stream().filter(h -> h.matches(archive)).toList();
    }
}
